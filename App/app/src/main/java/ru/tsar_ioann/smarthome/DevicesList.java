package ru.tsar_ioann.smarthome;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

public class DevicesList {
    private static final String KEY_COUNT = "count";
    private static final String KEY_MAC_PREFIX = "mac-";
    private static final String KEY_NAME_PREFIX = "name-";
    private static final String KEY_IP_PREFIX = "ip-";

    private final SharedPreferences storage;
    private final List<DeviceInfo> deviceInfoList;

    public DevicesList(SharedPreferences devicesLocalStorage) {
        storage = devicesLocalStorage;
        deviceInfoList = new ArrayList<>();
        loadStorage();
    }

    private void loadStorage() {
        int count = storage.getInt(KEY_COUNT, 0);
        for (int i = 0; i < count; ++i) {
            String macAddress = storage.getString(KEY_MAC_PREFIX + i, null);
            String name = storage.getString(KEY_NAME_PREFIX + i, null);
            String ipAddress = storage.getString(KEY_IP_PREFIX + i, null);
            if (macAddress == null || name == null || ipAddress == null) {
                // TODO: more reasonable reaction
                throw new RuntimeException("Devices local storage is broken!");
            }
            deviceInfoList.add(new DeviceInfo(macAddress, name, ipAddress));
        }
    }

    private void saveStorage() {
        SharedPreferences.Editor editor = storage.edit();
        for (int i = 0; i < deviceInfoList.size(); ++i) {
            editor.putString(KEY_MAC_PREFIX + i, deviceInfoList.get(i).getMacAddress());
            editor.putString(KEY_NAME_PREFIX + i, deviceInfoList.get(i).getName());
            editor.putString(KEY_IP_PREFIX + i, deviceInfoList.get(i).getIpAddress());
        }
        editor.putInt(KEY_COUNT, deviceInfoList.size());
        editor.apply();
    }

    public void addDevice(DeviceInfo deviceInfo) {
        deviceInfoList.add(deviceInfo);
        saveStorage();
    }

    public List<DeviceInfo> getList() {
        return deviceInfoList;
    }
}
