package com.studio08.xbgamestream.Cast;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;

import androidx.annotation.Nullable;

import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.media.widget.ExpandedControllerActivity;
import com.studio08.xbgamestream.Helpers.TutorialActivity;
import com.studio08.xbgamestream.MainActivity;
import com.studio08.xbgamestream.R;

public class MyExpandedControls extends ExpandedControllerActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = new Intent(MyExpandedControls.this, MainActivity.class);
        intent.putExtra("showCastRemote", true);
        startActivity(intent);
        finish();
    }
}