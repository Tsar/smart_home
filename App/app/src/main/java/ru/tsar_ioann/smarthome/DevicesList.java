package ru.tsar_ioann.smarthome;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DevicesList implements DeviceInfo.Listener {
    private static final String KEY_COUNT = "count";
    private static final String KEY_MAC_PREFIX = "mac-";
    private static final String KEY_NAME_PREFIX = "name-";
    private static final String KEY_IP_PREFIX = "ip-";

    private final SharedPreferences storage;
    private final List<DeviceInfo> deviceInfoList;
    private final Map<String, DeviceInfo> deviceMap;
    private Listener listener = null;

    public interface Listener {
        void onAnyDeviceInfoChanged();
    }

    public DevicesList(SharedPreferences devicesLocalStorage) {
        storage = devicesLocalStorage;
        deviceInfoList = new ArrayList<>();
        deviceMap = new HashMap<>();
        loadStorage();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
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
            DeviceInfo device = new DeviceInfo(macAddress, name, ipAddress, this);
            deviceInfoList.add(device);
            deviceMap.put(device.getMacAddress(), device);
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
        String macAddress = deviceInfo.getMacAddress();

        if (deviceMap.containsKey(macAddress)) {
            DeviceInfo existingDeviceInfo = deviceMap.get(macAddress);
            if (existingDeviceInfo != null) {
                existingDeviceInfo.setName(deviceInfo.getName());
                existingDeviceInfo.setIpAddress(deviceInfo.getIpAddress());
                saveStorage();
                // TODO: tell user that new device wasn't added, just existing device was updated
                return;
            }
        }

        deviceInfo.setListener(this);
        deviceInfoList.add(deviceInfo);
        deviceMap.put(macAddress, deviceInfo);
        saveStorage();
    }

    public List<DeviceInfo> getList() {
        return deviceInfoList;
    }

    public void rediscoverAll() {
        for (DeviceInfo device : deviceInfoList) {
            device.setDiscovered(false);
            device.asyncTryToDiscover();
        }
    }

    public DeviceInfo getDeviceByMacAddress(String macAddress) {
        return deviceMap.get(macAddress);
    }

    @Override
    public void onDeviceInfoChanged() {
        if (listener != null) {
            listener.onAnyDeviceInfoChanged();
        }
        saveStorage();  // TODO: save only device, which has changed
    }

    @Override
    public void onDeviceDiscovered() {
        if (listener != null) {
            listener.onAnyDeviceInfoChanged();
        }
    }
}
