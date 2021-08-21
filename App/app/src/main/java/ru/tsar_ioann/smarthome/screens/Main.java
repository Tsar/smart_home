package ru.tsar_ioann.smarthome.screens;

import android.app.Activity;
import android.widget.ListView;

import ru.tsar_ioann.smarthome.*;

public class Main extends BaseScreen {
    public Main(CommonData commonData) {
        super(commonData);
        commonData.getWifi().disconnect();  // this is required for back button to work correctly

        Activity activity = commonData.getActivity();
        ListView lstDevices = activity.findViewById(R.id.lstDevices);

        DevicesAdapter devicesAdapter = new DevicesAdapter(activity, commonData.getDevices().getList());
        lstDevices.setAdapter(devicesAdapter);

        asyncRefresh();
    }

    public void asyncRefresh() {
        DevicesList devices = getCommonData().getDevices();
        // TODO
    }

    @Override
    public int getViewFlipperChildId() {
        return 0;
    }

    @Override
    public boolean shouldMenuBeVisible() {
        return true;
    }
}
