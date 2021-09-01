package ru.tsar_ioann.smarthome.screens;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.text.InputFilter;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ViewFlipper;

import ru.tsar_ioann.smarthome.*;

public class DeviceSettings extends BaseScreen {
    private final EditText edtName;
    private final EditText edtIpAddress;
    private final EditText edtPort;
    private final CheckBox cbIpIsStatic;
    private final EditText edtPassword;

    public DeviceSettings(CommonData commonData) {
        super(commonData);

        Activity activity = commonData.getActivity();
        Button btnDeviceSettings = activity.findViewById(R.id.btnDeviceSettings);
        Button btnConnectionSettings = activity.findViewById(R.id.btnConnectionSettings);
        View underlineDeviceSettings = activity.findViewById(R.id.underlineDeviceSettings);
        View underlineConnectionSettings = activity.findViewById(R.id.underlineConnectionSettings);
        ViewFlipper viewFlipperSettings = activity.findViewById(R.id.viewFlipperSettings);
        edtName = activity.findViewById(R.id.edtName);
        edtIpAddress = activity.findViewById(R.id.edtIpAddress);
        edtPort = activity.findViewById(R.id.edtPort);
        cbIpIsStatic = activity.findViewById(R.id.cbIpIsStatic);
        edtPassword = activity.findViewById(R.id.edtPassword);

        setupShowPasswordCheckBox(activity.findViewById(R.id.cbShowPassword), edtPassword);

        edtIpAddress.setFilters(new InputFilter[]{new Utils.IpAddressInputFilter()});
        edtPort.setFilters(new InputFilter[]{new Utils.PortInputFilter()});

        btnDeviceSettings.setOnClickListener(v -> {
            setButtonsPressedAndUnpressed(btnDeviceSettings, btnConnectionSettings);
            underlineDeviceSettings.setVisibility(View.VISIBLE);
            underlineConnectionSettings.setVisibility(View.INVISIBLE);
            viewFlipperSettings.setDisplayedChild(0);
        });
        btnConnectionSettings.setOnClickListener(v -> {
            setButtonsPressedAndUnpressed(btnConnectionSettings, btnDeviceSettings);
            underlineDeviceSettings.setVisibility(View.INVISIBLE);
            underlineConnectionSettings.setVisibility(View.VISIBLE);
            viewFlipperSettings.setDisplayedChild(1);
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
        edtName.setText(device.getName());
        edtIpAddress.setText(device.getIpAddress());
        edtPort.setText(Integer.toString(device.getPort()));
        cbIpIsStatic.setChecked(device.isPermanentIp());
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
