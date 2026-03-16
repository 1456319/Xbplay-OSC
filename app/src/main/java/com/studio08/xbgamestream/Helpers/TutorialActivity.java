package com.studio08.xbgamestream.Helpers;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;

/**
 * Shows the onboarding/tutorial flow. Content is server-hosted; the same flow
 * includes access to third-party license and attribution information.
 */
public class TutorialActivity extends AppCompatActivity {

    private StreamWebview mainWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_tutorial);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setTitle("Tutorial");
        actionBar.hide();

        mainWebView = (StreamWebview) findViewById(R.id.webview1);
        mainWebView.init();
        mainWebView.setBackgroundColor(Color.TRANSPARENT);

        doTutorial(mainWebView);
    }

    public void doTutorial(StreamWebview loginWebview) {
        ApiClient apiClient = new ApiClient(TutorialActivity.this, loginWebview);
        apiClient.setCustomObjectListener(new ApiClient.StreamingClientListener() {
            @Override
            public void onReLoginDetected() {
                // do nothing
            }

            @Override
            public void onCloseScreenDetected() {
                EncryptClient encryptClient = new EncryptClient(TutorialActivity.this);
                encryptClient.saveValue("tutorialShown", "1");
                finish();
            }

            @Override
            public void pressButtonWifiRemote(String type) {

            }
            @Override
            public void setOrientationValue(String value) {}

            @Override
            public void vibrate() {}
            @Override
            public void genericMessage(String type, String msg) {}
        });
        boolean showFullTutorial = getIntent().getBooleanExtra("show_full", false);
        apiClient.doTutorialScreens(showFullTutorial);
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(TutorialActivity.this)
            .setTitle("Exit")
            .setMessage("Are you sure you want to exit tutorial")
            .setCancelable(true)
            .setPositiveButton("Close Tutorial", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    EncryptClient encryptClient = new EncryptClient(TutorialActivity.this);
                    encryptClient.saveValue("tutorialShown", "1");
                    Toast.makeText(TutorialActivity.this, "You can view the tutorial anytime in the settings", Toast.LENGTH_LONG).show();
                    TutorialActivity.this.finish();
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                }
            })
            .show();
    }
}