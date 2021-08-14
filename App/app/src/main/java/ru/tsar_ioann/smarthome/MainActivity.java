package ru.tsar_ioann.smarthome;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.ViewFlipper;

import java.util.HashSet;
import java.util.Set;

public class MainActivity extends Activity {
    private static class Screens {
        public static final int MANAGEMENT = 0;
        public static final int ADD_NEW_DEVICE = 1;
        public static final int FRESH_DEVICES = 2;
    }

    private static final String SMART_HOME_DEVICE_AP_SSID_PREFIX = "SmartHomeDevice_";
    private static final int SMART_HOME_DEVICE_AP_SSID_LENGTH = SMART_HOME_DEVICE_AP_SSID_PREFIX.length() + 6;

    private ViewFlipper viewFlipper;
    private MenuItem mnAddNewDevice;
    private MenuItem mnUpdateStatuses;
    private ListView lstDevices;

    private ArrayAdapter<String> lstDevicesAdapter;
    private Set<String> devices;

    private WifiScanner wifiScanner = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFlipper = findViewById(R.id.viewFlipper);
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

    private boolean requestPermissionsIfNeeded(String permission) {
        if (checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {permission}, 1);
            return true;
        }
        return false;
    }

    public void onAddFreshDevice(View view) {
        wifiScanner = new WifiScanner(this);
        if (!wifiScanner.isWifiEnabled()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle(getResources().getString(R.string.wifi_is_off));
            builder.setMessage(getResources().getString(R.string.enable_wifi_prompt));
            builder.setPositiveButton("OK", (dialog, which) -> {});
            builder.show();
            return;
        }
        if (requestPermissionsIfNeeded(Manifest.permission.ACCESS_FINE_LOCATION)) {
            return;
        }

        // TODO: run from here when permission is granted

        viewFlipper.setDisplayedChild(Screens.FRESH_DEVICES);
        devices = new HashSet<>();
        wifiScanner.startScan(ssid -> {
            if (ssid.length() == SMART_HOME_DEVICE_AP_SSID_LENGTH && ssid.startsWith(SMART_HOME_DEVICE_AP_SSID_PREFIX)) {
                if (!devices.contains(ssid)) {
                    devices.add(ssid);
                    lstDevicesAdapter.add(ssid);
                }
            }
        });
    }

    public void onAddConfiguredDevice(View view) {
        // TODO
    }
}
