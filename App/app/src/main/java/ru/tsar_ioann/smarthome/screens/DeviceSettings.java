package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.content.res.Resources;
import android.widget.Button;
import android.widget.ViewFlipper;

import ru.tsar_ioann.smarthome.*;

public class DeviceSettings extends BaseScreen {
    public DeviceSettings(CommonData commonData) {
        super(commonData);

        Activity activity = commonData.getActivity();
        Button btnDeviceSettings = activity.findViewById(R.id.btnDeviceSettings);
        Button btnConnectionSettings = activity.findViewById(R.id.btnConnectionSettings);
        ViewFlipper viewFlipperSettings = activity.findViewById(R.id.viewFlipperSettings);

        btnDeviceSettings.setOnClickListener(v -> {
            setButtonsPressedAndUnpressed(btnDeviceSettings, btnConnectionSettings);
            viewFlipperSettings.setDisplayedChild(0);
        });
        btnConnectionSettings.setOnClickListener(v -> {
            setButtonsPressedAndUnpressed(btnConnectionSettings, btnDeviceSettings);
            viewFlipperSettings.setDisplayedChild(1);
        });
    }

    private void setButtonsPressedAndUnpressed(Button mustBePressed, Button mustBeUnpressed) {
        final Resources resources = getCommonData().getResources();
        final Resources.Theme theme = getCommonData().getActivity().getTheme();
        mustBePressed.setTextColor(resources.getColorStateList(R.color.button_text_for_pressed_tab, theme));
        mustBePressed.setBackgroundTintList(resources.getColorStateList(R.color.button_bg_for_pressed_tab, theme));
        mustBeUnpressed.setTextColor(resources.getColorStateList(R.color.button_text, theme));
        mustBeUnpressed.setBackgroundTintList(resources.getColorStateList(R.color.button_bg, theme));

        // This dirty trick redraws buttons, otherwise background color gets stuck
        mustBePressed.setEnabled(false);
        mustBePressed.setEnabled(true);
        mustBeUnpressed.setEnabled(false);
        mustBeUnpressed.setEnabled(true);
    }

    public void setDevice(DeviceInfo device) {
        // TODO
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
