package com.studio08.xbgamestream.Helpers;

import android.net.Uri;
import android.util.Log;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.fragment.app.Fragment;
import androidx.appcompat.app.AppCompatActivity;
import androidx.activity.result.PickVisualMediaRequest;
import java.util.List;

public class MediaPickerHelper {
    private ActivityResultLauncher<String[]> pickAudio;

    public interface MediaPickerCallback {
        void onMediaPicked(List<Uri> uris);
    }

    private ActivityResultLauncher<PickVisualMediaRequest> pickMedia;
    private MediaPickerCallback listener;

    // Constructor for Activity
    public MediaPickerHelper(AppCompatActivity activity, MediaPickerCallback listener) {
        this.listener = listener;
        registerVideoPicker(activity);
        registerAudioPicker(activity);

    }

//    // Constructor for Fragment
//    public MediaPickerHelper(Fragment fragment, MediaPickerCallback callback) {
//        this.listener = callback;
//        registerMediaPicker(fragment);
//    }

    // Register the media picker for Activity
    private void registerVideoPicker(AppCompatActivity activity) {
        pickMedia = activity.registerForActivityResult(
                new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
                    if (!uris.isEmpty()) {
                        Log.d("VideoPicker", "Number of items selected: " + uris.size());
                        // Pass the URIs to the callback
                        listener.onMediaPicked(uris);
                    } else {
                        Log.d("VideoPicker", "No media selected");
                    }
                });
    }

    // Register the audio picker for Activity
    private void registerAudioPicker(AppCompatActivity activity) {
        pickAudio = activity.registerForActivityResult(
                new ActivityResultContracts.OpenMultipleDocuments(),
                uris -> {
                    if (uris != null && !uris.isEmpty()) {
                        Log.d("AudioPicker", "Number of audio files selected: " + uris.size());
                        listener.onMediaPicked(uris); // Pass the audio URIs to the callback
                    } else {
                        Log.d("AudioPicker", "No audio files selected");
                    }
                }
        );
    }

    // not needed as i am not calling this from a fragment
//    // Register the media picker for Fragment
//    private void registerMediaPicker(Fragment fragment) {
//        pickMedia = fragment.registerForActivityResult(
//                new ActivityResultContracts.PickMultipleVisualMedia(5), uris -> {
//                    if (!uris.isEmpty()) {
//                        Log.d("PhotoPicker", "Number of items selected: " + uris.size());
//                        // Pass the URIs to the callback
//                        listener.onMediaPicked(uris);
//                    } else {
//                        Log.d("PhotoPicker", "No media selected");
//                    }
//                });
//    }

    // Method to launch media picker
    public void launchVideoPicker() {
        pickMedia.launch(new PickVisualMediaRequest.Builder()
                .setMediaType(ActivityResultContracts.PickVisualMedia.VideoOnly.INSTANCE)
                .build());
    }

    // Method to launch the audio picker (No permissions required)
    public void launchAudioPicker() {
        pickAudio.launch(new String[]{"audio/*"});
    }
}
