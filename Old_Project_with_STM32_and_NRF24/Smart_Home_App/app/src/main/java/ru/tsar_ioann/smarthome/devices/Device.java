package ru.tsar_ioann.smarthome.devices;

import android.view.View;

import ru.tsar_ioann.smarthome.Client;
import ru.tsar_ioann.smarthome.MainActivity;

public abstract class Device {
    protected static final int DEVICE_UNAVAILABLE_STATE = 0xFF0000FF;

    protected int uuid;
    protected MainActivity activity;
    protected Client client;
    protected View view = null;

    public final View createView(int uuid, String name, MainActivity activity, Client client) {
        this.uuid = uuid;
        this.activity = activity;
        this.client = client;

        createViewInternal(name);
        setUnavailable();

        return view;
    }

    public final void setCurrentState(int state) {
        if (state == DEVICE_UNAVAILABLE_STATE) {
            setUnavailable();
        } else {
            view.setEnabled(true);
            setCurrentStateInternal(state);
        }
    }

    protected abstract void createViewInternal(String name);
    protected abstract void setCurrentStateInternal(int state);

    public void setUnavailable() {
        if (view != null) {
            view.setEnabled(false);
        }
    }
}
