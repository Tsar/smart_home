package ru.tsar_ioann.smarthome;

public class DeviceParams {
    private int nameId;
    private short deviceType;
    private short deviceId;

    public DeviceParams(int nameId, int uuid) {
        this.nameId = nameId;
        this.deviceType = (short)((uuid >> 16) & 0xFFFF);
        this.deviceId = (short)(uuid & 0xFFFF);
    }

    public int getNameId() {
        return nameId;
    }

    public short getDeviceType() {
        return deviceType;
    }

    public short getDeviceId() {
        return deviceId;
    }
}
