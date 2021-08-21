package ru.tsar_ioann.smarthome;

public class DeviceInfo {
    private final String macAddress;
    private String name;
    private String ipAddress;

    public DeviceInfo(String macAddress, String name) {
        this.macAddress = macAddress;
        this.name = name;
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

    public void setName(String name) {
        this.name = name;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
}
