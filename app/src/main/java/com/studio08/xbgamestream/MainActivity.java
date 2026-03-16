package com.studio08.xbgamestream;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

//import com.google.android.ads.mediationtestsuite.MediationTestSuite;
import com.google.android.gms.cast.Cast;
import com.google.android.gms.cast.CastDevice;
import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaLoadRequestData;
import com.google.android.gms.cast.MediaMetadata;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.cast.framework.CastSession;
import com.google.android.gms.cast.framework.CastState;
import com.google.android.gms.cast.framework.CastStateListener;
import com.google.android.gms.cast.framework.IntroductoryOverlay;
import com.google.android.gms.cast.framework.SessionManager;
import com.google.android.gms.cast.framework.SessionManagerListener;
import com.google.android.gms.cast.framework.media.RemoteMediaClient;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.images.WebImage;
import com.google.android.gms.tasks.Task;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.play.core.review.ReviewException;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.play.core.review.model.ReviewErrorCode;
import com.google.androidbrowserhelper.trusted.LauncherActivity;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.ironsource.mediationsdk.IronSource;
import com.studio08.xbgamestream.Controller.ControllerHandler;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.FirebaseAnalyticsClient;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.RewardedAdLoader;
import com.studio08.xbgamestream.Controller.RumbleHelper;
import com.studio08.xbgamestream.Helpers.SettingsActivity;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.Helpers.TutorialActivity;
import com.studio08.xbgamestream.Servers.Server;
import com.studio08.xbgamestream.Timers.PCheckInterface;
import com.studio08.xbgamestream.Timers.PurchaseChecker;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.studio08.xbgamestream.databinding.ActivityMainBinding;
import com.studio08.xbgamestream.ui.xcloud.XcloudFragment;

import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import network.BindService;

public class MainActivity extends AppCompatActivity {

    private AppBarConfiguration mAppBarConfiguration;
    private ActivityMainBinding binding;
    private StreamWebview mainWebView;
    public DrawerLayout drawer;
    public boolean inFullScreenMode = false;
    public NavController navController;
    public Server server;

    public String consoleText = null;
    public String mirrorcastText = null;

    // used for wifi remote only
    public boolean mServiceBound = false;
    public BindService mBoundService;
    public ServiceConnection mServiceConnection;

    CastContext mCastContext;
    public CastSession mCastSession;
    private SessionManager mSessionManager;
    private SessionManagerListener<CastSession> mSessionManagerListener = new SessionManagerListenerImpl();
    private MenuItem mediaRouteMenuItem;
    IntroductoryOverlay overlay;
    public FirebaseAnalyticsClient analyticsClient;
    public RewardedAdLoader rewardedAd;
    public ControllerHandler controllerHandler;

    private Dialog showTrialDialog;
    public String tvCodeUri = ""; // set if started from QR code intent

    public String xCloudShortcutTitleId = null; // if set, xcloud will go into this view right away

    private MenuItem unlockMenuItem;

    private PurchaseChecker purchaseChecker = new PurchaseChecker(MainActivity.this, new PCheckInterface() {
        @Override
        public void PCheckTriggered() {
            try {
                if (rewardedAd != null) {
                    boolean shouldInterruptUser = RewardedAdLoader.shouldShowAd(MainActivity.this);
                    if (shouldInterruptUser){
                        // reload the page to annoy the user
                        if (navController.getCurrentDestination() != null){
                            int id = navController.getCurrentDestination().getId();
                            navController.popBackStack(id, true);
                            navController.navigate(id);
                        }

                        // close any active dialogs
                        if (showTrialDialog != null && showTrialDialog.isShowing()){
                            showTrialDialog.dismiss();
                        }

                        // show new dialog
                        showTrialDialog = new AlertDialog.Builder(MainActivity.this)
                                .setTitle("Free Trial Ended")
                                .setMessage("Thanks for trying out the app! Please purchase the full version to continue use.")
                                .setCancelable(false)
                                .setPositiveButton("Upgrade", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Log.d("HERE", "Upgrade");
                                        if (rewardedAd != null) {
                                            rewardedAd.buyAdRemoval();
                                        }
                                    }
                                })
//                                .setPositiveButton("Return to App", new DialogInterface.OnClickListener() {
//                                    public void onClick(DialogInterface dialog, int which) {
//
//                                    }
//                                })
                                .setNegativeButton("Exit App", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        finishAffinity();
                                    }
                                })
                                .show();
                    }
                }
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    });

    private class SessionManagerListenerImpl implements SessionManagerListener<CastSession> {
        @Override
        public void onSessionStarted(CastSession session, String sessionId) {
            Log.e("HERE", "onSessionStarted");
            invalidateOptionsMenu();
            mCastSession = session;
            listenForCastMessages();
            startCastSession();
        }

        @Override
        public void onSessionStarting(@NonNull CastSession castSession) {
            Log.e("HERE", "onSessionStarting");
           try {
                new AlertDialog.Builder(MainActivity.this)
                .setTitle("Cast Warning")
                .setMessage("The settings that you have in the app will be applied to your cast device. Consider setting the client to 'Android' in the settings if there is lag on your TV. \n\nThis feature is still in development! You must have a high end Android TV or Chromecast device (60FPS support and 2GB of RAM or more). This will not work with a standard Chromecast (yet - hopefully coming soon).\n\nThis is tested and working with a 'Chromecast with Google TV' device.\nPlease be patient while I get this working with non 60FPS devices :)")
                .setCancelable(true)
                .setPositiveButton("I Understand", null)
                .show();
            } catch (Exception e){
                e.printStackTrace();
            }
        }

        @Override
        public void onSessionSuspended(@NonNull CastSession castSession, int i) {
            Log.e("HERE", "onSessionSuspended");
            mCastSession = castSession;
        }

        @Override
        public void onSessionResumed(CastSession session, boolean wasSuspended) {
            invalidateOptionsMenu();
            Log.e("HERE", "onSessionResumed");
            mCastSession = session;
        }

        @Override
        public void onSessionResuming(@NonNull CastSession castSession, @NonNull String s) {
            Log.e("HERE", "onSessionResuming");
            mCastSession = castSession;

        }

        @Override
        public void onSessionStartFailed(@NonNull CastSession castSession, int i) {
            Log.e("HERE", "onSessionStartFailed");
            mCastSession = castSession;
            try {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Failed to Cast")
                        .setMessage("Your cast devices refused the connection. Try restarting it")
                        .setCancelable(true)
                        .setPositiveButton("OK", null)
                        .show();
            } catch (Exception e){
                e.printStackTrace();
            }

        }

        @Override
        public void onSessionEnded(CastSession session, int error) {
            Log.e("HERE", "onSessionEnded");
            mCastSession = session;
        }

        @Override
        public void onSessionEnding(@NonNull CastSession castSession) {
            Log.e("HERE", "onSessionEnding");
            mCastSession = castSession;
        }

        @Override
        public void onSessionResumeFailed(@NonNull CastSession castSession, int i) {
            Log.e("HERE", "onSessionResumeFailed");
            mCastSession = castSession;
        }
    }
    private CastStateListener mCastStateListener = new CastStateListener() {
        @Override
        public void onCastStateChanged(int newState) {
                try {
                    if (newState != CastState.NO_DEVICES_AVAILABLE && !inFullScreenMode) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                overlay = new IntroductoryOverlay.Builder(MainActivity.this, mediaRouteMenuItem)
                                        .setTitleText("Cast your Xbox One or Series X/S screen straight to your TV. Now you can leave your Xbox in the living room and play from the smart TV in your bedroom!")
                                        .setSingleTime()
                                        .setOnOverlayDismissedListener(
                                                new IntroductoryOverlay.OnOverlayDismissedListener() {
                                                    @Override
                                                    public void onOverlayDismissed() {
                                                        overlay = null;
                                                    }
                                                })
                                        .build();
                                overlay.show();
                            }
                        });
                    }
                } catch (Exception e){
                    e.printStackTrace();
                } catch (Error e){
                    e.printStackTrace();
                }
            }

    };

    public void updateMenu() {
        try {
            invalidateOptionsMenu();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Hide the "Unlock" menu item initially
        if (unlockMenuItem != null) {
            unlockMenuItem.setVisible(false);

            boolean purchased = RewardedAdLoader.getPurchaseItem(getApplicationContext());
            unlockMenuItem.setVisible(!purchased);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        unlockMenuItem = menu.findItem(R.id.action_full_version);

        try {
            mediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(getApplicationContext(),
                    menu,
                    R.id.media_route_menu_item);
        } catch (Exception e){
            e.printStackTrace();
        }

        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            purchaseChecker.start();
        } catch (Exception e){
            Log.e("Error", "Error starting purchase checker");
            e.printStackTrace();
        }
        try {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MainActivity.this) == ConnectionResult.SUCCESS) {
                mCastSession = mSessionManager.getCurrentCastSession();
                mSessionManager.addSessionManagerListener(mSessionManagerListener, CastSession.class);
                mCastContext.addCastStateListener(mCastStateListener);
            }
        } catch (Exception e){
            e.printStackTrace();
        } catch (Error e){
            e.printStackTrace();
        }
        IronSource.onResume(this);

        if(rewardedAd != null){
            rewardedAd.resume();
        }

        this.updateMenu();
    }
    @Override
    protected void onPause() {
        super.onPause();
        try {
            purchaseChecker.stop();
        } catch (Exception e){
            Log.e("Error", "Error stopping purchase checker");
            e.printStackTrace();
        }
        try {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MainActivity.this) == ConnectionResult.SUCCESS) {
                mSessionManager.removeSessionManagerListener(mSessionManagerListener, CastSession.class);
                mCastContext.removeCastStateListener(mCastStateListener);
                mCastSession = null;
            }
        } catch (Exception e){
            e.printStackTrace();
        } catch (Error e){
            e.printStackTrace();
        }
        IronSource.onPause(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        // update shared prefs rating value so we prompt user every other open
        SharedPreferences prefs = getSharedPreferences("rate", MODE_PRIVATE);
        int appOpenCounter = prefs.getInt("appOpens", 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt("appOpens", appOpenCounter + 1);
        editor.apply();
        Log.w("HERE", "Open counter:" + appOpenCounter + 1);

        // attempt to close remote service if bound
        unbindRemoteControllerService();
    }

    public void unbindRemoteControllerService(){
        binding = null;
        try {
            unbindService(mServiceConnection);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    void listenForCastMessages() {
        Cast.MessageReceivedCallback callback = new Cast.MessageReceivedCallback() {
            @Override
            public void onMessageReceived(CastDevice castDevice, String namespace, String message) {
                // Do something with the message
                try {
                    // format for json
                    message = message.replace("\\\"","'");
                    Log.e("HERE", "'" +message+"'" + " - message received from cast device here");
                    JSONObject data = new JSONObject(message.substring(1,message.length()-1));

                    // handle relogin message
                    if(data.getString("type").equals("Error") && data.getString("message").equals("expired_tokens")) {
                         new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Cast - Login Required")
                            .setMessage("The cast device cannot connect to your console. Please navigate to the 'consoles' tab and click connect. Then recast")
                            .setCancelable(false)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    drawer.open();
                                }
                            }).show();
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        };
        // Add the message listener to the current cast session
        try {
            mCastSession.setMessageReceivedCallbacks("urn:x-cast:xbplay-toAndroid", callback);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    void startCastSession() {
        Log.e("HERE", "starting cast session");
        MediaMetadata movieMetadata = new MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC);

        movieMetadata.putString(MediaMetadata.KEY_TITLE, "xbPlay");
        movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, "Streaming console directly to TV");
        movieMetadata.addImage(new WebImage(Uri.parse("https://d1o4538xtdh4nmk7q.cloudfront.net/feature-art.png")));
        MediaInfo mediaInfo = new MediaInfo.Builder("none")
                .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
                .setMetadata(movieMetadata)
                .setStreamDuration(5)
                .build();
        RemoteMediaClient remoteMediaClient = mCastSession.getRemoteMediaClient();
        remoteMediaClient.load(new MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build());
        remoteMediaClient.play();

        // get creds
        EncryptClient encryptClient = new EncryptClient(MainActivity.this);
        String serverId = encryptClient.getValue("serverId");
        String gsToken = encryptClient.getValue("gsToken");

        // get audio prefs
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        Boolean enableInfoBar = prefs.getBoolean("info_cast_key", false);
        String videoFit = prefs.getString("video_fit_key", "cover");
        String emulateClient = prefs.getString("emulate_client_key", "windows");
        String controllerRefreshRate = prefs.getString("controller_refresh_key", "32");

        String maxBitrate = prefs.getString("max_bitrate_key", "");

//        String softResetOnLag = prefs.getString("soft_reset_on_lag_key", "");
//        String softResetOnInterval = prefs.getString("soft_reset_on_interval_key", "");
//        Boolean flashScreenOnSoftReset = prefs.getBoolean("flash_screen_on_soft_reset", false);
//        String clearBufferOnInterval = prefs.getString("clear_buffer_on_interval_key", "");

        // send complete config to cast device
        JSONObject config = new JSONObject();
        try {
            config.put("gsToken", gsToken);
            config.put("serverId", serverId);
            config.put("info", enableInfoBar);
            config.put("video-fit", videoFit);
            config.put("gamepadRefreshRateMs", controllerRefreshRate);
            config.put("userAgentType", emulateClient);
            config.put("maxBitrate", maxBitrate);

//            config.put("softResetOnLag", softResetOnLag);
//            config.put("softResetOnInterval", softResetOnInterval);
//            config.put("flashScreenOnSoftReset", flashScreenOnSoftReset);
//            config.put("clearBufferOnInterval", clearBufferOnInterval);
        } catch (Exception e){
            e.printStackTrace();
        }

       mCastSession.sendMessage("urn:x-cast:xbplay-config", config.toString());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        // set theme (for notch) before setting content view
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean shouldUseNotch = prefs.getBoolean("use_notch_key", true);
        if(!shouldUseNotch) {
            setTheme(R.style.Theme_MyApplication3);
        }

        super.onCreate(savedInstanceState);
        setupGoogleAnalytics();
        try { // this can fail sometimes according to google crash reports
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(MainActivity.this) == ConnectionResult.SUCCESS) {
                mCastContext = CastContext.getSharedInstance(this);
                mSessionManager = CastContext.getSharedInstance(this).getSessionManager();
                mCastContext.addCastStateListener(mCastStateListener);
            }
        } catch (Exception e){
            e.printStackTrace();
        } catch (Error e){
            e.printStackTrace();
        }

        setupInitialOrientation();
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        setSupportActionBar(binding.appBarMain.toolbar);
        binding.appBarMain.fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Load login WebView
                hideSystemUI();

                Snackbar.make(view, "Fullscreen mode enabled. Press the back button to exit.", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();

            }
        });
        drawer = binding.drawerLayout;
        NavigationView navigationView = binding.navView;
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        mAppBarConfiguration = new AppBarConfiguration.Builder(
                R.id.nav_remote, R.id.nav_home, R.id.nav_gamestream, R.id.nav_stream,
                    R.id.nav_controller, R.id.nav_controller_builder, R.id.nav_mirrorcast,
                    R.id.nav_filecast, R.id.nav_cast_remote, R.id.nav_tvcast, R.id.nav_audiocast,
                    R.id.nav_more_features, R.id.nav_android_tv, R.id.nav_xcloud,
                    R.id.nav_widgets, R.id.nav_voiceremote)
                .setOpenableLayout(drawer)
                .build();
        navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        NavigationUI.setupActionBarWithNavController(this, navController, mAppBarConfiguration);
        NavigationUI.setupWithNavController(navigationView, navController);

        navigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if(item.getItemId() == R.id.nav_settings) {
                    drawer.close();
                    Intent i = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(i);
                    return false;
                } else {
                    navController.navigate(item.getItemId());
                    drawer.close();
                }

                xCloudShortcutTitleId = null; // if we started from an xCloud shortcut, wipe the title on navigation
                return true;
            }
        });

        rewardedAd = new RewardedAdLoader(MainActivity.this);

        // only show popups on first create
        if (savedInstanceState == null) {
            handleTutorial();
            handleWifiPrompt();
            handleRateScreens();
            drawer.open();
        }

        handleTvCastRemoteRedirect();
        handleWidgetAdRedirect();

        Helper.checkIfUpdateAvailable(MainActivity.this);

//        MediationTestSuite.launch(MainActivity.this);

        controllerHandler = new ControllerHandler(getApplicationContext());

        handleDeepLinkRedirect();

        handleXCloudShortcutStart();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // dont rebuild the controller builder activity because its tricky to reload (due to needing to set orientation and type)
        if (navController != null && navController.getCurrentDestination() != null){
            int id = navController.getCurrentDestination().getId();
            if (id == R.id.nav_controller_builder){
                return;
            }
        }

        // all activities except the gamepad builder can safely handle orientation changes now
        recreate();
    }

    public void handleXCloudShortcutStart(){
        Intent intent = getIntent();
        if (intent == null){
            return;
        }

        String action = getIntent().getAction();
        String xCloudTitle = intent.getStringExtra("titleId");

        if (!TextUtils.isEmpty(xCloudTitle) && action != null && !TextUtils.isEmpty(action) && action.equals("xcloudstart")){
            xCloudShortcutTitleId = xCloudTitle;

            Toast.makeText(this, "Click start to play: " + xCloudShortcutTitleId, Toast.LENGTH_SHORT).show();
            setOrientationLandscape();
            drawer.close();
            navController.navigate(R.id.nav_xcloud);
        }
    }
    public void handleDeepLinkRedirect(){
        Uri uri = getIntent().getData();
        if (uri != null) {
            List<String> parameters = uri.getPathSegments();
            Log.e("LaunchIntent", "Params: " + parameters + uri);
            if(uri.getHost().equals("launch")){
                String launchActivity = parameters.get(0);

                switch(launchActivity){
                    case "xhome":
                        navController.navigate(R.id.nav_gamestream);
                        drawer.close();
                        break;
                    case "xcloud":
                        navController.navigate(R.id.nav_xcloud);
                        drawer.close();
                        break;
                    case "controller":
                        navController.navigate(R.id.nav_controller);
                        drawer.close();
                        break;
                    case "tv":
                        if(parameters.size() >= 2){
                            tvCodeUri = parameters.get(1);
                            navController.navigate(R.id.nav_android_tv);
                        }
                        drawer.close();
                        break;
                    default:
                        Log.e("LaunchIntent", "Unknown launch activity: " + launchActivity);
                        break;
                }
            }

            // after that we are extracting string
            // from that parameters.
            if(parameters.size() != 0) {
                String param = parameters.get(parameters.size() - 1);

                // on below line we are setting that string
                // to our text view which we got as params.
            }
        }
    }

    // 1. If the user has on connect policy
    // AND if the user has opened the app more than 2 times
    public void showConnectAdPossibly(){
        try {
            rewardedAd.showRewardedAdDialog();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private int getTotalNumberOfAppOpens(){
        SharedPreferences ratePrefs = getSharedPreferences("rate", MODE_PRIVATE);
        int appOpenCounter = ratePrefs.getInt("appOpens", 1);
        if(appOpenCounter >= 1000000) { // handle dumb rating increment
            appOpenCounter = appOpenCounter - 1000000;
        }
        return appOpenCounter;
    }

    private void setupGoogleAnalytics(){
        try {
            FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
            analyticsClient = new FirebaseAnalyticsClient(mFirebaseAnalytics);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    private void handleTvCastRemoteRedirect(){
        boolean calledFromNotificationClick = getIntent().getBooleanExtra("showCastRemote", false);
        if(calledFromNotificationClick){
            navController.navigate(R.id.nav_tvcast);
            drawer.close();
        }
    }
    private void handleWidgetAdRedirect(){
        boolean calledFromWidgetClick = getIntent().getBooleanExtra("widgetShowAd", false);
        if(calledFromWidgetClick){
            navController.navigate(R.id.nav_remote);
            drawer.close();
            showConnectAdPossibly();
        }
    }
    @Override
    protected void onNewIntent(Intent intent) {
        Log.e("HERE","New Intent");
        handleTvCastRemoteRedirect();
        handleWidgetAdRedirect();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        super.onNewIntent(intent);
    }

    @Override
    public void onBackPressed() {
        boolean drawerOpen = false;
        if(drawer != null && drawer.isDrawerOpen(GravityCompat.START)){
            drawerOpen = true;
        }
        if (inFullScreenMode) {
            showSystemUI();
        } else if (!drawerOpen && drawer != null) {
            drawer.open();
        } else {
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Exit")
                    .setMessage("Are you sure you want to exit?")
                    .setCancelable(true)
                    .setPositiveButton("Exit App", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finishAffinity();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .show();
        }
    }

    // called when login completes and user selects a console
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 444) {
            if(resultCode == Activity.RESULT_OK){
                String serverId = data.getStringExtra("serverId");
//                String consoleName = data.getStringExtra("consoleName");

                try {
                    TextView tv = findViewById(R.id.text_home);
                    this.consoleText = "Connected to console: " + serverId;
                    tv.setText(this.consoleText);
                    Button connectBtn = findViewById(R.id.connect_button);
                    connectBtn.setText("Reload Consoles");

                    drawer.open();
                    Toast.makeText(MainActivity.this, "Login success! Open a streaming/controller tab!", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    // will happen for android tv view
                }

            }  else {
                //Toast.makeText(MainActivity.this, "Failed to connect to a console!", Toast.LENGTH_LONG).show();
            }
        }
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_reddit) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/r/xbPlay/")));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(MainActivity.this, "Error opening reddit link", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (item.getItemId() ==  R.id.action_discord) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/zxEBXxWWza")));
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(MainActivity.this, "Error opening discord link", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            Intent i = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(i);
            return true;
        } else if (item.getItemId() == R.id.action_rate){
            showRatingDialog();
            return true;
        } else if (item.getItemId() == R.id.action_full_version) {
            try {
                if (rewardedAd != null) {
                    rewardedAd.buyAdRemoval();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return true;
        } else {
            // If we got here, the user's action was not recognized.
            // Invoke the superclass to handle it.
            return super.onOptionsItemSelected(item);
        }

    }

    // on start setup activity with defaulted values. Note, auto will be applied later once the page is loaded
    public void setupInitialOrientation(){
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        String orientation = prefs.getString("orientation_key", "auto");
        if(orientation.equals("portrait")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (orientation.equals("landscape")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (orientation.equals("reverse_landscape")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
        } else if (orientation.equals("full_sensor")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    public void setOrientationPortrait(){
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        String orientation = prefs.getString("orientation_key", "auto");
        if(orientation.equals("auto")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else if (orientation.equals("full_sensor")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    public void setOrientationLandscape(){
        SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        String orientation = prefs.getString("orientation_key", "auto");
        if(orientation.equals("auto")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        } else if (orientation.equals("full_sensor")) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }


    public void handleRateScreens(){
        // check if asking to rate prompt has been shown yet
        SharedPreferences prefs = getSharedPreferences("rate", MODE_PRIVATE);
        int appOpenCounter = prefs.getInt("appOpens", 1);

        boolean currentlyForcingToViewAd = getIntent().getBooleanExtra("widgetShowAd", false);

        // only ask to rate if they haven't pressed rate button yet
        if (appOpenCounter < 1000000 && !currentlyForcingToViewAd) {
            // show custom popup first
            if(appOpenCounter % 3 == 0) {
                showRatingDialog();
            } else if (appOpenCounter % 5 == 0){
                Helper.showRatingApiMaybe(MainActivity.this);
            }
        }
    }

    public void showRatingDialog(){
        analyticsClient.logButtonClickEvent("show_rate_app");

        new AlertDialog.Builder(MainActivity.this)
            .setTitle("Rate App")
            .setMessage("Please help support this app by leaving a rating on Google Play! Comment with any feature requests or improvements and I will do my best to get to them!")
            .setCancelable(true)
            .setPositiveButton("Rate", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    analyticsClient.logButtonClickEvent("open_rate_app");

                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getPackageName())));

                    // update shared prefs rating value so we dont prompt user again
                    SharedPreferences prefs = getSharedPreferences("rate", MODE_PRIVATE);
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putInt("appOpens", 1000000);
                    editor.apply();
                }
            })
            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    analyticsClient.logButtonClickEvent("cancel_rate_app");
                    Toast.makeText(MainActivity.this, "Okay :(", Toast.LENGTH_LONG).show();
                }
            })
        .show();
    }
    public void handleWifiPrompt() {
        // prompt user to connect wifi on start if not connected
        if (!Helper.checkWifiConnected(MainActivity.this.getApplicationContext())) {
            new AlertDialog.Builder(MainActivity.this)
                .setTitle("Connect to Wifi")
                .setMessage("You must be connected to the same Wifi network as your console. Connect now?")
                .setCancelable(true)
                .setPositiveButton("Connect Wifi", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                    }
                })
                .setNegativeButton("Continue Anyway", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        Toast.makeText(MainActivity.this, "You might not be able to connect to your console :(", Toast.LENGTH_LONG).show();
                    }
                })
                .show();
        }
    }

    public void handleTutorial() {
        EncryptClient encryptClient = new EncryptClient(MainActivity.this);
        String alreadyShown = encryptClient.getValue("tutorialShown");
        if(TextUtils.isEmpty(alreadyShown) || !TextUtils.equals(alreadyShown, "1")){
            // show tutorial if not already shown
            Intent intent = new Intent(this, TutorialActivity.class);
            startActivity(intent);
        }
    }

    public void hideSystemUI() {
        inFullScreenMode = true;
        binding.appBarMain.fab.setVisibility(View.INVISIBLE);
        getSupportActionBar().hide();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            // Set the content to appear under the system bars so that the
                            // content doesn't resize when the system bars hide and show.
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            // Hide the nav bar and status bar
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            WindowInsetsControllerCompat windowInsetsController =
                    ViewCompat.getWindowInsetsController(getWindow().getDecorView());
            if (windowInsetsController == null) {
                return;
            }
            // Configure the behavior of the hidden system bars
            windowInsetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
            // Hide both the status bar and the navigation bar
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());

            SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            boolean shouldUseNotch = prefs.getBoolean("use_notch_key", true);
            if(shouldUseNotch) {
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
                getWindow().setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
                getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            }

        }

        // don't allow accidental left swipes
        try {
            if (drawer != null) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    // Shows the system bars by removing all the flags
    // except for the ones that make the content appear under the system bars.
    public void showSystemUI() {
        binding.appBarMain.fab.setVisibility(View.VISIBLE);
        inFullScreenMode = false;
        getSupportActionBar().show();

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            View decorView = getWindow().getDecorView();
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        } else {
            SharedPreferences prefs = getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            boolean shouldUseNotch = prefs.getBoolean("use_notch_key", true);
            if(shouldUseNotch) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            }

            WindowInsetsControllerCompat windowInsetsController =
                    ViewCompat.getWindowInsetsController(getWindow().getDecorView());
            if (windowInsetsController == null) {
                return;
            }
            // Configure the behavior of the hidden system bars
            windowInsetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            );
            // Show both the status bar and the navigation bar
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
        }

        // allow left swipes
        try {
            if (drawer != null) {
                drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED);
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_content_main);
        return NavigationUI.navigateUp(navController, mAppBarConfiguration)
                || super.onSupportNavigateUp();
    }

}