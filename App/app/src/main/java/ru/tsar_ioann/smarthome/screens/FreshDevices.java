package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

import ru.tsar_ioann.smarthome.*;

public class FreshDevices extends BaseScreen {
    private static final String SMART_HOME_DEVICE_AP_SSID_PREFIX = "SmartHomeDevice_";
    private static final int SMART_HOME_DEVICE_AP_SSID_LENGTH = SMART_HOME_DEVICE_AP_SSID_PREFIX.length() + 6;

    public FreshDevices(CommonData commonData) {
        super(commonData);

        Activity activity = commonData.getActivity();
        TextView txtSearchTitle = activity.findViewById(R.id.txtSearchTitle);
        ListView lstNewDevices = activity.findViewById(R.id.lstNewDevices);

        ArrayAdapter<String> lstNewDevicesAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        lstNewDevices.setAdapter(lstNewDevicesAdapter);
        lstNewDevices.setOnItemClickListener((adapterView, view, position, id) -> {
            ConnectingFreshDevice screen = (ConnectingFreshDevice)commonData
                    .getScreenLauncher().launchScreen(ScreenId.CONNECTING_FRESH_DEVICE);
            screen.connectToDevice((String)adapterView.getItemAtPosition(position));
        });

        txtSearchTitle.setText(tr(R.string.searching_smart_home));
        Set<String> devices = new HashSet<>();
        lstNewDevicesAdapter.clear();
        commonData.getWifi().scan(30, new Wifi.ScanListener() {
            @Override
            public void onWifiFound(String ssid) {
                activity.runOnUiThread(() -> {
                    if (ssid.length() == SMART_HOME_DEVICE_AP_SSID_LENGTH && ssid.startsWith(SMART_HOME_DEVICE_AP_SSID_PREFIX)) {
                        if (!devices.contains(ssid)) {
                            devices.add(ssid);
                            lstNewDevicesAdapter.add(ssid);
                        }
                    }
                });
            }

            @Override
            public void onScanFinished() {
                activity.runOnUiThread(() -> txtSearchTitle.setText(tr(lstNewDevicesAdapter.getCount() > 0
                        ? R.string.search_finished_choose_device
                        : R.string.search_finished_nothing_found))
                );
            }
        });
    }

    @Override
    public int getViewFlipperChildId() {
        return 2;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return false;
    }
}