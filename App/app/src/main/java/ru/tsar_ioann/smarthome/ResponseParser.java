package ru.tsar_ioann.smarthome;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResponseParser {
    private static final Pattern MAC_EQUALS = Pattern.compile("MAC=((?:[0-9A-Fa-f]{2}[:-]){5}(?:[0-9A-Fa-f]{2}))");
    private static final Pattern MAC_AND_NAME = Pattern.compile(MAC_EQUALS + ";NAME=(.+)");

    public static String parseMac(String macString) {
        Matcher matcher = MAC_EQUALS.matcher(macString);
        if (matcher.matches()) {
            return matcher.group(1);
        }
        return null;
    }

    public static DeviceInfo parseMacAndName(String deviceInfo) {
        Matcher matcher = MAC_AND_NAME.matcher(deviceInfo);
        if (matcher.matches()) {
            return new DeviceInfo(matcher.group(1), matcher.group(2));
        }
        return null;
    }
}
