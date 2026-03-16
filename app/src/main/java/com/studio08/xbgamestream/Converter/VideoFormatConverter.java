package com.studio08.xbgamestream.Converter;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import com.studio08.xbgamestream.Helpers.SettingsFragment;

import java.io.File;

// ffmpeg is retired now

public class VideoFormatConverter {
    public interface VideoConvertEvents {
        void onVideoConvertSuccess(String path);
        void onVideoConvertFailed(String message);
    }

    String inputPath;
    String convertQualityValue;
    String resolution;
    Context context;

    public String getConvertQualityValue(){
        return convertQualityValue;
    }
    public String getResolution(){
        return resolution;
    }
    // listeners
    private VideoConvertEvents mListener;
    public void setCustomListener(VideoConvertEvents listener){
        mListener = listener;
    }

    public VideoFormatConverter(String inputPath, Context context){
        this.inputPath = inputPath;
        this.context = context;
    }

    public void getFFMpegMediaInput() {
        Log.w("VideoFormatConverter", "getFFMpegMediaInput: FFmpeg is disabled");
    }

    public void cancel() {
    }

    private String getOutputFileName() {
        File file = new File(inputPath);
        String fileShortName = file.getName() + "_converted";
        fileShortName = fileShortName.replace(".", "");

        // output file to downloads folder
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath() + File.separator + fileShortName + ".mp4";
    }

    private void setSettingValues() {
        SharedPreferences prefs = context.getSharedPreferences(SettingsFragment.PREF_FILE_NAME, 0);
        convertQualityValue = prefs.getString("video_convert_quality_key", "29");
        resolution = prefs.getString("video_convert_size_key", "1280x720");
    }

    public void runFFMpegConvert() {
        if (mListener != null) {
            mListener.onVideoConvertFailed("This file format is not supported.");
        }
        return;
    }
}
