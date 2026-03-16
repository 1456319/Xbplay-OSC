package com.studio08.xbgamestream.ui.androidtv;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.studio08.xbgamestream.Authenticate.LoginActivity;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.PopupWebview;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.Servers.Server;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.databinding.FragmentAndroidTvBinding;
import com.studio08.xbgamestream.databinding.FragmentMirrorcastBinding;

import java.io.IOException;

public class AndroidTvFragment extends Fragment {

    private FragmentAndroidTvBinding binding;
    ApiClient apiClient;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("androidtv");

        binding = FragmentAndroidTvBinding.inflate(inflater, container, false);
        View root = binding.getRoot();
        apiClient = new ApiClient(getContext());
        Button sendToTV = binding.sendToTvButton;
        sendToTV.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_send_android_tv");

                String tvCode = ((EditText)binding.tvCodeEditText).getText().toString();
                apiClient.doLookupTvCode(tvCode); // for Android TV
            }
        });

        Button signIn = binding.loginToAccount;
        signIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_console_connect_tv");

                if (!Helper.checkWifiConnected(getActivity().getApplicationContext())) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Connect to Wifi")
                            .setMessage("You must be connected to the same Wifi network as your console. Connect now?")
                            .setCancelable(true)
                            .setPositiveButton("Connect Wifi", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
                                }
                            })
                            .setNegativeButton("Continue Anyway", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(getContext(), LoginActivity.class);
                                    getActivity().startActivityForResult(intent, 444);
                                }
                            })
                            .show();
                } else {
                    Intent intent = new Intent(getContext(), LoginActivity.class);
                    getActivity().startActivityForResult(intent, 444);
                }

            }
        });

        binding.helpButton.setVisibility(View.VISIBLE);
        binding.helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupWebview popup = new PopupWebview(getActivity());
                popup.showPopup(view, PopupWebview.ANDROID_TV_POPUP);
            }
        });

        // handle tv code auto fill from deep link
        if(((MainActivity)getActivity()) != null && !((MainActivity)getActivity()).tvCodeUri.equals("")){
            ((EditText)binding.tvCodeEditText).setText(((MainActivity)getActivity()).tvCodeUri);
            ((MainActivity)getActivity()).tvCodeUri = "";
        }
        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            apiClient.cleanUp();
        } catch(Exception e) {}
        binding = null;
    }
}