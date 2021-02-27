package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int DEFAULT_SERVER_PORT = 9732;

    private Activity activity;
    private EditText edtServerAddress;
    private EditText edtPassword;
    private Button btnConnect;

    private Client client;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        activity = this;
        edtServerAddress = findViewById(R.id.edtServerAddress);
        edtPassword = findViewById(R.id.edtPassword);
        btnConnect = findViewById(R.id.btnConnect);

        ColorStateList cslButtonBg = getResources().getColorStateList(R.color.button_bg);
        ColorStateList cslButtonText = getResources().getColorStateList(R.color.button_text);
        btnConnect.setBackgroundTintList(cslButtonBg);
        btnConnect.setTextColor(cslButtonText);

        btnConnect.setOnClickListener(v -> tryToConnect());
    }

    private Client.PingListener pingListener = new Client.PingListener() {
        @Override
        public void onOKResult(int status) {
            runOnUiThread(() -> {
                Toast.makeText(activity, "PING OK: " + status, Toast.LENGTH_SHORT).show();
            });
        }

        @Override
        public void onError(String errorText) {
            runOnUiThread(() -> {
                Toast.makeText(activity, "PING FAILED: " + errorText, Toast.LENGTH_SHORT).show();
            });
        }
    };

    private void tryToConnect() {
        String serverAddress = edtServerAddress.getText().toString();
        int serverPort = DEFAULT_SERVER_PORT;
        String password = edtPassword.getText().toString();

        if (serverAddress.isEmpty()) {
            Toast.makeText(this, "Server address should not be empty!", Toast.LENGTH_LONG).show();
            return;
        }

        if (serverAddress.contains(":")) {
            String[] serverAddressAndPort = serverAddress.split(":");
            if (serverAddressAndPort.length != 2) {
                Toast.makeText(this, "Bad server address!", Toast.LENGTH_LONG).show();
                return;
            }
            serverAddress = serverAddressAndPort[0];
            try {
                serverPort = Integer.parseInt(serverAddressAndPort[1]);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Bad server address!", Toast.LENGTH_LONG).show();
                return;
            }
        }

        client = new Client(serverAddress, serverPort, password);
        client.ping(pingListener);
    }
}
