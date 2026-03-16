package com.studio08.xbgamestream.Web;

import static com.studio08.xbgamestream.Authenticate.LoginClientV4.volleyPolicy;

import android.content.Context;
import android.util.Log;
import android.util.Pair;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.Helper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SmartglassClient {
    Context mContext;

    public SmartglassClient(Context context) {
        this.mContext = context;
    }

    public void sendSmartglassCommand(String msg){
        try {
            Log.d("PWAMM", "sendSmartglassCommand: " + msg);
            Helper.vibrate(mContext);

            // extract tokens
            EncryptClient encryptClient = new EncryptClient(this.mContext);
            JSONObject xalData = encryptClient.getJSONObject("xalData");
            if (xalData == null){
                Toast.makeText(this.mContext, "Login Required", Toast.LENGTH_SHORT).show();
                return;
            }
            Pair<String, String> tokens = extractWebTokenAndUhs(xalData);
            String webTokenFinal = tokens.first;
            String uhs = tokens.second;

            // create payload
            JSONObject body = createSmartglassPayload(msg);
            Log.d("PWAMM", "Payload: " + body.toString());

            // hit api
            hitSmartglassApi(uhs, webTokenFinal, body);

        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void hitSmartglassApi(String uhs, String webTokenFinal, JSONObject body){
        RequestQueue queue = Volley.newRequestQueue(this.mContext);
        JsonObjectRequest stringRequest = new JsonObjectRequest(Request.Method.POST, ApiClient.SMARTGLASS_COMMAND_URL, body,
                response -> {
                    Log.d("SmartglassCommand", "Response: " + response.toString());
                },
                error -> {
                    // Handle the error response
                    int statusCode = error.networkResponse != null ? error.networkResponse.statusCode : -1;
                    if (statusCode == 404) {
                        Toast.makeText(this.mContext, "Send failed. Xbox not responding", Toast.LENGTH_SHORT).show();
                    } else if (statusCode == 401 || statusCode == 403) {
                        Toast.makeText(this.mContext, "Send failed. Try logging in again", Toast.LENGTH_SHORT).show();
                    } else if (statusCode != 200) {
                        Toast.makeText(this.mContext, "Send failed: " + statusCode, Toast.LENGTH_SHORT).show();
                    }
                }) {
            @Override
            public Map<String, String> getHeaders() {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", "application/json");
                headers.put("Authorization", "XBL3.0 x=" + uhs + ";" + webTokenFinal);
                headers.put("Accept-Language", "en-US");
                headers.put("x-xbl-contract-version", "4");
                headers.put("x-xbl-client-name", "XboxApp");
                headers.put("skillplatform", "RemoteManagement");
                headers.put("x-xbl-client-type", "UWA");
                headers.put("x-xbl-client-version", "39.39.22001.0");
                headers.put("MS-CV", "0");
                return headers;
            }
        };

        stringRequest.setRetryPolicy(volleyPolicy);
        queue.add(stringRequest);
    }
    private JSONObject createSmartglassPayload(String inputMessage){
        try {
            JSONObject inputPayload = new JSONObject(inputMessage);
            String consoleId = inputPayload.getString("consoleId");
            String commandType = inputPayload.getString("commandType");
            String command = inputPayload.getString("command");
            JSONArray params = inputPayload.getJSONArray("params");

            JSONObject body = new JSONObject();
            body.put("destination", "Xbox");
            body.put("sessionId", UUID.randomUUID().toString());
            body.put("sourceId", "com.microsoft.smartglass");
            body.put("type", commandType);
            body.put("command", command);
            body.put("parameters", params);
            body.put("linkedXboxId", consoleId);
            return body;
        } catch (Exception e){
            e.printStackTrace();
        }
        return null;
    }

    private Pair<String, String> extractWebTokenAndUhs(JSONObject xalData) throws JSONException {
        // Extract webTokenFinal
        JSONObject webTokens = xalData.getJSONObject("webToken");
        JSONObject webTokensData = webTokens.getJSONObject("data");
        String webTokenFinal = webTokensData.getString("Token");

        // Extract uhs
        JSONObject displayClaims = webTokensData.getJSONObject("DisplayClaims");
        JSONArray xui = displayClaims.getJSONArray("xui");
        JSONObject uhsObject = xui.getJSONObject(0);
        String uhs = uhsObject.getString("uhs");

        // Return as a Pair
        return new Pair<>(webTokenFinal, uhs);
    }
}
