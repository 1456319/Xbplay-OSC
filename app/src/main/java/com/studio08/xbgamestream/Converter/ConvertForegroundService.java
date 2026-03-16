package com.studio08.xbgamestream.Converter;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.ScreenCastActivity;

import org.json.JSONObject;

import java.util.ArrayList;

public class ConvertForegroundService extends Service implements VideoFormatConverter.VideoConvertEvents {
    public static final String CHANNEL_ID = "ForegroundServiceChannel";
    VideoFormatConverter converter;
    public String filePath;
    Notification notification;
    NotificationManager manager;
    /** Keeps track of all current registered clients. */
    ArrayList<Messenger> mClients = new ArrayList<Messenger>();
    /** Holds last value set by a client. */
    boolean isRunning = true;

    public static final int MSG_REGISTER_CLIENT = 1;
    public static final int MSG_UNREGISTER_CLIENT = 2;
    public static final int MSG_UPDATE_STATS = 3;
    public static final int MSG_CONVERT_COMPLETE = 4;
    public static final int MSG_CONVERT_FAILED = 5;
    public static final int MSG_STOP_SERVICE = 6;

    public int NOTIFICATION_ID = 4;
    public int NOTIFICATION_STATUS_ID = 5;

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_REGISTER_CLIENT:
                    mClients.add(msg.replyTo);
                    break;
                case MSG_UNREGISTER_CLIENT:
                    mClients.remove(msg.replyTo);
                    break;
                case MSG_STOP_SERVICE:
                    isRunning = false;
                    Log.w("HEREHERE", "STOPPING SERVICE from MESSAGE");
                    if (converter != null) converter.cancel();
                    stopForeground(true);
                    stopSelf();
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    /**
     * Target we publish for clients to send messages to IncomingHandler.
     */
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private void sendStatsMessage(String statsText) {
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                JSONObject object = new JSONObject();
                object.put("message", statsText);
                mClients.get(i).send(Message.obtain(null, MSG_UPDATE_STATS, object));
            } catch (RemoteException e) {
                mClients.remove(i);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void sendCompleteMessage(String path) {
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                JSONObject object = new JSONObject();
                object.put("message", path);
                mClients.get(i).send(Message.obtain(null, MSG_CONVERT_COMPLETE, object));
            } catch (RemoteException e) {
                mClients.remove(i);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    private void sendFailedMessage(String message) {
        for (int i=mClients.size()-1; i>=0; i--) {
            try {
                JSONObject object = new JSONObject();
                object.put("message", message);
                mClients.get(i).send(Message.obtain(null, MSG_CONVERT_FAILED, object));
            } catch (RemoteException e) {
                mClients.remove(i);
                e.printStackTrace();
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        filePath = intent.getStringExtra("filePath");

        createNotificationChannel();
        Intent notificationIntent = new Intent(this, ScreenCastActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);

        notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("xbPlay Video Convert Started")
                .setContentText("Converting video...")
                .setSmallIcon(R.drawable.app_notification_icon)
                .setContentIntent(pendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
        //do heavy work on a background thread
        convertVideo();
        return START_NOT_STICKY;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
    }
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Foreground Service Channel",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void convertVideo() {
        converter = new VideoFormatConverter(filePath, getApplicationContext());
        converter.setCustomListener(ConvertForegroundService.this);
        converter.runFFMpegConvert();
    }

    @Override
    public void onVideoConvertSuccess(String path) {
        sendCompleteMessage(path);

        Intent notificationIntent = new Intent(this, ScreenCastActivity.class);
        notificationIntent.putExtra("outputPath", path);
        notificationIntent.putExtra("completed", true);
        notificationIntent.putExtra("showCastView", true); // this will load cast view instead of remote
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("xbPlay Video Convert Completed!")
                .setContentText("Saved at:"+ path)
                .setSmallIcon(R.drawable.app_notification_icon)
                .setContentIntent(pendingIntent)
                .build();
        manager.notify(NOTIFICATION_STATUS_ID, notification);
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onVideoConvertFailed(String message) {
        Log.e("HERE", "ConvertForegroundService caught video convert failure");
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // need a delay or sometimes doesnt work!
                sendFailedMessage(message);
            }
        }, 500);

        if (isRunning) { // only trigger error notification if not from user
            Intent notificationIntent = new Intent(this, ScreenCastActivity.class);
            notificationIntent.putExtra("convertError", message); // this is the converted file path not the input path
            notificationIntent.putExtra("showCastView", true); // this will load cast view instead of remote
            notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("xbPlay Video Convert Failed!")
                    .setContentText("Error: " + message)
                    .setSmallIcon(R.drawable.app_notification_icon)
                    .setContentIntent(pendingIntent)
                    .build();
            manager.notify(NOTIFICATION_STATUS_ID, notification);
        }
        stopForeground(true);
        stopSelf();
    }
}
