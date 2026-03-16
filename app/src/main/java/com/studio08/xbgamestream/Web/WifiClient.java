package com.studio08.xbgamestream.Web;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

public class WifiClient {
    private final WifiManager wifiManager;
    private WifiManager.WifiLock wifiLock;

    public WifiClient(Context context) {
        // Get the WifiManager instance
        wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
    }

    public void acquireWifiLock() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (wifiLock == null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "WebRTC_LowLatencyLock");
                        } else {
                            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WebRTC_LowLatencyLock");
                        }
                    }
                    if (!wifiLock.isHeld()) {
                        wifiLock.acquire();
                        Log.w("WifiClient", "Wi-Fi Low Latency Lock Acquired");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void acquireWifiLockOld() {
        try {
            // Create a Wi-Fi lock with WIFI_MODE_FULL_LOW_LATENCY
            if (wifiLock == null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "WebRTC_LowLatencyLock");
                } else {
                    wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "WebRTC_LowLatencyLock");
                }
            }
            if (!wifiLock.isHeld()) {
                wifiLock.acquire(); // Acquire the lock
                Log.w("WifiClient", "Wi-Fi Low Latency Lock Acquired");
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void releaseWifiLock() {
        try {
            // Release the Wi-Fi lock
            if (wifiLock != null && wifiLock.isHeld()) {
                wifiLock.release();
                Log.w("WifiClient", "Wi-Fi Low Latency Lock Released");
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
