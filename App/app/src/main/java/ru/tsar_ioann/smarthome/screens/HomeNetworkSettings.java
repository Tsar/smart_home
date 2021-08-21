package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.net.Network;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Timer;
import java.util.TimerTask;

import ru.tsar_ioann.smarthome.CommonData;
import ru.tsar_ioann.smarthome.Http;
import ru.tsar_ioann.smarthome.R;
import ru.tsar_ioann.smarthome.Utils;

public class HomeNetworkSettings extends BaseScreen {
    private final EditText edtNetworkSsid;
    private final EditText edtPassphrase;

    public HomeNetworkSettings(CommonData commonData) {
        super(commonData);

        Activity activity = commonData.getActivity();
        edtNetworkSsid = activity.findViewById(R.id.edtNetworkSsid);
        edtPassphrase = activity.findViewById(R.id.edtPassphrase);
        CheckBox cbShowPassphrase = activity.findViewById(R.id.cbShowPassphrase);
        Button btnConnectDevice = activity.findViewById(R.id.btnConnectDevice);

        cbShowPassphrase.setOnCheckedChangeListener((buttonView, isChecked) -> {
            final int selStart = edtPassphrase.getSelectionStart();
            final int selEnd = edtPassphrase.getSelectionEnd();
            edtPassphrase.setTransformationMethod(isChecked ? null : new PasswordTransformationMethod());
            edtPassphrase.setSelection(selStart, selEnd);
        });

        Network deviceNetwork = commonData.getDeviceNetwork();
        btnConnectDevice.setOnClickListener(v -> {
            boolean edtNetworkSsidEnabled = edtNetworkSsid.isEnabled();
            edtNetworkSsid.setEnabled(false);
            edtPassphrase.setEnabled(false);
            btnConnectDevice.setEnabled(false);
            String data = "ssid=" + Utils.urlEncode(edtNetworkSsid.getText().toString())
                    + "&passphrase=" + Utils.urlEncode(edtPassphrase.getText().toString());
            Http.doAsyncRequest(
                    SMART_HOME_DEVICE_AP_ADDRESS + "/setup_wifi",
                    data.getBytes(),
                    SMART_HOME_DEVICE_DEFAULT_HTTP_PASSWORD,
                    deviceNetwork,
                    3,
                    new Http.Listener() {
                        private void showErrorAndEnableUI(String message) {
                            activity.runOnUiThread(() -> showOkDialog(tr(R.string.error), message, (dialog, which) -> {
                                edtNetworkSsid.setEnabled(edtNetworkSsidEnabled);
                                edtPassphrase.setEnabled(true);
                                btnConnectDevice.setEnabled(true);
                            }));
                        }

                        @Override
                        public void onResponse(Http.Response response) {
                            if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
                                if (response.getDataAsStr().equals("TRYING_TO_CONNECT")) {
                                    new Timer().schedule(new TimerTask() {
                                        @Override
                                        public void run() {
                                            try {
                                                String state = "IN_PROGRESS";
                                                while (state.equals("IN_PROGRESS")) {  // TODO: fix possible infinite cycle
                                                    Http.Response respState = Http.doRequest(
                                                            SMART_HOME_DEVICE_AP_ADDRESS + "/get_setup_wifi_state",
                                                            null,
                                                            SMART_HOME_DEVICE_DEFAULT_HTTP_PASSWORD,
                                                            deviceNetwork,
                                                            15
                                                    );
                                                    if (respState.getHttpCode() == HttpURLConnection.HTTP_OK) {
                                                        state = respState.getDataAsStr();
                                                    }
                                                }
                                                Log.d("DEVICE_RESP", "State: [" + state + "]");

                                                if (state.startsWith("SUCCESS:")) {
                                                    // TODO: CONTINUE MAIN FLOW
                                                } else if (state.startsWith("FAIL:")) {
                                                    // TODO: use error code to make more details in error message
                                                    showErrorAndEnableUI(tr(R.string.device_could_not_connect_to_wifi));
                                                } else {
                                                    disconnectAndShowErrorAndGoToMainScreen(tr(R.string.device_unexpected_response));
                                                }
                                            } catch (IOException exception) {
                                                // TODO: handle
                                            }
                                        }
                                    }, 5000);
                                } else {
                                    disconnectAndShowErrorAndGoToMainScreen(tr(R.string.device_unexpected_response));
                                }
                            } else {
                                disconnectAndShowErrorAndGoToMainScreen(tr(R.string.device_bad_response_code));
                            }
                        }

                        @Override
                        public void onError(IOException exception) {
                            Log.d("DEVICE_RESP", "Exception: " + exception.getMessage());
                            disconnectAndShowErrorAndGoToMainScreen(tr(R.string.device_bad_connect));
                        }
                    }
            );
        });

        edtNetworkSsid.setEnabled(true);
        edtPassphrase.setEnabled(true);
        btnConnectDevice.setEnabled(true);
        edtNetworkSsid.setText("");
        edtPassphrase.setText("");
        edtNetworkSsid.requestFocus();
    }

    public void setNetworkSsid(String networkSsid) {
        edtNetworkSsid.setEnabled(false);
        edtNetworkSsid.setText(networkSsid);
        edtPassphrase.requestFocus();
    }

    @Override
    public int getViewFlipperChildId() {
        return 4;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return false;
    }
}
