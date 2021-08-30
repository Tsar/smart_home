package ru.tsar_ioann.smarthome.screens;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import ru.tsar_ioann.smarthome.*;

public class ConfiguredDevices extends BaseScreen {
    private static final String LOG_TAG = "ConfiguredDevices";

    private static final int MULTICASTS_COUNT = 60;
    private static final int DELAY_BETWEEN_MULTICASTS = 1000;

    private final Activity activity;
    private final ArrayAdapter<DeviceInfo> lstConfiguredDevicesAdapter;

    private final List<DeviceInfo> configuredDevicesList;
    private final Set<String> configuredDevices;

    public ConfiguredDevices(CommonData commonData) {
        super(commonData);

        activity = commonData.getActivity();
        TextView txtSearchingConfigured = activity.findViewById(R.id.txtSearchingConfigured);
        ListView lstConfiguredDevices = activity.findViewById(R.id.lstConfiguredDevices);

        txtSearchingConfigured.setText(tr(R.string.searching_configured_devices));

        configuredDevices = new HashSet<>();
        for (DeviceInfo addedDevice : commonData.getDevices().getList()) {
            configuredDevices.add(addedDevice.getMacAddress());
        }

        configuredDevicesList = new ArrayList<>();
        lstConfiguredDevicesAdapter = new ArrayAdapter<DeviceInfo>(
                activity,
                android.R.layout.simple_list_item_2,
                android.R.id.text1,
                configuredDevicesList
        ) {
            @SuppressLint("SetTextI18n")
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView text1 = view.findViewById(android.R.id.text1);
                TextView text2 = view.findViewById(android.R.id.text2);
                DeviceInfo device = configuredDevicesList.get(position);
                text1.setText(device.getName());
                text2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                text2.setText(device.getMacAddress() + " - " + device.getIpAddress());
                return view;
            }
        };
        lstConfiguredDevices.setAdapter(lstConfiguredDevicesAdapter);

        Timer timerMulticastRepeater = new Timer();
        timerMulticastRepeater.schedule(new TimerTask() {
            private int sentMulticasts = 0;

            @Override
            public void run() {
                if (sentMulticasts >= MULTICASTS_COUNT) {
                    timerMulticastRepeater.cancel();
                    activity.runOnUiThread(() -> txtSearchingConfigured.setText(tr(configuredDevicesList.size() > 0
                            ? R.string.search_configured_finished_choose_device
                            : R.string.search_configured_finished_nothing_found))
                    );
                    return;
                }

                Udp.asyncMulticastNoThrow(
                        UdpSettings.UDP_MULTICAST_IP,
                        UdpSettings.UDP_MULTICAST_PORT,
                        UdpSettings.UDP_SCAN_REQUEST
                );
                ++sentMulticasts;
            }
        }, 0, DELAY_BETWEEN_MULTICASTS);

        lstConfiguredDevices.setOnItemClickListener((adapterView, view, position, id) -> {
            timerMulticastRepeater.cancel();
            // TODO: launch next screen
            //commonData.getScreenLauncher().launchScreen(ScreenId.XXX);
        });
    }

    @Override
    public void handleUdpDeviceInfo(String macAddress, String name, String ipAddress, int port) {
        final String deviceInfoStrForLog = macAddress + " - " + name + " - " + ipAddress;
        if (!configuredDevices.contains(macAddress)) {
            Log.d(LOG_TAG, "Got info about new configured device: " + deviceInfoStrForLog);
            configuredDevices.add(macAddress);
            configuredDevicesList.add(new DeviceInfo(macAddress, name, ipAddress, null));
            activity.runOnUiThread(lstConfiguredDevicesAdapter::notifyDataSetChanged);
        } else {
            Log.d(LOG_TAG, "Got info about already found or added device: " + deviceInfoStrForLog);
        }
    }

    @Override
    public int getViewFlipperChildId() {
        return 6;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return false;
    }
}
