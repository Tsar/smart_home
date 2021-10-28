package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.content.res.Resources;
import android.net.Network;

public class CommonData {
    private final Activity activity;
    private final Wifi wifi;
    private final DevicesList devices;
    private ScreenLauncher screenLauncher;

    // Used when adding new device
    private Network newDeviceNetwork = null;
    private DeviceInfo newDeviceInfo = null;
    private String homeNetworkSsid = null;

    public CommonData(Activity activity, Wifi wifi, DevicesList devices) {
        this.activity = activity;
        this.wifi = wifi;
        this.devices = devices;
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

    public void setHomeNetworkSsid(String homeNetworkSsid) {
        this.homeNetworkSsid = homeNetworkSsid;
    }

    public Activity getActivity() {
        return activity;
    }

    public Wifi getWifi() {
        return wifi;
    }

    public DevicesList getDevices() {
        return devices;
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

    public String getHomeNetworkSsid() {
        return homeNetworkSsid;
    }
}
