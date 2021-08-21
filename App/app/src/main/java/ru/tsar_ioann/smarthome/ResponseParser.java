package ru.tsar_ioann.smarthome;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResponseParser {
    public static DeviceInfo parseMacAndName(String deviceInfo) {
        Pattern macAndName = Pattern.compile("MAC=((?:[0-9A-Fa-f]{2}[:-]){5}(?:[0-9A-Fa-f]{2}));NAME=(.+)");
        Matcher matcher = macAndName.matcher(deviceInfo);
        if (matcher.matches()) {
            return new DeviceInfo(matcher.group(1), matcher.group(2));
        }
        return null;
    }
}
