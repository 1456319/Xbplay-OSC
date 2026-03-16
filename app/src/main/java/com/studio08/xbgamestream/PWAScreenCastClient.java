package com.studio08.xbgamestream;


import static android.app.Activity.RESULT_OK;
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
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
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
import androidx.core.content.ContextCompat;
import androidx.navigation.ui.AppBarConfiguration;

import com.connectsdk.device.ConnectableDevice;
import com.connectsdk.device.ConnectableDeviceListener;
import com.connectsdk.device.DevicePicker;
import com.connectsdk.discovery.DiscoveryManager;
import com.connectsdk.discovery.DiscoveryManagerListener;
import com.connectsdk.service.DeviceService;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.PlaylistControl;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommandError;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.studio08.xbgamestream.CastToConsole.CastToConsoleForegroundService;
import com.studio08.xbgamestream.Converter.ConvertForegroundService;
import com.studio08.xbgamestream.Helpers.FileHelper;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.MediaPickerHelper;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.Timer;
import java.util.TimerTask;


public class PWAScreenCastClient  implements ConnectableDeviceListener, DiscoveryManagerListener, CastToConsoleForegroundService.CastServiceListener, MediaPickerHelper.MediaPickerCallback {

    public boolean AUDIO_CAST_MODE = false;
    private static DiscoveryManager mDiscoveryManager;
    private static ConnectableDevice mDevice;
    private static final int READ_STORAGE_PERMISSION_REQUEST_CODE = 41;

//    TextView infoText;
    ProgressDialog progressDialog;
    MediaControl mMediaControl;
    PlaylistControl mPlaylistControl;

    int VOLUME_LEVEL = 100;
    boolean isMute = false;

    StreamWebview streamView;

    Messenger mService = null;
    boolean mIsBound;
    Context context;
    boolean didInit = false;
    StreamWebview mSystemWebview;
    boolean isPlaying = false;
    MediaPickerHelper mediaPickerHelper;
    public static int PICKFILE_RESULT_CODE = 556;

    public PWAScreenCastClient(Context ctx, StreamWebview mSystemWebview){
        this.context = ctx;
        this.mSystemWebview = mSystemWebview;
        mediaPickerHelper = new MediaPickerHelper((AppCompatActivity) ctx, PWAScreenCastClient.this);
    }

    @Override
    public void onMediaControlCreated(MediaControl mediaControl) {
        if(mMediaControl == null) {
            mMediaControl = mediaControl;
            mMediaControl.subscribePlayState(playStateListener);
        }
    }

    @Override
    public void onCastTitleUpdated() {
        updateInfoText();
    }

    @Override
    public void onMediaPicked(List<Uri> uris) {
        if (uris != null && !uris.isEmpty()) {
            Log.d("PWAScreenCastClient", "Number of items selected: " + uris.size());
            for (Uri uri : uris) {
                Log.d("PWAScreenCastClient", "Selected URI: " + uri.toString());
                // Process each selected URI, for example display or upload the media
                handleFileSelectCallback(uris);
            }
        } else {
            Log.d("PWAScreenCastClient", "No media selected");
        }
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
            } else {
                Log.e("HERE","Cast not running in service yet. Unable to load media control object" + (mMediaControl == null) + " - "+ (castBindService.getMediaControl() != null));
            }

            // get device object if already casted
            if(mDevice == null && castBindService.getDevice() != null){
                mDevice = castBindService.getDevice();
            }

            // set listener to update stats
            castBindService.setCastServiceListener(PWAScreenCastClient.this);
            updateInfoText();
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.e("HERE","Disconnected.");
            castServiceBound = false;
        }
    };

    void doBindConvertService() {
        this.context.bindService(new Intent(this.context, ConvertForegroundService.class), mConnection, Context.BIND_AUTO_CREATE);
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
            this.context.unbindService(mConnection);
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
            Toast.makeText(context, "Error sending command", Toast.LENGTH_SHORT).show();
            error.printStackTrace();
        }
    };

    // TODO use
    public void cleanUp() {
        Log.e("ScreenCastActivity", "Destroyed");
        try {
            didInit = false;
            isPlaying = false;

            if (mDiscoveryManager != null){
                mDiscoveryManager.stop();
                mDiscoveryManager = null;
            }
            doUnbindConvertService();

            // if we are not actively casting, stop the service to remote notification
            if(castServiceBound && mMediaControl == null){
                castBindService.stopServiceCommand();
            }
            updateInfoText();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void init(){
        if (!didInit){
            // immediately begin listening for consoles
            DiscoveryManager.init(this.context);
            mDiscoveryManager = DiscoveryManager.getInstance();
            mDiscoveryManager.addListener(PWAScreenCastClient.this);
            mDiscoveryManager.start();
            startForegroundCastService();
            didInit = true;
        }
    }

    public void setCastType(Intent intent){
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
        new AlertDialog.Builder(this.context)
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
            new AlertDialog.Builder(this.context)
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
                                Toast.makeText(context, "Error. Restart App", Toast.LENGTH_SHORT).show();
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
            progressDialog = new ProgressDialog(this.context);
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
                progressDialog.dismiss();
                stopService();
            }
        });
        try {
            progressDialog.show();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void sendCastRemoteCommand(String type){
        ((Activity)context).runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    if(mMediaControl == null){
                        return;
                    }
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
                            mMediaControl.rewind(castRemoteResponseListener);
//                            mMediaControl.seek((long) (MEDIA_CURRENT_TIME-20000), null);
                            break;
                        case "Fastforward":
//                            mMediaControl.seek((long) (MEDIA_CURRENT_TIME+20000), null);
                              mMediaControl.fastForward(castRemoteResponseListener);
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
                                Toast.makeText(context, VOLUME_LEVEL + "%", Toast.LENGTH_SHORT).show();
                            }

                            mDevice.getVolumeControl().setMute(false, null);
                            mDevice.getVolumeControl().setVolume((float)((double)VOLUME_LEVEL/100), null);
                            break;
                        case "Volumeup":
                            if (VOLUME_LEVEL <= 90) {
                                VOLUME_LEVEL += 10;
                                Toast.makeText(context, VOLUME_LEVEL + "%", Toast.LENGTH_SHORT).show();
                            }

                            mDevice.getVolumeControl().setMute(false, null);
                            mDevice.getVolumeControl().setVolume((float)((double) VOLUME_LEVEL/100), null);
                            break;
                        case "Return":
                            break;
                        default:
                            Toast.makeText(context, "Button not mapped", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    try {
                        // Get instance of Vibrator from current Context
                        Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

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
                    Toast.makeText(context, "Error sending command", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // MAIN BUTTONS
    public void selectDevice() {
        AdapterView.OnItemClickListener selectDevice = new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapter, View parent, int position, long id) {
                mDevice = (ConnectableDevice) adapter.getItemAtPosition(position);
                mDevice.addListener(PWAScreenCastClient.this);
                mDevice.connect();
            }
        };
        DevicePicker devicePicker = new DevicePicker((Activity) context);
        AlertDialog dialog = devicePicker.getPickerDialog("Loading Consoles...", selectDevice);
        dialog.show();
    }

    public void selectFile() {
        if(mDevice == null || !mDevice.isConnected()) {
            Toast.makeText(context, "Select an Xbox first!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!AUDIO_CAST_MODE){ // new good way to do it for video
            mediaPickerHelper.launchVideoPicker();
        } else { // no way to do it for audio, so we need all this crap. Hopefully media picker will audio only support soon
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if(!checkIfAlreadyHavePermission(Manifest.permission.READ_MEDIA_VIDEO, context) ||
                        !checkIfAlreadyHavePermission(Manifest.permission.READ_MEDIA_AUDIO, context)) { // ask for perms
                    try {
                        requestForSpecificPermission(new String[] {Manifest.permission.READ_MEDIA_VIDEO,
                                Manifest.permission.READ_MEDIA_AUDIO,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.POST_NOTIFICATIONS}, context);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return;
                }
            } else {
                if(!checkIfAlreadyHavePermission(Manifest.permission.READ_EXTERNAL_STORAGE, context) ||
                        !checkIfAlreadyHavePermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, context)) { // ask for perms
                    try {
                        requestForSpecificPermission(new String [] {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, context);
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
            ((Activity)context).startActivityForResult(chooseFile, PICKFILE_RESULT_CODE);
        }
    }

    public void castToConsole(boolean check360Device){
        if(mDevice == null || !mDevice.isConnected()) {
            Toast.makeText(context, "Select a console first", Toast.LENGTH_LONG).show();
            return;
        } else if (castBindService == null || castBindService.getFilePaths() == null) {
            Toast.makeText(context, "Choose a valid file first", Toast.LENGTH_LONG).show();
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
            Toast.makeText(context, "Error Casting. Please restart", Toast.LENGTH_SHORT).show();
        }
    }

    // converts video to 360 valid format if 360 device, else, calls castToConsole bypassing 360 check
    // get files paths cant be null at this point
    private void handle360FileConvert() {
        // if casting to a 360 and multiple files selected
        if(mDevice.getFriendlyName().contains("360") && castBindService.getFilePaths().length != 1){
            castToConsole(false);
            Toast.makeText(context, "Notice: 360 video convert feature not available when selecting multiple files", Toast.LENGTH_LONG).show();
            Toast.makeText(context, "If you get 'Playback Error', consider converting video format by selecting individual files.", Toast.LENGTH_LONG).show();
        } else if(mDevice.getFriendlyName().contains("360") && castBindService.getFilePaths().length == 1 && !castBindService.getFilePaths()[0].contains("converted")){
            SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            String convertPromptValue = prefs.getString("video_convert_prompt_key", "ask");

            // detect if should show promp
            if (convertPromptValue.equals("ask")) {
                new AlertDialog.Builder(context)
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
        createConvertProgressDialog("Starting...");
        startService();
    }
    private void handleNotificationClick(){
        String convertPath = ((Activity)context).getIntent().getStringExtra("outputPath");
        boolean completed = ((Activity)context).getIntent().getBooleanExtra("completed", false);
        String failed = ((Activity)context).getIntent().getStringExtra("convertError");

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
//        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
//        myToolbar.setTitle("Cast Media Remote");
//        setSupportActionBar(myToolbar);
//
//        if(mMediaControl == null){
//            Toast.makeText(PWAScreenCastClient.this, "Nothing playing. Cast something first!", Toast.LENGTH_LONG).show();
//            //return;
//        }
//        ApiClient streamingClient;
//        streamingClient = new ApiClient(PWAScreenCastClient.this, streamView);
//        streamingClient.setCustomObjectListener(buttonPressListener);
//        streamingClient.doCastRemote();
//
//        // set main buttons invisible
//        findViewById(R.id.cast_connect_button).setVisibility(View.INVISIBLE);
//        findViewById(R.id.cast_file_choose_button).setVisibility(View.INVISIBLE);
//        findViewById(R.id.cast_send_button).setVisibility(View.INVISIBLE);
//        findViewById(R.id.cast_remote_button).setVisibility(View.INVISIBLE);
//
//        // set return button visible
//        findViewById(R.id.webview1).setVisibility(View.VISIBLE);
//        findViewById(R.id.seekbar_layout).setVisibility(View.VISIBLE);
    }

    // HELPERS ///////////////////////////
    public void updateInfoText() {
        if (mSystemWebview == null || castBindService == null){
            return;
        }
        String consoleSelected = null;
        String videoFileSelected = null;
        String infoTextString = "";
        // set device name
        if(mDevice != null && mDevice.isConnected()){
            consoleSelected = "Connected to device: " + mDevice.getFriendlyName();
        }

        // set file name
        String filename = castBindService.getCurrentVideoFile();
        if(filename != null) {
            String prefix = (isPlaying) ? "Playing: " : "Selected: ";
            videoFileSelected = prefix + FileHelper.getFileNameFromPath(filename) + " (" + (castBindService.getCurrentPlayIndex() + 1) + "/" + castBindService.getFilePaths().length + ")";
        }

        if (videoFileSelected != null){
            infoTextString = videoFileSelected;
        } else if (consoleSelected != null){
            infoTextString = consoleSelected;
        } else {
            infoTextString = "";
        }
        String finalInfoTextString = infoTextString;
        ((Activity)context).runOnUiThread(() -> ApiClient.callJavaScript(mSystemWebview, "setCastData", finalInfoTextString, isPlaying));
    }

    public void handleFileSelectCallback(List<Uri> uris) {
        // If the selection didn't work
        boolean success = false;
        try {
            String[] filePaths = new String[0];
            if (uris != null && !uris.isEmpty()) {
                Log.d("PWAScreenCastClient", "Number of items selected: " + uris.size());

                // Convert the list of URIs into file paths
                filePaths = new String[uris.size()];
                for (int i = 0; i < uris.size(); i++) {
                    Uri uri = uris.get(i);
                    Log.d("PWAScreenCastClient", "Selected URI: " + uri.toString() + ". Is Audio: " + this.AUDIO_CAST_MODE );

                    // Convert URI to file path using your FileHelper class
                    filePaths[i] = FileHelper.getPath(context, uri);
                }

                success = true;
            }

            // Pass the file paths to the castBindService
            castBindService.setFilePaths(filePaths, AUDIO_CAST_MODE);
        } catch (Exception e) {
            e.printStackTrace();
            success = false;
        }

        // Handle the case where the file selection failed
        if (!success) {
            Toast.makeText(context, "Invalid file selected. Please use a different file... If this happens on a valid file, it might be in a protected directory. Try moving it to internal storage.", Toast.LENGTH_LONG).show();
        }

        updateInfoText();
    }


    // legacy way to do it, replace with photo picker
    public void handleFileSelectCallback(Intent returnIntent) {
        // If the selection didn't work
        boolean success = false;
        try {
            String [] filePaths = new String[0];
            if (returnIntent.getData() != null) {
                filePaths = new String[] {FileHelper.getPath(context, returnIntent.getData())};
                Log.e("HERE", "Got file URI from SINGLE return intent");
                success = true;
            } else if (returnIntent.getClipData() != null) {
                //multiple data received
                Log.e("HERE", "Got file URI from MULTIPLE return intent");
                ClipData clipData = returnIntent.getClipData();
                filePaths = new String[clipData.getItemCount()];
                for (int count = 0; count<clipData.getItemCount(); count++){
                    Uri uri = clipData.getItemAt(count).getUri();
                    filePaths[count] = FileHelper.getPath(context, uri);
                }
                success = true;
            }

            castBindService.setFilePaths(filePaths, AUDIO_CAST_MODE);
        } catch (Exception e) {
            e.printStackTrace();
            success = false;
        }

        if(!success) {
            Toast.makeText(context, "Invalid file selected. Please use a different file... If this happens on valid file its possible its in a protected directory. Try moving it to internal storage.", Toast.LENGTH_LONG).show();
        }
        updateInfoText();
    }

    // CONNECT SKD Listeners ////////////////////////////////
    @Override
    public void onDeviceReady(ConnectableDevice device) {
        Log.e("HERE", device.getFriendlyName());
        Toast.makeText(context, "Connected to: " + device.getFriendlyName(), Toast.LENGTH_SHORT).show();
        mDevice = device;
        updateInfoText();
        if(castServiceBound) {
            castBindService.setDevice(device);
        }
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
            isPlaying = false;
        }

        @Override
        public void onSuccess(MediaControl.PlayStateStatus playState) {
            Log.d("HERE", "Playstate changed | playState = " + playState);

            switch (playState) {
                case Playing:
                    if (!isPlaying){
                        isPlaying = true;
                        updateInfoText();
                    }
                    break;
                case Finished:
                    if (isPlaying){
                        isPlaying = false;
                        updateInfoText();
                    }
                    break;
            }
        }
    };


    public void startService() {
        Intent serviceIntent = new Intent(context, ConvertForegroundService.class);
        serviceIntent.putExtra("filePath", castBindService.getCurrentVideoFile());
        ContextCompat.startForegroundService(context, serviceIntent);
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

        Intent serviceIntent = new Intent(context, ConvertForegroundService.class);
        context.stopService(serviceIntent);
    }

    public void startForegroundCastService() {
        Intent serviceIntent = new Intent(context, CastToConsoleForegroundService.class);
        //serviceIntent.putExtra("filePaths", filePaths);
        ContextCompat.startForegroundService(context, serviceIntent);
        context.bindService(new Intent(context, CastToConsoleForegroundService.class), castToConsoleForegroundServiceBindConnection, Context.BIND_AUTO_CREATE);
    }
}