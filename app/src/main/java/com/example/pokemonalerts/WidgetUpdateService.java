package com.example.pokemonalerts;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WidgetUpdateService extends Service {
    private static final long UPDATE_INTERVAL = 5 * 1000; // 5 seconds
    private Timer timer;

    @Override
    public void onCreate() {
        super.onCreate();
        timer = new Timer();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Schedule frequent updates
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateWidgetData();
            }
        }, 0, UPDATE_INTERVAL);

        return START_STICKY;
    }

    private void updateWidgetData() {
        PokemonApiService apiService = ApiClient.getClient().create(PokemonApiService.class);
        Call<List<PokemonReport>> call = apiService.getPokemonReports();

        call.enqueue(new Callback<List<PokemonReport>>() {
            @Override
            public void onResponse(Call<List<PokemonReport>> call, Response<List<PokemonReport>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Check if data has actually changed before updating widget
                    boolean dataChanged = PokemonWidgetService.updatePokemonReports(response.body());

                    if (dataChanged) {
                        // Only update widgets if the data has changed
                        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(getApplicationContext());
                        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                                new ComponentName(getApplicationContext(), PokemonWidgetProvider.class));

                        // Notify widgets to update their data
                        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view);

                        // Broadcast to update widget UI
                        Intent updateIntent = new Intent(getApplicationContext(), PokemonWidgetProvider.class);
                        updateIntent.setAction(PokemonWidgetProvider.ACTION_UPDATE_WIDGET);
                        sendBroadcast(updateIntent);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<PokemonReport>> call, Throwable t) {
                // Handle error - but don't update widgets on error
            }
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
