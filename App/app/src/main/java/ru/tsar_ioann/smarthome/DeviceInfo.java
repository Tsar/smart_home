package ru.tsar_ioann.smarthome;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.Iterator;

public class DeviceInfo {
    public static final String ACCESS_POINT_ADDRESS = "http://192.168.4.1";
    public static final String ACCESS_POINT_PASSPHRASE = "setup12345";
    public static final String DEFAULT_HTTP_PASSWORD = "12345";

    private static final String LOG_TAG = "DeviceInfo";

    private static final int DIMMERS_COUNT = 3;
    private static final int SWITCHERS_COUNT = 4;
    private static final String DIMMER_PREFIX = "dim";
    private static final String SWITCHER_PREFIX = "sw";

    private final String macAddress;
    private String name;
    private String ipAddress = null;
    private int port = Http.DEFAULT_PORT;
    private boolean permanentIp = false;
    private String httpPassword = DEFAULT_HTTP_PASSWORD;

    private boolean discovered = false;
    private Listener listener = null;

    private final int[] dimmerValues = new int[DIMMERS_COUNT];
    private final boolean[] switcherValues = new boolean[SWITCHERS_COUNT];

    public static class DimmerSettings {
        public int valueChangeStep;
        public int minLightnessMicros;
        public int maxLightnessMicros;

        public DimmerSettings(int valueChangeStep, int minLightnessMicros, int maxLightnessMicros) {
            this.valueChangeStep = valueChangeStep;
            this.minLightnessMicros = minLightnessMicros;
            this.maxLightnessMicros = maxLightnessMicros;
        }

        public boolean equals(DimmerSettings other) {
            if (other == null) {
                return false;
            }
            return valueChangeStep == other.valueChangeStep
                    && minLightnessMicros == other.minLightnessMicros
                    && maxLightnessMicros == other.maxLightnessMicros;
        }
    };

    private final DimmerSettings[] dimmersSettings = new DimmerSettings[DIMMERS_COUNT];

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

        JSONObject dimmersSettings = obj.getJSONObject("dimmers_settings");
        keys = dimmersSettings.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            if (key.startsWith(DIMMER_PREFIX)) {
                try {
                    int n = Integer.parseInt(key.substring(DIMMER_PREFIX.length()));
                    if (n >= 0 && n < DIMMERS_COUNT) {
                        JSONObject dimmerSettings = dimmersSettings.getJSONObject(key);
                        result.dimmersSettings[n] = new DimmerSettings(
                                dimmerSettings.getInt("value_change_step"),
                                dimmerSettings.getInt("min_lightness_micros"),
                                dimmerSettings.getInt("max_lightness_micros")
                        );
                    }
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

    public DeviceInfo(String macAddress, String name, String ipAddress, int port, boolean permanentIp, String httpPassword, Listener listener) {
        this(macAddress, name);
        this.ipAddress = ipAddress;
        this.port = port;
        this.permanentIp = permanentIp;
        this.httpPassword = httpPassword;
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

    public int getPort() {
        return port;
    }

    public boolean isPermanentIp() {
        return permanentIp;
    }

    public static String getHttpAddressWithoutPrefix(String ipAddress, int port) {
        return ipAddress + (port != Http.DEFAULT_PORT ? ":" + port : "");
    }

    public static String getHttpAddress(String ipAddress, int port) {
        return "http://" + getHttpAddressWithoutPrefix(ipAddress, port);
    }

    public String getHttpAddressWithoutPrefix() {
        return getHttpAddressWithoutPrefix(ipAddress, port);
    }

    public String getHttpAddress() {
        return getHttpAddress(ipAddress, port);
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

    public DimmerSettings[] getDimmersSettings() {
        return dimmersSettings;
    }

    public void setParams(String name, String ipAddress, int port, boolean permanentIp, String httpPassword) {
        synchronized (this) {
            this.name = name;
            this.ipAddress = ipAddress;
            this.port = port;
            this.permanentIp = permanentIp;
            this.httpPassword = httpPassword;
            if (listener != null) {
                listener.onDeviceInfoChanged();
            }
        }
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
        for (int i = 0; i < dimmersSettings.length; ++i) {
            if ((dimmersSettings[i] == null && info.dimmersSettings[i] != null) || !dimmersSettings[i].equals(info.dimmersSettings[i])) {
                dimmersSettings[i] = info.dimmersSettings[i];
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
        Http.asyncRequest(
                getHttpAddress() + "/get_info",
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
