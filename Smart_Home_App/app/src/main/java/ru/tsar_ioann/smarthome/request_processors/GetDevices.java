package ru.tsar_ioann.smarthome.request_processors;

import android.util.Log;

import java.net.HttpURLConnection;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import ru.tsar_ioann.smarthome.DeviceParams;
import ru.tsar_ioann.smarthome.Http;
import ru.tsar_ioann.smarthome.UartMessage;

public class GetDevices extends RequestProcessor {
    private static final String LOG_TAG = "GetDevices";

    public interface Listener {
        void onOKResult(List<DeviceParams> devices);
        void onWrongPassword();
        void onError(String errorText);
    }

    private GetDevices.Listener listener;

    public GetDevices(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void process(String url, String password) {
        try {
            Response result = sendUartMessage(url, password,
                    new UartMessage(UartMessage.COMMAND_GET_DEVICES, new byte[0])
            );
            int httpCode = result.getHttpCode();
            switch (httpCode) {
                case HttpURLConnection.HTTP_OK:
                    parseDevices(result.getResponse());
                    break;
                case HttpURLConnection.HTTP_UNAUTHORIZED:
                    listener.onWrongPassword();
                    break;
                default:
                    listener.onError("Server responded with error code " + httpCode);
                    break;
            }
        } catch (UartMessage.ParseException e) {
            listener.onError("Invalid server response");
        } catch (Http.Exception e) {
            listener.onError(e.getMessage());
        }
    }

    private void parseDevices(UartMessage response) {
        if (response.getCommand() != UartMessage.COMMAND_RESPONSE_GET_DEVICES) {
            Log.d(LOG_TAG, "Bad command in response");
            listener.onError("Invalid server response");
            return;
        }
        byte[] payload = response.getPayload();
        ByteBuffer buffer = ByteBuffer.allocate(payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(payload);
        buffer.position(0);
        List<DeviceParams> result = new ArrayList<>();
        for (int i = 0; i < payload.length / 8; ++i) {
            int nameId = buffer.getInt();
            int uuid = buffer.getInt();
            result.add(new DeviceParams(nameId, uuid));
        }
        listener.onOKResult(result);
    }
}
