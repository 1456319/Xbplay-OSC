package com.studio08.xbgamestream.Cast;

import android.content.Context;

import com.google.android.gms.cast.framework.CastOptions;
import com.google.android.gms.cast.framework.OptionsProvider;
import com.google.android.gms.cast.framework.SessionProvider;
import com.google.android.gms.cast.framework.media.CastMediaOptions;
import com.google.android.gms.cast.framework.media.MediaIntentReceiver;
import com.google.android.gms.cast.framework.media.NotificationOptions;
import com.studio08.xbgamestream.BuildConfig;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.Web.ApiClient;

import java.util.ArrayList;
import java.util.List;

public class CastOptionsProvider implements OptionsProvider {

    @Override
    public CastOptions getCastOptions(Context context) {
        List<String> buttonActions = new ArrayList<>();
        buttonActions.add(MediaIntentReceiver.ACTION_STOP_CASTING);
        int[] compatButtonActionsIndices = new int[]{0};

        NotificationOptions notificationOptions = new NotificationOptions.Builder()
                .setActions(buttonActions, compatButtonActionsIndices)
                .setTargetActivityClassName(MyExpandedControls.class.getName())
                .build();

        CastMediaOptions mediaOptions = new CastMediaOptions.Builder()
                .setNotificationOptions(notificationOptions)
                //.setMediaIntentReceiverClassName(MyMediaIntentReceiver.class.getName())
                .build();

        String castApplicationId = "3E940AFA"; // PROD
        if(BuildConfig.BUILD_TYPE.equals("debug") && ApiClient.USE_DEV){
            castApplicationId = "30DD2B6B"; // DEV
        }
        CastOptions castOptions = new CastOptions.Builder()
                .setCastMediaOptions(mediaOptions)
                .setReceiverApplicationId(castApplicationId)
                .build();
        return castOptions;
    }
    @Override
    public List<SessionProvider> getAdditionalSessionProviders(Context context) {
        return null;
    }
}