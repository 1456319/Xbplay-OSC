//package com.studio08.xbgamestream.ui.stream;
//
//import android.app.AlertDialog;
//import android.content.DialogInterface;
//import android.content.pm.ActivityInfo;
//import android.graphics.Color;
//import android.os.Bundle;
//import android.text.TextUtils;
//import android.view.LayoutInflater;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.Button;
//import android.widget.Toast;
//
//import androidx.annotation.NonNull;
//import androidx.fragment.app.Fragment;
//import androidx.lifecycle.ViewModelProvider;
//
//import com.studio08.xbgamestream.Helpers.EncryptClient;
//import com.studio08.xbgamestream.MainActivity;
//import com.studio08.xbgamestream.R;
//import com.studio08.xbgamestream.Web.StreamWebview;
//import com.studio08.xbgamestream.Web.ApiClient;
//import com.studio08.xbgamestream.databinding.FragmentStreamBinding;
//
//public class StreamFragment extends Fragment {
//
//    private StreamViewModel galleryViewModel;
//    private FragmentStreamBinding binding;
//    ApiClient streamingClient;
//    boolean viewActive = false;
//
//    // listener - fires when streaming detects auth is required
//    ApiClient.StreamingClientListener loginRequiredListener = new ApiClient.StreamingClientListener() {
//        @Override
//        public void onReLoginDetected() {
//            Toast.makeText(getActivity(), "Re Login Required!", Toast.LENGTH_LONG).show();
//            promptUserForLogin();
//        }
//        // closing screen not supported in this view
//        @Override
//        public void onCloseScreenDetected() {}
//
//        @Override
//        public void pressButtonWifiRemote(String type) {
//
//        }
//        @Override
//        public void setOrientationValue(String value) {}
//
//        @Override
//        public void vibrate() {}
//        @Override
//        public void genericMessage(String type, String msg) {}
//    };
//
//    public void promptUserForLogin() {
//        try {
//            new AlertDialog.Builder(getActivity())
//                    .setTitle("Login Required")
//                    .setMessage("We cannot connect to your console. Please navigate to the 'consoles' tab and click connect")
//                    .setCancelable(false)
//                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int which) {
//                            ((MainActivity) getActivity()).drawer.open();
//                        }
//                    })
//                    .show();
//        } catch (Exception e){
//            e.printStackTrace();
//        }
//    }
//
//    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
//        viewActive = true;
//        ((MainActivity)getActivity()).analyticsClient.logFragmentCreated("stream");
//
//        galleryViewModel = new ViewModelProvider(this).get(StreamViewModel.class);
//
//        binding = FragmentStreamBinding.inflate(inflater, container, false);
//        View root = binding.getRoot();
//
//        StreamWebview streamView = binding.webview1;
//        streamView.setBackgroundColor(Color.TRANSPARENT);
//        streamView.init();
//
//        Button startStream = binding.streamButton;
//        startStream.setVisibility(View.VISIBLE);
//
//
//        // get
//        EncryptClient encryptClient = new EncryptClient(getContext());
//        String serverId = encryptClient.getValue("serverId");
//        String gsToken = encryptClient.getValue("gsToken");
//
//        startStream.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                ((MainActivity)getActivity()).analyticsClient.logButtonClickEvent("open_stream");
//
//                if (!TextUtils.isEmpty(gsToken) && !TextUtils.isEmpty(serverId)) {
//                    ((MainActivity) getActivity()).setOrientationLandscape();
//
//                    startStream.setVisibility(View.INVISIBLE);
//                    streamingClient = new ApiClient(getActivity(), streamView, gsToken, serverId, false);
//                    streamingClient.setCustomObjectListener(loginRequiredListener);
//                    streamingClient.doStreamingOnly();
//                    streamView.requestFocus();
//                } else {
//                    promptUserForLogin();
//                }
//            }
//        });
//
//        return root;
//    }
//
//    @Override
//    public void onDestroyView() {
//        super.onDestroyView();
//        try {
//            viewActive = false;
//            streamingClient.cleanUp();
//        } catch(Exception e) {}
//        binding = null;
//    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        if(!viewActive) {
//            try {
//                ((MainActivity) getActivity()).navController.navigate(R.id.nav_stream);
//            } catch (Exception e){
//                e.printStackTrace();
//            }
//        }
//    }
//
//    @Override
//    public void onStop() {
//        super.onStop();
//        try {
//            viewActive = false;
//            streamingClient.cleanUp();
//        } catch(Exception e) {
//            e.printStackTrace();
//        }
//        binding = null;
//    }
//}