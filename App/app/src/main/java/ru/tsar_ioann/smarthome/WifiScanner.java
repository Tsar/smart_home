package ru.tsar_ioann.smarthome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class WifiScanner {
    public interface WifiScanListener {
        void onWifiFound(String ssid);
        void onScanFinished();
    }

    private final Context context;
    private final WifiManager wifiManager;
    private WifiScanListener listener;

    public WifiScanner(Context context) {
        this.context = context.getApplicationContext();
        wifiManager = (WifiManager)this.context.getSystemService(Context.WIFI_SERVICE);
    }

    public boolean isWifiEnabled() {
        return wifiManager.isWifiEnabled();
    }

    public void startScan(WifiScanListener listener) {
        this.listener = listener;

        BroadcastReceiver wifiScanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context c, Intent intent) {
                boolean success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false);
                if (success) {
                    scanSuccess();
                } else {
                    scanFailure();
                }
            }
        };

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        context.registerReceiver(wifiScanReceiver, intentFilter);

        boolean success = wifiManager.startScan();
        if (!success) {
            scanFailure();
        }

        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                context.unregisterReceiver(wifiScanReceiver);
                listener.onScanFinished();
            }
        }, 5000);
    }

    private void scanSuccess() {
        Log.d("WIFI_SCAN", "scanSuccess");
        List<ScanResult> results = wifiManager.getScanResults();
        for (ScanResult wifi : results) {
            Log.d("WIFI_SCAN", "[#1] Found wi-fi: " + wifi.SSID);
            listener.onWifiFound(wifi.SSID);
        }
    }

    private void scanFailure() {
        Log.d("WIFI_SCAN", "scanFailure");
        // handle failure: new scan did NOT succeed
        // consider using old scan results: these are the OLD results!
        List<ScanResult> results = wifiManager.getScanResults();
        for (ScanResult wifi : results) {
            Log.d("WIFI_SCAN", "[#2] Found wi-fi: " + wifi.SSID);
            listener.onWifiFound(wifi.SSID);
        }
    }
}
