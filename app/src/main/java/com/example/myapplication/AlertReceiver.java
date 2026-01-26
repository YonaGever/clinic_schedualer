package com.example.myapplication;

import android.app.*;
import android.content.*;
import android.os.Build;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

public class AlertReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String time = intent.getStringExtra("time");
        String text = intent.getStringExtra("text");

        Toast.makeText(context, "Alarm Fired!", Toast.LENGTH_LONG).show();

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "schedule_alerts";

        // 1. Setup Channel for O+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Schedule Alerts",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Notifications for scheduled appointments");
            nm.createNotificationChannel(channel);
        }

        // 2. Create an Intent to open the app when tapping the notification
        Intent tapIntent = new Intent(context, MainActivity.class);
        tapIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // 3. Build Notification using NotificationCompat for better compatibility
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Upcoming Appointment")
                .setContentText(time + " • " + text)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH) // For pre-Oreo devices
                .setDefaults(NotificationCompat.DEFAULT_ALL)   // Sound/Vibrate
                .setContentIntent(pendingIntent);             // Click action

        // 4. Notify - Using a unique ID based on time and text
        int notificationId = (time + text).hashCode();
        nm.notify(notificationId, builder.build());
    }
}