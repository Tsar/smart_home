package ru.tsar_ioann.smarthome;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class SwitchersSettingsAdapter extends RecyclerView.Adapter<SwitchersSettingsAdapter.ViewHolder> {
    private final ItemTouchHelper itemTouchHelper;
    private final DeviceInfo.SwitcherSettings[] switchersSettings;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtSwPin;
        private final CheckBox cbSwEnabled;
        private final CheckBox cbSwInverted;
        private final ImageView imgSwReorderHandle;

        public ViewHolder(View view) {
            super(view);
            txtSwPin = view.findViewById(R.id.txtSwPin);
            cbSwEnabled = view.findViewById(R.id.cbSwEnabled);
            cbSwInverted = view.findViewById(R.id.cbSwInverted);
            imgSwReorderHandle = view.findViewById(R.id.imgSwReorderHandle);
        }
    }

    public SwitchersSettingsAdapter(ItemTouchHelper itemTouchHelper, DeviceInfo.SwitcherSettings[] switchersSettings) {
        this.itemTouchHelper = itemTouchHelper;
        this.switchersSettings = switchersSettings;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SwitchersSettingsAdapter.ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_switcher_settings, parent, false)
        );
    }

    @SuppressLint({"ClickableViewAccessibility", "SetTextI18n"})
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final DeviceInfo.SwitcherSettings swSettings = switchersSettings[position];
        // TODO: set holder.cbSwEnabled
        if (swSettings != null) {
            holder.txtSwPin.setText(Byte.toString(swSettings.pin));
            holder.cbSwInverted.setChecked(swSettings.inverted);
        } else {
            holder.txtSwPin.setText("");
            holder.cbSwInverted.setChecked(false);
        }
        holder.cbSwInverted.jumpDrawablesToCurrentState();

        holder.imgSwReorderHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                itemTouchHelper.startDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return switchersSettings.length;
    }
}
