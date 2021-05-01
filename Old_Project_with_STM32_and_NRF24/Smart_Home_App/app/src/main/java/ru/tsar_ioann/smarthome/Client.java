package ru.tsar_ioann.smarthome;

import android.widget.TextView;

import ru.tsar_ioann.smarthome.request_processors.*;

public class Client {
    private String url;
    private String password;
    private TextView txtStatus;

    private static class RequestThread extends Thread {
        private RequestProcessor requestProcessor;

        public RequestThread(RequestProcessor requestProcessor) {
            this.requestProcessor = requestProcessor;
        }

        @Override
        public void run() {
            requestProcessor.process();
        }
    }

    public Client(String serverAddress, int serverPort, String password, TextView txtStatus) {
        url = "http://" + serverAddress + ":" + serverPort + "/uart_message";
        this.password = password;
        this.txtStatus = txtStatus;
    }

    public void ping(Ping.Listener listener) {
        new RequestThread(new Ping(url, password, listener)).start();
    }

    public void getDevices(GetDevices.Listener listener, DeviceNamesCache deviceNamesCache) {
        txtStatus.setText("Getting devices info...");
        new RequestThread(new GetDevices(url, password, listener, deviceNamesCache)).start();
    }

    public void getDeviceStates(GetDeviceStates.Listener listener, boolean update) {
        txtStatus.setText(update
                ? "Getting current devices states..."
                : "Getting cached device states..."
        );
        new RequestThread(new GetDeviceStates(url, password, listener, update)).start();
    }

    public void sendNrfMessage(SendNrfMessage.Listener listener, NrfMessage message) {
        new RequestThread(new SendNrfMessage(url, password, listener, message)).start();
    }
}
