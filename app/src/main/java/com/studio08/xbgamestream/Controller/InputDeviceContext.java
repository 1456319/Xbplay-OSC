package com.studio08.xbgamestream.Controller;

import android.os.Build;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.view.InputDevice;

public class InputDeviceContext {
    public int vendorId;
    public int productId;
    public String name;
    public int id;
    public VibratorManager vibratorManager;
    public Vibrator vibrator;
    public InputDevice inputDevice;
    public boolean external;

    public int leftStickXAxis = -1;
    public int leftStickYAxis = -1;

    public int rightStickXAxis = -1;
    public int rightStickYAxis = -1;

    public int leftTriggerAxis = -1;
    public int rightTriggerAxis = -1;
    public boolean triggersIdleNegative;
    public boolean leftTriggerAxisUsed, rightTriggerAxisUsed;

    public int hatXAxis = -1;
    public int hatYAxis = -1;
    public boolean hatXAxisUsed, hatYAxisUsed;

    public boolean isNonStandardDualShock4;
    public boolean usesLinuxGamepadStandardFaceButtons;
    public boolean isNonStandardXboxBtController;
    public boolean isServal;
    public boolean backIsStart;
    public boolean modeIsSelect;
    public boolean searchIsMode;
    public boolean ignoreBack;
    public boolean hasJoystickAxes;
    public boolean hasSelect;
    public boolean hasMode;
    public long startDownTime = 0;

    public short inputMap = 0x0000;
    public byte leftTrigger = 0x00;
    public byte rightTrigger = 0x00;
    public short rightStickX = 0x0000;
    public short rightStickY = 0x0000;
    public short leftStickX = 0x0000;
    public short leftStickY = 0x0000;

    InputDeviceContext(InputDevice device){
        this.inputDevice = device;
        this.vendorId = device.getVendorId();
        this.productId = device.getProductId();
        setVibration();
    }
    public void setVibration(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && RumbleHelper.hasDualAmplitudeControlledRumbleVibrators(inputDevice.getVibratorManager())) {
            vibratorManager = inputDevice.getVibratorManager();
        }
        else if (inputDevice.getVibrator().hasVibrator()) {
            vibrator = inputDevice.getVibrator();
        }
    }
    public void destroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && vibratorManager != null) {
            vibratorManager.cancel();
        }
        else if (vibrator != null) {
            vibrator.cancel();
        }
    }
}
