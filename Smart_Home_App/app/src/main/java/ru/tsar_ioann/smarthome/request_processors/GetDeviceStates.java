package ru.tsar_ioann.smarthome.request_processors;

import ru.tsar_ioann.smarthome.UartMessage;

public class GetDeviceStates extends RequestProcessor {
    public interface Listener extends ErrorsListener {
        void onOKResult(/* TODO */);
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
                : UartMessage.COMMAND_GET_DEVICES;

        UartMessage response = sendUartMessage(
                new UartMessage(requestCommand, new byte[0])
        );
        if (response == null) {
            return;  // already handled in RequestProcessor
        }

        // TODO
    }
}
