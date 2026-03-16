package com.studio08.xbgamestream.Authenticate;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;
import java.util.Map;

public class LoginClientV4 {
    public interface LoginClientListener {
        void onLoginComplete();
        void errorMessage(String error);
        void showDialog();
        void hideDialog();
    }
    public static DefaultRetryPolicy volleyPolicy = new DefaultRetryPolicy(
            60000,
            0,
            DefaultRetryPolicy.DEFAULT_BACKOFF_MULT
    );
    private Context context;
    private StreamWebview loginWebview = null;
    LoginClientV4.LoginClientListener listener = null;
    private EncryptClient encryptClient = null;

    // tmp redirect values
    private JSONObject redirectTmpData;
    private String redirectTmpUri;
    private String headerTmpRedirectLocation;
    private int retryAttempts = 0;

    public LoginClientV4(Context context, StreamWebview webview) {
        this.context = context;
        this.listener = null;
        this.loginWebview = webview;
        this.encryptClient = new EncryptClient(this.context);
        setupWebviewListeners();
    }

    // Assign the listener implementing events interface that will receive the events
    public void setCustomObjectListener(LoginClientV4.LoginClientListener listener) {
        this.listener = listener;
    }

    public void loginButtonClicked() {
        appendLogs("Starting loginButtonClicked");
        JSONObject existingXal = getSavedXalToken();
        if (existingXal == null){
            getRedirectUriFromApi();
        } else {
            updateExistingXalTokens(existingXal);
        }
    }

    private void updateExistingXalTokens(JSONObject xalTokens){
        appendLogs("updateExistingXalTokens: " + xalTokens.toString());
        if (listener != null){
            listener.showDialog();
        }

        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject postData = new JSONObject();

        try {
            JSONObject xalTokensData = new JSONObject();
            xalTokensData.put("xalTokens", xalTokens);

            // put login region data
            String loginRegionIp = this.encryptClient.getValue("loginRegionIp");
            if (loginRegionIp != null){
                xalTokensData.put("loginRegionIp", loginRegionIp);
            }

            postData.put("data", xalTokensData);
        } catch (JSONException e) {
            e.printStackTrace();
            showError(listener, "Error Logging In. Try Clearing Cache. Failed to extract Xal Tokens.");
            return;
        }

        appendLogs("Data: " + postData.toString());

        // Create a JsonObjectRequest with a custom StringRequest body
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, ApiClient.TOKEN_DATA_ENDPOINT, postData,
                response -> {
                    handleXalTokenResponse(response);
                },
                error -> {
                    appendLogs("updateExistingXalTokens failed:" + error.getLocalizedMessage());
                    showError(listener, "Error Logging In. Try Clearing Cache. Failed to update Xal Tokens.");
                });

        stringRequest.setRetryPolicy(volleyPolicy);
        queue.add(stringRequest);
    }

    private void getRedirectUriFromApi(){
        appendLogs("getRedirectUriFromApi");
        RequestQueue queue = Volley.newRequestQueue(context);
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, ApiClient.TOKEN_DATA_ENDPOINT, null,
                response -> {
                    handleRedirectUriResponse(response);
                },
                error -> {
                    appendLogs("getRedirectUriFromApi failed: " + error.networkResponse);
                    showError(listener, "Failed getting redirect url from API.");
                }
        );
        stringRequest.setRetryPolicy(volleyPolicy);
        queue.add(stringRequest);
    }

    private void exchangeRedirectUriForTokens(String redirectUriString) {
        appendLogs("exchangeRedirectUriForTokens");
        if(listener != null){
            listener.showDialog();
        }

        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject postData = new JSONObject();

        try {
            // Copy data to new object
            JSONObject dataRedirectTmpData = new JSONObject(redirectTmpData.toString());

            // append region data to new object
            String loginRegionIp = this.encryptClient.getValue("loginRegionIp");
            if (loginRegionIp != null){
                dataRedirectTmpData.put("loginRegionIp", loginRegionIp);
            }

            // pass new object to postData
            postData.put("data", dataRedirectTmpData);
            postData.put("redirectURI", redirectUriString);
        } catch (JSONException e) {
            e.printStackTrace();
            showError(listener, "Error exchanging redirect uri for tokens. Try clearing cache and logging in again.");
            return;
        }

        appendLogs("Data: " + postData.toString());

        // Create a JsonObjectRequest with a custom StringRequest body
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, ApiClient.TOKEN_DATA_ENDPOINT, postData,
                response -> {
                    handleXalTokenResponse(response);
                },
                error -> {
                    appendLogs("exchangeRedirectUriForTokens failed:" + error.getLocalizedMessage());
                    showError(listener, "Error Logging In. Try Clearing Cache. Failed retrieving tokens from redirect url.");
                });

        stringRequest.setRetryPolicy(volleyPolicy);
        queue.add(stringRequest);
    }

    private JSONObject getSavedXalToken() {
        return encryptClient.getJSONObject("xalTokens");
    }

    private void setupWebviewListeners() {
        loginWebview.setWebViewClient(new WebViewClient(){

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                Log.e("LoginClientV4", "shouldOverrideUrlLoading: " + url + ". headerTmpRedirectLocation:" + headerTmpRedirectLocation);

                if (url.contains(headerTmpRedirectLocation)) {
                    appendLogs("Found redirect: " + url);
                    String error = request.getUrl().getQueryParameter("error");
                    String errorDesc = request.getUrl().getQueryParameter("error_description");

                    if (error != null) { // back button pressed or failure
                        Toast.makeText(context, "Error: " + error + " - " + errorDesc, Toast.LENGTH_LONG).show();
                        listener.errorMessage("Failed: " + error + " - " +  errorDesc);
                    } else {
                        exchangeRedirectUriForTokens(url);
                    }
                    return true;
                }


                // handle proxy redirect logic
                String urlWithoutDomain = url.replace("https://xbgamestreamproxy.com/a/", "");
                String decodedURLWithoutDomain = Helper.xorDecode(urlWithoutDomain);
                Log.e("LoginClientV4", "rawUrl: " + decodedURLWithoutDomain + ". headerTmpRedirectLocation:" + headerTmpRedirectLocation);
                if (decodedURLWithoutDomain.contains(headerTmpRedirectLocation)) {
                    appendLogs("Found proxy redirect: " + decodedURLWithoutDomain);
                    exchangeRedirectUriForTokens(decodedURLWithoutDomain);
                    return true; // Return true to block the URL from loading
                }

                return false; // Return false to allow the WebView to load the URL
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                Log.e("HERE", "Page started:" + url);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                // Here you can check your new URL.
                super.onPageFinished(view, url);
                Log.e("HERE", "Page Finished:" + url);

            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error){
                appendLogs( "Error: " + request.getUrl());
                appendLogs( "Error: " +  loginWebview.getUrl());
            }
        });
    }

    private static void appendLogs(String data){
        Log.e("LoginV4", data);
    }

    public void handleXalTokenResponse(JSONObject response){
        try {
            appendLogs("handleXalTokenResponse" + response.toString());

            String type = response.getString("type");
            String error = response.getString("error");
            JSONObject data = response.getJSONObject("data");

            if (type.equals("error")) {
                showError(listener, "Error Logging In. Try again or clear cache. Error: " + error);
            } else if (type.equals("tokens")){
                saveXalTokenData(data, this.context, this.listener);
                if(listener != null){
                    listener.onLoginComplete();
                }
            } else if (type.equals("redirect")) {
                clearTokens();
                handleRedirectUriResponse(response);
            } else {
                showError(listener, "Error Logging In. Try again or clear cache. Invalid login token response.");
            }
        } catch (Exception e){
            e.printStackTrace();
            showError(listener, "Error extracting token response");
        }

    }

    public static void saveXalTokenData(JSONObject data, Context ctx, LoginClientV4.LoginClientListener listener){
        try {
            EncryptClient tmpEncryptClient = new EncryptClient(ctx);

            Log.e("LoginClientv4", "Updated xal tokens: " + data);
            tmpEncryptClient.saveJSONObject("xalData", data);

            JSONObject tokenData = data.getJSONObject("xalTokens");
            tmpEncryptClient.saveJSONObject("xalTokens", tokenData);
        } catch (Exception e){
            e.printStackTrace();
            showError(listener, "Error saving token data.");
        }

    }

    private void
    handleRedirectUriResponse(JSONObject response){
        appendLogs("handleRedirectUriResponse: " + response.toString());

        // dont allow the login window opening flow to run more than 3 times without failing.
        if (this.failDueToMaxRetries()){
            return;
        }

        try {
            String url = response.getString("redirectURI");
            JSONObject redirectData = response.getJSONObject("data");
            String headerLocation = redirectData.getString("headerRedirectUriLocation");

            // TODO check values are not empty
            appendLogs("Received valid redirect data");

            this.headerTmpRedirectLocation = headerLocation;
            this.redirectTmpData = redirectData;
            this.redirectTmpUri = url;

            openLoginWindow();
        } catch (JSONException e) {
            e.printStackTrace();
            showError(listener, "Error Logging In. Try Clearing Cache. Invalid redirect url response from API.");
        }
    }

    private boolean failDueToMaxRetries(){
        if (this.retryAttempts >= 3){
            Toast.makeText(this.context, "Failed. Max retries reached", Toast.LENGTH_LONG).show();
            if(listener != null){
                listener.errorMessage("Failed. Max retries reached. Try again later.");
            }
            return true;
        }
        this.retryAttempts++;
        return false;
    }

    private void openLoginWindow(){
        appendLogs("open login window called");
        loginWebview.loadUrl(this.redirectTmpUri);
        if(listener != null){
            listener.hideDialog();
        }
    }

    private static void showError(LoginClientV4.LoginClientListener listener, String errorMessage ){
        appendLogs(errorMessage);
        if(listener != null){
            listener.errorMessage(errorMessage);
        }
    }

    private void clearTokens() {
        appendLogs( "clearTokens");

        // Clear all the cookies
//        CookieManager.getInstance().removeAllCookies(null);
//        CookieManager.getInstance().flush();

        encryptClient.deleteValue("xalData");
        encryptClient.deleteValue("xalTokens");
    }
}
