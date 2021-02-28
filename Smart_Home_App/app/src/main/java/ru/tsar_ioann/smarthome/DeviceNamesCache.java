package ru.tsar_ioann.smarthome;

import android.content.SharedPreferences;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeviceNamesCache {
    private SharedPreferences namesLocalStorage;

    public DeviceNamesCache(SharedPreferences namesLocalStorage) {
        this.namesLocalStorage = namesLocalStorage;
    }

    public Map<Integer, String> getDeviceNames(List<DeviceParams> devices) {
        Map<Integer, String> result = new HashMap<>();
        Set<Integer> notInCache = new HashSet<>();
        for (DeviceParams device : devices) {
            int nameId = device.getNameId();
            if (namesLocalStorage.contains("n" + nameId)) {
                result.put(nameId, namesLocalStorage.getString("n" + nameId, ""));
            } else {
                notInCache.add(nameId);
            }
        }
        if (!notInCache.isEmpty()) {
            // TODO: get them from HTTP server if possible
        }
        return result;
    }
}
