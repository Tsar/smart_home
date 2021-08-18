package ru.tsar_ioann.smarthome;

import android.net.Network;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Http {
    private static final String LOG_TAG = "Http";

    private static final int CONNECT_TIMEOUT_MS = 500;
    private static final int READ_TIMEOUT_MS = 2500;

    public static class Response {
        private int httpCode;
        private List<Byte> buffer;
        private byte[] data = null;

        public Response(int httpCode) {
            this.httpCode = httpCode;
            this.buffer = new ArrayList<>();
        }

        public void appendData(byte[] bytes, int len) {
            for (int i = 0; i < len; ++i) {
                buffer.add(bytes[i]);
            }
        }

        public int getHttpCode() {
            return httpCode;
        }

        public byte[] getData() {
            if (data == null) {
                data = new byte[buffer.size()];
                for (int i = 0; i < buffer.size(); ++i) {
                    data[i] = buffer.get(i);
                }
            }
            return data;
        }

        public String getDataAsStr() {
            return new String(getData(), StandardCharsets.UTF_8);
        }
    }

    public interface Listener {
        void onResponse(Response response);
        void onError(IOException exception);
    }

    public static void doAsyncRequest(String url, byte[] data, String password, boolean hexLogs, Listener listener) {
        doAsyncRequest(url, data, password, hexLogs, null, listener);
    }

    public static void doAsyncRequest(String url, byte[] data, String password, boolean hexLogs, Network network, Listener listener) {
        new Thread() {
            @Override
            public void run() {
                try {
                    Response response = doRequest(url, data, password, hexLogs, network);
                    listener.onResponse(response);
                } catch (IOException e) {
                    listener.onError(e);
                }
            }
        }.start();
    }

    public static Response doRequest(String url, byte[] data, String password, boolean hexLogs) throws IOException {
        return doRequest(url, data, password, hexLogs, null);
    }

    public static Response doRequest(String url, byte[] data, String password, boolean hexLogs, Network network) throws IOException {
        URL req = new URL(url);
        HttpURLConnection connection;
        if (network != null) {
            connection = (HttpURLConnection)network.openConnection(req);
        } else {
            connection = (HttpURLConnection)req.openConnection();
        }

        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setUseCaches(false);

        if (password != null) {
            connection.setRequestProperty("Password", password);
        }

        if (data != null) {
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            os.write(data, 0, data.length);
            os.close();
            logData("Request data:  ", data, hexLogs);
        } else {
            connection.setRequestMethod("GET");
        }

        int httpCode = connection.getResponseCode();
        Response response = new Response(httpCode);
        if (httpCode == HttpURLConnection.HTTP_OK) {
            InputStream is = connection.getInputStream();
            int n;
            byte[] buffer = new byte[1024];
            while ((n = is.read(buffer)) != -1) {
                response.appendData(buffer, n);
            }
            is.close();
            logData("Response data: ", response.getData(), hexLogs);
        }

        connection.disconnect();
        return response;
    }

    private static void logData(String prefix, byte[] data, boolean asHex) {
        Log.d(LOG_TAG, prefix + (asHex
                ? Utils.bytesToHex(data)
                : new String(data, StandardCharsets.UTF_8)
        ));
    }
}
