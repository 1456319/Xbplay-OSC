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

public class LoginActivityV4 extends AppCompatActivity {
    private StreamWebview mainWebView;
    private AlertDialog dialog;
    LoginClientV4 loginClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_v4);

        mainWebView = findViewById(R.id.webview1);
        mainWebView.init();
        mainWebView.setBackgroundColor(Color.TRANSPARENT);
        dialog = new AlertDialog.Builder(this)
//            .setTitle("Please Wait...")
            .setMessage("Loading. Please wait...")
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
    LoginClientV4.LoginClientListener loginReadyListener = new LoginClientV4.LoginClientListener() {
        @Override
        public void onLoginComplete() {
            try {
                if(dialog != null && dialog.isShowing()){
                    dialog.dismiss();
                }
            } catch(Exception e){
                e.printStackTrace();
            }
            closeActivity(true);
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
                dialog.show();
            } catch (Exception e){}
        }

        @Override
        public void errorMessage(String error) {
            try {
                if (!isFinishing() && !isDestroyed()) { // Check if the activity is valid
                    new AlertDialog.Builder(LoginActivityV4.this)
                            .setTitle("Error")
                            .setMessage(error)
                            .setCancelable(false)
                            .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    closeActivity(false);
                                }
                            })
                            .show();
                } else {
                    // Handle the case where the activity is no longer valid
                    Log.e("LoginActivityV4", "Activity is not valid to show the dialog");
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    };

    private void closeActivity(boolean success) {
        Intent returnIntent = new Intent();
        if (success) {
            setResult(Activity.RESULT_OK, returnIntent);
        } else {
            setResult(Activity.RESULT_CANCELED, returnIntent);
        }
        cleanUp();
        finish();
    }

    // show webpage prompting user to login. Attempt to use cache
    public void doLogin(StreamWebview loginWebview) {
        this.loginClient = new LoginClientV4(getApplicationContext(), loginWebview);
        loginClient.setCustomObjectListener(this.loginReadyListener);
        loginClient.loginButtonClicked();
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
        } catch(Exception e) {
            e.printStackTrace();
            mainWebView = null;
        }
    }
}