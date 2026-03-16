package com.studio08.xbgamestream.ui.xcloud;

import static com.studio08.xbgamestream.Helpers.Helper.getRenderEngine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.fragment.app.Fragment;

import com.studio08.xbgamestream.Authenticate.LoginActivity;
import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.PopupWebview;
import com.studio08.xbgamestream.Helpers.RewardedAdLoader;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.databinding.FragmentXcloudBinding;

import org.json.JSONObject;
import org.mozilla.geckoview.GeckoView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;

public class XcloudFragment extends Fragment {

    private FragmentXcloudBinding binding;
    ApiClient streamingClient;
    boolean viewActive = false;
    StreamWebview streamView;
    GeckoView geckoStreamView;
    String startStreamClickedGameTitleId = null;

    // listener - fires when streaming detects auth is required
    ApiClient.StreamingClientListener loginRequiredListener = new ApiClient.StreamingClientListener() {
        @Override
        public void onReLoginDetected() {
            Toast.makeText(getActivity(), "Re Login Required!", Toast.LENGTH_LONG).show();
            promptUserForLogin();
        }
        // closing screen not supported in this view
        @Override
        public void onCloseScreenDetected() {}

        @Override
        public void pressButtonWifiRemote(String type) {}

        @Override
        public void setOrientationValue(String value) {}

        @Override
        public void vibrate() {
            Helper.vibrate(getActivity());
        }

        @Override
        public void genericMessage(String type, String msg) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    handleGenericMessage(type, msg);
                }
            });
        }
    };

    public void handleGenericMessage(String type, String msg){
        if(type.equals("start_xcloud_stream_v2")){ // new version

            try {
                JSONObject results = new JSONObject(msg);
                String titleId = results.getString("titleId");
                String iconUrl = results.getString("image");
                String friendlyName =  results.getString("title");
                Log.e("HERE", results.toString());

                AlertDialog.Builder popup = new AlertDialog.Builder(getActivity())
                        .setTitle("Start Game?")
                        .setMessage("Start Playing: " + friendlyName)
                        .setCancelable(true)
                        .setPositiveButton("Play", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                handleGenericMessage("start_xcloud_stream", titleId);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });

                // if its not possible to create a shortcut, start stream.
                if (ShortcutManagerCompat.isRequestPinShortcutSupported(getContext())){
                    popup.setMessage("Start Playing: " + friendlyName + "?\n\nPlay this game often? Add a home-screen shortcut to start directly into this game!");
                    popup.setNeutralButton("Add Shortcut", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            Helper.addShortcutToHomeScreen(getActivity(), titleId, friendlyName, iconUrl, "xcloud");
                        }
                    });
                }
                popup.show();
            } catch (Exception e){
                e.printStackTrace();
                Log.e("HERE", "Failed to decode json message");
            }
        } else if(type.equals("start_xcloud_stream")){ // deprecated

            Toast.makeText(getActivity(), "Starting " + msg + "!", Toast.LENGTH_LONG).show();
            startStreamClickedGameTitleId = msg;

            EncryptClient encryptClient = new EncryptClient(getContext());
            String xcloudToken = encryptClient.getValue("xcloudToken");
            if (!TextUtils.isEmpty(xcloudToken) ) {
                ((MainActivity) getActivity()).setOrientationLandscape();

                startXCloudStream(msg);
            } else {
                promptUserForLogin();
            }
            Helper.hideKeyboard(getActivity());
        } else if (type.equals("quitGame")){
            Toast.makeText(getActivity(), "Quitting Stream", Toast.LENGTH_LONG).show();

            // reload the page to annoy the user
            if (((MainActivity) getActivity()) != null && ((MainActivity) getActivity()).navController.getCurrentDestination() != null){

                (getActivity()).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        ((MainActivity) getActivity()).navController.navigate(R.id.nav_xcloud);

                        if (((MainActivity) getActivity()).inFullScreenMode) {
                            ((MainActivity) getActivity()).showSystemUI();
                        }
                    }
                });
            }
        } else {
           Log.e("HERE", "Unknown generic message received in xcloudFrag: " + type);
        }
    }
    private void startXCloudStream(String titleId){

        EncryptClient encryptClient = new EncryptClient(getContext());
        String xcloudToken = encryptClient.getValue("xcloudToken");

        if (getRenderEngine(getActivity()).equals("geckoview")){
            streamingClient = new ApiClient(getActivity(), geckoStreamView, xcloudToken, titleId, true);
            binding.geckowebview.setVisibility(View.VISIBLE);
            geckoStreamView.requestFocus();
        } else {
            streamingClient = new ApiClient(getActivity(), streamView, xcloudToken, titleId, true);
            streamView.requestFocus();
        }

        streamingClient.setCustomObjectListener(loginRequiredListener);
        streamingClient.setControllerHandler(((MainActivity) getActivity()).controllerHandler);
        streamingClient.doStreaming();
        streamView.requestFocus();

    }
    public void promptUserForLogin() {
        try {
            Intent intent = new Intent(getContext(), LoginActivity.class);
            getActivity().startActivityForResult(intent, 444);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        viewActive = true;
        startStreamClickedGameTitleId = null;
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("xcloud");
        getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);

        binding = FragmentXcloudBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        streamView = binding.webview1;
        geckoStreamView = binding.geckowebview;

        if(getRenderEngine(getActivity()).equals("geckoview")) {
            Log.w("HERE", "Using GeckoView");
            geckoStreamView.setBackgroundColor(Color.TRANSPARENT);
            geckoStreamView.coverUntilFirstPaint(Color.TRANSPARENT);
            streamView.setVisibility(View.GONE);
            geckoStreamView.setVisibility(View.INVISIBLE); // since we cant make it clear
        } else {
            Log.w("HERE", "Not using GeckoView");
            geckoStreamView.setVisibility(View.GONE);
            streamView.setBackgroundColor(Color.TRANSPARENT);
            streamView.init();
        }

        Button startStream = binding.xcloudButton;
        startStream.setVisibility(View.VISIBLE);

        // get
        EncryptClient encryptClient = new EncryptClient(getContext());
        String xcloudToken = encryptClient.getValue("xcloudToken");

        startStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_xcloud");

                if (!TextUtils.isEmpty(xcloudToken) ) {

                    // start listening for ad play
                    ((MainActivity)getActivity()).rewardedAd.setCallbackListener(new RewardedAdLoader.RewardAdListener() {
                        @Override
                        public void onRewardComplete() {
                            if(binding != null) {
                                Log.e("HERE", "onRewardCompleteCaught!");
                                hideNonWebviewUI();

                                // auto hide system UI on stream started
                                if( ((MainActivity) getActivity()) != null) {
                                    if (!((MainActivity) getActivity()).inFullScreenMode) {
                                        ((MainActivity) getActivity()).hideSystemUI();
                                    }

                                    // short circuit the connect button press to load a game directly
                                    if ( ((MainActivity) getActivity()).xCloudShortcutTitleId != null){
                                        startXCloudStream(((MainActivity) getActivity()).xCloudShortcutTitleId);
                                        return;
                                    }
                                }

                                if (getRenderEngine(getActivity()).equals("geckoview")){
                                    streamingClient = new ApiClient(getActivity(), geckoStreamView, xcloudToken, null, true);
                                    geckoStreamView.setVisibility(View.VISIBLE);
                                    geckoStreamView.requestFocus();
                                } else {
                                    streamingClient = new ApiClient(getActivity(), streamView, xcloudToken, null, false);
                                    streamView.requestFocus();
                                }

                                streamingClient.setCustomObjectListener(loginRequiredListener);
                                streamingClient.setControllerHandler(((MainActivity) getActivity()).controllerHandler);
                                streamingClient.doXcloudGamePicker();
                            } else {
                                Log.e("HERE", "Binding null");
                            }
                        }
                    });

                    // start ad play and listen for complete
                    ((MainActivity)getActivity()).showConnectAdPossibly();
                } else {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Login Required")
                            .setMessage("You must login to use this feature.\n\nNote, Microsoft requires you to have a 'Game Pass' subscription to play xCloud games. If you don't have Game Pass, use the xHome feature.")
                            .setCancelable(false)
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    promptUserForLogin();
                                }
                            })
                            .show();
                }
            }
        });

        binding.textXcloudFooter.setVisibility(View.VISIBLE);
        binding.helpButton.setVisibility(View.VISIBLE);
        binding.helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupWebview popup = new PopupWebview(getActivity());
                popup.showPopup(view, PopupWebview.XCLOUD_POPUP);
            }
        });



        return root;
    }

    private void hideNonWebviewUI(){
        binding.helpButton.setVisibility(View.INVISIBLE);
        binding.textXcloudFooter.setVisibility(View.INVISIBLE);
        binding.xcloudButton.setVisibility(View.INVISIBLE);
    }
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is killed and restarted.
        if (startStreamClickedGameTitleId != null){
            savedInstanceState.putString("startStreamClickedGameTitleId", startStreamClickedGameTitleId);
        }
    }

    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        Log.e("xHomeFrag", "onViewStateRestored" + savedInstanceState);
        super.onViewStateRestored(savedInstanceState);
        if(savedInstanceState != null && savedInstanceState.getString("startStreamClickedGameTitleId") != null){
            Log.e("xHomeFrag", "Recreated from orientation change" + savedInstanceState.toString());
            hideNonWebviewUI();
            startXCloudStream(savedInstanceState.getString("startStreamClickedGameTitleId"));
            if( ((MainActivity) getActivity()) != null && !((MainActivity) getActivity()).inFullScreenMode){
                ((MainActivity) getActivity()).hideSystemUI();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            viewActive = false;
            binding.getRoot().removeAllViews();
            streamingClient.cleanUp();
        } catch(Exception e) {}
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        if(!viewActive) {
            try {
                ((MainActivity) getActivity()).navController.navigate(R.id.nav_xcloud);
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        try {
            viewActive = false;
            getActivity().getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            binding.getRoot().removeAllViews();
            streamingClient.cleanUp();
        } catch(Exception e) {
            e.printStackTrace();
        }
        binding = null;
    }
}