package ru.tsar_ioann.smarthome.devices;

import android.app.Activity;
import android.util.TypedValue;
import android.view.ViewGroup.LayoutParams;
import android.widget.CompoundButton;
import android.widget.Switch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ru.tsar_ioann.smarthome.NrfMessage;
import ru.tsar_ioann.smarthome.NrfMessageSender;
import ru.tsar_ioann.smarthome.request_processors.SendNrfMessage;

public class TestDeviceWithInvertedLED extends Device {
    private static final int LED_IS_OFF_BIT = 0x00000080;

    private Activity activity;
    private int uuid;
    private NrfMessageSender nrfMessageSender;

    @Override
    public void createView(Activity activity, String name, int uuid, NrfMessageSender nrfMessageSender) {
        this.activity = activity;
        this.uuid = uuid;
        this.nrfMessageSender = nrfMessageSender;

        Switch result = new Switch(activity);
        result.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        result.setText(name);
        result.setOnCheckedChangeListener(onCheckedChangeListener);

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

    private CompoundButton.OnCheckedChangeListener onCheckedChangeListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            byte[] stateAsBytes = ByteBuffer.allocate(4)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(isChecked ? 0 : LED_IS_OFF_BIT)
                    .array();
            nrfMessageSender.sendNrfMessage(
                    new SendNrfMessage.Listener() {
                        @Override
                        public void onOKResult(NrfMessage response) {
                            // TODO
                        }

                        @Override
                        public void onSendFailed() {
                            setCheckedWithoutListener(!isChecked);
                        }

                        @Override
                        public void onWrongPassword() {
                            setCheckedWithoutListener(!isChecked);
                            // TODO
                        }

                        @Override
                        public void onError(String errorText) {
                            setCheckedWithoutListener(!isChecked);
                        }
                    },
                    new NrfMessage(uuid, NrfMessage.COMMAND_SET_STATE, stateAsBytes)
            );
        }

        private void setCheckedWithoutListener(boolean isChecked) {
            activity.runOnUiThread(() -> {
                Switch sw = (Switch)view;
                sw.setOnCheckedChangeListener(null);
                sw.setChecked(isChecked);
                sw.setOnCheckedChangeListener(this);
            });
        }
    };
}
