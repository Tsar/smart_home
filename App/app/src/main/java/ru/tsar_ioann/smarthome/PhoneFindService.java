package ru.tsar_ioann.smarthome;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.util.Log;

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
    private static final int NOTIFICATION_ID = 1;

    private int originalVolume;
    private MediaPlayer mediaPlayer;

    @Override
    public void onNewToken(String token) {
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
        startRinging();
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

    private void showNotification() {
        createNotificationChannel();  // safe to call this repeatedly

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
                        .setContentIntent(PendingIntent.getActivity(this, 0, new Intent(), 0))
                        .build()
        );
    }

    private void startRinging() {
        // It's important to play sound on STREAM_ALARM: if any headphones are connected,
        // then both phone speakers and headphones will be used

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
        Log.d(LOG_TAG, "Set volume for STREAM_ALARM to " + maxVolume + " (was " + originalVolume + ")");

        mediaPlayer = MediaPlayer.create(
                this,
                R.raw.alarm,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                audioManager.generateAudioSessionId()
        );
        //mediaPlayer.setLooping(true);
        Log.d(LOG_TAG, "Starting media player");
        mediaPlayer.start();
        mediaPlayer.setOnCompletionListener(mp -> {
            mediaPlayer.reset();
            mediaPlayer.release();
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0);
            Log.d(LOG_TAG, "Restore volume for STREAM_ALARM back to " + originalVolume);
        });
    }

    /*
    private void stopRinging() {
        try {
            if (mediaPlayer != null && mediaPlayer.isPlaying()) {
                Log.d(LOG_TAG, "Stopping media player");
                mediaPlayer.stop();
                mediaPlayer.reset();
                mediaPlayer.release();
                AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0);
                Log.d(LOG_TAG, "Restore volume for STREAM_ALARM back to " + originalVolume);
            }
        } catch (Exception e) {
            Log.d(LOG_TAG, "Tried to stop media player, but caught exception: " + e.getMessage());
        }
    }
    */
}
