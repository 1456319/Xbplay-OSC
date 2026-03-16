package com.studio08.xbgamestream.ui.filecast;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.studio08.xbgamestream.Helpers.PopupWebview;
import com.studio08.xbgamestream.Helpers.RewardedAdLoader;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.ScreenCastActivity;
import com.studio08.xbgamestream.databinding.FragmentFilecastBinding;

public class FileCastFragment extends Fragment {

    private FragmentFilecastBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("video_cast");


        binding = FragmentFilecastBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        Button startFileCast = binding.filecastButton;
        startFileCast.setVisibility(View.VISIBLE);

        startFileCast.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_video_cast");

                // start listening for ad play
                ((MainActivity)getActivity()).rewardedAd.setCallbackListener(new RewardedAdLoader.RewardAdListener() {
                    @Override
                    public void onRewardComplete() {
                        Log.e("HERE", "onRewardCompleteCaught!");
                        try {
                            Intent intent = new Intent((MainActivity)getActivity(), ScreenCastActivity.class);
                            intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            intent.putExtra("showCastView", true);
                            intent.putExtra("audioCastType", false);
                            startActivity(intent);
                            getActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                        } catch (Exception e){
                            Log.e("HERE", "Failed to start file cast activity");
                            e.printStackTrace();
                        }
                    }
                });

                // start ad play and listen for complete
                ((MainActivity)getActivity()).showConnectAdPossibly();
            }
        });

        binding.helpButton.setVisibility(View.VISIBLE);
        binding.helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupWebview popup = new PopupWebview(getActivity());
                popup.showPopup(view, PopupWebview.FILECAST_POPUP);
            }
        });

        return root;
    }
}