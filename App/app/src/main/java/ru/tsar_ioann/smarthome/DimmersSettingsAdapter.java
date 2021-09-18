package ru.tsar_ioann.smarthome;

import android.annotation.SuppressLint;
import android.text.InputFilter;
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

public class DimmersSettingsAdapter extends RecyclerView.Adapter<DimmersSettingsAdapter.ViewHolder>
        implements ReorderItemTouchHelper.OrderChangedListener {
    private static final InputFilter[] INPUT_FILTERS_VALUE_CHANGE_STEP = new InputFilter[]{
            new Utils.IntInRangeInputFilter(1, DeviceInfo.MAX_VALUE_CHANGE_STEP)
    };
    private static final InputFilter[] INPUT_FILTERS_MICROS = new InputFilter[]{
            // Do not use more than 1 in min value here
            new Utils.IntInRangeInputFilter(1, DeviceInfo.MAX_LIGHTNESS_MICROS)
    };

    private final ItemTouchHelper itemTouchHelper;
    private final DeviceInfo.DimmerSettings[] dimmersSettings;
    private final OrderingKeeper ordering;

    public static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView txtDimPin;
        private final CheckBox cbDimActive;
        private final EditText edtDimValueChangeStep;
        private final EditText edtDimMinLightnessMicros;
        private final EditText edtDimMaxLightnessMicros;
        private final ImageView imgDimReorderHandle;

        public ViewHolder(View view) {
            super(view);
            txtDimPin = view.findViewById(R.id.txtDimPin);
            cbDimActive = view.findViewById(R.id.cbDimActive);
            edtDimValueChangeStep = view.findViewById(R.id.edtDimValueChangeStep);
            edtDimMinLightnessMicros = view.findViewById(R.id.edtDimMinLightnessMicros);
            edtDimMaxLightnessMicros = view.findViewById(R.id.edtDimMaxLightnessMicros);
            imgDimReorderHandle = view.findViewById(R.id.imgDimReorderHandle);
        }

        public boolean isActive() {
            return cbDimActive.isChecked();
        }

        public String getDimValueChangeStep() {
            return edtDimValueChangeStep.getText().toString();
        }

        public String getDimMinLightnessMicros() {
            return edtDimMinLightnessMicros.getText().toString();
        }

        public String getDimMaxLightnessMicros() {
            return edtDimMaxLightnessMicros.getText().toString();
        }
    }

    public DimmersSettingsAdapter(ItemTouchHelper itemTouchHelper, DeviceInfo.DimmerSettings[] dimmersSettings, OrderingKeeper ordering) {
        this.itemTouchHelper = itemTouchHelper;
        this.dimmersSettings = dimmersSettings;
        this.ordering = ordering;
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
        final DeviceInfo.DimmerSettings dimSettings = dimmersSettings[ordering.getIndex(position)];
        assert dimSettings != null;

        holder.edtDimValueChangeStep.setFilters(INPUT_FILTERS_VALUE_CHANGE_STEP);
        holder.edtDimMinLightnessMicros.setFilters(INPUT_FILTERS_MICROS);
        holder.edtDimMaxLightnessMicros.setFilters(INPUT_FILTERS_MICROS);

        holder.txtDimPin.setText(Byte.toString(dimSettings.pin));
        holder.cbDimActive.setChecked(dimSettings.active);
        holder.cbDimActive.jumpDrawablesToCurrentState();
        holder.edtDimValueChangeStep.setText(Integer.toString(dimSettings.valueChangeStep));
        holder.edtDimMinLightnessMicros.setText(Integer.toString(dimSettings.minLightnessMicros));
        holder.edtDimMaxLightnessMicros.setText(Integer.toString(dimSettings.maxLightnessMicros));

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

    @Override
    public void onOrderChanged(int fromPos, int toPos) {
        ordering.changeOrder(fromPos, toPos);
    }
}
