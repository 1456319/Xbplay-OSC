package com.studio08.xbgamestream.Controller;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

public class LocalService extends Service {
    static class VibrationEvent {
        long duration = 0;
        int amp = 0;
    }
    Vibrator vb;
    boolean running = false;
    VibrationEvent activeVibrationEvent;
    VibrationEvent lastSentVibEvent;

    // Binder given to clients.
    private final IBinder binder = new LocalBinder();

    public class LocalBinder extends Binder {
        LocalService  getService() {
            // Return this instance of LocalService so clients can call public methods.
            return LocalService.this;
        }
    }

    public void startRumbleLoop(){
        this.running = true;

        new Thread(new Runnable() {
            @Override
            public void run() {
                while (running){
                    try {
                        if(activeVibrationEvent == null){
                            // ignore
                        } else if (activeVibrationEvent.amp <= 0 || activeVibrationEvent.duration <= 0){
                            vb.cancel();
                            activeVibrationEvent = null;
                            Thread.sleep(100);
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            int amplitude = activeVibrationEvent.amp;
                            long dur = activeVibrationEvent.duration;
                            if (amplitude <= 0 || dur <= 0){
                                return;
                            }

                            Log.d("VIB", "Sending Vibration: " + amplitude + " | " + activeVibrationEvent.duration);
                            VibrationEffect effect = VibrationEffect.createOneShot(dur, amplitude);
                            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_GAME)
                                    .build();
                            vb.vibrate(effect, audioAttributes);
                            activeVibrationEvent =  null;
                            Thread.sleep(200);
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    @Override
    public void onCreate() {
        Log.e("LocalService", "onCreate");
        this.vb = (Vibrator) this.getSystemService(Context.VIBRATOR_SERVICE);

        super.onCreate();
    }

    @Override
    public void onRebind(Intent intent) {
        Log.e("LocalService", "onRebind");
        startRumbleLoop();
        super.onRebind(intent);
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.e("LocalService", "onUnbind");
        running = false;
        return super.onUnbind(intent);
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.e("LocalService", "onBind");
        startRumbleLoop();
        return binder;
    }

    public void sendPatter(long duration, int simulatedAmplitude){
        VibrationEvent event = new VibrationEvent();
        event.amp = simulatedAmplitude / 3;
        event.duration = duration;
        this.activeVibrationEvent = event;
    }
}

