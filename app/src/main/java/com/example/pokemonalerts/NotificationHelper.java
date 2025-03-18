package com.example.pokemonalerts;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class NotificationHelper {
    private static final String TAG = "NotificationHelper";
    private static final String CHANNEL_ID = "pokemon_alerts_channel";
    private static final String CHANNEL_NAME = "Pokemon Alerts";
    private static final String CHANNEL_DESC = "Notifications for new Pokemon alerts";
    
    // Store IDs of Pokemon we've already notified about to prevent duplicates
    private static Set<String> notifiedPokemonIds = new HashSet<>();
    
    // Clear notification history (e.g., when user clears all notifications)
    public static void clearNotificationHistory() {
        notifiedPokemonIds.clear();
    }

    // Check if we've already notified about this Pokemon
    public static boolean isAlreadyNotified(String pokemonId) {
        // If we don't have a proper ID, use name+coordinates as a unique identifier
        return notifiedPokemonIds.contains(pokemonId);
    }

    // Mark a Pokemon as notified
    public static void markAsNotified(String pokemonId) {
        notifiedPokemonIds.add(pokemonId);
    }

    // Create notification channel for Android 8.0+
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(CHANNEL_DESC);
            channel.enableVibration(true);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            Log.d(TAG, "Notification channel created");
        }
    }

    // Send a notification for a new Pokemon
    public static void sendPokemonNotification(Context context, PokemonReport pokemon) {
        // Generate a unique ID for this Pokemon
        String pokemonUniqueId = pokemon.getName() + "_" + pokemon.getLatitude() + "_" + pokemon.getLongitude();
        
        // Check if we've already sent a notification for this Pokemon
        if (isAlreadyNotified(pokemonUniqueId)) {
            Log.d(TAG, "Already notified about: " + pokemon.getName());
            return;
        }
        
        // Mark this Pokemon as notified
        markAsNotified(pokemonUniqueId);
        
        // Create intent for when notification is tapped
        Intent intent = new Intent(context, PokemonDetailActivity.class);
        intent.putExtra("pokemon", pokemon);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 
                0, 
                intent, 
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        
        // Set notification sound
        Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        // Get image for notification (keeping it simple - could be enhanced with image loading library)
        Bitmap largeIcon = null;
        try {
            // Basic approach - for production, you might want to use Glide with a callback
            URL url = new URL(pokemon.getImageUrl());
            largeIcon = BitmapFactory.decodeStream(url.openConnection().getInputStream());
        } catch (IOException e) {
            Log.e(TAG, "Error loading image for notification", e);
            // Use fallback image
            largeIcon = BitmapFactory.decodeResource(context.getResources(), R.drawable.placeholder_pokemon);
        }
        
        // Build notification
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(largeIcon)
                .setContentTitle("New Pokémon Alert: " + pokemon.getName())
                .setContentText(pokemon.getType() + " - Available until: " + pokemon.getEndTime())
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(pokemon.getDescription()))
                .setAutoCancel(true)
                .setSound(defaultSoundUri)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent);
        
        // Get notification manager and send notification
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        // Use a unique notification ID for each Pokemon
        int notificationId = pokemonUniqueId.hashCode();
        notificationManager.notify(notificationId, notificationBuilder.build());
        
        Log.d(TAG, "Sent notification for: " + pokemon.getName());
    }
}