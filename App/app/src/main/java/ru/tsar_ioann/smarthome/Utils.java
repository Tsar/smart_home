package ru.tsar_ioann.smarthome;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.text.InputFilter;
import android.text.Spanned;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Utils {
    private static final String MAC_ADDRESS_REGEX = "([0-9A-Fa-f]{2}[:-]){5}([0-9A-Fa-f]{2})";
    private static final String IP_ADDRESS_REGEX = "((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)";

    private static final int PORT_MIN_VALUE = 1;
    private static final int PORT_MAX_VALUE = 65535;

    public static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (UnsupportedEncodingException e) {
            Log.d("Utils", "Failed to URLEncode value, returning as is");
            return value;
        }
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 3 - 1];
        for (int j = 0; j < bytes.length; ++j) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 3] = HEX_ARRAY[v >>> 4];
            hexChars[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
            if (j < bytes.length - 1) {
                hexChars[j * 3 + 2] = ' ';
            }
        }
        return new String(hexChars);
    }

    public static String sha256(byte[] data) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            return new String(sha256.digest(data), StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No support for sha256!");
        }
    }

    public static String macAddressBytesToString(byte[] macAddressBytes) {
        StringBuilder sb = new StringBuilder(18);
        for (byte b : macAddressBytes) {
            if (sb.length() > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static boolean isValidMacAddress(String macAddress) {
        return macAddress.matches(MAC_ADDRESS_REGEX);
    }

    public static boolean isValidIpAddress(String ipAddress) {
        return ipAddress.matches(IP_ADDRESS_REGEX);
    }

    public static boolean isValidIntInRange(String str, int minValue, int maxValue) {
        try {
            int value = Integer.parseInt(str);
            return minValue <= value && value <= maxValue;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static boolean isValidPort(String portStr) {
        return isValidIntInRange(portStr, PORT_MIN_VALUE, PORT_MAX_VALUE);
    }

    public static String tr(Resources resources, int resId) {
        return resources.getString(resId);
    }

    public static String tr(Resources resources, int resId, Object... formatArgs) {
        return resources.getString(resId, formatArgs);
    }

    public static void showOkDialog(Context context, String title, String message) {
        showOkDialog(context, title, message, (dialogInterface, i) -> {});
    }

    public static void showOkDialog(Context context, String title, String message, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);
        builder.setPositiveButton(tr(context.getResources(), R.string.ok), listener);
        builder.show();
    }

    public static void showYesNoDialog(Context context, String title, String message, DialogInterface.OnClickListener yesListener, DialogInterface.OnClickListener noListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setCancelable(false);
        Resources resources = context.getResources();
        builder.setPositiveButton(tr(resources, R.string.yes), yesListener);
        builder.setNegativeButton(tr(resources, R.string.no), noListener);
        builder.show();
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

    public static class IntInRangeInputFilter implements InputFilter {
        private final int minValue;
        private final int maxValue;

        public IntInRangeInputFilter(int minValue, int maxValue) {
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            if (end > start) {
                String destText = dest.toString();
                String resultingText = destText.substring(0, dstart) + source.subSequence(start, end) + destText.substring(dend);
                if (!isValidIntInRange(resultingText, minValue, maxValue)) {
                    return "";
                }
            }
            return null;
        }
    }

    public static class PortInputFilter extends IntInRangeInputFilter {
        public PortInputFilter() {
            super(PORT_MIN_VALUE, PORT_MAX_VALUE);
        }
    }
}
