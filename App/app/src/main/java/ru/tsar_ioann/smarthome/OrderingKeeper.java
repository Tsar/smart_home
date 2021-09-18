package ru.tsar_ioann.smarthome;

import android.util.Log;

import java.util.HashSet;
import java.util.Set;

public class OrderingKeeper {
    private static final String LOG_TAG = "OrderingKeeper";

    private final DeviceInfo.BaseSettings[] settings;
    private final int[] orderToIndex;  // order -> index

    public OrderingKeeper(DeviceInfo.BaseSettings[] settings) {
        this.settings = settings;
        final Set<Integer> validationSet = new HashSet<>();
        orderToIndex = new int[settings.length];
        for (int i = 0; i < settings.length; ++i) {
            DeviceInfo.BaseSettings setting = settings[i];
            assert setting != null;
            if (setting.order < 0 || setting.order >= settings.length || validationSet.contains(setting.order)) {
                fillFallbackOrdering();
                return;
            }
            validationSet.add(setting.order);
            orderToIndex[setting.order] = i;
        }
    }

    private void fillFallbackOrdering() {
        Log.d(LOG_TAG, "Got invalid order of settings, fallback to default");
        for (int i = 0; i < orderToIndex.length; ++i) {
            orderToIndex[i] = i;
        }
    }

    public int getIndex(int order) {
        return orderToIndex[order];
    }

    private void orderSwap(int pos1, int pos2) {
        settings[orderToIndex[pos1]].order = pos2;
        settings[orderToIndex[pos2]].order = pos1;
        int tmp = orderToIndex[pos1];
        orderToIndex[pos1] = orderToIndex[pos2];
        orderToIndex[pos2] = tmp;
    }

    public void changeOrder(int fromPos, int toPos) {
        if (fromPos < toPos) {
            for (int i = fromPos; i < toPos; ++i) {
                orderSwap(i, i + 1);
            }
        } else {
            for (int i = fromPos; i > toPos; --i) {
                orderSwap(i, i - 1);
            }
        }
    }
}
