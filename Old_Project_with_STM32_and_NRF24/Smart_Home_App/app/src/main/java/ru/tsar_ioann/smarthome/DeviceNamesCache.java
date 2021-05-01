package ru.tsar_ioann.smarthome;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DeviceNamesCache {
    private static final String LOG_TAG = "DeviceNamesCache";

    private static final String URL_GET_DEVICE_NAMES = "https://tsar-ioann.ru/smart_home/get_device_names.php";

    private SharedPreferences namesLocalStorage;

    public DeviceNamesCache(SharedPreferences namesLocalStorage) {
        this.namesLocalStorage = namesLocalStorage;
    }

    private Map<Integer, String> getDeviceNamesByHTTP(Set<Integer> ids) {
        JSONArray idsJSON = new JSONArray();
        for (int id : ids) {
            idsJSON.put(id);
        }

        Map<Integer, String> result = new HashMap<>();
        try {
            Http.Response response = Http.doPostRequest(
                    URL_GET_DEVICE_NAMES,
                    idsJSON.toString().getBytes(StandardCharsets.UTF_8),
                    null,
                    false
            );
            int httpCode = response.getHttpCode();
            if (httpCode == HttpURLConnection.HTTP_OK) {
                try {
                    JSONObject respJSON = new JSONObject(new String(response.getData(), StandardCharsets.UTF_8));
                    for (Iterator<String> it = respJSON.keys(); it.hasNext(); ) {
                        String key = it.next();
                        try {
                            result.put(Integer.parseInt(key), respJSON.getString(key));
                        } catch (NumberFormatException e) {
                            Log.d(LOG_TAG, "Failed to get some device names, bad response key: " + e.getMessage());
                        }
                    }
                } catch (JSONException e) {
                    Log.d(LOG_TAG, "Failed to get device names, bad JSON: " + e.getMessage());
                }
            } else {
                Log.d(LOG_TAG, "Failed to get device names with HTTP code: " + httpCode);
            }
        } catch (Http.Exception e) {
            Log.d(LOG_TAG, "Failed to get device names: " + e.getMessage());
        }
        return result;
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
            Map<Integer, String> httpResult = getDeviceNamesByHTTP(notInCache);
            if (!httpResult.isEmpty()) {
                SharedPreferences.Editor editor = namesLocalStorage.edit();
                for (Map.Entry<Integer, String> nameInfo : httpResult.entrySet()) {
                    editor.putString("n" + nameInfo.getKey(), nameInfo.getValue());
                }
                editor.apply();

                result.putAll(httpResult);
            }
        }

        return result;
    }
}
