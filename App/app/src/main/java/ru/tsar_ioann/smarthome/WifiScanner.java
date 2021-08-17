package ru.tsar_ioann.smarthome;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.NetworkSpecifier;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiNetworkSpecifier;
import android.util.Log;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class WifiScanner {
    public interface WifiScanListener {
        void onWifiFound(String ssid);
        void onScanFinished();
    }

    private final int SCAN_DURATION_MS = 30000;

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
        }, SCAN_DURATION_MS);
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

    public void connectToWifi(String ssid, String passphrase) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            // TODO: other way (may be just ask user to connect manually?)
            return;
        }

        final NetworkSpecifier specifier = new WifiNetworkSpecifier.Builder()
                .setSsid(ssid)
                .setWpa2Passphrase(passphrase)
                .build();
        final NetworkRequest request = new NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build();
        final ConnectivityManager connectivityManager = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

        final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(Network network) {
                Log.d("WIFI_CONNECT", "onAvailable");
                // TODO: call some callback
            }

            @Override
            public void onUnavailable() {
                Log.d("WIFI_CONNECT", "onUnavailable");
                // TODO: call error callback
            }
        };
        connectivityManager.requestNetwork(request, networkCallback);

        // TODO later
        //connectivityManager.unregisterNetworkCallback(networkCallback);
    }
}
