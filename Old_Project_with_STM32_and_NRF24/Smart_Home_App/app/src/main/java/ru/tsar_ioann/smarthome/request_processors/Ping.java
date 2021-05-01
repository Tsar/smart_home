package ru.tsar_ioann.smarthome.request_processors;

import android.util.Log;

import java.util.Random;

import ru.tsar_ioann.smarthome.UartMessage;

public class Ping extends RequestProcessor {
    private static final String LOG_TAG = "Ping";

    private static final byte PING_PAYLOAD_SIZE = 16;

    public interface Listener extends ErrorsListener {
        void onOKResult();
    }

    private Listener listener;

    public Ping(String url, String password, Listener listener) {
        super(url, password, listener);
        this.listener = listener;
    }

    @Override
    public void process() {
        byte[] pingPayload = new byte[PING_PAYLOAD_SIZE];
        new Random().nextBytes(pingPayload);

        UartMessage response = sendUartMessage(
                new UartMessage(UartMessage.COMMAND_PING, pingPayload)
        );
        if (response == null) {
            return;  // already handled in RequestProcessor
        }

        if (verifyPingResponse(response, pingPayload)) {
            listener.onOKResult();
        } else {
            listener.onError("Invalid server response");
        }
    }

    private boolean verifyPingResponse(UartMessage response, byte[] requestPayload) {
        if (response.getCommand() != UartMessage.COMMAND_RESPONSE_PING) {
            Log.d(LOG_TAG, "Bad command in response");
            return false;
        }
        byte[] responsePayload = response.getPayload();
        if (responsePayload.length != requestPayload.length) {
            Log.d(LOG_TAG, "Bad payload length in response");
            return false;
        }
        for (int i = 0; i < requestPayload.length; ++i) {
            if ((byte)(requestPayload[i] ^ 0xff) != responsePayload[i]) {
                Log.d(LOG_TAG, "Bad payload in response");
                return false;
            }
        }
        return true;
    }
}
