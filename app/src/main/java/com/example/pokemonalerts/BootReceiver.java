package com.example.pokemonalerts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Log.d(TAG, "Boot completed, starting services");

            // Restart the widget update service after device boot
            context.startService(new Intent(context, WidgetUpdateService.class));

            // Also restart the foreground notification service
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(new Intent(context, ForegroundNotificationService.class));
            } else {
                context.startService(new Intent(context, ForegroundNotificationService.class));
            }
        }
    }
}
