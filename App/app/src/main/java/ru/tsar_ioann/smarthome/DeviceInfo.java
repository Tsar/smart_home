package ru.tsar_ioann.smarthome;

import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public class DeviceInfo {
    public static final String ACCESS_POINT_ADDRESS = "http://192.168.4.1";
    public static final String ACCESS_POINT_PASSPHRASE = "setup12345";
    public static final String DEFAULT_HTTP_PASSWORD = "12345";

    public static final String HEADER_PASSWORD = "Password";

    public static final class Handlers {
        public static final String GET_INFO             = "/get_info?binary&v=4";
        public static final String SETUP_WIFI           = "/setup_wifi";
        public static final String GET_SETUP_WIFI_STATE = "/get_setup_wifi_state";
        public static final String TURN_OFF_AP          = "/turn_off_ap";
        public static final String SET_VALUES           = "/set_values";
        public static final String SET_SETTINGS         = "/set_settings";
        public static final String UPDATE_FIRMWARE      = "/fw_update";
    }

    public static final int MIN_VALUE_CHANGE_STEP = 1;
    public static final int MAX_VALUE_CHANGE_STEP = 99;
    public static final int MIN_LIGHTNESS_MICROS = 50;
    public static final int MAX_LIGHTNESS_MICROS = 9950;

    public static final String DIMMER_PREFIX = "dim";
    public static final String SWITCHER_PREFIX = "sw";

    public static final int DIMMER_VALUE_AFTER_BOOT_MEANS_LOAD_SAVED_VALUES = 0xFFFF;
    public static final int SWITCHER_VALUE_AFTER_BOOT_MEANS_LOAD_SAVED_VALUES = 0xFF;

    public static class BaseSettings {
        public final byte pin;
        public boolean active;
        public int order;

        public BaseSettings(byte pin) {
            this.pin = pin;
        }
    }

    public static class DimmerSettings extends BaseSettings {
        public int valueChangeStep;
        public int minLightnessMicros;
        public int maxLightnessMicros;

        public DimmerSettings(boolean active, int order) {
            super((byte) 0);
            this.active = active;
            this.order = order;
        }

        public DimmerSettings(byte pin, int valueChangeStep, int minLightnessMicros, int maxLightnessMicros) {
            super(pin);
            this.valueChangeStep = valueChangeStep;
            this.minLightnessMicros = minLightnessMicros;
            this.maxLightnessMicros = maxLightnessMicros;
        }
    }

    public static class SwitcherSettings extends BaseSettings {
        public boolean inverted;

        public SwitcherSettings(boolean active, int order) {
            super((byte) 0);
            this.active = active;
            this.order = order;
        }

        public SwitcherSettings(byte pin, boolean inverted) {
            super(pin);
            this.inverted = inverted;
        }
    }

    private static final String LOG_TAG = "DeviceInfo";

    private static final String KEY_PROTO_PREFIX = "proto-";
    private static final String KEY_FIRMWARE_PREFIX = "fw-";
    private static final String KEY_MAC_PREFIX = "mac-";
    private static final String KEY_NAME_PREFIX = "name-";
    private static final String KEY_IP_PREFIX = "ip-";
    private static final String KEY_PORT_PREFIX = "port-";
    private static final String KEY_PERMANENT_IP_PREFIX = "permanent-ip-";
    private static final String KEY_PASSWORD_PREFIX = "pwd-";
    private static final String KEY_STATE_CACHE_PREFIX = "state-cache-";

    private short protoVersion = 2;
    private short firmwareVersion = 0;
    private String macAddress = null;
    private String name = null;
    private String ipAddress = null;
    private int port = Http.DEFAULT_PORT;
    private boolean permanentIp = false;
    private String httpPassword = DEFAULT_HTTP_PASSWORD;

    private boolean discovered = false;
    private Listener listener = null;

    private byte inputPin;

    private int dimmersCount = 0;
    private int[] dimmerValues;
    private DimmerSettings[] dimmersSettings;
    private short dimmerValueAfterBoot;
    private OrderingKeeper dimmersOrder;

    private int switchersCount = 0;
    private boolean[] switcherValues;
    private SwitcherSettings[] switchersSettings;
    private byte switcherValueAfterBoot;
    private OrderingKeeper switchersOrder;

    public interface Listener {
        void onDeviceUpdated(DeviceInfo device);
    }

    public static class BinaryInfoParseException extends Exception {
        public BinaryInfoParseException(String message) {
            super(message);
        }
    }

    public DeviceInfo(String macAddress, String name, String ipAddress) {
        this.macAddress = macAddress;
        this.name = name;
        this.ipAddress = ipAddress;
    }

    public DeviceInfo(SharedPreferences storage, int idInStorage, Listener listener) {
        this.listener = listener;

        protoVersion = (short)storage.getInt(KEY_PROTO_PREFIX + idInStorage, 2);
        firmwareVersion = (short)storage.getInt(KEY_FIRMWARE_PREFIX + idInStorage, 0);
        macAddress = storage.getString(KEY_MAC_PREFIX + idInStorage, null);
        name = storage.getString(KEY_NAME_PREFIX + idInStorage, null);
        ipAddress = storage.getString(KEY_IP_PREFIX + idInStorage, null);
        if (macAddress == null || name == null || ipAddress == null) {
            // TODO: more reasonable reaction
            throw new RuntimeException("Devices local storage is broken!");
        }

        port = storage.getInt(KEY_PORT_PREFIX + idInStorage, Http.DEFAULT_PORT);
        permanentIp = storage.getBoolean(KEY_PERMANENT_IP_PREFIX + idInStorage, false);
        httpPassword = storage.getString(KEY_PASSWORD_PREFIX + idInStorage, DeviceInfo.DEFAULT_HTTP_PASSWORD);

        try {
            parseStateCache(storage.getString(KEY_STATE_CACHE_PREFIX + idInStorage, ""));
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Failed to parse state cache for device " + macAddress);
        }
    }

    public void saveToStorage(SharedPreferences.Editor editor, int idInStorage) {
        editor.putInt(KEY_PROTO_PREFIX + idInStorage, protoVersion);
        editor.putInt(KEY_FIRMWARE_PREFIX + idInStorage, firmwareVersion);
        editor.putString(KEY_MAC_PREFIX + idInStorage, macAddress);
        editor.putString(KEY_NAME_PREFIX + idInStorage, name);
        editor.putString(KEY_IP_PREFIX + idInStorage, ipAddress);
        editor.putInt(KEY_PORT_PREFIX + idInStorage, port);
        editor.putBoolean(KEY_PERMANENT_IP_PREFIX + idInStorage, permanentIp);
        editor.putString(KEY_PASSWORD_PREFIX + idInStorage, httpPassword);

        String stateCache = "";
        try {
            stateCache = generateStateCache();
        } catch (JSONException e) {
            Log.d(LOG_TAG, "Failed to generate state cache for device " + macAddress);
        }
        editor.putString(KEY_STATE_CACHE_PREFIX + idInStorage, stateCache);
    }

    public DeviceInfo(byte[] binaryInfo) throws BinaryInfoParseException {
        updateFromBytes(binaryInfo);
    }

    private void updateFromBytes(byte[] binaryInfo) throws BinaryInfoParseException {
        ByteBuffer buffer = ByteBuffer.allocate(binaryInfo.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(binaryInfo);
        buffer.position(0);

        try {
            protoVersion = buffer.getShort();  // response format version
            if (protoVersion != 3 && protoVersion != 4) {
                // older firmwares put MAC at the beginning
                protoVersion = 2;
                buffer.position(0);
            }
            firmwareVersion = (protoVersion >= 4) ? buffer.getShort() : 0;

            byte[] macAddressBytes = new byte[6];
            buffer.get(macAddressBytes);
            final String macAddressParsed = Utils.macAddressBytesToString(macAddressBytes);
            if (macAddress == null) {
                macAddress = macAddressParsed;
            } else if (!macAddress.equals(macAddressParsed)) {
                throw new BinaryInfoParseException("MAC address differs");
            }

            short nameLength = buffer.getShort();
            byte[] nameBytes = new byte[nameLength];
            buffer.get(nameBytes);
            name = new String(nameBytes, StandardCharsets.UTF_8);

            inputPin = buffer.get();

            dimmersCount = buffer.get();
            if (dimmerValues == null || dimmerValues.length != dimmersCount) {
                dimmerValues = new int[dimmersCount];
            }
            if (dimmersSettings == null || dimmersSettings.length != dimmersCount) {
                dimmersSettings = new DimmerSettings[dimmersCount];
            }
            for (int i = 0; i < dimmersCount; ++i) {
                byte pin = buffer.get();
                dimmerValues[i] = buffer.getShort();
                int valueChangeStep = buffer.getShort();
                int minLightnessMicros = buffer.getShort();
                int maxLightnessMicros = buffer.getShort();
                dimmersSettings[i] = new DimmerSettings(pin, valueChangeStep, minLightnessMicros, maxLightnessMicros);
            }
            dimmerValueAfterBoot = supportsValueAfterBoot() ? buffer.getShort() : (short) DIMMER_VALUE_AFTER_BOOT_MEANS_LOAD_SAVED_VALUES;

            switchersCount = buffer.get();
            if (switcherValues == null || switcherValues.length != switchersCount) {
                switcherValues = new boolean[switchersCount];
            }
            if (switchersSettings == null || switchersSettings.length != switchersCount) {
                switchersSettings = new SwitcherSettings[switchersCount];
            }
            for (int i = 0; i < switchersCount; ++i) {
                byte pin = buffer.get();
                switcherValues[i] = buffer.get() != 0;
                boolean inverted = buffer.get() != 0;
                switchersSettings[i] = new SwitcherSettings(pin, inverted);
            }
            switcherValueAfterBoot = supportsValueAfterBoot() ? buffer.get() : (byte) SWITCHER_VALUE_AFTER_BOOT_MEANS_LOAD_SAVED_VALUES;

            parseAdditionalBlob(buffer);

            switchersOrder = new OrderingKeeper(switchersSettings);
            dimmersOrder = new OrderingKeeper(dimmersSettings);
        } catch (BufferUnderflowException e) {
            throw new BinaryInfoParseException("Binary info too short: " + e.getMessage());
        }
    }

    private void parseAdditionalBlob(ByteBuffer buffer) {
        try {
            buffer.getShort();  // blob length
            for (int i = 0; i < dimmersCount; ++i) {
                dimmersSettings[i].active = buffer.get() != 0;
                dimmersSettings[i].order = buffer.get();
            }
            for (int i = 0; i < switchersCount; ++i) {
                switchersSettings[i].active = buffer.get() != 0;
                switchersSettings[i].order = buffer.get();
            }
        } catch (BufferUnderflowException e) {
            Log.d(LOG_TAG, "Failed to parse additional blob, fallback to default");
            for (int i = 0; i < dimmersCount; ++i) {
                dimmersSettings[i].active = true;
                dimmersSettings[i].order = i;
            }
            for (int i = 0; i < switchersCount; ++i) {
                switchersSettings[i].active = true;
                switchersSettings[i].order = i;
            }
        }
    }

    public byte[] generateAdditionalBlob() {
        ByteBuffer buffer = ByteBuffer.allocate((dimmersCount + switchersCount) * 2)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < dimmersCount; ++i) {
            buffer.put((byte)(dimmersSettings[i].active ? 1 : 0));
            buffer.put((byte)dimmersSettings[i].order);
        }
        for (int i = 0; i < switchersCount; ++i) {
            buffer.put((byte)(switchersSettings[i].active ? 1 : 0));
            buffer.put((byte)switchersSettings[i].order);
        }
        return buffer.array();
    }

    private void parseStateCache(String stateCache) throws JSONException {
        JSONObject state = new JSONObject(stateCache);

        {
            JSONArray dimmersArr = state.getJSONArray("dimmers");
            dimmersCount = dimmersArr.length();

            if (dimmerValues == null || dimmerValues.length != dimmersCount) {
                dimmerValues = new int[dimmersCount];
            }
            if (dimmersSettings == null || dimmersSettings.length != dimmersCount) {
                dimmersSettings = new DimmerSettings[dimmersCount];
            }

            for (int i = 0; i < dimmersCount; ++i) {
                JSONObject dimmerObj = dimmersArr.getJSONObject(i);
                dimmerValues[i] = dimmerObj.getInt("value");
                dimmersSettings[i] = new DimmerSettings(dimmerObj.getBoolean("active"), dimmerObj.getInt("order"));
            }

            dimmersOrder = new OrderingKeeper(dimmersSettings);
        }

        {
            JSONArray switchersArr = state.getJSONArray("switchers");
            switchersCount = switchersArr.length();

            if (switcherValues == null || switcherValues.length != switchersCount) {
                switcherValues = new boolean[switchersCount];
            }
            if (switchersSettings == null || switchersSettings.length != switchersCount) {
                switchersSettings = new SwitcherSettings[switchersCount];
            }

            for (int i = 0; i < switchersCount; ++i) {
                JSONObject switcherObj = switchersArr.getJSONObject(i);
                switcherValues[i] = switcherObj.getBoolean("value");
                switchersSettings[i] = new SwitcherSettings(switcherObj.getBoolean("active"), switcherObj.getInt("order"));
            }

            switchersOrder = new OrderingKeeper(switchersSettings);
        }
    }

    private String generateStateCache() throws JSONException {
        if (dimmerValues == null || switcherValues == null || dimmersSettings == null || switchersSettings == null) {
            return "";
        }

        JSONArray dimmersArr = new JSONArray();
        for (int i = 0; i < dimmersCount; ++i) {
            JSONObject dimmerObj = new JSONObject();
            dimmerObj.put("value", dimmerValues[i]);
            dimmerObj.put("active", dimmersSettings[i].active);
            dimmerObj.put("order", dimmersSettings[i].order);
            dimmersArr.put(dimmerObj);
        }

        JSONArray switchersArr = new JSONArray();
        for (int i = 0; i < switchersCount; ++i) {
            JSONObject switcherObj = new JSONObject();
            switcherObj.put("value", switcherValues[i]);
            switcherObj.put("active", switchersSettings[i].active);
            switcherObj.put("order", switchersSettings[i].order);
            switchersArr.put(switcherObj);
        }

        JSONObject result = new JSONObject();
        result.put("dimmers", dimmersArr);
        result.put("switchers", switchersArr);

        return result.toString();
    }

    public short getFirmwareVersion() {
        return firmwareVersion > 0 ? firmwareVersion : protoVersion;
    }

    public boolean supportsValueAfterBoot() {
        return protoVersion >= 3;
    }

    public boolean supportsFirmwareUpdateOverNetwork() {
        return firmwareVersion >= 4;
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

    public int getDimmersCount() {
        if (dimmerValues == null) {
            return 3;
        }
        return dimmersCount;
    }

    public int getActiveDimmersCount() {
        if (dimmersOrder == null) {
            return 3;
        }
        return dimmersOrder.getActiveCount();
    }

    public int getDimmerValue(int n) {
        if (n >= 0 && n < dimmersCount && dimmerValues != null) {
            return dimmerValues[n];
        }
        return 500;
    }

    public short getDimmerValueAfterBoot() {
        return dimmerValueAfterBoot;
    }

    public int getSwitchersCount() {
        if (switcherValues == null) {
            return 4;
        }
        return switchersCount;
    }

    public int getActiveSwitchersCount() {
        if (switchersOrder == null) {
            return 4;
        }
        return switchersOrder.getActiveCount();
    }

    public boolean getSwitcherValue(int n) {
        if (n >= 0 && n < switchersCount && switcherValues != null) {
            return switcherValues[n];
        }
        return false;
    }

    public byte getSwitcherValueAfterBoot() {
        return switcherValueAfterBoot;
    }

    public byte getInputPin() {
        return inputPin;
    }

    public DimmerSettings[] getDimmersSettings() {
        return dimmersSettings;
    }

    public OrderingKeeper getDimmersOrder() {
        return dimmersOrder;
    }

    public boolean isDimmerActive(int id) {
        if (dimmersSettings != null && dimmersSettings[id] != null) {
            return dimmersSettings[id].active;
        }
        return true;
    }

    public int getDimmerIndexByOrder(int order) {
        if (dimmersOrder != null) {
            return dimmersOrder.getIndex(order);
        }
        return order;
    }

    public SwitcherSettings[] getSwitchersSettings() {
        return switchersSettings;
    }

    public OrderingKeeper getSwitchersOrder() {
        return switchersOrder;
    }

    public boolean isSwitcherActive(int id) {
        if (switchersSettings != null && switchersSettings[id] != null) {
            return switchersSettings[id].active;
        }
        return true;
    }

    public int getSwitcherIndexByOrder(int order) {
        if (switchersOrder != null) {
            return switchersOrder.getIndex(order);
        }
        return order;
    }

    public void setParams(String name, String ipAddress, int port, boolean permanentIp, String httpPassword) {
        synchronized (this) {
            this.name = name;
            this.ipAddress = ipAddress;
            this.port = port;
            this.permanentIp = permanentIp;
            this.httpPassword = httpPassword;
            onDeviceUpdated();
        }
    }

    public void setDiscovered(boolean discovered) {
        this.discovered = discovered;
    }

    public void setDimmerValue(int n, int value) {
        if (n >= 0 && n < dimmersCount && dimmerValues != null) {
            dimmerValues[n] = value;
            onDeviceUpdated();
        }
    }

    public void setSwitcherValue(int n, boolean value) {
        if (n >= 0 && n < switchersCount && switcherValues != null) {
            switcherValues[n] = value;
            onDeviceUpdated();
        }
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void asyncTryToDiscover() {
        if (ipAddress == null) {
            Log.d(LOG_TAG, "Discover failed: IP address is not set");
            return;
        }
        Http.asyncRequest(
                getHttpAddress() + Handlers.GET_INFO,
                null,
                Utils.createMapWithOneElement(HEADER_PASSWORD, httpPassword),
                null,
                5,
                new Http.Listener() {
                    @Override
                    public void onResponse(Http.Response response) {
                        if (response.getHttpCode() != HttpURLConnection.HTTP_OK) {
                            Log.d(LOG_TAG, "Discover failed: got bad response code " + response.getHttpCode());
                            return;
                        }
                        synchronized (DeviceInfo.this) {
                            try {
                                updateFromBytes(response.getData());
                                onDeviceUpdated();
                                discovered = true;
                                Log.d(LOG_TAG, "Device " + macAddress + " discovered");
                            } catch (BinaryInfoParseException e) {
                                Log.d(LOG_TAG, "Discover failed: could not parse response: " + e.getMessage());
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

    private void onDeviceUpdated() {
        if (listener != null) {
            listener.onDeviceUpdated(this);
        };
    }
}
