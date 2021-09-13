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
    private static final String KEY_PORT_PREFIX = "port-";
    private static final String KEY_PERMANENT_IP_PREFIX = "permanent-ip-";
    private static final String KEY_PASSWORD_PREFIX = "pwd-";

    private final SharedPreferences storage;
    private final List<DeviceInfo> deviceInfoList;
    private final Map<String, DeviceInfo> deviceMap;
    private Listener listener = null;

    public enum AddOrUpdateResult {
        ADD,
        UPDATE
    }

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
            int port = storage.getInt(KEY_PORT_PREFIX + i, Http.DEFAULT_PORT);
            boolean permanentIp = storage.getBoolean(KEY_PERMANENT_IP_PREFIX + i, false);
            String httpPassword = storage.getString(KEY_PASSWORD_PREFIX + i, DeviceInfo.DEFAULT_HTTP_PASSWORD);
            if (macAddress == null || name == null || ipAddress == null) {
                // TODO: more reasonable reaction
                throw new RuntimeException("Devices local storage is broken!");
            }
            DeviceInfo device = new DeviceInfo(macAddress, name, ipAddress, port, permanentIp, httpPassword, this);
            deviceInfoList.add(device);
            deviceMap.put(device.getMacAddress(), device);
        }
    }

    private void saveStorage() {
        SharedPreferences.Editor editor = storage.edit();
        for (int i = 0; i < deviceInfoList.size(); ++i) {
            DeviceInfo device = deviceInfoList.get(i);
            editor.putString(KEY_MAC_PREFIX + i, device.getMacAddress());
            editor.putString(KEY_NAME_PREFIX + i, device.getName());
            editor.putString(KEY_IP_PREFIX + i, device.getIpAddress());
            editor.putInt(KEY_PORT_PREFIX + i, device.getPort());
            editor.putBoolean(KEY_PERMANENT_IP_PREFIX + i, device.isPermanentIp());
            editor.putString(KEY_PASSWORD_PREFIX + i, device.getHttpPassword());
        }
        editor.putInt(KEY_COUNT, deviceInfoList.size());
        editor.apply();
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
                saveStorage();
                return AddOrUpdateResult.UPDATE;
            }
        }

        deviceInfo.setListener(this);
        deviceInfoList.add(deviceInfo);
        deviceMap.put(macAddress, deviceInfo);
        saveStorage();
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
        DeviceInfo temp = deviceInfoList.get(n1);
        deviceInfoList.set(n1, deviceInfoList.get(n2));
        deviceInfoList.set(n2, temp);
        saveStorage();
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
