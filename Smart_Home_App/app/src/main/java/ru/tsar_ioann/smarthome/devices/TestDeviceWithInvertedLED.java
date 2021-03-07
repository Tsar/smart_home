package ru.tsar_ioann.smarthome.devices;

import android.util.TypedValue;
import android.view.ViewGroup.LayoutParams;
import android.widget.CompoundButton;
import android.widget.Switch;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import ru.tsar_ioann.smarthome.NrfMessage;
import ru.tsar_ioann.smarthome.request_processors.SendNrfMessage;

public class TestDeviceWithInvertedLED extends Device {
    private static final int LED_IS_OFF_BIT = 0x00000080;

    @Override
    protected void createViewInternal(String name) {
        Switch result = new Switch(activity);
        result.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        result.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        result.setText(name);
        result.setOnCheckedChangeListener(onCheckedChangeListener);
        view = result;
    }

    @Override
    protected void setCurrentStateInternal(int state) {
        setCheckedWithoutListener((state & LED_IS_OFF_BIT) != LED_IS_OFF_BIT);
    }

    private void setCheckedWithoutListener(boolean isChecked) {
        Switch sw = (Switch)view;
        sw.setOnCheckedChangeListener(null);
        sw.setChecked(isChecked);
        sw.setOnCheckedChangeListener(onCheckedChangeListener);
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
                            setCheckedWithoutListenerOnUiThread(!isChecked);
                            client.getDeviceStates(activity.getDeviceStatesListener, true);
                        }

                        @Override
                        public void onWrongPassword() {
                            setCheckedWithoutListenerOnUiThread(!isChecked);
                            activity.handleWrongPassword(true);
                        }

                        @Override
                        public void onError(String errorText) {
                            setCheckedWithoutListenerOnUiThread(!isChecked);
                            client.getDeviceStates(activity.getDeviceStatesListener, true);
                        }
                    },
                    new NrfMessage(uuid, NrfMessage.COMMAND_SET_STATE, stateAsBytes)
            );
        }

        private void setCheckedWithoutListenerOnUiThread(boolean isChecked) {
            activity.runOnUiThread(() -> {
                setCheckedWithoutListener(isChecked);
            });
        }
    };
}
