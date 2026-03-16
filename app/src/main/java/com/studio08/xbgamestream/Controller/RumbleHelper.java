package com.studio08.xbgamestream.Controller;

import static com.studio08.xbgamestream.Helpers.Helper.getDeviceName;
import static com.studio08.xbgamestream.Helpers.Helper.getRumbleIntensity;

import android.annotation.TargetApi;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.os.Binder;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.IBinder;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.InputDevice;
import android.widget.Toast;

import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.Widgets.RemoteWidgetProvider;

import org.cgutman.shieldcontrollerextensions.SceManager;
import org.json.JSONObject;

import java.util.ArrayList;

import network.BindService;

public class RumbleHelper {
    private Context context;
    ArrayList<InputDeviceContext> deviceList = new ArrayList<InputDeviceContext>();
    private SceManager sceManager;
    private Vibrator deviceVibrator;
    private boolean shouldRumbleDevice = true;
    private LocalService mService;
    private boolean isGCloudDevice = false;

    public RumbleHelper(Context context) {
        Log.e("Rumble", "New Rumble Helper Construction");
        this.context = context;
        this.init();
    }

    public void destroy() {
        Log.e("Rumble", "Cleaning up vibrator");
        for (int i = 0; i < deviceList.size(); i++) {
            try {
                deviceList.get(i).destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        deviceVibrator.cancel();
        sceManager.stop();

        // custom logic for handling gCloud
        if (isGCloudDevice && mService != null){
            context.unbindService(localLogitechServiceConnection);
        }
    }

    public InputDeviceContext addDevice(InputDeviceContext ctx, InputDevice dev){
        Log.e("RumbleHelper", "Add Device" + dev);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hasDualAmplitudeControlledRumbleVibrators(dev.getVibratorManager())) {
            ctx.vibratorManager = dev.getVibratorManager();
            deviceList.add(ctx);
        }
        else if (dev.getVibrator().hasVibrator()) {
            ctx.vibrator = dev.getVibrator();
            deviceList.add(ctx);
        } else {
            Log.e("RumbleHelper", "Device has no vibration manager");
        }
        return ctx;
    }


    private void init() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            this.shouldRumbleDevice = prefs.getBoolean("rumble_device_key", true);
            this.sceManager = new SceManager(this.context);
            this.sceManager.start();
            this.deviceVibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

            // custom logic for handling gcloud
            String deviceName = getDeviceName();
            Log.e("DeviceName", "Device name: " + deviceName);
            if (deviceName.contains("Logitech_GR")){
                this.isGCloudDevice = true;
                Log.e("LocalService", "Starting Logitech Bind");
                Intent intent = new Intent(context, LocalService.class);
                context.bindService(intent, localLogitechServiceConnection, Context.BIND_AUTO_CREATE);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void handleRumble(String rumbleJson) {
        Log.d("Rumble", "Starting rumble: " + rumbleJson);

        short lowFreqMotor;
        short highFreqMotor;
        long duration = 0;
        try {
            JSONObject data = new JSONObject(rumbleJson);
            Log.d("Rumble", data.toString());
            duration = data.getLong("duration");
            double weakMagnitude = data.getDouble("weakMagnitude");
            double strongMagnitude = data.getDouble("strongMagnitude");

            lowFreqMotor = (short) (Math.min(32767, weakMagnitude * 32767 * getRumbleIntensity(context)));
            highFreqMotor = (short) (Math.min(32757, strongMagnitude * 32767 * getRumbleIntensity(context)));
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        boolean vibrated = false;

        for (int i = 0; i < deviceList.size(); i++) {
            Log.e("Rumble", "Found a connected gamepad to rumble");

            InputDeviceContext deviceContext = deviceList.get(i);

            // Prefer the documented Android 12 rumble API which can handle dual vibrators on PS/Xbox controllers
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && deviceContext.vibratorManager != null) {
                Log.e("Rumble", "Gamepad has dual rumble vibrator!");

                vibrated = true;
                rumbleDualVibrators(deviceContext.vibratorManager, lowFreqMotor, highFreqMotor);
            }
            // On Shield devices, we can use their special API to rumble Shield controllers
            else if (sceManager.rumble(deviceContext.inputDevice, lowFreqMotor, highFreqMotor)) {
                Log.e("Rumble", "Gamepad is shield device!");
                vibrated = true;
            }
            // If all else fails, we have to try the old Vibrator API
            else if (deviceContext.vibrator != null) {
                Log.e("Rumble", "Gamepad only has old vibrator api");
                vibrated = true;
                rumbleSingleVibrator(deviceContext.vibrator, lowFreqMotor, highFreqMotor, duration);
            }
        }

        if (!vibrated) {
            Log.i("Rumble", "Couldn't rumble gamepad, trying to rumble device: " + shouldRumbleDevice);

            if (shouldRumbleDevice) {
                // We found a device to vibrate but it didn't have rumble support. The user
                // has requested us to vibrate the device in this case.
                rumbleSingleVibrator(deviceVibrator, lowFreqMotor, highFreqMotor, duration);
            }
        }
    }


    @TargetApi(31)
    public static boolean hasDualAmplitudeControlledRumbleVibrators(VibratorManager vm) {
        int[] vibratorIds = vm.getVibratorIds();

        // There must be exactly 2 vibrators on this device
        if (vibratorIds.length != 2) {
            return false;
        }

        // Both vibrators must have amplitude control
        for (int vid : vibratorIds) {
            if (!vm.getVibrator(vid).hasAmplitudeControl()) {
                return false;
            }
        }
        return true;
    }

    // This must only be called if hasDualAmplitudeControlledRumbleVibrators() is true!
    @TargetApi(31)
    private void rumbleDualVibrators(VibratorManager vm, short lowFreqMotor, short highFreqMotor) {
        // Normalize motor values to 0-255 amplitudes for VibrationManager
        highFreqMotor = (short) ((highFreqMotor >> 8) & 0xFF);
        lowFreqMotor = (short) ((lowFreqMotor >> 8) & 0xFF);

        // If they're both zero, we can just call cancel().
        if (lowFreqMotor == 0 && highFreqMotor == 0) {
            vm.cancel();
            return;
        }

        // There's no documentation that states that vibrators for FF_RUMBLE input devices will
        // always be enumerated in this order, but it seems consistent between Xbox Series X (USB),
        // PS3 (USB), and PS4 (USB+BT) controllers on Android 12 Beta 3.
        int[] vibratorIds = vm.getVibratorIds();
        int[] vibratorAmplitudes = new int[]{highFreqMotor, lowFreqMotor};

        CombinedVibration.ParallelCombination combo = CombinedVibration.startParallel();

        for (int i = 0; i < vibratorIds.length; i++) {
            // It's illegal to create a VibrationEffect with an amplitude of 0.
            // Simply excluding that vibrator from our ParallelCombination will turn it off.
            if (vibratorAmplitudes[i] != 0) {
                combo.addVibrator(vibratorIds[i], VibrationEffect.createOneShot(60000, vibratorAmplitudes[i]));
            }
        }

        VibrationAttributes.Builder vibrationAttributes = new VibrationAttributes.Builder();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrationAttributes.setUsage(VibrationAttributes.USAGE_MEDIA);
        }

        vm.vibrate(combo.combine(), vibrationAttributes.build());
    }

    private void rumbleSingleVibrator(Vibrator vibrator, short lowFreqMotor, short highFreqMotor, long duration) {
        Log.d("Rumble", "low " + lowFreqMotor + " high: " + highFreqMotor + " Dur: " + duration);
        // Since we can only use a single amplitude value, compute the desired amplitude
        // by taking 80% of the big motor and 33% of the small motor, then capping to 255.
        // NB: This value is now 0-255 as required by VibrationEffect.
        short lowFreqMotorMSB = (short) ((lowFreqMotor >> 8) & 0xFF);
        short highFreqMotorMSB = (short) ((highFreqMotor >> 8) & 0xFF);
        int simulatedAmplitude = Math.min(255, (int) ((lowFreqMotorMSB * 0.80) + (highFreqMotorMSB * 0.33)));

        Log.d("Rumble", "simulatedAmplitude: " + simulatedAmplitude + "/255");

        if (simulatedAmplitude == 0) {
            // This case is easy - just cancel the current effect and get out.
            // NB: We cannot simply check lowFreqMotor == highFreqMotor == 0
            // because our simulatedAmplitude could be 0 even though our inputs
            // are not (ex: lowFreqMotor == 0 && highFreqMotor == 1).
            vibrator.cancel();

            // custom logic for gcloud
            if (this.isGCloudDevice && this.mService != null){
                this.mService.sendPatter(0, 0);
            }
            return;
        }
        vibrator.cancel();

        // Attempt to use amplitude-based control if we're on Oreo and the device
        // supports amplitude-based vibration control.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vibrator.hasAmplitudeControl()) {
                VibrationEffect effect = VibrationEffect.createOneShot(duration, simulatedAmplitude);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    VibrationAttributes vibrationAttributes = new VibrationAttributes.Builder()
                            .setUsage(VibrationAttributes.USAGE_MEDIA)
                            .build();
                    vibrator.vibrate(effect, vibrationAttributes);
                    Log.d("Rumble", "HERE6");
                    return;
                }
                // for some reason this isnt working for me on the pixel. Disable..
//                else {
//                    AudioAttributes audioAttributes = new AudioAttributes.Builder()
//                            .setUsage(AudioAttributes.USAGE_GAME)
//                            .build();
//                    vibrator.vibrate(effect, audioAttributes);
//                    Log.e("Rumble", "HERE5");
//
//                }
//                Log.e("Rumble", "HERE2");
//
//                return;
            }
        }

        // If we reach this point, we don't have amplitude controls available, so
        // we must emulate it by PWMing the vibration. Ick.
        long pwmPeriod = 20;
        long onTime = (long) ((simulatedAmplitude / 255.0) * pwmPeriod);
        long offTime = pwmPeriod - onTime;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            VibrationAttributes vibrationAttributes = new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_MEDIA)
                    .build();
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, onTime, offTime}, 0), vibrationAttributes);
            Log.d("Rumble", "HERE4");

        } else if (this.isGCloudDevice && this.mService != null) {
            Log.d("Rumble", "HERE3 GCloud");
            this.mService.sendPatter(duration, simulatedAmplitude);
        } else {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .build();
            vibrator.vibrate(new long[]{0, onTime, offTime}, 0, audioAttributes);
            Log.d("Rumble", "HERE3");
        }
    }

    private ServiceConnection localLogitechServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e("LocalService", "Logitech Bind service disconnected");
            mService = null;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.e("LocalService", "Logitech Bind service connected");

            LocalService.LocalBinder binder = (LocalService.LocalBinder) service;
            mService = binder.getService();
        }
    };
}
