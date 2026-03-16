package com.studio08.xbgamestream.Helpers;

import android.content.Context;
import android.os.Bundle;

import com.google.firebase.analytics.FirebaseAnalytics;

public class FirebaseAnalyticsClient {
    private FirebaseAnalytics mFirebaseAnalytics;

    public FirebaseAnalyticsClient(FirebaseAnalytics mFirebaseAnalytics) {
        this.mFirebaseAnalytics = mFirebaseAnalytics;
    }

    public void logButtonClickEvent(String buttonName){
        try {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, buttonName);
            mFirebaseAnalytics.logEvent(buttonName + "_button_click", bundle);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void logFragmentCreated(String fragmentName){
        try {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, fragmentName);
            mFirebaseAnalytics.logEvent(fragmentName + "_fragment_loaded", bundle);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public void logCustomEvent(String name, String value){
        try {
            Bundle bundle = new Bundle();
            bundle.putString(FirebaseAnalytics.Param.ITEM_ID, value);
            mFirebaseAnalytics.logEvent(name + "_custom_event", bundle);
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
