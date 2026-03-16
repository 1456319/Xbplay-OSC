package com.studio08.xbgamestream.Helpers;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class EncryptClient {
    private String masterKeyAlias = null;
    private SharedPreferences sharedPreference = null;
    private Context context = null;

    public EncryptClient(Context context) {
        this.context = context;
        this.init();
    }

    public String getValue(String name) {
        if (this.sharedPreference == null){
            return "";
        }
        return this.sharedPreference.getString(name, "");
    }

    public void saveValue(String name, String value) {
        this.sharedPreference
                .edit()
                .putString(name, value)
                .apply();
    }

    public void deleteValue(String name) {
        this.sharedPreference
                .edit()
                .remove(name)
                .apply();
    }

    public void deleteAll() {
        if (this.sharedPreference != null) {
            this.sharedPreference
                .edit()
                .clear()
                .commit();
        }
    }

    public void saveJSONObject(String name, JSONObject jsonObject) {
        try {
            String jsonString = jsonObject.toString();
            saveValue(name, jsonString);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public JSONObject getJSONObject(String name) {
        String jsonString = getValue(name);
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        try {
            return new JSONObject(jsonString);
        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }
    }

    private boolean init() {
        try {
            this.masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            this.sharedPreference = EncryptedSharedPreferences.create(
                    "secret_shared_prefs",
                    this.masterKeyAlias,
                    this.context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
        } catch (Exception e){
            return false;
        }
        return true;
    }
}
