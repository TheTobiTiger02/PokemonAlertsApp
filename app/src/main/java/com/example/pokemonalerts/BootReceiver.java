package com.example.pokemonalerts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    // Custom action strings for different manufacturers
    private static final String ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON";
    private static final String ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG, "BootReceiver received: " + action);

        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                ACTION_QUICKBOOT_POWERON.equals(action) ||
                ACTION_HTC_QUICKBOOT_POWERON.equals(action) ||
                Intent.ACTION_MY_PACKAGE_REPLACED.equals(action) ||
                Intent.ACTION_PACKAGE_REPLACED.equals(action)) {

            Log.d(TAG, "Boot/restart completed, starting services and alarms");

            try {
                // Start the alarm receiver for periodic updates
                AlarmReceiver.schedulePeriodicUpdates(context);

                // Restart the widget update service
                Intent widgetServiceIntent = new Intent(context, WidgetUpdateService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(widgetServiceIntent);
                } else {
                    context.startService(widgetServiceIntent);
                }

                // Also restart the foreground notification service
                Intent foregroundServiceIntent = new Intent(context, ForegroundNotificationService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(foregroundServiceIntent);
                } else {
                    context.startService(foregroundServiceIntent);
                }

                Log.d(TAG, "All services and alarms restarted successfully");
            } catch (Exception e) {
                Log.e(TAG, "Error starting services after boot", e);
            }
        }
    }
}