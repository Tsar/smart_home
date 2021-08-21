package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.content.res.Resources;
import android.net.Network;

public class CommonData {
    private final Activity activity;
    private final Resources resources;
    private final Wifi wifi;
    private ScreenLauncher screenLauncher;

    private Network newDeviceNetwork = null;
    private DeviceInfo newDeviceInfo = null;

    public CommonData(Activity activity, Wifi wifi) {
        this.activity = activity;
        resources = activity.getResources();
        this.wifi = wifi;
    }

    public void setScreenLauncher(ScreenLauncher screenLauncher) {
        this.screenLauncher = screenLauncher;
    }

    public void setNewDeviceNetwork(Network newDeviceNetwork) {
        this.newDeviceNetwork = newDeviceNetwork;
    }

    public void setNewDeviceInfo(DeviceInfo newDeviceInfo) {
        this.newDeviceInfo = newDeviceInfo;
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

    public Network getNewDeviceNetwork() {
        return newDeviceNetwork;
    }

    public DeviceInfo getNewDeviceInfo() {
        return newDeviceInfo;
    }
}
