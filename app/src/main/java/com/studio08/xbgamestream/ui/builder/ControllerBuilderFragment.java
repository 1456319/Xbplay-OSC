package com.studio08.xbgamestream.ui.builder;

import static com.studio08.xbgamestream.Helpers.Helper.getRenderEngine;

import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.Helpers.PopupWebview;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.studio08.xbgamestream.databinding.FragmentControllerBuilderBinding;

import org.mozilla.geckoview.GeckoView;

public class ControllerBuilderFragment extends Fragment {

    private ControllerBuilderViewModel galleryViewModel;
    private FragmentControllerBuilderBinding binding;
    ApiClient streamingClient;

    // listener - fires when streaming detects auth is required
    ApiClient.StreamingClientListener orientationChangeListener = new ApiClient.StreamingClientListener() {
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
        public void setOrientationValue(String value) {
            if(value.equals("landscape")) {
                ((MainActivity) getActivity()).setOrientationLandscape();
            } else if (value.equals("portrait")) {
                ((MainActivity) getActivity()).setOrientationPortrait();
            } else {
                Log.e("ERROR", "Invalid orientation specified from website");
            }
        }
        @Override
        public void vibrate() {}
        @Override
        public void genericMessage(String type, String msg) {}
    };
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("gamepad_builder");

        galleryViewModel = new ViewModelProvider(this).get(ControllerBuilderViewModel.class);

        binding = FragmentControllerBuilderBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        StreamWebview streamView = binding.webview1;
        GeckoView geckoStreamView = binding.geckowebview;

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

        Button startStream = binding.builderButton;
        startStream.setVisibility(View.VISIBLE);

        startStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("build_gamepad");
                binding.helpButton.setVisibility(View.INVISIBLE);
                startStream.setVisibility(View.INVISIBLE);

                if (getRenderEngine(getActivity()).equals("geckoview")){
                    streamingClient = new ApiClient(getActivity(), geckoStreamView);
                    streamingClient.setCustomObjectListener(orientationChangeListener);
                    geckoStreamView.setVisibility(View.VISIBLE);
                    geckoStreamView.requestFocus();
                } else {
                    streamingClient = new ApiClient(getActivity(), streamView);
                    streamingClient.setCustomObjectListener(orientationChangeListener);
                    streamView.requestFocus();
                }


                ((MainActivity) getActivity()).setOrientationLandscape();
                streamingClient.doControllerBuilder(null);
            }
        });

        binding.helpButton.setVisibility(View.VISIBLE);
        binding.helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupWebview popup = new PopupWebview(getActivity());
                popup.showPopup(view, PopupWebview.BUILDER_POPUP);
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            binding.getRoot().removeAllViews();
            streamingClient.cleanUp();
        } catch(Exception e) {}
        binding = null;
    }
}