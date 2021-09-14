package ru.tsar_ioann.smarthome.screens;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.text.Html;
import android.widget.Button;
import android.widget.Toast;

import ru.tsar_ioann.smarthome.*;

public class AddNewDevice extends BaseScreen {
    private static final String PERM = Manifest.permission.ACCESS_FINE_LOCATION;
    private static final int PERMISSION_REQUEST_CODE = 1;

    private final ScreenLauncher screenLauncher;

    public AddNewDevice(CommonData commonData) {
        super(commonData);

        screenLauncher = commonData.getScreenLauncher();

        Activity activity = commonData.getActivity();
        Button btnAddFresh = activity.findViewById(R.id.btnAddFresh);
        Button btnAddConfigured = activity.findViewById(R.id.btnAddConfigured);

        btnAddFresh.setText(Html.fromHtml(tr(R.string.add_fresh_device)));
        btnAddConfigured.setText(Html.fromHtml(tr(R.string.add_configured_device)));

        btnAddFresh.setOnClickListener(v -> {
            if (!commonData.getWifi().isWifiEnabled()) {
                showOkDialog(tr(R.string.wifi_is_off), tr(R.string.enable_wifi_prompt));
                return;
            }

            if (activity.checkSelfPermission(PERM) != PackageManager.PERMISSION_GRANTED) {
                if (activity.shouldShowRequestPermissionRationale(PERM)) {
                    showOkDialog(
                            tr(R.string.warning),
                            tr(R.string.permission_request_explanation),
                            (dialogInterface, i) -> activity.requestPermissions(new String[]{PERM}, PERMISSION_REQUEST_CODE)
                    );
                } else {
                    activity.requestPermissions(new String[]{PERM}, PERMISSION_REQUEST_CODE);
                }
                return;
            }

            screenLauncher.launchScreen(ScreenId.FRESH_DEVICES);
        });

        btnAddConfigured.setOnClickListener(v -> screenLauncher.launchScreen(ScreenId.CONFIGURED_DEVICES));
    }

    public void onRequestPermissionResults(int requestCode, int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            // If request is cancelled, the result arrays are empty
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                screenLauncher.launchScreen(ScreenId.FRESH_DEVICES);
            } else {
                Toast.makeText(
                        getCommonData().getActivity(),
                        tr(R.string.you_declined_permission_request),
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    @Override
    public int getViewFlipperChildId() {
        return 1;
    }
}
