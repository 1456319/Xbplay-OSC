package com.studio08.xbgamestream.ui.mirrorcast;

import android.annotation.SuppressLint;
import android.content.pm.ActivityInfo;
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
import com.studio08.xbgamestream.Helpers.PopupWebview;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.Servers.Server;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.databinding.FragmentMirrorcastBinding;

import java.io.IOException;

public class MirrorCastFragment extends Fragment {

    private FragmentMirrorcastBinding binding;
    ApiClient streamingClient;

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("mirrorcast");

        binding = FragmentMirrorcastBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        final TextView textView = binding.textMirrorcastFooter;
        if (!TextUtils.isEmpty(((MainActivity) requireActivity()).mirrorcastText))  {
            textView.setText( ((MainActivity) requireActivity()).mirrorcastText);
        }

        Button startMirrorCast = binding.mirrorcastButton;
        startMirrorCast.setVisibility(View.VISIBLE);

        startMirrorCast.setOnClickListener(new View.OnClickListener() {
            @SuppressLint("SetTextI18n")
            @Override
            public void onClick(View v) {
                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_mirrorcast");


                if(((MainActivity) requireActivity()).server != null) {
                    Toast.makeText(getActivity(), "Server already running! Restarting!", Toast.LENGTH_SHORT).show();

                    try{
                        ((MainActivity) requireActivity()).server.stop();
                        ((MainActivity) requireActivity()).server = null;
                    } catch (Exception e) {}
                }
                ((MainActivity) requireActivity()).server = new Server(Server.PORT, getActivity());
                try {
                    ((MainActivity) requireActivity()).server.start();
                    Toast.makeText(getActivity(), "MirrorCast Server Running!", Toast.LENGTH_SHORT).show();
                } catch(IOException e){
                    Toast.makeText(getActivity(), "Error creating MirrorCast server!" + e.getMessage(), Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }

                ((MainActivity) requireActivity()).mirrorcastText = "MirrorCast Running! Open a web-browser on any device connected to your local wifi and enter the following URL\n\n"+
                        Helper.getLocalIpAddress() + ":" +
                        Server.PORT;

                binding.textMirrorcastFooter.setText(((MainActivity) requireActivity()).mirrorcastText);
            }
        });

        binding.helpButton.setVisibility(View.VISIBLE);
        binding.helpButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupWebview popup = new PopupWebview(getActivity());
                popup.showPopup(view, PopupWebview.MIRRORCAST_POPUP);
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        try {
            streamingClient.cleanUp();
        } catch(Exception e) {}
        binding = null;
    }
}