package ru.tsar_ioann.smarthome;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class Http {
    private static final String LOG_TAG = "Http";

    private static final int CONNECT_TIMEOUT_MS = 300;
    private static final int READ_TIMEOUT_MS = 2500;

    public static class Exception extends java.lang.Exception {
        public Exception(String errorMessage) {
            super(errorMessage);
        }
    }

    public static class Response {
        private int httpCode;
        private List<Byte> data;

        public Response(int httpCode) {
            this.httpCode = httpCode;
            this.data = new ArrayList<>();
        }

        public void appendData(byte[] bytes, int len) {
            for (int i = 0; i < len; ++i) {
                data.add(bytes[i]);
            }
        }

        public int getHttpCode() {
            return httpCode;
        }

        public byte[] getData() {
            byte[] result = new byte[data.size()];
            for (int i = 0; i < data.size(); ++i) {
                result[i] = data.get(i);
            }
            return result;
        }
    }

    public static Response doRequest(String url, byte[] data, String password, boolean hexLogs) throws Exception {
        URL req;
        try {
            req = new URL(url);
        } catch (MalformedURLException e) {
            throw new Exception("Malformed URL: " + e.getMessage());
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) req.openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setUseCaches(false);

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

            connection.setRequestProperty("User-Agent", "Smart Home App 1.0");
            if (password != null) {
                connection.setRequestProperty("Password", password);
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
        } catch (SocketTimeoutException e) {
            throw new Exception("Timeout: " + e.getMessage());
        } catch (IOException e) {
            throw new Exception("Request to server failed: " + e.getMessage());
        }
    }

    private static void logData(String prefix, byte[] data, boolean asHex) {
        Log.d(LOG_TAG, prefix + (asHex
                ? Utils.bytesToHex(data)
                : new String(data, StandardCharsets.UTF_8)
        ));
    }
}
