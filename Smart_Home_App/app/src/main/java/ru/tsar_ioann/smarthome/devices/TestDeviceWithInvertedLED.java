package ru.tsar_ioann.smarthome.devices;

import android.content.Context;
import android.util.TypedValue;
import android.view.ViewGroup.LayoutParams;
import android.widget.Switch;

import ru.tsar_ioann.smarthome.NrfMessageSender;

public class TestDeviceWithInvertedLED extends Device {
    private static final int LED_IS_OFF_BIT = 0x00000080;

    @Override
    public void createView(Context context, String name, NrfMessageSender nrfMessageSender) {
        this.nrfMessageSender = nrfMessageSender;

        Switch result = new Switch(context);
        result.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        result.setText(name);
        result.setOnCheckedChangeListener((v, isChecked) -> {
            this.nrfMessageSender.sendNrfMessage(/* TODO */);
        });

        view = result;
        setUnavailable();
    }

    @Override
    public void setCurrentState(int state) {
        if (state == DEVICE_UNAVAILABLE_STATE) {
            setUnavailable();
        } else {
            view.setEnabled(true);
            ((Switch)view).setChecked((state & LED_IS_OFF_BIT) != LED_IS_OFF_BIT);
        }
    }
}
