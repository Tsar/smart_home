package ru.tsar_ioann.smarthome.devices;

import android.content.Context;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Switch;

public class TestDeviceWithLED implements Device {
    @Override
    public View createView(Context context, String name) {
        Switch result = new Switch(context);
        result.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        result.setText(name);
        return result;
    }
}
