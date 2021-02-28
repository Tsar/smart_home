package ru.tsar_ioann.smarthome.devices;

import android.content.Context;
import android.view.View;

public interface Device {
    View createView(Context context, String name);
}
