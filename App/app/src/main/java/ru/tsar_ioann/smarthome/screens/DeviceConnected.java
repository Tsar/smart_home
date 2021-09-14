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

        Http.asyncRequest(
                deviceInfo.getHttpAddress() + "/get_info?binary",
                null,
                DeviceInfo.DEFAULT_HTTP_PASSWORD,
                null,
                500,
                new Http.Listener() {
                    @Override
                    public void onResponse(Http.Response response) {
                        if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
                            try {
                                DeviceInfo deviceInfoAgain = new DeviceInfo(response.getData());
                                if (deviceInfo.getMacAddress().equals(deviceInfoAgain.getMacAddress())) {
                                    // Disable access point on device
                                    Http.asyncRequest(
                                            deviceInfo.getHttpAddress() + "/turn_off_ap",
                                            null,
                                            DeviceInfo.DEFAULT_HTTP_PASSWORD,
                                            null,
                                            3,
                                            null
                                    );

                                    new Timer().schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            DevicesList.AddOrUpdateResult addOrUpdate = commonData.getDevices().addOrUpdateDevice(deviceInfo);
                                            activity.runOnUiThread(() -> {
                                                commonData.getScreenLauncher().launchScreen(ScreenId.MAIN);
                                                if (addOrUpdate == DevicesList.AddOrUpdateResult.UPDATE) {
                                                    showOkDialog(tr(R.string.warning), tr(R.string.device_was_already_in_the_list));
                                                }
                                            });
                                        }
                                    }, 350);
                                } else {
                                    showErrorAndGoToMainScreen(tr(R.string.device_unexpected_response));
                                }
                            } catch (DeviceInfo.BinaryInfoParseException e) {
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
}
