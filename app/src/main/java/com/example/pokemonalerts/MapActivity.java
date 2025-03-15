package com.example.pokemonalerts;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "MapActivity";
    private GoogleMap map;
    private PokemonReport pokemon;
    private ProgressBar loadingIndicator;
    private TextView errorText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        // Initialize views
        loadingIndicator = findViewById(R.id.map_loading);
        errorText = findViewById(R.id.map_error_text);

        // Get Pokemon data from intent
        if (getIntent().hasExtra("pokemon")) {
            pokemon = (PokemonReport) getIntent().getSerializableExtra("pokemon");
            Log.d(TAG, "Received Pokemon: " + pokemon.getName() +
                    " at location: " + pokemon.getLatitude() + "," + pokemon.getLongitude());
        } else {
            Log.e(TAG, "No Pokemon data in intent");
            showError("No Pokemon data available");
            return;
        }

        // Check if the location is valid
        if (pokemon.getLatitude() == 0 && pokemon.getLongitude() == 0) {
            Log.e(TAG, "Invalid location coordinates (0,0)");
            showError("Invalid location coordinates");
            return;
        }

        // Set up the map
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);

        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        } else {
            Log.e(TAG, "Map fragment not found");
            showError("Map could not be loaded");
        }
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        Log.d(TAG, "Map is ready");
        map = googleMap;
        loadingIndicator.setVisibility(View.GONE);

        if (pokemon != null) {
            try {
                LatLng pokemonLocation = new LatLng(pokemon.getLatitude(), pokemon.getLongitude());
                Log.d(TAG, "Adding marker at: " + pokemonLocation.latitude + "," + pokemonLocation.longitude);

                // Add a marker with custom color
                map.addMarker(new MarkerOptions()
                        .position(pokemonLocation)
                        .title(pokemon.getName())
                        .snippet("Available until: " + pokemon.getEndTime())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));

                // Move camera with animation and set zoom level
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(pokemonLocation, 16));

                // Enable zoom controls and other map settings
                map.getUiSettings().setZoomControlsEnabled(true);
                map.getUiSettings().setCompassEnabled(true);
                map.getUiSettings().setMapToolbarEnabled(true);

                Toast.makeText(this, pokemon.getName() + " location", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Log.e(TAG, "Error setting marker: " + e.getMessage(), e);
                showError("Error showing location: " + e.getMessage());
            }
        } else {
            showError("No Pokemon data available");
        }
    }

    private void showError(String message) {
        if (errorText != null) {
            errorText.setText(message);
            errorText.setVisibility(View.VISIBLE);
        }
        if (loadingIndicator != null) {
            loadingIndicator.setVisibility(View.GONE);
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
