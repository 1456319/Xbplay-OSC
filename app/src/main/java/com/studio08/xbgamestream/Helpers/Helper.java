package com.studio08.xbgamestream.Helpers;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.play.core.appupdate.AppUpdateInfo;
import com.google.android.play.core.appupdate.AppUpdateManager;
import com.google.android.play.core.appupdate.AppUpdateManagerFactory;
import com.google.android.play.core.install.model.AppUpdateType;
import com.google.android.play.core.install.model.UpdateAvailability;
import com.google.android.play.core.review.ReviewException;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;
import com.google.android.play.core.review.model.ReviewErrorCode;
import com.studio08.xbgamestream.ControllerSetup.ControllerConfigActivity;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.PWAMainMenuActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

import constants.SystemInputButtonMappings;

public class Helper {

    public static void addShortcutToHomeScreen(Context context, String titleId, String titleName, String iconUrl, String type) {
        if (ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {

            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        URL imageurl = new URL(iconUrl);
                        Bitmap bitmap = BitmapFactory.decodeStream(imageurl.openConnection().getInputStream());
                        IconCompat icon = IconCompat.createWithBitmap(bitmap);

                        String stringAction = "xcloudstart";
                        if(type.equals("xhome")){
                            stringAction = "xhomestart";
                        }

                        Intent intent = new Intent(context, PWAMainMenuActivity.class)
                                .setAction(stringAction)  // Set action
                                .putExtra("titleId", titleId)  // Add extras
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);  // Ensure the activity is restarted or brought to the foreground properly

                        ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context, titleId)
                                .setIntent(intent)
                                .setShortLabel(titleName)
                                .setLongLabel(titleName)
                                .setIcon(icon).build();
                        ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }).start();

        } else {
            Toast.makeText(context, "cant make shortcut", Toast.LENGTH_SHORT).show();
        }
    }

    public static void checkIfUpdateAvailable(Context context){
        try {
            AppUpdateManager appUpdateManager = AppUpdateManagerFactory.create(context);

            // Returns an intent object that you use to check for an update.
            Task<AppUpdateInfo> appUpdateInfoTask = appUpdateManager.getAppUpdateInfo();

            // Checks that the platform will allow the specified type of update.
            appUpdateInfoTask.addOnSuccessListener(appUpdateInfo -> {
                if (appUpdateInfo.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                    if (context instanceof Activity && !((Activity) context).isFinishing()) {
                        new AlertDialog.Builder(context)
                                .setTitle("Update Available")
                                .setMessage("Please update this app to the latest version and restart it. If you don't, its possible some features might not work.")
                                .setCancelable(true)
                                .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + context.getPackageName())));
                                    }
                                })
                                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int which) {
                                        Toast.makeText(context, "Some features might not work. You have been warned :)", Toast.LENGTH_LONG).show();
                                    }
                                })
                                .show();
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] convertStringButtonToByteArray(String inputButton){
        byte[] result = null;

        try {
            Field[] fields = SystemInputButtonMappings.class.getFields();
            for (int i = 0; i < fields.length; i++) {
                String buttonStringName = fields[i].getName().toLowerCase();

                if (buttonStringName.equals(inputButton.toLowerCase())) {
                    result = (byte[]) fields[i].get(result);
                }
            }
        } catch (Exception e) {
            //Toast.makeText(getContext(), "Unknown button pressed: " + inputButton, Toast.LENGTH_SHORT).show();
        }
        if(result == null){
            //Toast.makeText(getContext(), "Unknown button pressed:" + inputButton, Toast.LENGTH_SHORT).show();
        }
        return result;
    }

    public static String formatTime(long millisec) {
        int seconds = (int) (millisec / 1000);
        int hours = seconds / (60 * 60);
        seconds %= (60 * 60);
        int minutes = seconds / 60;
        seconds %= 60;

        String time;
        if (hours > 0) {
            time = String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        else {
            time = String.format(Locale.US, "%d:%02d", minutes, seconds);
        }

        return time;
    }

    public static boolean checkWifiConnected(Context context) {
        boolean wifiAvailable = false;
        ConnectivityManager conManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo[] networkInfo = conManager.getAllNetworkInfo();
        for (NetworkInfo netInfo : networkInfo) {
            if (netInfo.getTypeName().equalsIgnoreCase("WIFI"))
                if (netInfo.isConnected())
                    wifiAvailable = true;
        }
        return wifiAvailable;
    }

    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces(); en.hasMoreElements();) {
                NetworkInterface intf = en.nextElement();
                for (Enumeration<InetAddress> enumIpAddr = intf.getInetAddresses(); enumIpAddr.hasMoreElements();) {
                    InetAddress inetAddress = enumIpAddr.nextElement();
                    if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
                        return inetAddress.getHostAddress();
                    }
                }
            }
        } catch (SocketException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static JSONObject getActiveCustomPhysicalGamepadMappings(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);

            // get the name of the active controller
            String activeControllerName = prefs.getString("physical_controller_button_mappings", null);
            if (activeControllerName == null || activeControllerName.equals("null")) {
                return null;
            }

            // get list of custom controller configs
            String allControllerConfigs = prefs.getString("physical_controller_configs", "[]");

            // load list into json array
            JSONArray allConfigs = new JSONArray(allControllerConfigs);
            int length = allConfigs.length();

            String data = null;
            for (int i = 0; i < length; i++) {
                JSONObject item = allConfigs.getJSONObject(i);

                // add values
                if(item.getString("name").equals(activeControllerName)) {
                    data = item.getString("data");
                }
            }

            Toast.makeText(context, "Loaded Custom Controller Config: " + activeControllerName, Toast.LENGTH_LONG).show();

            return new JSONObject(data);

        } catch (Exception e) {
            Toast.makeText(context, "Error loading custom controller" + e.getMessage(), Toast.LENGTH_LONG).show();
        }
        return null;
    }

    public static void vibrate(Context context) {
        try {
            SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            boolean shouldVibrate = prefs.getBoolean("vibrate_key", true);

            if(!shouldVibrate) {
                Log.w("HERE", "ignoring vibrate due to disabled in settings");
                return;
            }
            // Get instance of Vibrator from current Context
            Vibrator v = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);

            if (v.hasVibrator()) {
                v.vibrate(40);
            } else {
                Log.w("Can Vibrate", "NO");
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static void hideKeyboard(Activity activity) {
        try {
            InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
            //Find the currently focused view, so we can grab the correct window token from it.
            View view = activity.getCurrentFocus();
            //If no view currently has focus, create a new one, just so we can grab a window token from it
            if (view == null) {
                view = new View(activity);
            }
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public static String getRenderEngine(Context context){
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        String rawName = prefs.getString("render_engine_key", "empty");
        Log.e("RenderEngine", "Current render engine: " + rawName);

        // not set, see if we are on a device that we know should use geckoview
        if(rawName.equals("empty")){
            String deviceName = getDeviceName();
            Log.e("DeviceName", "Device name: " + deviceName);

            if (deviceName.contains("Odin") && !deviceName.contains("Lite")){ // Handle "AYN_Odin_M2", "AYN_Odin", not Lite
                Log.e("RenderEngine", "Using geckoview");
                //Toast.makeText(context, "Using GeckoView render engine on Odin device: " + deviceName, Toast.LENGTH_SHORT).show();
                return "geckoview";
            } else if (deviceName.equals("Amazon_AFTKA")) {
                Log.e("RenderEngine", "Using geckoview");
                //Toast.makeText(context, "Using GeckoView render engine on FireStick 4k Max device: " + deviceName, Toast.LENGTH_SHORT).show();
                return "geckoview";
            } else {
                Log.e("RenderEngine", "Using default webview");
                return "webview";
            }
        } else {
            return rawName;
        }
    }

    // we send the actual value to webview but we multiply by 10 here for device vibrator otherwise too soft
    public static double getRumbleIntensity(Context context){
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        int intensity = prefs.getInt("rumble_intensity_key", 1);
        if (intensity != 0){
            return 10 * intensity;
        } else { // 0 is handled in the webview
            return 8;
        }
    }

    public static String getDeviceName() {
        try {
            String manufacturer = Build.MANUFACTURER;
            String model = Build.MODEL;
            if (model.startsWith(manufacturer)) {
                return capitalize(model);
            } else {
                return capitalize(manufacturer) + "_" + model;
            }
        } catch (Exception e){
            return "";
        }
    }

    private static String capitalize(String s) {
        if (s == null || s.length() == 0) {
            return "";
        }
        char first = s.charAt(0);
        if (Character.isUpperCase(first)) {
            return s;
        } else {
            return Character.toUpperCase(first) + s.substring(1);
        }
    }

    public static boolean checkIfAlreadyHavePermission(String perm, Context ctx) {
        int result = ContextCompat.checkSelfPermission(ctx, perm);
        Log.e("PERM", "Permission: " + result);
        if (result == PackageManager.PERMISSION_GRANTED) {
            return true;
        } else {
            return false;
        }
    }

    public static void requestForSpecificPermission(String [] perms, Context ctx) {
        ActivityCompat.requestPermissions((Activity) ctx, perms, 101);
    }

    public static String xorDecode(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        String[] parts = str.split("\\?", 2);
        String input = parts[0];
        String search = parts.length > 1 ? parts[1] : "";

        StringBuilder result = new StringBuilder();
        String decodedInput;

        try {
            decodedInput = java.net.URLDecoder.decode(input, "UTF-8");
        } catch (Exception e) {
            e.printStackTrace();
            return str;
        }

        for (int i = 0; i < decodedInput.length(); i++) {
            char c = decodedInput.charAt(i);
            if (i % 2 == 1) {
                result.append((char) (c ^ 2));
            } else {
                result.append(c);
            }
        }

        if (!search.isEmpty()) {
            result.append("?").append(search);
        }

        return result.toString();
    }

    public static void showRatingApiMaybe(Activity activity) {
        try {
            // only ask to rate 20% of the time
            Random random = new Random();
            int chance = random.nextInt(100); // 0 to 99
            if (chance < 80){
                return;
            }

            //  ReviewManager manager = new FakeReviewManager(MainActivity.this);
            ReviewManager manager = ReviewManagerFactory.create(activity);
            Task<ReviewInfo> request = manager.requestReviewFlow();
            request.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // We can get the ReviewInfo object
                    ReviewInfo reviewInfo = task.getResult();

                    Task<Void> flow = manager.launchReviewFlow(activity, reviewInfo);
                    flow.addOnCompleteListener(task2 -> {
                        // The flow has finished. The API does not indicate whether the user
                        // reviewed or not, or even whether the review dialog was shown. Thus, no
                        // matter the result, we continue our app flow.
                        Log.d("MA", "Finished review flow");
                    });
                } else {
                    // There was some problem, log or handle the error code.
                    Log.e("MA", "Review flow error");
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
