package ru.tsar_ioann.smarthome.screens;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.text.method.PasswordTransformationMethod;
import android.widget.CheckBox;
import android.widget.EditText;

import ru.tsar_ioann.smarthome.*;

public abstract class BaseScreen {
    private final CommonData commonData;

    public BaseScreen(CommonData commonData) {
        this.commonData = commonData;
    }

    public abstract int getViewFlipperChildId();
    public void handleUdpDeviceInfo(String macAddress, String name, String ipAddress, int port) {}
    public void onScreenLeave() {}

    protected final CommonData getCommonData() {
        return commonData;
    }

    protected final String tr(int resId) {
        return commonData.getResources().getString(resId);
    }

    protected final String tr(int resId, Object... formatArgs) {
        return commonData.getResources().getString(resId, formatArgs);
    }

    protected final void showOkDialog(String title, String message) {
        showOkDialog(title, message, (dialogInterface, i) -> {});
    }

    protected final void showOkDialog(String title, String message, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(commonData.getActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(tr(R.string.ok), listener);
        builder.show();
    }

    protected final void showYesNoDialog(String title, String message, DialogInterface.OnClickListener yesListener, DialogInterface.OnClickListener noListener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(commonData.getActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton(tr(R.string.yes), yesListener);
        builder.setNegativeButton(tr(R.string.no), noListener);
        builder.show();
    }

    protected final void showErrorAndGoToMainScreen(String message) {
        commonData.getActivity().runOnUiThread(() -> showOkDialog(tr(R.string.error), message, (dialog, which) -> {
            commonData.getScreenLauncher().launchScreen(ScreenId.MAIN);
        }));
    }

    protected final void disconnectAndShowErrorAndGoToMainScreen(String message) {
        commonData.getWifi().disconnect();
        showErrorAndGoToMainScreen(message);
    }

    protected final void setupShowPasswordCheckBox(CheckBox cbShowPassword, EditText edtPassword) {
        cbShowPassword.setOnCheckedChangeListener((buttonView, isChecked) -> {
            final int selStart = edtPassword.getSelectionStart();
            final int selEnd = edtPassword.getSelectionEnd();
            edtPassword.setTransformationMethod(isChecked ? null : new PasswordTransformationMethod());
            edtPassword.setSelection(selStart, selEnd);
        });
    }
}
