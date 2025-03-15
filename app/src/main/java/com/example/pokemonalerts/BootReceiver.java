package com.example.pokemonalerts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            // Restart the widget update service after device boot
            context.startService(new Intent(context, WidgetUpdateService.class));
        }
    }
}
