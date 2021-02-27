package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class MainActivity extends Activity {
    private EditText edtServerAddress;
    private EditText edtPassword;
    private Button btnConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        edtServerAddress = findViewById(R.id.edtServerAddress);
        edtPassword = findViewById(R.id.edtPassword);
        btnConnect = findViewById(R.id.btnConnect);

        ColorStateList cslButtonBg = getResources().getColorStateList(R.color.button_bg);
        ColorStateList cslButtonText = getResources().getColorStateList(R.color.button_text);
        btnConnect.setBackgroundTintList(cslButtonBg);
        btnConnect.setTextColor(cslButtonText);

        btnConnect.setOnClickListener(v -> tryToConnect());
    }

    private void tryToConnect() {
        String serverAddress = edtServerAddress.getText().toString();
        String password = edtPassword.getText().toString();

        if (serverAddress.isEmpty()) {
            Toast.makeText(this, "Server address should not be empty!", Toast.LENGTH_LONG).show();
            return;
        }

        // TODO
    }
}
