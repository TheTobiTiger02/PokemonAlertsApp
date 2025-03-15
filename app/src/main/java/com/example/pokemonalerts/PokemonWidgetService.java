package com.example.pokemonalerts;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Response;

public class PokemonWidgetService extends RemoteViewsService {

    private static List<PokemonReport> pokemonReports = new ArrayList<>();

    public static PokemonReport getPokemonAtPosition(int position) {
        if (position >= 0 && position < pokemonReports.size()) {
            return pokemonReports.get(position);
        }
        return null;
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new PokemonRemoteViewsFactory(this.getApplicationContext());
    }

    class PokemonRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private Context context;

        PokemonRemoteViewsFactory(Context context) {
            this.context = context;
        }

        @Override
        public void onCreate() {
            // Initialize the data set
        }

        @Override
        public void onDataSetChanged() {
            // Fetch the latest Pokemon reports when data changes
            fetchPokemonReports();
        }

        private void fetchPokemonReports() {
            PokemonApiService apiService = ApiClient.getClient().create(PokemonApiService.class);
            Call<List<PokemonReport>> call = apiService.getPokemonReports();

            try {
                Response<List<PokemonReport>> response = call.execute(); // Synchronous call for widget
                if (response.isSuccessful() && response.body() != null) {
                    pokemonReports = response.body();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onDestroy() {
            pokemonReports.clear();
        }

        @Override
        public int getCount() {
            return pokemonReports.size();
        }

        @Override
        public RemoteViews getViewAt(int position) {
            if (position < 0 || position >= pokemonReports.size()) {
                return null;
            }

            PokemonReport pokemon = pokemonReports.get(position);
            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_list_item);

            // Set text elements
            rv.setTextViewText(R.id.widget_item_name, pokemon.getName());
            rv.setTextViewText(R.id.widget_item_type, "Type: " + pokemon.getType());
            rv.setTextViewText(R.id.widget_item_end_time, "Until: " + pokemon.getEndTime());

            // Try to load the image (this is a basic implementation, might not work for all images)
            try {
                Bitmap bitmap = getBitmapFromURL(pokemon.getImageUrl());
                if (bitmap != null) {
                    rv.setImageViewBitmap(R.id.widget_item_image, bitmap);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            // Set up the fill-in intent for item clicks
            Bundle extras = new Bundle();
            extras.putInt(PokemonWidgetProvider.EXTRA_ITEM_POSITION, position);
            Intent fillInIntent = new Intent();
            fillInIntent.putExtras(extras);
            rv.setOnClickFillInIntent(R.id.widget_list_item, fillInIntent);

            return rv;
        }

        private Bitmap getBitmapFromURL(String src) {
            try {
                URL url = new URL(src);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                return BitmapFactory.decodeStream(input);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        public RemoteViews getLoadingView() {
            return null; // Use default loading view
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }
    }
}
