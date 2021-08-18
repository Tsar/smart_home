package ru.tsar_ioann.smarthome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.net.Network;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static class Screens {
        public static final int MANAGEMENT = 0;
        public static final int ADD_NEW_DEVICE = 1;
        public static final int FRESH_DEVICES = 2;
    }

    private static final int PERMISSION_REQUEST_CODE = 1;

    private static final String SMART_HOME_DEVICE_AP_SSID_PREFIX = "SmartHomeDevice_";
    private static final int SMART_HOME_DEVICE_AP_SSID_LENGTH = SMART_HOME_DEVICE_AP_SSID_PREFIX.length() + 6;
    private static final String SMART_HOME_DEVICE_AP_PASSPHRASE = "setup12345";

    private Wifi wifi;

    private ViewFlipper viewFlipper;
    private MenuItem mnAddNewDevice;
    private MenuItem mnUpdateStatuses;
    private TextView txtSearchTitle;
    private ListView lstDevices;

    private ArrayAdapter<String> lstDevicesAdapter;
    private Set<String> devices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifi = new Wifi(this);

        viewFlipper = findViewById(R.id.viewFlipper);
        txtSearchTitle = findViewById(R.id.txtSearchTitle);
        lstDevices = findViewById(R.id.lstDevices);

        Button btnAddFresh = findViewById(R.id.btnAddFresh);
        Button btnAddConfigured = findViewById(R.id.btnAddConfigured);

        Button[] allButtons = new Button[]{
                btnAddFresh,
                btnAddConfigured
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
            String ssid = (String)adapterView.getItemAtPosition(position);
            wifi.connectToWifi(ssid, SMART_HOME_DEVICE_AP_PASSPHRASE, new Wifi.ConnectListener() {
                @Override
                public void onConnected(Network network) {
                    try {
                        Http.Response response = Http.doRequest("http://192.168.4.1/ping", null, "12345", false, network);
                        // TODO: next steps
                    } catch (Http.Exception e) {
                        Log.d("HTTP EXCEPTION", e.getMessage());
                    }
                }

                @Override
                public void onConnectFailed() {
                    // TODO: handle
                }

                @Override
                public void onConnectLost() {
                    // TODO: handle
                }
            });
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
            viewFlipper.setDisplayedChild(Screens.MANAGEMENT);
            setMenuVisibility(true);
        } else {
            super.onBackPressed();
        }
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
        wifi.scan(new Wifi.ScanListener() {
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
                runOnUiThread(() -> txtSearchTitle.setText(tr(R.string.searching_finished) + "\n" + tr(
                        lstDevicesAdapter.getCount() > 0 ? R.string.choose_device : R.string.nothing_found
                )));
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
}
