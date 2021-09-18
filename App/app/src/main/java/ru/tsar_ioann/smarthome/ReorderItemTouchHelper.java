package ru.tsar_ioann.smarthome;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

public class ReorderItemTouchHelper extends ItemTouchHelper {
    public interface OrderChangedListener {  // RecyclerView adapter should implement it
        void onOrderChanged(int fromPos, int toPos);
    }

    public ReorderItemTouchHelper(RecyclerView recyclerView) {
        super(new SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getBindingAdapterPosition();
                int toPos = target.getBindingAdapterPosition();
                final RecyclerView.Adapter adapter = recyclerView.getAdapter();
                if (adapter != null) {
                    ((OrderChangedListener)adapter).onOrderChanged(fromPos, toPos);
                    adapter.notifyItemMoved(fromPos, toPos);
                }
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}
        });
        attachToRecyclerView(recyclerView);
    }
}
