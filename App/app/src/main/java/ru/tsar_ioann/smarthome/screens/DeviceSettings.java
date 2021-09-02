package ru.tsar_ioann.smarthome.screens;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ViewFlipper;

import ru.tsar_ioann.smarthome.*;

public class DeviceSettings extends BaseScreen {
    private static class SVFChild {
        private static final int DEVICE_SETTINGS_UNAVAILABLE = 0;
        private static final int DEVICE_SETTINGS = 1;
        private static final int CONNECTION_SETTINGS = 2;
    }

    private final ViewFlipper settingsViewFlipper;
    private boolean deviceIsDiscovered;

    private final int dimmersCount;
    private final int switchersCount;

    private final EditText edtName;

    private final EditText[] edtsValueChangeStep;
    private final EditText[] edtsMinLightnessMicros;
    private final EditText[] edtsMaxLightnessMicros;

    private final CheckBox[] cbsSwInverted;

    private final EditText edtIpAddress;
    private final EditText edtPort;
    private final CheckBox cbIpIsStatic;
    private final EditText edtPassword;

    @SuppressLint("SetTextI18n")
    public DeviceSettings(CommonData commonData) {
        super(commonData);

        Activity activity = commonData.getActivity();
        Button btnDeviceSettings = activity.findViewById(R.id.btnDeviceSettings);
        Button btnConnectionSettings = activity.findViewById(R.id.btnConnectionSettings);
        View underlineDeviceSettings = activity.findViewById(R.id.underlineDeviceSettings);
        View underlineConnectionSettings = activity.findViewById(R.id.underlineConnectionSettings);
        settingsViewFlipper = activity.findViewById(R.id.settingsViewFlipper);

        edtName = activity.findViewById(R.id.edtName);

        LinearLayout[] layoutsDimSettings = new LinearLayout[]{
                activity.findViewById(R.id.layoutDimSettings1),
                activity.findViewById(R.id.layoutDimSettings2),
                activity.findViewById(R.id.layoutDimSettings3)
        };
        dimmersCount = layoutsDimSettings.length;
        edtsValueChangeStep = new EditText[dimmersCount];
        edtsMinLightnessMicros = new EditText[dimmersCount];
        edtsMaxLightnessMicros = new EditText[dimmersCount];
        for (int i = 0; i < dimmersCount; ++i) {
            LinearLayout layoutDimSettings = layoutsDimSettings[i];
            TextView txtDimNumber = layoutDimSettings.findViewById(R.id.txtDimNumber);
            txtDimNumber.setText(Integer.toString(i + 1));
            edtsValueChangeStep[i] = layoutDimSettings.findViewById(R.id.edtDimValueChangeStep);
            edtsMinLightnessMicros[i] = layoutDimSettings.findViewById(R.id.edtDimMinLightnessMicros);
            edtsMaxLightnessMicros[i] = layoutDimSettings.findViewById(R.id.edtDimMaxLightnessMicros);
        }

        LinearLayout[] layoutsSwSettings = new LinearLayout[]{
                activity.findViewById(R.id.layoutSwSettings1),
                activity.findViewById(R.id.layoutSwSettings2),
                activity.findViewById(R.id.layoutSwSettings3),
                activity.findViewById(R.id.layoutSwSettings4)
        };
        switchersCount = layoutsSwSettings.length;
        cbsSwInverted = new CheckBox[switchersCount];
        for (int i = 0; i < switchersCount; ++i) {
            LinearLayout layoutSwSettings = layoutsSwSettings[i];
            TextView txtSwNumber = layoutSwSettings.findViewById(R.id.txtSwNumber);
            txtSwNumber.setText(Integer.toString(i + 1));
            cbsSwInverted[i] = layoutSwSettings.findViewById(R.id.cbSwInverted);
        }

        Button btnSaveDeviceSettings = activity.findViewById(R.id.btnSaveDeviceSettings);

        btnSaveDeviceSettings.setEnabled(false);  // temporary, remove when continuing developing
        btnSaveDeviceSettings.setOnClickListener(v -> {
            // TODO
        });

        edtIpAddress = activity.findViewById(R.id.edtIpAddress);
        edtPort = activity.findViewById(R.id.edtPort);
        cbIpIsStatic = activity.findViewById(R.id.cbIpIsStatic);
        edtPassword = activity.findViewById(R.id.edtPassword);
        Button btnSaveConnectionSettings = activity.findViewById(R.id.btnSaveConnectionSettings);

        btnSaveConnectionSettings.setEnabled(false);  // temporary, remove when continuing developing
        btnSaveConnectionSettings.setOnClickListener(v -> {
            // TODO
        });

        setupShowPasswordCheckBox(activity.findViewById(R.id.cbShowPassword), edtPassword);

        edtIpAddress.setFilters(new InputFilter[]{new Utils.IpAddressInputFilter()});
        edtPort.setFilters(new InputFilter[]{new Utils.PortInputFilter()});

        btnDeviceSettings.setOnClickListener(v -> {
            setButtonsPressedAndUnpressed(btnDeviceSettings, btnConnectionSettings);
            underlineDeviceSettings.setVisibility(View.VISIBLE);
            underlineConnectionSettings.setVisibility(View.INVISIBLE);
            settingsViewFlipper.setDisplayedChild(deviceIsDiscovered
                    ? SVFChild.DEVICE_SETTINGS
                    : SVFChild.DEVICE_SETTINGS_UNAVAILABLE
            );
        });
        btnConnectionSettings.setOnClickListener(v -> {
            setButtonsPressedAndUnpressed(btnConnectionSettings, btnDeviceSettings);
            underlineDeviceSettings.setVisibility(View.INVISIBLE);
            underlineConnectionSettings.setVisibility(View.VISIBLE);
            settingsViewFlipper.setDisplayedChild(SVFChild.CONNECTION_SETTINGS);
        });
    }

    private void setButtonsPressedAndUnpressed(Button mustBePressed, Button mustBeUnpressed) {
        final Resources resources = getCommonData().getResources();
        final Resources.Theme theme = getCommonData().getActivity().getTheme();
        mustBePressed.setTextColor(resources.getColorStateList(R.color.button_text_for_pressed_tab, theme));
        mustBeUnpressed.setTextColor(resources.getColorStateList(R.color.button_text, theme));
    }

    @SuppressLint("SetTextI18n")
    public void setDevice(DeviceInfo device) {
        this.deviceIsDiscovered = device.isDiscovered();
        if (settingsViewFlipper.getDisplayedChild() == SVFChild.DEVICE_SETTINGS
                || settingsViewFlipper.getDisplayedChild() == SVFChild.DEVICE_SETTINGS_UNAVAILABLE) {
            settingsViewFlipper.setDisplayedChild(deviceIsDiscovered
                    ? SVFChild.DEVICE_SETTINGS
                    : SVFChild.DEVICE_SETTINGS_UNAVAILABLE
            );
        }

        edtName.setText(device.getName());
        DeviceInfo.DimmerSettings[] dimmersSettings = device.getDimmersSettings();
        for (int i = 0; i < dimmersCount; ++i) {
            if (dimmersSettings[i] != null) {
                edtsValueChangeStep[i].setText(Integer.toString(dimmersSettings[i].valueChangeStep));
                edtsMinLightnessMicros[i].setText(Integer.toString(dimmersSettings[i].minLightnessMicros));
                edtsMaxLightnessMicros[i].setText(Integer.toString(dimmersSettings[i].maxLightnessMicros));
            } else {
                edtsValueChangeStep[i].setText("");
                edtsMinLightnessMicros[i].setText("");
                edtsMaxLightnessMicros[i].setText("");
            }
        }
        for (int i = 0; i < switchersCount; ++i) {
            cbsSwInverted[i].setChecked(device.isSwitcherInverted(i));
            cbsSwInverted[i].jumpDrawablesToCurrentState();
        }

        edtIpAddress.setText(device.getIpAddress());
        edtPort.setText(Integer.toString(device.getPort()));
        cbIpIsStatic.setChecked(device.isPermanentIp());
        cbIpIsStatic.jumpDrawablesToCurrentState();
        edtPassword.setText(device.getHttpPassword());
    }

    @Override
    public int getViewFlipperChildId() {
        return 8;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return false;
    }
}
