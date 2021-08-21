package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.widget.TextView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Timer;
import java.util.TimerTask;

import ru.tsar_ioann.smarthome.*;

public class DeviceConnected extends BaseScreen {
    public DeviceConnected(CommonData commonData) {
        super(commonData);

        String networkSsid = commonData.getHomeNetworkSsid();
        assert networkSsid != null;

        Activity activity = commonData.getActivity();
        TextView txtDeviceConnected = activity.findViewById(R.id.txtDeviceConnected);
        txtDeviceConnected.setText(tr(R.string.device_connected_successfully, networkSsid));

        DeviceInfo deviceInfo = commonData.getNewDeviceInfo();
        assert deviceInfo != null;

        Http.doAsyncRequest(
                "http://" + deviceInfo.getIpAddress() + "/get_info",
                null,
                SMART_HOME_DEVICE_DEFAULT_HTTP_PASSWORD,
                null,
                500,
                new Http.Listener() {
                    @Override
                    public void onResponse(Http.Response response) {
                        if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
                            DeviceInfo deviceInfoAgain = ResponseParser.parseMacAndName(response.getDataAsStr());
                            if (deviceInfoAgain != null && deviceInfo.getMacAddress().equals(deviceInfoAgain.getMacAddress())) {
                                // Disable access point on device
                                Http.doAsyncRequest(
                                        "http://" + deviceInfo.getIpAddress() + "/turn_off_ap",
                                        null,
                                        SMART_HOME_DEVICE_DEFAULT_HTTP_PASSWORD,
                                        null,
                                        3,
                                        null
                                );

                                new Timer().schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        commonData.getDevices().addDevice(deviceInfo);
                                        activity.runOnUiThread(() -> commonData.getScreenLauncher().launchScreen(ScreenId.MAIN));
                                    }
                                }, 350);
                            } else {
                                showErrorAndGoToMainScreen(tr(R.string.device_unexpected_response));
                            }
                        } else {
                            showErrorAndGoToMainScreen(tr(R.string.device_bad_response_code));
                        }
                    }

                    @Override
                    public void onError(IOException exception) {
                        showErrorAndGoToMainScreen(tr(R.string.device_connect_failed));
                    }
                }
        );
    }

    @Override
    public int getViewFlipperChildId() {
        return 5;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return false;
    }
}
