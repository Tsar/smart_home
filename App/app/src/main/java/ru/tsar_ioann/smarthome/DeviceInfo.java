package ru.tsar_ioann.smarthome;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class DeviceInfo {
    public static final String ACCESS_POINT_ADDRESS = "http://192.168.4.1";
    public static final String ACCESS_POINT_PASSPHRASE = "setup12345";
    public static final String DEFAULT_HTTP_PASSWORD = "12345";

    public static final class Handlers {
        public static final String GET_INFO             = "/get_info?binary";
        public static final String SETUP_WIFI           = "/setup_wifi";
        public static final String GET_SETUP_WIFI_STATE = "/get_setup_wifi_state";
        public static final String TURN_OFF_AP          = "/turn_off_ap";
        public static final String SET_VALUES           = "/set_values";
        public static final String SET_SETTINGS         = "/set_settings";
    }

    public static final String DIMMER_PREFIX = "dim";
    public static final String SWITCHER_PREFIX = "sw";

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

    private static final String LOG_TAG = "DeviceInfo";

    private static final int DIMMERS_COUNT = 3;
    private static final int SWITCHERS_COUNT = 4;

    private final String macAddress;
    private String name;
    private String ipAddress = null;
    private int port = Http.DEFAULT_PORT;
    private boolean permanentIp = false;
    private String httpPassword = DEFAULT_HTTP_PASSWORD;

    private boolean discovered = false;
    private Listener listener = null;

    private final boolean[] switcherValues = new boolean[SWITCHERS_COUNT];
    private final boolean[] switchersInverted = new boolean[SWITCHERS_COUNT];

    private final int[] dimmerValues = new int[DIMMERS_COUNT];
    private final DimmerSettings[] dimmersSettings = new DimmerSettings[DIMMERS_COUNT];

    public interface Listener {
        void onDeviceInfoChanged();
        void onDeviceDiscovered();
    }

    public static class BinaryInfoParseException extends Exception {
        public BinaryInfoParseException(String message) {
            super(message);
        }
    }

    public DeviceInfo(String macAddress, String name, String ipAddress, int port, boolean permanentIp, String httpPassword, Listener listener) {
        this.macAddress = macAddress;
        this.name = name;
        this.ipAddress = ipAddress;
        this.port = port;
        this.permanentIp = permanentIp;
        this.httpPassword = httpPassword;
        this.listener = listener;
        Arrays.fill(this.dimmerValues, 500);
    }

    public DeviceInfo(byte[] binaryInfo) throws BinaryInfoParseException {
        ByteBuffer buffer = ByteBuffer.allocate(binaryInfo.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(binaryInfo);
        buffer.position(0);

        try {
            byte[] macAddressBytes = new byte[6];
            buffer.get(macAddressBytes);
            macAddress = Utils.macAddressBytesToString(macAddressBytes);

            short nameLength = buffer.getShort();
            byte[] nameBytes = new byte[nameLength];
            buffer.get(nameBytes);
            name = new String(nameBytes, StandardCharsets.UTF_8);

            byte dimmersCount = buffer.get();
            if (dimmersCount != DIMMERS_COUNT) {
                throw new BinaryInfoParseException("Unsupported dimmers count " + dimmersCount + ", expected " + DIMMERS_COUNT);
            }
            for (int i = 0; i < DIMMERS_COUNT; ++i) {
                dimmerValues[i] = buffer.getShort();
                int valueChangeStep = buffer.getShort();
                int minLightnessMicros = buffer.getShort();
                int maxLightnessMicros = buffer.getShort();
                dimmersSettings[i] = new DimmerSettings(valueChangeStep, minLightnessMicros, maxLightnessMicros);
            }

            byte switchersCount = buffer.get();
            if (switchersCount != SWITCHERS_COUNT) {
                throw new BinaryInfoParseException("Unsupported switchers count " + switchersCount + ", expected " + SWITCHERS_COUNT);
            }
            for (int i = 0; i < SWITCHERS_COUNT; ++i) {
                switcherValues[i] = (buffer.get() != 0);
                switchersInverted[i] = (buffer.get() != 0);
            }
        } catch (BufferUnderflowException e) {
            throw new BinaryInfoParseException("Binary info too short: " + e.getMessage());
        }
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

    public boolean[] getSwitchersInverted() {
        return switchersInverted;
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

    private boolean syncTo(DeviceInfo info) {
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
            if ((dimmersSettings[i] == null && info.dimmersSettings[i] != null)
                    || (dimmersSettings[i] != null && !dimmersSettings[i].equals(info.dimmersSettings[i]))) {
                dimmersSettings[i] = info.dimmersSettings[i];
                anythingChanged = true;
            }
        }
        for (int i = 0; i < switchersInverted.length; ++i) {
            if (switchersInverted[i] != info.switchersInverted[i]) {
                switchersInverted[i] = info.switchersInverted[i];
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
                getHttpAddress() + Handlers.GET_INFO,
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
                            info = new DeviceInfo(response.getData());
                        } catch (BinaryInfoParseException e) {
                            Log.d(LOG_TAG, "Discover failed: could not parse response: " + e.getMessage());
                            return;
                        }
                        synchronized (this) {
                            if (info.getMacAddress().equals(macAddress)) {
                                if (syncTo(info) && listener != null) {
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
