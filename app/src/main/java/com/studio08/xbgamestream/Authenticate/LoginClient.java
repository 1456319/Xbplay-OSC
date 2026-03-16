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

public class LoginClient {
    public interface LoginClientListener {
        void onLoginComplete(String consoles);
        void statusMessage(String message);
        void hideDialog();
        void showDialog();
        void genericMessage(String type, String message);
    }
    private Context context;
    private WebView loginWebview = null;
    LoginClient.LoginClientListener listener = null;
    private String LOGIN_URL = "https://account.xbox.com/account/signin?returnUrl=https%3A%2F%2Fwww.xbox.com%2Fen-US%2Fplay&ru=https%3A%2F%2Fwww.xbox.com%2Fen-US%2Fplay";
    private String LOGIN_COMPLETE_URL= "https://www.xbox.com/en-US/play";
    private String AUTH_COOKIE_KEY = "XBXXtkhttp://xboxlive.com";
    private String STREAM_COOKIE_KEY = "XBXXtkhttp://gssv.xboxlive.com";
    private String LOGIN_ENDPOINT = "https://xhome.gssv-play-prod.xboxlive.com/v2/login/user"; // used to get GS token
    private String XCLOUD_LOGIN_ENDPOINT = "https://xgpuweb.gssv-play-prod.xboxlive.com/v2/login/user"; // used to get xcloud token
    private String OAUTH_EXCHANGE_ACCESS_TOKENS = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"; // used to get access token from oauth

    private String LIST_CONSOLES_ENDPOINT = "https://uks.gssv-play-prodxhome.xboxlive.com/v6/servers/home";
    private String SIGN_IN_USER_PAGE = "login.live.com";
    private EncryptClient encryptClient = null;

    private String code;
    private String codeVerifier = "4FJg33qPZtL5zU4WpGSK2f-gC-MU8k-LrP8Vt6o1EQo"; // raw text
    private String codeChallenge = "Tt6Fc841IKfboO71IESdorZ-iH0ZHhcBRhX9jOPM048"; // sha 256 encrypted code verifier
    private Boolean alreadyFetchingAccessToken = false;
    private Boolean receivedMsalToken = false;
    private Boolean shouldInterceptMsalData = true;
    private Boolean consolesEndpointComplete = false;
    public LoginClient(Context context, WebView webview) {
        this.context = context;
        this.listener = null;
        this.loginWebview = webview;
        this.encryptClient = new EncryptClient(this.context);
        setupWebviewListeners();
        resetLogs();
    }

    // Assign the listener implementing events interface that will receive the events
    public void setCustomObjectListener(LoginClient.LoginClientListener listener) {
        this.listener = listener;
    }


    // if we have never saved a gsToken before, prompt for login, else check if the list_consoles endpoint works. If it does assume token valid
    public void doLogin() {

        // first attempt to user GS token from cache
        String gsToken = encryptClient.getValue("gsToken");

        if(!TextUtils.isEmpty(gsToken)){ // if we have a valid gsToken already saved, make sure its still good
            listener.statusMessage("Using previous GameStream token. Validating...");
            if (encryptClient.getValue("streamCookieRaw") != null) {
                exchangeCookieForXcloudToken(encryptClient.getValue("streamCookieRaw")); // always create post message to update xCloud token
            } else {
                appendLogs("streamCookieRaw NULL!! WTH");
            }
        }

        // attempt to get new access token if we haven't already
        if(!receivedMsalToken) {
            // always try to get new refresh token, will validate gsToken on success
            exchangeRefreshTokenForAccessToken(); // if no refresh token set, force re-login, false means we are re-loggin in, dont validate in this case
        }
        // load login url, if already logged in will redirect to profile page
        loginWebview.loadUrl(LOGIN_URL);

    }

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

    private void setupWebviewListeners() {
        loginWebview.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage cm) {
                if(cm.message().contains("InvalidCountry")){
                    // listener.genericMessage("cant_login", "InvalidCountry");
                    listener.statusMessage("Invalid location detected. XCloud feature will not work without using a VPN to login. Please wait up to 2 minutes...");
                }

                appendLogs(String.format(Locale.ENGLISH, "%s @ %d: %s", cm.message(), cm.lineNumber(), cm.sourceId()));
                return true;
            }
        });

        loginWebview.setWebViewClient(new WebViewClient(){
            @Nullable
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                appendLogs( "shouldInterceptRequest: " + request.getUrl() + " - " + request.getMethod());

                // 3. intercept the post request, stop it from going through since it will fail now that we changed the challenge
                if (shouldInterceptMsalData && request.getUrl().getHost() != null && request.getUrl().getHost().contains("login.microsoftonline") && request.getMethod().equals("POST")) {
                    String clientId = encryptClient.getValue("clientId");
                    appendLogs("3. INTERCEPTING THE POST REQUEST! " + "ClientId:" + clientId + " Code:" + code + " CodeVerifier:" + codeVerifier + " CodeChallenge:" + codeChallenge);

                    alreadyFetchingAccessToken = true;
                    exchangeOauthDataForAccessToken();
                    return new WebResourceResponse("text/javascript", "UTF-8", null); // stopping this otherwise will expire our code creating a race condition on if we can send the request before the browser
                }
                return super.shouldInterceptRequest(view, request);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                appendLogs("shouldOverrideUrlLoading: " + request.getUrl() + " - " + request.getMethod());

                // 1. inject our own code challenge in this request, we need to know the full challenge here so we can create the full post request
                if (shouldInterceptMsalData && request.getUrl().toString().contains("code_challenge") && !request.getUrl().toString().contains(codeChallenge) && request.getMethod().equals("GET")) {
                    appendLogs( "1. INJECTING OUR CHALLENGE! Original: " + request.getUrl().getQueryParameter("code_challenge"));
                    String clientId = request.getUrl().getQueryParameter("client_id");
                    encryptClient.saveValue("clientId", clientId);
                    Uri newUri = replaceUriParameter(request.getUrl(),"code_challenge", codeChallenge);
                    if (!alreadyFetchingAccessToken) {
                        view.loadUrl(newUri.toString());
                    } else {
                        appendLogs("Ignoring request to get access token because we already are: 1");
                    }
                    return true; // stop this request from going through because we are injecting our own ^
                }

                // 2. grab the code here, we need this for the post request
                if (shouldInterceptMsalData && request.getUrl().toString().contains("redirect") && request.getUrl().toString().contains("code=") && request.getMethod().equals("GET")) {
                    if (alreadyFetchingAccessToken) {
                        appendLogs("Ignoring request to get access token because we already are: 2");
                        return false;
                    }

                    // save auth code
                    String fullFrag = request.getUrl().getFragment();
                    code = fullFrag.substring(5, fullFrag.indexOf("&"));

                    appendLogs("2. SAVING THE CODE! Original: " + code);
                }

                // 3. hacky way of clearing local storage. After storage cleared will redirect here
                if (request.getUrl().toString().contains("google.com"))  {
                    appendLogs("Caught fake URL! RE prompting user to login!");
                    doLogin();
                    return true;
                }

                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);

                listener.showDialog();
                listener.statusMessage("Waiting for: " + url.substring(0, url.indexOf(".com") + 4));
                if (url.toLowerCase().contains(SIGN_IN_USER_PAGE.toLowerCase()))  {
                    listener.hideDialog();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {

                // Here you can check your new URL.
                super.onPageFinished(view, url);
                if(url.toLowerCase().contains(LOGIN_COMPLETE_URL.toLowerCase())) {
                    // pull tokens from cookie
                    String streamCookieRaw = getCookie(url, STREAM_COOKIE_KEY);
                    encryptClient.saveValue("streamCookieRaw", streamCookieRaw);
                    // String authCookieRaw = getCookie(url, AUTH_COOKIE_KEY); // not used, uncomment if we need normal api token
                    exchangeCookieForXcloudToken(streamCookieRaw);
                    exchangeCookieForGsToken(streamCookieRaw);
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

    // hits load consoles endpoint. If it doesn't work, pull the cookie from the sesh and try to re auth.
    // if it does work, continue to attempt to grab msal token
    private void validateGSToken(String gsToken) {
        if(TextUtils.isEmpty(gsToken)){
            appendLogs("Ignoring validateGsToken because string null");
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
                        listener.statusMessage("GameStream Token Valid... Please wait.");
                        encryptClient.saveValue("gsToken", gsToken); // reset gs saved value to be safe
                        encryptClient.saveValue("consoles", response);

                        appendLogs( "console response is: " + response);
                        appendLogs( "gsToken is: " + gsToken);

                        // set flag so we know we have valid gs token, will allow login
                        consolesEndpointComplete = true;

                        // normally we wont have an msal token yet, but emit the event which will verify this if possible
                        emitLoginCompleteIfReady();
                    }
                },
                new Response.ErrorListener()
                {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        // TODO Auto-generated method stub
                        listener.statusMessage("GameStream Token Expired. Refreshing");
                        refreshExpiredGsToken();
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

    // will emit login even if we have a msal token and gsToken
    private void emitLoginCompleteIfReady(){
        appendLogs( "Attempting to emit onLoginComplete event");

        // ignore login complete event if we dont have a consoles value yet
        if (TextUtils.isEmpty(encryptClient.getValue("consoles"))) {
            appendLogs( "Ignoring onComplete event due to no consoles data yet");
            return;
        }
        if(!receivedMsalToken) {
            appendLogs( "Ignoring onComplete event due to no msal data yet");
            return;
        }

        if(!consolesEndpointComplete){
            appendLogs( "Ignoring onComplete event due to no console endpoint data yet");
            return;
        }

        listener.onLoginComplete(encryptClient.getValue("consoles"));
    }
    private void refreshExpiredGsToken() {
        // reset gsToken since its invalid
        encryptClient.saveValue("gsToken", null);

        // first attempt to refresh gs token from saved cookie
        String streamCookieRaw = encryptClient.getValue("streamCookieRaw");

        if(!TextUtils.isEmpty(streamCookieRaw)){ // if we have a streamCookie already saved, try to use it to get a new gsToken
            listener.statusMessage("Found existing StreamCookie token. Exchanging for GameStream token");
            exchangeCookieForXcloudToken(streamCookieRaw);
            exchangeCookieForGsToken(streamCookieRaw);
        } else {
            // reset saved tokens since they are invalid
            clearTokensAndReLogin(false);
        }
    }

    private void exchangeCookieForGsToken(String streamCookieRaw) {
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
            // reset saved tokens since they are invalid
            clearTokensAndReLogin(false);
        }

        // Request a string response from the provided URL.
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, LOGIN_ENDPOINT, postData,
                response -> {
                    try {
                        String gsToken = response.getString("gsToken");
                        encryptClient.saveValue("gsToken", gsToken);
                        encryptClient.saveValue("streamCookieRaw", streamCookieRaw);
                        listener.statusMessage("Created new GameStream token from existing StreamCookie. Validating...");

                        validateGSToken(gsToken); // will hit /consoles endpoint to verify token is valid
                    } catch (JSONException e) {
                        listener.statusMessage("Cannot exchange StreamCookie for GameStream token. JSONException");
                        e.printStackTrace();

                        // reset saved tokens since they are invalid
                        clearTokensAndReLogin(false);
                    }
                }, error -> {
            // String responseData = new String(error.networkResponse.data, StandardCharsets.US_ASCII);
            listener.statusMessage("Cannot exchange StreamCookie for GameStream token. Attempting to regenerate.");

            // reset saved tokens since they are invalid
            clearTokensAndReLogin(false);
        });
        queue.add(stringRequest);
    }

    private void exchangeCookieForXcloudToken(String streamCookieRaw) {
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
            clearTokensAndReLogin(true);
            return;
        }

        // Request a string response from the provided URL.
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, XCLOUD_LOGIN_ENDPOINT, postData,
                response -> {
                    try {
                        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
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
                    } catch (JSONException e) {
                        listener.statusMessage("Cannot exchange StreamCookie for xCloud token. JSONException");
                        e.printStackTrace();
                    }
                }, error -> {
            // String responseData = new String(error.networkResponse.data, StandardCharsets.US_ASCII);
            listener.statusMessage("Cannot exchange StreamCookie for xCloud token... Attempting to regenerate.");
            encryptClient.saveValue("xcloudToken", null);
            encryptClient.saveValue("xcloudRegion", null);

            String responseData = null;
            boolean invalidCountryDetected = false;
            if(error != null && error.networkResponse != null && error.networkResponse.data != null) {
                try {
                    responseData = new String(error.networkResponse.data,"UTF-8");
                    appendLogs(responseData);

                    if(responseData.contains("InvalidCountry")){
                        invalidCountryDetected = true;
                        listener.statusMessage("Invalid country code detected. XCloud feature will not work.");
                        Toast.makeText(context, "Invalid location detected. XCloud feature will not work. Consider using a VPN to login if you plan to use XCloud.", Toast.LENGTH_LONG).show();
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            if(!invalidCountryDetected) {
                Toast.makeText(context, "You don't have GamePass. You can't use the XCloud feature, everything else will work :)", Toast.LENGTH_LONG).show();
            }

            // allow user to login (without xcloud)
            emitLoginCompleteIfReady();
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

    // triggers login complete on success or failurecl
    private void exchangeOauthDataForAccessToken() {

        // re-auth if we dont have a client id set
        if (TextUtils.isEmpty(encryptClient.getValue("clientId")) || TextUtils.isEmpty(code) || TextUtils.isEmpty(codeVerifier)) {
            clearTokensAndReLogin(false);
            return;
        }

        // use cookie to make api request to get game stream token
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.POST, OAUTH_EXCHANGE_ACCESS_TOKENS,
                response -> {
                    appendLogs( "Got msal token: " + response.toString());
                    try {
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        String accessToken = jsonResponse.getString("access_token");
                        String refreshToken = jsonResponse.getString("refresh_token");

                        encryptClient.saveValue("msalRefreshToken", refreshToken);

                        // base 64 encode the access token so it works in the URL
                        byte[] data = accessToken.getBytes(StandardCharsets.UTF_8);
                        String base64AccessToken = Base64.encodeToString(data, Base64.DEFAULT);
                        encryptClient.saveValue("msalAccessToken", base64AccessToken);

                        listener.statusMessage("Successfully received msal token" );


                    } catch (JSONException e) {
                        listener.statusMessage("Cannot acquire msal token. JSONException");
                        e.printStackTrace();
                    }
                    shouldInterceptMsalData = false;
                    receivedMsalToken = true;
                    emitLoginCompleteIfReady();
                    doLogin();
                }, error -> {
            String body = "";
            //get status code here
            String statusCode = "";
            //get response body and parse with appropriate encoding
            if(error != null && error.networkResponse.data!=null) {
                try {
                    body = new String(error.networkResponse.data,"UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            if (error != null && error.networkResponse != null){
                statusCode = String.valueOf(error.networkResponse.statusCode);
            }
            appendLogs( statusCode + body);
            listener.statusMessage("Error retrieving GS access token: " + statusCode + body);
            clearTokensAndReLogin(false);

        }) {
            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                // changing values
                params.put("client_id", (encryptClient.getValue("clientId")));
                params.put("code", code);
                params.put("code_verifier", codeVerifier);

                // static values
                params.put("redirect_uri", "https://www.xbox.com/play/login/redirect");
                params.put("scope", "service::http://Passport.NET/purpose::PURPOSE_XBOX_CLOUD_CONSOLE_TRANSFER_TOKEN openid profile offline_access");
                params.put("grant_type", "authorization_code");
                params.put("client_info", "1");
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Content-Type","application/x-www-form-urlencoded");
                params.put("Origin","https://www.xbox.com");
                return params;
            }
        };
        queue.add(stringRequest);
    }

    // triggers login complete on success or failure
    private void exchangeRefreshTokenForAccessToken() {
        if (TextUtils.isEmpty(encryptClient.getValue("msalRefreshToken")) || TextUtils.isEmpty(encryptClient.getValue("clientId"))) { // re-auth if we dont have a client id set
            appendLogs("no msal data set! Not prompting for relogin... Hopefully wont hang because we are always reloading site...");

            // only attempt to validate gsToken after msal auth as completed (everyone has msal token)
            String gsToken = encryptClient.getValue("gsToken");
            validateGSToken(gsToken);
            return;
        }

        // use cookie to make api request to get game stream token
        RequestQueue queue = Volley.newRequestQueue(context);

        // Request a string response from the provided URL.
        StringRequest stringRequest = new StringRequest(Request.Method.POST, OAUTH_EXCHANGE_ACCESS_TOKENS,
                response -> {
                    appendLogs( "Got msal token: " + response.toString());
                    try {
                        JSONObject jsonResponse = new JSONObject(response.toString());
                        String accessToken = jsonResponse.getString("access_token");
                        String refreshToken = jsonResponse.getString("refresh_token");

                        encryptClient.saveValue("msalRefreshToken", refreshToken);

                        // base 64 encode the access token so it works in the URL
                        byte[] data = accessToken.getBytes(StandardCharsets.UTF_8);
                        String base64AccessToken = Base64.encodeToString(data, Base64.DEFAULT);
                        encryptClient.saveValue("msalAccessToken", base64AccessToken);

                        listener.statusMessage("Successfully received msal token" );
                    } catch (JSONException e) {
                        listener.statusMessage("Cannot acquire msal token. JSONException");
                        e.printStackTrace();
                    }

                    receivedMsalToken = true;
                    shouldInterceptMsalData = false; // no longer intercept msal data so user can login
                    emitLoginCompleteIfReady();

                    // only attempt to validate gsToken after msal auth as completed (everyone has msal token)
                    doLogin();
                }, error -> {
            String body = "";
            //get status code here
            String statusCode = "";
            //get response body and parse with appropriate encoding
            if(error != null && error.networkResponse.data!=null) {
                try {
                    body = new String(error.networkResponse.data,"UTF-8");
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }
            if (error != null && error.networkResponse != null){
                statusCode = String.valueOf(error.networkResponse.statusCode);
            }
            appendLogs( statusCode + body);
            listener.statusMessage("Error retrieving GS access token: " + statusCode + body);

            // hard fail if we cant get an access token
            clearTokensAndReLogin(true);
        }) {
            @Override
            public String getBodyContentType() {
                return "application/x-www-form-urlencoded; charset=UTF-8";
            }
            @Override
            protected Map<String, String> getParams() throws AuthFailureError {
                Map<String, String> params = new HashMap<String, String>();
                // changing values
                params.put("client_id", encryptClient.getValue("clientId"));
                params.put("refresh_token", encryptClient.getValue("msalRefreshToken"));

                // static values
                params.put("redirect_uri", "https://www.xbox.com/play/login/redirect");
                params.put("scope", "service::http://Passport.NET/purpose::PURPOSE_XBOX_CLOUD_CONSOLE_TRANSFER_TOKEN openid profile offline_access");
                params.put("grant_type", "refresh_token");
                params.put("client_info", "1");
                return params;
            }

            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String>  params = new HashMap<String, String>();
                params.put("Content-Type","application/x-www-form-urlencoded");
                params.put("Origin","https://www.xbox.com");
                return params;
            }
        };
        queue.add(stringRequest);
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

        code = null;
        alreadyFetchingAccessToken = false;

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
    private static Uri replaceUriParameter(Uri uri, String key, String newValue) {
        final Set<String> params = uri.getQueryParameterNames();
        final Uri.Builder newUri = uri.buildUpon().clearQuery();
        for (String param : params) {
            newUri.appendQueryParameter(param,
                    param.equals(key) ? newValue : uri.getQueryParameter(param));
        }

        return newUri.build();
    }

    private String getCookie(String siteName, String cookieName){
        String CookieValue = null;

        CookieManager cookieManager = CookieManager.getInstance();
        String cookies = cookieManager.getCookie(siteName);

        if (cookies != null){
            String[] temp=cookies.split(";");
            for (String ar1 : temp ){
                if(ar1.contains(cookieName)){
                    String[] temp1=ar1.split("=");
                    CookieValue = temp1[1];
                    break;
                }
            }
        }

        return CookieValue;
    }
}
