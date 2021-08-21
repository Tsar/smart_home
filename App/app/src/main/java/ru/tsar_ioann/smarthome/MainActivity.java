package ru.tsar_ioann.smarthome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Network;
import android.os.Bundle;
import android.text.Html;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashSet;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends Activity {
    private static class Screens {
        public static final int MANAGEMENT = 0;
        public static final int ADD_NEW_DEVICE = 1;
        public static final int FRESH_DEVICES = 2;
        public static final int CONNECTING_FRESH_DEVICE = 3;
        public static final int HOME_NETWORK_SETTINGS = 4;
    }

    private static final int PERMISSION_REQUEST_CODE = 1;

    private static final String SMART_HOME_DEVICE_AP_SSID_PREFIX = "SmartHomeDevice_";
    private static final int SMART_HOME_DEVICE_AP_SSID_LENGTH = SMART_HOME_DEVICE_AP_SSID_PREFIX.length() + 6;
    private static final String SMART_HOME_DEVICE_AP_PASSPHRASE = "setup12345";
    private static final String SMART_HOME_DEVICE_AP_ADDRESS = "http://192.168.4.1";
    private static final String SMART_HOME_DEVICE_DEFAULT_HTTP_PASSWORD = "12345";

    private Wifi wifi;
    private Network temporaryNetwork = null;

    private ViewFlipper viewFlipper;
    private MenuItem mnAddNewDevice;
    private MenuItem mnUpdateStatuses;
    private TextView txtSearchTitle;
    private TextView txtConnecting;
    private Button btnSetNetwork;
    private ListView lstNetworks;
    private EditText edtNetworkSsid;
    private EditText edtPassphrase;
    private Button btnConnectDevice;

    private ArrayAdapter<String> lstDevicesAdapter;
    private Set<String> devices;

    private ArrayAdapter<String> lstNetworksAdapter;
    private Set<String> networks;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifi = new Wifi(this);

        viewFlipper = findViewById(R.id.viewFlipper);
        Button btnAddFresh = findViewById(R.id.btnAddFresh);
        Button btnAddConfigured = findViewById(R.id.btnAddConfigured);
        txtSearchTitle = findViewById(R.id.txtSearchTitle);
        ListView lstDevices = findViewById(R.id.lstDevices);
        txtConnecting = findViewById(R.id.txtConnecting);
        btnSetNetwork = findViewById(R.id.btnSetNetwork);
        lstNetworks = findViewById(R.id.lstNetworks);
        edtNetworkSsid = findViewById(R.id.edtNetworkSsid);
        edtPassphrase = findViewById(R.id.edtPassphrase);
        CheckBox cbShowPassphrase = findViewById(R.id.cbShowPassphrase);
        btnConnectDevice = findViewById(R.id.btnConnectDevice);

        btnAddFresh.setText(Html.fromHtml(tr(R.string.add_fresh_device)));
        btnAddConfigured.setText(Html.fromHtml(tr(R.string.add_configured_device)));

        cbShowPassphrase.setOnCheckedChangeListener((buttonView, isChecked) -> {
            final int selStart = edtPassphrase.getSelectionStart();
            final int selEnd = edtPassphrase.getSelectionEnd();
            edtPassphrase.setTransformationMethod(isChecked ? null : new PasswordTransformationMethod());
            edtPassphrase.setSelection(selStart, selEnd);
        });

        Button[] allButtons = new Button[]{
                btnAddFresh,
                btnAddConfigured,
                btnSetNetwork,
                btnConnectDevice
        };

        ColorStateList cslButtonBg = getResources().getColorStateList(R.color.button_bg);
        ColorStateList cslButtonText = getResources().getColorStateList(R.color.button_text);
        for (Button btn : allButtons) {
            btn.setBackgroundTintList(cslButtonBg);
            btn.setTextColor(cslButtonText);
        }

        lstDevicesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        lstDevices.setAdapter(lstDevicesAdapter);
        lstDevices.setOnItemClickListener((adapterView, view, position, id) -> {
            txtConnecting.setText(R.string.connecting_to_device);
            btnSetNetwork.setVisibility(View.GONE);
            lstNetworks.setVisibility(View.GONE);
            viewFlipper.setDisplayedChild(Screens.CONNECTING_FRESH_DEVICE);

            String deviceSsid = (String)adapterView.getItemAtPosition(position);
            wifi.connectToWifi(deviceSsid, SMART_HOME_DEVICE_AP_PASSPHRASE, new Wifi.ConnectListener() {
                @Override
                public void onConnected(Network network) {
                    temporaryNetwork = network;
                    try {
                        Http.Response response = Http.doRequest(
                                SMART_HOME_DEVICE_AP_ADDRESS + "/get_info",
                                null,
                                SMART_HOME_DEVICE_DEFAULT_HTTP_PASSWORD,
                                network,
                                3
                        );
                        if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
                            // TODO: parse answer properly
                            if (response.getDataAsStr().startsWith("MAC=")) {
                                runOnUiThread(() -> {
                                    txtConnecting.setText(R.string.connected_to_device);
                                    networks = new HashSet<>();
                                    lstNetworksAdapter.clear();

                                    btnSetNetwork.setVisibility(View.VISIBLE);
                                    lstNetworks.setVisibility(View.VISIBLE);

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
                                showErrorAndGoToMainScreen(tr(R.string.device_unexpected_response));
                            }
                        } else {
                            showErrorAndGoToMainScreen(tr(R.string.device_bad_response_code));
                        }
                    } catch (IOException e) {
                        Log.d("DEVICE_RESP", "Exception: " + e.getMessage());
                        showErrorAndGoToMainScreen(tr(R.string.device_bad_connect));
                    }
                }

                @Override
                public void onConnectFailed() {
                    temporaryNetwork = null;
                    showErrorAndGoToMainScreen(tr(R.string.device_connect_failed));
                }

                @Override
                public void onConnectLost() {
                    temporaryNetwork = null;
                    showErrorAndGoToMainScreen(tr(R.string.device_connection_lost));
                }
            });
        });

        lstNetworksAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        lstNetworks.setAdapter(lstNetworksAdapter);
        lstNetworks.setOnItemClickListener((adapterView, view, position, id) -> {
            String networkSsid = (String)adapterView.getItemAtPosition(position);
            edtNetworkSsid.setEnabled(false);
            edtPassphrase.setEnabled(true);
            btnConnectDevice.setEnabled(true);
            edtNetworkSsid.setText(networkSsid);
            edtPassphrase.setText("");
            viewFlipper.setDisplayedChild(Screens.HOME_NETWORK_SETTINGS);
            edtPassphrase.requestFocus();
        });
    }

    private String tr(int resId) {
        return getResources().getString(resId);
    }

    private void setMenuVisibility(boolean visible) {
        mnAddNewDevice.setVisible(visible);
        mnUpdateStatuses.setVisible(visible);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        mnAddNewDevice = menu.findItem(R.id.mnAddNewDevice);
        mnUpdateStatuses = menu.findItem(R.id.mnUpdateStatuses);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (viewFlipper.getDisplayedChild() != Screens.MANAGEMENT) {
            wifi.disconnect();
            viewFlipper.setDisplayedChild(Screens.MANAGEMENT);
            setMenuVisibility(true);
        } else {
            super.onBackPressed();
        }
    }

    private void showErrorAndGoToMainScreen(String message) {
        wifi.disconnect();
        runOnUiThread(() -> showOkDialog(tr(R.string.error), message, (dialog, which) -> {
            viewFlipper.setDisplayedChild(Screens.MANAGEMENT);
            setMenuVisibility(true);
        }));
    }

    public void onAddNewDevice(MenuItem menuItem) {
        viewFlipper.setDisplayedChild(Screens.ADD_NEW_DEVICE);
        setMenuVisibility(false);
    }

    public void onUpdateStatuses(MenuItem menuItem) {
        // TODO
    }

    private void showOkDialog(String title, String message) {
        showOkDialog(title, message, (dialogInterface, i) -> {});
    }

    private void showOkDialog(String title, String message, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("OK", listener);
        builder.show();
    }

    private void switchToFreshDevicesAndStartScan() {
        txtSearchTitle.setText(tr(R.string.searching_smart_home));
        viewFlipper.setDisplayedChild(Screens.FRESH_DEVICES);
        devices = new HashSet<>();
        lstDevicesAdapter.clear();
        wifi.scan(30, new Wifi.ScanListener() {
            @Override
            public void onWifiFound(String ssid) {
                runOnUiThread(() -> {
                    if (ssid.length() == SMART_HOME_DEVICE_AP_SSID_LENGTH && ssid.startsWith(SMART_HOME_DEVICE_AP_SSID_PREFIX)) {
                        if (!devices.contains(ssid)) {
                            devices.add(ssid);
                            lstDevicesAdapter.add(ssid);
                        }
                    }
                });
            }

            @Override
            public void onScanFinished() {
                runOnUiThread(() -> txtSearchTitle.setText(tr(lstDevicesAdapter.getCount() > 0
                        ? R.string.search_finished_choose_device
                        : R.string.search_finished_nothing_found)));
            }
        });
    }

    public void onAddFreshDevice(View view) {
        if (!wifi.isWifiEnabled()) {
            showOkDialog(tr(R.string.wifi_is_off), tr(R.string.enable_wifi_prompt));
            return;
        }

        final String perm = Manifest.permission.ACCESS_FINE_LOCATION;
        if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
            if (shouldShowRequestPermissionRationale(perm)) {
                showOkDialog(
                        tr(R.string.warning),
                        tr(R.string.permission_request_explanation),
                        (dialogInterface, i) -> requestPermissions(new String[]{perm}, PERMISSION_REQUEST_CODE)
                );
            } else {
                requestPermissions(new String[]{perm}, PERMISSION_REQUEST_CODE);
            }
            return;
        }

        switchToFreshDevicesAndStartScan();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // If request is cancelled, the result arrays are empty
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                switchToFreshDevicesAndStartScan();
            } else {
                Toast.makeText(
                        this,
                        tr(R.string.you_declined_permission_request),
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    public void onAddConfiguredDevice(View view) {
        // TODO
    }

    public void onInputNetworkManually(View view) {
        edtNetworkSsid.setEnabled(true);
        edtPassphrase.setEnabled(true);
        btnConnectDevice.setEnabled(true);
        edtNetworkSsid.setText("");
        edtPassphrase.setText("");
        viewFlipper.setDisplayedChild(Screens.HOME_NETWORK_SETTINGS);
        edtNetworkSsid.requestFocus();
    }

    public void onConnectDeviceToNetwork(View view) {
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
                temporaryNetwork,
                3,
                new Http.Listener() {
                    private void showErrorAndEnableUI(String message) {
                        runOnUiThread(() -> showOkDialog(tr(R.string.error), message, (dialog, which) -> {
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
                                                        temporaryNetwork,
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
                                                showErrorAndGoToMainScreen(tr(R.string.device_unexpected_response));
                                            }
                                        } catch (IOException exception) {
                                            // TODO: handle
                                        }
                                    }
                                }, 5000);
                            } else {
                                showErrorAndGoToMainScreen(tr(R.string.device_unexpected_response));
                            }
                        } else {
                            showErrorAndGoToMainScreen(tr(R.string.device_bad_response_code));
                        }
                    }

                    @Override
                    public void onError(IOException exception) {
                        Log.d("DEVICE_RESP", "Exception: " + exception.getMessage());
                        showErrorAndGoToMainScreen(tr(R.string.device_bad_connect));
                    }
                }
        );
    }
}
