package com.example.pokemonalerts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

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
    private TabLayout tabLayout;
    private Button btnDebug;

    private static final String[] ALERT_TYPES = {"All", "Rare", "PvP", "Hundo", "Nundo", "Raid", "Rocket", "Kecleon"};

    // Auto-refresh handler and runnable
    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;
    private static final long AUTO_REFRESH_INTERVAL = 5000; // 5 seconds

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start widget update service
        startService(new Intent(this, WidgetUpdateService.class));

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        errorText = findViewById(R.id.errorText);
        tabLayout = findViewById(R.id.tabLayout);
        btnDebug = findViewById(R.id.btnDebug);

        // Set up debug button
        btnDebug.setOnClickListener(v -> {
            callTestEndpoint();
        });

        // Set up auto-refresh
        setupAutoRefresh();

        // Set up tabs
        for (String type : ALERT_TYPES) {
            tabLayout.addTab(tabLayout.newTab().setText(type));
        }

        // Set up adapter
        adapter = new PokemonAdapter(this, this);
        recyclerView.setAdapter(adapter);

        // Set up ViewModel
        viewModel = new ViewModelProvider(this).get(PokemonViewModel.class);

        // Observe LiveData
        viewModel.getPokemonReports().observe(this, pokemonReports -> {
            adapter.setPokemonList(pokemonReports);

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
        swipeRefreshLayout.setOnRefreshListener(() -> {
            viewModel.loadPokemonReports();
        });

        // Set up tab selection listener
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                String selectedType = tab.getText().toString();
                if (selectedType.equals("All")) {
                    adapter.setPokemonList(viewModel.getPokemonReports().getValue());
                } else {
                    adapter.setPokemonList(viewModel.filterByType(selectedType));
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });

        // Load data
        viewModel.loadPokemonReports();
    }

    private void callTestEndpoint() {
        Toast.makeText(this, "Calling test endpoint...", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Calling /api/test endpoint");

        // Show loading indicator
        progressBar.setVisibility(View.VISIBLE);

        // Create API service
        PokemonApiService apiService = ApiClient.getClient().create(PokemonApiService.class);
        Call<Void> call = apiService.testApiEndpoint();

        // Execute the call
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
                // Refresh data silently (without showing loading indicator)
                viewModel.loadPokemonReports(true);

                // Schedule next refresh
                autoRefreshHandler.postDelayed(this, AUTO_REFRESH_INTERVAL);
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start auto-refresh when activity is visible
        autoRefreshHandler.postDelayed(autoRefreshRunnable, AUTO_REFRESH_INTERVAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop auto-refresh when activity is not visible
        autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
    }

    @Override
    public void onViewMapClick(PokemonReport pokemon) {
        try {
            // Correct order: latitude first, then longitude for Google Maps
            String uriString = "geo:" + pokemon.getLatitude() + "," + pokemon.getLongitude() +
                    "?q=" + pokemon.getLatitude() + "," + pokemon.getLongitude() +
                    "(" + pokemon.getName() + ")";

            Uri gmmIntentUri = Uri.parse(uriString);
            Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
            mapIntent.setPackage("com.google.android.apps.maps");

            if (mapIntent.resolveActivity(getPackageManager()) != null) {
                startActivity(mapIntent);
            } else {
                // Fallback to custom map if needed
                Intent intent = new Intent(this, MapActivity.class);
                intent.putExtra("pokemon", pokemon);
                startActivity(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error opening maps: " + e.getMessage(), e);
            Toast.makeText(this, "Error opening map", Toast.LENGTH_SHORT).show();
        }
    }
}
