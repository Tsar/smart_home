package ru.tsar_ioann.smarthome.devices;

import android.app.Activity;
import android.view.View;

import ru.tsar_ioann.smarthome.NrfMessageSender;

public abstract class Device {
    protected static final int DEVICE_UNAVAILABLE_STATE = 0xFF0000FF;

    protected View view = null;

    public abstract void createView(Activity activity, String name, int uuid, NrfMessageSender nrfMessageSender);
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
