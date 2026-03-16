package com.studio08.xbgamestream;


import static com.studio08.xbgamestream.Helpers.Helper.checkIfAlreadyHavePermission;
import static com.studio08.xbgamestream.Helpers.Helper.requestForSpecificPermission;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.ui.AppBarConfiguration;

import com.connectsdk.core.MediaInfo;
import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.device.DevicePicker;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.capability.PlaylistControl;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.sessions.LaunchSession;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.studio08.xbgamestream.CastToConsole.CastToConsoleForegroundService;
import com.studio08.xbgamestream.Converter.ConvertForegroundService;
import com.studio08.xbgamestream.Helpers.FileHelper;
import com.studio08.xbgamestream.Helpers.FirebaseAnalyticsClient;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.Servers.FileServer;
import com.studio08.xbgamestream.Servers.Xbox360FileServer;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.studio08.xbgamestream.ui.remote.RemoteFragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import network.BindService;


public class ScreenCastActivity extends AppCompatActivity  implements ConnectableDeviceListener, DiscoveryManagerListener, CastToConsoleForegroundService.CastServiceListener {

    private boolean AUDIO_CAST_MODE = false;
    private AppBarConfiguration appBarConfiguration;
    private static DiscoveryManager mDiscoveryManager;
    private static ConnectableDevice mDevice;
    private int PICKFILE_RESULT_CODE = 1235;
    private static final int READ_STORAGE_PERMISSION_REQUEST_CODE = 41;
    public FirebaseAnalyticsClient analyticsClient;

    TextView infoText;
    ProgressDialog progressDialog;

//    LaunchSession mLaunchSession;
    MediaControl mMediaControl;
    PlaylistControl mPlaylistControl;

    Timer refreshTimer;
    int MEDIA_CURRENT_TIME = 0;
    long MEDIA_DURATION = 0;
    int VOLUME_LEVEL = 100;
    boolean isMute = false;

    StreamWebview streamView;

    Messenger mService = null;
    boolean mIsBound;

    @Override
    public void onMediaControlCreated(MediaControl mediaControl) {
        if(mMediaControl == null) {
            mMediaControl = mediaControl;
            mMediaControl.subscribePlayState(playStateListener);
        }
    }
    private void setupGoogleAnalytics(){
        try {
            FirebaseAnalytics mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
            analyticsClient = new FirebaseAnalyticsClient(mFirebaseAnalytics);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    @Override
    public void onCastTitleUpdated() {
        updateInfoText();
    }

    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            try {
                switch (msg.what) {
                    case ConvertForegroundService.MSG_UPDATE_STATS:
                        String updateMessage = ((JSONObject) msg.obj).getString("message");
                        Log.e("HERE", "Got Stats Data" + updateMessage);
                        updateConvertDialogStatus(updateMessage);
                        break;
                    case ConvertForegroundService.MSG_CONVERT_COMPLETE:
                        String completeMessage = ((JSONObject) msg.obj).getString("message");
                        Log.e("HERE", "Got Complete Data" + completeMessage);
                        showConvertCompleteDialog(completeMessage);
                        break;
                    case ConvertForegroundService.MSG_CONVERT_FAILED:
                        Log.e("HERE", "Screencast Activity caught video convert failure");
                        String failedMessage = ((JSONObject) msg.obj).getString("message");

                        Log.e("HERE", "Got Failed Data" + failedMessage);
                        showConvertFailedDialog(failedMessage);
                        break;
                    default:
                        super.handleMessage(msg);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }
    final Messenger mMessenger = new Messenger(new IncomingHandler());

    CastToConsoleForegroundService castBindService;
    Boolean castServiceBound = false;

    /**
     * Class for interacting with the main interface of the service.
     */
    private ServiceConnection mConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = new Messenger(service);
            Log.e("HERE","Attached.");

            try {
                Message msg = Message.obtain(null, ConvertForegroundService.MSG_REGISTER_CLIENT);
                msg.replyTo = mMessenger;
                mService.send(msg);

//                // Give it some value as an example.
//                msg = Message.obtain(null, ConvertForegroundServi.MSG_SET_VALUE, this.hashCode(), 0);
//                mService.send(msg);
            } catch (RemoteException e) {
               e.printStackTrace();
            }
            Log.e("HERE","Remote service connected.");
        }

        public void onServiceDisconnected(ComponentName className) {
            mService = null;
            Log.e("HERE","Disconnected.");
        }
    };

    private ServiceConnection castToConsoleForegroundServiceBindConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.e("HERE","castToConsoleForegroundServiceBindConnection Attached.");

            // bind
            CastToConsoleForegroundService.MyBinder myBinder = (CastToConsoleForegroundService.MyBinder) service;
            castBindService = myBinder.getService();
            castServiceBound = true;

            // get media control object if currently casting
            if(mMediaControl == null && castBindService.getMediaControl() != null){
                Log.e("HERE","Hydrating media control element from service!");
                mMediaControl = castBindService.getMediaControl();
                mMediaControl.subscribePlayState(playStateListener);
                startUpdating();
            } else {
                Log.e("HERE","Cast not running in service yet. Unable to load media control object" + (mMediaControl == null) + " - "+ (castBindService.getMediaControl() != null));
            }

            // get device object if already casted
            if(mDevice == null && castBindService.getDevice() != null){
                mDevice = castBindService.getDevice();
            }

            // set listener to update stats
            castBindService.setCastServiceListener(ScreenCastActivity.this);
            updateInfoText();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e("HERE","Disconnected.");
            castServiceBound = false;
        }
    };

    void doBindConvertService() {
        bindService(new Intent(ScreenCastActivity.this, ConvertForegroundService.class), mConnection, Context.BIND_AUTO_CREATE);
        mIsBound = true;
        Log.e("HERE","Binding convert service");
    }
    void doUnbindConvertService() {
        if (mIsBound) {
            if (mService != null) {
                try {
                    Message msg = Message.obtain(null, ConvertForegroundService.MSG_UNREGISTER_CLIENT);
                    msg.replyTo = mMessenger;
                    mService.send(msg);
                } catch (RemoteException e) {
                   e.printStackTrace();
                }
            }

            // Detach our existing connection.
            unbindService(mConnection);
            mIsBound = false;
            Log.e("HERE","Unbinding convert service");
        }
    }


    // listener - fires when streaming detects auth is required
    ApiClient.StreamingClientListener buttonPressListener = new ApiClient.StreamingClientListener() {
        @Override
        public void onReLoginDetected() {}

        @Override
        public void onCloseScreenDetected() {}

        @Override
        public void pressButtonWifiRemote(String type) {
           sendCastRemoteCommand(type);
        }
        @Override
        public void setOrientationValue(String value) {}
        @Override
        public void vibrate() {}
        @Override
        public void genericMessage(String type, String msg) {}
    };

    // listen for button presses
    ResponseListener castRemoteResponseListener = new ResponseListener<Object>() {
        @Override
        public void onSuccess(Object object) {
        }

        @Override
        public void onError(ServiceCommandError error) {
            Toast.makeText(ScreenCastActivity.this, "Error sending command", Toast.LENGTH_SHORT).show();
            error.printStackTrace();
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e("ScreenCastActivity", "Destroyed");
        try {
            mDiscoveryManager.stop();
            mDiscoveryManager = null;
            doUnbindConvertService();

            // if we are not actively casting, stop the service to remote notification
            if(castServiceBound && mMediaControl == null){
                castBindService.stopServiceCommand();
            }
            stopUpdating();
        } catch (Exception e){

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_cast);
        setOrientationPortrait();
        setupGoogleAnalytics();

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        myToolbar.setTitle("Video Cast");
        setSupportActionBar(myToolbar);

        infoText = findViewById(R.id.text_cast_footer);

        // immediately begin listening for consoles
        DiscoveryManager.init(ScreenCastActivity.this);
        mDiscoveryManager = DiscoveryManager.getInstance();
        mDiscoveryManager.addListener(ScreenCastActivity.this);
        mDiscoveryManager.start();

        ((SeekBar)findViewById(R.id.seekbar)).setOnSeekBarChangeListener(seekListener);

        //setup cast remote webview
        streamView = findViewById(R.id.webview1);
        streamView.setBackgroundColor(Color.TRANSPARENT);
        streamView.init();

        // hide seekbar until on remote page
        findViewById(R.id.seekbar_layout).setVisibility(View.INVISIBLE);

        // handle button clicks
        findViewById(R.id.cast_connect_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectDevice();
            }
        });

        findViewById(R.id.cast_file_choose_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectFile();
            }
        });

        findViewById(R.id.cast_send_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                analyticsClient.logButtonClickEvent("cast_to_console");
                castToConsole(true);
            }
        });

        findViewById(R.id.cast_remote_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showRemoteView();
            }
        });

        // handle different view loading in on create as well as new intent
        if (getIntent().getBooleanExtra("showRemoteView", false)){
            showRemoteView();
        } else if (getIntent().getBooleanExtra("showCastView", false)){
            setCastType(getIntent());
            showCastView();
        }

        // start cast background service
        startForegroundCastService();
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
    private void setCastType(Intent intent){
        if(intent != null && intent.getBooleanExtra("audioCastType", false)){
            Log.e("HERE", AUDIO_CAST_MODE + " - AUDIO_CAST_MODE");
            AUDIO_CAST_MODE = true;
        } else {
            Log.e("HERE", AUDIO_CAST_MODE + " - AUDIO_CAST_MODE");
            AUDIO_CAST_MODE = false;
        }
    }
    // CONVERT MESSAGE DIALOGS
    void showConvertFailedDialog(String message){
        new AlertDialog.Builder(ScreenCastActivity.this)
            .setTitle("Failed to Convert Video")
            .setMessage("Failed to convert video to Xbox 360 compatible format. Please report this error if there is not an obvious solution!\n\n Details: " + message)
            .setCancelable(true)
            .setPositiveButton("Exit", null)
            .show();
        if(progressDialog != null && progressDialog.isShowing()){
            progressDialog.dismiss();
        }
    }
    void updateConvertDialogStatus(String message){
        if(progressDialog != null && progressDialog.isShowing()){
            createConvertProgressDialog(message);
        }
    }
    void showConvertCompleteDialog(String message){
        try {
            new AlertDialog.Builder(ScreenCastActivity.this)
                    .setTitle("Video Converted!")
                    .setMessage("Video successfully converted to a format that is compatible with an Xbox 360!\n\nYou can view, recast, or delete this video anytime. It is stored on your device at the location of: " + message)
                    .setCancelable(true)
                    .setPositiveButton("Cast Now", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            if (castBindService != null){ // need to call this on click so it happens after service bound
                                castBindService.setFilePaths(new String [] {message}, false); // convert never runs on audio files
                                updateInfoText();
                            } else {
                                Toast.makeText(ScreenCastActivity.this, "Error. Restart App", Toast.LENGTH_SHORT).show();
                            }
                            castToConsole(false);
                        }
                    })
                    .setNegativeButton("Close", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            stopService();
                        }
                    })
                    .show();
        } catch (Exception e){
            e.printStackTrace();
        }

        if(progressDialog != null && progressDialog.isShowing()){
            progressDialog.dismiss();
        }
    }

    void createConvertProgressDialog(String message){
        if(progressDialog == null){
            progressDialog = new ProgressDialog(ScreenCastActivity.this);
        }
        progressDialog.setTitle("Converting Video to Valid Xbox 360 Format");
        progressDialog.setMessage(message);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        progressDialog.setCancelable(true);
        progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, "Run In Background", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                progressDialog.dismiss();//dismiss dialog
            }
        });
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                progressDialog.dismiss();//dismiss dialog
                stopService();
            }
        });
        try {
            progressDialog.show();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    void sendCastRemoteCommand(String type){
        ScreenCastActivity.this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    switch (type) {
                        case "Pause":
                            mMediaControl.pause(castRemoteResponseListener);
                            break;
                        case "Play":
                            mMediaControl.play(castRemoteResponseListener);
                            break;
                        case "Stop":
                            mMediaControl.stop(castRemoteResponseListener);
                            break;
                        case "Rewind":
                            mMediaControl.seek((long) (MEDIA_CURRENT_TIME-20000), null);
                            break;
                        case "Fastforward":
                            mMediaControl.seek((long) (MEDIA_CURRENT_TIME+20000), null);
                            break;
                        case "Next":
                            castBindService.playNext();
                            break;
                        case "Previous":
                            castBindService.playPrevious();
                            break;
                        case "Mute":
                            isMute = !isMute;
                            mDevice.getVolumeControl().setMute(isMute, new ResponseListener<Object>() {
                                @Override
                                public void onSuccess(Object object) {
                                    Log.e("","");
                                }

                                @Override
                                public void onError(ServiceCommandError error) {
                                    Log.e("","");

                                }
                            });
                            break;
                        case "Volumedown":
                            if (VOLUME_LEVEL >= 10) {
                                VOLUME_LEVEL -= 10;
                                Toast.makeText(ScreenCastActivity.this, VOLUME_LEVEL + "%", Toast.LENGTH_SHORT).show();
                            }

                            mDevice.getVolumeControl().setMute(false, null);
                            mDevice.getVolumeControl().setVolume((float)((double)VOLUME_LEVEL/100), null);
                            break;
                        case "Volumeup":
                            if (VOLUME_LEVEL <= 90) {
                                VOLUME_LEVEL += 10;
                                Toast.makeText(ScreenCastActivity.this, VOLUME_LEVEL + "%", Toast.LENGTH_SHORT).show();
                            }

                            mDevice.getVolumeControl().setMute(false, null);
                            mDevice.getVolumeControl().setVolume((float)((double) VOLUME_LEVEL/100), null);
                            break;
                        case "Return":
                            showCastView();
                            break;
                        default:
                            Toast.makeText(ScreenCastActivity.this, "Button not mapped", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    try {
                        // Get instance of Vibrator from current Context
                        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

                        if (v.hasVibrator()) {
                            v.vibrate(50);
                        } else {
                            Log.v("Can Vibrate", "NO");
                        }
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                } catch (Exception e){
                    e.printStackTrace();
                    Log.e("HERE", "Error sending cast remote command");
                    Toast.makeText(ScreenCastActivity.this, "Error sending command", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // MAIN BUTTONS
    private void selectDevice() {
        AdapterView.OnItemClickListener selectDevice = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View parent, int position, long id) {
                mDevice = (ConnectableDevice) adapter.getItemAtPosition(position);
                mDevice.addListener(ScreenCastActivity.this);
                mDevice.connect();
            }
        };
        DevicePicker devicePicker = new DevicePicker(this);
        AlertDialog dialog = devicePicker.getPickerDialog("Loading Consoles...", selectDevice);
        dialog.show();
    }

    private void selectFile() {
        if(mDevice == null || !mDevice.isConnected()) {
            Toast.makeText(ScreenCastActivity.this, "Select an Xbox first!", Toast.LENGTH_SHORT).show();
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if(!checkIfAlreadyHavePermission(Manifest.permission.READ_MEDIA_VIDEO, ScreenCastActivity.this) ||
                    !checkIfAlreadyHavePermission(Manifest.permission.READ_MEDIA_AUDIO, ScreenCastActivity.this)) { // ask for perms
                try {
                    requestForSpecificPermission(new String[] {Manifest.permission.READ_MEDIA_VIDEO,
                            Manifest.permission.READ_MEDIA_AUDIO,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.POST_NOTIFICATIONS}, ScreenCastActivity.this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }
        } else {
            if(!checkIfAlreadyHavePermission(Manifest.permission.READ_EXTERNAL_STORAGE, ScreenCastActivity.this) ||
                    !checkIfAlreadyHavePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, ScreenCastActivity.this)) { // ask for perms
                try {
                    requestForSpecificPermission(new String [] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, ScreenCastActivity.this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                return;
            }
        }

        // launch file picker
        Intent chooseFile = new Intent(Intent.ACTION_GET_CONTENT);
        chooseFile.setType((!AUDIO_CAST_MODE) ? "video/*" : "audio/*");
        chooseFile.addCategory(Intent.CATEGORY_OPENABLE);
        chooseFile.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
        chooseFile.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        chooseFile = Intent.createChooser(chooseFile, (!AUDIO_CAST_MODE) ? "Choose a video" : "Choose an audio file");
        startActivityForResult(chooseFile, PICKFILE_RESULT_CODE);
    }

    private void castToConsole(boolean check360Device){
        if(mDevice == null || !mDevice.isConnected()) {
            Toast.makeText(ScreenCastActivity.this, "Select a console first", Toast.LENGTH_LONG).show();
            return;
        } else if (castBindService.getFilePaths() == null) {
            Toast.makeText(ScreenCastActivity.this, "Choose a valid file first", Toast.LENGTH_LONG).show();
            return;
        }

        // don't prompt to convert audio files
        if (check360Device && !AUDIO_CAST_MODE) {
            handle360FileConvert();
        } else {
            performCast();
        }
    }

    private void performCast() {
        if(castServiceBound){ // reset the index to 0 on service so we play from begging
            castBindService.setIndex(0);
            castBindService.beginListeningForPlayback(); // will update notification text
            castBindService.startCastingFirstVideo();
        } else {
            Toast.makeText(ScreenCastActivity.this, "Error Casting. Please restart", Toast.LENGTH_SHORT).show();
        }
    }

    // converts video to 360 valid format if 360 device, else, calls castToConsole bypassing 360 check
    // get files paths cant be null at this point
    private void handle360FileConvert() {
        // if casting to a 360 and multiple files selected
        if(mDevice.getFriendlyName().contains("360") && castBindService.getFilePaths().length != 1){
            castToConsole(false);
            Toast.makeText(ScreenCastActivity.this, "Notice: 360 video convert feature not available when selecting multiple files", Toast.LENGTH_LONG).show();
            Toast.makeText(ScreenCastActivity.this, "If you get 'Playback Error', consider converting video format by selecting individual files.", Toast.LENGTH_LONG).show();
        } else if(mDevice.getFriendlyName().contains("360") && castBindService.getFilePaths().length == 1 && !castBindService.getFilePaths()[0].contains("converted")){
            SharedPreferences prefs = ScreenCastActivity.this.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            String convertPromptValue = prefs.getString("video_convert_prompt_key", "ask");

            // detect if should show promp
            if (convertPromptValue.equals("ask")) {
                new AlertDialog.Builder(ScreenCastActivity.this)
                    .setTitle("Warning: Xbox 360 Detected")
                    .setMessage("Xbox 360's do not support most new video formats! You can try to cast this video, but it might show 'Playback Error' on your 360.\n\nIf this happens, this app has the ability to convert your video to a format that the Xbox 360 can play! Would you like to convert the video now?\n\nNote, converting videos is quite slow. Consider going into the settings and lowering the video conversion quality to increase the speed of the conversion process.")
                    .setCancelable(true)
                    .setPositiveButton("Convert", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            convertFileBackground();
                        }
                    })
                    .setNegativeButton("Continue without converting", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            castToConsole(false);
                        }
                    })
                    .show();
            } else if(convertPromptValue.equals("no_convert")) {
                castToConsole(false);
            } else {
                convertFileBackground();
            }
        } else {
            castToConsole(false);
        }
    }
    private void convertFileBackground() {
        analyticsClient.logButtonClickEvent("convert_360_video");
        createConvertProgressDialog("Starting...");
        startService();
    }
    private void handleNotificationClick(){
        String convertPath = getIntent().getStringExtra("outputPath");
        boolean completed = getIntent().getBooleanExtra("completed", false);
        String failed = getIntent().getStringExtra("convertError");

        if (convertPath != null && completed){
            Log.e("HERE", "Convert completed! Showing dialog");
            showConvertCompleteDialog(convertPath);
        } else if (convertPath != null){
            Log.e("HERE", "Convert in progress. Updating text");
        } else if (failed!= null){
            showConvertFailedDialog(failed);
        }
    }
    public void showRemoteView(){
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        myToolbar.setTitle("Cast Media Remote");
        setSupportActionBar(myToolbar);

        if(mMediaControl == null){
            Toast.makeText(ScreenCastActivity.this, "Nothing playing. Cast something first!", Toast.LENGTH_LONG).show();
            //return;
        }
        ApiClient streamingClient;
        streamingClient = new ApiClient(ScreenCastActivity.this, streamView);
        streamingClient.setCustomObjectListener(buttonPressListener);
        streamingClient.doCastRemote();

        // set main buttons invisible
        findViewById(R.id.cast_connect_button).setVisibility(View.INVISIBLE);
        findViewById(R.id.cast_file_choose_button).setVisibility(View.INVISIBLE);
        findViewById(R.id.cast_send_button).setVisibility(View.INVISIBLE);
        findViewById(R.id.cast_remote_button).setVisibility(View.INVISIBLE);

        // set return button visible
        findViewById(R.id.webview1).setVisibility(View.VISIBLE);
        findViewById(R.id.seekbar_layout).setVisibility(View.VISIBLE);
    }

    public void showCastView(){
        Log.e("HERE", "showCastView: " + AUDIO_CAST_MODE);

        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        myToolbar.setTitle((!AUDIO_CAST_MODE) ? "Video Cast" : "Audio Cast");
        setSupportActionBar(myToolbar);

        findViewById(R.id.cast_connect_button).setVisibility(View.VISIBLE);
        findViewById(R.id.cast_file_choose_button).setVisibility(View.VISIBLE);
        ((Button)findViewById(R.id.cast_file_choose_button)).setText((!AUDIO_CAST_MODE) ? "2. Choose Video File" : "2. Choose Audio File");
        findViewById(R.id.cast_send_button).setVisibility(View.VISIBLE);
        findViewById(R.id.cast_remote_button).setVisibility(View.VISIBLE);

        // set return button invisible
        findViewById(R.id.webview1).setVisibility(View.INVISIBLE);
        findViewById(R.id.seekbar_layout).setVisibility(View.INVISIBLE);

        handleNotificationClick();
    }

    // HELPERS ///////////////////////////
    private void updateInfoText() {
        String consoleSelected;
        String videoFileSelected;
        // set device name
        if(mDevice != null && mDevice.isConnected()){
            consoleSelected = "Connected to device: " + mDevice.getFriendlyName();
        } else {
            consoleSelected = "Connected to device: N/A";
        }

        // set file name
        String filename = castBindService.getCurrentVideoFile();
        if(filename != null) {
            videoFileSelected = "File: " + FileHelper.getFileNameFromPath(filename) + " (" + (castBindService.getCurrentPlayIndex() + 1) + "/" + castBindService.getFilePaths().length + ")";
        } else {
            videoFileSelected = "File: N/A";
        }

        infoText.setText(consoleSelected + "\n " + videoFileSelected /* + "\n " + castServerSelected */ );
    }

    // ACTIVITY LISTENER /////////////////////////////////
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent returnIntent) {
        // If the selection didn't work
        super.onActivityResult(requestCode, resultCode, returnIntent);
        boolean success = false;
        if (resultCode != RESULT_OK) {
            // Exit without doing anything else
            return;
        } else if (requestCode == PICKFILE_RESULT_CODE) {
            try {
                String [] filePaths = new String[0];
                if (returnIntent.getData() != null) {
                    filePaths = new String[] {FileHelper.getPath(ScreenCastActivity.this, returnIntent.getData())};
                    Log.e("HERE", "Got file URI from SINGLE return intent");
                    success = true;
                } else if (returnIntent.getClipData() != null) {
                    //multiple data received
                    Log.e("HERE", "Got file URI from MULTIPLE return intent");
                    ClipData clipData = returnIntent.getClipData();
                    filePaths = new String[clipData.getItemCount()];
                    for (int count = 0; count<clipData.getItemCount(); count++){
                        Uri uri = clipData.getItemAt(count).getUri();
                        filePaths[count] = FileHelper.getPath(ScreenCastActivity.this, uri);
                    }
                    success = true;
                }

                castBindService.setFilePaths(filePaths, AUDIO_CAST_MODE);

            } catch (Exception e) {
                e.printStackTrace();
                success = false;
            }

            if(!success) {
                Toast.makeText(ScreenCastActivity.this, "Invalid file selected. Please use a different file... If this happens on valid file its possible its in a protected directory. Try moving it to internal storage.", Toast.LENGTH_LONG).show();
            }
            updateInfoText();
        }
    }

    // MAIN DEVICE LISTENER
    @Override
    public void onBackPressed() {
        Log.e("HERE", "on back pressed restart main");
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.e("HERE", "On New Intent");
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);

        //handle special view from main activity
        if (intent.getBooleanExtra("showRemoteView", false)){
            Log.e("HERE", "setup remote view");
            showRemoteView();
        } else if (intent.getBooleanExtra("showCastView", false)){
            Log.e("HERE", "setup cast view");
            setCastType(intent);
            showCastView();
        }
        super.onNewIntent(intent);
    }

    @Override
    protected void onPause() {
        super.onPause();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    // CONNECT SKD Listeners ////////////////////////////////
    @Override
    public void onDeviceReady(ConnectableDevice device) {
        Log.e("HERE", device.getFriendlyName());
        Toast.makeText(ScreenCastActivity.this, "Connected to: " + device.getFriendlyName(), Toast.LENGTH_SHORT).show();
        mDevice = device;
        updateInfoText();
        if(castServiceBound) {
            castBindService.setDevice(device);
        }
        analyticsClient.logCustomEvent("cast_xbox_discovered", device.getFriendlyName());
    }

    @Override
    public void onDeviceDisconnected(ConnectableDevice device) {
        updateInfoText();
    }

    @Override
    public void onPairingRequired(ConnectableDevice device, DeviceService service, DeviceService.PairingType pairingType) {

    }

    @Override
    public void onCapabilityUpdated(ConnectableDevice device, List<String> added, List<String> removed) {

    }

    @Override
    public void onConnectionFailed(ConnectableDevice device, ServiceCommandError error) {
        updateInfoText();
    }

    @Override
    public void onDeviceAdded(DiscoveryManager manager, ConnectableDevice device) {
        if(device.getFriendlyName().contains("Xbox")) {
            Log.e("HERE", "FOUND XBOX");
        }
    }

    @Override
    public void onDeviceUpdated(DiscoveryManager manager, ConnectableDevice device) {

    }

    @Override
    public void onDeviceRemoved(DiscoveryManager manager, ConnectableDevice device) {

    }

    @Override
    public void onDiscoveryFailed(DiscoveryManager manager, ServiceCommandError error) {

    }

    // MEDIA PLAYBACK LISTENER

    // listen for duration changes
    public MediaControl.PlayStateListener playStateListener = new MediaControl.PlayStateListener() {

        @Override
        public void onError(ServiceCommandError error) {
            Log.d("HERE", "Playstate Listener error = " + error);
        }

        @Override
        public void onSuccess(MediaControl.PlayStateStatus playState) {
            Log.d("HERE", "Playstate changed | playState = " + playState);

            switch (playState) {
                case Playing:
                    startUpdating();

                    if (mMediaControl != null && mDevice.hasCapability(MediaControl.Duration)) {
                        mMediaControl.getDuration(durationListener);
                    }
                    break;
                case Finished:
                    ((TextView)findViewById(R.id.left_seek_tv)).setText("--:--");
                    ((TextView)findViewById(R.id.right_seek_tv)).setText("--:--");
                    ((SeekBar)findViewById(R.id.seekbar)).setProgress(0);
                    stopUpdating();
            }
        }
    };

    // listen for seekbar position moved by user
    public SeekBar.OnSeekBarChangeListener seekListener = new SeekBar.OnSeekBarChangeListener() {

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            seekBar.setSecondaryProgress(0);
            onSeekBarMoved(seekBar.getProgress());
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            seekBar.setSecondaryProgress(seekBar.getProgress());
            stopUpdating();
        }

        @Override
        public void onProgressChanged(SeekBar arg0, int arg1, boolean arg2) {

        }
    };

    // handle position seek bar moved by user
    protected void onSeekBarMoved(long position) {
        if (mMediaControl != null && mDevice.hasCapability(MediaControl.Seek)) {

            mMediaControl.seek(position, new ResponseListener<Object>() {

                @Override
                public void onSuccess(Object response) {
                    Log.d("HERE", "Success on Seeking");
                    startUpdating();
                }

                @Override
                public void onError(ServiceCommandError error) {
                    Log.w("Connect SDK", "Unable to seek: " + error.getCode());
                    startUpdating();
                }
            });
        }
    }

    // handle play listener update events
    private void startUpdating() {
        if (refreshTimer != null) {
            refreshTimer.cancel();
            refreshTimer = null;
        }
        refreshTimer = new Timer();
        refreshTimer.schedule(new TimerTask() {

            @Override
            public void run() {
                Log.d("HERE", "Updating information");
                if (mMediaControl != null && mDevice != null && mDevice.hasCapability(MediaControl.Position)) {
                    mMediaControl.getPosition(positionListener);
                }

                if (mMediaControl != null
                        && mDevice != null
                        && mDevice.hasCapability(MediaControl.Duration)
                        && !mDevice.hasCapability(MediaControl.PlayState_Subscribe)
                        && MEDIA_DURATION <= 0) {
                    mMediaControl.getDuration(durationListener);
                }
            }
        }, 0, 1000);
    }

    // gets where we are in the video
    private MediaControl.PositionListener positionListener = new MediaControl.PositionListener() {

        @Override public void onError(ServiceCommandError error) { }

        @Override
        public void onSuccess(Long position) {
            ((TextView)findViewById(R.id.left_seek_tv)).setText(""+Helper.formatTime(position.intValue()));
            ((SeekBar)findViewById(R.id.seekbar)).setProgress(position.intValue());
            MEDIA_CURRENT_TIME = position.intValue();
        }
    };

    // gets how long the video is
    private MediaControl.DurationListener durationListener = new MediaControl.DurationListener() {

        @Override public void onError(ServiceCommandError error) { }

        @Override
        public void onSuccess(Long duration) {
            MEDIA_DURATION = duration;
            if ( ((SeekBar)findViewById(R.id.seekbar)) != null && ((TextView)findViewById(R.id.right_seek_tv)) != null) {
                ((SeekBar)findViewById(R.id.seekbar)).setMax( duration.intValue() );
                ((TextView)findViewById(R.id.right_seek_tv)).setText(""+ Helper.formatTime(duration.intValue()) );
            }
        }
    };

    private void stopUpdating() {
        if (refreshTimer == null)
            return;
        try {
            refreshTimer.cancel();
            refreshTimer = null;
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void startService() {
        Intent serviceIntent = new Intent(this, ConvertForegroundService.class);
        serviceIntent.putExtra("filePath", castBindService.getCurrentVideoFile());
        ContextCompat.startForegroundService(this, serviceIntent);
        doBindConvertService();
    }
    public void stopService() {
        try {
            Message msg = Message.obtain(null, ConvertForegroundService.MSG_STOP_SERVICE, this.hashCode(), 0);
            mService.send(msg);
            doUnbindConvertService();
            Log.w("HERE", "Sent stop command");
        } catch(Exception e){
            e.printStackTrace();
        }

        Intent serviceIntent = new Intent(this, ConvertForegroundService.class);
        stopService(serviceIntent);
    }

    public void startForegroundCastService() {
        Intent serviceIntent = new Intent(this, CastToConsoleForegroundService.class);
        //serviceIntent.putExtra("filePaths", filePaths);
        ContextCompat.startForegroundService(this, serviceIntent);
        bindService(new Intent(ScreenCastActivity.this, CastToConsoleForegroundService.class), castToConsoleForegroundServiceBindConnection, Context.BIND_AUTO_CREATE);
    }
}