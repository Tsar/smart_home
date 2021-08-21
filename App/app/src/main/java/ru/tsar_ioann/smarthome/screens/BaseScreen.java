package ru.tsar_ioann.smarthome.screens;

import android.app.AlertDialog;
import android.content.DialogInterface;

import ru.tsar_ioann.smarthome.CommonData;
import ru.tsar_ioann.smarthome.R;
import ru.tsar_ioann.smarthome.ScreenId;

public abstract class BaseScreen {
    protected static final String SMART_HOME_DEVICE_AP_PASSPHRASE = "setup12345";
    protected static final String SMART_HOME_DEVICE_AP_ADDRESS = "http://192.168.4.1";
    protected static final String SMART_HOME_DEVICE_DEFAULT_HTTP_PASSWORD = "12345";

    private final CommonData commonData;

    public BaseScreen(CommonData commonData) {
        this.commonData = commonData;
    }

    public abstract int getViewFlipperChildId();
    public abstract boolean shouldMenuBeVisible();

    protected final CommonData getCommonData() {
        return commonData;
    }

    protected final String tr(int resId) {
        return commonData.getResources().getString(resId);
    }

    protected final void showOkDialog(String title, String message) {
        showOkDialog(title, message, (dialogInterface, i) -> {});
    }

    protected final void showOkDialog(String title, String message, DialogInterface.OnClickListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(commonData.getActivity());
        builder.setTitle(title);
        builder.setMessage(message);
        builder.setPositiveButton("OK", listener);
        builder.show();
    }

    protected final void disconnectAndShowErrorAndGoToMainScreen(String message) {
        commonData.getWifi().disconnect();
        commonData.getActivity().runOnUiThread(() -> showOkDialog(tr(R.string.error), message, (dialog, which) -> {
            commonData.getScreenLauncher().launchScreen(ScreenId.MAIN);
        }));
    }
}
