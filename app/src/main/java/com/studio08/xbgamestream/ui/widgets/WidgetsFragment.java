package com.studio08.xbgamestream.ui.widgets;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.KeyboardMovementCalculations;
import com.studio08.xbgamestream.Helpers.PopupWebview;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Servers.Server;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;
import com.studio08.xbgamestream.Widgets.PowerWidgetProvider;
import com.studio08.xbgamestream.Widgets.RemoteWidgetProvider;
import com.studio08.xbgamestream.databinding.FragmentMirrorcastBinding;
import com.studio08.xbgamestream.databinding.FragmentWidgetsBinding;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

public class WidgetsFragment extends Fragment {

    private FragmentWidgetsBinding binding;
    ApiClient streamingClient;

    ApiClient.StreamingClientListener buttonPressListener = new ApiClient.StreamingClientListener() {
        @Override
        public void onReLoginDetected() {}

        @Override
        public void onCloseScreenDetected() {}

        @Override
        public void pressButtonWifiRemote(String type) {
        }
        @Override
        public void setOrientationValue(String value) {
        }

        @Override
        public void vibrate() {}
        @Override
        public void genericMessage(String type, String msg) {
            Log.e("HERE", "caught generic message");
            try {
                boolean showedPopup = false;
                if (type.equals("addPowerWidget")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        AppWidgetManager mAppWidgetManager = (getActivity()).getSystemService(AppWidgetManager.class);
                        if (mAppWidgetManager.isRequestPinAppWidgetSupported()) {
                            ComponentName myProvider = new ComponentName(getActivity(), PowerWidgetProvider.class);
                            Bundle b = new Bundle();
                            Intent pinnedWidgetCallbackIntent = new Intent(getActivity(), PowerWidgetProvider.class);
                            PendingIntent successCallback =
                                    PendingIntent.getBroadcast(getActivity(), 0, pinnedWidgetCallbackIntent, PendingIntent.FLAG_IMMUTABLE);

                            mAppWidgetManager.requestPinAppWidget(myProvider, b, successCallback);
                            showedPopup = true;
                        }
                    }
                } else if (type.equals("addRemoteWidget")) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        AppWidgetManager mAppWidgetManager = (getActivity()).getSystemService(AppWidgetManager.class);
                        if (mAppWidgetManager.isRequestPinAppWidgetSupported()) {
                            ComponentName myProvider = new ComponentName(getActivity(), RemoteWidgetProvider.class);
                            Bundle b = new Bundle();
                            Intent pinnedWidgetCallbackIntent = new Intent(getActivity(), RemoteWidgetProvider.class);
                            PendingIntent successCallback =
                                    PendingIntent.getBroadcast(getActivity(), 0, pinnedWidgetCallbackIntent, PendingIntent.FLAG_IMMUTABLE);

                            mAppWidgetManager.requestPinAppWidget(myProvider, b, successCallback);
                            showedPopup = true;
                        }
                    }
                }

                if (!showedPopup) {
                    String appname = getResources().getString(R.string.app_name);
                    Toast.makeText(getContext(), "Long press on your home-screen and select widgets->" + appname + " to add the widget", Toast.LENGTH_SHORT).show();
                }
            } catch (Error e) {
                e.printStackTrace();
            }
        }
    };

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ((MainActivity) getActivity()).setOrientationPortrait();

        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("widget");

        binding = FragmentWidgetsBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        StreamWebview streamView = binding.webview1;
        streamView.setBackgroundColor(Color.TRANSPARENT);
        streamView.init();

        streamingClient = new ApiClient(getActivity(), binding.webview1);
        streamingClient.setCustomObjectListener(buttonPressListener);
        streamingClient.doWidgetTutorial();
        streamView.requestFocus();

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