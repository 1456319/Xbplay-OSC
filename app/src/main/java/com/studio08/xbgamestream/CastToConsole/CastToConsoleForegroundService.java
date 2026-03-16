package com.studio08.xbgamestream.CastToConsole;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.connectsdk.core.MediaInfo;
import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.command.ServiceCommandError;
import com.studio08.xbgamestream.Helpers.FileHelper;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.PWAMainMenuActivity;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.ScreenCastActivity;
import com.studio08.xbgamestream.Servers.FileServer;
import com.studio08.xbgamestream.Servers.Xbox360FileServer;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class CastToConsoleForegroundService extends Service implements ConnectableDeviceListener {
    boolean AUDIO_CAST_MODE = false;
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    Notification notification;
    NotificationManager manager;
    private IBinder mBinder = new MyBinder();
    public int NOTIFICATION_ID = 234;

    FileServer fileServer;
    Xbox360FileServer xbox360FileServer;
    public String [] filePaths;

    private static ConnectableDevice mDevice;
    MediaControl mMediaControl;

    private int currentPlayIndex = 0;
    private boolean playingCooldown = false;
    private boolean startedPlaying = false;

    CastServiceListener castServiceListener;

    public interface CastServiceListener {
        void onMediaControlCreated(MediaControl mediaControl);
        void onCastTitleUpdated();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    // listen for duration changes
    public MediaControl.PlayStateListener playStateListener = new MediaControl.PlayStateListener() {

        @Override
        public void onError(ServiceCommandError error) {
            Log.d("HERE", "Playstate Listener error = " + error);
        }

        @Override
        public void onSuccess(MediaControl.PlayStateStatus playState) {
            Log.d("HERE", "Playstate changed | playState = " + playState);

            switch (playState) {
                case Playing:
                    startedPlaying = true;
                    break;
                case Finished:
                    playNext();
                    startedPlaying = false;
                    break;
            }
        }
    };

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        boolean performedClick = false;
        if (intent != null && intent.getAction() != null ) {
            if (intent.getAction().equals(Constants.ACTION.EXIT_ACTION)) {
                Log.e("HERE", "EXIT_ACTION");
                performedClick = true;
                stopForeground(true);
                stopSelf();
            } else if (intent.getAction().equals (Constants.ACTION.NEXT_ACTION)) {
                Log.e("HERE", "NEXT_ACTION");
                performedClick = true;
                startedPlaying = true;
                playNext();
            } else if (intent.getAction().equals (Constants.ACTION.PREV_ACTION)) {
                Log.e("HERE", "PREV_ACTION");
                performedClick = true;
                startedPlaying = true;
                playPrevious();
            }
        }

//        // if clicking a button exit after click
        if (performedClick || notification != null) {
            return START_NOT_STICKY;
        }
        beginListeningForPlayback();
        return START_NOT_STICKY;
    }

    public void startCastingFirstVideo(){
        Log.e("HERE", "startCastingFirstVideo");
        currentPlayIndex = 0;

        String currentVideoFile = getCurrentVideoFile();
        if(currentVideoFile != null){
            startFileServer(currentVideoFile);
            updateNotification(FileHelper.getFileNameFromPath(currentVideoFile) + " (" + (currentPlayIndex + 1) + "/" + filePaths.length + ")", "Swipe to expand");
            performCast(FileHelper.getFileNameFromPath(currentVideoFile));
        }
    }

    private Class getNotificationClickIntent(){
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean useLegacyTheme = prefs.getBoolean("pwa_use_legacy_theme_key", false);
        return useLegacyTheme ? ScreenCastActivity.class : PWAMainMenuActivity.class;
    }
    // called when activity casts the first video
    public void beginListeningForPlayback(){
        createNotificationChannel();
        Intent notificationIntent = new Intent(this, getNotificationClickIntent());
        notificationIntent.putExtra("audioCastType", AUDIO_CAST_MODE);
        notificationIntent.putExtra("showCastView", true);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("xbPlay - Cast To Console")
                .setContentText("Waiting for user to cast...")
                .setSmallIcon(R.drawable.app_notification_icon)
                .setContentIntent(pendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        if(filePaths != null && getCurrentVideoFile() != null) {
            updateNotification(FileHelper.getFileNameFromPath(getCurrentVideoFile()) + " (" + (currentPlayIndex + 1) + "/" + filePaths.length + ")", "Swipe to expand");
        }

    }

    private String getNextVideoFile(){
        if(filePaths != null) {
            if(currentPlayIndex < filePaths.length - 1){
                currentPlayIndex++;
                return filePaths[currentPlayIndex];
            } else {
                Log.d("HERE", "On end of playlist!");
            }
        } else {
            Log.d("HERE", "Tried to call play video before paths set!");
        }
        return null;
    }
    private String getPreviousVideoFile(){
        if(filePaths != null) {
            if(currentPlayIndex > 0){
                currentPlayIndex--;
                return filePaths[currentPlayIndex];
            } else {
                Log.d("HERE", "On start of playlist!");
            }
        } else {
            Log.d("HERE", "Tried to call play video before paths set!");
        }
        return null;
    }
    public String getCurrentVideoFile(){
        if(filePaths != null) {
            if(currentPlayIndex <= filePaths.length - 1 && currentPlayIndex >= 0){
                return filePaths[currentPlayIndex];
            } else {
                Log.d("HERE", "Cant get currently playing track!");
            }
        } else {
            Log.d("HERE", "Tried to call get video before paths set!");
        }
        return null;
    }
    private boolean setCooldownLock(){
        if(!startedPlaying){
            Log.e("HERE", "Tried to call setCooldownLock before video playing. Ignoring");
            return false;
        }
        if(playingCooldown){ // something else has lock, exit
            Log.e("HERE", "Not playing next video due to cooldown. Ignoring");
            return false;
        }

        // set lock
        playingCooldown = true;

        // release log in 2 seconds
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                playingCooldown = false;
            }
        }, 2000);
        return true;
    }
    public void playNext() {
        Log.e("HERE", "playNext");

        if(!setCooldownLock()){
            return;
        }

        String nextVideo = getNextVideoFile();
        if(nextVideo != null){
            startFileServer(nextVideo);
            updateNotification(FileHelper.getFileNameFromPath(nextVideo) + " (" + (currentPlayIndex + 1) + "/" + filePaths.length + ")", "Swipe to expand");
            performCast(FileHelper.getFileNameFromPath(nextVideo));
        }
    }
    public void playPrevious() {
        Log.e("HERE", "playPrevious");

        if(!setCooldownLock()){
            return;
        }

        String prevVideo = getPreviousVideoFile();
        if(prevVideo != null){
            startFileServer(prevVideo);
            updateNotification(FileHelper.getFileNameFromPath(prevVideo) + " (" + (currentPlayIndex + 1) + "/" + filePaths.length + ")", "Swipe to expand");
            performCast(FileHelper.getFileNameFromPath(prevVideo));
        }
    }

    // public update endpoints
    public void setCastServiceListener(CastServiceListener listener){
        this.castServiceListener = listener;
    }
    public void setIndex(int index){
        Log.e("HERE", "setIndex");
        currentPlayIndex = index;
    }
    public void setDevice(ConnectableDevice device){
        Log.e("HERE", "setDevice");
        mDevice = device;
    }
    public void setMediaControl(MediaControl mediaControl){
        Log.e("HERE", "setMediaControl");
        mMediaControl = mediaControl;
        mMediaControl.subscribePlayState(playStateListener);
    }
    public void setFilePaths(String [] filePathsInput, boolean isAudio){
        Log.e("HERE", "setFilePaths: " + filePathsInput.length + (filePathsInput.length > 0 ? filePathsInput[0] : "no_media"));
        filePaths = filePathsInput;
        currentPlayIndex = 0;
        AUDIO_CAST_MODE = isAudio;
    }
    public MediaControl getMediaControl() {
        return mMediaControl;
    }
    public ConnectableDevice getDevice() {
        return mDevice;
    }
    public String [] getFilePaths(){
        if(filePaths != null && filePaths.length <= 0){
            return null;
        }
        return filePaths;
    }
    public int getCurrentPlayIndex() {
        return currentPlayIndex;
    }
    // file server
    private void startFileServer(String path){
        // create xbox one server
        if(fileServer != null && fileServer.isAlive()) { // update existing filepath
            fileServer.setUrl(path);
        } else { // start new server
            fileServer = new FileServer(path, (!AUDIO_CAST_MODE) ? "video" : "audio", getApplicationContext());
            try {
                fileServer.start();
            } catch(IOException e){
                Toast.makeText(getApplicationContext(), "Error creating local file server!" + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
            }
        }
        // create xbox 360 server
        if(xbox360FileServer != null && xbox360FileServer.isRunning()){
            xbox360FileServer.stop();
        }
        xbox360FileServer = new Xbox360FileServer(new File(path));
        xbox360FileServer.init((!AUDIO_CAST_MODE) ? "video/mp4" : "audio/*");
        xbox360FileServer.start();
    }
    private String getServerUrl() {
        // handle xbox 360 server
        if(mDevice != null && mDevice.isConnected() && mDevice.getFriendlyName().contains("360")){
            if (xbox360FileServer != null & xbox360FileServer.isRunning()) {
                return xbox360FileServer.getFileUrl();
            } else {
                Toast.makeText(getApplicationContext(), "Error sending file. Restart app", Toast.LENGTH_SHORT).show();
            }
        } else if(fileServer != null && fileServer.isAlive()) { // handle xbox one server
            return "http://" + Helper.getLocalIpAddress() + ":" + fileServer.getListeningPort();
        }
        return "No file selected";
    }

    public void updateNotification(String title, String text) {
        Intent notificationIntent = new Intent(this, getNotificationClickIntent());

        notificationIntent.putExtra("audioCastType", AUDIO_CAST_MODE);
        notificationIntent.putExtra("showCastView", true);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        //Intent for Next
        Intent nextIntent = new Intent(this, CastToConsoleForegroundService.class);
        nextIntent.setAction(Constants.ACTION.NEXT_ACTION);
        PendingIntent pnextIntent = PendingIntent.getService(this, 0, nextIntent, PendingIntent.FLAG_IMMUTABLE);

        //Intent for Prev
        Intent prevIntent = new Intent(this, CastToConsoleForegroundService.class);
        prevIntent.setAction(Constants.ACTION.PREV_ACTION);
        PendingIntent pprevIntent = PendingIntent.getService(this, 0, prevIntent, PendingIntent.FLAG_IMMUTABLE);

        //Intent for Exit
        Intent exitIntent = new Intent(this, CastToConsoleForegroundService.class);
        exitIntent.setAction(Constants.ACTION.EXIT_ACTION);
        PendingIntent pexitIntent = PendingIntent.getService(this, 0, exitIntent, PendingIntent.FLAG_IMMUTABLE);

        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.app_notification_icon)
                .setContentIntent(pendingIntent)
                .setContentTitle(title)
                .setContentText(text)
                .addAction(android.R.drawable.ic_media_previous , "PREVIOUS", pprevIntent)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel , "EXIT", pexitIntent)
                .addAction(android.R.drawable.ic_media_next , "NEXT", pnextIntent)
                .build();
        manager.notify(NOTIFICATION_ID, notification);
    }
    private void performCast(String titleText) {
        String iconURL = "https://d1o4538xtdh4nmxb2.cloudfront.net/xb_gamestream_icon.png";
        String title = "xbPlay - " + titleText;
        String description = "Casting from xbPlay";
        String mimeType = ((!AUDIO_CAST_MODE) ) ? "video/*" : "audio/*";

        String url = getServerUrl();
        Log.e("HERE", "Casting: " + url);
        MediaInfo mediaInfo = new MediaInfo.Builder(url, mimeType)
                .setTitle(title)
                .setDescription(description)
                .setIcon(iconURL)
                .build();

        MediaPlayer.LaunchListener listener = new MediaPlayer.LaunchListener() {
            @Override
            public void onSuccess(MediaPlayer.MediaLaunchObject object) {
//                mMediaControl = object.mediaControl;
//                mMediaControl.subscribePlayState(playStateListener);
                setMediaControl(object.mediaControl);

                if(castServiceListener != null){
                    castServiceListener.onMediaControlCreated(object.mediaControl);
                }
            }

            @Override
            public void onError(ServiceCommandError error) {
                Log.d("App Tag", "Play media failure: " + error);
            }
        };

        try {
            mDevice.getMediaPlayer().playMedia(mediaInfo, false, listener);
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Error Casting. Please restart", Toast.LENGTH_SHORT).show();
        }

        if(castServiceListener != null) {
            castServiceListener.onCastTitleUpdated();
        }
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "CastForegroundServiceChannel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    public void stopServiceCommand(){
        stopSelf();
        stopForeground(true);
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onDeviceReady(ConnectableDevice device) {
        mDevice = device;
        mMediaControl = null;
    }

    @Override
    public void onDeviceDisconnected(ConnectableDevice device) {
        mDevice = null;

    }

    @Override
    public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType) {

    }

    @Override
    public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed) {

    }

    @Override
    public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error) {

    }
    public class MyBinder extends Binder {
        //Return object of BoundService class which can be used to access all the public methods of this class
        public CastToConsoleForegroundService getService() {
            return CastToConsoleForegroundService.this;
        }
    }
    public static class Constants {
        public interface ACTION {
            public static String PREV_ACTION = "action.prev";
            public static String EXIT_ACTION = "action.exit";
            public static String NEXT_ACTION = "action.next";
        }
    }
}
