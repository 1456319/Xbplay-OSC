package com.studio08.xbgamestream.Authenticate;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.studio08.xbgamestream.Web.StreamWebviewListener;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class LoginClientV2 {
    public interface LoginClientListener {
        void onLoginComplete(String consoles);
        void statusMessage(String message);
        void hideDialog();
        void showDialog();
        void genericMessage(String type, String message);
    }
    private Context context;
    private StreamWebview loginWebview = null;
    LoginClientV2.LoginClientListener listener = null;
    private String LOGIN_URL = "https://account.xbox.com/account/signin?returnUrl=https%3A%2F%2Fwww.xbox.com%2Fen-US%2Fplay&ru=https%3A%2F%2Fwww.xbox.com%2Fen-US%2Fplay";
    private String LOGIN_COMPLETE_URL= "https://www.xbox.com/en-US/play";
    private String AUTH_COOKIE_KEY = "XBXXtkhttp://xboxlive.com";
    private String STREAM_COOKIE_KEY = "XBXXtkhttp://gssv.xboxlive.com";
    private String LOGIN_ENDPOINT = "https://xhome.gssv-play-prod.xboxlive.com/v2/login/user"; // used to get GS token
    private String XCLOUD_LOGIN_ENDPOINT = "https://xgpuweb.gssv-play-prod.xboxlive.com/v2/login/user"; // used to get xcloud token

    private String LIST_CONSOLES_ENDPOINT = "https://uks.gssv-play-prodxhome.xboxlive.com/v6/servers/home";
    private String SIGN_IN_USER_PAGE = "login.live.com";
    private EncryptClient encryptClient = null;

    private TokenStatus gsTokenStatus =  new TokenStatus();
    private TokenStatus consoleStatus = new TokenStatus();
    private TokenStatus xCloudTokenStatus = new TokenStatus();
    private TokenStatus msalTokenStatus = new TokenStatus();
    private int retryAttempts = 0;
    class TokenStatus {
        public Boolean waiting = true;
        public Boolean completed = false;
        public Boolean failed = false;
        public void setCompleted(){
            completed = true;
            failed = false;
            waiting = false;
            emitLoginCompleteIfReady();
        }
        public void setFailed(){
            completed = false;
            failed = true;
            waiting = false;
            emitLoginCompleteIfReady();
        }
        public String getAllStatuses(){
            return "completed: " + completed + " failed: " + failed + " waiting: " + waiting + "|| ";
        }
        public String getStatus() {
            if(waiting){
                return "waiting";
            } else if (failed) {
                return "failed";
            } else if (completed){
                return "complete";
            } else {
                return "unknown";
            }
        }
    };

    public LoginClientV2(Context context, StreamWebview webview) {
        this.context = context;
        this.listener = null;
        this.loginWebview = webview;
        this.encryptClient = new EncryptClient(this.context);
        setupWebviewListeners();
        resetLogs();
    }

    // Assign the listener implementing events interface that will receive the events
    public void setCustomObjectListener(LoginClientV2.LoginClientListener listener) {
        this.listener = listener;
    }

    public void doLogin() {
        appendLogs("Starting doLogin");
        loginWebview.loadUrl(LOGIN_URL);
    }

    private void setupWebviewListeners() {
        loginWebview.setCustomObjectListener(new StreamWebviewListener() {
            @Override
            public void onReLoginRequest() {}

            @Override
            public void closeScreen() {}

            @Override
            public void pressButtonWifiRemote(String type) {}

            @Override
            public void setOrientationValue(String value) {}

            @Override
            public void vibrate() {}

            @Override
            public void genericMessage(String type, String msg) {
                if(type.equals("msalTokenSet")){
                    appendLogs("Found MSAL Token: " + msg);
                    // base 64 encode the access token so it works in the URL
                    byte[] data = msg.getBytes(StandardCharsets.UTF_8);
                    String base64AccessToken = Base64.encodeToString(data, Base64.DEFAULT);
                    encryptClient.saveValue("msalAccessToken", base64AccessToken);
                    msalTokenStatus.setCompleted();
                }
            }
        });

            loginWebview.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                if(cm.message().contains("InvalidCountry")){
                    listener.statusMessage("Invalid location detected. XCloud feature will not work without using a VPN to login...");
                }
                if(cm.message().contains("Uncaught Error: Script error for \"jquery\"") || cm.message().contains("Uncaught TypeError: Cannot read properties of undefined (reading 'trim')") || cm.message().contains("Uncaught TypeError: Cannot read property 'trim' of undefined")) {
                    appendLogs("Microsoft website had an error. Retrying: " + retryAttempts);
                    listener.statusMessage("Microsoft website had an error. Retrying: " + retryAttempts + ".\n\nIf this keeps happening you may need to clear the cache.");
                    ((Activity)context).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            final Handler handler = new Handler();
                            handler.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    retryAttempts++;
                                    if(loginWebview != null) {
                                        loginWebview.reload();
                                    }
                                }
                            }, 2000);
                        }
                    });

                }
                appendLogs(String.format(Locale.ENGLISH, "%s @ %d: %s", cm.message(), cm.lineNumber(), cm.sourceId()));
                return true;
            }
        });

        loginWebview.setWebViewClient(new WebViewClient(){

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                listener.showDialog();
                listener.statusMessage("Waiting for: " + url.substring(0, url.indexOf(".com") + 4));
                if (url.toLowerCase().contains(SIGN_IN_USER_PAGE.toLowerCase()))  {
                    listener.hideDialog();
                }

                // magic happens here, wait for the attempt to set msal access token. Intentionally don't allow it to persist so it will always regenerate
                listenForAccessTokenSet();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Here you can check your new URL.
                super.onPageFinished(view, url);
                if(url.toLowerCase().contains(LOGIN_COMPLETE_URL.toLowerCase())) {

                    // get cookie
                    String streamCookieRaw = getCookie(url, STREAM_COOKIE_KEY);
                    encryptClient.saveValue("streamCookieRaw", streamCookieRaw);

                   startPageCompleteTokenExtraction(streamCookieRaw);
                } else if (url.toLowerCase().contains(SIGN_IN_USER_PAGE.toLowerCase()) || url.startsWith("file"))  {
                    listener.hideDialog();
                } else {
                    listener.showDialog();
                    listener.statusMessage("Waiting for: " + url.substring(0, url.indexOf(".com") + 4));
                }
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error){
                appendLogs( "Error: " + request.getUrl());

                if(error.getErrorCode() == -9){
                    Toast.makeText(view.getContext(), "Network Error. Try again", Toast.LENGTH_SHORT).show();

                    // Clear all the cookies
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                } else {
                    appendLogs( "Possible Error: " + error.getErrorCode() + "|" + error.getDescription());
                }
                listener.hideDialog();
            }
        });
    }

    private void startPageCompleteTokenExtraction(String streamCookieRaw){
        appendLogs("startPageCompleteTokenExtraction");
        exchangeCookieForXcloudToken(streamCookieRaw);
        exchangeCookieForGsToken(streamCookieRaw);
    }

    private void listenForAccessTokenSet(){
        boolean skipXcloudLogin = getSkipXcloud();

        if (skipXcloudLogin){
            appendLogs("Ignoring msal retrieval because user set skipXcloudLogin to true");
            return;
        }

        String js =
                "try{ localStorage.clear(); } catch(err){ console.log(err); } "+
                "localStorage.setItem = function(key, value) {" +
                "   console.log('setItem called!', key, value);" +
                "   try {" +
                "       let jsonData = JSON.parse(value);" +

                "       if(jsonData &&" +
                "           jsonData['credentialType'] &&" +
                "           jsonData['credentialType'] === 'RefreshToken' &&" +
                "           jsonData['environment'] == 'login.windows.net' &&" +
                "           jsonData['secret']){" +
                "               console.log('msalTokenFound!', key, value);" +
                "               Android.genericMessage('msalTokenSet', jsonData['secret']);" +
                "       }" +
                "   } catch(err) {" +
                "       console.log('Error', err);" +
                "   }" +
                "}" ;
        callJavaScriptCode(this.loginWebview, js);
    }
    private void emitLoginCompleteIfReady(){
        boolean skipXcloudLogin = getSkipXcloud();

        appendLogs( "Attempting to emit onLoginComplete event: " + skipXcloudLogin + " | " + gsTokenStatus.getAllStatuses() + consoleStatus.getAllStatuses() + xCloudTokenStatus.getAllStatuses() + msalTokenStatus.getAllStatuses());
        listener.statusMessage("GSToken: " + gsTokenStatus.getStatus() + "\nxCloudToken: " + xCloudTokenStatus.getStatus() + "\nMSALToken: " + msalTokenStatus.getStatus() + "\nConsole: " + consoleStatus.getStatus());

        if(skipXcloudLogin && gsTokenStatus.completed && consoleStatus.completed){ // short circuit if skip xcloud set
            appendLogs( "Considering complete because skipXcloudLogin = false");
            listener.onLoginComplete(encryptClient.getValue("consoles"));
        } else if(gsTokenStatus.waiting || consoleStatus.waiting || msalTokenStatus.waiting || xCloudTokenStatus.waiting){
            appendLogs( "Ignoring onComplete event due to waiting...");
        } else if(gsTokenStatus.failed || consoleStatus.failed){
            appendLogs( "Login Failed!");
            clearTokensAndReLogin(true);
        } else if(xCloudTokenStatus.failed || msalTokenStatus.failed){
            appendLogs( "Login worked but no xCloud!");
            Toast.makeText(context, "xCloud feature disabled due to no GamePass subscription", Toast.LENGTH_LONG).show();
            listener.onLoginComplete(encryptClient.getValue("consoles"));
        } else {
            appendLogs( "Login worked 100%");
            listener.onLoginComplete(encryptClient.getValue("consoles"));
        }
    }
    private void exchangeCookieForGsToken(String streamCookieRaw) {
        if(gsTokenStatus.completed){
            appendLogs("Ignoring gsToken retrieval because we already have it");
            return;
        }
        // use cookie to make api request to get game stream token
        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject postData = new JSONObject();

        try {
            String streamCookieDecoded = URLDecoder.decode(streamCookieRaw, "UTF-8");
            JSONObject tokenObject = new JSONObject(streamCookieDecoded);
            postData.put("token", tokenObject.getString("Token"));
            postData.put("offeringId", "xhome");
        } catch (Exception e) {
            listener.statusMessage("Cannot exchange StreamCookie for GameStream token. AuthError");
            e.printStackTrace();
            gsTokenStatus.setFailed();
            consoleStatus.setFailed();
        }

        // Request a string response from the provided URL.
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, LOGIN_ENDPOINT, postData,
                response -> {
                    try {
                        String gsToken = response.getString("gsToken");
                        encryptClient.saveValue("gsToken", gsToken);
                        encryptClient.saveValue("streamCookieRaw", streamCookieRaw);
                        listener.statusMessage("Created new GameStream token from existing StreamCookie. Validating...");

                        // get consoles once we have valid gs token
                        gsTokenStatus.setCompleted();
                        getConsoles(gsToken);
                    } catch (JSONException e) {
                        listener.statusMessage("Cannot exchange StreamCookie for GameStream token. JSONException");
                        e.printStackTrace();
                        gsTokenStatus.setFailed();
                        consoleStatus.setFailed();
                    }
                }, error -> {
            // String responseData = new String(error.networkResponse.data, StandardCharsets.US_ASCII);
            listener.statusMessage("Cannot exchange StreamCookie for GameStream token. Attempting to regenerate.");
            gsTokenStatus.setFailed();
            consoleStatus.setFailed();
        });
        queue.add(stringRequest);
    }
    private void exchangeCookieForXcloudToken(String streamCookieRaw) {
        boolean skipXcloudLogin = getSkipXcloud();

        if(xCloudTokenStatus.completed){
            appendLogs("Ignoring xcloud retrieval because we already have it");
            return;
        } else if (skipXcloudLogin){
            appendLogs("Ignoring xcloud retrieval because user set skipXcloudLogin to true");
            return;
        }
        // use cookie to make api request to get game stream token
        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject postData = new JSONObject();

        try {
            String streamCookieDecoded = URLDecoder.decode(streamCookieRaw, "UTF-8");
            JSONObject tokenObject = new JSONObject(streamCookieDecoded);
            postData.put("token", tokenObject.getString("Token"));
            postData.put("offeringId", "xgpuweb");
        } catch (Exception e) {
            listener.statusMessage("Error pulling streamCookieRaw... Exploding...");
            e.printStackTrace();
            xCloudTokenStatus.setFailed();
            return;
        }

        // Request a string response from the provided URL.
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, XCLOUD_LOGIN_ENDPOINT, postData,
                response -> {
                    try {
                        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
                        String desiredRegion = prefs.getString("region_key", "default");

                        String xcloudToken = response.getString("gsToken");
                        encryptClient.saveValue("xcloudToken", xcloudToken);
                        JSONArray regions = response.getJSONObject("offeringSettings").getJSONArray("regions");
                        String regionName = "";

                        for (int i = 0; i < regions.length(); i++) { // loop over all regions
                            JSONObject region = regions.getJSONObject(i);

                            // if we have a custom region use that and break
                            if(!desiredRegion.equals("default") && region.getString("name").equals(desiredRegion)){
                                appendLogs( "Saving custom region");
                                encryptClient.saveValue("xcloudRegion", region.getString("baseUri").substring(8)); // remove https://
                                regionName = region.getString("name");
                                Toast.makeText(this.context, "Using custom region: " + regionName, Toast.LENGTH_SHORT).show();
                                break;
                            }

                            // if we dont have a custom region fallback to default region
                            if (region.getBoolean("isDefault")) {
                                appendLogs( "Saving default region");
                                encryptClient.saveValue("xcloudRegion", region.getString("baseUri").substring(8)); // remove https://
                                regionName = region.getString("name");
                            }
                        }
                        appendLogs( "Got xCloud token: " + xcloudToken + " - " + regionName);
                        listener.statusMessage("Got xCloud token! Using region" +  regionName);
                        xCloudTokenStatus.setCompleted();
                    } catch (JSONException e) {
                        listener.statusMessage("Cannot exchange StreamCookie for xCloud token. JSONException");
                        e.printStackTrace();
                        xCloudTokenStatus.setFailed();
                    }
                }, error -> {
            // String responseData = new String(error.networkResponse.data, StandardCharsets.US_ASCII);
            listener.statusMessage("Cannot exchange StreamCookie for xCloud token... Attempting to regenerate.");
            encryptClient.saveValue("xcloudToken", null);
            encryptClient.saveValue("xcloudRegion", null);
            xCloudTokenStatus.setFailed();

            String responseData = null;
            if(error != null && error.networkResponse != null && error.networkResponse.data != null) {
                try {
                    responseData = new String(error.networkResponse.data,"UTF-8");
                    appendLogs(responseData);

                    if(responseData.contains("InvalidCountry")){
                        listener.statusMessage("Invalid country code detected. XCloud feature will not work.");
                        Toast.makeText(context, "Invalid location detected. XCloud feature will not work. Consider using a VPN to login if you plan to use XCloud.", Toast.LENGTH_LONG).show();
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
        }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Content-Type","application/json; charset=utf-8");
                params.put("x-gssv-client","XboxComBrowser");
                return params;
            }
        };
        queue.add(stringRequest);
    }
    private void getConsoles(String gsToken) {
        if(consoleStatus.completed){
            appendLogs("Ignoring console retrieval because we already have it");
            return;
        }
        if(TextUtils.isEmpty(gsToken)){
            appendLogs("Ignoring validateGsToken because string null");
            consoleStatus.setFailed();
            return;
        }
        // use cookie to make api request to get game stream token
        RequestQueue queue = Volley.newRequestQueue(context);
        StringRequest getRequest = new StringRequest(Request.Method.GET, LIST_CONSOLES_ENDPOINT,
                new Response.Listener<String>()
                {
                    @Override
                    public void onResponse(String response) {
                        // response
                        listener.statusMessage("Got console data... Please wait.");
                        encryptClient.saveValue("gsToken", gsToken); // reset gs saved value to be safe
                        encryptClient.saveValue("consoles", response);

                        appendLogs( "console response is: " + response);
                        appendLogs( "gsToken is: " + gsToken);

                        consoleStatus.setCompleted();
                    }
                },
                new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO Auto-generated method stub
                        listener.statusMessage("Failed to get console data");
                        consoleStatus.setFailed();
                    }
                }
        ) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Authorization", "Bearer " + gsToken);
                params.put("Content-Type","application/json");
                return params;
            }
        };

        queue.add(getRequest);
    }

    private void clearTokensAndReLogin(Boolean clearStorageData) {
        listener.statusMessage("Clearing cached tokens and prompting user for ReLogin");
        appendLogs( "clearTokensAndReLogin " + clearStorageData);
        if(clearStorageData){
            // Clear all the cookies
            CookieManager.getInstance().removeAllCookies(null);
            CookieManager.getInstance().flush();

            // wipe local storage for site otherwise will pull account from cache and not trigger oauth process again
            String mimeType = "text/html";
            String encoding = "utf-8";
            String injection = "<script type='text/javascript'>localStorage.clear();window.location.href = 'https://google.com';</script>";
            loginWebview.loadDataWithBaseURL("https://www.xbox.com?custom_refresh", injection, mimeType, encoding, null);
        }

        encryptClient.saveValue("gsToken", "");
        encryptClient.saveValue("streamCookieRaw", "");
        encryptClient.saveValue("xcloudToken", "");
        encryptClient.saveValue("xcloudRegion", "");
        encryptClient.saveValue("msalAccessToken", "");
        encryptClient.saveValue("msalRefreshToken", "");
        encryptClient.saveValue("clientId", "");
        encryptClient.saveValue("consoles", "");

        if (!clearStorageData){ // if soft refreshing redo login. If hard wiping then wait for above load url to complete which will call login when done
            ((Activity)context).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    doLogin(); // recall do login now with cleared cache
                }
            });
        }
    }

    // HELPERS
    private void appendLogs(String data){
        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean captureLogs = prefs.getBoolean("capture_debug_logs_key", false);

        if(!captureLogs){
            return;
        }

        String currentData = prefs.getString("login_logs", "");
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("login_logs", currentData + "\n" + data);
        editor.apply();
    }
    private void resetLogs(){
        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean captureLogs = prefs.getBoolean("capture_debug_logs_key", false);

        if(!captureLogs){
            return;
        }

        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("login_logs", "");
        editor.apply();
    }
    private String getCookie(String siteName, String cookieName){
        String CookieValue = null;

        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(siteName);
        String[] temp=cookies.split(";");
        for (String ar1 : temp ){
            if(ar1.contains(cookieName)){
                String[] temp1=ar1.split("=");
                CookieValue = temp1[1];
                break;
            }
        }
        return CookieValue;
    }
    boolean getSkipXcloud(){
        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        return prefs.getBoolean("skip_xcloud_login_key", false);
    }
    private void callJavaScriptCode(WebView view, String input){
        view.evaluateJavascript(input, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String token) {
//                appendLogs("MSAL Response: " + token);
//
//                if (token == null || token.equals("null") || TextUtils.isEmpty(token)){
//
//                    listener.statusMessage("Waiting for MSAL Token. Attempt: " + retryAttempts);
//                    appendLogs("Reloading due to MSAL: " + token);
//                    ((Activity)context).runOnUiThread(new Runnable() {
//                        @Override
//                        public void run() {
//                            final Handler handler = new Handler();
//                            handler.postDelayed(new Runnable() {
//                                @Override
//                                public void run() {
//                                    retryAttempts++;
//                                    if(loginWebview != null) {
//                                        loginWebview.reload();
//                                    }
//                                }
//                            }, 2000);
//                        }
//                    });
//                }  else {
//                    // base 64 encode the access token so it works in the URL
//                    byte[] data = token.getBytes(StandardCharsets.UTF_8);
//                    String base64AccessToken = Base64.encodeToString(data, Base64.DEFAULT);
//                    encryptClient.saveValue("msalAccessToken", base64AccessToken);
//
//                    msalTokenStatus.setCompleted();
//                }
            }
        });
    }
}

// if overriding prototype ever stops working, revert to getting and wiping the tokens
//    private void getMsalToken(){
//        if(msalTokenStatus.completed){
//            appendLogs("Ignoring msal retrieval because we already have it");
//            return;
//        }
//        String extractMsalTokenString =
//                        " (function() {" +
//                        "        let result;" +
//                        "        const items = localStorage;" +
//                        "        const keys = Object.keys(items);" +
//                        "            for(let i = 0; i < keys.length; i++){" +
//                        "                try {" +
//                        "                    const key = keys[i];" +
//                        "                    const dataRaw = items[key];" +
//                        "                    let jsonData = JSON.parse(dataRaw);" +
//                        "                    if(jsonData &&" +
//                        "                            jsonData['credentialType'] &&" +
//                        "                            jsonData['credentialType'] === 'AccessToken' &&" +
//                        "                            jsonData['secret']){" +
//                        "                           result = jsonData['secret'];" +
//                        "                    }" +
//            "                                window.localStorage.removeItem(key);" +
//                        "                } catch (err){" +
//                        "                    console.error(err);" +
//                        "                }" +
//                        "            }" +
//                        "            return result;" +
//                        "        })()";
//        callJavaScriptCode(this.loginWebview, extractMsalTokenString);
//    }
