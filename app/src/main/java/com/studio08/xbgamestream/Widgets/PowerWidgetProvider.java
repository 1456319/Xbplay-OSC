package com.studio08.xbgamestream.Widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.R;

import Interfaces.SmartglassEvents;
import network.BindService;


public class PowerWidgetProvider extends AppWidgetProvider implements SmartglassEvents {

    public Boolean mServiceBound = false;
    public BindService mBoundService;
    public Context mContext;

    // listener for service connection
    private ServiceConnection localServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BindService.MyBinder myBinder = (BindService.MyBinder) service;
            mBoundService = myBinder.getService();
            mServiceBound = true;

            mBoundService.setListener(PowerWidgetProvider.this);
            mBoundService.discover();
        }
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.e("here", "ON UPDATE" + appWidgetIds.length);
        updateWidgetViews(context, false);
    }

    void updateWidgetViews(Context context, boolean disableButton){
        Log.e("Here", "Called updateWidgetViews: " + disableButton);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout_power);
        ComponentName watchWidget = new ComponentName(context, PowerWidgetProvider.class);


        if(disableButton) {
            views.setViewVisibility(R.id.power_on, View.INVISIBLE);
            views.setViewVisibility(R.id.power_waiting, View.VISIBLE);
        } else {
            views.setOnClickPendingIntent(R.id.power_on, getPendingSelfIntent(context, "power"));
            views.setViewVisibility(R.id.power_on, View.VISIBLE);
            views.setViewVisibility(R.id.power_waiting, View.INVISIBLE);
        }

        AppWidgetManager.getInstance(context).updateAppWidget(watchWidget, views);
    }

    PendingIntent getPendingSelfIntent(Context context, String id) {
        Intent intent = new Intent(context, getClass());
        intent.setAction(id);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public void sendXboxCommand(Intent intent, Context context){
        if( intent.getAction() != null) {
            Log.e("here", "Receive value: " + intent.getAction());

            if(!TextUtils.isEmpty(intent.getAction()) && !intent.getAction().equals("android.appwidget.action.APPWIDGET_UPDATE_OPTIONS")) {
                // send vibration
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build();
                v.vibrate(40, audioAttributes);

                // always send power on command
                sendBroadcast(context, intent.getAction());
            } else {
                Log.e("here", "Receive BAD command: " + intent.getAction());
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        Log.e("here", "Receive");
        mContext = context;
        if(TextUtils.isEmpty(intent.getAction()) || !intent.getAction().equals("power")){
            return;
        }
        updateWidgetViews(context, true);

        // attempt to connect to console and power off
        retryConnect(3, context.getApplicationContext());

        // attempt to power on
        sendXboxCommand(intent, context);
    }

    // todo listen for ack response somehow and implement all buttons
    public void sendBroadcast(Context context, String action){
        Intent intent = new Intent();
        intent.setAction("buttonPress");
        intent.putExtra("buttonValue", action);
        context.sendBroadcast(intent);
    }


    private void retryConnect(int retriesLeft, Context context) {
        updateWidgetViews(context, true);

        // show error after retrying
        if(retriesLeft <= 0){
            Log.e("HERE", "Error connecting, out of retries!!!!");
            Toast.makeText(context.getApplicationContext(), "Cannot connect to Xbox!", Toast.LENGTH_SHORT).show();
            updateWidgetViews(context, false);
            return;
        }

        if(TextUtils.isEmpty(getLiveId(context))){
            Log.e("HERE","Cant use widget until initial connect");
            Toast.makeText(context.getApplicationContext(), "Your Xbox must be powered on for first time setup", Toast.LENGTH_SHORT).show();
        }

        // logic to connect to console
        boolean isConnected = bindToService(context);

        // if we couldn't connect, schedule retry
        if(!isConnected) {
            // attempt to connect again if we are not connected
            final Handler handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    boolean isConnected = bindToService(context);
                    if (!isConnected) {
                        retryConnect(retriesLeft - 1, context);
                    }
                }
            }, 3000);
        } else {
            updateWidgetViews(context, false);
        }
    }

    public boolean checkIfConnected(){
        if (mServiceBound && mBoundService.ready) {
            Log.e("HERE", "Service is running and connected!");
            return true;
        }
        Log.e("HERE", "Service is not connected!");
        return false;
    }

    public boolean bindToService(Context context){
        if (checkIfConnected()) {
            Log.e("HERE", "Detected service already running! Not restarting.");
//            sendBroadcast(context, "power_on");
            updateWidgetViews(context, false);
            return true;
        }

        // always try to power on if possible
        if(mBoundService != null) {
            mBoundService.powerOn(getLiveId(context));
        }

        // start xbox smartglass
        Intent serviceIntent = new Intent(context, BindService.class);
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        String notificationSettings = prefs.getString("notification_remote_key", "close_on_exit");
        serviceIntent.putExtra("notification_type", "always_show");
        ContextCompat.startForegroundService(context.getApplicationContext(), serviceIntent);
        context.bindService(serviceIntent, localServiceConnection, Context.BIND_AUTO_CREATE);
        if (mServiceBound) {
            mBoundService.discover();
        }
        return false;
    }

    @Override
    public void xboxDiscovered() {
        Log.e("widget", "Xbox Discovered");
        mBoundService.connect();
    }

    @Override
    public void xboxConnected() {
        Log.e("widget", "Xbox Connected");
        Log.e("RemoteFrag", "Xbox Connected");
        try {
            mBoundService.openChannels();
            String xboxLiveId = mBoundService.getLiveId();
            saveLiveId(xboxLiveId);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void xboxDisconnected() {
        Log.e("widget", "Xbox disconnected");
        mBoundService.ready = false;
    }

    private void saveLiveId(String liveId){
        Log.e("HERE","saving live id: " + liveId);
        EncryptClient encryptClient = new EncryptClient(mContext.getApplicationContext());
        encryptClient.saveValue("liveId", liveId);
    }

    private String getLiveId(Context context){
        EncryptClient encryptClient = new EncryptClient(context.getApplicationContext());
        return encryptClient.getValue("liveId");
    }
}