package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.content.DialogInterface;
import android.text.method.PasswordTransformationMethod;
import android.widget.CheckBox;
import android.widget.EditText;

import ru.tsar_ioann.smarthome.*;

public abstract class BaseScreen {
    private final CommonData commonData;
    private final Activity activity;

    public BaseScreen(CommonData commonData) {
        this.commonData = commonData;
        this.activity = commonData.getActivity();
    }

    public abstract int getViewFlipperChildId();
    public void handleUdpDeviceInfo(String macAddress, String name, String ipAddress, int port) {}
    public void onScreenLeave() {}

    protected final CommonData getCommonData() {
        return commonData;
    }

    protected final String tr(int resId) {
        return Utils.tr(activity.getResources(), resId);
    }

    protected final String tr(int resId, Object... formatArgs) {
        return Utils.tr(activity.getResources(), resId, formatArgs);
    }

    protected final void showOkDialog(String title, String message) {
        Utils.showOkDialog(activity, title, message);
    }

    protected final void showOkDialog(String title, String message, DialogInterface.OnClickListener listener) {
        Utils.showOkDialog(activity, title, message, listener);
    }

    protected final void showYesNoDialog(String title, String message, DialogInterface.OnClickListener yesListener, DialogInterface.OnClickListener noListener) {
        Utils.showYesNoDialog(activity, title, message, yesListener, noListener);
    }

    protected final void showErrorAndGoToMainScreen(String message) {
        activity.runOnUiThread(() -> showOkDialog(tr(R.string.error), message, (dialog, which) -> {
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
