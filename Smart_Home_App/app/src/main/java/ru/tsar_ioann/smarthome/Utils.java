package ru.tsar_ioann.smarthome;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Utils {
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static String bytesToHex(byte[] bytes) {
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

    public static void logDataHex(String logTag, String prefix, byte[] data) {
        Log.d(logTag, prefix + bytesToHex(data));
    }

    public static ByteBuffer createByteBuffer(byte[] byteArray) {
        ByteBuffer buffer = ByteBuffer.allocate(byteArray.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(byteArray);
        buffer.position(0);
        return buffer;
    }
}
