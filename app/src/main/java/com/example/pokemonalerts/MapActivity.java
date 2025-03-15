package com.example.pokemonalerts;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private GoogleMap map;
    private PokemonReport pokemon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);

        pokemon = (PokemonReport) getIntent().getSerializableExtra("pokemon");

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        map = googleMap;

        if (pokemon != null) {
            LatLng pokemonLocation = new LatLng(pokemon.getLatitude(), pokemon.getLongitude());
            map.addMarker(new MarkerOptions()
                    .position(pokemonLocation)
                    .title(pokemon.getName())
                    .snippet("Available until: " + pokemon.getEndTime()));
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(pokemonLocation, 16));
        }
    }
}
