package com.studio08.xbgamestream.Authenticate;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.volley.AuthFailureError;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.IPublicClientApplication;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.exception.MsalException;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Web.StreamWebview;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
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
    LoginClientV4.LoginClientListener listener = null;
    private EncryptClient encryptClient = null;
    private ISingleAccountPublicClientApplication mSingleAccountApp;

    public LoginClientV4(Context context, StreamWebview webview) {
        this.context = context;
        this.listener = null;
        this.encryptClient = new EncryptClient(this.context);

        PublicClientApplication.createSingleAccountPublicClientApplication(context,
                R.raw.auth_config_single_account,
                new IPublicClientApplication.ISingleAccountApplicationCreatedListener() {
                    @Override
                    public void onCreated(ISingleAccountPublicClientApplication application) {
                        mSingleAccountApp = application;
                        appendLogs("MSAL initialized");
                    }

                    @Override
                    public void onError(MsalException exception) {
                        appendLogs("MSAL initialization failed: " + exception.toString());
                        showError(listener, "Failed to initialize MSAL.");
                    }
                });
    }

    public void setCustomObjectListener(LoginClientV4.LoginClientListener listener) {
        this.listener = listener;
    }

    public void loginButtonClicked() {
        appendLogs("Starting loginButtonClicked");
        if (mSingleAccountApp == null) {
            showError(listener, "MSAL not initialized yet.");
            return;
        }

        mSingleAccountApp.signIn((Activity) context, null, new String[]{"Xboxlive.signin", "Xboxlive.offline_access"}, new AuthenticationCallback() {
            @Override
            public void onSuccess(IAuthenticationResult authenticationResult) {
                appendLogs("MSAL SignIn Success");
                if (listener != null) {
                    listener.showDialog();
                }
                exchangeMsalTokenForXboxLive(authenticationResult.getAccessToken());
            }

            @Override
            public void onError(MsalException exception) {
                appendLogs("MSAL SignIn Error: " + exception.toString());
                showError(listener, "MSAL SignIn Error: " + exception.getMessage());
            }

            @Override
            public void onCancel() {
                appendLogs("MSAL SignIn Cancelled");
                showError(listener, "Sign in cancelled");
            }
        });
    }

    private void exchangeMsalTokenForXboxLive(String msalToken) {
        appendLogs("exchangeMsalTokenForXboxLive");
        RequestQueue queue = Volley.newRequestQueue(context);

        JSONObject postData = new JSONObject();
        try {
            JSONObject properties = new JSONObject();
            properties.put("AuthMethod", "RPS");
            properties.put("SiteName", "user.auth.xboxlive.com");
            properties.put("RpsTicket", "d=" + msalToken);

            postData.put("RelyingParty", "http://auth.xboxlive.com");
            postData.put("TokenType", "JWT");
            postData.put("Properties", properties);
        } catch (JSONException e) {
            e.printStackTrace();
            showError(listener, "Error building Xbox Live auth request.");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, "https://user.auth.xboxlive.com/user.authenticate", postData,
                response -> {
                    try {
                        String xboxLiveToken = response.getString("Token");
                        exchangeXboxLiveTokenForXSTS(xboxLiveToken);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        showError(listener, "Error parsing Xbox Live token.");
                    }
                },
                error -> {
                    appendLogs("exchangeMsalTokenForXboxLive failed: " + error.toString());
                    showError(listener, "Failed to get Xbox Live token.");
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                headers.put("x-xbl-contract-version", "1");
                return headers;
            }
        };

        request.setRetryPolicy(volleyPolicy);
        queue.add(request);
    }

    private void exchangeXboxLiveTokenForXSTS(String xboxLiveToken) {
        appendLogs("exchangeXboxLiveTokenForXSTS");
        RequestQueue queue = Volley.newRequestQueue(context);

        JSONObject postData = new JSONObject();
        try {
            JSONObject properties = new JSONObject();
            properties.put("SandboxId", "RETAIL");
            JSONArray userTokens = new JSONArray();
            userTokens.put(xboxLiveToken);
            properties.put("UserTokens", userTokens);

            postData.put("RelyingParty", "http://gssv.xboxlive.com/");
            postData.put("TokenType", "JWT");
            postData.put("Properties", properties);
        } catch (JSONException e) {
            e.printStackTrace();
            showError(listener, "Error building XSTS auth request.");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(Request.Method.POST, "https://xsts.auth.xboxlive.com/xsts.authorize", postData,
                response -> {
                    handleXSTSResponse(response);
                },
                error -> {
                    appendLogs("exchangeXboxLiveTokenForXSTS failed: " + error.toString());
                    showError(listener, "Failed to get XSTS token.");
                }) {
            @Override
            public Map<String, String> getHeaders() throws AuthFailureError {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Accept", "application/json");
                headers.put("x-xbl-contract-version", "1");
                return headers;
            }
        };

        request.setRetryPolicy(volleyPolicy);
        queue.add(request);
    }

    private void handleXSTSResponse(JSONObject xstsResponse) {
        appendLogs("handleXSTSResponse");
        try {
            JSONObject displayClaims = xstsResponse.getJSONObject("DisplayClaims");
            JSONArray xui = displayClaims.getJSONArray("xui");
            JSONObject uhsObject = xui.getJSONObject(0);
            String uhs = uhsObject.getString("uhs");

            JSONObject webTokenData = new JSONObject();
            webTokenData.put("Token", xstsResponse.getString("Token"));
            webTokenData.put("DisplayClaims", displayClaims);

            JSONObject webToken = new JSONObject();
            webToken.put("data", webTokenData);

            JSONObject xalData = new JSONObject();
            xalData.put("webToken", webToken);

            JSONObject xalTokens = new JSONObject();
            xalTokens.put("uhs", uhs);
            xalTokens.put("webTokenFinal", xstsResponse.getString("Token"));

            JSONObject data = new JSONObject();
            data.put("xalData", xalData);
            data.put("xalTokens", xalTokens);

            saveXalTokenData(data, this.context, this.listener);

            if(listener != null){
                listener.onLoginComplete();
            }
        } catch (JSONException e) {
            e.printStackTrace();
            showError(listener, "Error parsing XSTS response.");
        }
    }

    private static void appendLogs(String data){
        Log.e("LoginV4", data);
    }

    public static void saveXalTokenData(JSONObject data, Context ctx, LoginClientV4.LoginClientListener listener){
        try {
            EncryptClient tmpEncryptClient = new EncryptClient(ctx);

            JSONObject xalData = data.getJSONObject("xalData");
            tmpEncryptClient.saveJSONObject("xalData", xalData);

            JSONObject xalTokens = data.getJSONObject("xalTokens");
            tmpEncryptClient.saveJSONObject("xalTokens", xalTokens);
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
        appendLogs("clearTokens");
        encryptClient.deleteValue("xalData");
        encryptClient.deleteValue("xalTokens");
        if (mSingleAccountApp != null) {
            mSingleAccountApp.signOut(new ISingleAccountPublicClientApplication.SignOutCallback() {
                @Override
                public void onSignOut() {
                    appendLogs("MSAL SignOut Complete");
                }

                @Override
                public void onError(@NonNull MsalException exception) {
                    appendLogs("MSAL SignOut Error");
                }
            });
        }
    }
}
