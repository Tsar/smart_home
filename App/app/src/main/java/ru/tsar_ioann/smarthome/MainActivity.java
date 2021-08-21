package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import ru.tsar_ioann.smarthome.screens.AddNewDevice;

public class MainActivity extends Activity implements MenuVisibilityChanger {
    private ScreenLauncher screenLauncher;

    private MenuItem mnAddNewDevice = null;
    private MenuItem mnUpdateStatuses = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        screenLauncher = new ScreenLauncher(
                new CommonData(this, new Wifi(this)),
                findViewById(R.id.viewFlipper),
                this
        );
        screenLauncher.launchScreen(ScreenId.MAIN);
    }

    @Override
    public void setMenuVisibility(boolean visible) {
        if (mnAddNewDevice != null) {
            mnAddNewDevice.setVisible(visible);
        }
        if (mnUpdateStatuses != null) {
            mnUpdateStatuses.setVisible(visible);
        }
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
        if (screenLauncher.getCurrentScreenId() != ScreenId.MAIN) {
            screenLauncher.launchScreen(ScreenId.MAIN);
        } else {
            super.onBackPressed();
        }
    }

    public void onAddNewDevice(MenuItem menuItem) {
        screenLauncher.launchScreen(ScreenId.ADD_NEW_DEVICE);
    }

    public void onUpdateStatuses(MenuItem menuItem) {
        // TODO
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (screenLauncher.getCurrentScreenId() == ScreenId.ADD_NEW_DEVICE) {
            AddNewDevice screen = (AddNewDevice)screenLauncher.getCurrentScreen();
            screen.onRequestPermissionResults(requestCode, grantResults);
        }
    }
}
