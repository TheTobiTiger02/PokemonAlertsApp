package com.example.pokemonalerts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.tabs.TabLayout;

public class MainActivity extends AppCompatActivity implements PokemonAdapter.OnPokemonClickListener {

    private PokemonViewModel viewModel;
    private PokemonAdapter adapter;
    private RecyclerView recyclerView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private TextView errorText;
    private TabLayout tabLayout;

    private static final String[] ALERT_TYPES = {"All", "Rare", "PvP", "Hundo", "Nundo", "Raid", "Rocket", "Kecleon"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        recyclerView = findViewById(R.id.recyclerView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        errorText = findViewById(R.id.errorText);
        tabLayout = findViewById(R.id.tabLayout);

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

    @Override
    public void onViewMapClick(PokemonReport pokemon) {
        // Option 1: Open in Google Maps
        Uri gmmIntentUri = Uri.parse("geo:" + pokemon.getLatitude() + "," + pokemon.getLongitude() + "?q=" + pokemon.getLatitude() + "," + pokemon.getLongitude() + "(" + pokemon.getName() + ")");
        Intent mapIntent = new Intent(Intent.ACTION_VIEW, gmmIntentUri);
        mapIntent.setPackage("com.google.android.apps.maps");

        if (mapIntent.resolveActivity(getPackageManager()) != null) {
            startActivity(mapIntent);
        } else {
            // Option 2: Open in our own MapActivity
            Intent intent = new Intent(this, MapActivity.class);
            intent.putExtra("pokemon", pokemon);
            startActivity(intent);
        }
    }
}
