package com.example.pokemonalerts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;
import android.widget.RemoteViews;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ForegroundNotificationService extends Service {
    private static final String TAG = "ForegroundNotifService";
    private static final String CHANNEL_ID = "pokemon_alerts_foreground_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long UPDATE_INTERVAL = 30 * 1000; // 30 seconds

    private Timer timer;
    private List<PokemonReport> currentAlerts;
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "ForegroundNotificationService created");

        // Acquire wake lock to prevent service from being killed
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "PokemonAlerts:ForegroundService"
        );
        wakeLock.acquire();

        createNotificationChannel();
        notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        timer = new Timer();

        // Initial notification with loading state
        startForeground(NOTIFICATION_ID, createNotification("Loading alerts..."));

        // Schedule regular updates with more aggressive timing
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                fetchAlerts();
            }
        }, 0, UPDATE_INTERVAL);

        // Schedule periodic service restart to ensure persistence
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                ensureServicePersistence();
            }
        }, 5 * 60 * 1000, 5 * 60 * 1000); // Every 5 minutes
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Pokemon Alerts Foreground Service",
                    NotificationManager.IMPORTANCE_LOW); // Low importance for persistent notification
            channel.setDescription("Shows current Pokemon alerts");
            channel.setShowBadge(false);
            channel.setSound(null, null); // No sound for persistent notification
            channel.enableVibration(false);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created");
        }
    }

    private Notification createNotification(String message) {
        // Create intent for opening the main activity
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        // Create refresh intent
        Intent refreshIntent = new Intent(this, ForegroundNotificationService.class);
        refreshIntent.setAction("REFRESH_ALERTS");
        PendingIntent refreshPendingIntent = PendingIntent.getService(
                this, 0, refreshIntent, PendingIntent.FLAG_IMMUTABLE);

        // Get current time for last updated
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(new Date());

        // Create the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("Pokémon Alerts Active")
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_refresh, "Refresh", refreshPendingIntent)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);

        // If we have alerts, show the count and first few alerts
        if (currentAlerts != null && !currentAlerts.isEmpty()) {
            StringBuilder alertText = new StringBuilder();
            alertText.append("Active alerts (").append(currentAlerts.size()).append("): ");

            // Show first 3 alerts max
            int displayCount = Math.min(currentAlerts.size(), 3);
            for (int i = 0; i < displayCount; i++) {
                PokemonReport report = currentAlerts.get(i);
                alertText.append("\n• ").append(report.getName())
                        .append(" (").append(report.getType()).append(")");
            }

            if (currentAlerts.size() > 3) {
                alertText.append("\n• ... and ").append(currentAlerts.size() - 3).append(" more");
            }

            alertText.append("\n\nLast updated: ").append(currentTime);

            builder.setContentText(alertText.toString())
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(alertText.toString()));
        }

        return builder.build();
    }

    private void fetchAlerts() {
        Log.d(TAG, "Fetching alerts for notification");
        PokemonApiService apiService = ApiClient.getClient().create(PokemonApiService.class);
        Call<List<PokemonReport>> call = apiService.getPokemonReports();

        call.enqueue(new Callback<List<PokemonReport>>() {
            @Override
            public void onResponse(Call<List<PokemonReport>> call, Response<List<PokemonReport>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    currentAlerts = response.body();
                    Log.d(TAG, "Fetched " + currentAlerts.size() + " alerts for notification");
                    updateNotification();
                } else {
                    Log.e(TAG, "Failed to fetch alerts: " + response.code());
                    notificationManager.notify(NOTIFICATION_ID, createNotification(
                            "Error loading alerts. Code: " + response.code()));
                }
            }

            @Override
            public void onFailure(Call<List<PokemonReport>> call, Throwable t) {
                Log.e(TAG, "Error fetching alerts: " + t.getMessage());
                notificationManager.notify(NOTIFICATION_ID, createNotification(
                        "Error loading alerts: " + t.getMessage()));
            }
        });
    }

    private void updateNotification() {
        if (currentAlerts == null || currentAlerts.isEmpty()) {
            notificationManager.notify(NOTIFICATION_ID,
                    createNotification("No active Pokémon alerts"));
            return;
        }

        notificationManager.notify(NOTIFICATION_ID, createNotification(""));
    }

    private void ensureServicePersistence() {
        // Re-schedule alarm receiver to ensure periodic updates continue
        AlarmReceiver.schedulePeriodicUpdates(this);

        // Update notification to show service is still alive
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(new Date());
        Log.d(TAG, "Service persistence check at " + currentTime);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        if (intent != null && "REFRESH_ALERTS".equals(intent.getAction())) {
            Log.d(TAG, "Manual refresh requested");
            notificationManager.notify(NOTIFICATION_ID,
                    createNotification("Refreshing alerts..."));
            fetchAlerts();
        }

        // Return START_STICKY to ensure service is restarted if killed
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service destroyed - attempting restart");

        if (timer != null) {
            timer.cancel();
            timer = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        // Restart the service
        Intent restartIntent = new Intent(this, ForegroundNotificationService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(restartIntent);
        } else {
            startService(restartIntent);
        }

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "Task removed - ensuring service continues");

        // Restart the service when task is removed
        Intent restartIntent = new Intent(this, ForegroundNotificationService.class);
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