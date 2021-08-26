package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.util.Log;
import android.widget.ListView;

import java.io.IOException;
import java.net.SocketException;

import ru.tsar_ioann.smarthome.*;

public class Main extends BaseScreen implements DevicesList.Listener {
    private static final String UDP_SCAN_REQUEST = "SMART_HOME_SCAN";
    private static final String UDP_MULTICAST_IP = "227.16.119.203";
    private static final int UDP_MULTICAST_PORT = 25061;
    private static final int UDP_LISTEN_PORT = 25062;

    private final Activity activity;
    private final DevicesList devices;
    private final DevicesAdapter devicesAdapter;

    public Main(CommonData commonData) {
        super(commonData);
        commonData.getWifi().disconnect();  // this is required for back button to work correctly

        activity = commonData.getActivity();
        devices = commonData.getDevices();
        devices.setListener(this);

        ListView lstDevices = activity.findViewById(R.id.lstDevices);

        devicesAdapter = new DevicesAdapter(activity, commonData.getDevices().getList());
        lstDevices.setAdapter(devicesAdapter);

        Udp.asyncListen(UDP_LISTEN_PORT, new Udp.Listener() {
            private static final String LOG_TAG = "UdpListener";

            @Override
            public boolean finish() {
                return false;
            }

            @Override
            public void onReceive(String message, String senderIp, int senderPort) {
                String macAddress = ResponseParser.parseMac(message);
                if (macAddress == null) {
                    Log.d(LOG_TAG, "Failed to parse MAC address from message: " + message);
                    return;
                }
                DeviceInfo device = devices.getDeviceByMacAddress(macAddress);
                if (device == null) {
                    Log.d(LOG_TAG, "Received MAC address which we do not know: " + macAddress);
                    return;
                }
                if (device.isDiscovered()) {
                    Log.d(LOG_TAG, "Got info about device which is already discovered (" + macAddress + ")");
                    return;
                }
                if (device.getIpAddress().equals(senderIp)) {
                    Log.d(LOG_TAG, "Got info about device which has not changed it's IP (" + macAddress + ")");
                    return;
                }

                Log.d(LOG_TAG, "Device with MAC address " + macAddress + " has changed IP to " + senderIp + ", trying to discover");
                device.setIpAddress(senderIp);
                device.asyncTryToDiscover();
            }

            @Override
            public void onError(IOException exception) {
                Log.d(LOG_TAG, "Error on receiving UDP: " + exception.getMessage());
            }

            @Override
            public void onFatalError(SocketException exception) {
                Log.d(LOG_TAG, "Failed to start listening UDP: " + exception.getMessage());
            }
        });

        asyncRefresh();
    }

    public void asyncRefresh() {
        devices.rediscoverAll();
        devicesAdapter.notifyDataSetChanged();

        Udp.asyncMulticastNoThrow(UDP_MULTICAST_IP, UDP_MULTICAST_PORT, UDP_SCAN_REQUEST);
    }

    @Override
    public int getViewFlipperChildId() {
        return 0;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return true;
    }

    @Override
    public void onAnyDeviceInfoChanged() {
        activity.runOnUiThread(devicesAdapter::notifyDataSetChanged);
    }
}
