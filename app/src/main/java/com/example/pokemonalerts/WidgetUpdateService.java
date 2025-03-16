// WidgetUpdateService.java
package com.example.pokemonalerts;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WidgetUpdateService extends Service {

    private static final String TAG = "WidgetUpdateService";
    private static final long UPDATE_INTERVAL = 5 * 1000; // 5 seconds
    private static final String CHANNEL_ID = "PokemonAlertsChannel";
    private static int notificationId = 0; // Increment for each notification
    private Timer timer;

    @Override
    public void onCreate() {
        super.onCreate();
        timer = new Timer();
        createNotificationChannel();
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

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Pokemon Alerts Channel";
            String description = "Channel for Pokemon Alerts notifications";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateWidgetData() {
        PokemonApiService apiService = ApiClient.getClient().create(PokemonApiService.class);
        Call<List<PokemonReport>> call = apiService.getPokemonReports();

        call.enqueue(new Callback<List<PokemonReport>>() {
            @Override
            public void onResponse(Call<List<PokemonReport>> call, Response<List<PokemonReport>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PokemonReport> newPokemonReports = response.body();
                    boolean dataChanged =
                            PokemonWidgetService.updatePokemonReports(newPokemonReports);

                    if (dataChanged) {
                        // Show notification for the first new alert
                        if (!newPokemonReports.isEmpty()) {
                            showNotification(newPokemonReports.get(0));
                        }

                        // Only update widgets if the data has changed
                        AppWidgetManager appWidgetManager =
                                AppWidgetManager.getInstance(getApplicationContext());
                        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                                new ComponentName(
                                        getApplicationContext(), PokemonWidgetProvider.class));

                        // Notify widgets to update their data
                        appWidgetManager.notifyAppWidgetViewDataChanged(
                                appWidgetIds, R.id.widget_list_view);

                        // Broadcast to update widget UI
                        Intent updateIntent =
                                new Intent(getApplicationContext(), PokemonWidgetProvider.class);
                        updateIntent.setAction(PokemonWidgetProvider.ACTION_UPDATE_WIDGET);
                        sendBroadcast(updateIntent);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<PokemonReport>> call, Throwable t) {
                // Handle error - but don't update widgets on error
                Log.e(TAG, "API call failed: " + t.getMessage());
            }
        });
    }

    private void showNotification(PokemonReport pokemon) {
        // Create an explicit intent for the MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher_round)
                        .setContentTitle("New Pokémon Alert!")
                        .setContentText(
                                "A "
                                        + pokemon.getName()
                                        + " of type "
                                        + pokemon.getType()
                                        + " is available!")
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Notification permission not granted");
            return;
        }
        notificationManager.notify(notificationId++, builder.build());
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
