package ru.tsar_ioann.smarthome;

public class DeviceInfo {
    private final String macAddress;
    private String name;
    private String ipAddress;
    private String homeNetworkSsid;

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

    public String getHomeNetworkSsid() {
        return homeNetworkSsid;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setHomeNetworkSsid(String homeNetworkSsid) {
        this.homeNetworkSsid = homeNetworkSsid;
    }
}
