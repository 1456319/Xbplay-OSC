package com.studio08.xbgamestream.Helpers;

import static com.studio08.xbgamestream.Helpers.Helper.getRenderEngine;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;

import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.studio08.xbgamestream.Authenticate.LoginActivity;
import com.studio08.xbgamestream.ControllerSetup.ControllerConfigActivity;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.ScreenCastActivity;
import com.studio08.xbgamestream.Timers.PurchaseChecker;

import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsFragment extends PreferenceFragmentCompat {
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

        // below line is used to add preference
        // fragment from our xml folder.
        addPreferencesFromResource(R.xml.settings_prefs);

        RewardedAdLoader rewardedAd = new RewardedAdLoader(getActivity());

        findPreference("clear_cache_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                new AlertDialog.Builder(getActivity())
                        .setTitle("Are you sure?")
                        .setMessage("This will delete all cached data including tokens and controllers")
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

        // Tutorial is loaded from the server; that flow also presents third-party licenses and attributions.
        try {
            findPreference("show_tutorial_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(getActivity(), TutorialActivity.class);
                    intent.putExtra("show_full", true);
                    startActivity(intent);
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("rate_app_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + getActivity().getPackageName())));
                    return true;
                }
            });
        } catch (Exception e){}

//        try {
//            findPreference("fix_missing_ice_candidates_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
//                @Override
//                public boolean onPreferenceClick(Preference preference) {
//                    new AlertDialog.Builder(getActivity())
//                            .setTitle("Fix streaming issues")
//                            .setMessage("WHAT IS THIS ERROR:\nAndroid 11 itself has a bug resulting in some devices being unable to discover ice candidates (something that is required to stream from an Xbox).\n\n" +
//                                    "HOW CAN IT BE FIXED:\nGoogle has already fixed this issue in Android 12, so updating to Android 12 should fix this. However, many devices cant be updated to Android 12 at this time.\n\n" +
//                                    "As a workaround, I have complied this application with an older version Android (Android 10). If you install this version of the application the issue will be fixed.\n\n" +
//                                    "WHY NOT COMPILE WITH ANDROID 10 BY DEFAULT:\nGoogle Play will not allow any new or updated apps to be published to the play store using Android 10 (or earlier). So its not possible for me to set the compiled Android version to 10 by default.\n\n" +
//                                    "RECAP:\nGoogle Play forces me to publish this app with a version of Android that has a bug resulting in some devices being unable to stream. As a workaround you can install this app complied with Android 10 to fix the issue. I know this is not ideal, but there is nothing else I can do to fix this. Thank you for understanding :)\n\n" +
//                                    "IMPORTANT:\nAfter installing this version of the app, which will be titled 'xbPlay (legacy)', be sure to delete the original xbPlay app! Otherwise, they could compete with one another. To get future updates, you will have to reinstall the app from here again.")
//                            .setCancelable(true)
//                            .setPositiveButton("Install Now", new DialogInterface.OnClickListener() {
//                                public void onClick(DialogInterface dialog, int which) {
//                                    startApkInstall();
//                                }
//                            })
//                            .setNegativeButton("Exit", null)
//                            .show();
//                    return true;
//                }
//            });
//        } catch (Exception e){}

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
            findPreference("emulate_client_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Info")
                            .setMessage("Changing between clients might require you to restart your console and re-login to your account. If you notice that the quality doesn't change or that you can't stream 360 games, please restart your console and re-sign into your account.")
                            .setCancelable(true)
                            .setPositiveButton("I understand", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("mini_gamepad_size_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Info")
                            .setMessage("Since you are changing the size of the Mini Gamepad, any custom controller layouts that you created for the Mini Gamepoad via the 'Gamepad Builder' feature might also need to be resized. If you notice that that your custom layout no longer scales properly, open the 'Gamepad Builder' tab and rebuild the layout (for the larger Mini Gamepad size)")
                            .setCancelable(true)
                            .setPositiveButton("I understand", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
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
            findPreference("region_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Info")
                            .setMessage("You must open the consoles tab again to use a new region.")
                            .setCancelable(true)
                            .setPositiveButton("I understand", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
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

        try {
            findPreference("ask_for_help_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    sendSupportEmail();
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("forget_saved_console_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    EncryptClient encryptClient = new EncryptClient(getContext());
                    encryptClient.saveValue("rememberConsole", "");
                    Toast.makeText(getActivity(), "Console cleared. Re-login and select a new default console", Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("custom_local_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Info")
                            .setMessage("If you already started a game with a different language within the past 5 minutes, you must restart the game for the new language to be applied. Restart the game by starting any other xCloud game.")
                            .setCancelable(true)
                            .setPositiveButton("I understand", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .show();
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("orientation_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Toast.makeText(getContext(), "Restart app to see changes.", Toast.LENGTH_LONG).show();
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("unlock_full_version_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    rewardedAd.buyAdRemoval();
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("unlock_full_version_main_app_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    rewardedAd.buyAdRemoval();
                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("restore_purchase_main_app_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {

                    // reset cache timer
                    SharedPreferences freqPrefs = getContext().getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
                    SharedPreferences.Editor editor = freqPrefs.edit();
                    editor.putLong("nextMakeGetTokenRequest", 0);
                    editor.apply();

                    // reset token value
                    EncryptClient encryptClient = new EncryptClient(getContext());
                    encryptClient.saveValue("purchaseToken", "0");

                    rewardedAd.queryPurchases();

                    String gsToken = encryptClient.getValue("gsToken");

                    if (TextUtils.isEmpty(gsToken)){
                        Toast.makeText(getActivity(), "Not logged in. You must be logged in for cross restore to work.", Toast.LENGTH_LONG).show();
                    }

                    return true;
                }
            });
        } catch (Exception e){}

        try {
            findPreference("pwa_use_legacy_theme_key").setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    new AlertDialog.Builder(getActivity())
                            .setTitle("Change Theme")
                            .setMessage("Restart app to apply changes.")
                            .setCancelable(true)
                            .setPositiveButton("Close App", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    clearCache();
                                    dialog.dismiss();
                                    getActivity().finishAffinity();
                                }
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                    return true;
                }
            });
        } catch (Exception e){}

        populatePhysicalControllerMappings();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

    }

    public void onResume() {
        super.onResume();
        populatePhysicalControllerMappings();
    }

    private void populatePhysicalControllerMappings() {
        // get list of custom controller configs
        SharedPreferences prefs = getActivity().getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
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

    private void sendSupportEmail() {
        Intent i = new Intent(Intent.ACTION_SENDTO);
        i.setData(Uri.parse("mailto:"));
        i.putExtra(Intent.EXTRA_EMAIL  , new String[]{"alexwarddev1230@gmail.com"});
        i.putExtra(Intent.EXTRA_SUBJECT, "xbPlay Support");
        String emailText = "Please type a detailed description of the issue you are facing below:\n";

        // attach login logs if we need to
        SharedPreferences prefs = this.getActivity().getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        boolean captureLoginLogs = prefs.getBoolean("capture_debug_logs_key", false);
        boolean captureGameLogs = prefs.getBoolean("capture_debug_logs_gameplay_key", false);

        // append logs
        if(captureGameLogs){
            emailText += "\n\nGamePlay Logs: " + prefs.getString("gameplay_logs", "empty");
        } if(captureLoginLogs){
            emailText += "\n\nLogin Logs: " + prefs.getString("login_logs", "empty");
        }

        // add logs to intent
        i.putExtra(Intent.EXTRA_TEXT, emailText);

        try {
            startActivity(Intent.createChooser(i, "Send support mail..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(getActivity(), "There are no email clients installed.", Toast.LENGTH_SHORT).show();
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
// DEPRECIATED
//    private void startApkInstall(){
//        InstallAppFromUrl updateApp = new InstallAppFromUrl();
//        updateApp.setContext(getActivity());
//        updateApp.execute("https://d1o4538xtdh4nm9zq.cloudfront.net/app-legacySdkVersion-release.apk");
//    }
    private void clearCache() {
        Toast.makeText(getActivity(), "Clearing cache", Toast.LENGTH_LONG).show();
        // Clear all the Application Cache, Web SQL Database and the HTML5 Web Storage
        WebStorage.getInstance().deleteAllData();

        // Clear all the cookies
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();

        // Clear stored tokens
        EncryptClient encryptClient = new EncryptClient(getContext());
        encryptClient.saveValue("serverId", "");
        encryptClient.saveValue("gsToken", "");
        encryptClient.saveValue("streamCookieRaw", "");
        encryptClient.saveValue("xcloudToken", "");
        encryptClient.saveValue("xcloudRegion", "");
        encryptClient.saveValue("msalAccessToken", "");
        encryptClient.saveValue("msalRefreshToken", "");
        encryptClient.saveValue("clientId", "");
        encryptClient.saveValue("consoles", "");
        encryptClient.saveValue("purchaseToken", "0");

        try {
            SharedPreferences freqPrefs = getContext().getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            SharedPreferences.Editor editor = freqPrefs.edit();
            editor.putLong("nextMakeGetTokenRequest", 0);
            editor.apply();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
