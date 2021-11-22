package ru.tsar_ioann.smarthome;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.net.HttpURLConnection;

import ru.tsar_ioann.smarthome.screens.DeviceSettings;

public class DevicesAdapter extends RecyclerView.Adapter<DevicesAdapter.ViewHolder>
        implements ReorderItemTouchHelper.OrderChangedListener {
    private final Activity activity;
    private final DevicesList devicesList;
    private final ScreenLauncher screenLauncher;
    private final FirmwareUpdater firmwareUpdater;
    private final ItemTouchHelper itemTouchHelper;

    private boolean settingsButtonsVisible = false;
    private LayoutInflater inflater;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtDeviceName;
        private final TextView txtDeviceMac;
        private final TextView txtDeviceIp;
        private final LinearLayout layoutDimmers;
        private final LinearLayout layoutSwitchers;
        private final LinearLayout layoutSettingsButtons;
        private final ImageButton btnSettings;
        private final ImageButton btnDelete;
        private final ImageButton btnUpdateFirmware;
        private final LinearLayout layoutMoveHandles;

        public ViewHolder(View view) {
            super(view);
            txtDeviceName = view.findViewById(R.id.txtDeviceName);
            txtDeviceMac = view.findViewById(R.id.txtDeviceMac);
            txtDeviceIp = view.findViewById(R.id.txtDeviceIp);
            layoutDimmers = view.findViewById(R.id.layoutDimmers);
            layoutSwitchers = view.findViewById(R.id.layoutSwitchers);
            layoutSettingsButtons = view.findViewById(R.id.layoutSettingsButtons);
            btnSettings = view.findViewById(R.id.btnSettings);
            btnDelete = view.findViewById(R.id.btnDelete);
            btnUpdateFirmware = view.findViewById(R.id.btnUpdateFirmware);
            layoutMoveHandles = view.findViewById(R.id.layoutMoveHandles);
        }
    }

    public DevicesAdapter(Activity activity, DevicesList devicesList, ScreenLauncher screenLauncher, FirmwareUpdater firmwareUpdater, ItemTouchHelper itemTouchHelper) {
        this.activity = activity;
        this.devicesList = devicesList;
        this.screenLauncher = screenLauncher;
        this.firmwareUpdater = firmwareUpdater;
        this.itemTouchHelper = itemTouchHelper;
    }

    public void notifyAllUpdated() {
        notifyItemRangeChanged(0, getItemCount());
    }

    public void setSettingsButtonsVisible(boolean visible) {
        settingsButtonsVisible = visible;
        notifyAllUpdated();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        inflater = LayoutInflater.from(parent.getContext());
        return new ViewHolder(inflater.inflate(R.layout.item_device, parent, false));
    }

    @SuppressLint({"ClickableViewAccessibility", "SetTextI18n"})
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final DeviceInfo device = devicesList.getList().get(position);

        final LinearLayout layoutDimmers = holder.layoutDimmers;
        final LinearLayout layoutSwitchers = holder.layoutSwitchers;

        synchronized (device) {
            final int dimmersCount = device.getActiveDimmersCount();
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

            final int switchersCount = device.getActiveSwitchersCount();
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
            for (int i = 0; i < switchersCount; ++i) {
                switchers[i] = (Switch) layoutSwitchers.getChildAt(i * 2 + 1);
            }

            if (settingsButtonsVisible) {
                holder.btnUpdateFirmware.setVisibility(
                        device.isDiscovered()
                                && device.supportsFirmwareUpdateOverNetwork()
                                && device.getFirmwareVersion() < firmwareUpdater.getLastFirmwareVersion()
                            ? View.VISIBLE
                            : View.GONE
                );

                holder.layoutSettingsButtons.setVisibility(View.VISIBLE);
                holder.layoutMoveHandles.setVisibility(View.VISIBLE);

                holder.layoutMoveHandles.setOnTouchListener((v, event) -> {
                    if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        itemTouchHelper.startDrag(holder);
                    }
                    return false;
                });

                holder.btnSettings.setOnClickListener(v -> {
                    DeviceSettings screen = (DeviceSettings) screenLauncher.launchScreen(ScreenId.DEVICE_SETTINGS);
                    screen.setDevice(device);
                });

                holder.btnDelete.setOnClickListener(v -> {
                    final Resources resources = activity.getResources();
                    Utils.showYesNoDialog(
                            activity,
                            Utils.tr(resources, R.string.question),
                            Utils.tr(resources, R.string.confirm_delete, device.getName()),
                            (dialog, which) -> notifyItemRemoved(devicesList.removeDevice(device.getMacAddress())),
                            (dialog, which) -> {}
                    );
                });

                holder.btnUpdateFirmware.setOnClickListener(v -> {
                    final Resources resources = activity.getResources();
                    Utils.showYesNoDialog(
                            activity,
                            Utils.tr(resources, R.string.question),
                            Utils.tr(
                                    resources,
                                    R.string.confirm_firmware_update,
                                    firmwareUpdater.getLastFirmwareVersion(),
                                    device.getName(),
                                    firmwareUpdater.getLastFirmwareDescription(Utils.tr(resources, R.string.lang_key))
                            ),
                            (dialog, which) -> Utils.showYesNoDialog(
                                    activity,
                                    Utils.tr(resources, R.string.warning),
                                    Utils.tr(resources, R.string.confirm_firmware_update_2, device.getName()),
                                    (dialog2, which2) -> {
                                        Toast.makeText(
                                                activity,
                                                Utils.tr(resources, R.string.update_started, device.getName()),
                                                Toast.LENGTH_LONG
                                        ).show();
                                        firmwareUpdater.asyncUpdateFirmware(device, new FirmwareUpdater.Listener() {
                                            @Override
                                            public void onSuccess() {
                                                activity.runOnUiThread(() -> {
                                                    // TODO
                                                });
                                            }

                                            @Override
                                            public void onError(String message) {
                                                activity.runOnUiThread(() -> Utils.showOkDialog(
                                                        activity,
                                                        Utils.tr(resources, R.string.error),
                                                        message
                                                ));
                                            }
                                        });
                                    },
                                    (dialog2, which2) -> {}
                            ),
                            (dialog, which) -> {}
                    );
                });
            } else {
                holder.layoutSettingsButtons.setVisibility(View.GONE);
                holder.layoutMoveHandles.setVisibility(View.GONE);
            }

            // Populate the data into the template view using the data object
            holder.txtDeviceName.setText(device.getName());
            holder.txtDeviceMac.setText(device.getMacAddress() + " (v" + device.getFirmwareVersion() + ")");
            holder.txtDeviceIp.setText(device.getHttpAddressWithoutPrefix());

            final String httpPassword = device.getHttpPassword();
            final boolean discovered = device.isDiscovered();

            int i = 0;
            for (int j = 0; j < device.getDimmersCount(); ++j) {
                final int dimId = device.getDimmerIndexByOrder(j);
                if (!device.isDimmerActive(dimId)) {
                    continue;
                }
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
                ++i;
            }

            i = 0;
            for (int j = 0; j < device.getSwitchersCount(); ++j) {
                final int swId = device.getSwitcherIndexByOrder(j);
                if (!device.isSwitcherActive(swId)) {
                    continue;
                }
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
                ++i;
            }
        }
    }

    @Override
    public int getItemCount() {
        return devicesList.getList().size();
    }

    @Override
    public void onOrderChanged(int fromPos, int toPos) {
        if (fromPos < toPos) {
            for (int i = fromPos; i < toPos; ++i) {
                devicesList.swap(i, i + 1);
            }
        } else {
            for (int i = fromPos; i > toPos; --i) {
                devicesList.swap(i, i - 1);
            }
        }
    }
}
