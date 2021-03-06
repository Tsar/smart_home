package ru.tsar_ioann.smarthome;

import ru.tsar_ioann.smarthome.request_processors.SendNrfMessage;

public interface NrfMessageSender {
    void sendNrfMessage(SendNrfMessage.Listener listener, NrfMessage message);
}
