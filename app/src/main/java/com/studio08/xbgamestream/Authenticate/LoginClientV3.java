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
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

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
import java.util.concurrent.ExecutionException;

public class LoginClientV3 {
    public interface LoginClientListener {
        void onLoginComplete(String consoles);
        void statusMessage(String message);
        void hideDialog();
        void showDialog();
        void genericMessage(String type, String message);
    }
    private Context context;
    private StreamWebview loginWebview = null;
    LoginClientV3.LoginClientListener listener = null;
    private String LOGIN_URL = "https://www.xbox.com/en-US/auth/msa?action=logIn&returnUrl=https%3A%2F%2Fwww.xbox.com%2Fen-US%2Fplay";
    // https://login.live.com/oauth20_authorize.srf?client_id=1f907974-e22b-4810-a9de-d9647380c97e&scope=xboxlive.signin+openid+profile+offline_access&redirect_uri=https%3a%2f%2fwww.xbox.com%2fauth%2fmsa%3faction%3dloggedIn%26locale_hint%3den-US&response_type=code&state=eyJpZCI6ImZlOTNiZGUxLTZjYzEtNGEzZS1hM2MxLTc5ZGQ5MDkxZmYxMSIsIm1ldGEiOnsiaW50ZXJhY3Rpb25UeXBlIjoicmVkaXJlY3QifX0%3d%7chttps%253A%252F%252Fwww.xbox.com%252Fen-us%252Fplay&response_mode=fragment&nonce=6389ab3d-9ff1-4847-b92f-fc3451c09124&prompt=login&code_challenge=5A2psoPjwgEhYDInCaB643tR9v1dBZEE1f75bw7TFQ8&code_challenge_method=S256&x-client-SKU=msal.js.browser&x-client-Ver=2.32.2&uaid=3d7a0a7563ed47b0983a274dc21ba3b4&msproxy=1&issuer=mso&tenant=consumers&ui_locales=en-US&client_info=1&epct=PAQABAAEAAAD--DLA3VO7QrddgJg7WevrTvXaUMW_EChMSPEE35Av9NEJtbeIipoTmecCHxqO_-swhdE-X7v9EUMjtP7KB7189_HAaIM3ehQo6kuSzOfeGQArOAm-BKs3faQxuRRdJJ3kCCURivzgY9PdWEauDPW6ZyXOplFNND7rEPBnWs6YjrlBjEGubqFGtQQz5Y-FdDAOXiHLag93UUOm5gGTL1DeT5VmIHwj99UPRp0x2SgKUyAA&jshs=0#

    private String AUTH_COOKIE_KEY = "XBXXtkhttp://xboxlive.com";
    private String STREAM_COOKIE_KEY = "XBXXtkhttp://gssv.xboxlive.com";
    private String LOGIN_ENDPOINT = "https://xhome.gssv-play-prod.xboxlive.com/v2/login/user"; // used to get GS token
    private String XCLOUD_LOGIN_ENDPOINT = "https://xgpuweb.gssv-play-prod.xboxlive.com/v2/login/user"; // used to get xcloud token

    private String LIST_CONSOLES_ENDPOINT = "https://uks.core.gssv-play-prodxhome.xboxlive.com/v6/servers/home";
    private String SIGN_IN_USER_PAGE = "login.live.com";
    private String AUTH_LOADING_PAGE = "https://www.xbox.com/en-US/auth/msa";
    private EncryptClient encryptClient = null;

    private TokenStatus gsTokenStatus =  new TokenStatus();
    private TokenStatus consoleStatus = new TokenStatus();
    private TokenStatus xCloudTokenStatus = new TokenStatus();
    private TokenStatus msalTokenStatus = new TokenStatus();
    private int retryAttempts = 0;
    public boolean pollerRunning = false;
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

    public LoginClientV3(Context context, StreamWebview webview) {
        this.context = context;
        this.listener = null;
        this.loginWebview = webview;
        this.encryptClient = new EncryptClient(this.context);
        setupWebviewListeners();
        resetLogs();
    }

    // Assign the listener implementing events interface that will receive the events
    public void setCustomObjectListener(LoginClientV3.LoginClientListener listener) {
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
//                if(type.equals("msalTokenSet")){
//                    appendLogs("Found MSAL Token: " + msg);
//                    // base 64 encode the access token so it works in the URL
//                    byte[] data = msg.getBytes(StandardCharsets.UTF_8);
//                    String base64AccessToken = Base64.encodeToString(data, Base64.DEFAULT);
//                    encryptClient.saveValue("msalAccessToken", base64AccessToken);
//                    msalTokenStatus.setCompleted();
//                }
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
                Log.e("HERE", "Page started:" + url);
                listener.showDialog();
                listener.statusMessage("Waiting for: " + url.substring(0, url.indexOf(".com") + 4));
                if (url.toLowerCase().contains(SIGN_IN_USER_PAGE.toLowerCase()) || (url.toLowerCase().contains(AUTH_LOADING_PAGE.toLowerCase()) && !url.toLowerCase().contains("https://www.xbox.com/en-us/auth/msa?action=loggedin&locale_hint=en-us")))  {
                    listener.hideDialog();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Here you can check your new URL.
                super.onPageFinished(view, url);
                Log.e("HERE", "Page Finished:" + url);

                if (url.toLowerCase().contains(SIGN_IN_USER_PAGE.toLowerCase()) ||(url.toLowerCase().contains(AUTH_LOADING_PAGE.toLowerCase()) && !url.toLowerCase().contains("https://www.xbox.com/en-us/auth/msa?action=loggedin&locale_hint=en-us")))  {
                   listener.hideDialog();
                } else {
                    listener.showDialog();
                    listener.statusMessage("Waiting for: " + url.substring(0, url.indexOf(".com") + 4));
                }

                startPollingForTokens();
                setupPrefillHelper();
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error){
                appendLogs( "Error: " + request.getUrl());
                appendLogs( "Error: " +  loginWebview.getUrl());

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

    private void startPollingForTokens() {
        if (pollerRunning){
            return;
        }
        pollerRunning = true;
        final Handler handler = new Handler();
        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                if (!pollerRunning || loginWebview == null){
                    appendLogs("killing pollerRunning");
                    pollerRunning = false;
                    return;
                }
                if(gsTokenStatus.getStatus().equals("complete")) {
                  appendLogs("gsToken complete, attempt to pull msal token");
                  getMsalToken();
                } else {
                  appendLogs("gsToken not complete, attempt to pull cookie token");
                  startPageCompleteTokenExtraction();
                }

                handler.postDelayed(this, 3000);
            }
        };

        handler.post(runnable);
    }
    public void startPageCompleteTokenExtraction(){
        appendLogs("startPageCompleteTokenExtraction");
        if(loginWebview != null && loginWebview.getUrl() != null){
            String streamCookieRaw = getCookie(loginWebview.getUrl(), STREAM_COOKIE_KEY);
            encryptClient.saveValue("streamCookieRaw", streamCookieRaw);

            exchangeCookieForGsToken(streamCookieRaw); // will trigger xcloud check too
        } else {
            appendLogs("loginWebview or getUrl null");
        }
    }

    private void emitLoginCompleteIfReady(){
        boolean skipXcloudLogin = getSkipXcloud();

        appendLogs( "Attempting to emit onLoginComplete event: " + skipXcloudLogin + " | " + gsTokenStatus.getAllStatuses() + consoleStatus.getAllStatuses() + xCloudTokenStatus.getAllStatuses() + msalTokenStatus.getAllStatuses());

        if (loginWebview != null && loginWebview.getUrl() != null && loginWebview.getUrl().toLowerCase().contains(SIGN_IN_USER_PAGE.toLowerCase())){
            appendLogs("not showing status update while on 2fa page");
        } else {
            listener.statusMessage("GSToken: " + gsTokenStatus.getStatus() + "\nxCloudToken: " + xCloudTokenStatus.getStatus() + "\nMSALToken: " + msalTokenStatus.getStatus() + "\nConsole: " + consoleStatus.getStatus());
        }

        if(skipXcloudLogin && gsTokenStatus.completed && consoleStatus.completed){ // short circuit if skip xcloud set
            appendLogs( "Considering complete because skipXcloudLogin = false");
            listener.onLoginComplete(encryptClient.getValue("consoles"));
        } else if(gsTokenStatus.waiting || consoleStatus.waiting || msalTokenStatus.waiting || xCloudTokenStatus.waiting){
            appendLogs( "Ignoring onComplete event due to waiting...");
        } else if(gsTokenStatus.failed || consoleStatus.failed){
            appendLogs( "Login gsTokenStatus or consoleStatus failed");
        } else if(xCloudTokenStatus.failed || msalTokenStatus.failed){ // since this is called after gsToken is validated, we shouldnt have false positives from polling an old gsToken too soon
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
        } else if (streamCookieRaw == null){
            appendLogs("Ignoring gsToken retrieval because streamCookieRaw is null");
            return;
        }
        // use cookie to make api request to get game stream token
        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject postData = new JSONObject();

        Log.e("LOG", streamCookieRaw);
        try {
            String streamCookieDecoded = URLDecoder.decode(streamCookieRaw, "UTF-8");
            JSONObject tokenObject = new JSONObject(streamCookieDecoded);
            postData.put("offeringId", "xhome");

            JSONObject tokenData = null;
            String token = null;
            try {
                tokenData = tokenObject.getJSONObject("tokenData");
                token = tokenData.getString("token");
            } catch (Exception e){}

            if (token == null){
                token = tokenObject.getString("Token");
            }

            postData.put("token", token);
        } catch (Exception e) {
//            listener.statusMessage("Cannot exchange StreamCookie for GameStream token. AuthError");
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
//                        listener.statusMessage("Created new GameStream token from existing StreamCookie. Validating...");

                        // get consoles once we have valid gs token
                        gsTokenStatus.setCompleted();
                        getConsoles(gsToken);
                        exchangeCookieForXcloudToken(streamCookieRaw, null);
                    } catch (JSONException e) {
//                        listener.statusMessage("Cannot exchange StreamCookie for GameStream token. JSONException");
                        e.printStackTrace();
                        gsTokenStatus.setFailed();
                        consoleStatus.setFailed();
                    }
                }, error -> {
            // String responseData = new String(error.networkResponse.data, StandardCharsets.US_ASCII);
//            listener.statusMessage("Cannot exchange StreamCookie for GameStream token. Attempting to regenerate.");
            gsTokenStatus.setFailed();
            consoleStatus.setFailed();
        });
        queue.add(stringRequest);
    }
    private void exchangeCookieForXcloudToken(String streamCookieRaw, String invalidCountryCodeRetryIp) {
        boolean skipXcloudLogin = getSkipXcloud();
        if(xCloudTokenStatus.completed){
            appendLogs("Ignoring xcloud retrieval because we already have it");
            return;
        } else if (skipXcloudLogin){
            appendLogs("Ignoring xcloud retrieval because user set skipXcloudLogin to true");
            return;
        } else if (streamCookieRaw == null){
            appendLogs("Ignoring xcloud retrieval because user streamCookieRaw is null");
            return;
        }
        // use cookie to make api request to get game stream token
        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject postData = new JSONObject();

        try {
            String streamCookieDecoded = URLDecoder.decode(streamCookieRaw, "UTF-8");
            JSONObject tokenObject = new JSONObject(streamCookieDecoded);
            postData.put("offeringId", "xgpuweb");

            JSONObject tokenData = null;
            String token = null;
            try {
                tokenData = tokenObject.getJSONObject("tokenData");
                token = tokenData.getString("token");
            } catch (Exception e){}

            if (token == null){
                token = tokenObject.getString("Token");
            }

            postData.put("token", token);
        } catch (Exception e) {
            e.printStackTrace();
            xCloudTokenStatus.setFailed();
            return;
        }

        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        String desiredRegion = prefs.getString("region_key", "default");

        // Request a string response from the provided URL.
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, XCLOUD_LOGIN_ENDPOINT, postData,
                response -> {
                    try {
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
                        if (invalidCountryCodeRetryIp != null){
                            appendLogs("Recovered from invalid country error: " + invalidCountryCodeRetryIp);
                        }
                        xCloudTokenStatus.setCompleted();
                    } catch (JSONException e) {
                        e.printStackTrace();
                        xCloudTokenStatus.setFailed();
                    }
                }, error -> {

            encryptClient.saveValue("xcloudToken", null);
            encryptClient.saveValue("xcloudRegion", null);

            String responseData = null;
            Boolean retryingDueToInvalidCountry = false;
            if(error != null && error.networkResponse != null && error.networkResponse.data != null && invalidCountryCodeRetryIp == null) {
                responseData = new String(error.networkResponse.data, StandardCharsets.UTF_8);
                appendLogs(responseData);

                if(responseData.contains("InvalidCountry")){
                    if (desiredRegion.equals("default")) {
                        appendLogs("not attempting to spoof location");
                        Toast.makeText(context, "Invalid location. Set an xCloud region in the settings and re-login!", Toast.LENGTH_LONG).show();
                    } else {
                        appendLogs("attempting to spoof location");
                        String forwardIp = getIpFromCustomRegion(desiredRegion);
                        if (forwardIp != null){
                            retryingDueToInvalidCountry = true;
                            exchangeCookieForXcloudToken(streamCookieRaw, forwardIp);
                        } else {
                            appendLogs("Invalid custom xCloud Region! Choose a different region in the settings " + desiredRegion);
                            Toast.makeText(context, "Invalid custom xCloud Region! Choose a different region in the settings " + desiredRegion, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }

            if (!retryingDueToInvalidCountry){
                xCloudTokenStatus.setFailed();
            }
        }){
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Content-Type","application/json; charset=utf-8");
                params.put("x-gssv-client","XboxComBrowser");
                if (invalidCountryCodeRetryIp != null){
                    params.put("X-Forwarded-For", invalidCountryCodeRetryIp);
                }
                return params;
            }
        };
        queue.add(stringRequest);
    }

    private String getIpFromCustomRegion(String desiredCustomRegion) {
        if (desiredCustomRegion.equals("default")) {
            return null;
        } else if (desiredCustomRegion.contains("Australia")) {
            return "203.41.44.20";
        } else if (desiredCustomRegion.contains("Brazil")) {
            return "200.221.11.101";
        } else if (desiredCustomRegion.contains("Europe") || desiredCustomRegion.contains("UK")){
            return "194.25.0.68";
        } else if (desiredCustomRegion.contains("Japan")) {
            return "122.1.0.154";
        } else if (desiredCustomRegion.contains("Korea")) {
            return "203.253.64.1";
        } else if (desiredCustomRegion.contains("US") || desiredCustomRegion.contains("Us")) {
            return "4.2.2.2";
        } else {
            return null;
        }
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
        }

        encryptClient.saveValue("gsToken", "");
        encryptClient.saveValue("streamCookieRaw", "");
        encryptClient.saveValue("xcloudToken", "");
        encryptClient.saveValue("xcloudRegion", "");
        encryptClient.saveValue("msalAccessToken", "");
        encryptClient.saveValue("msalRefreshToken", "");
        encryptClient.saveValue("clientId", "");
        encryptClient.saveValue("consoles", "");

        ((Activity)context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                doLogin(); // recall do login now with cleared cache
            }
        });

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
    private String getCookie(String siteName, String cookieDesiredName){
        String CookieValue = null;

        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(siteName);
        if (cookies == null || cookies.length() <= 0){
            return null;
        }
        String[] temp = cookies.split(";");
        for (String cookiePair : temp ){
            try {
                String[] cookiePairArray = cookiePair.split("=");
                String cookieName = cookiePairArray[0];
                String cookieValue = cookiePairArray[1];

                // url decode the name
                cookieName = URLDecoder.decode(cookieName, "UTF-8");

                if (cookieName.contains(cookieDesiredName)) {
                    CookieValue = cookieValue;
                    break;
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        return CookieValue;
    }
    boolean getSkipXcloud(){
        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        return prefs.getBoolean("skip_xcloud_login_key", false);
    }

    public void setupPrefillHelper() {
        if(this.loginWebview == null){
            appendLogs("Ignoring prefill helper");
            return;
        }

        String handlePrefillJS = "" +
                " (function() {\n" +
                "     function handlePrefillKey() {\n" +
                "         const prefilledKey = localStorage.getItem('prefilled_key');\n" +
                "         try {\n" +
                "             const prefillKeyField = document.querySelector('input[type=\"password\"]');\n" +
                "             const submitButton = document.querySelector('input[type=\"submit\"]');\n" +
                "             if (!prefillKeyField || !submitButton) {\n" +
                "                 return false;\n" +
                "             }\n" +
                "             const originalMethod = submitButton.onclick;\n" +
                "             submitButton.onclick = function(event) {\n" +
                "                 localStorage.setItem('prefilled_key', prefillKeyField.value);\n" +
                "                 console.log('set value', prefillKeyField.value);\n" +
                "                 if (originalMethod) {\n" +
                "                     originalMethod.call(this, event);\n" +
                "                 }\n" +
                "             };\n" +
                "             if (prefilledKey) {\n" +
                "                 prefillKeyField.focus();\n" +
                "                 prefillKeyField.value = prefilledKey;\n" +
                "             }\n" +
                "             return true;\n" +
                "         } catch (err) {\n" +
                "             console.log(err);\n" +
                "             return false;\n" +
                "         }\n" +
                "     }\n" +
                "     let setValueOnceInterval = setInterval(() => {\n" +
                "         let worked = handlePrefillKey();\n" +
                "         if (worked) {\n" +
                "             clearInterval(setValueOnceInterval);\n" +
                "             console.log('worked, stop trying');\n" +
                "         } else {\n" +
                "         }\n" +
                "     }, 100);\n" +
                " })();";
        callJavaScriptCode(this.loginWebview, handlePrefillJS);
    }

    public void getMsalToken(){
        if(msalTokenStatus.completed || this.loginWebview == null){
            appendLogs("Ignoring msal retrieval because we already have it");
            return;
        }
        String extractMsalTokenString =
                " (function() {" +
                        "        let result;" +
                        "        const items = localStorage;" +
                        "        const keys = Object.keys(items);" +
                        "            for(let i = 0; i < keys.length; i++){" +
                        "                try {" +
                        "                    const key = keys[i];" +
                        "                    const dataRaw = items[key];" +
                        "                    let jsonData = JSON.parse(dataRaw);" +
                        "                    console.log('LOCAL STORAGE ITEM', key, dataRaw);" +
                        "                    if(jsonData &&" +
                        "                            jsonData['credentialType'] &&" +
                        "                            jsonData['credentialType'] === 'RefreshToken' &&" +
                        "                            jsonData['environment'] == 'login.windows.net' &&" +
                        "                            jsonData['secret']){" +
                        "                                   result = jsonData['secret'].replace('\"', '');" +
                        "                    }" +
                        "                } catch (err){" +
                        "                    console.error(err);" +
                        "                }" +
                        "            }" +
                        "            return result;" +
                        "        })()";
        callJavaScriptCode(this.loginWebview, extractMsalTokenString);
    }
    private void callJavaScriptCode(WebView view, String input){
        view.evaluateJavascript(input, new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String token) {
                appendLogs("MSAL Response: " + token);

                if (token == null || token.equals("null") || TextUtils.isEmpty(token)){
                    appendLogs("NO MSAL! " + token);
                    // msalTokenStatus.setFailed();
                }  else {
                    token = token.replaceAll("\"", "");
                    appendLogs("Found Valid MSAL Token: " + token);

                    // base 64 encode the access token so it works in the URL
                    byte[] data = token.getBytes(StandardCharsets.UTF_8);
                    String base64AccessToken = Base64.encodeToString(data, Base64.DEFAULT);
                    encryptClient.saveValue("msalAccessToken", base64AccessToken);

                    msalTokenStatus.setCompleted();
                }
            }
        });
    }
}
