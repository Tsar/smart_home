package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.net.Network;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;

import ru.tsar_ioann.smarthome.*;

public class ConnectingFreshDevice extends BaseScreen {
    private final TextView txtConnecting;
    private final ListView lstNetworks;
    private final Button btnSetNetwork;
    private final ArrayAdapter<String> lstNetworksAdapter;

    public ConnectingFreshDevice(CommonData commonData) {
        super(commonData);

        Activity activity = commonData.getActivity();
        txtConnecting = activity.findViewById(R.id.txtConnecting);
        lstNetworks = activity.findViewById(R.id.lstNetworks);
        btnSetNetwork = activity.findViewById(R.id.btnSetNetwork);

        lstNetworksAdapter = new ArrayAdapter<>(activity, android.R.layout.simple_list_item_1);
        lstNetworks.setAdapter(lstNetworksAdapter);
        lstNetworks.setOnItemClickListener((adapterView, view, position, id) -> {
            HomeNetworkSettings screen = (HomeNetworkSettings)commonData
                    .getScreenLauncher().launchScreen(ScreenId.HOME_NETWORK_SETTINGS);
            screen.setNetworkSsid((String)adapterView.getItemAtPosition(position));
        });

        btnSetNetwork.setOnClickListener(v -> commonData.getScreenLauncher().launchScreen(ScreenId.HOME_NETWORK_SETTINGS));
    }

    public void connectToDevice(String deviceSsid) {
        txtConnecting.setText(R.string.connecting_to_device);
        lstNetworks.setVisibility(View.GONE);
        btnSetNetwork.setVisibility(View.GONE);

        CommonData commonData = getCommonData();
        Wifi wifi = commonData.getWifi();
        wifi.connectToWifi(deviceSsid, SMART_HOME_DEVICE_AP_PASSPHRASE, new Wifi.ConnectListener() {
            @Override
            public void onConnected(Network network) {
                commonData.setNewDeviceNetwork(network);
                try {
                    Http.Response response = Http.doRequest(
                            SMART_HOME_DEVICE_AP_ADDRESS + "/get_info",
                            null,
                            SMART_HOME_DEVICE_DEFAULT_HTTP_PASSWORD,
                            network,
                            3
                    );

                    if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
                        DeviceInfo deviceInfo = ResponseParser.parseMacAndName(response.getDataAsStr());
                        if (deviceInfo != null) {
                            commonData.setNewDeviceInfo(deviceInfo);
                            commonData.getActivity().runOnUiThread(() -> {
                                txtConnecting.setText(R.string.connected_to_device);
                                Set<String> networks = new HashSet<>();
                                lstNetworksAdapter.clear();

                                lstNetworks.setVisibility(View.VISIBLE);
                                btnSetNetwork.setVisibility(View.VISIBLE);

                                wifi.scan(300, new Wifi.ScanListener() {
                                    @Override
                                    public void onWifiFound(String ssid) {
                                        if (!ssid.isEmpty() && !ssid.equals(deviceSsid) && !networks.contains(ssid)) {
                                            networks.add(ssid);
                                            lstNetworksAdapter.add(ssid);
                                        }
                                    }

                                    @Override
                                    public void onScanFinished() {
                                        // TODO: handle
                                    }
                                });
                            });
                        } else {
                            disconnectAndShowErrorAndGoToMainScreen(tr(R.string.device_unexpected_response));
                        }
                    } else {
                        disconnectAndShowErrorAndGoToMainScreen(tr(R.string.device_bad_response_code));
                    }
                } catch (IOException e) {
                    Log.d("DEVICE_RESP", "Exception: " + e.getMessage());
                    disconnectAndShowErrorAndGoToMainScreen(tr(R.string.device_bad_connect));
                }
            }

            @Override
            public void onConnectFailed() {
                commonData.setNewDeviceNetwork(null);
                disconnectAndShowErrorAndGoToMainScreen(tr(R.string.device_connect_failed));
            }

            @Override
            public void onConnectLost() {
                commonData.setNewDeviceNetwork(null);
                disconnectAndShowErrorAndGoToMainScreen(tr(R.string.device_connection_lost));
            }
        });
    }

    @Override
    public int getViewFlipperChildId() {
        return 3;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return false;
    }
}
