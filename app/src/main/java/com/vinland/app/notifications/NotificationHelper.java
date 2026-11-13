package com.vinland.app.notifications;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.vinland.app.R;
import com.vinland.app.weather.WeatherActivity;

import java.util.HashMap;
import java.util.Map;

public class NotificationHelper {

    public static final String CHANNEL_ID = "vinland_weather_alerts";
    public static final String CHANNEL_NAME = "Vinland Weather Alerts";
    public static final String CHANNEL_DESC = "Notifications for severe weather and urban garden care advisories in Quezon City.";

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(CHANNEL_DESC);
            channel.enableVibration(true);
            channel.setLockscreenVisibility(android.app.Notification.VISIBILITY_PUBLIC);

            NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public static void saveNotificationToFirestore(String title, String message) {
        saveNotificationToFirestore(title, message, null);
    }

    /** @param category fixed category for the history filter, or null to guess it from the text */
    public static void saveNotificationToFirestore(String title, String message, String category) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            String uid = auth.getCurrentUser().getUid();
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            String id = db.collection("users").document(uid).collection("notifications").document().getId();

            if (category == null) category = resolveCategory(title, message);

            Map<String, Object> notif = new HashMap<>();
            notif.put("id", id);
            notif.put("title", title);
            notif.put("message", message);
            notif.put("category", category);
            notif.put("timestamp", System.currentTimeMillis());

            db.collection("users").document(uid).collection("notifications").document(id)
                    .set(notif);
        }
    }

    public static String resolveCategory(String title, String message) {
        String text = ((title != null ? title : "") + " " + (message != null ? message : "")).toLowerCase();
        if (text.contains("rain") || text.contains("thunderstorm") || text.contains("drizzle") || text.contains("shower") || text.contains("☔")) {
            return "rain";
        }
        if (text.contains("heat") || text.contains("temperature") || text.contains("hot") || text.contains("33°c") || text.contains("34°c") || text.contains("35°c") || text.contains("🌡️")) {
            return "heat";
        }
        if (text.contains("humidity") || text.contains("humid") || text.contains("fungal") || text.contains("damp") || text.contains("🌿")) {
            return "humidity";
        }
        return "general";
    }

    public static void sendNotification(Context context, String title, String message) {
        sendNotification(context, title, message, WeatherActivity.class, null);
    }

    /**
     * @param openOnTap screen opened when the notification is tapped
     * @param category  fixed history category, or null to guess it from the text
     */
    public static void sendNotification(Context context, String title, String message,
                                        Class<?> openOnTap, String category) {
        createNotificationChannel(context);
        saveNotificationToFirestore(title, message, category);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                Log.w("NotificationHelper", "POST_NOTIFICATIONS permission not granted");
                return;
            }
        }

        Intent intent = new Intent(context, openOnTap);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        try {
            int notifId = (int) (System.currentTimeMillis() & 0xfffffff);
            manager.notify(notifId, builder.build());
            Log.d("NotificationHelper", "Notification sent successfully with ID: " + notifId);
        } catch (Exception e) {
            Log.e("NotificationHelper", "Failed to display notification: " + e.getMessage(), e);
        }
    }
}
