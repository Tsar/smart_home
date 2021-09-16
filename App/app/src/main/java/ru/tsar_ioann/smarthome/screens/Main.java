package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ListView;

import ru.tsar_ioann.smarthome.*;

public class Main extends BaseScreen implements DevicesList.Listener {
    private static final String LOG_TAG = "Main";
    private static final long ASYNC_REFRESH_INTERVAL_MS = 1000;

    private final Activity activity;
    private final DevicesList devices;
    private final DevicesAdapter devicesAdapter;

    private long lastAsyncRefreshET = 0;
    private boolean setupMode = false;

    public Main(CommonData commonData, MenuVisibilityChanger menuVisibilityChanger) {
        super(commonData);
        commonData.getWifi().disconnect();  // this is required for back button to work correctly

        activity = commonData.getActivity();
        devices = commonData.getDevices();
        devices.setListener(this);

        ListView lstDevices = activity.findViewById(R.id.lstDevices);

        devicesAdapter = new DevicesAdapter(activity, commonData.getDevices(), commonData.getScreenLauncher());
        lstDevices.setAdapter(devicesAdapter);

        menuVisibilityChanger.setMenuVisibility(true, !devices.getList().isEmpty(), true);

        asyncRefresh(false);
    }

    public void asyncRefresh(boolean checkTimePassed) {
        synchronized (this) {
            long nowET = SystemClock.elapsedRealtime();
            if (checkTimePassed) {
                if (nowET - lastAsyncRefreshET < ASYNC_REFRESH_INTERVAL_MS) {
                    Log.d(LOG_TAG, "Skipping refresh because previous was done recently");
                    return;
                }
            }
            lastAsyncRefreshET = nowET;
        }

        devices.rediscoverAll();
        devicesAdapter.notifyDataSetChanged();

        Udp.asyncMulticastNoThrow(
                UdpSettings.UDP_MULTICAST_IP,
                UdpSettings.UDP_MULTICAST_PORT,
                UdpSettings.UDP_SCAN_REQUEST
        );
    }

    public void toggleSetupMode() {
        setupMode = !setupMode;
        devicesAdapter.setSettingsButtonsVisible(setupMode);
    }

    @Override
    public void handleUdpDeviceInfo(String macAddress, String name, String ipAddress, int port) {
        DeviceInfo device = devices.getDeviceByMacAddress(macAddress);
        if (device == null) {
            Log.d(LOG_TAG, "Received MAC address which we do not know: " + macAddress);
            return;
        }
        if (device.isDiscovered()) {
            Log.d(LOG_TAG, "Got info about device which is already discovered (MAC: " + macAddress + ")");
            return;
        }
        if (device.isPermanentIp()) {
            Log.d(LOG_TAG, "Got info about device which has permanent IP (MAC: " + macAddress + ")");
            return;
        }
        if (device.getIpAddress().equals(ipAddress)) {
            Log.d(LOG_TAG, "Got info about device which has not changed it's IP (MAC: " + macAddress + ")");
            return;
        }

        Log.d(LOG_TAG, "Device with MAC address " + macAddress + " has changed IP to " + ipAddress + ", trying to discover");
        device.setParams(device.getName(), ipAddress, Http.DEFAULT_PORT, false, device.getHttpPassword());
        device.asyncTryToDiscover();
    }

    @Override
    public int getViewFlipperChildId() {
        return 0;
    }

    @Override
    public void onAnyDeviceInfoChanged() {
        activity.runOnUiThread(devicesAdapter::notifyDataSetChanged);
    }
}
