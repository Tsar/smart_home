package ru.tsar_ioann.smarthome;

import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;

public class FirmwareUpdater {
    private static final String LOG_TAG = "FirmwareUpdater";

    private static final String FIRMWARE_UPDATES_ADDRESS = "https://smarthome.tsar-ioann.ru/";
    private static final String LAST_FIRMWARE_INFO_FILENAME = "last_firmware_info.json";

    private int lastFirmwareVersion = -1;
    private String lastFirmwareFileUrl = null;

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
                            if (!info.has("version")) {
                                Log.d(LOG_TAG, ANY_ERROR_PREFIX + ": no key 'version' in JSON [" + respStr + "]");
                                return;
                            }
                            if (!info.has("file")) {
                                Log.d(LOG_TAG, ANY_ERROR_PREFIX + ": no key 'file' in JSON [" + respStr + "]");
                                return;
                            }

                            lastFirmwareVersion = info.getInt("version");
                            lastFirmwareFileUrl = info.getString("file");
                            Log.d(LOG_TAG, "Got info about last available firmware: version " + lastFirmwareVersion + ", file '" + lastFirmwareFileUrl + "'");
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

    public int getLastFirmwareVersion() {
        return lastFirmwareVersion;
    }
}
