package ru.tsar_ioann.smarthome;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

public class DeviceTypes {
    public static final short TEST_DEVICE_WITH_LED = 1;

    private static View createViewForTestDeviceWithLED(int nameId, Context context) {
        Button test = new Button(context);
        test.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));
        test.setText("nameId: " + nameId);
        return test;
    }

    public static void addViewForDevice(DeviceParams device, Context context, LinearLayout devicesLayout) {
        switch (device.getDeviceType()) {
            case TEST_DEVICE_WITH_LED:
                devicesLayout.addView(createViewForTestDeviceWithLED(device.getNameId(), context));
                break;
        }
    }
}
