package ru.tsar_ioann.smarthome;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.List;

public class DevicesAdapter extends ArrayAdapter<DeviceInfo> {
    private final Activity activity;

    public DevicesAdapter(Activity activity, List<DeviceInfo> devices) {
        super(activity, 0, devices);
        this.activity = activity;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the data item for this position
        DeviceInfo device = getItem(position);

        // Check if an existing view is being reused, otherwise inflate the view
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_device, parent, false);
        }

        // Lookup view for data population
        final TextView txtDeviceName = convertView.findViewById(R.id.txtDeviceName);
        final TextView txtDeviceMac = convertView.findViewById(R.id.txtDeviceMac);
        final TextView txtDeviceIp = convertView.findViewById(R.id.txtDeviceIp);
        final SeekBar[] dimmers = new SeekBar[]{
                convertView.findViewById(R.id.dim0),
                convertView.findViewById(R.id.dim1),
                convertView.findViewById(R.id.dim2)
        };
        final Switch[] switchers = new Switch[]{
                convertView.findViewById(R.id.sw0),
                convertView.findViewById(R.id.sw1),
                convertView.findViewById(R.id.sw2),
                convertView.findViewById(R.id.sw3),
        };

        // Populate the data into the template view using the data object
        txtDeviceName.setText(device.getName());
        txtDeviceMac.setText(device.getMacAddress());
        txtDeviceIp.setText(device.getHttpAddressWithoutPrefix());

        final String httpPassword = device.getHttpPassword();
        final boolean discovered = device.isDiscovered();

        for (int i = 0; i < dimmers.length; ++i) {
            dimmers[i].setProgress(device.getDimmerValue(i));
            dimmers[i].setEnabled(discovered);
            int finalI = i;
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
                            device.getHttpAddress() + "/set_values?dim" + finalI + "=" + seekBar.getProgress(),
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
                                            device.setDimmerValue(finalI, seekBar.getProgress());
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
            switchers[i].setOnCheckedChangeListener(null);
            switchers[i].setChecked(device.getSwitcherValue(i));
            switchers[i].setEnabled(discovered);
            int finalI = i;
            switchers[i].setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    CompoundButton.OnCheckedChangeListener checkedChangeListener = this;
                    buttonView.setEnabled(false);
                    Http.asyncRequest(
                            device.getHttpAddress() + "/set_values?sw" + finalI + "=" + (isChecked ? 1 : 0),
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
                                            device.setSwitcherValue(finalI, isChecked);
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
