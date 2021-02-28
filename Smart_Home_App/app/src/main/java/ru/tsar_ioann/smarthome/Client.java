package ru.tsar_ioann.smarthome;

import ru.tsar_ioann.smarthome.request_processors.*;

public class Client {
    private String url;
    private String password;

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

    public Client(String serverAddress, int serverPort, String password) {
        url = "http://" + serverAddress + ":" + serverPort + "/uart_message";
        this.password = password;
    }

    public void ping(Ping.Listener listener) {
        new RequestThread(new Ping(url, password, listener)).start();
    }

    public void getDevices(GetDevices.Listener listener, DeviceNamesCache deviceNamesCache) {
        new RequestThread(new GetDevices(url, password, listener, deviceNamesCache)).start();
    }

    public void getDeviceStates(GetDeviceStates.Listener listener, boolean update) {
        new RequestThread(new GetDeviceStates(url, password, listener, update)).start();
    }
}
