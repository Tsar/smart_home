package ru.tsar_ioann.smarthome;

import ru.tsar_ioann.smarthome.request_processors.*;

public class Client {
    private String url;
    private String password;

    private static class RequestThread extends Thread {
        private String url;
        private String password;
        private RequestProcessor requestProcessor;

        public RequestThread(String url, String password, RequestProcessor requestProcessor) {
            this.url = url;
            this.password = password;
            this.requestProcessor = requestProcessor;
        }

        @Override
        public void run() {
            requestProcessor.process(url, password);
        }
    }

    public Client(String serverAddress, int serverPort, String password) {
        url = "http://" + serverAddress + ":" + serverPort + "/uart_message";
        this.password = password;
    }

    public void ping(Ping.Listener listener) {
        new RequestThread(url, password, new Ping(listener)).start();
    }

    public void getDevices(GetDevices.Listener listener) {
        new RequestThread(url, password, new GetDevices(listener)).start();
    }
}
