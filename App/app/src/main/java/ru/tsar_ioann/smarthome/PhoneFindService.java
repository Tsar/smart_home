package ru.tsar_ioann.smarthome;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

public class PhoneFindService extends FirebaseMessagingService {
    private static final String LOG_TAG = "PhoneFindService";

    private static final String EXPECTED_SENDER = "18674982621";
    private static final String DATA_KEY_RING = "ring";
    private static final String DATA_VALUE_ENABLE = "enable";

    private static final String NOTIFICATION_CHANNEL_ID = "SmartHomePhoneFindChannel";
    public static final int NOTIFICATION_ID = 1;

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(LOG_TAG, "FCM registration token: " + token);

        // TODO: handle token is some way?
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        final String sender = remoteMessage.getFrom();
        if (sender == null) {
            Log.d(LOG_TAG, "Skipping firebase message, because sender is null");
            return;
        }
        if (!sender.equals(EXPECTED_SENDER)) {
            Log.d(LOG_TAG, "Skipping firebase message, because sender is invalid: " + sender);
            return;
        }
        final Map<String, String> data = remoteMessage.getData();
        if (data.isEmpty()) {
            Log.d(LOG_TAG, "Skipping firebase message, because data is empty");
            return;
        }
        if (!data.containsKey(DATA_KEY_RING)) {
            Log.d(LOG_TAG, "Skipping firebase message, because no key '" + DATA_KEY_RING + "' in data");
            return;
        }
        final String ringValue = data.get(DATA_KEY_RING);
        if (ringValue == null) {
            Log.d(LOG_TAG, "Skipping firebase message, because key '" + DATA_KEY_RING + "' is null");
            return;
        }
        if (!ringValue.equals(DATA_VALUE_ENABLE)) {
            Log.d(LOG_TAG, "Skipping firebase message, because key '" + DATA_KEY_RING + "' has unexpected value: " + ringValue);
            return;
        }

        Log.d(LOG_TAG, "Received firebase message 'ring: enable'");
        showNotification();
        startService(new Intent(this, RingingService.class).setAction(RingingService.ACTION_START_RINGING));
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.phone_find_notification_channel_title),
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription(getString(R.string.phone_find_notification_channel_description));
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @SuppressLint("LaunchActivityFromNotification")
    private void showNotification() {
        createNotificationChannel();  // safe to call this repeatedly

        PendingIntent stopRingingIntent = PendingIntent.getService(
                this,
                0,
                new Intent(this, RingingService.class).setAction(RingingService.ACTION_STOP_RINGING),
                0
        );

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.notify(
                NOTIFICATION_ID,
                new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.drawable.phone_find_icon)
                        .setContentTitle(getString(R.string.phone_find_notification_title))
                        .setContentText(getString(R.string.phone_find_notification_text))
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                        .setAutoCancel(true)
                        .setContentIntent(stopRingingIntent)
                        .addAction(
                                R.drawable.device_is_found,
                                getString(R.string.phone_find_notification_action_device_found),
                                stopRingingIntent
                        )
                        .build()
        );
    }
}
