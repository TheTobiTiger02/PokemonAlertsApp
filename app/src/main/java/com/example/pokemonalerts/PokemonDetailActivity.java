package com.example.pokemonalerts;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

public class PokemonDetailActivity extends AppCompatActivity {

    private static final String TAG = "PokemonDetailActivity";
    private ImageView detailImage;
    private TextView detailName, detailEndTime, detailDescription;
    private Chip detailType;
    private MaterialButton btnDetailShare, btnViewOnMap, btnBack;
    private PokemonReport pokemon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pokemon_detail);

        // Setup toolbar
        Toolbar toolbar = findViewById(R.id.detailToolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);

        toolbar.setNavigationOnClickListener(v -> finish());

        // Find views
        detailImage = findViewById(R.id.detailImage);
        detailName = findViewById(R.id.detailName);
        detailType = findViewById(R.id.detailType);
        detailEndTime = findViewById(R.id.detailEndTime);
        detailDescription = findViewById(R.id.detailDescription);
        btnDetailShare = findViewById(R.id.btnDetailShare);
        btnViewOnMap = findViewById(R.id.btnViewOnMap);
        btnBack = findViewById(R.id.btnBack);

        if (getIntent().hasExtra("pokemon")) {
            pokemon = (PokemonReport) getIntent().getSerializableExtra("pokemon");
            Log.d(TAG, "Received Pokemon: " + pokemon.getName());
            updateUI();
        } else {
            Log.e(TAG, "No Pokemon data in intent");
            Toast.makeText(this, "No Pokémon data available", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void updateUI() {
        // Set text values
        detailName.setText(pokemon.getName());
        detailType.setText(pokemon.getType());
        detailEndTime.setText("Available until: " + pokemon.getEndTime());
        detailDescription.setText(pokemon.getDescription());

        // Set toolbar title
        getSupportActionBar().setTitle(pokemon.getName());

        // Set type chip color
        setTypeChipColor(detailType, pokemon.getType());

        // Load image with animation
        Glide.with(this)
                .load(pokemon.getImageUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.placeholder_pokemon)
                .error(R.drawable.error_pokemon)
                .into(detailImage);

        // Share button
        btnDetailShare.setOnClickListener(v -> {
            // Create a Google Maps search link using the Pokémon's coordinates.
            String mapsLink = "https://www.google.com/maps/search/?api=1&query=" +
                    pokemon.getLatitude() + "," + pokemon.getLongitude();
            String shareText = "Check out this Pokémon alert!\n\n" +
                    "Name: " + pokemon.getName() + "\n" +
                    "Type: " + pokemon.getType() + "\n" +
                    "Available until: " + pokemon.getEndTime() + "\n" +
                    "Description: " + pokemon.getDescription() + "\n" +
                    "Location: " + mapsLink;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(shareIntent, "Share via"));
        });

        // View on map button
        btnViewOnMap.setOnClickListener(v -> {
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
                    Intent intent = new Intent(PokemonDetailActivity.this, MapActivity.class);
                    intent.putExtra("pokemon", pokemon);
                    startActivity(intent);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error opening map: " + e.getMessage(), e);
                Toast.makeText(PokemonDetailActivity.this,
                        "Error opening map", Toast.LENGTH_SHORT).show();
            }
        });

        btnBack.setOnClickListener(v -> finish());
    }

    private void setTypeChipColor(Chip chip, String type) {
        int colorResId;
        switch (type.toLowerCase()) {
            case "rare":
                colorResId = R.color.type_rare;
                break;
            case "pvp":
                colorResId = R.color.type_pvp;
                break;
            case "hundo":
                colorResId = R.color.type_hundo;
                break;
            case "nundo":
                colorResId = R.color.type_nundo;
                break;
            case "raid":
                colorResId = R.color.type_raid;
                break;
            case "rocket":
                colorResId = R.color.type_rocket;
                break;
            case "kecleon":
                colorResId = R.color.type_kecleon;
                break;
            default:
                colorResId = R.color.colorPrimary;
                break;
        }
        chip.setChipBackgroundColorResource(colorResId);
    }
}
