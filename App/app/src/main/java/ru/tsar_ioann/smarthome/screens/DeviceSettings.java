package ru.tsar_ioann.smarthome.screens;

import ru.tsar_ioann.smarthome.*;

public class DeviceSettings extends BaseScreen {
    public DeviceSettings(CommonData commonData) {
        super(commonData);
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
