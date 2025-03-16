package com.example.pokemonalerts;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import com.bumptech.glide.Glide;

public class PokemonDetailActivity extends AppCompatActivity {

    private static final String TAG = "PokemonDetailActivity";
    private ImageView detailImage;
    private TextView detailName, detailType, detailEndTime, detailDescription;
    private Button btnDetailShare, btnViewOnMap, btnBack;
    private PokemonReport pokemon;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pokemon_detail);

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
        detailName.setText(pokemon.getName());
        detailType.setText("Type: " + pokemon.getType());
        detailEndTime.setText("Available until: " + pokemon.getEndTime());
        detailDescription.setText(pokemon.getDescription());
        Glide.with(this)
                .load(pokemon.getImageUrl())
                .placeholder(R.drawable.placeholder_pokemon)
                .error(R.drawable.error_pokemon)
                .into(detailImage);

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
}
