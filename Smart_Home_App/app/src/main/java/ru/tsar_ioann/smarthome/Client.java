package ru.tsar_ioann.smarthome;

public class Client {
    private String url;
    private String password;

    public interface PingListener {
        void onOKResult(int status);
        void onError(String errorText);
    }

    private static class RequestThread extends Thread {
        private String url;
        private byte[] data;
        private String password;
        private PingListener listener;

        public RequestThread(String url, byte[] data, String password, PingListener listener) {
            this.url = url;
            this.data = data;
            this.password = password;
            this.listener = listener;
        }

        @Override
        public void run() {
            try {
                HttpRequest.HttpResponse result = HttpRequest.doPostRequest(url, data, password);
                listener.onOKResult(result.getHttpCode());
            } catch (HttpRequest.HttpRequestException e) {
                listener.onError(e.getMessage());
            }
        }
    }

    public Client(String serverAddress, int serverPort, String password) {
        url = "http://" + serverAddress + ":" + serverPort + "/uart_message";
        this.password = password;
    }

    public void ping(PingListener listener) {
        byte[] request = {(byte)0xCE, (byte)0xBF, (byte)0x01, (byte)0x02, (byte)0xFF, (byte)0xAA};
        new RequestThread(url, request, password, listener).start();
    }
}
