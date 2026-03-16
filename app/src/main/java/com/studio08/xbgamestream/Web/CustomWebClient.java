package com.studio08.xbgamestream.Web;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.studio08.xbgamestream.Helpers.PWAWebviewHandler;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.PWAMainMenuActivity;
import com.studio08.xbgamestream.ScreenCastActivity;

public class CustomWebClient extends WebViewClient {

    private AlertDialog dialog;
    private boolean showLoadingDialog;

    public CustomWebClient(boolean showLoadingDialog) {
        this.showLoadingDialog = showLoadingDialog;
    }

    private void hideDialog() {
        try {
            dialog.dismiss();
        } catch (Exception e) {}
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
        super.onPageStarted(view, url, favicon);
        try {
            setProgressDialog(view.getContext());
        } catch (Exception e) {}
    }

    @Override
    public void onPageFinished(WebView view, String url) {
        super.onPageFinished(view, url);
        try {
            hideDialog();
        } catch (Exception e) {}
    }
    @Override
    public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error){
        hideDialog();
        Log.e("CustomWV", error.getDescription() + " - " + error.getErrorCode() + " - " + request.getUrl().getPath());

        if(request.getUrl().getPath().contains("/image/")){ // ignore failed image loads
            return;
        }

        Toast.makeText(view.getContext(), "Error - Check Internet. " + error.getDescription(), Toast.LENGTH_SHORT).show();
        boolean useLegacyTheme = false;

        try {
            SharedPreferences prefs = view.getContext().getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
            useLegacyTheme = prefs.getBoolean("pwa_use_legacy_theme_key", false);
        } catch (Exception e){
            e.printStackTrace();
        }

        // if we cant load the main pages, redirect back to main menu
        if(!useLegacyTheme && request.getUrl() != null && (
                request.getUrl().getPath().contains("android_stream")
                || request.getUrl().getPath().contains("title_picker")
                || request.getUrl().getPath().contains("controller_builder")
        )){
            view.loadUrl(PWAWebviewHandler.PWA_MAIN_MENU);
        } else if(request.getUrl() != null && request.getUrl().getPath().contains("pwa/main.html")){
            view.loadUrl("file:///android_asset/warning-screen.html");
        }
    }

    public void cleanup(){
        hideDialog();
    }

    public void setProgressDialog(Context context) {
        if(!showLoadingDialog){
            return;
        }
        int llPadding = 10;
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setPadding(llPadding, llPadding, llPadding, llPadding);
        ll.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llParam = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        ll.setLayoutParams(llParam);

        ProgressBar progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        progressBar.setPadding(0, 0, 0, 0);
        progressBar.setLayoutParams(llParam);
        progressBar.setVisibility(View.VISIBLE);

        llParam = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llParam.gravity = Gravity.CENTER;
        TextView tvText = new TextView(context);
        tvText.setText("Please Wait...");
        tvText.setTextColor(Color.parseColor("#FFFFFF"));
        tvText.setTextSize(20);
        tvText.setLayoutParams(llParam);

        ll.addView(progressBar);
        //ll.addView(tvText);

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setCancelable(true);
        builder.setView(ll);

        hideDialog();
        this.dialog = builder.create();
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
            layoutParams.copyFrom(dialog.getWindow().getAttributes());
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            dialog.getWindow().setAttributes(layoutParams);
        }
    }
}