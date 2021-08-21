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

    private static final int CONNECT_TIMEOUT_MS = 300;
    private static final int READ_TIMEOUT_MS = 2500;

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

    public static void doAsyncRequest(String url, byte[] data, String password, Network network, int attempts, Listener listener) {
        new Thread() {
            @Override
            public void run() {
                try {
                    Response response = doRequest(url, data, password, network, attempts);
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

    public static Response doRequest(String url, byte[] data, String password, Network network, int attempts) throws IOException {
        Http.Response response = new Http.Response(0);
        int spentAttempts = 0;
        while (response.getHttpCode() != HttpURLConnection.HTTP_OK && spentAttempts++ < attempts) {
            try {
                response = doRequest(url, data, password, network);
            } catch (IOException e) {
                if (spentAttempts == attempts) {
                    throw e;
                }
                Log.d(LOG_TAG, "Skipping exception '" + e.getMessage() + "' because not all attempts were spent");
            }
        }
        return response;
    }

    public static Response doRequest(String url, byte[] data, String password, Network network) throws IOException {
        Log.d(LOG_TAG, "Making request to '" + url + "'");
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
            logData("Request data:  ", data);
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
            logData("Response data: ", response.getData());
        } else {
            Log.d(LOG_TAG, "Response code: " + httpCode);
        }

        connection.disconnect();
        return response;
    }

    private static void logData(String prefix, byte[] data) {
        Log.d(LOG_TAG, prefix + new String(data, StandardCharsets.UTF_8));
    }
}
