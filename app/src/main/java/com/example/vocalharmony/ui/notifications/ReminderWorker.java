package com.example.vocalharmony.ui.notifications;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.vocalharmony.MainActivity;
import com.example.vocalharmony.R;

import java.util.Random;

public class ReminderWorker extends Worker {

    private static final String TAG = "ReminderWorker";
    private final Context context;

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.context = context;
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Work triggered. Preparing to show notification.");

        // Get a random reminder message from the string array
        String notificationText;
        try {
            Resources res = context.getResources();
            String[] messages = res.getStringArray(R.array.daily_reminder_messages);
            notificationText = messages[new Random().nextInt(messages.length)];
        } catch (Exception e) {
            Log.e(TAG, "Could not get random message, using default.", e);
            notificationText = "Time for your daily practice!";
        }

        // Create an intent to open the app when the notification is tapped
        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        // Build the notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "VocalHarmonyReminderChannel")
                .setSmallIcon(R.drawable.ic_mic_on) // IMPORTANT: Replace with your actual notification icon
                .setContentTitle("Vocal Harmony Reminder")
                .setContentText(notificationText)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent) // Set the intent that will fire when the user taps the notification
                .setAutoCancel(true); // Automatically removes the notification when the user taps it

        // Show the notification
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        // Check for permission before notifying. This check is crucial on API 33+.
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Cannot post notification, permission not granted.");
            // The worker can't ask for permission, so we just fail silently here.
            // The permission should be requested from the Fragment.
            return Result.failure();
        }

        notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        Log.d(TAG, "Notification posted.");

        return Result.success();
    }
}