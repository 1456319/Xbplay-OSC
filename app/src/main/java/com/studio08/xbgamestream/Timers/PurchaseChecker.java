package com.studio08.xbgamestream.Timers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.RewardedAdLoader;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.Web.ApiClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class PurchaseChecker {
    PCheckInterface listener;
    Context context;
    Handler handler = new Handler();
    Runnable runnable;
    int delay = 5 * 60 * 1000; // 5 minutes

    public PurchaseChecker(Context ctx, PCheckInterface list){
        this.context = ctx;
        this.listener = list;
    }

    public void start(){
        try {
            if (handler != null) {
                handler.postDelayed(runnable = new Runnable() {
                    public void run() {
                        Log.e("PCheck", "PCheck started");
                        handler.postDelayed(runnable, delay);

                        if(listener != null){
                            listener.PCheckTriggered();
                        }
                    }
                }, delay);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    public void stop(){
        try {
            if (handler != null) {
                handler.removeCallbacks(runnable); //stop handler when activity not visible
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    // below this is code for VR specific license check -_-
    public void showLicenseCheckDialog(boolean allowCancel){
        AlertDialog.Builder dialog = new AlertDialog.Builder(context)
                .setTitle("Unlock Full Version")
                .setMessage(
                        "1. Download the official XBPlay app from Google Play or the Apple App Store on any mobile device.\n" +
                        "2. Login to your Microsoft account in the XBPlay mobile app.\n" +
                        "3. Purchase the full version of the app by clicking the 'Unlock Full Version' button in the settings of the mobile app.\n" +
                        "4. Return to this app and click the check license button below.\n\n" +
                        "If you followed step 1 through 3 correctly, it will unlock this app.")
                .setCancelable(allowCancel)
                .setPositiveButton("Check License", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // reset cache timer
                        SharedPreferences freqPrefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
                        SharedPreferences.Editor editor = freqPrefs.edit();
                        editor.putLong("nextMakeGetTokenRequest", 0);
                        editor.apply();

                        doLookupPCheck(allowCancel, false, false);
                    }
                });

        if (allowCancel){ // if hit from settings page where dont want to lock user out
            dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {

                }
            });
        } else {
            dialog.setNegativeButton("Exit App", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    ((Activity)context).finishAffinity();
                }
            });
        }

        dialog.show();
    }



    // hide popup if started from main app on start, allow cancel if started from settings page
    // dont use cache if being hit by UI
    public void doLookupPCheck(boolean allowCancel, boolean hidePopup, boolean useCache){
        try {
            EncryptClient encryptClient = new EncryptClient(context);
            String gsToken = encryptClient.getValue("gsToken");

            if(TextUtils.isEmpty(gsToken)) {
                if(!hidePopup){
                    createPopup("Sign-in Required", "You must sign-in to your Xbox Live account first. Click the Login button, then try again.");
                }
                return;
            }

            // only hit api every 24 hours
            boolean checkToken = RewardedAdLoader.shouldCheckNewToken(context);
            if (!checkToken && useCache){
                Log.e("Purchase", "Not checking token. Not expired yet");
                return;
            }

            // hit api
            String url = ApiClient.TOKEN_GET_BASE_URL;
            RequestQueue queue = Volley.newRequestQueue(context);
            StringRequest getRequest = new StringRequest(Request.Method.POST, url,
                    new Response.Listener<String>() {
                        @Override
                        public void onResponse(String response) {
                            try {
                                JSONObject jsonResponse = new JSONObject(response);
                                int active = jsonResponse.getInt("activePurchase");

                                // show cross restore status on new unlock
                                if (active == 1 && !hidePopup){
                                    createPopup("License Granted!", "You have successfully unlocked the full version of this app. Thank you!");
                                }

                                // unlock / lock
                                RewardedAdLoader.setPurchaseItem(active == 1, context);

                                // save that we were able to hit the cache endpoint so we don't again
                                SharedPreferences freqPrefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
                                SharedPreferences.Editor editor = freqPrefs.edit();
                                editor.putLong("nextMakeGetTokenRequest", System.currentTimeMillis() + RewardedAdLoader.GET_TOKEN_CACHE_DURATION);
                                editor.apply();

                                if (active == 0){
                                    if(!hidePopup) {
                                        createFailureDialog(allowCancel);
                                    } else {
                                        Toast.makeText(context, "License Check Failed", Toast.LENGTH_SHORT).show();
                                    }
                                }
                            } catch (JSONException e) {
                                e.printStackTrace();
                                RewardedAdLoader.setPurchaseItem(false, context);
                                if(!hidePopup) {
                                    createFailureDialog(allowCancel);
                                } else {
                                    Toast.makeText(context, "License Check Failed", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    },
                    new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // TODO Auto-generated method stub
                            RewardedAdLoader.setPurchaseItem(false, context);
                            if(!hidePopup) {
                                createFailureDialog(allowCancel);
                            } else {
                                Toast.makeText(context, "License Check Failed", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }
            ) {
                @Override
                public String getBodyContentType() {
                    return "application/json; charset=utf-8";
                }

                @Override
                public byte[] getBody() throws AuthFailureError {
                    try {
                        JSONObject data = new JSONObject();
                        data.put("gsToken", gsToken);
                        return data.toString().getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        e.printStackTrace();
                        return null;
                    }
                }
            };
            queue.add(getRequest);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void createPopup(String title, String message) {
        try {
            new AlertDialog.Builder(context)
                    .setTitle(title)
                    .setMessage(message)
                    .setCancelable(false)
                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    private void createFailureDialog(boolean allowCancel) {
        try {
            AlertDialog.Builder dialog = new AlertDialog.Builder(context)
                    .setTitle("License Check Failed")
                    .setMessage("License not found. Ensure you signed in and purchased the XBPlay app. Then try again.")
                    .setCancelable(allowCancel)
                    .setNegativeButton("Exit App", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            ((Activity)context).finishAffinity();
                        }
                    })
                    .setPositiveButton("Try Again", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            doLookupPCheck(allowCancel, false, false);
                        }
                    });

            if (allowCancel){
                dialog.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });
            } else {
                dialog.setNegativeButton("Exit App", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        ((Activity)context).finishAffinity();
                    }
                });
            }
            dialog.show();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
