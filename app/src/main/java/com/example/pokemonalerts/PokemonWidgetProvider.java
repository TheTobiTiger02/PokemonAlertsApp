package com.example.pokemonalerts;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;
import android.os.Build;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PokemonWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_UPDATE_WIDGET = "com.example.pokemonalerts.ACTION_UPDATE_WIDGET";
    public static final String ACTION_ITEM_CLICK = "com.example.pokemonalerts.ACTION_ITEM_CLICK";
    public static final String EXTRA_ITEM_POSITION = "com.example.pokemonalerts.EXTRA_ITEM_POSITION";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // Update each widget
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    public static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Set up the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_pokemon_alerts);

        // Set up the intent for the RemoteViewsService
        Intent serviceIntent = new Intent(context, PokemonWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        // Using the URI to make the intent unique
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));

        // Set the RemoteViews service
        views.setRemoteAdapter(R.id.widget_list_view, serviceIntent);
        views.setEmptyView(R.id.widget_list_view, R.id.widget_empty_view);

        // Set last updated time
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        String currentTime = sdf.format(new Date());
        views.setTextViewText(R.id.widget_last_updated, "Last updated: " + currentTime);

        // Create intent to launch the main activity when widget title is clicked
        Intent openAppIntent = new Intent(context, MainActivity.class);
        PendingIntent openAppPendingIntent = PendingIntent.getActivity(context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setOnClickPendingIntent(R.id.widget_title, openAppPendingIntent);

        // Set up the intent template for list item clicks
        Intent itemClickIntent = new Intent(context, PokemonWidgetProvider.class);
        itemClickIntent.setAction(ACTION_ITEM_CLICK);
        itemClickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        PendingIntent itemClickPendingIntent = PendingIntent.getBroadcast(context, 0, itemClickIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0));
        views.setPendingIntentTemplate(R.id.widget_list_view, itemClickPendingIntent);

        // Update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);

        if (ACTION_UPDATE_WIDGET.equals(intent.getAction())) {
            // Update all widgets
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(new ComponentName(context, PokemonWidgetProvider.class));

            // First, update the data in the remote adapter
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list_view);

            // Then update each widget UI
            for (int appWidgetId : appWidgetIds) {
                updateAppWidget(context, appWidgetManager, appWidgetId);
            }
        } else if (ACTION_ITEM_CLICK.equals(intent.getAction())) {
            // Handle item click
            int position = intent.getIntExtra(EXTRA_ITEM_POSITION, -1);
            if (position != -1) {
                // Load and open the map activity with the selected Pokemon
                Intent mapIntent = new Intent(context, MapActivity.class);
                PokemonReport pokemon = PokemonWidgetService.getPokemonAtPosition(position);
                if (pokemon != null) {
                    mapIntent.putExtra("pokemon", pokemon);
                    mapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(mapIntent);
                }
            }
        }
    }
}
