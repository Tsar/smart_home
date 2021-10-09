package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.IOException;
import java.net.HttpURLConnection;

import ru.tsar_ioann.smarthome.screens.DeviceSettings;

public class DevicesAdapter extends ArrayAdapter<DeviceInfo> {
    private final Activity activity;
    private final DevicesList devicesList;
    private final ScreenLauncher screenLauncher;

    private boolean settingsButtonsVisible = false;

    public DevicesAdapter(Activity activity, DevicesList devicesList, ScreenLauncher screenLauncher) {
        super(activity, 0, devicesList.getList());
        this.activity = activity;
        this.devicesList = devicesList;
        this.screenLauncher = screenLauncher;
    }

    public void setSettingsButtonsVisible(boolean visible) {
        settingsButtonsVisible = visible;
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        DeviceInfo device = getItem(position);

        // Check if an existing view is being reused, otherwise inflate the view
        LayoutInflater inflater = LayoutInflater.from(getContext());
        if (convertView == null) {
            convertView = inflater.inflate(R.layout.item_device, parent, false);
        }

        // Lookup view for data population
        final TextView txtDeviceName = convertView.findViewById(R.id.txtDeviceName);
        final TextView txtDeviceMac = convertView.findViewById(R.id.txtDeviceMac);
        final TextView txtDeviceIp = convertView.findViewById(R.id.txtDeviceIp);

        final LinearLayout layoutDimmers = convertView.findViewById(R.id.layoutDimmers);
        final int dimmersCount = device.getDimmersCount();
        while (layoutDimmers.getChildCount() > dimmersCount) {
            layoutDimmers.removeViewAt(layoutDimmers.getChildCount() - 1);
        }
        while (layoutDimmers.getChildCount() < dimmersCount) {
            inflater.inflate(R.layout.dimmer, layoutDimmers, true);
        }
        assert layoutDimmers.getChildCount() == dimmersCount;

        final SeekBar[] dimmers = new SeekBar[dimmersCount];
        for (int i = 0; i < dimmersCount; ++i) {
            dimmers[i] = (SeekBar) layoutDimmers.getChildAt(i);
        }

        final LinearLayout layoutSwitchers = convertView.findViewById(R.id.layoutSwitchers);
        final int switchersCount = device.getSwitchersCount();
        final int targetLayoutSwitchersElementsCount = switchersCount * 2 + 1;
        while (layoutSwitchers.getChildCount() > targetLayoutSwitchersElementsCount) {
            for (int i = 0; i < 2; ++i) {
                layoutSwitchers.removeViewAt(layoutSwitchers.getChildCount() - 1);
            }
        }
        while (layoutSwitchers.getChildCount() < targetLayoutSwitchersElementsCount) {
            inflater.inflate(R.layout.switcher, layoutSwitchers, true);
            inflater.inflate(R.layout.switcher_space, layoutSwitchers, true);
        }
        assert layoutSwitchers.getChildCount() == targetLayoutSwitchersElementsCount;

        final Switch[] switchers = new Switch[switchersCount];
        final View[] switcherSpaces = new View[switchersCount];
        for (int i = 0; i < switchersCount; ++i) {
            switchers[i] = (Switch) layoutSwitchers.getChildAt(i * 2 + 1);
            switcherSpaces[i] = layoutSwitchers.getChildAt(i * 2 + 2);
        }

        final LinearLayout layoutSettingsButtons = convertView.findViewById(R.id.layoutSettingsButtons);
        if (settingsButtonsVisible) {
            layoutSettingsButtons.setVisibility(View.VISIBLE);
            final ImageButton btnMoveUp = convertView.findViewById(R.id.btnMoveUp);
            final ImageButton btnSettings = convertView.findViewById(R.id.btnSettings);
            final ImageButton btnMoveDown = convertView.findViewById(R.id.btnMoveDown);

            btnMoveUp.setEnabled(position > 0);
            btnMoveDown.setEnabled(position < getCount() - 1);

            btnMoveUp.setOnClickListener(v -> {
                devicesList.swap(position, position - 1);
                notifyDataSetChanged();
            });
            btnMoveDown.setOnClickListener(v -> {
                devicesList.swap(position, position + 1);
                notifyDataSetChanged();
            });
            btnSettings.setOnClickListener(v -> {
                DeviceSettings screen = (DeviceSettings)screenLauncher.launchScreen(ScreenId.DEVICE_SETTINGS);
                screen.setDevice(device);
            });
        } else {
            layoutSettingsButtons.setVisibility(View.GONE);
        }

        // Populate the data into the template view using the data object
        txtDeviceName.setText(device.getName());
        txtDeviceMac.setText(device.getMacAddress());
        txtDeviceIp.setText(device.getHttpAddressWithoutPrefix());

        final String httpPassword = device.getHttpPassword();
        final boolean discovered = device.isDiscovered();

        for (int i = 0; i < dimmers.length; ++i) {
            final int dimId = device.getDimmerIndexByOrder(i);
            if (!device.isDimmerActive(dimId)) {
                dimmers[i].setVisibility(View.GONE);
                continue;
            }
            dimmers[i].setVisibility(View.VISIBLE);
            dimmers[i].setProgress(device.getDimmerValue(dimId));
            dimmers[i].setEnabled(discovered);
            dimmers[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                private int oldProgress = 0;

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    oldProgress = seekBar.getProgress();
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    seekBar.setEnabled(false);
                    Http.asyncRequest(
                            device.getHttpAddress() + DeviceInfo.Handlers.SET_VALUES + "?dim" + dimId + "=" + seekBar.getProgress(),
                            null,
                            httpPassword,
                            null,
                            3,
                            new Http.Listener() {
                                @Override
                                public void onResponse(Http.Response response) {
                                    if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
                                        final String responseStr = response.getDataAsStr();
                                        if (responseStr.startsWith("ACCEPTED") || responseStr.startsWith("NOTHING_CHANGED")) {
                                            device.setDimmerValue(dimId, seekBar.getProgress());
                                            activity.runOnUiThread(() -> seekBar.setEnabled(true));
                                        } else {
                                            onError(new IOException("Bad response: " + responseStr));
                                        }
                                    } else {
                                        onError(new IOException("Bad response code: " + response.getHttpCode()));
                                    }
                                }

                                @Override
                                public void onError(IOException exception) {
                                    activity.runOnUiThread(() -> {
                                        seekBar.setProgress(oldProgress);
                                        seekBar.setEnabled(true);
                                    });
                                }
                            }
                    );
                }
            });
        }

        for (int i = 0; i < switchers.length; ++i) {
            final int swId = device.getSwitcherIndexByOrder(i);
            if (!device.isSwitcherActive(swId)) {
                switchers[i].setVisibility(View.GONE);
                switcherSpaces[i].setVisibility(View.GONE);
                continue;
            }
            switchers[i].setVisibility(View.VISIBLE);
            switcherSpaces[i].setVisibility(View.VISIBLE);
            switchers[i].setOnCheckedChangeListener(null);
            switchers[i].setChecked(device.getSwitcherValue(swId));
            switchers[i].jumpDrawablesToCurrentState();
            switchers[i].setEnabled(discovered);
            switchers[i].setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    CompoundButton.OnCheckedChangeListener checkedChangeListener = this;
                    buttonView.setEnabled(false);
                    Http.asyncRequest(
                            device.getHttpAddress() + DeviceInfo.Handlers.SET_VALUES + "?sw" + swId + "=" + (isChecked ? 1 : 0),
                            null,
                            httpPassword,
                            null,
                            3,
                            new Http.Listener() {
                                @Override
                                public void onResponse(Http.Response response) {
                                    if (response.getHttpCode() == HttpURLConnection.HTTP_OK) {
                                        final String responseStr = response.getDataAsStr();
                                        if (responseStr.startsWith("ACCEPTED") || responseStr.startsWith("NOTHING_CHANGED")) {
                                            device.setSwitcherValue(swId, isChecked);
                                            activity.runOnUiThread(() -> buttonView.setEnabled(true));
                                        } else {
                                            onError(new IOException("Bad response: " + responseStr));
                                        }
                                    } else {
                                        onError(new IOException("Bad response code: " + response.getHttpCode()));
                                    }
                                }

                                @Override
                                public void onError(IOException exception) {
                                    activity.runOnUiThread(() -> {
                                        buttonView.setOnCheckedChangeListener(null);
                                        buttonView.setChecked(!isChecked);
                                        buttonView.setOnCheckedChangeListener(checkedChangeListener);
                                        buttonView.setEnabled(true);
                                    });
                                }
                            }
                    );
                }
            });
        }

        // Return the completed view to render on screen
        return convertView;
    }
}
