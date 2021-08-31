package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.IOException;
import java.net.SocketException;

import ru.tsar_ioann.smarthome.screens.AddNewDevice;
import ru.tsar_ioann.smarthome.screens.Main;

public class MainActivity extends Activity implements MenuVisibilityChanger {
    private ScreenLauncher screenLauncher;

    private MenuItem mnAddNewDevice = null;
    private MenuItem mnSetup = null;
    private MenuItem mnUpdateStatuses = null;
    private boolean mnAddNewDeviceVisible = true;
    private boolean mnSetupVisible = true;
    private boolean mnUpdateStatusesVisible = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        screenLauncher = new ScreenLauncher(
                new CommonData(
                        this,
                        new Wifi(this),
                        new DevicesList(getSharedPreferences("devices", MODE_PRIVATE))
                ),
                findViewById(R.id.viewFlipper),
                this
        );
        screenLauncher.launchScreen(ScreenId.MAIN);

        Udp.asyncListen(UdpSettings.UDP_LISTEN_PORT, new Udp.Listener() {
            private static final String LOG_TAG = "UdpListener";
            private static final String MAC_PREFIX = "MAC=";
            private static final String NAME_PREFIX = "NAME=";

            @Override
            public boolean finish() {
                return false;
            }

            @Override
            public void onReceive(String message, String senderIp, int senderPort) {
                final String[] lines = message.split("\n");
                if (lines.length != 2) {
                    Log.d(LOG_TAG, "Bad lines count in response: expected 2, got " + lines.length);
                    return;
                }
                if (!lines[0].startsWith(MAC_PREFIX)) {
                    Log.d(LOG_TAG, "Bad line with MAC: " + lines[0]);
                    return;
                }
                if (!lines[1].startsWith(NAME_PREFIX)) {
                    Log.d(LOG_TAG, "Bad line with name: " + lines[1]);
                    return;
                }
                String macAddress = lines[0].substring(MAC_PREFIX.length());
                if (!Utils.isValidMacAddress(macAddress)) {
                    Log.d(LOG_TAG, "Invalid MAC address: " + macAddress);
                    return;
                }
                String name = lines[1].substring(NAME_PREFIX.length());
                screenLauncher.getCurrentScreen().handleUdpDeviceInfo(macAddress, name, senderIp, senderPort);
            }

            @Override
            public void onError(IOException exception) {
                Log.d(LOG_TAG, "Error on receiving UDP: " + exception.getMessage());
            }

            @Override
            public void onFatalError(SocketException exception) {
                Log.d(LOG_TAG, "Failed to start listening UDP: " + exception.getMessage());
            }
        });
    }

    @Override
    public void setMenuVisibility(boolean addVisible, boolean setupVisible, boolean updateVisible) {
        mnAddNewDeviceVisible = addVisible;
        mnSetupVisible = setupVisible;
        mnUpdateStatusesVisible = updateVisible;

        if (mnAddNewDevice != null) {
            mnAddNewDevice.setVisible(addVisible);
        }
        if (mnSetup != null) {
            mnSetup.setVisible(setupVisible);
        }
        if (mnUpdateStatuses != null) {
            mnUpdateStatuses.setVisible(updateVisible);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
        mnAddNewDevice = menu.findItem(R.id.mnAddNewDevice);
        mnSetup = menu.findItem(R.id.mnSetup);
        mnUpdateStatuses = menu.findItem(R.id.mnUpdateStatuses);
        setMenuVisibility(mnAddNewDeviceVisible, mnSetupVisible, mnUpdateStatusesVisible);
        return true;
    }

    @Override
    public void onBackPressed() {
        if (screenLauncher.getCurrentScreenId() != ScreenId.MAIN) {
            screenLauncher.launchScreen(ScreenId.MAIN);
        } else {
            super.onBackPressed();
        }
    }

    public void onAddNewDevice(MenuItem menuItem) {
        screenLauncher.launchScreen(ScreenId.ADD_NEW_DEVICE);
    }

    public void onSetup(MenuItem menuItem) {
        if (screenLauncher.getCurrentScreenId() == ScreenId.MAIN) {
            ((Main)screenLauncher.getCurrentScreen()).toggleSetupMode();
        }
    }

    public void onUpdateStatuses(MenuItem menuItem) {
        if (screenLauncher.getCurrentScreenId() == ScreenId.MAIN) {
            ((Main)screenLauncher.getCurrentScreen()).asyncRefresh();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (screenLauncher.getCurrentScreenId() == ScreenId.ADD_NEW_DEVICE) {
            ((AddNewDevice)screenLauncher.getCurrentScreen()).onRequestPermissionResults(requestCode, grantResults);
        }
    }
}
