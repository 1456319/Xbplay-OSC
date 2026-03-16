package com.studio08.xbgamestream.ui.tvcast;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.mediarouter.app.MediaRouteButton;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.IntroductoryOverlay;
import com.studio08.xbgamestream.Authenticate.LoginActivity;
import com.studio08.xbgamestream.Helpers.PopupWebview;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.databinding.FragmentTvcastRemoteBinding;

public class TVCastFragment extends Fragment {

    private FragmentTvcastRemoteBinding remoteBinding;


    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("tvcast");

        // load remote specific view
        remoteBinding = FragmentTvcastRemoteBinding.inflate(inflater, container, false);
        View root = remoteBinding.getRoot();

        // setup cast button (exists in both views)
        MediaRouteButton mMediaRouteButton = (MediaRouteButton) remoteBinding.tvcastButton;
        CastButtonFactory.setUpMediaRouteButton(getActivity(), mMediaRouteButton);

        remoteBinding.tvcastRefreshButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if (isCastConnected()) {
                        ((MainActivity) requireActivity()).mCastSession.sendMessage("urn:x-cast:xbplay-refreshVideo", "{}");
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

//        remoteBinding.tvcastMuteButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                try {
//                    if(isCastConnected()) {
//                        ((MainActivity) requireActivity()).mCastSession.sendMessage("urn:x-cast:xbplay-toggleMute", "{}");
//                    }
//                } catch (Exception e){
//                    e.printStackTrace();
//                }
//            }
//        });
        remoteBinding.tvcastToggleInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    if(isCastConnected()) {
                        ((MainActivity) requireActivity()).mCastSession.sendMessage("urn:x-cast:xbplay-toggleInfoBar", "{}");
                    }
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        });

        mMediaRouteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // if we are already casting ignore alert
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_tvcast");

            }
        });

        remoteBinding.helpButton.setVisibility(View.VISIBLE);
        remoteBinding.helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupWebview popup = new PopupWebview(getActivity());
                popup.showPopup(view, PopupWebview.TV_CAST_POPUP);
            }
        });

        return root;
    }

    public boolean isCastConnected() {
        if ((((MainActivity) requireActivity()).mCastSession != null && ((MainActivity) requireActivity()).mCastSession.isConnected())){
            return true;
        }
        Toast.makeText(getActivity(), "Not casting. Click the cast button first!", Toast.LENGTH_SHORT).show();
        return false;
    }
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        remoteBinding = null;
    }
}