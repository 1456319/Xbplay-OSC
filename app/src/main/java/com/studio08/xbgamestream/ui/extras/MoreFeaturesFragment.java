package com.studio08.xbgamestream.ui.extras;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.TutorialActivity;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.Servers.Server;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.databinding.FragmentMirrorcastBinding;
import com.studio08.xbgamestream.databinding.FragmentMoreFeaturesBinding;

import java.io.IOException;

public class MoreFeaturesFragment extends Fragment {

    private FragmentMoreFeaturesBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("more_features");

        binding = FragmentMoreFeaturesBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        binding.downloadFeaturesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_more_features");
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.studio08.xbgamestream")));
            }
        });

        binding.viewFeaturesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getActivity(), TutorialActivity.class);
                intent.putExtra("show_full", true);
                startActivity(intent);
            }
        });

        return root;
    }
}