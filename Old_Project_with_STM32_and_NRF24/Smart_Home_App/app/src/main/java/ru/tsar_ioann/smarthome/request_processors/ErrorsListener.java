package ru.tsar_ioann.smarthome.request_processors;

public interface ErrorsListener {
    void onWrongPassword();
    void onError(String errorText);
}
