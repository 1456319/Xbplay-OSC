package com.studio08.xbgamestream;

import static com.studio08.xbgamestream.Authenticate.LoginClientV4.volleyPolicy;
import static com.studio08.xbgamestream.Helpers.Helper.getRenderEngine;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.ClipData;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.util.Rational;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.studio08.xbgamestream.Authenticate.LoginActivity;
import com.studio08.xbgamestream.Authenticate.LoginActivityV4;
import com.studio08.xbgamestream.Authenticate.LoginClientV4;
import com.studio08.xbgamestream.Controller.ControllerHandler;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.FirebaseAnalyticsClient;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.IAPPricesManager;
import com.studio08.xbgamestream.Helpers.PWASettingsActivity;
import com.studio08.xbgamestream.Helpers.PWAWebviewHandler;
import com.studio08.xbgamestream.Helpers.PurchaseClient;
import com.studio08.xbgamestream.Helpers.SettingsActivity;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.Servers.Server;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.GeckoWebviewClient;
import com.studio08.xbgamestream.Web.SmartglassClient;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.studio08.xbgamestream.Web.WifiClient;
import com.studio08.xbgamestream.databinding.ActivityFullscreenBinding;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.GeckoView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class PWAMainMenuActivity extends AppCompatActivity {

    private StreamWebview mSystemWebview;
    private GeckoView mGeckoview;
    private ConstraintLayout constraintLayout;
    private PWAWebviewHandler pwaWebviewHandler;

    private ActivityFullscreenBinding binding;
    private Server server;
    private PurchaseClient purchaseClient = null;
    private SmartglassClient smartglass = null;
    private boolean isInPip = false;
    private FirebaseAnalyticsClient analyticsClient;
    private PWAScreenCastClient pWAScreenCastClient;
    private int RELOAD_WEBVIEW_ON_SUCCESS_ACTIVITY_RESULT = 555;
    private boolean initializedViews = false;
    private WifiClient wifiClient;
    private boolean startedByShortcut = false;


    ApiClient.StreamingClientListener webviewEventListener = new ApiClient.StreamingClientListener() {
        @Override
        public void onReLoginDetected() {
//            Toast.makeText(getActivity(), "Re Login Required!", Toast.LENGTH_LONG).show();
//            promptUserForLogin();
        }
        // closing screen not supported in this view
        @Override
        public void onCloseScreenDetected() {}

        @Override
        public void pressButtonWifiRemote(String type) {}

        @Override
        public void setOrientationValue(String value) {}

        @Override
        public void vibrate() {
            Helper.vibrate(PWAMainMenuActivity.this);
        }

        @Override
        public void genericMessage(String type, String msg) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleGenericMessage(type, msg);
                }
            });
        }
    };

    private void handleGenericMessage(String type, String msg) {
        Log.e("PWAMM", "handleGenericMessage: " + type + msg);
        if (type.equals("pwa_show_login")){
            try {

                // save login region data
                EncryptClient encryptClient = new EncryptClient(getApplicationContext());
                if (!msg.isEmpty()){
                    encryptClient.saveValue("loginRegionIp", msg);
                } else {
                    encryptClient.deleteValue("loginRegionIp");
                }

                Intent intent = new Intent(PWAMainMenuActivity.this, LoginActivityV4.class);
                startActivityForResult(intent, RELOAD_WEBVIEW_ON_SUCCESS_ACTIVITY_RESULT);
            } catch (Exception e){
                e.printStackTrace();
            }
        } else if (type.equals("pwa_show_logout")){
            clearCache();
        } else if (type.equals("set_orientation")){
           setOrientationType(msg);
        } else if (type.equals("pwa_show_settings")){
            Intent intent = new Intent(PWAMainMenuActivity.this, PWASettingsActivity.class);
            startActivityForResult(intent, RELOAD_WEBVIEW_ON_SUCCESS_ACTIVITY_RESULT);
        } else if (type.equals("pwa_toggle_mirrorcast")){
            toggleMirrorCast(msg, false);
        } else if (type.equals("smartglass")){
            this.smartglass.sendSmartglassCommand(msg);
        } else if (type.equals("pwa_show_restore_purchase")){
            purchaseClient.queryPurchases();
        } else if (type.equals("pwa_show_iap")){
            if (msg.contains("yearly") || msg.contains("monthly")){
                purchaseClient.purchasesSubscription(msg);
            } else {
                purchaseClient.purchaseProduct(msg);
            }
        } else if (type.equals("open_link")){
            if (!msg.isEmpty()){
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(msg));
                startActivity(browserIntent);
            }
        } else if (type.equals("xalTokenUpdateRequest")){
            try {
                JSONObject response = new JSONObject(msg);
                JSONObject data = response.getJSONObject("data");

                Log.e("PWAMM", "xalTokenUpdateRequest: " + data);
                LoginClientV4.saveXalTokenData(data, PWAMainMenuActivity.this, null);

                // ensure we call setConfigData after token update on the next load to ensure tokens set from geckoview in android stream are used when returning
                pwaWebviewHandler.alreadyCalledPwaConfig = false;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        } else if (type.equals("pwaInitialLoadComplete")){
            // this is required to handle when the app is fully closed and we open it. This gets called after the webpage is loaded.
            handleXCloudShortcutStart(getIntent());
            handleDeepLinkRedirect(getIntent());
            handleXHomeShortcutStart(getIntent());
        } else if (type.equals("webviewPageLoadComplete")){
            if (msg.contains("#media_screen") && pWAScreenCastClient != null){
                pWAScreenCastClient.updateInfoText();
            }

            new Handler().postDelayed(() -> { // gross 1 second delay so we dont call this too fast (before page loaded)
                hideSystemUI(PWAMainMenuActivity.this);
            }, 1000);
        } else if (type.equals("pwa_prompt_for_shortcut_creation")){
            createShortcut(msg);
        } else if (type.equals("pwa_media_cast_action")) {
            if (msg.equals("selectDevice")){
                pWAScreenCastClient.init();
                pWAScreenCastClient.selectDevice();
            } else if (msg.equals("selectAudioFile")) {
                pWAScreenCastClient.init();
                pWAScreenCastClient.AUDIO_CAST_MODE = true;
                pWAScreenCastClient.selectFile();
            } else if (msg.equals("selectVideoFile")) {
                pWAScreenCastClient.init();
                pWAScreenCastClient.AUDIO_CAST_MODE = false;
                pWAScreenCastClient.selectFile();
            }  else if (msg.equals("doCast")) {
                if (pWAScreenCastClient.isPlaying){
                    pWAScreenCastClient.cleanUp();
                    Toast.makeText(PWAMainMenuActivity.this, "Stopping cast", Toast.LENGTH_SHORT).show();
                } else {
                    pWAScreenCastClient.init();
                    pWAScreenCastClient.castToConsole(true);
                }
            } else {
                Log.e("PWAMM", "Invalid pwa_media_cast_action msg sent" + msg);
            }
        } else if (type.equals("pwa_media_cast_button_press_action")){
            pWAScreenCastClient.sendCastRemoteCommand(msg);
        } else if (type.equals("pwa_exit_main_menu")) {
            if(!getRenderEngine(PWAMainMenuActivity.this).equals("webview")){
                pwaWebviewHandler.handleRenderEngineSwitch(msg);
            }
        } else if (type.equals("pwa_return_main_menu")) {
            if(!getRenderEngine(PWAMainMenuActivity.this).equals("webview")){
                pwaWebviewHandler.handleRenderEngineReturn(Objects.equals(msg, "true"));
            }
        } else if (type.equals("quitGameUseForShortcuts")){
            if(this.startedByShortcut){
                Log.e("PWAMM","Handle QuitGame");
                finishAffinity();
            }
        } else {
            Log.e("PWAMM","Invalid PWA generic message type: " + type);
        }
    }

    private void setupGoogleAnalytics(){
        try {
            FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
            analyticsClient = new FirebaseAnalyticsClient(mFirebaseAnalytics);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    private void toggleMirrorCast(String msg, boolean isRetry){
        try {
            Log.e("PWAMM", "toggleMirrorCast");
            JSONObject data = new JSONObject(msg);
            String serverId = data.getString("serverId");
            String gsToken = data.getString("gsToken");
            Log.e("PWAMM", data.toString());

            if(server != null) {
                addMirrorCastLog("", "Stopped");
                server.stop();
                server = null;
            } else {
                // if we are retrying use a random port (the retry is due to port in use)
                server = new Server((!isRetry) ? Server.PORT : 0, PWAMainMenuActivity.this, gsToken, serverId);
                server.start();

                // add initial log
                addMirrorCastLog("Running on http://" + Helper.getLocalIpAddress() + ":" + server.getListeningPort(), "Started http://" + Helper.getLocalIpAddress() + ":" + server.getListeningPort());
            }
        } catch (Error | IOException | JSONException e) {
            e.printStackTrace();

            if (!isRetry){
                if (server != null) {
                    server.stop();
                    server = null;
                }
                toggleMirrorCast(msg, true);
            } else {
                Toast.makeText(PWAMainMenuActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void addMirrorCastLog(String status, String connection){
//        streamingClient.setMirrorCastMessage(status, connection);  123
        ApiClient.callJavaScript(mSystemWebview, "setMirrorcastData", status, connection);
    }

    private void setOrientationType(String msg){
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean lockOrientation = prefs.getBoolean("lock_orientation_key", true);

        if(!lockOrientation) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }  else if (msg.equals("landscape")){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else if (msg.equals("portrait")){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
        } else if (msg.equals("unlock")){
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        } else {
            Log.e("PWAMM", "Invalid orientation value: " + msg);
        }
    }
    private void clearCache(){
        Toast.makeText(PWAMainMenuActivity.this, "Clearing cache", Toast.LENGTH_LONG).show();
        WebStorage.getInstance().deleteAllData();

        // Clear all the cookies
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();

        EncryptClient encryptClient = new EncryptClient(getApplicationContext());
        encryptClient.deleteAll();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.e("PWAMM", "onCreate");

        this.configureTheme();
        super.onCreate(savedInstanceState);
        if (!initializedViews){
            handleLegacyTheme();
            getProductPrices();
        }

        binding = ActivityFullscreenBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        smartglass = new SmartglassClient(getApplicationContext());

        if(!initializedViews){
            setupWebviews();
            purchaseClient = new PurchaseClient(PWAMainMenuActivity.this, mSystemWebview);
            loadPwaMainMenu();
            Helper.checkIfUpdateAvailable(PWAMainMenuActivity.this);
            setupGoogleAnalytics();
            if (pWAScreenCastClient != null){
                pWAScreenCastClient.cleanUp();
            }

            Helper.showRatingApiMaybe(PWAMainMenuActivity.this);

            pWAScreenCastClient = new PWAScreenCastClient(PWAMainMenuActivity.this, mSystemWebview);

            handleWifiLowLatencyMode();
            handleAudioLowLatencyMode();

        }

        hideSystemUI(PWAMainMenuActivity.this);
        initializedViews = true;
    }

    private void handleAudioLowLatencyMode() {
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean useLowLatency = prefs.getBoolean("use_audio_low_latency_mode_key", true);
        if (useLowLatency) {
            enableMinimalPostProcessing();
        }
    }

    private void handleWifiLowLatencyMode() {
        wifiClient = new WifiClient(PWAMainMenuActivity.this);
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean useLowLatency = prefs.getBoolean("use_wifi_low_latency_mode_key", true);
        if (useLowLatency) {
            wifiClient.acquireWifiLock();
        }
    }

    private void getProductPrices(){
        new IAPPricesManager(PWAMainMenuActivity.this);
    }
    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();

        if (pwaWebviewHandler != null && pwaWebviewHandler.getCurrentUrl() != null && pwaWebviewHandler.getCurrentUrl().contains("android_stream")) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
                    Rational ratio = new Rational(16, 9);
                    PictureInPictureParams.Builder
                            pip_Builder
                            = new PictureInPictureParams
                            .Builder();
                    pip_Builder.setAspectRatio(ratio).build();
                    enterPictureInPictureMode(pip_Builder.build());
                }
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        if (isInPictureInPictureMode) {
            Log.d("PiPMode", "Entered PiP mode");
            pwaWebviewHandler.togglePip(true);
            isInPip = true;
        } else {
            // Handle UI restoration when exiting PiP
            Log.d("PiPMode", "Exited PiP mode");
            pwaWebviewHandler.togglePip(false);
            isInPip = false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (pwaWebviewHandler != null){
            pwaWebviewHandler.cleanUpBeforeDestroy();
        } if (pWAScreenCastClient != null){
            pWAScreenCastClient.cleanUp();
        }
    }


    @Override
    protected void onStop() {
        super.onStop();
        if (isInPip) {
            finishAffinity();
            return;
        }
        if (wifiClient != null){
            wifiClient.releaseWifiLock();
        }
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(PWAMainMenuActivity.this)
            .setTitle("Exit")
            .setMessage("Are you sure you want to exit?")
            .setCancelable(true)
            .setPositiveButton("Exit App", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    finishAffinity();
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                }
            })
            .show();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState){
        super.onSaveInstanceState(outState);
        if (mSystemWebview != null) {
            mSystemWebview.saveState(outState);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // handle returning from chrome, unlock orientation
        if (getRenderEngine(PWAMainMenuActivity.this).equals("chrome")){
            setOrientationType("unlock");
        }

        // re-acquire wake lock on restart
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean useLowLatency = prefs.getBoolean("use_wifi_low_latency_mode_key", true);
        if (useLowLatency) {
            wifiClient.acquireWifiLock();
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState){
        super.onRestoreInstanceState(savedInstanceState);
        if (mSystemWebview != null){
            mSystemWebview.restoreState(savedInstanceState);
        }
    }

    private void handleLegacyTheme(){
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean useLegacyTheme = prefs.getBoolean("pwa_use_legacy_theme_key", false);
        if (useLegacyTheme){
            Intent currentIntent = getIntent(); // Get the current intent

            Intent nextActivityIntent = new Intent(PWAMainMenuActivity.this, MainActivity.class);

            // get all tons of data
            String action = currentIntent.getAction();
            if (action != null) {
                nextActivityIntent.setAction(action);
            }
            Uri data = currentIntent.getData();
            if (data != null) {
                nextActivityIntent.setData(data);
            }
            String type = currentIntent.getType();
            if (type != null) {
                nextActivityIntent.setType(type);
            }
            Set<String> categories = currentIntent.getCategories();
            if (categories != null) {
                for (String category : categories) {
                    nextActivityIntent.addCategory(category);
                }
            }
            int flags = currentIntent.getFlags();
            nextActivityIntent.setFlags(flags);
            Bundle extras = currentIntent.getExtras();
            if (extras != null) {
                nextActivityIntent.putExtras(extras); // Add all the extras
            }
            ClipData clipData = currentIntent.getClipData();
            if (clipData != null) {
                nextActivityIntent.setClipData(clipData);
            }
            startActivity(nextActivityIntent);
            finish();
            return;
        }

        // check if user previously used the legacy theme and if so, show an upgrade dialog
        SharedPreferences ratePrefs = getSharedPreferences("rate", MODE_PRIVATE);
        int appOpenCounter = ratePrefs.getInt("appOpens", 0);
        boolean alreadyShowedPopup = ratePrefs.getBoolean("alreadyShowedPopup", false);

        if(!alreadyShowedPopup && appOpenCounter > 0){
            SharedPreferences.Editor editor = ratePrefs.edit();
            editor.putBoolean("alreadyShowedPopup", true);
            editor.apply();

            new AlertDialog.Builder(this)
                .setTitle("New Theme")
                .setMessage("Welcome to the new app theme! If you prefer the old theme, you can revert to the legacy theme in the settings.")
                .setCancelable(false)
                .setPositiveButton("Ok", null)
                .show();
        }
    }

    public void loadPwaMainMenu(){
        pwaWebviewHandler.doPwaMainMenu();
        mSystemWebview.requestFocus();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != RESULT_OK) {
            return;
        }

        if (requestCode == RELOAD_WEBVIEW_ON_SUCCESS_ACTIVITY_RESULT) { // login or settings request
            pwaWebviewHandler.setRenderEngineDependentListeners(); // if render engine has changed, re-create listeners for correct listeners
            ApiClient.callJavaScript(mSystemWebview, "setPWAConfigData", PWAWebviewHandler.getPwaConfigSettings(PWAMainMenuActivity.this));
        }
        // todo: remove this once I update to media picker for audio
        else if (requestCode == PWAScreenCastClient.PICKFILE_RESULT_CODE) {
            pWAScreenCastClient.handleFileSelectCallback(data);
        }
    }

    // should only be called if shortcuts are created
    private void createShortcut(String msg){
        try {
            // this shouldn't be possible
            if (!ShortcutManagerCompat.isRequestPinShortcutSupported(PWAMainMenuActivity.this)){
                return;
            }

            JSONObject results = new JSONObject(msg);
            String type = results.getString("type");
            if (type.equals("xcloud")){
                String titleId = results.getString("titleId");
                String iconUrl = results.getString("image");
                String friendlyName =  results.getString("title");
                Log.e("PWAMM", results.toString());
                Helper.addShortcutToHomeScreen(PWAMainMenuActivity.this, titleId, friendlyName, iconUrl, type);
            } else if (type.equals("xhome")){
                String titleId = results.getString("titleId");
                String iconUrl = results.getString("image");
                String friendlyName =  results.getString("title");
                Log.e("PWAMM", results.toString());
                Helper.addShortcutToHomeScreen(PWAMainMenuActivity.this, titleId, friendlyName, iconUrl, type);
            }
        } catch (Exception e){
            e.printStackTrace();
            Log.e("HERE", "Failed to decode json message");
        }
    }

    private void handleXCloudShortcutStart(Intent intent){
        if (intent == null){
            return;
        }

        String action = intent.getAction();
        String xCloudTitle = intent.getStringExtra("titleId");

        Log.e("PWAMM", "handleXCloudShortcutStart: " + action + " " + xCloudTitle);

        if (!TextUtils.isEmpty(xCloudTitle) && action != null && !TextUtils.isEmpty(action) && action.equals("xcloudstart")){
            Toast.makeText(this, "Launching: " + xCloudTitle, Toast.LENGTH_SHORT).show();
            this.startedByShortcut = true;
            setIntent(new Intent()); // override so not called twice

            runOnUiThread(() -> ApiClient.callJavaScript(mSystemWebview, "redirectToCloudPlay", xCloudTitle));
        }
    }

    private void handleXHomeShortcutStart(Intent intent){
        if (intent == null){
            return;
        }

        String action = intent.getAction();
        String title = intent.getStringExtra("titleId");

        Log.e("PWAMM", "handleXHomeShortcutStart: " + action + " " + title);

        if (!TextUtils.isEmpty(title) && action != null && !TextUtils.isEmpty(action) && action.equals("xhomestart")){
            Toast.makeText(this, "Launching: " + title, Toast.LENGTH_SHORT).show();
            this.startedByShortcut = true;
            setIntent(new Intent()); // override so not called twice

            runOnUiThread(() -> ApiClient.callJavaScript(mSystemWebview, "redirectToRemotePlay", "false","false", title));
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void handleDeepLinkRedirect(Intent intent){
        Uri uri = intent.getData();
        if (uri != null) {
            List<String> parameters = uri.getPathSegments();
            Log.e("LaunchIntent", "Params: " + parameters + uri);
            if(uri.getHost().equals("launch")){
                String launchActivity = parameters.get(0);

                switch(launchActivity){
                    case "xhome":
                    case "xcloud":
                        runOnUiThread(() -> ApiClient.callJavaScript(mSystemWebview, "showScreen", "play_screen"));
                        setIntent(new Intent());
                        break;
                    case "controller":
                        runOnUiThread(() -> ApiClient.callJavaScript(mSystemWebview, "showScreen", "controller_screen"));
                        setIntent(new Intent());
                        break;
                    case "tv":
                        if(parameters.size() >= 2){
                            String tvCodeValue = parameters.get(1);
                            Toast.makeText(this, "TV CODE: " + tvCodeValue, Toast.LENGTH_SHORT).show();
                            new Handler().postDelayed(() -> { // gross 1 second delay so we dont call this too fast (before page loaded)
                                runOnUiThread(() -> ApiClient.callJavaScript(mSystemWebview, "setTvCodeFromDeepLink", tvCodeValue));
                            }, 1000);

                            setIntent(new Intent());
                        }
                        break;
                    default:
                        Log.e("LaunchIntent", "Unknown launch activity: " + launchActivity);
                        break;
                }
            }
        }
    }

    public void setupWebviews(){
        constraintLayout = binding.constrainedLayout;

        // Initialize GeckoView (not yet added)
        mGeckoview = new GeckoView(this);
        mGeckoview.setId(View.generateViewId());

        // init system webview
        mSystemWebview = binding.systemwebview;
        mSystemWebview.setBackgroundColor(Color.TRANSPARENT);

        // load views and set listeners
        pwaWebviewHandler = new PWAWebviewHandler(PWAMainMenuActivity.this, mSystemWebview, mGeckoview, webviewEventListener, constraintLayout);
        pwaWebviewHandler.initWebviews();
    }

    public void configureTheme(){
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // set theme (for notch) before setting content view
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean shouldUseNotch = prefs.getBoolean("use_notch_key", true);
        if(!shouldUseNotch) {
            setTheme(R.style.Theme_MyApplication3);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.e("PWAMM", "handle new intent" + intent.getAction());
        handleXCloudShortcutStart(intent);
        handleDeepLinkRedirect(intent);
        handleXHomeShortcutStart(intent);
    }

    // call after loading views
    public static void hideSystemUI(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            View decorView = ((Activity)context).getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            // Set the content to appear under the system bars so that the
                            // content doesn't resize when the system bars hide and show.
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            // Hide the nav bar and status bar
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            WindowInsetsControllerCompat windowInsetsController =
                    ViewCompat.getWindowInsetsController(((Activity)context).getWindow().getDecorView());
            if (windowInsetsController == null) {
                return;
            }
            // Configure the behavior of the hidden system bars
            windowInsetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
            // Hide both the status bar and the navigation bar
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

            SharedPreferences prefs = ((Activity)context).getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            boolean shouldUseNotch = prefs.getBoolean("use_notch_key", true);
            if(shouldUseNotch) {
                ((Activity)context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                ((Activity)context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                ((Activity)context).getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

        }
    }

    public void enableMinimalPostProcessing() {
        try {
            // Ensure the API level supports this feature
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // add flag to window
                getWindow().addFlags(ActivityInfo.FLAG_PREFER_MINIMAL_POST_PROCESSING);
            } else {
                Log.w("PWAMM", "Minimal post-processing requires Android 11 or higher.");
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

}