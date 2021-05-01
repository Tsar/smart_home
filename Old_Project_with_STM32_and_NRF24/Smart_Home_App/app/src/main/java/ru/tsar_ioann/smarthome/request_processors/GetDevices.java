package ru.tsar_ioann.smarthome.request_processors;

import android.util.Log;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ru.tsar_ioann.smarthome.DeviceNamesCache;
import ru.tsar_ioann.smarthome.DeviceParams;
import ru.tsar_ioann.smarthome.UartMessage;
import ru.tsar_ioann.smarthome.Utils;

public class GetDevices extends RequestProcessor {
    private static final String LOG_TAG = "GetDevices";

    public interface Listener extends ErrorsListener {
        void onOKResult(List<DeviceParams> devices, Map<Integer, String> deviceNames);
    }

    private Listener listener;
    private DeviceNamesCache deviceNamesCache;

    public GetDevices(String url, String password, Listener listener, DeviceNamesCache deviceNamesCache) {
        super(url, password, listener);
        this.listener = listener;
        this.deviceNamesCache = deviceNamesCache;
    }

    @Override
    public void process() {
        UartMessage response = sendUartMessage(
                new UartMessage(UartMessage.COMMAND_GET_DEVICES, new byte[0])
        );
        if (response == null) {
            return;  // already handled in RequestProcessor
        }

        if (response.getCommand() != UartMessage.COMMAND_RESPONSE_GET_DEVICES) {
            Log.d(LOG_TAG, "Bad command in response");
            listener.onError("Invalid server response");
            return;
        }
        byte[] payload = response.getPayload();
        ByteBuffer buffer = Utils.createByteBuffer(payload);
        List<DeviceParams> result = new ArrayList<>();
        for (int i = 0; i < payload.length / 8; ++i) {
            int nameId = buffer.getInt();
            int uuid = buffer.getInt();
            result.add(new DeviceParams(nameId, uuid));
        }
        listener.onOKResult(result, deviceNamesCache.getDeviceNames(result));
    }
}
