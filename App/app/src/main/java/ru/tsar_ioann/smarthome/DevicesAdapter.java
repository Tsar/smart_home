package ru.tsar_ioann.smarthome;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

public class DevicesAdapter extends ArrayAdapter<DeviceInfo> {
    public DevicesAdapter(Context context, List<DeviceInfo> devices) {
        super(context, 0, devices);
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
        final TextView txtDeviceName = (TextView)convertView.findViewById(R.id.txtDeviceName);
        final TextView txtDeviceMac = (TextView)convertView.findViewById(R.id.txtDeviceMac);
        final TextView txtDeviceIp = (TextView)convertView.findViewById(R.id.txtDeviceIp);
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
        String ipAddress = device.getIpAddress();
        txtDeviceIp.setText(ipAddress);

        for (int i = 0; i < dimmers.length; ++i) {
            int finalI = i;
            dimmers[i].setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser) {
                        return;
                    }

                    // interpolation is here only for first tests
                    int value = (4000 - 8300) * progress / 1000 + 8300;

                    Http.doAsyncRequest(
                            "http://" + ipAddress + "/set_values?dim" + finalI + "=" + value,
                            null,
                            "12345",  // TODO: work on this
                            null,
                            3,
                            null
                    );
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        for (int i = 0; i < switchers.length; ++i) {
            int finalI = i;
            switchers[i].setOnCheckedChangeListener((buttonView, isChecked) -> {
                Http.doAsyncRequest(
                        "http://" + ipAddress + "/set_values?sw" + finalI + "=" + (isChecked ? 1 : 0),
                        null,
                        "12345",  // TODO: work on this
                        null,
                        3,
                        null
                );
            });
        }

        // Return the completed view to render on screen
        return convertView;
    }
}
