package com.example.pokemonalerts;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity implements PokemonAdapter.OnPokemonClickListener {

    private static final String TAG = "MainActivity";

    private PokemonViewModel viewModel;
    private PokemonAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView errorText;
    private FloatingActionButton btnDebug;

    // Auto-refresh handler and runnable
    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;
    private static final long AUTO_REFRESH_INTERVAL = 5000; // 5 seconds

    // Permission request launcher
    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        errorText = findViewById(R.id.errorText);
        btnDebug = findViewById(R.id.btnDebug);

        // Set up permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        checkBatteryOptimization();
                    } else {
                        // Permission denied, show a dialog explaining why we need it
                        showPermissionExplanationDialog();
                    }
                }
        );

        // Check for notification permission first (for Android 13+)
        checkNotificationPermission();

        // Set up debug button
        btnDebug.setOnClickListener(v -> callTestEndpoint());

        // Set up auto-refresh
        setupAutoRefresh();

        // Set up adapter
        adapter = new PokemonAdapter(this, this);
        recyclerView.setAdapter(adapter);

        // Set up ViewModel
        viewModel = new ViewModelProvider(this).get(PokemonViewModel.class);

        // Observe LiveData
        viewModel.getPokemonReports().observe(this, pokemonReports -> {
            updateDisplayedPokemonList();

            // Update widgets when data changes
            Intent updateWidgetIntent = new Intent(this, PokemonWidgetProvider.class);
            updateWidgetIntent.setAction(PokemonWidgetProvider.ACTION_UPDATE_WIDGET);
            sendBroadcast(updateWidgetIntent);
        });

        viewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            swipeRefreshLayout.setRefreshing(isLoading);
        });

        viewModel.getError().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                errorText.setText(error);
                errorText.setVisibility(View.VISIBLE);
            } else {
                errorText.setVisibility(View.GONE);
            }
        });

        // Set up SwipeRefreshLayout
        swipeRefreshLayout.setOnRefreshListener(() -> viewModel.loadPokemonReports());

        // Load data
        viewModel.loadPokemonReports();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; adds a Settings option
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            // Launch the settings activity
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                checkBatteryOptimization();
            }
        } else {
            checkBatteryOptimization();
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            String packageName = getPackageName();

            if (!powerManager.isIgnoringBatteryOptimizations(packageName)) {
                showBatteryOptimizationDialog();
            } else {
                startServices();
            }
        } else {
            startServices();
        }
    }

    private void showBatteryOptimizationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Battery Optimization")
                .setMessage("To ensure Pokemon Alerts continues running in the background and sending notifications, please disable battery optimization for this app.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        try {
                            Intent intent = new Intent();
                            intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        } catch (Exception e) {
                            // Fallback to general battery optimization settings
                            Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                            startActivity(intent);
                        }
                    }
                    startServices();
                })
                .setNegativeButton("Skip", (dialog, which) -> {
                    dialog.dismiss();
                    startServices();
                })
                .show();
    }

    private void showPermissionExplanationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Notification Permission Required")
                .setMessage("Pokémon Alerts needs notification permission to show you alerts about nearby Pokémon. Without this permission, you won't receive important alerts.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    dialog.dismiss();
                    checkBatteryOptimization();
                })
                .show();
    }

    private void startServices() {
        // Schedule alarm receiver for periodic updates
        AlarmReceiver.schedulePeriodicUpdates(this);

        // Start widget update service
        Intent widgetServiceIntent = new Intent(this, WidgetUpdateService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(widgetServiceIntent);
        } else {
            startService(widgetServiceIntent);
        }

        // Start foreground notification service
        Intent foregroundServiceIntent = new Intent(this, ForegroundNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(foregroundServiceIntent);
        } else {
            startService(foregroundServiceIntent);
        }

        Log.d(TAG, "All services and alarms started");
    }

    private void setupAutoRefresh() {
        autoRefreshHandler = new Handler(Looper.getMainLooper());
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() {
                viewModel.loadPokemonReports(true);
                autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);

        // Ensure services are still running when app resumes
        startServices();
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    /**
     * Updates the displayed Pokémon list by filtering based on user settings.
     */
    private void updateDisplayedPokemonList() {
        List<PokemonReport> fullList = viewModel.getPokemonReports().getValue();
        if (fullList == null) {
            adapter.setPokemonList(new ArrayList<>());
            return;
        }

        // Get the user preference for alert types to display
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> displayTypes = prefs.getStringSet("pref_display_types",
                new HashSet<>(Arrays.asList("All")));

        boolean showAll = displayTypes.contains("All");

        List<PokemonReport> filteredList = new ArrayList<>();
        for (PokemonReport report : fullList) {
            if (showAll || displayTypes.contains(report.getType())) {
                filteredList.add(report);
            }
        }

        adapter.setPokemonList(filteredList);
    }

    // -----------------------
    // Implementation of OnPokemonClickListener:

    // Launch the detail activity to show more info.
    @Override
    public void onItemClick(PokemonReport pokemon) {
        Intent detailIntent = new Intent(this, PokemonDetailActivity.class);
        detailIntent.putExtra("pokemon", pokemon);
        startActivity(detailIntent);
    }

    // Opens the Pokémon location on a map.
    @Override
    public void onViewMapClick(PokemonReport pokemon) {
        try {
            String uriString = "geo:" + pokemon.getLatitude() + "," + pokemon.getLongitude() +
                    "?q=" + pokemon.getLatitude() + "," + pokemon.getLongitude() +
                    "(" + pokemon.getName() + ")";
            Uri gmmIntentUri = Uri.parse(uriString);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");
            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                Intent intent = new Intent(this, MapActivity.class);
                intent.putExtra("pokemon", pokemon);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening maps: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening map", Toast.LENGTH_SHORT).show();
        }
    }

    // Share the Pokémon alert including a Google Maps link.
    @Override
    public void onShareClick(PokemonReport pokemon) {
        String mapsLink = "https://www.google.com/maps/search/?api=1&query=" +
                pokemon.getLatitude() + "," + pokemon.getLongitude();
        String shareText = "Check out this Pokémon alert!\n\n" +
                "Name: " + pokemon.getName() + "\n" +
                "Type: " + pokemon.getType() + "\n" +
                "Available until: " + pokemon.getEndTime() + "\n" +
                "Description: " + pokemon.getDescription() + "\n" +
                "View Location: " + mapsLink;
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
        startActivity(Intent.createChooser(shareIntent, "Share via"));
    }

    /**
     * Added to support the debug button; calls a test endpoint.
     */
    private void callTestEndpoint() {
        Toast.makeText(this, "Calling test endpoint...", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Calling /api/test endpoint");
        progressBar.setVisibility(View.VISIBLE);
        PokemonApiService apiService = ApiClient.getClient().create(PokemonApiService.class);
        Call<Void> call = apiService.testApiEndpoint();
        call.enqueue(new Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful()) {
                    Log.d(TAG, "Test endpoint call successful: " + response.code());
                    Toast.makeText(MainActivity.this,
                            "Test successful! Response code: " + response.code(),
                            Toast.LENGTH_LONG).show();
                } else {
                    Log.e(TAG, "Test endpoint failed: " + response.code());
                    Toast.makeText(MainActivity.this,
                            "Test failed! Response code: " + response.code(),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Test endpoint error: " + t.getMessage(), t);
                Toast.makeText(MainActivity.this,
                        "Test error: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}