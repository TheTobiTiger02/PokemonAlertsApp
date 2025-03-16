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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // Track previously seen alerts to avoid duplicate notifications
    private Set<String> notifiedAlertIds = new HashSet<>();

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
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void updateWidgetData() {
        PokemonApiService apiService =
                ApiClient.getClient().create(PokemonApiService.class);
        Call<List<PokemonReport>> call = apiService.getPokemonReports();

        call.enqueue(new Callback<List<PokemonReport>>() {
            @Override
            public void onResponse(Call<List<PokemonReport>> call,
                                   Response<List<PokemonReport>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<PokemonReport> newPokemonReports = response.body();

                    // First check for any new alerts that need notifications
                    checkForNewAlerts(newPokemonReports);

                    boolean dataChanged =
                            PokemonWidgetService.updatePokemonReports(newPokemonReports);

                    if (dataChanged) {
                        // Only update widgets if the data has changed
                        AppWidgetManager appWidgetManager =
                                AppWidgetManager.getInstance(getApplicationContext());
                        int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                                new ComponentName(getApplicationContext(),
                                        PokemonWidgetProvider.class));

                        // Notify widgets to update their data
                        appWidgetManager.notifyAppWidgetViewDataChanged(
                                appWidgetIds, R.id.widget_list_view);

                        // Broadcast to update widget UI
                        Intent updateIntent = new Intent(getApplicationContext(),
                                PokemonWidgetProvider.class);
                        updateIntent.setAction(PokemonWidgetProvider.ACTION_UPDATE_WIDGET);
                        sendBroadcast(updateIntent);
                    }
                }
            }

            @Override
            public void onFailure(Call<List<PokemonReport>> call, Throwable t) {
                Log.e(TAG, "API call failed: " + t.getMessage());
            }
        });
    }

    private void checkForNewAlerts(List<PokemonReport> currentReports) {
        List<PokemonReport> newAlerts = new ArrayList<>();

        // Create a unique identifier for each alert
        for (PokemonReport report : currentReports) {
            String alertId = createAlertId(report);

            // If we haven't seen this alert before, it's new
            if (!notifiedAlertIds.contains(alertId)) {
                newAlerts.add(report);
                notifiedAlertIds.add(alertId);
            }
        }

        // Show notifications only for new alerts
        for (PokemonReport newAlert : newAlerts) {
            showNotification(newAlert);
        }

        // Limit the size of the notifiedAlertIds set to prevent memory issues
        // over long periods of time (this is a simple approach)
        if (notifiedAlertIds.size() > 1000) {
            // If we have too many IDs stored, clear older ones
            // This is a very simple approach - in a real app, you might want
            // to use a more sophisticated caching mechanism with expiration
            Set<String> newSet = new HashSet<>();
            for (PokemonReport report : currentReports) {
                newSet.add(createAlertId(report));
            }
            notifiedAlertIds = newSet;
        }
    }

    // Create a unique identifier for a Pokemon alert
    private String createAlertId(PokemonReport report) {
        // Combine key fields to create a unique identifier
        return report.getName() + "_" +
                report.getType() + "_" +
                report.getLatitude() + "_" +
                report.getLongitude() + "_" +
                report.getEndTime();
    }

    private void showNotification(PokemonReport pokemon) {
        // Create an explicit intent for the MainActivity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ?
                                PendingIntent.FLAG_IMMUTABLE : 0));

        NotificationCompat.Builder builder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher_round)
                        .setContentTitle("New Pokémon Alert!")
                        .setContentText("A " + pokemon.getName() + " of type " +
                                pokemon.getType() + " is available!")
                        .setStyle(new NotificationCompat.BigTextStyle().bigText(
                                "Alert: " + pokemon.getName() + "\nType: " + pokemon.getType() +
                                        "\nAvailable until: " + pokemon.getEndTime() +
                                        "\nDescription: " + pokemon.getDescription()))
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(pendingIntent)
                        .setAutoCancel(true)
                        .setColor(getResources().getColor(R.color.colorPrimary));

        NotificationManagerCompat notificationManager =
                NotificationManagerCompat.from(this);
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
