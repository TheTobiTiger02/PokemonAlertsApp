package com.example.pokemonalerts;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
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
    private static final String WIDGET_SERVICE_CHANNEL_ID = "WidgetServiceChannel";
    private static final int FOREGROUND_NOTIFICATION_ID = 2001;
    private static int notificationId = 0; // Increment for each notification
    private Timer timer;
    private PowerManager.WakeLock wakeLock;

    // Track previously seen alerts to avoid duplicate notifications
    private Set<String> notifiedAlertIds = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        timer = new Timer();

        // Acquire wake lock to prevent service from being killed
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PokemonAlerts:WidgetService"
        );
        wakeLock.acquire();

        createNotificationChannels();

        // Start as foreground service to prevent being killed
        startForeground(FOREGROUND_NOTIFICATION_ID, createServiceNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Cancel existing timer and start new one
        if (timer != null) {
            timer.cancel();
        }
        timer = new Timer();

        // Schedule frequent updates
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateWidgetData();
            }
        }, 0, UPDATE_INTERVAL);

        // Schedule periodic service restart to ensure persistence
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ensureServicePersistence();
            }
        }, 5 * 60 * 1000, 5 * 60 * 1000); // Every 5 minutes

        return START_STICKY;
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for Pokemon alerts
            NotificationChannel alertChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pokemon Alerts Channel",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChannel.setDescription("Channel for Pokemon Alerts notifications");

            // Channel for widget service
            NotificationChannel serviceChannel = new NotificationChannel(
                    WIDGET_SERVICE_CHANNEL_ID,
                    "Widget Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            serviceChannel.setDescription("Channel for Widget Update Service");
            serviceChannel.setSound(null, null);
            serviceChannel.enableVibration(false);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(alertChannel);
            notificationManager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification createServiceNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, WIDGET_SERVICE_CHANNEL_ID)
                .setContentTitle("Pokemon Alerts Widget Service")
                .setContentText("Keeping widgets updated")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
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

                    // First, check for any new alerts that need notifications
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

                        Log.d(TAG, "Widget data updated with " + newPokemonReports.size() + " items");
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

        // Limit the size of the notifiedAlertIds set
        if (notifiedAlertIds.size() > 1000) {
            Set<String> newSet = new HashSet<>();
            for (PokemonReport report : currentReports) {
                newSet.add(createAlertId(report));
            }
            notifiedAlertIds = newSet;
        }
    }

    // Create a unique identifier for a Pokemon alert
    private String createAlertId(PokemonReport report) {
        return report.getName() + "_" +
                report.getType() + "_" +
                report.getLatitude() + "_" +
                report.getLongitude() + "_" +
                report.getEndTime();
    }

    private void showNotification(PokemonReport pokemon) {
        // Check the user's notification preferences
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        Set<String> notifyTypes = prefs.getStringSet("pref_notify_types",
                new HashSet<>(Arrays.asList("All")));
        boolean notifyAll = notifyTypes.contains("All");

        if (!notifyAll && !notifyTypes.contains(pokemon.getType())) {
            Log.d(TAG, "Not notifying for type: " + pokemon.getType());
            return;
        }

        // Create an explicit intent for the PokemonDetailActivity
        Intent intent = new Intent(this, PokemonDetailActivity.class);
        intent.putExtra("pokemon", pokemon);
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

    private void ensureServicePersistence() {
        // Re-schedule alarm receiver to ensure periodic updates continue
        AlarmReceiver.schedulePeriodicUpdates(this);
        Log.d(TAG, "Service persistence check - rescheduled alarms");
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "WidgetUpdateService destroyed - attempting restart");

        if (timer != null) {
            timer.cancel();
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Restart the service
        Intent restartIntent = new Intent(this, WidgetUpdateService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Task removed - ensuring widget service continues");

        // Restart the service when task is removed
        Intent restartIntent = new Intent(this, WidgetUpdateService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }

        super.onTaskRemoved(rootIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}