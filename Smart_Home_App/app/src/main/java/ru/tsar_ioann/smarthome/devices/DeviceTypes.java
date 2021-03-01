package ru.tsar_ioann.smarthome.devices;

public class DeviceTypes {
    private static final short TEST_DEVICE_WITH_LED = 1;

    public static Device getDeviceByType(short deviceType) {
        switch (deviceType) {
            case TEST_DEVICE_WITH_LED:
                return new TestDeviceWithInvertedLED();
        }
        return null;
    }
}
