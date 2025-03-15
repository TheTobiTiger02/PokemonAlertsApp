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
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Response;

public class PokemonWidgetService extends RemoteViewsService {

    private static List<PokemonReport> pokemonReports = new ArrayList<>();
    // Keep track of previous data to avoid unnecessary updates
    private static List<PokemonReport> previousPokemonReports = new ArrayList<>();

    public static PokemonReport getPokemonAtPosition(int position) {
        if (position >= 0 && position < pokemonReports.size()) {
            return pokemonReports.get(position);
        }
        return null;
    }

    /**
     * Compares two PokemonReport lists to check if there are actual changes
     * @return true if the data has changed, false otherwise
     */
    public static boolean hasDataChanged(List<PokemonReport> newData) {
        if (newData.size() != previousPokemonReports.size()) {
            return true; // Different size means data has changed
        }

        // Compare each item - simplified comparison focusing on key fields
        for (int i = 0; i < newData.size(); i++) {
            PokemonReport newItem = newData.get(i);
            PokemonReport oldItem = previousPokemonReports.get(i);

            // Compare essential fields - name and endTime are usually enough
            // to determine if it's a different Pokemon alert
            if (!Objects.equals(newItem.getName(), oldItem.getName()) ||
                    !Objects.equals(newItem.getEndTime(), oldItem.getEndTime())) {
                return true;
            }
        }

        return false; // No changes detected
    }

    /**
     * Updates the Pokemon reports data only if there are changes
     * @return true if the data was updated, false if no changes were needed
     */
    public static boolean updatePokemonReports(List<PokemonReport> newData) {
        if (newData == null) {
            return false;
        }

        if (hasDataChanged(newData)) {
            // Save previous data before updating
            previousPokemonReports = new ArrayList<>(pokemonReports);
            pokemonReports = newData;
            return true;
        }

        return false; // No update needed
    }

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new PokemonRemoteViewsFactory(this.getApplicationContext());
    }

    class PokemonRemoteViewsFactory implements RemoteViewsService.RemoteViewsFactory {
        private Context context;
        private boolean dataChanged = false;

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
            dataChanged = fetchPokemonReports();
        }

        private boolean fetchPokemonReports() {
            PokemonApiService apiService = ApiClient.getClient().create(PokemonApiService.class);
            Call<List<PokemonReport>> call = apiService.getPokemonReports();

            try {
                Response<List<PokemonReport>> response = call.execute(); // Synchronous call for widget
                if (response.isSuccessful() && response.body() != null) {
                    return updatePokemonReports(response.body());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            return false;
        }

        @Override
        public void onDestroy() {
            // No need to clear data on destroy - we want to keep it for comparison
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
