package ru.tsar_ioann.smarthome;

import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class Utils {
    private static final String MAC_ADDRESS_REGEX = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})";
    private static final String IP_ADDRESS_REGEX = "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";

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

    public static boolean isValidIpAddress(String ipAddress) {
        return ipAddress.matches(IP_ADDRESS_REGEX);
    }

    public static boolean isValidPort(String portStr) {
        try {
            int port = Integer.parseInt(portStr);
            return port > 0 && port < 65536;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static class IpAddressInputFilter implements InputFilter {
        private static final String IP_ADDRESS_PREFIX_REGEX = "^\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3}(\\.(\\d{1,3})?)?)?)?)?)?";

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (end > start) {
                String destText = dest.toString();
                String resultingText = destText.substring(0, dstart) + source.subSequence(start, end) + destText.substring(dend);
                if (resultingText.matches(IP_ADDRESS_PREFIX_REGEX)) {
                    String[] ipNumbers = resultingText.split("\\.");
                    for (String ipNumber : ipNumbers) {
                        try {
                            if (Integer.parseInt(ipNumber) > 255) {
                                return "";
                            }
                        } catch (NumberFormatException e) {
                            return "";
                        }
                    }
                } else {
                    return "";
                }
            }
            return null;
        }
    }

    public static class PortInputFilter implements InputFilter {
        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (end > start) {
                String destText = dest.toString();
                String resultingText = destText.substring(0, dstart) + source.subSequence(start, end) + destText.substring(dend);
                if (!isValidPort(resultingText)) {
                    return "";
                }
            }
            return null;
        }
    }
}
