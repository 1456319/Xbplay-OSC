package com.studio08.xbgamestream.Helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import com.google.androidbrowserhelper.trusted.LauncherActivity;

public class TWAClient {
    Context mContext;
    String config;

    public TWAClient(Context ctx, String config){
        this.mContext = ctx;
        this.config = config;
        Log.e("TWACLIENT", "Started with config " + config);
    }

    public void launchTWSA(String url){
        String finalURL = url;
        if(url.contains("?")){
            finalURL += "&";
        } else {
            finalURL += "?";
        }
        finalURL += "setConfig=" + config;
        Log.e("TWACLIENT", "Launched with url: " + finalURL);

        Intent intent = new Intent(mContext, LauncherActivity.class);
        intent.setData(Uri.parse(finalURL));
        mContext.startActivity(intent);
    }

    public boolean getShouldUseTWA(){
        return Helper.getRenderEngine(mContext).equals("chrome");
    }
}
