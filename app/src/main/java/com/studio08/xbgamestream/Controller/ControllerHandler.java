package com.studio08.xbgamestream.Controller;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.input.InputManager;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.CombinedVibration;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.InputDevice;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

import com.studio08.xbgamestream.Helpers.SettingsFragment;

import org.cgutman.shieldcontrollerextensions.SceManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;

public class ControllerHandler implements InputManager.InputDeviceListener, View.OnKeyListener, View.OnGenericMotionListener {
    private Context context;
    private final SparseArray<InputDeviceContext> inputDeviceContexts = new SparseArray<>();
    private boolean hasGameController;
    private final Vector2d inputVector = new Vector2d();
    RumbleHelper rumbleHelper;
    ControllerHandlerListener listener;
    private View sourceView;
    SharedPreferences prefs;
    String currentControllerName;
    String previousPayload = "";

    @Override
    public boolean onKey(View view, int i, KeyEvent keyEvent) {
        switch (keyEvent.getAction()) {
            case KeyEvent.ACTION_DOWN:
                return handleButtonDown(keyEvent);
            case KeyEvent.ACTION_UP:
                return handleButtonUp(keyEvent);
            default:
                return false;
        }
    }

    @Override
    public boolean onGenericMotion(View view, MotionEvent motionEvent) {
        return handleMotionEvent(motionEvent);
    }

    public interface ControllerHandlerListener {
        void controllerData(JSONObject data);
    }

    public ControllerHandler(Context context) {
        this.context = context;
        rumbleHelper = new RumbleHelper(context);
        prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        currentControllerName = prefs.getString("physical_controller_button_mappings", "");
    }

    public void destroy(){
        rumbleHelper.destroy();
    }

    public void handleRumble(String rumbleJson){
        rumbleHelper.handleRumble(rumbleJson);
    }

    public void setListener(ControllerHandlerListener listener){
        this.listener = listener;
    }

    // intercept all controller button clicks (geckoview)
    public void setSourceView(View v){
        sourceView = v;
        sourceView.setOnKeyListener(this);
        sourceView.setOnGenericMotionListener(this);
    }

    // used if you dont want to intercept button clicks, but u do want to listen for connected controllers to vibrate (system webview)
    public void setPassthroughView(View v){
        sourceView = v;
        sourceView.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View view, int i, KeyEvent keyEvent) {
                getContextForEvent(keyEvent);
                return false;
            }
        });
        sourceView.setOnGenericMotionListener(new View.OnGenericMotionListener() {
            @Override
            public boolean onGenericMotion(View view, MotionEvent motionEvent) {
                getContextForEvent(motionEvent);
                return false;
            }
        });
    }

    private static InputDevice.MotionRange getMotionRangeForJoystickAxis(InputDevice dev, int axis) {
        InputDevice.MotionRange range;

        // First get the axis for SOURCE_JOYSTICK
        range = dev.getMotionRange(axis, InputDevice.SOURCE_JOYSTICK);
        if (range == null) {
            // Now try the axis for SOURCE_GAMEPAD
            range = dev.getMotionRange(axis, InputDevice.SOURCE_GAMEPAD);
        }
        return range;
    }

    private static boolean hasGamepadButtons(InputDevice device) {
        return (device.getSources() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD;
    }

    private static boolean hasJoystickAxes(InputDevice device) {
        return (device.getSources() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK &&
                getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_X) != null &&
                getMotionRangeForJoystickAxis(device, MotionEvent.AXIS_Y) != null;
    }


    @Override
    public void onInputDeviceAdded(int deviceId) {
        Log.e("ControllerHandler", "Gamepad added");
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        Log.e("ControllerHandler", "Gamepad removed");
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        Log.e("ControllerHandler", "Gamepad changed");
    }


    public boolean handleMotionEvent(MotionEvent event) {
        InputDeviceContext context = getContextForEvent(event);
        if (context == null) {
            return true;
        }

        if ((event.getSource() & InputDevice.SOURCE_MOUSE) == InputDevice.SOURCE_MOUSE){
            return false;
        }

        float lsX = 0, lsY = 0, rsX = 0, rsY = 0, rt = 0, lt = 0, hatX = 0, hatY = 0;

        // We purposefully ignore the historical values in the motion event as it makes
        // the controller feel sluggish for some users.

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            lsX = event.getAxisValue(context.leftStickXAxis);
            lsY = event.getAxisValue(context.leftStickYAxis);
        }

        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            rsX = event.getAxisValue(context.rightStickXAxis);
            rsY = event.getAxisValue(context.rightStickYAxis);
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            lt = event.getAxisValue(context.leftTriggerAxis);
            rt = event.getAxisValue(context.rightTriggerAxis);
        }

        if (context.hatXAxis != -1 && context.hatYAxis != -1) {
            hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X);
            hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
        }

        handleAxisSet(context, lsX, lsY, rsX, rsY, lt, rt, hatX, hatY);

        return true;
    }

    private byte maxByMagnitude(byte a, byte b) {
        int absA = Math.abs(a);
        int absB = Math.abs(b);
        if (absA > absB) {
            return a;
        }
        else {
            return b;
        }
    }

    private short maxByMagnitude(short a, short b) {
        int absA = Math.abs(a);
        int absB = Math.abs(b);
        if (absA > absB) {
            return a;
        }
        else {
            return b;
        }
    }
    private void sendControllerInputPacket() {

        // Take the context's controller number and fuse all inputs with the same number
        short inputMap = 0;
        byte leftTrigger = 0;
        byte rightTrigger = 0;
        short leftStickX = 0;
        short leftStickY = 0;
        short rightStickX = 0;
        short rightStickY = 0;

        // In order to properly handle controllers that are split into multiple devices,
        // we must aggregate all controllers with the same controller number into a single
        // device before we send it.
        for (int i = 0; i < inputDeviceContexts.size(); i++) {
            InputDeviceContext context = inputDeviceContexts.valueAt(i);

            inputMap |= context.inputMap;
            leftTrigger |= maxByMagnitude(leftTrigger, context.leftTrigger);
            rightTrigger |= maxByMagnitude(rightTrigger, context.rightTrigger);
            leftStickX |= maxByMagnitude(leftStickX, context.leftStickX);
            leftStickY |= maxByMagnitude(leftStickY, context.leftStickY);
            rightStickX |= maxByMagnitude(rightStickX, context.rightStickX);
            rightStickY |= maxByMagnitude(rightStickY, context.rightStickY);
        }

//        Log.e("ControllerHelper",
//                "leftTrigger" + normalizeByte(leftTrigger)  + " rightTrigger: " + normalizeByte(rightTrigger) +
//                " leftStickX: " + (leftStickX) + " leftStickY: " + (leftStickY) +
//                " rightStickX: " + normalizeShort(rightStickX) + " rightStickY: " + normalizeShort(rightStickY) +
//                " inputMap: " + inputMap);

        JSONObject config = new JSONObject();
        try {
            config.put("leftTrigger", normalizeByte(leftTrigger));
            config.put("rightTrigger", normalizeByte(rightTrigger));

            config.put("leftStickX", normalizeShort(leftStickX));
            config.put("leftStickY", -normalizeShort(leftStickY));

            config.put("rightStickX", normalizeShort(rightStickX));
            config.put("rightStickY", -normalizeShort(rightStickY));
            config.put("inputMap", inputMap);

            String configString = config.toString();
            if (previousPayload.equals(configString)){
//                Log.e("Ignore", "Ignore Duplicate Payload");
            } else if (this.listener != null) {
                listener.controllerData(config);
                this.previousPayload = configString;
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private float normalizeShort(short input){
        return (float) ((float)input / (float) 0x7FFF);
    }
    private float normalizeByte(byte input){
        return (float) ((float)input / (float) 0x7F);
    }
    private Vector2d populateCachedVector(float x, float y) {
        // Reinitialize our cached Vector2d object
        inputVector.initialize(x, y);
        return inputVector;
    }
    private void handleDeadZone(Vector2d stickVector, float deadzoneRadius) {
        if (stickVector.getMagnitude() <= deadzoneRadius) {
            // Deadzone
            stickVector.initialize(0, 0);
        }
    }
    private void handleAxisSet(InputDeviceContext context, float lsX, float lsY, float rsX,
                               float rsY, float lt, float rt, float hatX, float hatY) {

        if (context.leftStickXAxis != -1 && context.leftStickYAxis != -1) {
            Vector2d leftStickVector = populateCachedVector(lsX, lsY);

            handleDeadZone(leftStickVector, .1f);

            context.leftStickX = (short) (leftStickVector.getX() * 0x7FFE);
            context.leftStickY = (short) (-leftStickVector.getY() * 0x7FFE);
        }

        if (context.rightStickXAxis != -1 && context.rightStickYAxis != -1) {
            Vector2d rightStickVector = populateCachedVector(rsX, rsY);

            handleDeadZone(rightStickVector, .1f);

            context.rightStickX = (short) (rightStickVector.getX() * 0x7FFE);
            context.rightStickY = (short) (-rightStickVector.getY() * 0x7FFE);
        }

        if (context.leftTriggerAxis != -1 && context.rightTriggerAxis != -1) {
            // Android sends an initial 0 value for trigger axes even if the trigger
            // should be negative when idle. After the first touch, the axes will go back
            // to normal behavior, so ignore triggersIdleNegative for each trigger until
            // first touch.
            if (lt != 0) {
                context.leftTriggerAxisUsed = true;
            }
            if (rt != 0) {
                context.rightTriggerAxisUsed = true;
            }
            if (context.triggersIdleNegative) {
                if (context.leftTriggerAxisUsed) {
                    lt = (lt + 1) / 2;
                }
                if (context.rightTriggerAxisUsed) {
                    rt = (rt + 1) / 2;
                }
            }

            context.leftTrigger = (byte)(lt * 0x7F);
            context.rightTrigger = (byte)(rt * 0x7F);
        }

        if (context.hatXAxis != -1 && context.hatYAxis != -1) {
            context.inputMap &= ~(ControllerPacket.LEFT_FLAG | ControllerPacket.RIGHT_FLAG);
            if (hatX < -0.5) {
                context.inputMap |= ControllerPacket.LEFT_FLAG;
                context.hatXAxisUsed = true;
            }
            else if (hatX > 0.5) {
                context.inputMap |= ControllerPacket.RIGHT_FLAG;
                context.hatXAxisUsed = true;
            }

            context.inputMap &= ~(ControllerPacket.UP_FLAG | ControllerPacket.DOWN_FLAG);
            if (hatY < -0.5) {
                context.inputMap |= ControllerPacket.UP_FLAG;
                context.hatYAxisUsed = true;
            }
            else if (hatY > 0.5) {
                context.inputMap |= ControllerPacket.DOWN_FLAG;
                context.hatYAxisUsed = true;
            }
        }

        sendControllerInputPacket();
    }

    private InputDeviceContext getContextForEvent(InputEvent event) {
        // Unknown devices use the default context
        if (event.getDeviceId() == 0) {
            return null;
        }
        else if (event.getDevice() == null) {
            // During device removal, sometimes we can get events after the
            // input device has been destroyed. In this case we'll see a
            // != 0 device ID but no device attached.
            return null;
        }

//        // HACK for https://issuetracker.google.com/issues/163120692
//        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.R) {
//            if (event.getDeviceId() == -1) {
//                return null;
//            }
//        }

        // Return the existing context if it exists
        InputDeviceContext context = inputDeviceContexts.get(event.getDeviceId());
        if (context != null) {
            return context;
        }

        // Otherwise create a new context
        context = createInputDeviceContextForDevice(event.getDevice());
        inputDeviceContexts.put(event.getDeviceId(), context);

        return context;
    }

    public boolean handleButtonDown(KeyEvent event) {
        Log.e("HERE", "HANDLE KEY DOWN");

//         don't override any presses unless its from a controller
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD){
            Log.e("HERE", "Not gamepad");
            return false;
        }

        InputDeviceContext context = getContextForEvent(event);
        if (context == null) {
            Log.e("HERE", "null context");
            return true;
        }

        int keyCode = handleRemapping(context, event);

//        if (prefConfig.flipFaceButtons) {
//            keyCode = handleFlipFaceButtons(keyCode);
//        }

        if (keyCode == 0) {
            Log.e("HERE", "0 keycode");
            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_MODE:
                context.hasMode = true;
                context.inputMap |= ControllerPacket.SPECIAL_BUTTON_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                if (event.getRepeatCount() == 0) {
                    context.startDownTime = event.getEventTime();
                }
                context.inputMap |= ControllerPacket.PLAY_FLAG;
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                context.hasSelect = true;
                context.inputMap |= ControllerPacket.BACK_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (context.hatXAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true;
                }
                context.inputMap |= ControllerPacket.LEFT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (context.hatXAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true;
                }
                context.inputMap |= ControllerPacket.RIGHT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true;
                }
                context.inputMap |= ControllerPacket.UP_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true;
                }
                context.inputMap |= ControllerPacket.DOWN_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                context.inputMap |= ControllerPacket.B_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                context.inputMap |= ControllerPacket.A_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                context.inputMap |= ControllerPacket.X_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                context.inputMap |= ControllerPacket.Y_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                context.inputMap |= ControllerPacket.LB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                context.inputMap |= ControllerPacket.RB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                context.inputMap |= ControllerPacket.LS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                context.inputMap |= ControllerPacket.RS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (context.leftTriggerAxisUsed) {
                    // Suppress this digital event if an analog trigger is active
                    return true;
                }
                context.leftTrigger = (byte)0xFF;
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (context.rightTriggerAxisUsed) {
                    // Suppress this digital event if an analog trigger is active
                    return true;
                }
                context.rightTrigger = (byte)0xFF;
                break;
            default:
                return false;
        }



        // We don't need to send repeat key down events, but the platform
        // sends us events that claim to be repeats but they're from different
        // devices, so we just send them all and deal with some duplicates.
        sendControllerInputPacket();
        return true;
    }

    public boolean handleButtonUp(KeyEvent event) {
        if ((event.getSource() & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD){
            Log.e("HEREUP", "Not gamepad");
            return false;
        }

        InputDeviceContext context = getContextForEvent(event);
        if (context == null) {
            Log.e("HEREUP", "null context");

            return true;
        }

        int keyCode = handleRemapping(context, event);

        if (keyCode == 0) {
            Log.e("HEREUP", "0 keycode");

            return true;
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_BUTTON_MODE:
                context.inputMap &= ~ControllerPacket.SPECIAL_BUTTON_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
            case KeyEvent.KEYCODE_MENU:
                context.inputMap &= ~ControllerPacket.PLAY_FLAG;
                break;
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                context.inputMap &= ~ControllerPacket.BACK_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (context.hatXAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true;
                }
                context.inputMap &= ~ControllerPacket.LEFT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (context.hatXAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true;
                }
                context.inputMap &= ~ControllerPacket.RIGHT_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true;
                }
                context.inputMap &= ~ControllerPacket.UP_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (context.hatYAxisUsed) {
                    // Suppress this duplicate event if we have a hat
                    return true;
                }
                context.inputMap &= ~ControllerPacket.DOWN_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                context.inputMap &= ~ControllerPacket.B_FLAG;
                break;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_BUTTON_A:
                context.inputMap &= ~ControllerPacket.A_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                context.inputMap &= ~ControllerPacket.X_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                context.inputMap &= ~ControllerPacket.Y_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                context.inputMap &= ~ControllerPacket.LB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                context.inputMap &= ~ControllerPacket.RB_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                context.inputMap &= ~ControllerPacket.LS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                context.inputMap &= ~ControllerPacket.RS_CLK_FLAG;
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                if (context.leftTriggerAxisUsed) {
                    // Suppress this digital event if an analog trigger is active
                    return true;
                }
                context.leftTrigger = 0;
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                if (context.rightTriggerAxisUsed) {
                    // Suppress this digital event if an analog trigger is active
                    return true;
                }
                context.rightTrigger = 0;
                break;
            default:
                return false;
        }

        sendControllerInputPacket();

        return true;
    }


    private int handleRemapping(InputDeviceContext context, KeyEvent event) {
//        // Don't capture the back button if configured
        if (context.ignoreBack) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                return -1;
            }
        }

        // Override mode button for 8BitDo controllers
        if (context.vendorId == 0x2dc8 && event.getScanCode() == 306) {
            return KeyEvent.KEYCODE_BUTTON_MODE;
        }

        // This mapping was adding in Android 10, then changed based on
        // kernel changes (adding hid-nintendo) in Android 11. If we're
        // on anything newer than Pie, just use the built-in mapping.
        if ((context.vendorId == 0x057e && context.productId == 0x2009 && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) || // Switch Pro controller
                (context.vendorId == 0x0f0d && context.productId == 0x00c1)) { // HORIPAD for Switch
            switch (event.getScanCode()) {
                case 0x130:
                    return KeyEvent.KEYCODE_BUTTON_A;
                case 0x131:
                    return KeyEvent.KEYCODE_BUTTON_B;
                case 0x132:
                    return KeyEvent.KEYCODE_BUTTON_X;
                case 0x133:
                    return KeyEvent.KEYCODE_BUTTON_Y;
                case 0x134:
                    return KeyEvent.KEYCODE_BUTTON_L1;
                case 0x135:
                    return KeyEvent.KEYCODE_BUTTON_R1;
                case 0x136:
                    return KeyEvent.KEYCODE_BUTTON_L2;
                case 0x137:
                    return KeyEvent.KEYCODE_BUTTON_R2;
                case 0x138:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
                case 0x139:
                    return KeyEvent.KEYCODE_BUTTON_START;
                case 0x13A:
                    return KeyEvent.KEYCODE_BUTTON_THUMBL;
                case 0x13B:
                    return KeyEvent.KEYCODE_BUTTON_THUMBR;
                case 0x13D:
                    return KeyEvent.KEYCODE_BUTTON_MODE;
            }
        }

        if (context.usesLinuxGamepadStandardFaceButtons) {
            // Android's Generic.kl swaps BTN_NORTH and BTN_WEST
            switch (event.getScanCode()) {
                case 304:
                    return KeyEvent.KEYCODE_BUTTON_A;
                case 305:
                    return KeyEvent.KEYCODE_BUTTON_B;
                case 307:
                    return KeyEvent.KEYCODE_BUTTON_Y;
                case 308:
                    return KeyEvent.KEYCODE_BUTTON_X;
            }
        }

        if (context.isNonStandardDualShock4) {
            switch (event.getScanCode()) {
                case 304:
                    return KeyEvent.KEYCODE_BUTTON_X;
                case 305:
                    return KeyEvent.KEYCODE_BUTTON_A;
                case 306:
                    return KeyEvent.KEYCODE_BUTTON_B;
                case 307:
                    return KeyEvent.KEYCODE_BUTTON_Y;
                case 308:
                    return KeyEvent.KEYCODE_BUTTON_L1;
                case 309:
                    return KeyEvent.KEYCODE_BUTTON_R1;
                /*
                **** Using analog triggers instead ****
                case 310:
                    return KeyEvent.KEYCODE_BUTTON_L2;
                case 311:
                    return KeyEvent.KEYCODE_BUTTON_R2;
                */
                case 312:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
                case 313:
                    return KeyEvent.KEYCODE_BUTTON_START;
                case 314:
                    return KeyEvent.KEYCODE_BUTTON_THUMBL;
                case 315:
                    return KeyEvent.KEYCODE_BUTTON_THUMBR;
                case 316:
                    return KeyEvent.KEYCODE_BUTTON_MODE;
                default:
                    return 0;
            }
        }
        // If this is a Serval controller sending an unknown key code, it's probably
        // the start and select buttons
        else if (context.isServal && event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            switch (event.getScanCode())  {
                case 314:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
                case 315:
                    return KeyEvent.KEYCODE_BUTTON_START;
            }
        }
        else if (context.isNonStandardXboxBtController) {
            switch (event.getScanCode()) {
                case 306:
                    return KeyEvent.KEYCODE_BUTTON_X;
                case 307:
                    return KeyEvent.KEYCODE_BUTTON_Y;
                case 308:
                    return KeyEvent.KEYCODE_BUTTON_L1;
                case 309:
                    return KeyEvent.KEYCODE_BUTTON_R1;
                case 310:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
                case 311:
                    return KeyEvent.KEYCODE_BUTTON_START;
                case 312:
                    return KeyEvent.KEYCODE_BUTTON_THUMBL;
                case 313:
                    return KeyEvent.KEYCODE_BUTTON_THUMBR;
                case 139:
                    return KeyEvent.KEYCODE_BUTTON_MODE;
                default:
                    // Other buttons are mapped correctly
            }

            // The Xbox button is sent as MENU
            if (event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
                return KeyEvent.KEYCODE_BUTTON_MODE;
            }
        }
        else if (context.vendorId == 0x0b05 && // ASUS
                (context.productId == 0x7900 || // Kunai - USB
                        context.productId == 0x7902)) // Kunai - Bluetooth
        {
            // ROG Kunai has special M1-M4 buttons that are accessible via the
            // joycon-style detachable controllers that we should map to Start
            // and Select.
            switch (event.getScanCode()) {
                case 264:
                case 266:
                    return KeyEvent.KEYCODE_BUTTON_START;

                case 265:
                case 267:
                    return KeyEvent.KEYCODE_BUTTON_SELECT;
            }
        }

        if (context.hatXAxis == -1 &&
                context.hatYAxis == -1 &&
                 /* FIXME: There's no good way to know for sure if xpad is bound
                    to this device, so we won't use the name to validate if these
                    scancodes should be mapped to DPAD

                    context.isXboxController &&
                  */
                event.getKeyCode() == KeyEvent.KEYCODE_UNKNOWN) {
            // If there's not a proper Xbox controller mapping, we'll translate the raw d-pad
            // scan codes into proper key codes
            switch (event.getScanCode())
            {
                case 704:
                    return KeyEvent.KEYCODE_DPAD_LEFT;
                case 705:
                    return KeyEvent.KEYCODE_DPAD_RIGHT;
                case 706:
                    return KeyEvent.KEYCODE_DPAD_UP;
                case 707:
                    return KeyEvent.KEYCODE_DPAD_DOWN;
            }
        }

        // Past here we can fixup the keycode and potentially trigger
        // another special case so we need to remember what keycode we're using
        int keyCode = event.getKeyCode();

        // This is a hack for (at least) the "Tablet Remote" app
        // which sends BACK with META_ALT_ON instead of KEYCODE_BUTTON_B
        if (keyCode == KeyEvent.KEYCODE_BACK &&
                !event.hasNoModifiers() &&
                (event.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0)
        {
            keyCode = KeyEvent.KEYCODE_BUTTON_B;
        }

        if (keyCode == KeyEvent.KEYCODE_BUTTON_START ||
                keyCode == KeyEvent.KEYCODE_MENU) {
            // Ensure that we never use back as start if we have a real start
            context.backIsStart = false;
        }
        else if (keyCode == KeyEvent.KEYCODE_BUTTON_SELECT) {
            // Don't use mode as select if we have a select
            context.modeIsSelect = false;
        }
        else if (context.backIsStart && keyCode == KeyEvent.KEYCODE_BACK) {
            // Emulate the start button with back
            return KeyEvent.KEYCODE_BUTTON_START;
        }
        else if (context.modeIsSelect && keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            // Emulate the select button with mode
            return KeyEvent.KEYCODE_BUTTON_SELECT;
        }
        else if (context.searchIsMode && keyCode == KeyEvent.KEYCODE_SEARCH) {
            // Emulate the mode button with search
            return KeyEvent.KEYCODE_BUTTON_MODE;
        }

        return keyCode;
    }

    private static boolean isExternal(InputDevice dev) {
        // The ASUS Tinker Board inaccurately reports Bluetooth gamepads as internal,
        // causing shouldIgnoreBack() to believe it should pass through back as a
        // navigation event for any attached gamepads.
        if (Build.MODEL.equals("Tinker Board")) {
            return true;
        }

        String deviceName = dev.getName();
        if (deviceName.contains("gpio") || // This is the back button on Shield portable consoles
                deviceName.contains("joy_key") || // These are the gamepad buttons on the Archos Gamepad 2
                deviceName.contains("keypad") || // These are gamepad buttons on the XPERIA Play
                deviceName.equalsIgnoreCase("NVIDIA Corporation NVIDIA Controller v01.01") || // Gamepad on Shield Portable
                deviceName.equalsIgnoreCase("NVIDIA Corporation NVIDIA Controller v01.02")) // Gamepad on Shield Portable (?)
        {
            Log.e("Controllerhelper", dev.getName()+" is internal by hardcoded mapping");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Landroid/view/InputDevice;->isExternal()Z is officially public on Android Q
            return dev.isExternal();
        }
        else {
            try {
                // Landroid/view/InputDevice;->isExternal()Z is on the light graylist in Android P
                return (Boolean)dev.getClass().getMethod("isExternal").invoke(dev);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (ClassCastException e) {
                e.printStackTrace();
            }
        }

        // Answer true if we don't know
        return true;
    }
    private InputDeviceContext createInputDeviceContextForDevice(InputDevice dev) {
        InputDeviceContext context = new InputDeviceContext(dev);
        String devName = dev.getName();

        context.inputDevice = dev;
        context.name = devName;
        context.id = dev.getId();
        context.external = isExternal(dev);
        context.vendorId = dev.getVendorId();
        context.productId = dev.getProductId();

        // Detect if the gamepad has Mode and Select buttons according to the Android key layouts.
        // We do this first because other codepaths below may override these defaults.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            boolean[] buttons = dev.hasKeys(KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BACK, 0);
            context.hasMode = buttons[0];
            context.hasSelect = buttons[1] || buttons[2];
        }

        context.leftStickXAxis = MotionEvent.AXIS_X;
        context.leftStickYAxis = MotionEvent.AXIS_Y;
        if (getMotionRangeForJoystickAxis(dev, context.leftStickXAxis) != null &&
                getMotionRangeForJoystickAxis(dev, context.leftStickYAxis) != null) {
            // This is a gamepad
            hasGameController = true;
            context.hasJoystickAxes = true;
        }

        InputDevice.MotionRange leftTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_LTRIGGER);
        InputDevice.MotionRange rightTriggerRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RTRIGGER);
        InputDevice.MotionRange brakeRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_BRAKE);
        InputDevice.MotionRange gasRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_GAS);
        InputDevice.MotionRange throttleRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_THROTTLE);
        if (leftTriggerRange != null && rightTriggerRange != null)
        {
            // Some controllers use LTRIGGER and RTRIGGER (like Ouya)
            context.leftTriggerAxis = MotionEvent.AXIS_LTRIGGER;
            context.rightTriggerAxis = MotionEvent.AXIS_RTRIGGER;
        }
        else if (brakeRange != null && gasRange != null)
        {
            // Others use GAS and BRAKE (like Moga)
            context.leftTriggerAxis = MotionEvent.AXIS_BRAKE;
            context.rightTriggerAxis = MotionEvent.AXIS_GAS;
        }
        else if (brakeRange != null && throttleRange != null)
        {
            // Others use THROTTLE and BRAKE (like Xiaomi)
            context.leftTriggerAxis = MotionEvent.AXIS_BRAKE;
            context.rightTriggerAxis = MotionEvent.AXIS_THROTTLE;
        }
        else
        {
            InputDevice.MotionRange rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX);
            InputDevice.MotionRange ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY);
            if (rxRange != null && ryRange != null && devName != null) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    if (dev.getVendorId() == 0x054c) { // Sony
                        if (dev.hasKeys(KeyEvent.KEYCODE_BUTTON_C)[0]) {
                            Log.e("ControllerHelper", "Detected non-standard DualShock 4 mapping");
                            context.isNonStandardDualShock4 = true;
                        }
                        else {
                            Log.e("ControllerHelper", "Detected DualShock 4 (Linux standard mapping)");
                            context.usesLinuxGamepadStandardFaceButtons = true;
                        }
                    }
                }
                else if (!devName.contains("Xbox") && !devName.contains("XBox") && !devName.contains("X-Box")) {
                    Log.e("ControllerHelper", "Assuming non-standard DualShock 4 mapping on < 4.4");
                    context.isNonStandardDualShock4 = true;
                }

                if (context.isNonStandardDualShock4) {
                    // The old DS4 driver uses RX and RY for triggers
                    context.leftTriggerAxis = MotionEvent.AXIS_RX;
                    context.rightTriggerAxis = MotionEvent.AXIS_RY;

                    // DS4 has Select and Mode buttons (possibly mapped non-standard)
                    context.hasSelect = true;
                    context.hasMode = true;
                }
                else {
                    // If it's not a non-standard DS4 controller, it's probably an Xbox controller or
                    // other sane controller that uses RX and RY for right stick and Z and RZ for triggers.
                    context.rightStickXAxis = MotionEvent.AXIS_RX;
                    context.rightStickYAxis = MotionEvent.AXIS_RY;

                    // While it's likely that Z and RZ are triggers, we may have digital trigger buttons
                    // instead. We must check that we actually have Z and RZ axes before assigning them.
                    if (getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Z) != null &&
                            getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RZ) != null) {
                        context.leftTriggerAxis = MotionEvent.AXIS_Z;
                        context.rightTriggerAxis = MotionEvent.AXIS_RZ;
                    }
                }

                // Triggers always idle negative on axes that are centered at zero
                context.triggersIdleNegative = true;
            }
        }

        if (context.rightStickXAxis == -1 && context.rightStickYAxis == -1) {
            InputDevice.MotionRange zRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_Z);
            InputDevice.MotionRange rzRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RZ);

            // Most other controllers use Z and RZ for the right stick
            if (zRange != null && rzRange != null) {
                context.rightStickXAxis = MotionEvent.AXIS_Z;
                context.rightStickYAxis = MotionEvent.AXIS_RZ;
            }
            else {
                InputDevice.MotionRange rxRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RX);
                InputDevice.MotionRange ryRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_RY);

                // Try RX and RY now
                if (rxRange != null && ryRange != null) {
                    context.rightStickXAxis = MotionEvent.AXIS_RX;
                    context.rightStickYAxis = MotionEvent.AXIS_RY;
                }
            }
        }

        // Some devices have "hats" for d-pads
        InputDevice.MotionRange hatXRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_X);
        InputDevice.MotionRange hatYRange = getMotionRangeForJoystickAxis(dev, MotionEvent.AXIS_HAT_Y);
        if (hatXRange != null && hatYRange != null) {
            context.hatXAxis = MotionEvent.AXIS_HAT_X;
            context.hatYAxis = MotionEvent.AXIS_HAT_Y;
        }

        // The ADT-1 controller needs a similar fixup to the ASUS Gamepad
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // The device name provided is just "Gamepad" which is pretty useless, so we
            // use VID/PID instead
            if (dev.getVendorId() == 0x18d1 && dev.getProductId() == 0x2c40) {
                context.backIsStart = true;
                context.modeIsSelect = true;
                context.hasSelect = true;
                context.hasMode = false;
            }
        }

        context.ignoreBack = shouldIgnoreBack(dev);

        if (devName != null) {
            // For the Nexus Player (and probably other ATV devices), we should
            // use the back button as start since it doesn't have a start/menu button
            // on the controller
            if (devName.contains("ASUS Gamepad")) {
                // We can only do this check on KitKat or higher, but it doesn't matter since ATV
                // is Android 5.0 anyway
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    boolean[] hasStartKey = dev.hasKeys(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_MENU, 0);
                    if (!hasStartKey[0] && !hasStartKey[1]) {
                        context.backIsStart = true;
                        context.modeIsSelect = true;
                        context.hasSelect = true;
                        context.hasMode = false;
                    }
                }

                // The ASUS Gamepad has triggers that sit far forward and are prone to false presses
                // so we increase the deadzone on them to minimize this
            }
            // SHIELD controllers will use small stick deadzones
            else if (devName.contains("SHIELD") || devName.contains("NVIDIA Controller")) {
                // The big Nvidia button on the Shield controllers acts like a Search button. It
                // summons the Google Assistant on the Shield TV. On my Pixel 4, it seems to do
                // nothing, so we can hijack it to act like a mode button.
                if (devName.contains("NVIDIA Controller v01.03") || devName.contains("NVIDIA Controller v01.04")) {
                    context.searchIsMode = true;
                    context.hasMode = true;
                }
            }
            // The Serval has a couple of unknown buttons that are start and select. It also has
            // a back button which we want to ignore since there's already a select button.
            else if (devName.contains("Razer Serval")) {
                context.isServal = true;

                // Serval has Select and Mode buttons (possibly mapped non-standard)
                context.hasMode = true;
                context.hasSelect = true;
            }
            // The Xbox One S Bluetooth controller has some mappings that need fixing up.
            // However, Microsoft released a firmware update with no change to VID/PID
            // or device name that fixed the mappings for Android. Since there's
            // no good way to detect this, we'll use the presence of GAS/BRAKE axes
            // that were added in the latest firmware. If those are present, the only
            // required fixup is ignoring the select button.
            else if (devName.equals("Xbox Wireless Controller")) {
                if (gasRange == null) {
                    context.isNonStandardXboxBtController = true;

                    // Xbox One S has Select and Mode buttons (possibly mapped non-standard)
                    context.hasMode = true;
                    context.hasSelect = true;
                }
            }
        }

        // handle vibration setup
        context = rumbleHelper.addDevice(context, dev);

        return context;
    }
    private boolean shouldIgnoreBack(InputDevice dev) {
        String devName = dev.getName();

        // The Serval has a Select button but the framework doesn't
        // know about that because it uses a non-standard scancode.
        if (devName.contains("Razer Serval")) {
            return true;
        }

        // Classify this device as a remote by name if it has no joystick axes
        if (!hasJoystickAxes(dev) && devName.toLowerCase().contains("remote")) {
            return true;
        }

        // Otherwise, dynamically try to determine whether we should allow this
        // back button to function for navigation.
        //
        // First, check if this is an internal device we're being called on.
        if (!isExternal(dev)) {
            InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);

            boolean foundInternalGamepad = false;
            boolean foundInternalSelect = false;
            for (int id : im.getInputDeviceIds()) {
                InputDevice currentDev = im.getInputDevice(id);

                // Ignore external devices
                if (currentDev == null || isExternal(currentDev)) {
                    continue;
                }

                // Note that we are explicitly NOT excluding the current device we're examining here,
                // since the other gamepad buttons may be on our current device and that's fine.
                if (currentDev.hasKeys(KeyEvent.KEYCODE_BUTTON_SELECT)[0]) {
                    foundInternalSelect = true;
                }

                // We don't check KEYCODE_BUTTON_A here, since the Shield Android TV has a
                // virtual mouse device that claims to have KEYCODE_BUTTON_A. Instead, we rely
                // on the SOURCE_GAMEPAD flag to be set on gamepad devices.
                if (hasGamepadButtons(currentDev)) {
                    foundInternalGamepad = true;
                }
            }

            // Allow the back button to function for navigation if we either:
            // a) have no internal gamepad (most phones)
            // b) have an internal gamepad but also have an internal select button (GPD XD)
            // but not:
            // c) have an internal gamepad but no internal select button (NVIDIA SHIELD Portable)
            return !foundInternalGamepad || foundInternalSelect;
        }
        else {
            // For external devices, we want to pass through the back button if the device
            // has no gamepad axes or gamepad buttons.
            return !hasJoystickAxes(dev) && !hasGamepadButtons(dev);
        }
    }
}
