package ru.tsar_ioann.smarthome.devices;

import android.util.TypedValue;
import android.view.ViewGroup.LayoutParams;
import android.widget.CompoundButton;
import android.widget.Switch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ru.tsar_ioann.smarthome.Client;
import ru.tsar_ioann.smarthome.MainActivity;
import ru.tsar_ioann.smarthome.NrfMessage;
import ru.tsar_ioann.smarthome.request_processors.SendNrfMessage;

public class TestDeviceWithInvertedLED extends Device {
    private static final int LED_IS_OFF_BIT = 0x00000080;

    private int uuid;
    private MainActivity activity;
    private Client client;

    @Override
    public void createView(int uuid, String name, MainActivity activity, Client client) {
        this.uuid = uuid;
        this.activity = activity;
        this.client = client;

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
            client.sendNrfMessage(
                    new SendNrfMessage.Listener() {
                        @Override
                        public void onOKResult(NrfMessage response) {
                            // TODO: show warning if result is strange?
                        }

                        @Override
                        public void onSendFailed() {
                            setCheckedWithoutListener(!isChecked);
                            client.getDeviceStates(activity.getDeviceStatesListener, true);
                        }

                        @Override
                        public void onWrongPassword() {
                            setCheckedWithoutListener(!isChecked);
                            activity.handleWrongPassword(true);
                        }

                        @Override
                        public void onError(String errorText) {
                            setCheckedWithoutListener(!isChecked);
                            client.getDeviceStates(activity.getDeviceStatesListener, true);
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
