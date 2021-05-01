package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ru.tsar_ioann.smarthome.devices.Device;
import ru.tsar_ioann.smarthome.devices.DeviceTypes;
import ru.tsar_ioann.smarthome.request_processors.GetDeviceStates;
import ru.tsar_ioann.smarthome.request_processors.GetDevices;
import ru.tsar_ioann.smarthome.request_processors.Ping;

public class MainActivity extends Activity {
    private static final int DEFAULT_SERVER_PORT = 9732;

    private static final int VIEWFLIPPER_SCREEN_LOGIN = 0;
    private static final int VIEWFLIPPER_SCREEN_DEVICES = 1;

    private static final String SETTING_KEY_SERVER_ADDRESS = "serverAddress";
    private static final String SETTING_KEY_SERVER_PORT = "serverPort";
    private static final String SETTING_KEY_PASSWORD = "password";

    private ViewFlipper viewFlipper;
    private EditText edtServerAddress;
    private EditText edtPassword;
    private Button btnConnect;
    private TextView txtStatus;
    private LinearLayout devicesLayout;

    private String serverAddress;
    private int serverPort;
    private String password;

    private SharedPreferences settings;
    private DeviceNamesCache deviceNamesCache;
    private Client client;

    private List<Device> devices;
    private boolean deviceStatusesWereOnceUpdated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewFlipper = findViewById(R.id.viewFlipper);
        edtServerAddress = findViewById(R.id.edtServerAddress);
        edtPassword = findViewById(R.id.edtPassword);
        btnConnect = findViewById(R.id.btnConnect);
        txtStatus = findViewById(R.id.txtStatus);
        devicesLayout = findViewById(R.id.devicesLayout);

        ColorStateList cslButtonBg = getResources().getColorStateList(R.color.button_bg);
        ColorStateList cslButtonText = getResources().getColorStateList(R.color.button_text);
        btnConnect.setBackgroundTintList(cslButtonBg);
        btnConnect.setTextColor(cslButtonText);

        settings = getSharedPreferences("settings", MODE_PRIVATE);
        deviceNamesCache = new DeviceNamesCache(getSharedPreferences("deviceNames", MODE_PRIVATE));
        serverAddress = settings.getString(SETTING_KEY_SERVER_ADDRESS, "");
        serverPort = settings.getInt(SETTING_KEY_SERVER_PORT, DEFAULT_SERVER_PORT);
        password = settings.getString(SETTING_KEY_PASSWORD, "");
        if (!serverAddress.isEmpty()) {
            edtServerAddress.setText(serverAddress + (serverPort != DEFAULT_SERVER_PORT ? ":" + serverPort : ""));
            tryToConnect(false);
        }

        btnConnect.setOnClickListener(v -> tryToConnect(true));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.empty, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (deviceStatusesWereOnceUpdated) {
            getMenuInflater().inflate(R.menu.menu, menu);
        } else {
            getMenuInflater().inflate(R.menu.empty, menu);
        }
        return true;
    }

    public void handleWrongPassword(boolean wasChanged) {
        runOnUiThread(() -> {
            btnConnect.setEnabled(true);
            viewFlipper.setDisplayedChild(VIEWFLIPPER_SCREEN_LOGIN);
            Toast.makeText(
                    MainActivity.this,
                    wasChanged ? "Password has changed!" : "Wrong password!",
                    Toast.LENGTH_SHORT).show();
        });
    }

    private class AuthPingListener implements Ping.Listener {
        private final boolean startedByButton;

        public AuthPingListener(boolean startedByButton) {
            this.startedByButton = startedByButton;
        }

        @Override
        public void onOKResult() {
            runOnUiThread(() -> {
                if (startedByButton) {
                    // saving valid settings
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString(SETTING_KEY_SERVER_ADDRESS, serverAddress);
                    editor.putInt(SETTING_KEY_SERVER_PORT, serverPort);
                    editor.putString(SETTING_KEY_PASSWORD, password);
                    editor.apply();
                }
                viewFlipper.setDisplayedChild(VIEWFLIPPER_SCREEN_DEVICES);
                client.getDevices(getDevicesListener, deviceNamesCache);
                btnConnect.setEnabled(true);
            });
        }

        @Override
        public void onWrongPassword() {
            handleWrongPassword(!startedByButton);
        }

        @Override
        public void onError(String errorText) {
            runOnUiThread(() -> {
                btnConnect.setEnabled(true);
                Toast.makeText(MainActivity.this, errorText, Toast.LENGTH_SHORT).show();
            });
        }
    };

    private void tryToConnect(boolean startedByButton) {
        if (startedByButton) {
            serverAddress = edtServerAddress.getText().toString();
            serverPort = DEFAULT_SERVER_PORT;
            password = edtPassword.getText().toString();
        }

        if (serverAddress.isEmpty()) {
            if (startedByButton) {
                Toast.makeText(this, "Server address should not be empty!", Toast.LENGTH_LONG).show();
            }
            return;
        }

        if (serverAddress.contains(":")) {
            String[] serverAddressAndPort = serverAddress.split(":");
            if (serverAddressAndPort.length != 2) {
                if (startedByButton) {
                    Toast.makeText(this, "Bad server address!", Toast.LENGTH_LONG).show();
                }
                return;
            }
            serverAddress = serverAddressAndPort[0];
            try {
                serverPort = Integer.parseInt(serverAddressAndPort[1]);
            } catch (NumberFormatException e) {
                if (startedByButton) {
                    Toast.makeText(this, "Bad server address!", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }

        btnConnect.setEnabled(false);
        client = new Client(serverAddress, serverPort, password, txtStatus);
        client.ping(new AuthPingListener(startedByButton));
    }

    private GetDevices.Listener getDevicesListener = new GetDevices.Listener() {
        @Override
        public void onOKResult(List<DeviceParams> devices, Map<Integer, String> deviceNames) {
            runOnUiThread(() -> {
                MainActivity.this.devices = new ArrayList<>();
                devicesLayout.removeAllViews();
                for (DeviceParams deviceParams : devices) {
                    int nameId = deviceParams.getNameId();
                    String name = deviceNames.containsKey(nameId) ? deviceNames.get(nameId) : "n" + nameId;
                    Device device = DeviceTypes.getDeviceByType(deviceParams.getDeviceType());
                    MainActivity.this.devices.add(device);
                    if (device != null) {
                        devicesLayout.addView(
                                device.createView(deviceParams.getUuid(), name, MainActivity.this, client)
                        );
                    } else {
                        // TODO: handle bad/unsupported device type
                    }
                }

                client.getDeviceStates(getDeviceStatesListener, false);
            });
        }

        @Override
        public void onWrongPassword() {
            handleWrongPassword(true);
        }

        @Override
        public void onError(String errorText) {
            // TODO: handle
        }
    };

    public GetDeviceStates.Listener getDeviceStatesListener = new GetDeviceStates.Listener() {
        @Override
        public void onOKResult(List<Integer> deviceStates, boolean wereUpdated) {
            if (MainActivity.this.devices.size() != deviceStates.size()) {
                // TODO: retry getDevices and getDeviceStates?
                Log.d("GetDeviceStatesListener", "devices.size != deviceStates.size");
                return;
            }

            runOnUiThread(() -> {
                for (int i = 0; i < deviceStates.size(); ++i) {
                    Device device = MainActivity.this.devices.get(i);
                    if (device != null) {
                        device.setCurrentState(deviceStates.get(i));
                    }
                }

                txtStatus.setText("");
            });

            if (!deviceStatusesWereOnceUpdated) {
                if (wereUpdated) {
                    deviceStatusesWereOnceUpdated = true;
                    invalidateOptionsMenu();
                } else {
                    client.getDeviceStates(getDeviceStatesListener, true);
                }
            }
        }

        @Override
        public void onWrongPassword() {
            handleWrongPassword(true);
        }

        @Override
        public void onError(String errorText) {
            // TODO: handle
        }
    };

    public void onUpdateDevices(MenuItem menuItem) {
        client.getDeviceStates(getDeviceStatesListener, true);
    }
}
