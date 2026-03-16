package com.studio08.xbgamestream.Helpers;

import static com.studio08.xbgamestream.Helpers.Helper.getRenderEngine;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.studio08.xbgamestream.ControllerSetup.ControllerConfigActivity;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Timers.PurchaseChecker;

import org.json.JSONArray;
import org.json.JSONObject;

public class PWASettingsFragment extends PreferenceFragmentCompat {
    public static final String PREF_FILE_NAME = "SettingsSharedPref";

    // NOTE, this is to fix bad indent. Its only looking 2 levels deep screen->cat->item wont work for
    // screen->cat->cat->item
    @Override
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        super.setPreferenceScreen(preferenceScreen);
        if (preferenceScreen != null) {
            int count = preferenceScreen.getPreferenceCount();
            // loop over all prefs including categories
            for (int i = 0; i < count; i++) {
                // set icon padding to 0
                preferenceScreen.getPreference(i).setIconSpaceReserved(false);

                // if we are on a category loop over all items
                if (preferenceScreen.getPreference(i) instanceof PreferenceGroup) {
                    int count2 = ((PreferenceGroup)preferenceScreen.getPreference(i)).getPreferenceCount();
                    for (int i2 = 0; i2 < count2; i2++) {
                        // set the category sub item padding to 0
                        ((PreferenceGroup)preferenceScreen.getPreference(i)).getPreference(i2).setIconSpaceReserved(false);
                    }
                }
            }

        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName("SettingsSharedPref");
        addPreferencesFromResource(R.xml.pwa_settings_prefs);


        try {
            findPreference("rate_app_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getActivity().getPackageName())));
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("create_physical_controller_button_mappings").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    if (!getRenderEngine(getContext()).equals("webview")) {
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Not Available")
                                .setMessage("You can't create custom controller mappings while using this render engine. If this is a feature you would like, please let me know in the discord channel or via email :)")
                                .setCancelable(true)
                                .setNegativeButton("Close", null)
                                .show();
                    } else {
                        new AlertDialog.Builder(getActivity())
                                .setTitle("Custom Controller Button Mappings")
                                .setMessage("This will begin the process to setup custom button mappings for a physical controller (connected to your device). Note, you should only have to do this for unique controllers, such as the Nintendo Switch.")
                                .setCancelable(true)
                                .setPositiveButton("Configure Now", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        startCustomButtonMap();
                                    }
                                })
                                .setNegativeButton("Exit", null)
                                .show();
                    }
                    return true;
                }
            });
        } catch (Exception e){}


        try {
            findPreference("join_discord_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    joinDiscordServer();
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("join_reddit_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    joinSubReddit();
                    return true;
                }
            });
        } catch (Exception e){}


        try {
            findPreference("use_notch_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                   Toast.makeText(getContext(), "Restart app to see changes.", Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        } catch (Exception e){}

        findPreference("clear_cache_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialog.Builder(getActivity())
                        .setTitle("Are you sure?")
                        .setMessage("This will delete all cached data")
                        .setCancelable(true)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                clearCache();
                            }
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
        });

        try {
            findPreference("pwa_use_legacy_theme_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Activating Legacy Theme")
                            .setMessage("You can revert back to the new theme at any time in the settings. Restart app to apply changes!")
                            .setCancelable(false)
                            .setPositiveButton("I Understand", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                    clearCache();
                                }
                            })
                            .show();
                    return true;
                }
            });
        } catch (Exception $e){}

        try {
            findPreference("use_audio_low_latency_mode_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Restart App")
                            .setMessage("You must restart the app to apply changes!")
                            .setCancelable(false)
                            .setPositiveButton("I Understand", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .show();
                    return true;
                }
            });
        } catch (Exception $e){}
        try {
            findPreference("use_wifi_low_latency_mode_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Restart App")
                            .setMessage("You must restart the app to apply changes!")
                            .setCancelable(false)
                            .setPositiveButton("I Understand", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .show();
                    return true;
                }
            });
        } catch (Exception $e){}

        populatePhysicalControllerMappings();
    }


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    }

private void clearCache(){
        Toast.makeText(getActivity(), "Cache Cleared", Toast.LENGTH_LONG).show();
        // Clear all the Application Cache, Web SQL Database and the HTML5 Web Storage
        WebStorage.getInstance().deleteAllData();

        // Clear all the cookies
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();

        // Clear stored tokens
        EncryptClient encryptClient = new EncryptClient(getContext());
        encryptClient.deleteAll();
    }

    public void onResume() {
        super.onResume();
        populatePhysicalControllerMappings();
    }

    private void populatePhysicalControllerMappings() {
        // get list of custom controller configs
        SharedPreferences prefs = getActivity().getSharedPreferences(PWASettingsFragment.PREF_FILE_NAME, 0);
        String allControllerConfigs = prefs.getString("physical_controller_configs", "[]");

        try {
            // load list into json array
            JSONArray allConfigs = new JSONArray(allControllerConfigs);
            int length = allConfigs.length();

            // set physical controller values
            CharSequence entries[] = new CharSequence[length + 1];
            CharSequence entryValues[] = new CharSequence[length + 1];

            // set init
            entries[0] = "default";
            entryValues[0] = "null";

            for (int i = 0; i < length; i++) {
                JSONObject item = allConfigs.getJSONObject(i);

                // add name
                String name = item.getString("name");
                entries[i + 1] = name;
                // add values
                entryValues[i + 1] = name;
            }

            // set names and values of list pref
            ListPreference lp = (ListPreference) findPreference("physical_controller_button_mappings");
            lp.setEntries(entries);
            lp.setEntryValues(entryValues);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void joinDiscordServer() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/zxEBXxWWza")));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getActivity(), "Error opening discord link", Toast.LENGTH_SHORT).show();
        }
    }

    private void joinSubReddit() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.reddit.com/r/xbPlay/")));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getActivity(), "Error opening reddit link", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCustomButtonMap() {
        Intent intent = new Intent(getActivity(), ControllerConfigActivity.class);
        startActivity(intent);

    }
}
