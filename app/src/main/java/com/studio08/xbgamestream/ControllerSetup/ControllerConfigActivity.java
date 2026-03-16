package com.studio08.xbgamestream.ControllerSetup;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;

import org.json.JSONArray;
import org.json.JSONObject;

public class ControllerConfigActivity extends AppCompatActivity {

    private StreamWebview mainWebView;
    ApiClient streamingClient;
    String currentButton;

    // listener - fires when streaming detects auth is required
    ApiClient.StreamingClientListener saveControllerListener = new ApiClient.StreamingClientListener() {
        @Override
        public void onReLoginDetected() {
        }
        // closing screen not supported in this view
        @Override
        public void onCloseScreenDetected() {}

        @Override
        public void pressButtonWifiRemote(String type) {

        }
        @Override
        public void setOrientationValue(String value) {}

        @Override
        public void vibrate() {}

        @Override
        public void genericMessage(String type, String msg) {
            if(type.equals("physical_controller_config_save")) {
                saveControllerConfig(msg);
            } if(type.equals("physical_controller_config_current_button")) {
                currentButton = msg;
            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_physical_controller_config);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Physical Controller Setup");

        mainWebView = (StreamWebview) findViewById(R.id.webview1);
        mainWebView.init();

        doSetup();
    }

    // show webpage prompting user to login. Attempt to use cache
    public void doSetup() {
        streamingClient = new ApiClient(ControllerConfigActivity.this, mainWebView);
        streamingClient.setCustomObjectListener(saveControllerListener);
        streamingClient.doPhysicalControllerSetup();
    }

    public void saveControllerConfig(String config) {
        try {
            JSONObject response = new JSONObject(config);
            SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            // object to shared prefs
            editor.putString(response.getString("name"), response.getJSONObject("data").toString());
            editor.apply();

            // get list of custom controller configs
            String allControllerConfigs = prefs.getString("physical_controller_configs", "[]");

            // load list into json array
            JSONArray allConfigs = new JSONArray(allControllerConfigs);
            int length = allConfigs.length();

            // place new config in array
            allConfigs.put(length, response);

            // save new config to shared prefs
            editor.putString("physical_controller_configs", allConfigs.toString());
            editor.apply();

        } catch (Exception e){
            Toast.makeText(ControllerConfigActivity.this, "Error saving config" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.e("HERE", "KEYPRESS: " + keyCode + " SOURCE: " + event.getSource() + " Current Button:" + currentButton);
        return super.onKeyDown(keyCode, event);
    }
}