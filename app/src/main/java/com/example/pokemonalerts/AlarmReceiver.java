package com.example.pokemonalerts;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

public class AlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "AlarmReceiver";
    private static final String ACTION_PERIODIC_UPDATE = "com.example.pokemonalerts.PERIODIC_UPDATE";
    private static final long UPDATE_INTERVAL = 30 * 1000; // 30 seconds

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "AlarmReceiver received: " + action);

        if (ACTION_PERIODIC_UPDATE.equals(action)) {
            // Acquire wake lock to ensure update completes
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "PokemonAlerts:PeriodicUpdate"
            );
            wakeLock.acquire(60000); // Hold for max 1 minute

            try {
                // Ensure services are running
                restartServices(context);

                // Schedule next alarm
                scheduleNextAlarm(context);
            } finally {
                if (wakeLock.isHeld()) {
                    wakeLock.release();
                }
            }
        }
    }

    public static void schedulePeriodicUpdates(Context context) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.setAction(ACTION_PERIODIC_UPDATE);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0)
        );

        long triggerTime = System.currentTimeMillis() + UPDATE_INTERVAL;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
            }
            Log.d(TAG, "Scheduled periodic update alarm");
        } catch (SecurityException e) {
            Log.e(TAG, "Failed to schedule exact alarm, falling back to inexact", e);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent);
        }
    }

    private void scheduleNextAlarm(Context context) {
        schedulePeriodicUpdates(context);
    }

    private void restartServices(Context context) {
        try {
            // Start widget update service
            Intent widgetServiceIntent = new Intent(context, WidgetUpdateService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(widgetServiceIntent);
            } else {
                context.startService(widgetServiceIntent);
            }

            // Start foreground notification service
            Intent foregroundServiceIntent = new Intent(context, ForegroundNotificationService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(foregroundServiceIntent);
            } else {
                context.startService(foregroundServiceIntent);
            }

            Log.d(TAG, "Services restarted from alarm");
        } catch (Exception e) {
            Log.e(TAG, "Error restarting services", e);
        }
    }
}