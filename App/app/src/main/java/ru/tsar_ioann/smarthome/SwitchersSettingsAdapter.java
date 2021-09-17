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
    private final boolean[] switchersInverted;

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

    public SwitchersSettingsAdapter(ItemTouchHelper itemTouchHelper, boolean[] switchersInverted) {
        this.itemTouchHelper = itemTouchHelper;
        this.switchersInverted = switchersInverted;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new SwitchersSettingsAdapter.ViewHolder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_switcher_settings, parent, false)
        );
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.txtSwPin.setText("9");
        holder.cbSwInverted.setChecked(switchersInverted[position]);
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
        return switchersInverted.length;
    }
}
