package com.cube.memorygames;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Offline splash: the original init block (Appodeal ad network, Branch deep
 * link session, secret-game unlock from referral links) is gone, so the
 * launcher goes straight to the main menu.
 */
public class SplashActivity extends AppCompatActivity {
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startActivity(new Intent(this, MainMenuActivity.class));
        finish();
    }
}
