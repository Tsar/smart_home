package ru.tsar_ioann.smarthome.devices;

import android.content.Context;
import android.view.View;

import ru.tsar_ioann.smarthome.NrfMessageSender;

public abstract class Device {
    protected static final int DEVICE_UNAVAILABLE_STATE = 0xFF0000FF;

    protected View view = null;
    protected NrfMessageSender nrfMessageSender = null;

    public abstract void createView(Context context, String name, NrfMessageSender nrfMessageSender);
    public abstract void setCurrentState(int state);

    public View getView() {
        return view;
    }

    public void setUnavailable() {
        if (view != null) {
            view.setEnabled(false);
        }
    }
}
