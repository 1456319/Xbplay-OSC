package com.studio08.xbgamestream.Helpers;

import static com.studio08.xbgamestream.Helpers.Helper.getRenderEngine;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.pm.ShortcutManagerCompat;

import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.Controller.ControllerHandler;
import com.studio08.xbgamestream.PWAMainMenuActivity;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.CustomWebClient;
import com.studio08.xbgamestream.Web.GeckoWebviewClient;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.studio08.xbgamestream.Web.StreamWebviewListener;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoView;

public class PWAWebviewHandler {
    StreamWebview mSystemWebview ;
    GeckoView mGeckoView;
    GeckoWebviewClient geckoWebviewClient;
    Context mContext;
    public Boolean isGeckoViewRenderEngine;
    ControllerHandler controllerHandler;
    ApiClient.StreamingClientListener listener;
    private ConstraintLayout constraintLayout;

    public static String PWA_MAIN_MENU = (ApiClient.USE_DEV) ? ApiClient.BASE_URL_DEV + "pwa/main.html" : ApiClient.BASE_URL_PROD + "pwa/main.html";
    private final int MAX_CALLS_PER_SECOND = 60;
    private final long MIN_TIME_BETWEEN_CALLS_MS = 1000 / MAX_CALLS_PER_SECOND;
    private long lastCallTime = 0;
    private float movementXValue = 0;
    private float movementYValue = 0;
    public boolean alreadyCalledPwaConfig = false;
    private String lastUrl;

    ControllerHandler.ControllerHandlerListener controllerHandlerListener = new ControllerHandler.ControllerHandlerListener() {
        @Override
        public void controllerData(JSONObject data) {
            if (isGeckoViewRenderEngine){
                geckoWebviewClient.sendControllerInput(data);
            }
        }
    };

    CustomWebClient webviewPageFinishedListener = new CustomWebClient(false){
        @Override
        public void onPageFinished(WebView view, String url) {
            // Here you can check your new URL.
            super.onPageFinished(view, url);
            if(url.contains("pwa")){
                if (!alreadyCalledPwaConfig){
                    ApiClient.callJavaScript(mSystemWebview, "setPWAConfigData", getPwaConfigSettings(mContext));
                    listener.genericMessage("pwaInitialLoadComplete", "");
                    alreadyCalledPwaConfig = true;
                }

                listener.genericMessage("webviewPageLoadComplete", url);
            }
        }
    };

    StreamWebviewListener webviewListener = new StreamWebviewListener() {
        @Override
        public void onReLoginRequest() {
            ((Activity)mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    listener.onReLoginDetected();
                }
            });
        }

        // bubble close screen events up
        @Override
        public void closeScreen() {
            listener.onCloseScreenDetected();
        }

        @Override
        public void pressButtonWifiRemote(String type) {
            listener.pressButtonWifiRemote(type);
        }

        @Override
        public void setOrientationValue(String value) {
            listener.setOrientationValue(value);
        }

        @Override
        public void vibrate() {
            listener.vibrate();
        }

        @Override
        public void genericMessage(String type, String msg) {
            if (type.equals("rumble")){
                ((Activity)mContext).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (controllerHandler != null) {
                            try {
                                controllerHandler.handleRumble(msg);
                            } catch (Exception e){
                                e.printStackTrace();
                            }
                        }
                    }
                });
            } else if (type.equals("pointerLock")){
                togglePointerLock(msg);
            } else {
                listener.genericMessage(type, msg);
            }
        }
    };

    public PWAWebviewHandler(Context ctx, StreamWebview systemWebview, GeckoView geckoView, ApiClient.StreamingClientListener listener, ConstraintLayout constraintLayout){
        this.mGeckoView = geckoView;
        this.mSystemWebview = systemWebview;
        this.mContext = ctx;
        isGeckoViewRenderEngine = getRenderEngine(ctx).equals("geckoview");
        controllerHandler = new ControllerHandler(mContext);
        this.listener = listener;
        this.constraintLayout = constraintLayout;
    }

    public void initWebviews(){
        mSystemWebview.init();
        geckoWebviewClient = new GeckoWebviewClient(mContext, this.mGeckoView, true); //this auto calls setPWADataConfig on load

        setRenderEngineDependentListeners();
        controllerHandler.setListener(controllerHandlerListener);

        // set listeners
        mSystemWebview.setWebViewClient(webviewPageFinishedListener); // listen for page loads, sets config and render engine redirect

        mSystemWebview.setCustomObjectListener(webviewListener);
        geckoWebviewClient.setCustomObjectListener(webviewListener);
    }

    public void setRenderEngineDependentListeners(){
        setPointerCaptureListener();
        setControllerHandler();
    }

    public void doPwaMainMenu() {
        mSystemWebview.loadUrl(this.PWA_MAIN_MENU + "?pwaPlatform=" + this.getPwaPlatform());
    }

    public String getCurrentUrl() {
        if(getRenderEngine(mContext).equals("geckoview")){
            if(geckoWebviewClient != null){
                return geckoWebviewClient.getCurrentUrl();
            }
        } else if (getRenderEngine(mContext).equals("chrome")) {
            return null;
        } else {
            if (mSystemWebview != null && mSystemWebview.getUrl() != null){
                return mSystemWebview.getUrl();
            }
        }
        return null;
    }

    private String getPwaPlatform(){
        return "android";
    }

    public void setControllerHandler(){
        if (isGeckoViewRenderEngine) {
            controllerHandler.setSourceView(mGeckoView);// will prevent events going to gamepad api
        } else {
            controllerHandler.setPassthroughView(mSystemWebview); // Buttons Wont work in chrome because chrome steals the controller input. Needed for vibration still
        }
    }

    public void handleRenderEngineSwitch(String pwaConfigData){
        String renderEngine = getRenderEngine(mContext);
        if (renderEngine.equals("webview") || renderEngine.equals("empty")){
            return;
        }

        try {
            this.lastUrl = mSystemWebview.getUrl();
            Log.e("PWAWH", "Saved current URL Before render engine switch" + this.lastUrl);

            // create pwaConfig body
            JSONObject data = new JSONObject(pwaConfigData);

            // load new location
            Uri uri = Uri.parse(this.lastUrl);
            String cleanUrl = uri.getScheme() + "://" + uri.getAuthority() + uri.getPath();
            String url = data.getString("url"); // url is relative
            String newUrl = cleanUrl + "/../" + url;

            if (renderEngine.equals("geckoview")) {
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(this.mContext)){
                    data.put("steam_game_list_shortcuts", true); // hack to ensure shortcuts work i geckoview
                }
                data.put("native_app_version", BuildConfig.VERSION_CODE);
                geckoWebviewClient.pwaConfigData = data;
                switchToGeckoView();
                geckoWebviewClient.loadUrl(newUrl);
            } else if(renderEngine.equals("chrome")) {
                TWAClient twaClient = new TWAClient(mContext, pwaConfigData);
                twaClient.launchTWSA(newUrl);
            }
        } catch(Exception e){
            e.printStackTrace();
        }
    }

    public void handleRenderEngineReturn(boolean purchaseRequired){
        String renderEngine = getRenderEngine(mContext);
        if (renderEngine.equals("webview") || renderEngine.equals("empty")){
            return;
        }

        if (getRenderEngine(mContext).equals("geckoview")){
            Log.e("PWAWH", this.lastUrl);
            switchToWebView();

            if (purchaseRequired){
                mSystemWebview.loadUrl(PWA_MAIN_MENU + "?pcheckRedirect=1");
            } else {
                mSystemWebview.loadUrl(this.lastUrl);
            }
        }
    }

    public void togglePointerLock(String msg) {
        Log.e("HERE", "handle pointer lock" + msg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (msg.equals("true")) {
                if(!isGeckoViewRenderEngine){
                    mSystemWebview.requestPointerCapture();
                } else {
                    mGeckoView.requestPointerCapture();
                }
            } else {
                if(!isGeckoViewRenderEngine){
                    mSystemWebview.releasePointerCapture();
                } else {
                    mGeckoView.releasePointerCapture();
                }
            }
        }
    }
    private void setPointerCaptureListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isGeckoViewRenderEngine) {
                mSystemWebview.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                    @Override
                    public boolean onCapturedPointer(View view, MotionEvent motionEvent) {
                        return handleMouseMotionEvent(motionEvent);
                    }
                });
            } else {
                mGeckoView.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                    @Override
                    public boolean onCapturedPointer(View view, MotionEvent motionEvent) {
                        return handleMouseMotionEvent(motionEvent);
                    }
                });
            }
        }
    }

    public boolean handleMouseMotionEvent(MotionEvent motionEvent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            JSONObject payload = new JSONObject();
            int action = motionEvent.getAction();
            if (action == MotionEvent.ACTION_BUTTON_RELEASE ||
                    action == MotionEvent.ACTION_BUTTON_PRESS ||
                    action == MotionEvent.ACTION_DOWN ||
                    action == MotionEvent.ACTION_UP) {
                try {
                    payload.put("buttonState", motionEvent.getButtonState());
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else if (action == MotionEvent.ACTION_MOVE) {
                try {
                    if (this.shouldDebounceMotionEvent()){
                        this.movementXValue += motionEvent.getX();
                        this.movementYValue += motionEvent.getY();
                        return true;
                    }
                    // randomly increasing sensitivity bc its weirdly lwo
                    payload.put("movementX", (motionEvent.getX() + this.movementXValue) * 2);
                    payload.put("movementY", (motionEvent.getY() + this.movementYValue) * 2);
                    this.movementXValue = 0;
                    this.movementYValue = 0;
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else if (action == MotionEvent.ACTION_SCROLL) {
                try {
                    payload.put("deltaY", motionEvent.getAxisValue(MotionEvent.AXIS_VSCROLL));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                Log.e("HERE", "unknown mouse event called" + motionEvent.getAction());
                return true;
            }

            if (!isGeckoViewRenderEngine){
                ApiClient.callJavaScript(mSystemWebview, "setMousePayload", payload.toString());
            } else {
                geckoWebviewClient.sendMouseInput(payload);
            }
            return true;
        }
        return false;
    }

    public boolean shouldDebounceMotionEvent() {
        long currentTime = System.currentTimeMillis();

        // Check if the time since the last call is less than the minimum time between calls
        if (currentTime - lastCallTime < MIN_TIME_BETWEEN_CALLS_MS) {
            return true; // Ignore this event
        }

        lastCallTime = currentTime;
        return false;
    }


    public static String getPwaConfigSettings(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        EncryptClient encryptClient = new EncryptClient(context);
        // 1. Get config
        JSONObject config = new JSONObject();
        String videoFit = prefs.getString("video_fit_key", "cover");
        Integer videoOffset = prefs.getInt("video_vertical_offset_key", 50);

        String emulateClient = prefs.getString("emulate_client_key", "windows");

        String controllerRefreshRate = prefs.getString("controller_refresh_key", "32");
        String maxBitrate = prefs.getString("max_bitrate_key", "");

        Boolean enableAudio = prefs.getBoolean("enable_audio_default_key", true);
        String miniGamepadSize = prefs.getString("mini_gamepad_size_key", "30");

        // get tilt control params
        int tiltSensitivity = prefs.getInt("tilt_sensitivity_key", 2);
        int tiltDeadzone = prefs.getInt("tilt_deadzone_key", 3);
        boolean invertX = prefs.getBoolean("tilt_invert_x_key", false);
        boolean invertY = prefs.getBoolean("tilt_invert_y_key", false);
        boolean rumbleController = prefs.getBoolean("rumble_controller_key", true);
        int rumbleIntensity = prefs.getInt("rumble_intensity_key", 1);

        try {
            config.put("video-fit", videoFit);
            config.put("video-vertical-offset", videoOffset);

            JSONObject customControllerMap = Helper.getActiveCustomPhysicalGamepadMappings(context);
            if(customControllerMap != null){
                config.put("physical-controller-button-mappings", Helper.getActiveCustomPhysicalGamepadMappings(context));
            }

            config.put("gamepadRefreshRateMs", controllerRefreshRate);
            config.put("userAgentType", emulateClient);
            config.put("miniGamepadSize", miniGamepadSize);

            if(!enableAudio) {
                config.put("disable-audio", true);
            }

            // add tilt controls
            config.put("tiltSensitivity", tiltSensitivity);
            config.put("tiltDeadzone", tiltDeadzone);
            if(invertX){
                config.put("tiltInvertX", true);
            }
            if(invertY){
                config.put("tiltInvertY", true);
            }
            if(!TextUtils.isEmpty(maxBitrate)){
                config.put("maxBitrate", maxBitrate);
            }

            // rumble
            config.put("rumble_controller", rumbleController);
            config.put("rumble_intensity", rumbleIntensity);
            config.put("render_engine", getRenderEngine(context));
            if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)){
                config.put("pwa_prompt_for_xcloud_shortcuts", true);
                config.put("pwa_prompt_for_xhome_shortcuts", true);
            }
            config.put("native_app_version", BuildConfig.VERSION_CODE);
        } catch (Exception e) {}

        //2. Get Xal
        JSONObject xalData = encryptClient.getJSONObject("xalData");

        //3. get price data
        JSONObject priceData = encryptClient.getJSONObject("productPriceData");

        // 3. combine results
        JSONObject result = new JSONObject();
        try {
            if (xalData != null){
                result.put("xalData", xalData);
            }
            if (priceData != null){
                result.put("productPriceData", priceData);
            }
            result.put("configData", config);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return result.toString();
    }

    // Method to switch to GeckoView
    public void switchToGeckoView() {
        // Remove WebView from the layout
        if (mSystemWebview.getParent() != null) {
            constraintLayout.removeView(mSystemWebview);
        }

        if (mGeckoView.getParent() == null) {
            constraintLayout.addView(mGeckoView, new ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.MATCH_PARENT));

            // Set the constraints for GeckoView
            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(constraintLayout);
            constraintSet.connect(mGeckoView.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            constraintSet.connect(mGeckoView.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            constraintSet.connect(mGeckoView.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(mGeckoView.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            constraintSet.applyTo(constraintLayout);
        }

        setPointerCaptureListener();
        mGeckoView.requestFocus();
    }

    // Method to switch back to WebView
    public void switchToWebView() {
        // Remove GeckoView from the layout, if it exists
        if (geckoWebviewClient != null){
            geckoWebviewClient.loadUrl("about:blank");
        }
        if (mGeckoView.getParent() != null) {
            constraintLayout.removeView(mGeckoView);
        }

        // Add WebView back to the layout if it is not already added
        if (mSystemWebview.getParent() == null) {
            constraintLayout.addView(mSystemWebview, new ConstraintLayout.LayoutParams(
                    ConstraintLayout.LayoutParams.MATCH_PARENT,
                    ConstraintLayout.LayoutParams.MATCH_PARENT));

            // Set the constraints for WebView
            ConstraintSet constraintSet = new ConstraintSet();
            constraintSet.clone(constraintLayout);
            constraintSet.connect(mSystemWebview.getId(), ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP);
            constraintSet.connect(mSystemWebview.getId(), ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM);
            constraintSet.connect(mSystemWebview.getId(), ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START);
            constraintSet.connect(mSystemWebview.getId(), ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END);
            constraintSet.applyTo(constraintLayout);
        }

        mSystemWebview.requestFocus();
    }

    public void togglePip(boolean enabled) {
        if (getRenderEngine(mContext).equals("geckoview")){
            geckoWebviewClient.togglePip(enabled);
        } else {
            ApiClient.callJavaScript(mSystemWebview, "togglePip", enabled);
        }
    }

    public void cleanUpBeforeDestroy() {
        try {
            if (mSystemWebview != null) {
                this.mSystemWebview.clearHistory();
                this.mSystemWebview.clearCache(false);
                this.mSystemWebview.loadUrl("about:blank");
                this.mSystemWebview.onPause();
                this.mSystemWebview.removeAllViews();
                this.mSystemWebview.destroy();
                this.mSystemWebview.cleanup();
                this.mSystemWebview = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.mSystemWebview = null;
        }
        try {
            if (geckoWebviewClient != null){
                geckoWebviewClient.destroy();
                geckoWebviewClient = null;
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        controllerHandler.destroy();
    }
}
