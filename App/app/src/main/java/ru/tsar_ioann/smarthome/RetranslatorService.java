package ru.tsar_ioann.smarthome;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class RetranslatorService extends FirebaseMessagingService {
    private static final String LOG_TAG = "RetranslatorService";

    @Override
    public void onNewToken(String token) {
        Log.d(LOG_TAG, "FCM registration token: " + token);

        // TODO: handle token is some way?
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(LOG_TAG, "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.d(LOG_TAG, "Message data payload: " + remoteMessage.getData());
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            Log.d(LOG_TAG, "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }
    }
}
