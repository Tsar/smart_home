package ru.tsar_ioann.smarthome;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Client {
    private static final int CONNECT_TIMEOUT = 3000;
    private static final int READ_TIMEOUT = 5000;

    private String url;
    private String password;

    public interface PingListener {
        void onOKResult(int status);
        void onError(String errorText);
    }

    private static class PostRequestException extends Exception {
        public PostRequestException(String errorMessage) {
            super(errorMessage);
        }
    }

    private static class PostResponse {
        private int status;  // http code
        private List<Byte> data;

        public PostResponse(int status) {
            this.status = status;
            this.data = new ArrayList<>();
        }

        public void appendData(byte[] bytes, int len) {
            for (int i = 0; i < len; ++i) {
                data.add(bytes[i]);
            }
        }

        public int getStatus() {
            return status;
        }
    }

    private static PostResponse doPostRequest(String url, byte[] data, String password) throws PostRequestException {
        URL req;
        try {
            req = new URL(url);
        } catch (MalformedURLException e) {
            throw new PostRequestException("Malformed URL: " + e.getMessage());
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) req.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT);
            connection.setReadTimeout(READ_TIMEOUT);
            connection.setUseCaches(false);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "Smart Home App 1.0");
            connection.setRequestProperty("Password", password);
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            os.write(data, 0, data.length);
            os.close();
            int status = connection.getResponseCode();
            PostResponse response = new PostResponse(status);
            if (status == HttpURLConnection.HTTP_OK) {
                InputStream is = connection.getInputStream();
                int n;
                byte[] buffer = new byte[1024];
                while ((n = is.read(buffer)) != -1) {
                    response.appendData(buffer, n);
                }
                is.close();
                connection.disconnect();
                return response;
            } else if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                return response;
            } else {
                throw new PostRequestException("Invalid response code " + status);
            }
        } catch (SocketTimeoutException e) {
            throw new PostRequestException("Connection timeout");
        } catch (IOException e) {
            throw new PostRequestException("Request to server failed: " + e.getMessage());
        }
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
                PostResponse result = doPostRequest(url, data, password);
                listener.onOKResult(result.getStatus());
            } catch (PostRequestException e) {
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
