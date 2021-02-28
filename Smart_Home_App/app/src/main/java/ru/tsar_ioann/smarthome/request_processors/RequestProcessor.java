package ru.tsar_ioann.smarthome.request_processors;

import java.net.HttpURLConnection;

import ru.tsar_ioann.smarthome.Http;
import ru.tsar_ioann.smarthome.UartMessage;

public abstract class RequestProcessor {
    public abstract void process(String url, String password);

    protected class Response {
        private int httpCode;
        private UartMessage response;

        public Response(int httpCode, UartMessage response) {
            this.httpCode = httpCode;
            this.response = response;
        }

        public int getHttpCode() {
            return httpCode;
        }

        public UartMessage getResponse() {
            return response;
        }
    }

    protected Response sendUartMessage(String url, String password, UartMessage request) throws Http.Exception, UartMessage.ParseException {
        Http.Response result = Http.doPostRequest(url, request.serialize(), password);
        int httpCode = result.getHttpCode();
        if (httpCode == HttpURLConnection.HTTP_OK) {
            UartMessage response = new UartMessage(result.getData());
            return new Response(httpCode, response);
        }
        return new Response(httpCode, null);
    }
}
