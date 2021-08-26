package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.util.Log;
import android.widget.ListView;

import java.io.IOException;
import java.net.SocketException;

import ru.tsar_ioann.smarthome.*;

public class Main extends BaseScreen {
    private static final String LOG_TAG = "MainScreen";

    private static final String UDP_SCAN_REQUEST = "SMART_HOME_SCAN";
    private static final String UDP_MULTICAST_IP = "227.16.119.203";
    private static final int UDP_MULTICAST_PORT = 25061;
    private static final int UDP_LISTEN_PORT = 25062;

    public Main(CommonData commonData) {
        super(commonData);
        commonData.getWifi().disconnect();  // this is required for back button to work correctly

        Activity activity = commonData.getActivity();
        ListView lstDevices = activity.findViewById(R.id.lstDevices);

        DevicesAdapter devicesAdapter = new DevicesAdapter(activity, commonData.getDevices().getList());
        lstDevices.setAdapter(devicesAdapter);

        Udp.asyncListen(UDP_LISTEN_PORT, new Udp.Listener() {
            @Override
            public boolean finish() {
                return false;
            }

            @Override
            public void onReceive(String message, String senderIp, int senderPort) {
                Log.d(LOG_TAG, "Got [" + message + "] from IP " + senderIp);
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
        Udp.asyncMulticastNoThrow(UDP_MULTICAST_IP, UDP_MULTICAST_PORT, UDP_SCAN_REQUEST);

        DevicesList devices = getCommonData().getDevices();
        // TODO
    }

    @Override
    public int getViewFlipperChildId() {
        return 0;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return true;
    }
}
