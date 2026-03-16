package com.studio08.xbgamestream.Servers;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import com.studio08.xbgamestream.Helpers.EncryptClient;
import com.studio08.xbgamestream.Helpers.Helper;
import com.studio08.xbgamestream.Helpers.SettingsFragment;
import com.studio08.xbgamestream.Web.ApiClient;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import fi.iki.elonen.NanoHTTPD;

public class Server extends NanoHTTPD{
    Context context;
    public static int PORT = 3000;
    String gsTokenPwa = "";
    String serverIdPwa = "";

    public Server(int port, Context context){
        super(port);
        this.context = context;
    }

    public Server(int port, Context context, String gsToken, String serverId){
        super(port);
        this.context = context;
        this.gsTokenPwa = gsToken;
        this.serverIdPwa = serverId;
    }

    @Override
    public Response serve(IHTTPSession session){
        String msg = "<html><body><h1>Error Creating Local Server :(</h1></body></html>";
        try {
            return newFixedLengthResponse(readFile("play-anywhere.html"));
        } catch (IOException e) {
            e.printStackTrace();
            return newFixedLengthResponse(msg);
        }
    }

    private String readFile(String path) throws IOException {
        BufferedReader br = new BufferedReader( new InputStreamReader(context.getAssets().open(path), "UTF-8"));
        try {
            StringBuilder sb = new StringBuilder();
            String line = br.readLine();

            while (line != null) {
                line = injectUrls(line);
                sb.append(line);
                sb.append("\n");
                line = br.readLine();
            }
            return sb.toString();
        } finally {
            br.close();
        }
    }

    private String injectUrls(String content) {
        if(content.contains("STREAM_VIEW_URL")) {
            return content.replace("STREAM_VIEW_URL", ApiClient.STREAMING_URL + queryStringParams());
        } else {
            return content;
        }
    }
    private String queryStringParams() {
        // get
        EncryptClient encryptClient = new EncryptClient(context);
        String serverId = encryptClient.getValue("serverId");
        String gsToken = encryptClient.getValue("gsToken");

        // handle PWA mirrorcast
        if(!this.gsTokenPwa.equals("")){
            gsToken = this.gsTokenPwa;
        } if(!this.serverIdPwa.equals("")){
            serverId = this.serverIdPwa;
        }

        //defaulting tokens to invalid values, if they are not set then the mirrorcast view will not prompt for re-login if the user hasnt logged in at all yet
        if(serverId.equals("")) {
            serverId = "-1";
        } if(gsToken.equals("")) {
            gsToken = "-1";
        }

        SharedPreferences prefs = this.context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        String videoFit = prefs.getString("video_fit_key", "cover");
        Integer videoOffset = prefs.getInt("video_vertical_offset_key", 50);

        String emulateClient = prefs.getString("emulate_client_key", "windows");
        String controllerRefreshRate = prefs.getString("controller_refresh_key", "32");

        Boolean enableAudio = prefs.getBoolean("enable_audio_default_key", true);
        String miniGamepadSize = prefs.getString("mini_gamepad_size_key", "30");

        String maxBitrate = prefs.getString("max_bitrate_key", "");
//        String softResetOnLag = prefs.getString("soft_reset_on_lag_key", "");
//        String softResetOnInterval = prefs.getString("soft_reset_on_interval_key", "");
//        Boolean flashScreenOnSoftReset = prefs.getBoolean("flash_screen_on_soft_reset", false);
//        String clearBufferOnInterval = prefs.getString("clear_buffer_on_interval_key", "");

        String result = "?gsToken=" + gsToken + "&serverId=" + serverId + "&originId=" + Helper.getLocalIpAddress() + ":" + Server.PORT +
                "&video-fit=" + videoFit +
                "&video-vertical-offset=" + videoOffset +
                "&userAgentType=" + emulateClient +
                "&gamepadRefreshRateMs="+ controllerRefreshRate +
                "&miniGamepadSize=" + miniGamepadSize +
                "&maxBitrate=" + maxBitrate;

//                "&softResetOnLag=" + softResetOnLag +
//                "&softResetOnInterval=" + softResetOnInterval +
//                "&flashScreenOnSoftReset=" + flashScreenOnSoftReset +
//                "&clearBufferOnInterval=" + clearBufferOnInterval;

        if(!enableAudio) {
            result += "&disable-audio=true";
        }

//        // uncomment if we ever want to support xcloud in mirrocast
//        String msalToken = encryptClient.getValue("msalAccessToken");
//        String xcloudToken = encryptClient.getValue("xcloudToken");
//        if (!TextUtils.isEmpty(msalToken)) {
//            result += "&msalToken=" + msalToken;
//        }
//        if (!TextUtils.isEmpty(xcloudToken)) {
//            result += "&xcloudToken=" + xcloudToken;
//        }

        return result;
    }
}
