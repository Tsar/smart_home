package ru.tsar_ioann.smarthome.screens;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.text.InputFilter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import java.io.IOException;
import java.net.HttpURLConnection;

import ru.tsar_ioann.smarthome.*;

public class ConfiguredDeviceParams extends BaseScreen {
    private final EditText edtCfgDevIpAddress;
    private final EditText edtCfgDevPort;
    private final EditText edtCfgDevPassword;

    @SuppressLint("SetTextI18n")
    public ConfiguredDeviceParams(CommonData commonData) {
        super(commonData);

        Activity activity = commonData.getActivity();
        edtCfgDevIpAddress = activity.findViewById(R.id.edtCfgDevIpAddress);
        edtCfgDevPort = activity.findViewById(R.id.edtCfgDevPort);
        CheckBox cbCfgDevIpIsStatic = activity.findViewById(R.id.cbCfgDevIpIsStatic);
        edtCfgDevPassword = activity.findViewById(R.id.edtCfgDevPassword);
        Button btnAddDevice = activity.findViewById(R.id.btnAddDevice);

        setupShowPasswordCheckBox(activity.findViewById(R.id.cbShowCfgDevPassword), edtCfgDevPassword);

        btnAddDevice.setOnClickListener(v -> {
            String ipAddress = edtCfgDevIpAddress.getText().toString();
            String portStr = edtCfgDevPort.getText().toString();
            if (!Utils.isValidIpAddress(ipAddress) || !Utils.isValidPort(portStr)) {
                showOkDialog(tr(R.string.error), tr(R.string.invalid_ip_or_port));
                return;
            }
            int port = Integer.parseInt(portStr);
            boolean permanentIp = cbCfgDevIpIsStatic.isChecked();
            String httpPassword = edtCfgDevPassword.getText().toString();

            final boolean edtCfgDevIpAddressWasEnabled = edtCfgDevIpAddress.isEnabled();
            final boolean edtCfgDevPortWasEnabled = edtCfgDevIpAddress.isEnabled();
            edtCfgDevIpAddress.setEnabled(false);
            edtCfgDevPort.setEnabled(false);
            cbCfgDevIpIsStatic.setEnabled(false);
            edtCfgDevPassword.setEnabled(false);
            btnAddDevice.setEnabled(false);

            Http.asyncRequest(
                    DeviceInfo.getHttpAddress(ipAddress, port) + DeviceInfo.Handlers.GET_INFO,
                    null,
                    Utils.createMapWithOneElement(DeviceInfo.HEADER_PASSWORD, httpPassword),
                    null,
                    5,
                    new Http.Listener() {
                        private void enableUI() {
                            edtCfgDevIpAddress.setEnabled(edtCfgDevIpAddressWasEnabled);
                            edtCfgDevPort.setEnabled(edtCfgDevPortWasEnabled);
                            cbCfgDevIpIsStatic.setEnabled(true);
                            edtCfgDevPassword.setEnabled(true);
                            btnAddDevice.setEnabled(true);
                        }

                        private void showErrorAndEnableUI(String message) {
                            activity.runOnUiThread(() -> showOkDialog(tr(R.string.error), message, (dialog, which) -> enableUI()));
                        }

                        @Override
                        public void onResponse(Http.Response response) {
                            final int respCode = response.getHttpCode();
                            if (respCode == HttpURLConnection.HTTP_OK) {
                                try {
                                    DeviceInfo deviceInfo = new DeviceInfo(response.getData());
                                    deviceInfo.setParams(deviceInfo.getName(), ipAddress, port, permanentIp, httpPassword);

                                    DeviceInfo existingDeviceWithSameMac = commonData.getDevices().getDeviceByMacAddress(deviceInfo.getMacAddress());
                                    if (existingDeviceWithSameMac != null) {
                                        if (existingDeviceWithSameMac.getIpAddress().equals(deviceInfo.getIpAddress())
                                                && existingDeviceWithSameMac.getPort() == deviceInfo.getPort()) {
                                            showErrorAndEnableUI(tr(R.string.device_already_added));
                                        } else {
                                            activity.runOnUiThread(() -> showYesNoDialog(
                                                    tr(R.string.question),
                                                    tr(R.string.device_already_added_with_other_ip),
                                                    (dialog, which) -> {
                                                        existingDeviceWithSameMac.setParams(
                                                                deviceInfo.getName(),
                                                                deviceInfo.getIpAddress(),
                                                                deviceInfo.getPort(),
                                                                deviceInfo.isPermanentIp(),
                                                                deviceInfo.getHttpPassword()
                                                        );
                                                        commonData.getScreenLauncher().launchScreen(ScreenId.MAIN);
                                                    },
                                                    (dialog, which) -> enableUI()
                                            ));
                                        }
                                    } else {
                                        commonData.getDevices().addOrUpdateDevice(deviceInfo);
                                        activity.runOnUiThread(() -> commonData.getScreenLauncher().launchScreen(ScreenId.MAIN));
                                    }
                                } catch (DeviceInfo.BinaryInfoParseException e) {
                                    showErrorAndEnableUI(tr(R.string.device_unexpected_response));
                                }
                            } else if (respCode == HttpURLConnection.HTTP_FORBIDDEN) {
                                showErrorAndEnableUI(tr(R.string.invalid_password));
                            } else {
                                showErrorAndEnableUI(tr(R.string.device_bad_response_code));
                            }
                        }

                        @Override
                        public void onError(IOException exception) {
                            showErrorAndEnableUI(tr(R.string.device_connect_failed));
                        }
                    }
            );
        });

        edtCfgDevIpAddress.setFilters(new InputFilter[]{new Utils.IpAddressInputFilter()});
        edtCfgDevPort.setFilters(new InputFilter[]{new Utils.PortInputFilter()});

        edtCfgDevIpAddress.setEnabled(true);
        edtCfgDevPort.setEnabled(true);
        cbCfgDevIpIsStatic.setEnabled(true);
        edtCfgDevPassword.setEnabled(true);
        btnAddDevice.setEnabled(true);

        edtCfgDevIpAddress.setText("");
        edtCfgDevPort.setText(Integer.toString(Http.DEFAULT_PORT));
        cbCfgDevIpIsStatic.setChecked(false);
        cbCfgDevIpIsStatic.jumpDrawablesToCurrentState();
        edtCfgDevPassword.setText("");

        edtCfgDevIpAddress.requestFocus();
    }

    @SuppressLint("SetTextI18n")
    public void setIpAddressAndPort(String ipAddress, int port) {
        edtCfgDevIpAddress.setEnabled(false);
        edtCfgDevPort.setEnabled(false);

        edtCfgDevIpAddress.setText(ipAddress);
        edtCfgDevPort.setText(Integer.toString(port));

        edtCfgDevPassword.requestFocus();
    }

    @Override
    public int getViewFlipperChildId() {
        return 7;
    }
}
