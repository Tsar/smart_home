package ru.tsar_ioann.smarthome;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Arrays;
import java.util.HashSet;
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

    public void asyncUpdateFirmware(DeviceInfo device) {
        if (lastFirmwareInfo == null) {
            throw new RuntimeException("Firmware update was started without info about it");
        }

        if (lastFirmwareBinary == null || !Utils.sha256(lastFirmwareBinary).equalsIgnoreCase(lastFirmwareInfo.sha256)) {
            lastFirmwareBinary = null;

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
                            final byte[] firmwareBinary = response.getData();
                            if (Utils.sha256(firmwareBinary).equalsIgnoreCase(lastFirmwareInfo.sha256)) {
                                lastFirmwareBinary = firmwareBinary;
                            } else {
                                // TODO
                            }
                        }

                        @Override
                        public void onError(IOException exception) {
                            // TODO
                        }
                    },
                    3000,
                    15000
            );
        }

        if (lastFirmwareBinary == null) {
            return;
        }

        Log.d(LOG_TAG, "Uploading firmware to device; size: " + lastFirmwareBinary.length + " bytes");
        Http.asyncRequest(
                device.getHttpAddress() + DeviceInfo.Handlers.UPDATE_FIRMWARE,
                null /* TODO: multipart form-data */,
                null,
                null,
                1,
                new Http.Listener() {
                    @Override
                    public void onResponse(Http.Response response) {

                    }

                    @Override
                    public void onError(IOException exception) {

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
