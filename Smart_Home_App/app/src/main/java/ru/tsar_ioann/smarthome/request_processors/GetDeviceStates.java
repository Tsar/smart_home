package ru.tsar_ioann.smarthome.request_processors;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import ru.tsar_ioann.smarthome.UartMessage;
import ru.tsar_ioann.smarthome.Utils;

public class GetDeviceStates extends RequestProcessor {
    private static final String LOG_TAG = "GetDeviceStates";

    public interface Listener extends ErrorsListener {
        void onOKResult(List<Integer> deviceStates);
    }

    private Listener listener;
    private boolean update;

    public GetDeviceStates(String url, String password, Listener listener, boolean update) {
        super(url, password, listener);
        this.listener = listener;
        this.update = update;
    }

    @Override
    public void process() {
        byte requestCommand = update
                ? UartMessage.COMMAND_UPDATE_DEVICE_STATES
                : UartMessage.COMMAND_GET_DEVICE_STATES;

        UartMessage response = sendUartMessage(
                new UartMessage(requestCommand, new byte[0])
        );
        if (response == null) {
            return;  // already handled in RequestProcessor
        }

        byte expectedResponseCommand = update
                ? UartMessage.COMMAND_RESPONSE_UPDATE_DEVICE_STATES
                : UartMessage.COMMAND_RESPONSE_GET_DEVICE_STATES;
        if (response.getCommand() != expectedResponseCommand) {
            Log.d(LOG_TAG, "Bad command in response");
            listener.onError("Invalid server response");
            return;
        }
        byte[] payload = response.getPayload();
        ByteBuffer buffer = Utils.createByteBuffer(payload);
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < payload.length / 4; ++i) {
            result.add(buffer.getInt());
        }
        listener.onOKResult(result);
    }
}
