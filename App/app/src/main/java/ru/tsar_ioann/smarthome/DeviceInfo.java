package ru.tsar_ioann.smarthome;

import android.util.Log;

import java.io.IOException;
import java.net.HttpURLConnection;

public class DeviceInfo {
    private static final String LOG_TAG = "DeviceInfo";

    private final String macAddress;
    private String name;
    private String ipAddress = null;
    private String httpPassword = "12345";  // work on this
    private boolean discovered = false;
    private Listener listener = null;

    public interface Listener {
        void onDeviceInfoChanged();
        void onDeviceDiscovered();
    }

    public DeviceInfo(String macAddress, String name) {
        this.macAddress = macAddress;
        this.name = name;
    }

    public DeviceInfo(String macAddress, String name, String ipAddress, Listener listener) {
        this.macAddress = macAddress;
        this.name = name;
        this.ipAddress = ipAddress;
        this.listener = listener;
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

    public void setListener(Listener listener) {
        this.listener = listener;
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
                        DeviceInfo info = ResponseParser.parseMacAndName(response.getDataAsStr());
                        if (info == null) {
                            Log.d(LOG_TAG, "Discover failed: could not parse response");
                            return;
                        }
                        synchronized (this) {
                            if (info.getMacAddress().equals(macAddress)) {
                                if (!info.getName().equals(name)) {
                                    name = info.getName();
                                    if (listener != null) {
                                        listener.onDeviceInfoChanged();
                                    }
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
