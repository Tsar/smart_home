package ru.tsar_ioann.smarthome.request_processors;

import android.util.Log;

import java.net.HttpURLConnection;

import ru.tsar_ioann.smarthome.Http;
import ru.tsar_ioann.smarthome.UartMessage;

public abstract class RequestProcessor {
    private static final String LOG_TAG = "RequestProcessor";

    private String url;
    private String password;
    private ErrorsListener errorsListener;

    protected RequestProcessor(String url, String password, ErrorsListener errorsListener) {
        this.url = url;
        this.password = password;
        this.errorsListener = errorsListener;
    }

    public abstract void process();

    protected UartMessage sendUartMessage(UartMessage request) {
        try {
            Http.Response result = Http.doPostRequest(url, request.serialize(), password);
            int httpCode = result.getHttpCode();
            switch (httpCode) {
                case HttpURLConnection.HTTP_OK:
                    try {
                        return new UartMessage(result.getData());
                    } catch (UartMessage.ParseException e) {
                        Log.d(LOG_TAG, "Response parsing failed: " + e.getMessage());
                        errorsListener.onError("Invalid server response");
                    }
                    break;
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    Log.d(LOG_TAG, "Wrong password");
                    errorsListener.onWrongPassword();
                    break;
                default:
                    Log.d(LOG_TAG, "Unexpected response code " + httpCode);
                    errorsListener.onError("Server responded with error code " + httpCode);
                    break;
            }
        } catch (Http.Exception e) {
            Log.d(LOG_TAG, "HTTP request failed: " + e.getMessage());
            errorsListener.onError(e.getMessage());
        }
        return null;
    }
}
