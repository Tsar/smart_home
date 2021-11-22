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
    public static final int DEFAULT_PORT = 80;

    private static final String LOG_TAG = "Http";
    private static final int MAX_RESPONSE_SIZE_FOR_LOGGING = 128;

    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 2500;
    private static final int DEFAULT_READ_TIMEOUT_MS = 2500;
    private static final int PAUSE_BETWEEN_RETRIES_MS = 50;  // helps when connect exception happens immediately

    public static class Response {
        private final int httpCode;
        private final List<Byte> buffer;
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

    public static void asyncRequest(String url, byte[] data, String password, Network network, int attempts, Listener listener) {
        asyncRequest(url, data, password, network, attempts, listener, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public static void asyncRequest(String url, byte[] data, String password, Network network, int attempts, Listener listener, int connectTimeoutMs, int readTimeoutMs) {
        new Thread() {
            @Override
            public void run() {
                try {
                    Response response = request(url, data, password, network, attempts, connectTimeoutMs, readTimeoutMs);
                    if (listener != null) {
                        listener.onResponse(response);
                    }
                } catch (IOException e) {
                    if (listener != null) {
                        listener.onError(e);
                    }
                }
            }
        }.start();
    }

    public static Response request(String url, byte[] data, String password, Network network, int attempts) throws IOException {
        return request(url, data, password, network, attempts, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }

    public static Response request(String url, byte[] data, String password, Network network, int attempts, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        Http.Response response = new Http.Response(0);
        int spentAttempts = 0;
        while (response.getHttpCode() != HttpURLConnection.HTTP_OK && spentAttempts++ < attempts) {
            try {
                response = request(url, data, password, network, connectTimeoutMs, readTimeoutMs);
            } catch (IOException e) {
                if (spentAttempts == attempts) {
                    Log.d(LOG_TAG, "Failed with exception '" + e.getMessage() + "', all " + attempts + " attempts spent");
                    throw e;
                }
                Log.d(LOG_TAG, "Skipping exception '" + e.getMessage() + "' because only " + spentAttempts + " of " + attempts + " attempts spent");
                try {
                    Thread.sleep(PAUSE_BETWEEN_RETRIES_MS);
                } catch (InterruptedException ignored) {
                }
            }
        }
        return response;
    }

    public static Response request(String url, byte[] data, String password, Network network, int connectTimeoutMs, int readTimeoutMs) throws IOException {
        Log.d(LOG_TAG, "Making request to '" + url + "'");
        URL req = new URL(url);
        HttpURLConnection connection;
        if (network != null) {
            connection = (HttpURLConnection)network.openConnection(req);
        } else {
            connection = (HttpURLConnection)req.openConnection();
        }

        connection.setConnectTimeout(connectTimeoutMs);
        connection.setReadTimeout(readTimeoutMs);
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
            Log.d(LOG_TAG, "Request data:  " + new String(data, StandardCharsets.UTF_8));
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

            final byte[] responseData = response.getData();
            if (responseData.length > MAX_RESPONSE_SIZE_FOR_LOGGING) {
                Log.d(LOG_TAG, "Response data too big for logging (" + responseData.length + " bytes)");
            } else {
                String contentType = connection.getHeaderField("Content-Type");
                if (contentType != null && contentType.equals("application/octet-stream")) {
                    Log.d(LOG_TAG, "Response data bytes: " + Utils.bytesToHex(responseData));
                } else {
                    Log.d(LOG_TAG, "Response data: " + response.getDataAsStr());
                }
            }
        } else {
            Log.d(LOG_TAG, "Response code: " + httpCode);
        }

        connection.disconnect();
        return response;
    }
}
