package com.studio08.xbgamestream.ui.remote;

import static com.studio08.xbgamestream.Helpers.Helper.checkIfAlreadyHavePermission;
import static com.studio08.xbgamestream.Helpers.Helper.requestForSpecificPermission;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.KeyboardMovementCalculations;
import com.studio08.xbgamestream.Helpers.PopupWebview;
import com.studio08.xbgamestream.Helpers.RewardedAdLoader;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.studio08.xbgamestream.databinding.FragmentRemoteBinding;
import com.studio08.xbgamestream.databinding.FragmentVoiceremoteBinding;

import org.json.JSONException;
import org.json.JSONObject;

import Interfaces.SmartglassEvents;
import network.BindService;

public class RemoteFragment extends Fragment implements SmartglassEvents {

    // listener - fires when streaming detects auth is required
    ApiClient.StreamingClientListener buttonPressListener = new ApiClient.StreamingClientListener() {
        @Override
        public void onReLoginDetected() {}

        @Override
        public void onCloseScreenDetected() {}

        @Override
        public void pressButtonWifiRemote(String type) {
            try {
                Log.e("HERE", "Nav Remote button press: " + type);
                Helper.vibrate(getActivity());
            } catch (Exception e){
                e.printStackTrace();
            }
            if(type != null && type.equals("power")){
                ((MainActivity) requireActivity()).mBoundService.powerOn(getLiveId());
                ((MainActivity) requireActivity()).mBoundService.powerOn(getLiveId());
                ((MainActivity) requireActivity()).mBoundService.powerOn(getLiveId());
                ((MainActivity) requireActivity()).mBoundService.powerOff();
                return;
            }
            byte [] desiredButton = Helper.convertStringButtonToByteArray(type);
            ((MainActivity) requireActivity()).mBoundService.sendSystemInputCommand(desiredButton);
        }
        @Override
        public void setOrientationValue(String value) {
           // unused
        }

        @Override
        public void vibrate() {}
        @Override
        public void genericMessage(String type, String msg) {
            try{
                SharedPreferences prefs = getActivity().getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
                int keyboardSpeed;
                try {
                    keyboardSpeed = Integer.parseInt(prefs.getString("keyboard_speed_key", "0"));
                }
                catch (NumberFormatException e) {
                    keyboardSpeed = 0;
                }

                if(type.equals("keyboard_input")){ // normal keyboard keypress
                    JSONObject data = new JSONObject(msg);
                    String start = data.getString("start");
                    String dest = data.getString("destination");
                    String keyboardType = data.getString("keyboard_type");

                    if (TextUtils.isEmpty(keyboardType)){
                        Toast.makeText(getActivity(), "Enter a keyboard type. What app are you trying to search with?", Toast.LENGTH_LONG).show();
                        throw new Error("Invalid Char");
                    } else if(TextUtils.isEmpty(start)){
                        Toast.makeText(getActivity(), "Enter a cursor start position. What key is currently highlighted on the screen?", Toast.LENGTH_LONG).show();
                        throw new Error("Invalid Char");
                    } else if (TextUtils.isEmpty(dest)){
                        Toast.makeText(getActivity(), "Invalid character selected. What character did you type? Special characters are not supported.", Toast.LENGTH_LONG).show();
                        throw new Error("Invalid Char");
                    }
                    KeyboardMovementCalculations keyboardMovementCalculations = new KeyboardMovementCalculations(start, dest,  keyboardType);
                    byte [][] seq = keyboardMovementCalculations.convertPositionsToByteArray();
                    if(seq == null){
                        Toast.makeText(getActivity(), "Invalid character selected. Special characters are not supported.", Toast.LENGTH_SHORT).show();
                        throw new Error("Invalid Char");
                    }
                    ((MainActivity) requireActivity()).mBoundService.sendSystemInputSequence(seq, keyboardSpeed);
                } else if(type.equals("keyboard_button")){ // x and y buttons
                    if(msg.equals("Backspace")){
                        ((MainActivity) requireActivity()).mBoundService.sendSystemInputSequence(new byte[][]{
                                Helper.convertStringButtonToByteArray("x")
                        }, keyboardSpeed);
                    } else if(msg.equals("Space")){
                        ((MainActivity) requireActivity()).mBoundService.sendSystemInputSequence(new byte[][]{
                                Helper.convertStringButtonToByteArray("y")
                        }, keyboardSpeed);
                    } else {
                        Log.e("HERE", "Invalid payload? " + msg);
                    }
                } else if(type.equals("show_keyboard_popup")){
                    ((MainActivity) getContext()).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PopupWebview popup = new PopupWebview(getActivity());
                            popup.showPopup(getView(), PopupWebview.KEYBOARD_WARNING_POPUP);;
                        }
                    });
                } else {
                    Log.e("HERE", "Invalid type? " + type);
                }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        }
    };

    private FragmentRemoteBinding binding;
    private FragmentVoiceremoteBinding bindingVoice;

    private Dialog dialog;
    Button startRemote;
    Button buildRemote;
    FloatingActionButton helpButton;
    StreamWebview streamView;
    Intent serviceIntent;
    ApiClient streamingClient;
    boolean inRemoteView = false;
    private int RETRY_COUNT = 3;
    private int CONNECT_TIMEOUT = 1500;

    private NavController localNavController;
    private ServiceConnection localServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {
            ((MainActivity) requireActivity()).mServiceBound = false;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BindService.MyBinder myBinder = (BindService.MyBinder) service;
            ((MainActivity) requireActivity()).mBoundService = myBinder.getService();
            ((MainActivity) requireActivity()).mServiceBound = true;

            ((MainActivity) requireActivity()).mBoundService.setListener(RemoteFragment.this);
            ((MainActivity) requireActivity()).mBoundService.discover();
        }
    };

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log.e("HERE", "Creating frag");

        ((MainActivity) getActivity()).setOrientationPortrait();
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("nav_remote");
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        View root;
        if(checkIfVoiceRemote()){
            bindingVoice = FragmentVoiceremoteBinding.inflate(inflater, container, false);
            root = bindingVoice.getRoot();
            setupVoiceRemoteViews(root);
        } else {
            binding = FragmentRemoteBinding.inflate(inflater, container, false);
            root = binding.getRoot();
            setupRemoteViews(root);
        }

        setupSharedViews(root);

        localNavController = ((MainActivity) requireActivity()).navController;

        return root;
    }

    private void setupVoiceRemoteViews(View view){
        buildRemote = view.findViewById(R.id.remote_builder_button);
    }
    private void setupRemoteViews(View view){
        buildRemote = view.findViewById(R.id.remote_builder_button);
        buildRemote.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_builder_remote");
                // actually load builder
                ((MainActivity) getActivity()).setOrientationPortrait();
                loadWifiRemoteBuilderView();
            }
        });

        buildRemote.setVisibility(View.VISIBLE);
    }

    private void setupSharedViews(View view){
        // setup webview
        streamView = view.findViewById(R.id.webview1);
        streamView.setBackgroundColor(Color.TRANSPARENT);
        streamView.init();

        // setup start button
        startRemote = view.findViewById(R.id.remote_button);
        startRemote.setVisibility(View.VISIBLE);
        startRemote.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_nav_remote");

                // start listening for ad play
                ((MainActivity)getActivity()).rewardedAd.setCallbackListener(new RewardedAdLoader.RewardAdListener() {
                    @Override
                    public void onRewardComplete() {
                        Log.e("HERE", "onRewardCompleteCaught!");
                        connectToConsole(RETRY_COUNT);
                    }
                });

                // start ad play and listen for complete
                ((MainActivity)getActivity()).showConnectAdPossibly();
            }
        });

        helpButton = view.findViewById(R.id.help_button);
        helpButton.setVisibility(View.VISIBLE);
        helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(checkIfVoiceRemote()){
                    PopupWebview popup = new PopupWebview(getActivity());
                    popup.showPopup(view, PopupWebview.VOICE_REMOTE_POPUP);
                } else {
                    PopupWebview popup = new PopupWebview(getActivity());
                    popup.showPopup(view, PopupWebview.MEDIA_REMOTE_POPUP);
                }
            }
        });
    }

    private boolean checkIfVoiceRemote(){
        if( NavHostFragment.findNavController(RemoteFragment.this).getCurrentDestination().getId() == R.id.nav_voiceremote){
            Log.e("HERE", "Using Voice Remote");
            return true;
        }
        return false;
    }

    public void connectToConsole(int retries){
        try {
            Log.e("Remote", "Connecting to console retries left:" + retries);

            Activity activity = getActivity();
            if(activity == null || !isAdded()){
                Log.e("HERE", "Caught activity not added! lets reload the fucking activity for some reason");
                localNavController.navigate(R.id.nav_remote);

                ((MainActivity) getContext()).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getContext(), "Disconnected. Please reconnect", Toast.LENGTH_LONG).show();
                    }
                });

            }

            //if service already bound and had discovered an xbox just the wifi view
            if ( ((MainActivity) getActivity()).mServiceBound && ((MainActivity) getActivity()).mBoundService.ready) {
                Log.e("HERE", "Detected service running. Loading wifi remote");
                loadWifiRemoteView();
                return;
            }

            // show loading popup for user
            try {
                setProgressDialog(getContext());
            } catch (Exception e) {
                e.printStackTrace();
            }

            // start xbox smartglass
            serviceIntent = new Intent((MainActivity) getContext(), BindService.class);
            SharedPreferences prefs = (getActivity()).getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            String notificationSettings = prefs.getString("notification_remote_key", "close_on_exit");
            serviceIntent.putExtra("notification_type", notificationSettings);
            ContextCompat.startForegroundService(getActivity(), serviceIntent);
            ((MainActivity) requireActivity()).mServiceConnection = localServiceConnection;
            ((MainActivity) getContext()).bindService(serviceIntent, localServiceConnection, Context.BIND_AUTO_CREATE);
            if (((MainActivity) requireActivity()).mServiceBound) {
                ((MainActivity) requireActivity()).mBoundService.discover();
            }

            retryConnect(retries);
        } catch (Exception e){
            e.printStackTrace();
        }

    }

    private void retryConnect(int retriesLeft) {

        // show error after retrying
        if(retriesLeft <= 0){
            closeProgressDialog();
            showErrorConnectDialog();
            return;
        }

        // attempt to connect again if we are not connected
        final Handler handler = new Handler(Looper.getMainLooper());
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                connectToConsole(retriesLeft - 1);
            }
        }, CONNECT_TIMEOUT);
    }

    private void showErrorConnectDialog(){
        closeProgressDialog();
        String message = "Unable to connect to console. Ensure you are on the same WiFi network as your console and it's powered on.\n\nTips: If you are still unable to connect, hold the power button on your console for 10 seconds to hard reboot it, then restart the app and again. This almost always fixes any issues.";
        AlertDialog.Builder tmpDialog = new AlertDialog.Builder(getActivity());
        tmpDialog.setTitle("Unable to Connect");
        tmpDialog.setMessage(message);
        tmpDialog.setCancelable(true);
        tmpDialog.setNegativeButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        if(!getLiveId().equals("")){
            tmpDialog.setMessage(message + "\n\nIf your Xbox is currently powered off, send the wake command to attempt to turn it on.\n\nAn Xbox can only be powered on by a wifi remote if it is in standby mode (must enable in console's settings). If you powered off your console by pressing the power button on your console (as apposed to using a controller), then standby mode is disabled and this app won't be able to turn it on.");

            tmpDialog.setPositiveButton("Send Wake Command", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    Log.e("HERE",getLiveId());
                    ((MainActivity) requireActivity()).mBoundService.powerOn(getLiveId());
                    ((MainActivity) requireActivity()).mBoundService.powerOn(getLiveId());
                    ((MainActivity) requireActivity()).mBoundService.powerOn(getLiveId());
                }
            });
        }
        tmpDialog.show();
    }

    public void loadWifiRemoteView(){
        closeProgressDialog();
        if(checkIfVoiceRemote()) {
            if (checkIfAlreadyHavePermission(Manifest.permission.RECORD_AUDIO, getActivity())) {
                Log.e("HERE", "Already have audio perm");
            } else {
                Toast.makeText(getActivity(), "Grant Audio Permissions", Toast.LENGTH_SHORT).show();
                requestForSpecificPermission(new String[] {Manifest.permission.RECORD_AUDIO}, getActivity());
                return;
            }
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkIfAlreadyHavePermission(Manifest.permission.POST_NOTIFICATIONS, getActivity())) {
                    Log.e("HERE", "Already have notification perm");
                } else {
                    Toast.makeText(getActivity(), "Grant Notification Permissions", Toast.LENGTH_SHORT).show();
                    requestForSpecificPermission(new String[] {Manifest.permission.POST_NOTIFICATIONS}, getActivity());
                    return;
                }
            }
        }
        startRemote.setVisibility(View.INVISIBLE);
        buildRemote.setVisibility(View.INVISIBLE);
        helpButton.setVisibility(View.INVISIBLE);

        if(!inRemoteView) {
            streamingClient = new ApiClient(getActivity(), streamView);
            streamingClient.setCustomObjectListener(buttonPressListener);
            if(checkIfVoiceRemote()){
                streamingClient.doWifiVoiceRemote();
            } else {
                streamingClient.doWifRemote();
            }
            inRemoteView = true;
        } else {
            Log.e("HERE", "inRemoteView = true");

        }
    }

    public void loadWifiRemoteBuilderView(){
        closeProgressDialog();
        startRemote.setVisibility(View.INVISIBLE);
        buildRemote.setVisibility(View.INVISIBLE);
        helpButton.setVisibility(View.INVISIBLE);

        // load the builder view
        streamingClient = new ApiClient(getActivity(), streamView);
        streamingClient.setCustomObjectListener(buttonPressListener);
        streamingClient.doControllerBuilder("navigationRemoteControllerOption");

    }


    public void closeProgressDialog(){
        try{
            if(this.dialog != null) {
                this.dialog.dismiss();
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void setProgressDialog(Context context) {
        Log.e("HERE", "called show progress dialog!!!!");
        int llPadding = 10;
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setPadding(llPadding, llPadding, llPadding, llPadding);
        ll.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llParam = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        ll.setLayoutParams(llParam);

        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        progressBar.setPadding(0, 0, 0, 0);
        progressBar.setLayoutParams(llParam);
        progressBar.setVisibility(View.VISIBLE);

        llParam = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        TextView tvText = new TextView(context);
        tvText.setText("Connecting...");
        tvText.setTextColor(Color.parseColor("#FFFFFF"));
        tvText.setTextSize(20);
        tvText.setLayoutParams(llParam);

        ll.addView(progressBar);
        ll.addView(tvText);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(false);
        builder.setView(ll);

        closeProgressDialog();
        dialog = builder.create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(layoutParams);
        }
    }

    @Override
    public void xboxDiscovered() {
        Log.e("RemoteFrag", "Xbox Discovered");
        try {
            ((MainActivity) requireActivity()).mBoundService.connect();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void xboxConnected() {
        Log.e("RemoteFrag", "Xbox Connected");
        try {
            ((MainActivity) requireActivity()).mBoundService.openChannels();
            ((MainActivity) getContext()).runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    loadWifiRemoteView();
                }
            });
            String xboxLiveId = ((MainActivity) requireActivity()).mBoundService.getLiveId();
            saveLiveId(xboxLiveId);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private void saveLiveId(String liveId){
        Log.e("HERE","saving live id: " + liveId);
        EncryptClient encryptClient = new EncryptClient(getActivity());
        encryptClient.saveValue("liveId", liveId);
    }

    private String getLiveId(){
        EncryptClient encryptClient = new EncryptClient(getContext());
        return encryptClient.getValue("liveId");
    }

    @Override
    public void xboxDisconnected() {
        try {
            Log.e("HERE", "XBOX disconnected!!!");
            ((MainActivity) requireActivity()).runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    try {
                        ((MainActivity) requireActivity()).mBoundService.ready = false;
                        connectToConsole(RETRY_COUNT);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            });
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.e("HERE", "onPause");
        closeProgressDialog(); // try to close any open dialogs when resumed (specifically helpful for ads)
    }

    @Override
    public void onResume() {
        super.onResume();
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        closeProgressDialog(); // try to close any open dialogs when resumed (specifically helpful for ads)
        Log.e("HERE", "onResume");

    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            streamingClient.cleanUp();
        } catch(Exception e) {}
        binding = null;
        bindingVoice = null;
    }


    @Override
    public void onStop() {
        super.onStop();
        try {
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

}