package ru.tsar_ioann.smarthome;

import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Utils {
    private static final String MAC_ADDRESS_REGEX = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})";

    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            Log.d("Utils", "Failed to URLEncode value, returning as is");
            return value;
        }
    }

    public static boolean isValidMacAddress(String macAddress) {
        return macAddress.matches(MAC_ADDRESS_REGEX);
    }
}
