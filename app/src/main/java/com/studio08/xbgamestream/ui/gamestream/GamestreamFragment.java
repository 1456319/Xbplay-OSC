package com.studio08.xbgamestream.ui.gamestream;

import static com.studio08.xbgamestream.Helpers.Helper.getRenderEngine;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

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
import com.studio08.xbgamestream.databinding.FragmentGamestreamBinding;
import org.mozilla.geckoview.GeckoView;

public class GamestreamFragment extends Fragment {

    private GamestreamViewModel galleryViewModel;
    private FragmentGamestreamBinding binding;
    ApiClient streamingClient;
    boolean viewActive = false;
    boolean startStreamClicked = false;

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
        public void pressButtonWifiRemote(String type) {

        }
        @Override
        public void setOrientationValue(String value) {}

        @Override
        public void vibrate() {
            Helper.vibrate(getActivity());
        }

        @Override
        public void genericMessage(String type, String msg) {
            if(type.equals("quitGame")){
                Toast.makeText(getActivity(), "Quitting Stream", Toast.LENGTH_LONG).show();

                // reload the page to annoy the user
                if (((MainActivity) getActivity()) != null && ((MainActivity) getActivity()).navController.getCurrentDestination() != null){

                    (getActivity()).runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ((MainActivity) getActivity()).navController.navigate(R.id.nav_gamestream);

                            if (((MainActivity) getActivity()).inFullScreenMode) {
                                ((MainActivity) getActivity()).showSystemUI();
                            }
                        }
                    });
                }
            } else {
                Log.e("HERE", "Unknown generic message received in xhomeFrag: " + type);
            }
        }
    };

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
        startStreamClicked = false;
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("gamestream");

        galleryViewModel = new ViewModelProvider(this).get(GamestreamViewModel.class);

        binding = FragmentGamestreamBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        // load geckoview or streamview based on setting
        GeckoView geckoStreamView = binding.geckowebview;
        StreamWebview streamView = binding.webview1;
        Button startStream = binding.gamestreamButton;

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

        startStream.setVisibility(View.VISIBLE);

        startStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startStreamButtonPress();
            }
        });

        binding.helpButton.setVisibility(View.VISIBLE);

        binding.helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupWebview popup = new PopupWebview(getActivity());
                popup.showPopup(view, PopupWebview.GAMESTREAM_POPUP);
            }
        });

        return root;
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is killed and restarted.
        savedInstanceState.putBoolean("startStreamClicked", startStreamClicked);
    }
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        Log.e("xHomeFrag", "onViewStateRestored" + savedInstanceState);
        super.onViewStateRestored(savedInstanceState);
        if(savedInstanceState != null && savedInstanceState.getBoolean("startStreamClicked")){
            Log.e("xHomeFrag", "Recreated from orientation change" + savedInstanceState.toString());
            startStreamButtonPress();
        }
    }

    private void startStreamButtonPress(){
        startStreamClicked = true;
        EncryptClient encryptClient = new EncryptClient(getContext());
        String serverId = encryptClient.getValue("serverId");
        String gsToken = encryptClient.getValue("gsToken");

        GeckoView geckoStreamView = binding.geckowebview;
        StreamWebview streamView = binding.webview1;
        Button startStream = binding.gamestreamButton;

        if (!TextUtils.isEmpty(gsToken) && !TextUtils.isEmpty(serverId)) {
            ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_gamestream");

            // start listening for ad play
            ((MainActivity)getActivity()).rewardedAd.setCallbackListener(new RewardedAdLoader.RewardAdListener() {
                @Override
                public void onRewardComplete() {
                    if(binding != null) { //
                        Log.e("HERE", "onRewardCompleteCaught!");
                        ((MainActivity) getActivity()).setOrientationLandscape();

                        binding.helpButton.setVisibility(View.INVISIBLE);
                        startStream.setVisibility(View.INVISIBLE);

                        if (getRenderEngine(getActivity()).equals("geckoview")){
                            streamingClient = new ApiClient(getActivity(), geckoStreamView, gsToken, serverId, false);
                            geckoStreamView.setVisibility(View.VISIBLE);
                            geckoStreamView.requestFocus();
                        } else {
                            streamingClient = new ApiClient(getActivity(), streamView, gsToken, serverId, false);
                            streamView.requestFocus();
                        }

                        streamingClient.setCustomObjectListener(loginRequiredListener);
                        streamingClient.setControllerHandler(((MainActivity) getActivity()).controllerHandler);
                        streamingClient.doStreaming();

                        // auto hide system UI on stream started
                        if( ((MainActivity) getActivity()) != null && !((MainActivity) getActivity()).inFullScreenMode){
                            ((MainActivity) getActivity()).hideSystemUI();
                        }
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
                    .setMessage("You must login to connect to your console.")
                    .setCancelable(false)
                    .setPositiveButton("Login", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            promptUserForLogin();
                        }
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
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
                ((MainActivity) getActivity()).navController.navigate(R.id.nav_gamestream);
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
            binding.getRoot().removeAllViews();
            streamingClient.cleanUp();
        } catch(Exception e) {
            e.printStackTrace();
        }
        binding = null;
    }
}