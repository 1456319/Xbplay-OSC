package com.studio08.xbgamestream.Helpers;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.studio08.xbgamestream.R;

public class PWASettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // below line is used to check if
        // frame layout is empty or not.
        if (findViewById(R.id.idFrameLayout) != null) {
            if (savedInstanceState != null) {
                return;
            }
            // below line is to inflate our fragment.
            getSupportFragmentManager().beginTransaction().add(R.id.idFrameLayout, new PWASettingsFragment()).commit();
        }

        Intent returnIntent = new Intent();
        setResult(Activity.RESULT_OK, returnIntent);
    }
}
