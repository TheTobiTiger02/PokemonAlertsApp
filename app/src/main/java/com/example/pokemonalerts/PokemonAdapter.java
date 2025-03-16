package com.example.pokemonalerts;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

import java.util.ArrayList;
import java.util.List;

public class PokemonAdapter extends RecyclerView.Adapter<PokemonAdapter.PokemonViewHolder> {

    private List<PokemonReport> pokemonList = new ArrayList<>();
    private Context context;
    private OnPokemonClickListener listener;

    // Extended interface with extra callbacks:
    public interface OnPokemonClickListener {
        void onItemClick(PokemonReport pokemon);
        void onViewMapClick(PokemonReport pokemon);
        void onShareClick(PokemonReport pokemon);
    }

    public PokemonAdapter(Context context, OnPokemonClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPokemonList(List<PokemonReport> pokemonList) {
        this.pokemonList = (pokemonList != null) ? pokemonList : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PokemonViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_pokemon, parent, false);
        return new PokemonViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PokemonViewHolder holder, int position) {
        PokemonReport pokemon = pokemonList.get(position);

        holder.pokemonName.setText(pokemon.getName());
        holder.pokemonType.setText(pokemon.getType());
        holder.pokemonEndTime.setText("Until: " + pokemon.getEndTime());
        holder.pokemonDescription.setText(pokemon.getDescription());

        // Set type chip color based on the Pokemon type
        setTypeChipColor(holder.pokemonType, pokemon.getType());

        // Load image with Glide with fade animation
        Glide.with(context)
                .load(pokemon.getImageUrl())
                .transition(DrawableTransitionOptions.withCrossFade())
                .placeholder(R.drawable.placeholder_pokemon)
                .error(R.drawable.error_pokemon)
                .into(holder.pokemonImage);

        // Item click (for detailed view)
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(pokemon);
            }
        });

        // View on Map button
        holder.btnViewOnMap.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewMapClick(pokemon);
            }
        });

        // Share button
        holder.btnShare.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShareClick(pokemon);
            }
        });
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

    @Override
    public int getItemCount() {
        return pokemonList.size();
    }

    static class PokemonViewHolder extends RecyclerView.ViewHolder {
        ImageView pokemonImage;
        TextView pokemonName, pokemonEndTime, pokemonDescription;
        Chip pokemonType;
        MaterialButton btnViewOnMap, btnShare;

        public PokemonViewHolder(@NonNull View itemView) {
            super(itemView);
            pokemonImage = itemView.findViewById(R.id.pokemonImage);
            pokemonName = itemView.findViewById(R.id.pokemonName);
            pokemonType = itemView.findViewById(R.id.pokemonType);
            pokemonEndTime = itemView.findViewById(R.id.pokemonEndTime);
            pokemonDescription = itemView.findViewById(R.id.pokemonDescription);
            btnViewOnMap = itemView.findViewById(R.id.btnViewOnMap);
            btnShare = itemView.findViewById(R.id.btnShare);
        }
    }
}
