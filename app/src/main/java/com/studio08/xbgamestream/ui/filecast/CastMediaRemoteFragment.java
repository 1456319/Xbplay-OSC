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

import com.studio08.xbgamestream.Helpers.RewardedAdLoader;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.ScreenCastActivity;
import com.studio08.xbgamestream.databinding.FragmentFilecastMediaRemoteBinding;

public class CastMediaRemoteFragment extends Fragment {

    private FragmentFilecastMediaRemoteBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("cast_remote");

        binding = FragmentFilecastMediaRemoteBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        Button startFileCast = binding.filecastButton;
        startFileCast.setVisibility(View.VISIBLE);

        startFileCast.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_cast_remote");

                // start listening for ad play
                ((MainActivity)getActivity()).rewardedAd.setCallbackListener(new RewardedAdLoader.RewardAdListener() {
                    @Override
                    public void onRewardComplete() {
                        Log.e("HERE", "onRewardCompleteCaught!");
                        Intent intent = new Intent(getContext(), ScreenCastActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        intent.putExtra("showRemoteView", true);
                        startActivity(intent);
                        getActivity().overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                });

                // start ad play and listen for complete
                ((MainActivity)getActivity()).showConnectAdPossibly();

            }
        });

        return root;
    }
}