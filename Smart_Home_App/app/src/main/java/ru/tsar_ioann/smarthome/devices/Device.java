package ru.tsar_ioann.smarthome.devices;

import android.view.View;

import ru.tsar_ioann.smarthome.Client;
import ru.tsar_ioann.smarthome.MainActivity;

public abstract class Device {
    protected static final int DEVICE_UNAVAILABLE_STATE = 0xFF0000FF;

    protected View view = null;

    public abstract void createView(int uuid, String name, MainActivity activity, Client client);
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
