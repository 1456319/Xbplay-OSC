package com.studio08.xbgamestream.Widgets;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.RewardedAdLoader;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.R;

import Interfaces.SmartglassEvents;
import network.BindService;


public class RemoteWidgetProvider extends AppWidgetProvider implements SmartglassEvents {

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

            mBoundService.setListener(RemoteWidgetProvider.this);
            mBoundService.discover();
        }
    };

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.e("here", "ON UPDATE: " + appWidgetIds.length);
        updateWidgetViews(context, false, false, false);
    }

    void updateWidgetViews(Context context, Boolean showLoading, boolean showFailed, boolean showSuccess){
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
        ComponentName watchWidget = new ComponentName(context, RemoteWidgetProvider.class);

        // always enable these buttons even if remote isnt conencted
        views.setOnClickPendingIntent(R.id.widget_connect_button, getPendingSelfIntent(context, "widget_connect_button"));
        views.setOnClickPendingIntent(R.id.widget_connect_button2, getPendingSelfIntent(context, "widget_connect_button"));
        views.setOnClickPendingIntent(R.id.widget_connect_button3, getPendingSelfIntent(context, "widget_connect_button"));

        views.setOnClickPendingIntent(R.id.power, getPendingSelfIntent(context, "power"));

        views.setOnClickPendingIntent(R.id.home2, getPendingSelfIntent(context, "open"));
        views.setOnClickPendingIntent(R.id.home, getPendingSelfIntent(context, "open"));

        if(showLoading){
            views.setViewVisibility(R.id.widget_connected, View.VISIBLE);
            addButtonActions(context, views, false);
        } else {
            addButtonActions(context, views, true);
            views.setViewVisibility(R.id.widget_connected, View.INVISIBLE);
        }

        if(showFailed){
            views.setInt(R.id.status_left, "setBackgroundColor", Color.RED);
            views.setViewVisibility(R.id.status_left, View.VISIBLE);

            views.setInt(R.id.status_middle, "setBackgroundColor", Color.RED);
            views.setViewVisibility(R.id.status_middle, View.VISIBLE);

            views.setInt(R.id.status_right, "setBackgroundColor", Color.RED);
            views.setViewVisibility(R.id.status_right, View.VISIBLE);
        }

        if(showSuccess){
            views.setInt(R.id.status_left, "setBackgroundColor", Color.GREEN);
            views.setViewVisibility(R.id.status_left, View.VISIBLE);

            views.setInt(R.id.status_middle, "setBackgroundColor", Color.GREEN);
            views.setViewVisibility(R.id.status_middle, View.VISIBLE);

            views.setInt(R.id.status_right, "setBackgroundColor", Color.GREEN);
            views.setViewVisibility(R.id.status_right, View.VISIBLE);

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    views.setViewVisibility(R.id.status_left, View.INVISIBLE);
                    views.setViewVisibility(R.id.status_middle, View.INVISIBLE);
                    views.setViewVisibility(R.id.status_right, View.INVISIBLE);
                    AppWidgetManager.getInstance(context).updateAppWidget(watchWidget, views);
                }
            }, 2000);
        }

        AppWidgetManager.getInstance(context).updateAppWidget(watchWidget, views);
    }

    public RemoteViews addButtonActions(Context context, RemoteViews views, Boolean enableAll){
        // top
        views.setOnClickPendingIntent(R.id.power, (enableAll) ? getPendingSelfIntent(context, "power") : null);

        views.setOnClickPendingIntent(R.id.nexus, (enableAll) ? getPendingSelfIntent(context, "nexus") : null);
        views.setOnClickPendingIntent(R.id.view, (enableAll) ? getPendingSelfIntent(context, "view") : null);

        // nav
        views.setOnClickPendingIntent(R.id.dpadRight, (enableAll) ? getPendingSelfIntent(context, "dpadRight") : null);
        views.setOnClickPendingIntent(R.id.dpadLeft, (enableAll) ? getPendingSelfIntent(context, "dpadLeft") : null);
        views.setOnClickPendingIntent(R.id.dpadUp, (enableAll) ? getPendingSelfIntent(context, "dpadUp") : null);
        views.setOnClickPendingIntent(R.id.dpadDown, (enableAll) ? getPendingSelfIntent(context, "dpadDown") : null);
        views.setOnClickPendingIntent(R.id.a, (enableAll) ? getPendingSelfIntent(context, "a") : null);

        // middle
        views.setOnClickPendingIntent(R.id.b, (enableAll) ? getPendingSelfIntent(context, "b") : null);
        views.setOnClickPendingIntent(R.id.menu, (enableAll) ? getPendingSelfIntent(context, "menu") : null);

        // middle bottom
        views.setOnClickPendingIntent(R.id.x, (enableAll) ?  getPendingSelfIntent(context, "x") : null);
        views.setOnClickPendingIntent(R.id.x2, (enableAll) ? getPendingSelfIntent(context, "x") : null);

        views.setOnClickPendingIntent(R.id.y, (enableAll) ? getPendingSelfIntent(context, "y") : null);
        views.setOnClickPendingIntent(R.id.y2, (enableAll) ?  getPendingSelfIntent(context, "y") : null);
        return views;
    }

    PendingIntent getPendingSelfIntent(Context context, String id) {
        Intent intent = new Intent(context, getClass());
        intent.setAction(id);
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);
    }

    public void sendXboxCommand(Intent intent, Context context){
        if( intent.getAction() != null) {
            Log.e("here", "Receive value: " + intent.getAction());

            if (intent.getAction().equals("widget_connect_button")) { ;
                retryConnect(5, context.getApplicationContext());
            } else if(intent.getAction().equals("open")) {
                Intent i = new Intent(context, MainActivity.class);
                i.setFlags(i.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            } else if(!TextUtils.isEmpty(intent.getAction()) && !intent.getAction().equals("android.appwidget.action.APPWIDGET_UPDATE_OPTIONS")) {
                sendBroadcast(context, intent.getAction());

                // send vibration
                Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                AudioAttributes audioAttributes = new AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .build();
                v.vibrate(40, audioAttributes);
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

        sendXboxCommand(intent, context);
    }

    public void sendBroadcast(Context context, String action){
        Intent intent = new Intent();
        intent.setAction("buttonPress");
        intent.putExtra("buttonValue", action);
        context.sendBroadcast(intent);
    }

    private void retryConnect(int retriesLeft, Context context) {
        // show error after retrying
        if(retriesLeft <= 0){
            Log.e("HERE", "Error connecting, out of retries!!!!");
            Toast.makeText(context.getApplicationContext(), "Cannot connect to Xbox!", Toast.LENGTH_SHORT).show();
            updateWidgetViews(context, false, true, false);
            return;
        }

        if(TextUtils.isEmpty(getLiveId(context))){
            Log.e("HERE","Cant use widget until initial connect");
            //return;
        }


        if(RewardedAdLoader.shouldShowAd(context.getApplicationContext())){
            Toast.makeText(context.getApplicationContext(), "View ad to continue", Toast.LENGTH_SHORT).show();

            Intent i = new Intent(context, MainActivity.class);
            i.setFlags(i.FLAG_ACTIVITY_NEW_TASK);
            i.putExtra("widgetShowAd", true);
            context.startActivity(i);

            return;
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
            updateWidgetViews(context, false, false, true);
            return true;
        }

        updateWidgetViews(context, true, true, false);

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