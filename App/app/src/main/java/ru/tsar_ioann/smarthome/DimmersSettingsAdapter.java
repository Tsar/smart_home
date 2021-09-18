package ru.tsar_ioann.smarthome;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class DimmersSettingsAdapter extends RecyclerView.Adapter<DimmersSettingsAdapter.ViewHolder> {
    private final ItemTouchHelper itemTouchHelper;
    private final DeviceInfo.DimmerSettings[] dimmersSettings;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtDimPin;
        private final CheckBox cbDimEnabled;
        private final EditText edtDimValueChangeStep;
        private final EditText edtDimMinLightnessMicros;
        private final EditText edtDimMaxLightnessMicros;
        private final ImageView imgDimReorderHandle;

        public ViewHolder(View view) {
            super(view);
            txtDimPin = view.findViewById(R.id.txtDimPin);
            cbDimEnabled = view.findViewById(R.id.cbDimEnabled);
            edtDimValueChangeStep = view.findViewById(R.id.edtDimValueChangeStep);
            edtDimMinLightnessMicros = view.findViewById(R.id.edtDimMinLightnessMicros);
            edtDimMaxLightnessMicros = view.findViewById(R.id.edtDimMaxLightnessMicros);
            imgDimReorderHandle = view.findViewById(R.id.imgDimReorderHandle);
        }
    }

    public DimmersSettingsAdapter(ItemTouchHelper itemTouchHelper, DeviceInfo.DimmerSettings[] dimmersSettings) {
        this.itemTouchHelper = itemTouchHelper;
        this.dimmersSettings = dimmersSettings;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_dimmer_settings, parent, false)
        );
    }

    @SuppressLint({"SetTextI18n", "ClickableViewAccessibility"})
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        final DeviceInfo.DimmerSettings dimSettings = dimmersSettings[position];
        // TODO: set holder.cbDimEnabled
        if (dimSettings != null) {
            holder.txtDimPin.setText(Byte.toString(dimSettings.pin));
            holder.edtDimValueChangeStep.setText(Integer.toString(dimSettings.valueChangeStep));
            holder.edtDimMinLightnessMicros.setText(Integer.toString(dimSettings.minLightnessMicros));
            holder.edtDimMaxLightnessMicros.setText(Integer.toString(dimSettings.maxLightnessMicros));
        } else {
            holder.txtDimPin.setText("");
            holder.edtDimValueChangeStep.setText("");
            holder.edtDimMinLightnessMicros.setText("");
            holder.edtDimMaxLightnessMicros.setText("");
        }

        holder.imgDimReorderHandle.setOnTouchListener((v, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                itemTouchHelper.startDrag(holder);
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return dimmersSettings.length;
    }
}
