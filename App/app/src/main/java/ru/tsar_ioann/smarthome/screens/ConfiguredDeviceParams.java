package ru.tsar_ioann.smarthome.screens;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import ru.tsar_ioann.smarthome.*;

public class ConfiguredDeviceParams extends BaseScreen {
    private final EditText edtCfgDevIpAddress;
    private final EditText edtCfgDevPort;
    private final EditText edtCfgDevPassword;

    public ConfiguredDeviceParams(CommonData commonData) {
        super(commonData);

        Activity activity = commonData.getActivity();
        edtCfgDevIpAddress = activity.findViewById(R.id.edtCfgDevIpAddress);
        edtCfgDevPort = activity.findViewById(R.id.edtCfgDevPort);
        CheckBox cbCfgDevIpIsStatic = activity.findViewById(R.id.cbCfgDevIpIsStatic);
        edtCfgDevPassword = activity.findViewById(R.id.edtCfgDevPassword);
        CheckBox cbShowCfgDevPassword = activity.findViewById(R.id.cbShowCfgDevPassword);
        Button btnAddDevice = activity.findViewById(R.id.btnAddDevice);

        cbShowCfgDevPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            final int selStart = edtCfgDevPassword.getSelectionStart();
            final int selEnd = edtCfgDevPassword.getSelectionEnd();
            edtCfgDevPassword.setTransformationMethod(isChecked ? null : new PasswordTransformationMethod());
            edtCfgDevPassword.setSelection(selStart, selEnd);
        });

        btnAddDevice.setOnClickListener(v -> {
            edtCfgDevIpAddress.setEnabled(false);
            edtCfgDevPort.setEnabled(false);
            cbCfgDevIpIsStatic.setEnabled(false);
            edtCfgDevPassword.setEnabled(false);
            btnAddDevice.setEnabled(false);

            // TODO: try to connect to device and add it to devices list
        });

        edtCfgDevIpAddress.setEnabled(true);
        edtCfgDevPort.setEnabled(true);
        cbCfgDevIpIsStatic.setEnabled(true);
        edtCfgDevPassword.setEnabled(true);
        btnAddDevice.setEnabled(true);

        edtCfgDevIpAddress.setText("");
        edtCfgDevPort.setText("");
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

    @Override
    public boolean shouldMenuBeVisible() {
        return false;
    }
}
