package com.example.pokemonalerts;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity
        implements PokemonAdapter.OnPokemonClickListener {

    private static final String TAG = "MainActivity";
    private PokemonViewModel viewModel;
    private PokemonAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView errorText;
    private ChipGroup filterChipGroup;
    private FloatingActionButton btnDebug;
    private SearchView searchView;

    private static final String[] ALERT_TYPES = {"All", "Rare", "PvP", "Hundo", "Nundo", "Raid", "Rocket", "Kecleon"};

    // Auto-refresh handler and runnable
    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;
    private static final long AUTO_REFRESH_INTERVAL = 5000; // 5 seconds

    // Used for filtering
    private String searchQuery = "";
    private Set<String> selectedFilters = new HashSet<>();

    // Permission request
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
        filterChipGroup = findViewById(R.id.filterChipGroup);
        btnDebug = findViewById(R.id.btnDebug);
        searchView = findViewById(R.id.searchView);

        // Set up permission launcher
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        startServices();
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

        // Set up filter chips
        setupFilterChips();

        // Set up adapter
        adapter = new PokemonAdapter(this, this);
        recyclerView.setAdapter(adapter);

        // Set up ViewModel
        viewModel = new ViewModelProvider(this).get(PokemonViewModel.class);

        // Observe LiveData
        viewModel.getPokemonReports().observe(this, pokemonReports -> {
            // Update the displayed list when new data arrives
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

        // Set up SearchView listener for filtering by Pokémon name
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                updateDisplayedPokemonList();
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                updateDisplayedPokemonList();
                return false;
            }
        });

        // Load data
        viewModel.loadPokemonReports();
    }

    private void checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Permission already granted, start services
                startServices();
            }
        } else {
            // For pre-Android 13, notification permission is not needed
            startServices();
        }
    }

    private void showPermissionExplanationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Notification Permission Required")
                .setMessage("Pokémon Alerts needs notification permission to show you " +
                        "alerts about nearby Pokémon. Without this permission, " +
                        "you won't receive important alerts.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    // Open app settings
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Not Now", (dialog, which) -> {
                    dialog.dismiss();
                    // Start services without notification capability
                    startServices();
                })
                .show();
    }

    private void startServices() {
        // Start widget update service
        startService(new Intent(this, WidgetUpdateService.class));

        // Start foreground notification service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, ForegroundNotificationService.class));
        } else {
            startService(new Intent(this, ForegroundNotificationService.class));
        }
    }

    private void setupFilterChips() {
        // Create a chip for each alert type
        for (String type : ALERT_TYPES) {
            Chip chip = new Chip(this);
            chip.setText(type);
            chip.setCheckable(true);
            chip.setClickable(true);

            // Apply custom chip style
            chip.setChipBackgroundColorResource(R.color.chipBackgroundColor);

            // Set "All" as the default selection
            if (type.equals("All")) {
                chip.setChecked(true);
                selectedFilters.add(type);
            }

            // Add chip click listener
            chip.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // Handle chip selection state changes
                if (isChecked) {
                    // Chip is being selected
                    if (type.equals("All")) {
                        // Clear all other selections if "All" is selected
                        selectedFilters.clear();
                        selectedFilters.add("All");

                        // Update all other chips (uncheck them)
                        for (int i = 0; i < filterChipGroup.getChildCount(); i++) {
                            Chip otherChip = (Chip) filterChipGroup.getChildAt(i);
                            if (!otherChip.getText().equals("All")) {
                                // Temporarily remove listener to avoid recursion
                                otherChip.setOnCheckedChangeListener(null);
                                otherChip.setChecked(false);
                                // Re-add the listener
                                final int chipIndex = i;
                                otherChip.setOnCheckedChangeListener((v, checked) -> {
                                    String chipType = ALERT_TYPES[chipIndex];
                                    onChipSelectionChanged(chipType, checked);
                                });
                            }
                        }
                    } else {
                        // A specific filter is being selected, so uncheck "All"
                        selectedFilters.add(type);
                        if (selectedFilters.contains("All")) {
                            selectedFilters.remove("All");

                            // Find and uncheck the "All" chip
                            for (int i = 0; i < filterChipGroup.getChildCount(); i++) {
                                Chip otherChip = (Chip) filterChipGroup.getChildAt(i);
                                if (otherChip.getText().equals("All")) {
                                    // Temporarily remove listener to avoid recursion
                                    otherChip.setOnCheckedChangeListener(null);
                                    otherChip.setChecked(false);
                                    // Re-add the listener
                                    otherChip.setOnCheckedChangeListener((v, checked) ->
                                            onChipSelectionChanged("All", checked));
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    // Chip is being unselected
                    selectedFilters.remove(type);

                    // If no filters are selected, select "All"
                    if (selectedFilters.isEmpty()) {
                        selectedFilters.add("All");

                        // Find and check the "All" chip
                        for (int i = 0; i < filterChipGroup.getChildCount(); i++) {
                            Chip otherChip = (Chip) filterChipGroup.getChildAt(i);
                            if (otherChip.getText().equals("All")) {
                                // Temporarily remove listener to avoid recursion
                                otherChip.setOnCheckedChangeListener(null);
                                otherChip.setChecked(true);
                                // Re-add the listener
                                otherChip.setOnCheckedChangeListener((v, checked) ->
                                        onChipSelectionChanged("All", checked));
                                break;
                            }
                        }
                    }
                }

                // Update the displayed list based on new filter selection
                updateDisplayedPokemonList();
            });

            filterChipGroup.addView(chip);
        }
    }

    private void onChipSelectionChanged(String type, boolean isChecked) {
        if (isChecked) {
            // Handling chip selection
            if (type.equals("All")) {
                selectedFilters.clear();
                selectedFilters.add("All");

                // Uncheck all other chips
                for (int i = 0; i < filterChipGroup.getChildCount(); i++) {
                    Chip chip = (Chip) filterChipGroup.getChildAt(i);
                    if (!chip.getText().equals("All")) {
                        chip.setChecked(false);
                    }
                }
            } else {
                // A specific filter is selected
                selectedFilters.add(type);

                // If "All" was selected, uncheck it
                if (selectedFilters.contains("All")) {
                    selectedFilters.remove("All");
                    for (int i = 0; i < filterChipGroup.getChildCount(); i++) {
                        Chip chip = (Chip) filterChipGroup.getChildAt(i);
                        if (chip.getText().equals("All")) {
                            chip.setChecked(false);
                            break;
                        }
                    }
                }
            }
        } else {
            // Handling chip deselection
            selectedFilters.remove(type);

            // If no filters are selected, select "All"
            if (selectedFilters.isEmpty()) {
                selectedFilters.add("All");
                for (int i = 0; i < filterChipGroup.getChildCount(); i++) {
                    Chip chip = (Chip) filterChipGroup.getChildAt(i);
                    if (chip.getText().equals("All")) {
                        chip.setChecked(true);
                        break;
                    }
                }
            }
        }

        updateDisplayedPokemonList();
    }

    private void callTestEndpoint() {
        Toast.makeText(this, "Calling test endpoint...", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Calling /api/test endpoint");

        // Show loading indicator
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    /**
     * Updates the displayed Pokémon list by filtering by selected types
     * and the search query.
     */
    private void updateDisplayedPokemonList() {
        List<PokemonReport> fullList = viewModel.getPokemonReports().getValue();
        if (fullList == null) {
            adapter.setPokemonList(new ArrayList<>());
            return;
        }

        List<PokemonReport> filteredList = new ArrayList<>();

        for (PokemonReport report : fullList) {
            // Check if report matches the search query
            boolean matchesSearch = searchQuery.isEmpty() ||
                    report.getName().toLowerCase().contains(searchQuery.toLowerCase());

            // Check if report matches selected filter types
            boolean matchesFilter = false;

            // If "All" is selected, show everything
            if (selectedFilters.contains("All")) {
                matchesFilter = true;
            } else {
                // Check if the report's type matches any of the selected filters
                matchesFilter = selectedFilters.contains(report.getType());
            }

            // Add to filtered list if both conditions match
            if (matchesSearch && matchesFilter) {
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
        // Create a Google Maps search link using latitude and longitude.
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
}
