package com.studio08.xbgamestream.Authenticate;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import androidx.appcompat.widget.Toolbar;

import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Web.StreamWebview;

import org.apache.http.util.TextUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private StreamWebview mainWebView;
    private AlertDialog dialog;
    private String serverId;
    private String consoleName;
    LoginClientV3 loginClient;

    private boolean loginComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Login");

        mainWebView = (StreamWebview) findViewById(R.id.webview1);
        mainWebView.init();
        mainWebView.setBackgroundColor(Color.TRANSPARENT);
        dialog = new AlertDialog.Builder(this)
            .setTitle("Please Wait...")
            .setMessage("Authenticating with Xbox Live")
            .setCancelable(false)
            .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    closeActivity(false);
                }
            })
            .setNegativeButton("Hide",null)
            .show();

        WindowManager.LayoutParams lp = dialog.getWindow().getAttributes();
        lp.dimAmount=0.97f;
        dialog.getWindow().setAttributes(lp);
        dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);

        doLogin(mainWebView);
    }

    // Listener - fires when login has completed
    LoginClientV3.LoginClientListener loginReadyListener = new LoginClientV3.LoginClientListener() {
        @Override
        public void onLoginComplete(String consoles) {
            try {
                loginComplete = true;
                if(dialog != null && dialog.isShowing()){
                    dialog.dismiss();
                }
                promptForConsole(consoles);
            } catch(Exception e){
                e.printStackTrace();
            }
        }

        @Override
        public void statusMessage(String message) {
            Log.e("StatusMessage", message);
            runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    showDialog();
                    dialog.setMessage(message);
                }
            });
        }

        @Override
        public void hideDialog() {
            try {
                if(dialog != null) {
                    dialog.hide();
                }
            } catch (Exception e){}
        }

        @Override
        public void showDialog() {
            try {
                if(loginComplete) {
                    Log.e("HRE", "Ignoring show dialog because already got valid response");
                } else {
                    dialog.show();
                }
            } catch (Exception e){}
        }

        @Override
        public void genericMessage(String type, String message) {
            if(type.equals("cant_login") && message.equals("InvalidCountry")){
                Toast.makeText(LoginActivity.this, "Possibly Invalid Location", Toast.LENGTH_LONG).show();
                new AlertDialog.Builder(LoginActivity.this)
                        .setTitle("Invalid Location")
                        .setMessage("You appear to be in a location that doesn't support XCloud. That's OK! Click the 'exit' button and try again. It should work the second time. If you still see this error. Please report it!")
                        .setCancelable(false)
                        .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                closeActivity(false);
                            }
                        })
                        .setNegativeButton("Hide",null)
                        .show();
            }
        }
    };

    public void promptForConsole(String consoleList) {
        try {
            JSONObject results = new JSONObject(consoleList);
            JSONArray consoles = results.getJSONArray("results");

            String [] consolesArray = new String[consoles.length()];
            String [] serverIdArray = new String[consoles.length()];

            for (int i = 0; i < consoles.length(); i++) {
                String consoleName = consoles.getJSONObject(i).getString("deviceName");
                String consoleId = consoles.getJSONObject(i).getString("serverId");
                consolesArray[i] = consoleName + " - " + consoleId;
                serverIdArray[i] = consoleId;
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setCancelable(false);

            if(consoles.length() < 1) {
                SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
                boolean ignoreNoConsolesWarning = prefs.getBoolean("ignore_no_consoles_warning", false);

                if(ignoreNoConsolesWarning){
                    Toast.makeText(this, "Warning: No consoles found.", Toast.LENGTH_LONG).show();
                    closeActivity(false);
                    return;
                }

                builder.setTitle("Warning: No Consoles Found");
                builder.setMessage("We couldn't find any Xbox consoles associated with your account. Ensure 'Remote Features'" +
                    " is enabled in your console's 'Settings->Devices & Connections' page for the same profile you signed in for in this app. Additionally, make sure you are connected to the same WiFi network as your console.\n\nTo login to a different account, click 'clear cache' in the settings of this app.\n\nIf you only intend to use xCloud, ignore this warning.");

                // add OK and Cancel buttons
                builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        closeActivity(true);
                    }
                });

                builder.setNegativeButton("Don't Show Again", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("ignore_no_consoles_warning", true);
                        editor.apply();

                        closeActivity(false);
                    }
                });

                builder.setNeutralButton("Help", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://support.xbox.com/en-US/help/games-apps/game-setup-and-play/how-to-set-up-remote-play"));
                        startActivity(browserIntent);

                        closeActivity(false);
                    }
                });

            } else {
                EncryptClient encryptClient = new EncryptClient(LoginActivity.this);

                if (!TextUtils.isEmpty(encryptClient.getValue("rememberConsole")) && !TextUtils.isEmpty(encryptClient.getValue("serverId"))){
                    serverId = encryptClient.getValue("serverId");
                    closeActivity(true);
                    return;
                }
                builder.setTitle("Choose a default console");
                int checkedItem = 0;
                serverId = serverIdArray[checkedItem];
                consoleName = consolesArray[checkedItem];

                builder.setSingleChoiceItems(consolesArray, checkedItem, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // user checked an item
                        serverId = serverIdArray[which];
                        consoleName = consolesArray[which];
                    }
                });

                // add OK and Cancel buttons
                builder.setNeutralButton("Select", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        encryptClient.saveValue("serverId", serverId);
                        closeActivity(true);
                    }
                });

                // add OK and Cancel buttons
                builder.setPositiveButton("Remember", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        encryptClient.saveValue("serverId", serverId);
                        encryptClient.saveValue("rememberConsole", serverId);

                        Toast.makeText(LoginActivity.this, "Use 'Settings > Forget Saved Console' to use a new console.", Toast.LENGTH_LONG).show();
                        closeActivity(true);
                    }
                });

                builder.setNegativeButton("Back", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(LoginActivity.this, "Cant connect to console. User closed" , Toast.LENGTH_LONG).show();
                        closeActivity(false);
                    }
                });
            }

            // create and show the alert dialog
            AlertDialog dialog = builder.create();

            try {
                dialog.show();
            } catch( Exception e){}
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void closeActivity(boolean success) {
        Intent returnIntent = new Intent();
        if (success) {
            returnIntent.putExtra("serverId", serverId);
            returnIntent.putExtra("consoleName", consoleName);
            setResult(Activity.RESULT_OK, returnIntent);
        } else {
            setResult(Activity.RESULT_CANCELED, returnIntent);
        }
        cleanUp();
        finish();
    }

    // show webpage prompting user to login. Attempt to use cache
    public void doLogin(StreamWebview loginWebview) {
        this.loginClient = new LoginClientV3(LoginActivity.this, loginWebview);
        loginClient.setCustomObjectListener(this.loginReadyListener);
        loginClient.doLogin();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        this.cleanUp();
    }

    public void cleanUp() {
        try {
            if (mainWebView != null) {
                ViewGroup vg = (ViewGroup) (mainWebView.getParent());
                vg.removeAllViews();
                this.mainWebView.clearHistory();
                this.mainWebView.clearCache(false);
                this.mainWebView.loadUrl("about:blank");
                this.mainWebView.onPause();
                this.mainWebView.removeAllViews();
                this.mainWebView.destroy();
                this.mainWebView = null;
            }
            if(this.loginClient != null){
                this.loginClient.pollerRunning = false;
            }
        } catch(Exception e) {
            e.printStackTrace();
            mainWebView = null;
        }
    }
}