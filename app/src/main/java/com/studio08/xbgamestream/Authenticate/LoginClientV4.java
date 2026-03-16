package com.studio08.xbgamestream.Authenticate;

import android.app.Activity;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.IPublicClientApplication;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.exception.MsalException;

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
    private Activity context;
    private StreamWebview loginWebview;
    LoginClientV4.LoginClientListener listener;
    private EncryptClient encryptClient;

    private ISingleAccountPublicClientApplication mSingleAccountApp;

    public LoginClientV4(Activity context, StreamWebview webview) {
        this.context = context;
        this.listener = null;
        this.loginWebview = webview;
        this.encryptClient = new EncryptClient(this.context);

        PublicClientApplication.createSingleAccountPublicClientApplication(context,
                R.raw.auth_config,
                new IPublicClientApplication.ISingleAccountApplicationCreatedListener() {
                    @Override
                    public void onCreated(ISingleAccountPublicClientApplication application) {
                        mSingleAccountApp = application;
                    }
                    @Override
                    public void onError(MsalException exception) {
                        appendLogs("Error creating MSAL app: " + exception.getMessage());
                    }
                });
    }

    // Assign the listener implementing events interface that will receive the events
    public void setCustomObjectListener(LoginClientV4.LoginClientListener listener) {
        this.listener = listener;
    }

    public void loginButtonClicked() {
        appendLogs("Starting loginButtonClicked");
        if (mSingleAccountApp == null) {
            showError(listener, "MSAL app not initialized yet. Try again.");
            return;
        }

        String[] scopes = {"XboxLive.signin"};
        mSingleAccountApp.signIn(context, null, scopes, new AuthenticationCallback() {
            @Override
            public void onSuccess(IAuthenticationResult authenticationResult) {
                String msalAccessToken = authenticationResult.getAccessToken();
                appendLogs("MSAL success. Access token obtained.");
                // Next plan step: Exchange MSAL token for Xbox tokens.
                exchangeMsalForXboxLive(msalAccessToken);
            }

            @Override
            public void onError(MsalException exception) {
                showError(listener, "MSAL login error: " + exception.getMessage());
            }

            @Override
            public void onCancel() {
                appendLogs("MSAL login cancelled.");
            }
        });
    }

    private void exchangeMsalForXboxLive(String msalToken) {
        appendLogs("exchangeMsalForXboxLive");
        if (listener != null) {
            listener.showDialog();
        }

        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject postData = new JSONObject();

        try {
            JSONObject properties = new JSONObject();
            properties.put("AuthMethod", "RPS");
            properties.put("SiteName", "user.auth.xboxlive.com");
            properties.put("RpsTicket", "d=" + msalToken);
            postData.put("Properties", properties);
            postData.put("RelyingParty", "http://auth.xboxlive.com");
            postData.put("TokenType", "JWT");
        } catch (JSONException e) {
            e.printStackTrace();
            showError(listener, "Error building Xbox Live auth request.");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, "https://user.auth.xboxlive.com/user/authenticate", postData,
                response -> {
                    try {
                        String userToken = response.getString("Token");
                        exchangeXboxLiveForXsts(userToken);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        showError(listener, "Invalid response from Xbox Live auth.");
                    }
                },
                error -> {
                    appendLogs("exchangeMsalForXboxLive failed: " + error.toString());
                    showError(listener, "Failed to authenticate with Xbox Live.");
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                headers.put("x-xbl-contract-version", "1");
                return headers;
            }
        };

        request.setRetryPolicy(volleyPolicy);
        queue.add(request);
    }

    private void exchangeXboxLiveForXsts(String userToken) {
        appendLogs("exchangeXboxLiveForXsts");

        RequestQueue queue = Volley.newRequestQueue(context);
        JSONObject postData = new JSONObject();

        try {
            JSONObject properties = new JSONObject();
            properties.put("SandboxId", "RETAIL");
            org.json.JSONArray userTokens = new org.json.JSONArray();
            userTokens.put(userToken);
            properties.put("UserTokens", userTokens);
            postData.put("Properties", properties);
            postData.put("RelyingParty", "http://gssv.xboxlive.com/");
            postData.put("TokenType", "JWT");
        } catch (JSONException e) {
            e.printStackTrace();
            showError(listener, "Error building XSTS auth request.");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, "https://xsts.auth.xboxlive.com/xsts/authorize", postData,
                response -> {
                    try {
                        String xstsToken = response.getString("Token");
                        JSONObject displayClaims = response.getJSONObject("DisplayClaims");
                        org.json.JSONArray xui = displayClaims.getJSONArray("xui");
                        String uhs = xui.getJSONObject(0).getString("uhs");

                        // Next step: save to EncryptClient
                        saveFinalTokens(xstsToken, uhs);

                    } catch (JSONException e) {
                        e.printStackTrace();
                        showError(listener, "Invalid response from XSTS auth.");
                    }
                },
                error -> {
                    appendLogs("exchangeXboxLiveForXsts failed: " + error.toString());
                    showError(listener, "Failed to authenticate with XSTS.");
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new java.util.HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                headers.put("x-xbl-contract-version", "1");
                return headers;
            }
        };

        request.setRetryPolicy(volleyPolicy);
        queue.add(request);
    }

    private void saveFinalTokens(String xstsToken, String uhs) {
        appendLogs("saveFinalTokens");
        try {
            JSONObject finalData = new JSONObject();
            finalData.put("Token", xstsToken);

            JSONObject displayClaims = new JSONObject();
            org.json.JSONArray xui = new org.json.JSONArray();
            JSONObject uhsObj = new JSONObject();
            uhsObj.put("uhs", uhs);
            xui.put(uhsObj);
            displayClaims.put("xui", xui);

            finalData.put("DisplayClaims", displayClaims);

            saveXalTokenData(finalData, context, listener);

            if (listener != null) {
                listener.onLoginComplete();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            showError(listener, "Failed to construct final token data.");
        }
    }

    private static void appendLogs(String data){
        Log.e("LoginV4", data);
    }

    public static void saveXalTokenData(JSONObject data, Context ctx, LoginClientV4.LoginClientListener listener){
        try {
            EncryptClient tmpEncryptClient = new EncryptClient(ctx);

            Log.e("LoginClientv4", "Updated xal tokens: " + data);
            tmpEncryptClient.saveJSONObject("xalData", data);

            if (data.has("xalTokens")) {
                JSONObject tokenData = data.getJSONObject("xalTokens");
                tmpEncryptClient.saveJSONObject("xalTokens", tokenData);
            } else {
                // If we don't have the old structure with xalTokens, simply save the final data as xalTokens to be backward compatible
                tmpEncryptClient.saveJSONObject("xalTokens", data);
            }
        } catch (Exception e){
            e.printStackTrace();
            showError(listener, "Error saving token data.");
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
        encryptClient.deleteValue("xalData");
        encryptClient.deleteValue("xalTokens");
    }
}
