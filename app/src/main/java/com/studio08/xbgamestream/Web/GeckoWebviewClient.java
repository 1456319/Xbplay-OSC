package com.studio08.xbgamestream.Web;

import static com.studio08.xbgamestream.Helpers.Helper.checkIfAlreadyHavePermission;
import static com.studio08.xbgamestream.Helpers.Helper.requestForSpecificPermission;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.Helpers.PWAWebviewHandler;
import com.studio08.xbgamestream.PWAMainMenuActivity;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

public class GeckoWebviewClient {
    Context context;
    GeckoView geckoView;
    WebExtension.Port mPort;
    GeckoSession session;
    GeckoRuntime runtime;
    StreamWebviewListener listener;
    ApiClient apiClient;
    AlertDialog dialog;
    boolean isPWA = false;
    public JSONObject pwaConfigData;
    private String lastUrlStarted;


    class MyNavigationDelegate implements GeckoSession.NavigationDelegate {
        @Override
        public GeckoResult<AllowOrDeny> onLoadRequest(final GeckoSession session, final LoadRequest request) {
            Log.d(
                    "tag",
                    "onLoadRequest=" + request.uri + " triggerUri=" + request.triggerUri + " where=" + request.target + " isRedirect=" + request.isRedirect + " isDirectNavigation=" + request.isDirectNavigation);

            return GeckoResult.allow();
        }
    }

    private class PermissionDelegate implements GeckoSession.PermissionDelegate {
        @Override
        public void onAndroidPermissionsRequest(final GeckoSession session,
                                                final String[] permissions,
                                                final Callback callback) {
            Log.e("TAG", "onAndroidPermissionsRequest");
            callback.grant();
        }

        @Override
        public void onMediaPermissionRequest(@NonNull GeckoSession session, @NonNull String uri, @Nullable MediaSource[] video, @Nullable MediaSource[] audio, @NonNull MediaCallback callback) {
            Log.e("TAG", "onMediaPermissionRequest");

            if (audio != null) {
                if (checkIfAlreadyHavePermission(Manifest.permission.RECORD_AUDIO, context)) {
                    Log.e("HERE", "Already have audio perm");
                } else {
                    Toast.makeText(context, "Grant Permissions and Retry", Toast.LENGTH_SHORT).show();
                    requestForSpecificPermission(new String[]{Manifest.permission.RECORD_AUDIO}, context);
                }

                for (int i = 0; i < audio.length; i++) {
                    Log.e("TAG", "Granting audio: " + audio[i].name + " : " + audio[i].id);
                    callback.grant(null, audio[i]);
                }
            }
        }

        @Nullable
        @Override
        public GeckoResult<Integer> onContentPermissionRequest(@NonNull GeckoSession session, @NonNull ContentPermission perm) {
            Log.e("TAG", "onContentPermissionRequest " + perm + perm.permission);
            return GeckoResult.fromValue(ContentPermission.VALUE_ALLOW);
        }
    }

//    WebExtension.MessageDelegate messageDelegate = new WebExtension.MessageDelegate() {
//        @Nullable
//        public GeckoResult<Object> onMessage(final @NonNull String nativeApp,
//                                             final @NonNull Object message,
//                                             final @NonNull WebExtension.MessageSender sender) {
//            // The sender object contains information about the session that
//            // originated this message and can be used to validate that the message
//            // has been sent from the expected location.
//
//            // Be careful when handling the type of message as it depends on what
//            // type of object was sent from the WebExtension script.
//            if (message instanceof JSONObject) {
//                // Do something with message
//            }
//            Log.e("HERE","GOT MESSAGE FROM FF! " + message.toString());
//            return null;
//        }
//    };

    WebExtension.PortDelegate portDelegate = new WebExtension.PortDelegate() {

        public void onPortMessage(final @NonNull Object message, final @NonNull WebExtension.Port port) {
            Log.e("GeckoView", "onPortMessage: " + message);
            if (message instanceof String) {
                // Do something with message
                try {
                    JSONObject messageObject = new JSONObject((String) message);
                    String type = messageObject.getString("type");
                    JSONObject data = messageObject.getJSONObject("data");

                    Log.e("GeckoView", "Received GeckoView Event from fronted of type: " + type);

                    // not supporting all possible messages at this time since its not possible to send nav remote commands from geckoview
                    if (type.equals("relogin")){
                        listener.onReLoginRequest();
                    } else if (type.equals("start_xcloud_stream")){
                        listener.genericMessage(type, data.getString("titleId"));
                    } else if(type.equals("vibrate")){
                        listener.vibrate();
                    } else if (type.equals("set_orientation")){
                        listener.setOrientationValue(data.getString("message"));
                    } else if (type.equals("generic")){
                       listener.genericMessage(data.getString("generic_type"), data.getString("message"));
                    } else {
                        Log.e("GVWC", "Invalid geckoview event type: " + type);
                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            } else {
                Log.e("TAG", "Geckoview port message not an object");
            }
        }

        public void onDisconnect(final @NonNull WebExtension.Port port) {
            Log.e("GeckoView", "onDisconnect");
            if (port == mPort) {
                mPort = null;
            }
        }
    };

    WebExtension.MessageDelegate messageDelegate = new WebExtension.MessageDelegate() {
        @Nullable
        public void onConnect(final @NonNull WebExtension.Port port) {
            Log.e("GeckoView", "onConnect. Sending config data");
            mPort = port;
            mPort.setDelegate(portDelegate);
            setConfig();
        }
    };

    GeckoSession.ProgressDelegate progressDelegate = new GeckoSession.ProgressDelegate() {

        @Override
        public void onPageStart(@NonNull @NotNull GeckoSession session, @NonNull @NotNull String url) {
            Log.e("GeckoView", "OnPageStarted " + url);
            try {
                lastUrlStarted = url;
                setProgressDialog(context);
            } catch (Exception e) {}
        }

        @Override
        public void onPageStop(@NonNull @NotNull GeckoSession session, boolean success) {
            Log.e("GeckoView", "OnPageStopped " + success);
            try {
                hideDialog();
            } catch (Exception e) {}
            if(!success){
                try {
//                    loadUrl("resource://android/assets/warning-screen.html"); // this will explode?
                    Toast.makeText(context, "Error load page. Ensure you are connected to the internet and try again.", Toast.LENGTH_LONG).show();

                } catch (Exception e){
                    e.printStackTrace();
                }
            }

        }
    };

    public GeckoWebviewClient(Context ctx, GeckoView gv, ApiClient client) {
        context = ctx;
        geckoView = gv;
        apiClient = client;
        init();
    }

    public GeckoWebviewClient(Context ctx, GeckoView gv, boolean isPWA) {
        context = ctx;
        geckoView = gv;
        init();
        this.isPWA = isPWA;
    }

    // Assign the listener implementing events interface that will receive the events
    public void setCustomObjectListener(StreamWebviewListener listener) {
        this.listener = listener;
    }

    public void init(){
        if (runtime == null) {
            runtime = GeckoRuntime.getDefault(context);
        }

        if (BuildConfig.BUILD_TYPE.equals("debug")) {
            runtime.getSettings().setRemoteDebuggingEnabled(true);
            runtime.getSettings().setConsoleOutputEnabled(true);
            runtime.getSettings().setAboutConfigEnabled(true);
        }

        GeckoSessionSettings settings = new GeckoSessionSettings.Builder()
//                .usePrivateMode(true)
                .useTrackingProtection(true)
                .suspendMediaWhenInactive(true)
                .allowJavascript(true)
                .build();

        session = new GeckoSession(settings);
        session.setPermissionDelegate(new PermissionDelegate());
        session.setPromptDelegate(new XBPlayGeckoPromptDelegate((Activity) context));
        session.setProgressDelegate(progressDelegate);
        session.setNavigationDelegate(new MyNavigationDelegate());


//         Let's make sure the extension is installed
        runtime.getWebExtensionController()
                .ensureBuiltIn("resource://android/assets/messaging/", "browser@xbgamestreamplay.com").accept(
                // Set delegate that will receive messages coming from this extension.
                extension -> {
                    if(session != null) {
                        session.getWebExtensionController().setMessageDelegate(extension, messageDelegate, "browser");
                    }
                },
                // Something bad happened, let's log an error
                e -> Log.e("MessageDelegate", "Error registering extension", e)
        );

        session.open(runtime);
        geckoView.setSession(session);
    }

    public String getCurrentUrl(){
       return lastUrlStarted;
    }

    public void sendControllerInput(JSONObject data){
        if (mPort == null) {
            Log.e("GeckoClient", "mPort NULL");
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("data", data);
            message.put("type", "setControllerInput");
            mPort.postMessage(message);
        } catch (JSONException ex) {
            Log.e("GeckoClient", "Failed to parse config data! Explode!");
            ex.printStackTrace();
        }
    }

    public void togglePip(boolean enabled){
        if (mPort == null) {
            Log.e("GeckoClient", "mPort NULL");
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("data", enabled);
            message.put("type", "togglePip");
            mPort.postMessage(message);
        } catch (JSONException ex) {
            Log.e("GeckoClient", "Failed to parse togglePip data! Explode!");
            ex.printStackTrace();
        }
    }

    public void sendMouseInput(JSONObject data){
        if (mPort == null) {
            Log.e("GeckoClient", "mPort NULL");
            return;
        }
        try {
            JSONObject message = new JSONObject();
            message.put("data", data);
            message.put("type", "setMousePayload");
            mPort.postMessage(message);
        } catch (JSONException ex) {
            Log.e("GeckoClient", "Failed to parse mouse data! Explode!");
            ex.printStackTrace();
        }
    }

    private void setConfig() {
        if (mPort == null) {
            Log.e("GeckoClient", "mPort NULL");
            return;
        }
        try {
            JSONObject message = new JSONObject();

            if (isPWA){
                message.put("data", pwaConfigData);
                message.put("type", "setConfig");
            } else {
                JSONObject config = new JSONObject(apiClient.getConfigSettings());
                message.put("data", config);
                message.put("type", "setConfig");
            }

            mPort.postMessage(message);
        } catch (JSONException ex) {
            Log.e("GeckoClient", "Failed to parse config data! Explode!");
            ex.printStackTrace();
        }
    }
    public void loadUrl(String url){
        session.loadUri(url);
    }
    public void destroy(){
        if (runtime != null){
            runtime = null;
        }
        if (session != null){
            session.stop();
            session.close();
            session = null;
        }
        if (geckoView != null){
            geckoView.destroyDrawingCache();
            geckoView = null;
        }

        hideDialog();
    }

    public void setProgressDialog(Context context) {
        int llPadding = 10;
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setPadding(llPadding, llPadding, llPadding, llPadding);
        ll.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llParam = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        ll.setLayoutParams(llParam);

        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        progressBar.setPadding(0, 0, 0, 0);
        progressBar.setLayoutParams(llParam);
        progressBar.setVisibility(View.VISIBLE);

        llParam = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        TextView tvText = new TextView(context);
        tvText.setText("Please Wait...");
        tvText.setTextColor(Color.parseColor("#FFFFFF"));
        tvText.setTextSize(20);
        tvText.setLayoutParams(llParam);

        ll.addView(progressBar);
        //ll.addView(tvText);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setView(ll);

        hideDialog();
        this.dialog = builder.create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(layoutParams);
        }
    }
    private void hideDialog() {
        try {
            dialog.dismiss();
        } catch (Exception e) {}
        try {
            PWAMainMenuActivity.hideSystemUI(context);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
