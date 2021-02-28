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

public class Http {
    private static final String LOG_TAG = "Http";

    private static final int CONNECT_TIMEOUT_MS = 300;
    private static final int READ_TIMEOUT_MS = 500;

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

    public static Response doPostRequest(String url, byte[] data, String password) throws Exception {
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

            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "Smart Home App 1.0");
            connection.setRequestProperty("Password", password);

            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            os.write(data, 0, data.length);
            os.close();
            Utils.logDataHex(LOG_TAG, "Request data:  ", data);

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
                Utils.logDataHex(LOG_TAG, "Response data: ", response.getData());
            }

            connection.disconnect();
            return response;
        } catch (SocketTimeoutException e) {
            throw new Exception("Connection timeout");
        } catch (IOException e) {
            throw new Exception("Request to server failed");
        }
    }
}
