package ru.tsar_ioann.smarthome;

import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;

public class RingingService extends Service {
    private static final String LOG_TAG = "RingingService";

    public static final String SETTINGS_NAME = "phone_find";
    public static final String SETTINGS_KEY_SOUND = "sound_id";
    public static final int SETTINGS_VALUE_SOUND_NASTY = 0;
    public static final int SETTINGS_VALUE_SOUND_MI_FIT = 1;

    public static final String ACTION_START_RINGING = "START_RINGING";
    public static final String ACTION_STOP_RINGING = "STOP_RINGING";

    private int originalVolume;
    private MediaPlayer mediaPlayer;
    PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            if (intent.getAction().equals(ACTION_START_RINGING)) {
                startRinging();
            } else if (intent.getAction().equals(ACTION_STOP_RINGING)) {
                stopRinging();
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                notificationManager.cancel(PhoneFindService.NOTIFICATION_ID);
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    private void startRinging() {
        // It's important to play sound on STREAM_ALARM: if any headphones are connected,
        // then both phone speakers and headphones will be used

        PowerManager powerManager = getSystemService(PowerManager.class);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "smarthome:ring");
        wakeLock.acquire(60 * 1000L);  // timeout is 1 minute

        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        originalVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM);
        int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0);
        Log.d(LOG_TAG, "Volume for STREAM_ALARM set to " + maxVolume + " (was " + originalVolume + ")");

        final int soundId = getSharedPreferences(SETTINGS_NAME, MODE_PRIVATE).getInt(SETTINGS_KEY_SOUND, 0);
        int soundResourceId = R.raw.findphone1;
        switch (soundId) {
            case SETTINGS_VALUE_SOUND_NASTY:
                soundResourceId = R.raw.findphone1;
                break;
            case SETTINGS_VALUE_SOUND_MI_FIT:
                soundResourceId = R.raw.findphone2;
                break;
        }

        mediaPlayer = MediaPlayer.create(
                this,
                soundResourceId,
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build(),
                audioManager.generateAudioSessionId()
        );

        mediaPlayer.setOnCompletionListener(mp -> {
            Log.d(LOG_TAG, "Media player has finished playing");
            finish();
        });

        Log.d(LOG_TAG, "Starting media player");
        mediaPlayer.start();
    }

    private void stopRinging() {
        Log.d(LOG_TAG, "Received stop ringing intent");
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            Log.d(LOG_TAG, "Stopping media player");
            mediaPlayer.stop();
            finish();
        }
    }

    private void finish() {
        mediaPlayer.reset();
        mediaPlayer.release();
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, originalVolume, 0);
        Log.d(LOG_TAG, "Restored volume for STREAM_ALARM back to " + originalVolume);
        wakeLock.release();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
