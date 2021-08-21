package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.content.res.Resources;
import android.net.Network;

public class CommonData {
    private final Activity activity;
    private final Resources resources;
    private final Wifi wifi;
    private ScreenLauncher screenLauncher;
    private Network deviceNetwork;

    public CommonData(Activity activity, Wifi wifi) {
        this.activity = activity;
        resources = activity.getResources();
        this.wifi = wifi;
    }

    public void setScreenLauncher(ScreenLauncher screenLauncher) {
        this.screenLauncher = screenLauncher;
    }

    public void setDeviceNetwork(Network deviceNetwork) {
        this.deviceNetwork = deviceNetwork;
    }

    public Activity getActivity() {
        return activity;
    }

    public Resources getResources() {
        return resources;
    }

    public Wifi getWifi() {
        return wifi;
    }

    public ScreenLauncher getScreenLauncher() {
        return screenLauncher;
    }

    public Network getDeviceNetwork() {
        return deviceNetwork;
    }
}
