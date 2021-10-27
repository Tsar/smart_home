package ru.tsar_ioann.smarthome;

import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DevicesList implements DeviceInfo.Listener {
    private static final String LOG_TAG = "DevicesList";

    private static final String KEY_COUNT = "count";

    private final SharedPreferences storage;
    private final List<DeviceInfo> deviceInfoList;
    private final Map<String, DeviceInfo> deviceMap;  // MAC -> device info
    private final Map<String, Integer> deviceIdsMap;  // MAC -> id in list
    private Listener listener = null;

    public enum AddOrUpdateResult {
        ADD,
        UPDATE
    }

    public interface Listener {
        void onAnyDeviceInfoUpdated();
    }

    public DevicesList(SharedPreferences devicesLocalStorage) {
        storage = devicesLocalStorage;
        deviceInfoList = new ArrayList<>();
        deviceMap = new HashMap<>();
        deviceIdsMap = new HashMap<>();
        loadStorage();
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private void loadStorage() {
        int count = storage.getInt(KEY_COUNT, 0);
        for (int i = 0; i < count; ++i) {
            DeviceInfo device = new DeviceInfo(storage, i, this);
            deviceInfoList.add(device);
            final String macAddress = device.getMacAddress();
            deviceMap.put(macAddress, device);
            deviceIdsMap.put(macAddress, i);
        }
    }

    private void saveDeviceToStorage(DeviceInfo device, int deviceId) {
        final SharedPreferences.Editor editor = storage.edit();
        device.saveToStorage(editor, deviceId);
        editor.apply();
    }

    private boolean saveDeviceToStorage(DeviceInfo device) {
        final Integer deviceId = deviceIdsMap.get(device.getMacAddress());
        if (deviceId != null) {
            saveDeviceToStorage(device, deviceId);
            return true;
        }
        return false;
    }

    public AddOrUpdateResult addOrUpdateDevice(DeviceInfo deviceInfo) {
        String macAddress = deviceInfo.getMacAddress();

        if (deviceMap.containsKey(macAddress)) {
            DeviceInfo existingDeviceInfo = deviceMap.get(macAddress);
            if (existingDeviceInfo != null) {
                existingDeviceInfo.setParams(
                        deviceInfo.getName(),
                        deviceInfo.getIpAddress(),
                        deviceInfo.getPort(),
                        deviceInfo.isPermanentIp(),
                        deviceInfo.getHttpPassword()
                );
                saveDeviceToStorage(existingDeviceInfo);
                return AddOrUpdateResult.UPDATE;
            }
        }

        deviceInfo.setListener(this);
        deviceInfoList.add(deviceInfo);
        deviceMap.put(macAddress, deviceInfo);
        final int deviceId = deviceInfoList.size() - 1;
        deviceIdsMap.put(macAddress, deviceId);

        final SharedPreferences.Editor editor = storage.edit();
        deviceInfo.saveToStorage(editor, deviceId);
        editor.putInt(KEY_COUNT, deviceInfoList.size());
        editor.apply();

        return AddOrUpdateResult.ADD;
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

    public void swap(int n1, int n2) {
        if (n1 < 0 || n1 >= deviceInfoList.size() || n2 < 0 || n2 >= deviceInfoList.size() || n1 == n2) {
            return;
        }
        DeviceInfo dev1 = deviceInfoList.get(n1);
        DeviceInfo dev2 = deviceInfoList.get(n2);
        deviceInfoList.set(n1, dev2);
        deviceInfoList.set(n2, dev1);
        deviceIdsMap.put(dev1.getMacAddress(), n2);
        deviceIdsMap.put(dev2.getMacAddress(), n1);

        final SharedPreferences.Editor editor = storage.edit();
        dev1.saveToStorage(editor, n2);
        dev2.saveToStorage(editor, n1);
        editor.apply();
    }

    @Override
    public void onDeviceUpdated(DeviceInfo device) {
        if (listener != null) {
            listener.onAnyDeviceInfoUpdated();
        }
        if (saveDeviceToStorage(device)) {
            Log.d(LOG_TAG, "Saved device " + device.getMacAddress() + " to storage");
        }
    }
}
