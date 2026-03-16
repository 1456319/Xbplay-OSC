package com.studio08.xbgamestream.Web;

import static com.studio08.xbgamestream.Helpers.Helper.getRenderEngine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.core.content.pm.ShortcutManagerCompat;

import com.android.billingclient.api.Purchase;
import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.Controller.ControllerHandler;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.RewardedAdLoader;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.Helpers.TWAClient;
import com.studio08.xbgamestream.PWAMainMenuActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoView;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ApiClient {
    // emits event to indicate that we must re authenticate
    public interface StreamingClientListener {
        void onReLoginDetected();
        void onCloseScreenDetected();
        void pressButtonWifiRemote(String type);
        void setOrientationValue(String value);
        void vibrate();
        void genericMessage(String type, String msg);
    }
    private Context context;
    private StreamWebview streamWebview;
    private GeckoView geckoStreamWebview;
    GeckoWebviewClient geckoWebviewClient;

    ApiClient.StreamingClientListener listener;
    public static boolean USE_DEV = BuildConfig.BUILD_TYPE.equals("debug");
    public static String BASE_URL_DEV = BuildConfig.BASE_URL_DEV;
    public static String BASE_URL_PROD = TextUtils.isEmpty(BuildConfig.BASE_URL_PROD) ? "https://www.xbgamestreamplay.com/" : BuildConfig.BASE_URL_PROD;

    public static String STREAMING_URL = (USE_DEV) ? BASE_URL_DEV + "android_stream.html" : BASE_URL_PROD + "android_stream.html";
    private String CONTROLLER_URL = (USE_DEV) ? BASE_URL_DEV + "android_stream.html?controllerOnly=1" : BASE_URL_PROD + "android_stream.html?controllerOnly=1";
    private String CONTROLLER_BUILDER_URL = (USE_DEV) ? BASE_URL_DEV + "builder/controller_builder.html" : BASE_URL_PROD + "builder/controller_builder.html";
    private String TUTORIAL_SCREENS_URL = (USE_DEV) ? BASE_URL_DEV + "swipe-screens/features_full.html" : BASE_URL_PROD + "swipe-screens/features_full.html";
    private String WIFI_REMOTE_URL = (USE_DEV) ? BASE_URL_DEV + "android_stream.html?remoteOnly=1" : BASE_URL_PROD + "android_stream.html?remoteOnly=1";
    private String CAST_REMOTE_URL = (USE_DEV) ? BASE_URL_DEV + "android_stream.html?castRemoteOnly=1" : BASE_URL_PROD + "android_stream.html?castRemoteOnly=1";
    private String PHYSICAL_CONTROLLER_SETUP_URL = (USE_DEV) ? BASE_URL_DEV + "physical_controller/setup.html" : BASE_URL_PROD + "physical_controller/setup.html";
    private String LOOKUP_TVCODE_BASE_URL = (USE_DEV) ? BASE_URL_DEV + "aws/tv_code" : BASE_URL_PROD + "aws/tv_code";
    private String WEBOS_TVCODE_BASE_URL = (USE_DEV) ? BASE_URL_DEV + "aws/webos/tv_code_tokens" : BASE_URL_PROD + "aws/webos/tv_code_tokens";

    private String XCLOUD_GAME_PICKER = (USE_DEV) ? BASE_URL_DEV + "title_picker.html" : BASE_URL_PROD + "title_picker.html";
    private String VOICE_REMOTE_URL = (USE_DEV) ? BASE_URL_DEV + "voice_commands.html" : BASE_URL_PROD + "voice_commands.html";
    private String WIDGET_INFO_URL = (USE_DEV) ? BASE_URL_DEV + "swipe-screens/info-popup/features_widgets.html" : BASE_URL_PROD + "swipe-screens/info-popup/features_widgets.html";
    public static String LOOKUP_PCHECK_BASE_URL = (USE_DEV) ? BASE_URL_DEV + "api/get_user_data" : BASE_URL_PROD + "api/get_user_data";
    public static String TOKEN_SAVE_BASE_URL = (USE_DEV) ? BASE_URL_DEV + "users/tokens/android" : BASE_URL_PROD + "users/tokens/android";
    public static String TOKEN_GET_BASE_URL = (USE_DEV) ? BASE_URL_DEV + "users/tokens/get_active" : BASE_URL_PROD + "users/tokens/get_active";
    public static String TOKEN_DATA_ENDPOINT = (USE_DEV) ? BASE_URL_DEV + "xal/tokenData" : BASE_URL_PROD + "xal/tokenData";
    public static String SMARTGLASS_COMMAND_URL = "https://xccs.xboxlive.com/commands";

    private String gsToken;
    private String serverId;
    private Boolean loadedStreamView = false;
    private Boolean isXcloud = false;
    CustomWebClient webviewStreamStartClient;
    private ControllerHandler controllerHandler;

    // for debouncing mouse events
    private final int MAX_CALLS_PER_SECOND = 60;
    private final long MIN_TIME_BETWEEN_CALLS_MS = 1000 / MAX_CALLS_PER_SECOND;
    private long lastCallTime = 0;
    private float movementXValue = 0;
    private float movementYValue = 0;

    // listener applied to webview for all streaming implementations
    StreamWebviewListener webviewListener = new StreamWebviewListener() {

        // bubble login events up
        @Override
        public void onReLoginRequest() {
            ((Activity)context).runOnUiThread(new Runnable() {
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
                ((Activity)context).runOnUiThread(new Runnable() {
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

    ControllerHandler.ControllerHandlerListener controllerHandlerListener = new ControllerHandler.ControllerHandlerListener() {
        @Override
        public void controllerData(JSONObject data) {
            if (geckoWebviewClient != null){
                geckoWebviewClient.sendControllerInput(data);
            }
        }
    };

    public void togglePointerLock(String msg) {
        Log.e("HERE", "handle pointer lock" + msg);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (msg.equals("true")) {
                if(this.streamWebview != null){
                    streamWebview.requestPointerCapture();
                } else if (this.geckoStreamWebview != null){
                    geckoStreamWebview.requestPointerCapture();
                }
            } else {
                if(this.streamWebview != null){
                    streamWebview.releasePointerCapture();
                } else if (this.geckoStreamWebview != null){
                    geckoStreamWebview.releasePointerCapture();
                }
            }
        }
    }

    public void setControllerHandler(ControllerHandler handler){
        controllerHandler = handler;
        controllerHandler.setListener(controllerHandlerListener);
        if (geckoStreamWebview != null) {
            controllerHandler.setSourceView(geckoStreamWebview);// will prevent events going to gamepad api
        } else if (streamWebview != null){
            controllerHandler.setPassthroughView(streamWebview); // Buttons Wont work in chrome because chrome steals the controller input. Needed for vibration still
        }
    }

    // default use - for streaming
    public ApiClient(Context context, StreamWebview webview, String token, String titleIdOrServerId, boolean isXcloud) {
        this.context = context;
        this.listener = null;
        this.streamWebview = webview;
        this.gsToken = token;
        this.serverId = titleIdOrServerId;
        this.isXcloud = isXcloud;
        setWebClient();
    }

    public ApiClient(Context context, GeckoView webview, String token, String titleIdOrServerId, boolean isXcloud) {
        this.context = context;
        this.listener = null;
        this.geckoStreamWebview = webview;
        this.gsToken = token;
        this.serverId = titleIdOrServerId;
        this.isXcloud = isXcloud;
        setGeckoWebClient();
    }

    // used for tutorial and controller builder
    public ApiClient(Context context, StreamWebview webview){
        this.context = context;
        this.streamWebview = webview;
        setWebClient();
    }

    public ApiClient(Context context, GeckoView webview){
        this.context = context;
        this.geckoStreamWebview = webview;
        setGeckoWebClient();
    }

    // used for API calls only
    public ApiClient(Context context){
        this.context = context;
    }

    // Assign the listener implementing events interface that will receive the events
    public void setCustomObjectListener(ApiClient.StreamingClientListener listener) {
        this.listener = listener;
    }

    private void setGeckoWebClient(){
        geckoWebviewClient = new GeckoWebviewClient(this.context, this.geckoStreamWebview, this);
        setPointerCaptureListener();
    }

    private void setWebClient(){
        // call start stream on complete
        webviewStreamStartClient = new CustomWebClient(streamWebview.showLoadingDialog){
            @Override
            public void onPageFinished(WebView view, String url) {
                // Here you can check your new URL.
                super.onPageFinished(view, url);
                setStreamConfig();
                loadedStreamView = true;
                //callJavaScript(streamWebview, "startStream"); // not calling this anymore
            }
        };

        setPointerCaptureListener();
    }

    void setPointerCaptureListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (streamWebview != null) {
                streamWebview.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
                    @Override
                    public boolean onCapturedPointer(View view, MotionEvent motionEvent) {
                        return handleMouseMotionEvent(motionEvent);
                    }
                });
            } else if (geckoStreamWebview != null) {
                geckoStreamWebview.setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
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
                    payload.put("movementX", (motionEvent.getX() + this.movementXValue) * 4);
                    payload.put("movementY", (motionEvent.getY() + this.movementYValue) * 4);
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

            if (streamWebview != null){
                callJavaScript(streamWebview, "setMousePayload", payload.toString());
            } else if (geckoStreamWebview != null && geckoWebviewClient != null){
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

    // dont do this in twa since we dont have js callbacks
    public void doXcloudGamePicker() {
        if (geckoWebviewClient != null){
            Log.e("APICLIENT", "Using geckoview to stream");
            geckoWebviewClient.setCustomObjectListener(webviewListener);
            geckoWebviewClient.loadUrl(this.XCLOUD_GAME_PICKER + "?xcloudToken=" + gsToken + "&use_v2=1");
        } else {
            this.streamWebview.setWebViewClient(webviewStreamStartClient);
            this.streamWebview.setCustomObjectListener(webviewListener);
            this.streamWebview.loadUrl(this.XCLOUD_GAME_PICKER + "?xcloudToken=" + gsToken + "&use_v2=1");
        }
    }

    public void doStreaming() {
        TWAClient twaClient = new TWAClient(context, getConfigSettings());
        if (twaClient.getShouldUseTWA()){
            Log.e("APICLIENT", "Using TWA to stream");
            twaClient.launchTWSA(this.STREAMING_URL);
        } else if (geckoWebviewClient != null){
            Log.e("APICLIENT", "Using geckoview to stream");
            geckoWebviewClient.setCustomObjectListener(webviewListener);
            geckoWebviewClient.loadUrl(this.STREAMING_URL);
        } else {
            Log.e("APICLIENT", "Using system webview");
            this.streamWebview.setWebViewClient(webviewStreamStartClient);
            this.streamWebview.setCustomObjectListener(webviewListener);
            this.streamWebview.loadUrl(this.STREAMING_URL);
        }
    }

    public void doController() {
        TWAClient twaClient = new TWAClient(context, getConfigSettings());
        if (twaClient.getShouldUseTWA()){
            Log.e("APICLIENT", "Using TWA to stream");
            twaClient.launchTWSA(this.CONTROLLER_URL);
        } else if (geckoWebviewClient != null){
            Log.e("APICLIENT", "Using geckoview to stream");
            geckoWebviewClient.setCustomObjectListener(webviewListener);
            geckoWebviewClient.loadUrl(this.CONTROLLER_URL);
        } else {
            Log.e("APICLIENT", "Using system webview");
            this.streamWebview.setWebViewClient(webviewStreamStartClient);
            this.streamWebview.setCustomObjectListener(webviewListener);
            this.streamWebview.loadUrl(this.CONTROLLER_URL);
        }
    }

    public void doControllerBuilder(String type) {
        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);

        // add optional snap to grid params
        String params = "";
        boolean enableSnapGrids = prefs.getBoolean("build_snap_grid_key", false);
        String miniGamepadSize = prefs.getString("mini_gamepad_size_key", "30");

        if(enableSnapGrids){
            int grid_size = prefs.getInt("build_grid_size_key", 20);
            if(grid_size < 5) grid_size = 5;
            params += "?snap_to_grid=true&snap_to_grid_size=" + grid_size;
        }
        if(type != null){
            // add correct query string params
            if (enableSnapGrids) {
                params += "&";
            } else {
                params += "?";
            }

            params += "customType=" + type;
        }

        //add mini gamepad size
        if(!params.contains("?")) {
            params+= "?miniGamepadSize=" + miniGamepadSize;
        } else {
            params += "&miniGamepadSize=" + miniGamepadSize;
        }

        TWAClient twaClient = new TWAClient(context, getConfigSettings());
        if (twaClient.getShouldUseTWA()){
            Log.e("APICLIENT", "Using TWA to stream");
            twaClient.launchTWSA(this.CONTROLLER_BUILDER_URL + params);
        } else if (geckoWebviewClient != null){
            Log.e("APICLIENT", "Using geckoview to stream");
            geckoWebviewClient.setCustomObjectListener(webviewListener);
            geckoWebviewClient.loadUrl(this.CONTROLLER_BUILDER_URL + params);
        } else {
            Log.e("APICLIENT", "Using system webview");
            this.streamWebview.setCustomObjectListener(webviewListener);
            this.streamWebview.loadUrl(this.CONTROLLER_BUILDER_URL + params);
        }
    }

    public void doTutorialScreens(boolean showFull) {
        this.streamWebview.disableLoadingDialog();
        this.streamWebview.setCustomObjectListener(webviewListener);

        TUTORIAL_SCREENS_URL = (USE_DEV) ? BASE_URL_DEV + "swipe-screens/features_full.html" : BASE_URL_PROD + "swipe-screens/features_full.html";
        this.streamWebview.loadUrl(this.TUTORIAL_SCREENS_URL);
    }

    public void doWidgetTutorial() {
        this.streamWebview.disableLoadingDialog();
        this.streamWebview.setCustomObjectListener(webviewListener);
        this.streamWebview.loadUrl(this.WIDGET_INFO_URL);
    }

    public void doWifiVoiceRemote() {
        this.streamWebview.setCustomObjectListener(webviewListener);
        this.streamWebview.loadUrl(this.VOICE_REMOTE_URL);
    }

    public void doWifRemote() {
        this.streamWebview.setCustomObjectListener(webviewListener);
        this.streamWebview.loadUrl(this.WIFI_REMOTE_URL);
    }

    public void doCastRemote() {
        this.streamWebview.setCustomObjectListener(webviewListener);
        this.streamWebview.loadUrl(this.CAST_REMOTE_URL);
    }

    // dont do this in geckoview. Not supported yet
    public void doPhysicalControllerSetup() {
        TWAClient twaClient = new TWAClient(context, getConfigSettings());
        if (twaClient.getShouldUseTWA()){
            twaClient.launchTWSA(this.PHYSICAL_CONTROLLER_SETUP_URL);
        } else {
            this.streamWebview.setCustomObjectListener(webviewListener);
            this.streamWebview.loadUrl(this.PHYSICAL_CONTROLLER_SETUP_URL);
        }
    }

    public void doSaveTvCode(Integer code, String serverUrl){
        try {
            String url = LOOKUP_TVCODE_BASE_URL;
            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest getRequest = new StringRequest(Request.Method.POST, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.i("HERE", "Saved tv code");
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO Auto-generated method stub
                            Toast.makeText(context, "Network error. Companion app might not work..", Toast.LENGTH_LONG).show();
                        }
                    }
            )  {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("code", code.toString());
                        data.put("url", serverUrl);
                        return data.toString().getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            };
            queue.add(getRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // REVOKES ACCESS on failure
    public void getToken(){
        try {
            EncryptClient encryptClient = new EncryptClient(context);
            String gsToken = encryptClient.getValue("gsToken");

            if (TextUtils.isEmpty(gsToken)) {
                RewardedAdLoader.setPurchaseItem(false, context);
                return;
            }

            String url = TOKEN_GET_BASE_URL;
            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest getRequest = new StringRequest(Request.Method.POST, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            try {
                                JSONObject jsonResponse = new JSONObject(response);
                                int active = jsonResponse.getInt("activePurchase");

                                // show cross restore status on new unlock
                                if (active == 1 && !RewardedAdLoader.getPurchaseItem(context)){
                                    Toast.makeText(context, "License: " + jsonResponse.getString("message"), Toast.LENGTH_LONG).show();
                                }

                                // unlock
                                RewardedAdLoader.setPurchaseItem(active == 1, context);

                                // save that we were able to hit the cache endpoint so we don't again
                                SharedPreferences freqPrefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
                                SharedPreferences.Editor editor = freqPrefs.edit();
                                editor.putLong("nextMakeGetTokenRequest", System.currentTimeMillis() + RewardedAdLoader.GET_TOKEN_CACHE_DURATION);
                                editor.apply();

                            } catch (JSONException e) {
                                e.printStackTrace();
                                RewardedAdLoader.setPurchaseItem(false, context);
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO Auto-generated method stub
                            Toast.makeText(context, "Network error.", Toast.LENGTH_SHORT).show();
                            RewardedAdLoader.setPurchaseItem(false, context);
                        }
                    }
            ) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("gsToken", gsToken);
                        return data.toString().getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            };
            queue.add(getRequest);
        } catch (Exception e) {
            e.printStackTrace();
            RewardedAdLoader.setPurchaseItem(false, context);
        }
    }

    public void sendToken(Purchase purchase){
        try {
            EncryptClient encryptClient = new EncryptClient(context);
            String gsToken = encryptClient.getValue("gsToken");

            if (TextUtils.isEmpty(gsToken)) {
                Log.e("ApiClient", "Ignore sendToken due to empty gsToken");
                return;
            }

            String url = TOKEN_SAVE_BASE_URL;
            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest getRequest = new StringRequest(Request.Method.POST, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            try {
                                JSONObject jsonResponse = new JSONObject(response);
                                String purchaseToken = jsonResponse.getString("purchaseToken");
                                encryptClient.saveValue("purchaseToken", purchaseToken);
                                encryptClient.saveValue("gamertag", jsonResponse.getString("gamertag"));
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO Auto-generated method stub
                            Toast.makeText(context, "Network error.", Toast.LENGTH_SHORT).show();
                        }
                    }
            ) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("purchaseToken", purchase.getPurchaseToken());
                        data.put("purchaseTime", purchase.getPurchaseTime());
                        data.put("packageName", purchase.getPackageName());
                        data.put("product", purchase.getProducts().get(0));
                        data.put("orderId", purchase.getOrderId());
                        data.put("gsToken", gsToken);
                        return data.toString().getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            };
            queue.add(getRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doLookupTvCode(String code) {
        try {
            if(TextUtils.isEmpty(code) || code.length() != 6) {
                createPopup("Invalid Code", "You entered an invalid TV code. Please enter the 6 digit code displayed on your TV above.");
                return;
            }

            EncryptClient encryptClient = new EncryptClient(context);
            String serverId = encryptClient.getValue("serverId");
            String gsToken = encryptClient.getValue("gsToken");

            if(TextUtils.isEmpty(gsToken)) {
                createPopup("Sign-in Required", "You must sign-in to your Xbox Live account first. Click the Sign-in button above, then try again.");
                return;
            }

            Toast.makeText(context, "Connecting to TV. Please Wait...", Toast.LENGTH_SHORT).show();

            String url = LOOKUP_TVCODE_BASE_URL + "/" + code;
            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest getRequest = new StringRequest(Request.Method.GET, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.i("HERE", "Got tv code");
                            startTvStream(response);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO Auto-generated method stub
                            if(error.networkResponse != null && error.networkResponse.statusCode == 404) {
                                // if we cant find the AndroidTV code, lookup the LG tv code.
                                doLookupLGTvCodeTokens(code);
                            } else {
                                createPopup("Network Error", "Error getting TV code. Please make sure you are connected to the internet and try again later");
                            }
                        }
                    }
            );
            queue.add(getRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // check LG WebOS TVs
    public void doLookupLGTvCodeTokens(String code) {
        try {
            if(TextUtils.isEmpty(code) || code.length() != 6) {
                createPopup("Invalid Code", "You entered an invalid TV code. Please enter the 6 digit code displayed on your TV above.");
                return;
            }

            EncryptClient encryptClient = new EncryptClient(context);
            String serverId = encryptClient.getValue("serverId");
            String gsToken = encryptClient.getValue("gsToken");

            if(TextUtils.isEmpty(gsToken)) {
                createPopup("Sign-in Required", "You must sign-in to your Xbox Live account first. Click the Sign-in button above, then try again.");
                return;
            }

            String url = WEBOS_TVCODE_BASE_URL + "/" + code;
            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest getRequest = new StringRequest(Request.Method.GET, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.i("HERE", "Got LG tv code");
                            startLGTvStream(code);
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO Auto-generated method stub
                            if(error.networkResponse != null && error.networkResponse.statusCode == 404) {
                                createPopup("Invalid TV Code", "TV code not found. Make sure the app is open on your TV and that you entered the correct code. If he issue persists, restart the TV app.");
                            } else {
                                createPopup("Network Error", "Error getting TV code. Please make sure you are connected to the internet and try again later");
                            }
                        }
                    }
            );
            queue.add(getRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void startLGTvStream(String code) {
        try {
            //these cant be empty at this point
            EncryptClient encryptClient = new EncryptClient(context);
            String serverId = encryptClient.getValue("serverId");
            String gsToken = encryptClient.getValue("gsToken");
            String xcloudToken = encryptClient.getValue("xcloudToken");
            String msalToken = encryptClient.getValue("msalAccessToken");
            boolean didPurchase = RewardedAdLoader.getPurchaseItem(this.context);

            JSONObject tokensData = new JSONObject();
            tokensData.put("gsToken", gsToken);
            tokensData.put("serverId", serverId);
            tokensData.put("xCloudToken", xcloudToken);
            tokensData.put("msalToken", msalToken);
            tokensData.put("pcheck", didPurchase); // always pass pcheck when starting lg stream

            JSONObject body = new JSONObject();
            body.put("code", code);
            body.put("tokens", tokensData.toString());

            // save LG data
            String bodyString = body.toString();
            doSaveLGTvCode(bodyString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void doSaveLGTvCode(String data) {
        try {
            String url = WEBOS_TVCODE_BASE_URL;
            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest getRequest = new StringRequest(Request.Method.POST, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            Log.i("HERE", "Saved lg tv code");
                            createPopup("TV Found", "That's it! Your LG TV should begin streaming automatically. Please ensure your console and TV are on a 5GHz or wired connection.");
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO Auto-generated method stub
                            Toast.makeText(context, "Network error. TV app might not work..", Toast.LENGTH_LONG).show();
                        }
                    }
            ) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        return data.getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            };
            queue.add(getRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startTvStream(String url) {
        try {
            //these cant be empty at this point
            EncryptClient encryptClient = new EncryptClient(context);
            String serverId = encryptClient.getValue("serverId");
            String gsToken = encryptClient.getValue("gsToken");
            String xcloudToken = encryptClient.getValue("xcloudToken");
            String msalToken = encryptClient.getValue("msalAccessToken");

            String finalUrl = "http://" + url + "/startSession?gsToken=" + gsToken + "&serverId=" + serverId;
            if(!TextUtils.isEmpty(xcloudToken)){
                finalUrl += "&xcloudToken=" + xcloudToken;
            }
            if(!TextUtils.isEmpty(msalToken)){
                finalUrl += "&msalToken=" + msalToken;
            }
            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest getRequest = new StringRequest(Request.Method.GET, finalUrl,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            if(response.equals("ok")) {
                                createPopup("Success!", "Your TV should start streaming directly from your console now!\n\nIt is recommended that your TV use a wired connection or a 5GHz WiFi network at a minimum.\n\nPlease be aware that not all TVs are supported. You must have a high end Android TV (60FPS support and 2GB of RAM or more).\n\nThis is tested and working with a 'Chromecast with Google TV' device\n\nTip: If the video gets stuck on your TV for any reason, hold down the back button on your remote to refresh the video.");
                            } else {
                                createPopup("Error", "Error returned from TV. Please try again later: " + response);
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO Auto-generated method stub
                            createPopup("TV Not Found", "Error connecting to TV. Ensure the TV app is open on your TV and that its on the same WiFi network as your phone and console.");
                        }
                    }
            );
            queue.add(getRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setStreamConfig() {
        String config = getConfigSettings();
        callJavaScript(streamWebview, "setConfigData", config);
    }

    public String getConfigSettings(){
        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        EncryptClient encryptClient = new EncryptClient(this.context);
        JSONObject config = new JSONObject();

        String videoFit = prefs.getString("video_fit_key", "cover");
        // Boolean audioEnabledCast = prefs.getBoolean("audio", false); // only used for cast, TODO add default audio control for normal stream
        Integer videoOffset = prefs.getInt("video_vertical_offset_key", 50);

        // get performance params
        String emulateClient = prefs.getString("emulate_client_key", "windows");
        String controllerRefreshRate = prefs.getString("controller_refresh_key", "32");

        String maxBitrate = prefs.getString("max_bitrate_key", "");
//        String softResetOnLag = prefs.getString("soft_reset_on_lag_key", "");
//        String softResetOnInterval = prefs.getString("soft_reset_on_interval_key", "");
//        Boolean flashScreenOnSoftReset = prefs.getBoolean("flash_screen_on_soft_reset", false);
//        String clearBufferOnInterval = prefs.getString("clear_buffer_on_interval_key", "");

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

            if(this.isXcloud){ // add extra data for xCloud streaming
                String msalToken = encryptClient.getValue("msalAccessToken");
                String xcloudRegion = encryptClient.getValue("xcloudRegion");

                msalToken = msalToken.replace("\n", ""); // sketch, apparently base64 has newlines but json doesnt like them. Removing newlines, decodes fine still
                config.put("msalToken", msalToken);
                config.put("xcloudTitle", this.serverId);
                config.put("xcloudRegion", xcloudRegion);
            }
            config.put("video-fit", videoFit);
            config.put("video-vertical-offset", videoOffset);

            config.put("gsToken", this.gsToken);
            config.put("serverId", this.serverId);
            JSONObject customControllerMap = Helper.getActiveCustomPhysicalGamepadMappings(this.context);

            if(customControllerMap != null){
                config.put("physical-controller-button-mappings", Helper.getActiveCustomPhysicalGamepadMappings(this.context));
            }
            // config.put("audio", audioEnabledCast);
            // could set audio here too via disable-audio flag

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

            // add custom local
            String customLocal = prefs.getString("custom_local_key", "en-US");
            if(!customLocal.equals("en-US")){
                config.put("customLocal", customLocal);
            }

            // rumble
            config.put("rumble_controller", rumbleController);
            config.put("rumble_intensity", rumbleIntensity);

            boolean didPurchase = RewardedAdLoader.getPurchaseItem(this.context);
            config.put("platform", BuildConfig.APP_PLATFORM);
            config.put("render_engine", getRenderEngine(this.context));

            config.put("pcheck", didPurchase);
        } catch (Exception e) {}
        return config.toString();
    }

    public static void callJavaScript(WebView view, String methodName, Object...params){
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("javascript:try{");
        stringBuilder.append(methodName);
        stringBuilder.append("(");
        String separator = "";
        for (Object param : params) {
            stringBuilder.append(separator);
            separator = ",";
            if(param instanceof String){
                stringBuilder.append("'");
            }
            stringBuilder.append(param.toString().replace("'", "\\'"));
            if(param instanceof String){
                stringBuilder.append("'");
            }

        }
        stringBuilder.append(")}catch(error){console.error(error.message);}");
        final String call = stringBuilder.toString();
        Log.e("HERE", "callJavaScript: call="+call);
        view.loadUrl(call);
    }

    public void cleanUp() {
        loadedStreamView = false;
        try {
            if (streamWebview != null) {
                this.streamWebview.clearHistory();
                this.streamWebview.clearCache(false);
                // Loading a blank page is optional, but will ensure that the WebView isn't doing anything when you destroy it.
                this.streamWebview.loadUrl("about:blank");
                this.streamWebview.onPause();
                this.streamWebview.removeAllViews();
                this.streamWebview.destroy();
                this.streamWebview.cleanup();
                // Null out the reference so that you don't end up re-using it.
                this.streamWebview = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            this.streamWebview = null;
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

    public void createPopup(String title, String message) {
        try {
            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
