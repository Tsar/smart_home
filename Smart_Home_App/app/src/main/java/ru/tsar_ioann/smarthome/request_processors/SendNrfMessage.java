package ru.tsar_ioann.smarthome.request_processors;

import android.util.Log;

import ru.tsar_ioann.smarthome.NrfMessage;
import ru.tsar_ioann.smarthome.UartMessage;

public class SendNrfMessage extends RequestProcessor {
    private static final String LOG_TAG = "SendNrfMessage";

    public interface Listener extends ErrorsListener {
        void onOKResult(NrfMessage response);
        void onSendFailed();
    }

    private Listener listener;
    private NrfMessage nrfMessage;

    public SendNrfMessage(String url, String password, Listener listener, NrfMessage nrfMessage) {
        super(url, password, listener);
        this.listener = listener;
        this.nrfMessage = nrfMessage;
    }

    @Override
    public void process() {
        UartMessage response = sendUartMessage(
                new UartMessage(UartMessage.COMMAND_SEND_NRF_MESSAGE, nrfMessage.serialize())
        );
        if (response == null) {
            return;  // already handled in RequestProcessor
        }

        switch (response.getCommand()) {
            case UartMessage.COMMAND_RESPONSE_SEND_NRF_MESSAGE:
                try {
                    listener.onOKResult(new NrfMessage(response.getPayload()));
                } catch (NrfMessage.ParseException e) {
                    Log.d(LOG_TAG, "Bad NRF message in response: " + e.getMessage());
                    listener.onError("Bad NRF message in response: " + e.getMessage());
                }
                break;
            case UartMessage.COMMAND_RESPONSE_SEND_NRF_MESSAGE_FAILED:
                Log.d(LOG_TAG, "Failed to send NRF message");
                listener.onSendFailed();
                break;
            default:
                Log.d(LOG_TAG, "Bad command in response");
                listener.onError("Invalid server response");
                break;
        }
    }
}
