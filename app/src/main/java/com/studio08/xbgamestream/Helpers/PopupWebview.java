package com.studio08.xbgamestream.Helpers;

import static android.content.Context.LAYOUT_INFLATER_SERVICE;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroupOverlay;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.R;
import com.studio08.xbgamestream.Web.ApiClient;
import com.studio08.xbgamestream.Web.StreamWebview;

public class PopupWebview {
    Context context;
    Activity activity;

    public static String GAMESTREAM_POPUP = "swipe-screens/info-popup/features_stream.html";
    public static String ANDROID_TV_POPUP = "swipe-screens/info-popup/features_androidTv.html";
    public static String BUILDER_POPUP = "swipe-screens/info-popup/features_builder.html";
    public static String CONSOLES_POPUP = "swipe-screens/info-popup/features_console.html";
    public static String FILECAST_POPUP = "swipe-screens/info-popup/features_filecast.html";
    public static String STANDALONE_GAMEPAD_POPUP = "swipe-screens/info-popup/features_gamepadController.html";
    public static String MEDIA_REMOTE_POPUP = "swipe-screens/info-popup/features_mediaRemote.html";
    public static String MIRRORCAST_POPUP = "swipe-screens/info-popup/features_mirrorcast.html";
    public static String TV_CAST_POPUP = "swipe-screens/info-popup/features_tvCast.html";
    public static String XCLOUD_POPUP = "swipe-screens/info-popup/features_xcloud.html";
    public static String KEYBOARD_WARNING_POPUP = "swipe-screens/info-popup/features_keyboardWarning.html";
    public static String VOICE_REMOTE_POPUP = "swipe-screens/info-popup/features_voiceRemote.html";


    public PopupWebview(Activity activity) {
        this.activity = activity;
        this.context = activity.getApplicationContext();
    }

    public void showPopup(View view, String type) {
        Toast.makeText(activity, "Swipe to view details about this feature", Toast.LENGTH_LONG).show();

        // inflate the layout of the popup window
        LayoutInflater inflater = (LayoutInflater) (context).getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_view, null);

        boolean focusable = true; // lets taps outside the popup also dismiss it
        int width = (int)((context).getResources().getDisplayMetrics().widthPixels*0.90);
        int height = (int)((context).getResources().getDisplayMetrics().heightPixels*0.75);
        final PopupWindow popupWindow = new PopupWindow(popupView, width, height, focusable);

        // show the popup window
        // which view you pass in doesn't matter, it is only used for the window tolken
        popupWindow.showAtLocation(view, Gravity.CENTER, 0, 0);

//        // dismiss the popup window when touched
//        popupView.findViewById(R.id.close_popup).setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                popupWindow.dismiss();
//            }
//        });

        // clear dim on close
        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                ViewGroup root = (ViewGroup) activity.getWindow().getDecorView().getRootView();
                clearDim(root);
            }
        });

        // load swiper page
        StreamWebview streamView = popupView.findViewById(R.id.webview1);
        streamView.setBackgroundColor(Color.TRANSPARENT);
        streamView.init();
        streamView.loadUrl(buildUrl(type));

        // dim background
        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView().getRootView();
        applyDim(root, .8f);
    }

    public static void applyDim(@NonNull ViewGroup parent, float dimAmount){
        Drawable dim = new ColorDrawable(Color.BLACK);
        dim.setBounds(0, 0, parent.getWidth(), parent.getHeight());
        dim.setAlpha((int) (255 * dimAmount));

        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.add(dim);
    }

    public static void clearDim(@NonNull ViewGroup parent) {
        ViewGroupOverlay overlay = parent.getOverlay();
        overlay.clear();
    }

    private String buildUrl(String url) {
        return ApiClient.USE_DEV ? ApiClient.BASE_URL_DEV + url : ApiClient.BASE_URL_PROD + url;
    }
}
