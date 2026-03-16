package com.studio08.xbgamestream.Web;

import static com.studio08.xbgamestream.Helpers.Helper.checkIfAlreadyHavePermission;
import static com.studio08.xbgamestream.Helpers.Helper.requestForSpecificPermission;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.Helpers.SettingsFragment;

import java.util.Locale;

// events that we can listen for. Emits the re login event

public class StreamWebview extends WebView {
    public StreamWebviewListener listener;
    private Context context = null;
    private CustomWebClient webClient;
    public Boolean showLoadingDialog = true;
    public Boolean captureLogs = false;

    public StreamWebview(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public StreamWebview(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        this.context = context;
    }

    public StreamWebview(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        this.context = context;
    }

    public StreamWebview(@NonNull Context context) {
        super(context);
        this.context = context;
    }

    // Assign the listener implementing events interface that will receive the events
    public void setCustomObjectListener(StreamWebviewListener listener) {
        this.listener = listener;
    }

    public void init() {
        // determine if we should capture logs (once)
        resetLogs();
        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean captureLogsSaved = prefs.getBoolean("capture_debug_logs_gameplay_key", false);
        if(captureLogsSaved){
            captureLogs = true;
        }

        WebSettings settings = this.getSettings();
        settings.setJavaScriptEnabled(true);
        // Use WideViewport and Zoom out if there is no viewport defined
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        // Enable pinch to zoom without the zoom buttons
        settings.setBuiltInZoomControls(false);
        // Allow use of Local Storage
        settings.setAllowFileAccess(true);
        settings.setDatabaseEnabled(true);

        settings.setDomStorageEnabled(true);
        settings.setSupportMultipleWindows(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
//      settings.setRenderPriority(WebSettings.RenderPriority.HIGH);
//        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
//      settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptThirdPartyCookies(this, true);
        cookieManager.setAcceptCookie(true);

        if(BuildConfig.BUILD_TYPE.equals("debug")){
            this.setWebContentsDebuggingEnabled(true);
        }
        // log chrome errors
        this.setWebChromeClient(new CustomWebChromeClient());

        // listen for JS events
        this.addJavascriptInterface(new WebAppInterface(this.context), "Android");

        webClient = new CustomWebClient(true);
        this.setWebViewClient(webClient);

        // disable long click listener
        this.setLongClickable(false);
        this.setOnLongClickListener(new OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;
            }
        });
    }
    public void disableLoadingDialog(){
        showLoadingDialog = false;
        webClient = new CustomWebClient(false);
        this.setWebViewClient(webClient);
    }

    class CustomWebChromeClient extends WebChromeClient {
        @Override
        public boolean onConsoleMessage(ConsoleMessage cm) {
            if(captureLogs){
                Log.e("CONSOLE", String.format("%s @ %d: %s", cm.message(), cm.lineNumber(), cm.sourceId()));
                appendLogs(String.format(Locale.ENGLISH, "%s @ %d: %s", cm.message(), cm.lineNumber(), cm.sourceId()));
            }
            return true;
        }
        @Override
        public void onPermissionRequest(PermissionRequest request) {
            ((Activity)context).runOnUiThread(() -> {

                // always assuming any permission requests will be audio requests
                if (checkIfAlreadyHavePermission(Manifest.permission.RECORD_AUDIO, context)) {
                    Log.e("HERE", "Already have audio perm");
                } else {
                    Toast.makeText(context, "Grant Permissions and Retry", Toast.LENGTH_SHORT).show();
                    requestForSpecificPermission(new String[] {Manifest.permission.RECORD_AUDIO}, context);
                }
                String[] PERMISSIONS = {
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                };
                request.grant(PERMISSIONS);
            });
        }
    }

    // HELPERS
    private void appendLogs(String data){
        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        String currentData = prefs.getString("gameplay_logs", "");
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("gameplay_logs", currentData + "\n" + data);
        editor.apply();
    }
    private void resetLogs(){
        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean captureLogs = prefs.getBoolean("capture_debug_logs_gameplay_key", false);

        if(!captureLogs){
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("gameplay_logs", "");
        editor.apply();
    }

    // hides the dialog
    public void cleanup(){
        if(this.webClient != null){
            this.webClient.cleanup();
        }
    }

    // listen to JS method calls
    public class WebAppInterface {
        Context mContext;

        /** Instantiate the interface and set the context */
        WebAppInterface(Context c) {
            mContext = c;
        }

        /** Show a toast from the web page */
        @JavascriptInterface
        public void showToast(String toast) {
            Toast.makeText(mContext, toast, Toast.LENGTH_LONG).show();
        }

        @JavascriptInterface
        public void reLoginRequest() {
            listener.onReLoginRequest();
        }

        @JavascriptInterface
        public void closeScreen() {
            listener.closeScreen();
        }

        @JavascriptInterface
        public void pressButtonWifiRemote(String type, int value) {
            Log.e("StreamWebview", "Caught pressButtonWifiRemote");
            listener.pressButtonWifiRemote(type);
        }

        @JavascriptInterface
        public void setOrientationValue(String value) {
            listener.setOrientationValue(value);
        }

        @JavascriptInterface
        public void vibrate() {
            listener.vibrate();
        }

        @JavascriptInterface
        public String genericMessage(String type, String msg) {
            listener.genericMessage(type, msg);

            //handle system wide version check
            if(type.equals("version_code")){
                Log.e("HERE", "Got version code: " + BuildConfig.VERSION_NAME);
                return BuildConfig.VERSION_NAME;
            }
            return null;
        }
    }

}
