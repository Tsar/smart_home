package ru.tsar_ioann.smarthome;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Iterator;

public class DeviceInfo {
    private static final String LOG_TAG = "DeviceInfo";

    private static final int DIMMERS_COUNT = 3;
    private static final int SWITCHERS_COUNT = 4;
    private static final String DIMMER_PREFIX = "dim";
    private static final String SWITCHER_PREFIX = "sw";

    private final String macAddress;
    private String name;
    private String ipAddress = null;
    private String httpPassword = "12345";  // work on this
    private boolean discovered = false;
    private Listener listener = null;

    private final int[] dimmerValues = new int[DIMMERS_COUNT];
    private final boolean[] switcherValues = new boolean[SWITCHERS_COUNT];

    public interface Listener {
        void onDeviceInfoChanged();
        void onDeviceDiscovered();
    }

    public static class InvalidMacAddressException extends Exception {
        public InvalidMacAddressException(String macAddress) {
            super("Invalid MAC address '" + macAddress + "'");
        }
    }

    public static DeviceInfo parseMinimalJson(String json) throws JSONException, InvalidMacAddressException {
        return parseJson(json, true);
    }

    public static DeviceInfo parseJson(String json) throws JSONException, InvalidMacAddressException {
        return parseJson(json, false);
    }

    private static DeviceInfo parseJson(String json, boolean minimal) throws JSONException, InvalidMacAddressException {
        JSONObject obj = new JSONObject(json);
        String macAddress = obj.getString("mac");
        if (!Utils.isValidMacAddress(macAddress)) {
            throw new InvalidMacAddressException(macAddress);
        }
        DeviceInfo result = new DeviceInfo(macAddress, obj.getString("name"));
        if (minimal) {
            return result;
        }
        JSONObject values = obj.getJSONObject("values");
        Iterator<String> keys = values.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(DIMMER_PREFIX)) {
                try {
                    int n = Integer.parseInt(key.substring(DIMMER_PREFIX.length()));
                    result.setDimmerValue(n, values.getInt(key));
                } catch (NumberFormatException ignored) {
                    // skipping field
                }
            } else if (key.startsWith("sw")) {
                try {
                    int n = Integer.parseInt(key.substring(SWITCHER_PREFIX.length()));
                    result.setSwitcherValue(n, values.getInt(key) != 0);
                } catch (NumberFormatException ignored) {
                    // skipping field
                }
            }
        }
        return result;
    }

    private DeviceInfo(String macAddress, String name) {
        this.macAddress = macAddress;
        this.name = name;
        Arrays.fill(this.dimmerValues, 500);
    }

    public DeviceInfo(String macAddress, String name, String ipAddress, Listener listener) {
        this.macAddress = macAddress;
        this.name = name;
        this.ipAddress = ipAddress;
        this.listener = listener;
        Arrays.fill(this.dimmerValues, 500);
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getName() {
        return name;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHttpPassword() {
        return httpPassword;
    }

    public boolean isDiscovered() {
        return discovered;
    }

    public int getDimmerValue(int n) {
        return dimmerValues[n];
    }

    public boolean getSwitcherValue(int n) {
        return switcherValues[n];
    }

    public void setName(String name) {
        synchronized (this) {
            this.name = name;
            if (listener != null) {
                listener.onDeviceInfoChanged();
            }
        }
    }

    public void setIpAddress(String ipAddress) {
        synchronized (this) {
            this.ipAddress = ipAddress;
            if (listener != null) {
                listener.onDeviceInfoChanged();
            }
        }
    }

    public void setHttpPassword(String httpPassword) {
        this.httpPassword = httpPassword;
    }

    public void setDiscovered(boolean discovered) {
        this.discovered = discovered;
    }

    public void setDimmerValue(int n, int value) {
        if (n >= 0 && n < DIMMERS_COUNT) {
            dimmerValues[n] = value;
        }
    }

    public void setSwitcherValue(int n, boolean value) {
        if (n >= 0 && n < SWITCHERS_COUNT) {
            switcherValues[n] = value;
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    private boolean sync(DeviceInfo info) {
        boolean anythingChanged = false;
        if (!name.equals(info.name)) {
            name = info.name;
            anythingChanged = true;
        }
        for (int i = 0; i < dimmerValues.length; ++i) {
            if (dimmerValues[i] != info.dimmerValues[i]) {
                dimmerValues[i] = info.dimmerValues[i];
                anythingChanged = true;
            }
        }
        for (int i = 0; i < switcherValues.length; ++i) {
            if (switcherValues[i] != info.switcherValues[i]) {
                switcherValues[i] = info.switcherValues[i];
                anythingChanged = true;
            }
        }
        return anythingChanged;
    }

    public void asyncTryToDiscover() {
        if (ipAddress == null) {
            Log.d(LOG_TAG, "Discover failed: IP address is not set");
            return;
        }
        Http.doAsyncRequest(
                "http://" + ipAddress + "/get_info",
                null,
                httpPassword,
                null,
                5,
                new Http.Listener() {
                    @Override
                    public void onResponse(Http.Response response) {
                        if (response.getHttpCode() != HttpURLConnection.HTTP_OK) {
                            Log.d(LOG_TAG, "Discover failed: got bad response code " + response.getHttpCode());
                            return;
                        }
                        DeviceInfo info;
                        try {
                            info = DeviceInfo.parseJson(response.getDataAsStr());
                        } catch (JSONException | InvalidMacAddressException e) {
                            Log.d(LOG_TAG, "Discover failed: could not parse response: " + e.getMessage());
                            return;
                        }
                        synchronized (this) {
                            if (info.getMacAddress().equals(macAddress)) {
                                if (sync(info) && listener != null) {
                                    listener.onDeviceInfoChanged();
                                }
                                if (!discovered && listener != null) {
                                    listener.onDeviceDiscovered();
                                }
                                discovered = true;
                                Log.d(LOG_TAG, "Device " + macAddress + " discovered");
                            } else {
                                Log.d(LOG_TAG, "Discover failed: MAC address differs");
                            }
                        }
                    }

                    @Override
                    public void onError(IOException exception) {
                        Log.d(LOG_TAG, "Discover failed with exception: " + exception.getMessage());
                    }
                }
        );
    }
}
