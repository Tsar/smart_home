package ru.tsar_ioann.smarthome;

import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class FirmwareUpdater {
    private static final String LOG_TAG = "FirmwareUpdater";

    private static final String FIRMWARE_UPDATES_ADDRESS = "https://smarthome.tsar-ioann.ru/";
    private static final String LAST_FIRMWARE_INFO_FILENAME = "last_firmware_info.json";
    private static final String DEFAULT_LANG_KEY = "en";

    private static final String INFO_KEY_VERSION = "version";
    private static final String INFO_KEY_FILE = "file";
    private static final String INFO_KEY_SHA256 = "sha256";
    private static final String INFO_KEY_DESCRIPTION = "description";

    private static final Set<String> INFO_REQUIRED_KEYS = new HashSet<>(Arrays.asList(
            INFO_KEY_VERSION,
            INFO_KEY_FILE,
            INFO_KEY_SHA256,
            INFO_KEY_DESCRIPTION
    ));

    private static final String UPDATER_USERNAME = "admin";

    private static final String UPDATE_SUCCESS_RESPONSE = "<META http-equiv=\"refresh\" content=\"15;URL=/\">Update Success! Rebooting...";
    private static final String UPDATE_ERROR_PREFIX = "Update error: ";

    private static class FirmwareInfo {
        public final int version;
        public final String fileUrl;
        public final String sha256;
        public final JSONObject description;

        public FirmwareInfo(int version, String fileUrl, String sha256, JSONObject description) {
            this.version = version;
            this.fileUrl = fileUrl;
            this.sha256 = sha256;
            this.description = description;
        }
    }

    private FirmwareInfo lastFirmwareInfo = null;
    private byte[] lastFirmwareBinary = null;

    public void asyncCheckForFirmwareUpdates() {
        Http.asyncRequest(
                FIRMWARE_UPDATES_ADDRESS + LAST_FIRMWARE_INFO_FILENAME,
                null,
                null,
                null,
                2,
                new Http.Listener() {
                    private static final String ANY_ERROR_PREFIX = "Failed to check for firmware updates";

                    @Override
                    public void onResponse(Http.Response response) {
                        if (response.getHttpCode() != HttpURLConnection.HTTP_OK) {
                            Log.d(LOG_TAG, ANY_ERROR_PREFIX + ", got bad response code " + response.getHttpCode());
                            return;
                        }

                        final String respStr = response.getDataAsStr();
                        try {
                            JSONObject info = new JSONObject(respStr);
                            for (String requiredKey : INFO_REQUIRED_KEYS) {
                                if (!info.has(requiredKey)) {
                                    Log.d(LOG_TAG, ANY_ERROR_PREFIX + ": no key '" + requiredKey + "' in JSON [" + respStr + "]");
                                    return;
                                }
                            }
                            final JSONObject description = info.getJSONObject(INFO_KEY_DESCRIPTION);
                            if (!description.has(DEFAULT_LANG_KEY)) {
                                Log.d(LOG_TAG, ANY_ERROR_PREFIX + ": no default language key '" + DEFAULT_LANG_KEY
                                        + "' in '" + INFO_KEY_DESCRIPTION + "' in JSON [" + respStr + "]");
                                return;
                            }

                            lastFirmwareInfo = new FirmwareInfo(
                                    info.getInt(INFO_KEY_VERSION),
                                    info.getString(INFO_KEY_FILE),
                                    info.getString(INFO_KEY_SHA256),
                                    description
                            );
                            Log.d(LOG_TAG, "Got info about last available firmware: version " + lastFirmwareInfo.version + ", file '" + lastFirmwareInfo.fileUrl + "'");
                        } catch (JSONException e) {
                            Log.d(LOG_TAG, ANY_ERROR_PREFIX + ": could not parse JSON [" + respStr + "]");
                        }
                    }

                    @Override
                    public void onError(IOException exception) {
                        Log.d(LOG_TAG, ANY_ERROR_PREFIX + " with exception: " + exception.getMessage());
                    }
                }
        );
    }

    public interface Listener {
        void onSuccess();
        void onError(String message);
    }

    public void asyncUpdateFirmware(DeviceInfo device, Listener listener) {
        if (lastFirmwareInfo == null) {
            throw new RuntimeException("Firmware update was started without info about it");
        }

        if (lastFirmwareBinary != null && Utils.sha256(lastFirmwareBinary).equalsIgnoreCase(lastFirmwareInfo.sha256)) {
            Log.d(LOG_TAG, "Using cached firmware binary");
            uploadFirmwareToDevice(device, lastFirmwareBinary, listener);
        } else {
            Log.d(LOG_TAG, "Downloading latest firmware binary");
            Http.asyncRequest(
                    FIRMWARE_UPDATES_ADDRESS + lastFirmwareInfo.fileUrl,
                    null,
                    null,
                    null,
                    1,
                    new Http.Listener() {
                        @Override
                        public void onResponse(Http.Response response) {
                            if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
                                final byte[] firmwareBinary = response.getData();
                                if (Utils.sha256(firmwareBinary).equalsIgnoreCase(lastFirmwareInfo.sha256)) {
                                    lastFirmwareBinary = firmwareBinary;
                                    uploadFirmwareToDevice(device, lastFirmwareBinary, listener);
                                } else {
                                    listener.onError("sha256 does not match!");  // TODO: translated string
                                }
                            } else {
                                listener.onError("Bad response code from server!");  // TODO: translated string
                            }
                        }

                        @Override
                        public void onError(IOException exception) {
                            listener.onError("Failed to download firmware binary!");  // TODO: translated string
                        }
                    },
                    3000,
                    15000
            );
        }
    }

    private static void uploadFirmwareToDevice(DeviceInfo device, byte[] firmwareBinary, Listener listener) {
        Log.d(LOG_TAG, "Uploading firmware to device (size: " + firmwareBinary.length + " bytes)");

        String loginAndPassword = UPDATER_USERNAME + ":" + device.getHttpPassword();
        String boundary =  "*****" + System.currentTimeMillis() + "*****";

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic " + Base64.encodeToString(loginAndPassword.getBytes(StandardCharsets.UTF_8), Base64.DEFAULT));
        headers.put("Connection", "Keep-Alive");
        headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);

        ByteArrayOutputStream multipartData = new ByteArrayOutputStream();
        DataOutputStream outputStream = new DataOutputStream(multipartData);
        try {
            outputStream.writeBytes("--" + boundary + "\r\n");
            outputStream.writeBytes("Content-Disposition: form-data; name=\"firmware\"; filename=\"firmware.bin\"\r\n");
            outputStream.writeBytes("Content-Type: application/octet-stream\r\n");
            //outputStream.writeBytes("Content-Transfer-Encoding: binary\r\n");  // ESP fails to parse with this line
            outputStream.writeBytes("\r\n");
            outputStream.write(firmwareBinary, 0, firmwareBinary.length);
            outputStream.writeBytes("\r\n");
            outputStream.writeBytes("--" + boundary + "--\r\n");
            outputStream.flush();
        } catch (IOException exception) {
            listener.onError("Could not prepare data buffer!");  // TODO: translated string
            return;
        }

        Http.asyncRequest(
                device.getHttpAddress() + DeviceInfo.Handlers.UPDATE_FIRMWARE,
                multipartData.toByteArray(),
                headers,
                null,
                1,
                new Http.Listener() {
                    @Override
                    public void onResponse(Http.Response response) {
                        if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
                            final String responseStr = response.getDataAsStr();
                            if (responseStr.equals(UPDATE_SUCCESS_RESPONSE)) {
                                listener.onSuccess();
                            } else if (responseStr.startsWith(UPDATE_ERROR_PREFIX)) {
                                Log.d(LOG_TAG, "Firmware update failed, full response: [" + responseStr + "]");
                                listener.onError("Firmware update failed");  // TODO: translated string
                            } else {
                                Log.d(LOG_TAG, "Unrecognized answer from device, full response: [" + responseStr + "]");
                                listener.onError("Unrecognized answer from device");  // TODO: translated string
                            }
                        } else {
                            Log.d(LOG_TAG, "Firmware update failed, error code: " + response.getHttpCode());
                            listener.onError("Bad response code from device!");  // TODO: translated string
                        }
                    }

                    @Override
                    public void onError(IOException exception) {
                        listener.onError("Failed to upload firmware binary to device!");  // TODO: translated string
                    }
                },
                3500,
                20000
        );
    }

    public int getLastFirmwareVersion() {
        if (lastFirmwareInfo == null) {
            return -1;
        }
        return lastFirmwareInfo.version;
    }

    public String getLastFirmwareDescription(String langKey) {
        if (lastFirmwareInfo == null) {
            return "";
        }
        try {
            if (lastFirmwareInfo.description.has(langKey)) {
                return lastFirmwareInfo.description.getString(langKey);
            }
            return lastFirmwareInfo.description.getString(DEFAULT_LANG_KEY);
        } catch (JSONException ignored) {
            return "";
        }
    }
}
