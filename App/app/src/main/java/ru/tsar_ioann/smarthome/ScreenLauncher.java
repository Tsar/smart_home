package ru.tsar_ioann.smarthome;

import android.widget.ViewFlipper;

import ru.tsar_ioann.smarthome.screens.*;

public class ScreenLauncher {
    private ScreenId currentScreenId;
    private BaseScreen currentScreen = null;

    private final CommonData commonData;
    private final ViewFlipper viewFlipper;
    private final MenuVisibilityChanger menuVisibilityChanger;

    public ScreenLauncher(CommonData commonData, ViewFlipper viewFlipper, MenuVisibilityChanger menuVisibilityChanger) {
        this.commonData = commonData;
        commonData.setScreenLauncher(this);
        this.viewFlipper = viewFlipper;
        this.menuVisibilityChanger = menuVisibilityChanger;
    }

    public BaseScreen launchScreen(ScreenId screenId) {
        if (screenId != ScreenId.MAIN) {
            menuVisibilityChanger.setMenuVisibility(false, false, false);
        }
        switch (screenId) {
            case MAIN:
                currentScreen = new Main(commonData, menuVisibilityChanger);
                break;
            case ADD_NEW_DEVICE:
                currentScreen = new AddNewDevice(commonData);
                break;
            case FRESH_DEVICES:
                currentScreen = new FreshDevices(commonData);
                break;
            case CONNECTING_FRESH_DEVICE:
                currentScreen = new ConnectingFreshDevice(commonData);
                break;
            case HOME_NETWORK_SETTINGS:
                currentScreen = new HomeNetworkSettings(commonData);
                break;
            case DEVICE_CONNECTED:
                currentScreen = new DeviceConnected(commonData);
                break;
            case CONFIGURED_DEVICES:
                currentScreen = new ConfiguredDevices(commonData);
                break;
            case CONFIGURED_DEVICE_PARAMS:
                currentScreen = new ConfiguredDeviceParams(commonData);
                break;
            case DEVICE_SETTINGS:
                currentScreen = new DeviceSettings(commonData);
                break;
        }
        currentScreenId = screenId;
        viewFlipper.setDisplayedChild(currentScreen.getViewFlipperChildId());
        return currentScreen;
    }

    public ScreenId getCurrentScreenId() {
        return currentScreenId;
    }

    public BaseScreen getCurrentScreen() {
        return currentScreen;
    }
}
