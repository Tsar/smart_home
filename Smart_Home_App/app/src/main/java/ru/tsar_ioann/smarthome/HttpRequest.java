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

public class HttpRequest {
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 300;
    private static final int DEFAULT_READ_TIMEOUT_MS = 500;

    public static class HttpRequestException extends Exception {
        public HttpRequestException(String errorMessage) {
            super(errorMessage);
        }
    }

    public static class HttpResponse {
        private int httpCode;
        private List<Byte> data;

        public HttpResponse(int httpCode) {
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

        public List<Byte> getData() {
            return data;
        }
    }

    public static HttpResponse doPostRequest(String url, byte[] data, String password, int connectTimeoutMs, int readTimeoutMs) throws HttpRequestException {
        URL req;
        try {
            req = new URL(url);
        } catch (MalformedURLException e) {
            throw new HttpRequestException("Malformed URL: " + e.getMessage());
        }
        try {
            HttpURLConnection connection = (HttpURLConnection) req.openConnection();
            connection.setConnectTimeout(connectTimeoutMs);
            connection.setReadTimeout(readTimeoutMs);
            connection.setUseCaches(false);

            connection.setRequestMethod("POST");
            connection.setRequestProperty("User-Agent", "Smart Home App 1.0");
            connection.setRequestProperty("Password", password);

            connection.setDoOutput(true);
            OutputStream os = connection.getOutputStream();
            os.write(data, 0, data.length);
            os.close();

            int httpCode = connection.getResponseCode();
            HttpResponse response = new HttpResponse(httpCode);
            if (httpCode == HttpURLConnection.HTTP_OK) {
                InputStream is = connection.getInputStream();
                int n;
                byte[] buffer = new byte[1024];
                while ((n = is.read(buffer)) != -1) {
                    response.appendData(buffer, n);
                }
                is.close();
            }

            connection.disconnect();
            return response;
        } catch (SocketTimeoutException e) {
            throw new HttpRequestException("Connection timeout");
        } catch (IOException e) {
            throw new HttpRequestException("Request to server failed");
        }
    }

    public static HttpResponse doPostRequest(String url, byte[] data, String password) throws HttpRequestException {
        return doPostRequest(url, data, password, DEFAULT_CONNECT_TIMEOUT_MS, DEFAULT_READ_TIMEOUT_MS);
    }
}
