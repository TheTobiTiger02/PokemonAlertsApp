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

import java.util.ArrayList;
import java.util.List;

public class PokemonAdapter extends RecyclerView.Adapter<PokemonAdapter.PokemonViewHolder> {

    private List<PokemonReport> pokemonList = new ArrayList<>();
    private Context context;
    private OnPokemonClickListener listener;

    public interface OnPokemonClickListener {
        void onViewMapClick(PokemonReport pokemon);
    }

    public PokemonAdapter(Context context, OnPokemonClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setPokemonList(List<PokemonReport> pokemonList) {
        this.pokemonList = pokemonList != null ? pokemonList : new ArrayList<>();
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
        holder.pokemonType.setText("Type: " + pokemon.getType());
        holder.pokemonEndTime.setText("Available until: " + pokemon.getEndTime());
        holder.pokemonDescription.setText(pokemon.getDescription());

        // Load image with Glide
        Glide.with(context)
                .load(pokemon.getImageUrl())
                .placeholder(R.drawable.placeholder_pokemon)
                .error(R.drawable.error_pokemon)
                .into(holder.pokemonImage);

        holder.btnViewOnMap.setOnClickListener(v -> {
            if (listener != null) {
                listener.onViewMapClick(pokemon);
            }
        });
    }

    @Override
    public int getItemCount() {
        return pokemonList.size();
    }

    static class PokemonViewHolder extends RecyclerView.ViewHolder {
        ImageView pokemonImage;
        TextView pokemonName, pokemonType, pokemonEndTime, pokemonDescription;
        Button btnViewOnMap;

        public PokemonViewHolder(@NonNull View itemView) {
            super(itemView);
            pokemonImage = itemView.findViewById(R.id.pokemonImage);
            pokemonName = itemView.findViewById(R.id.pokemonName);
            pokemonType = itemView.findViewById(R.id.pokemonType);
            pokemonEndTime = itemView.findViewById(R.id.pokemonEndTime);
            pokemonDescription = itemView.findViewById(R.id.pokemonDescription);
            btnViewOnMap = itemView.findViewById(R.id.btnViewOnMap);
        }
    }
}
