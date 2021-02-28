package ru.tsar_ioann.smarthome.request_processors;

import android.util.Log;

import java.net.HttpURLConnection;
import java.util.Random;

import ru.tsar_ioann.smarthome.Http;
import ru.tsar_ioann.smarthome.UartMessage;

public class Ping extends RequestProcessor {
    private static final String LOG_TAG = "Ping";

    private static final byte PING_PAYLOAD_SIZE = 16;

    public interface Listener {
        void onOKResult();
        void onWrongPassword();
        void onError(String errorText);
    }

    private Listener listener;

    public Ping(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void process(String url, String password) {
        byte[] pingPayload = new byte[PING_PAYLOAD_SIZE];
        new Random().nextBytes(pingPayload);
        try {
            Response result = sendUartMessage(url, password,
                    new UartMessage(UartMessage.COMMAND_PING, pingPayload)
            );
            int httpCode = result.getHttpCode();
            switch (httpCode) {
                case HttpURLConnection.HTTP_OK:
                    if (verifyPingResponse(result.getResponse(), pingPayload)) {
                        listener.onOKResult();
                    } else {
                        listener.onError("Invalid server response");
                    }
                    break;
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    listener.onWrongPassword();
                    break;
                default:
                    listener.onError("Server responded with error code " + httpCode);
                    break;
            }
        } catch (UartMessage.ParseException e) {
            Log.d(LOG_TAG, "Response parsing failed: " + e.getMessage());
            listener.onError("Invalid server response");
        } catch (Http.Exception e) {
            listener.onError(e.getMessage());
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
