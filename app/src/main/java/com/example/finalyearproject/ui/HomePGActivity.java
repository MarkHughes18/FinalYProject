package com.example.finalyearproject.ui;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;

import com.example.finalyearproject.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomePGActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Get logged-in email
        SharedPreferences authPrefs = getSharedPreferences("auth", MODE_PRIVATE);
        String email = authPrefs.getString("email", null);

        //Build a per-user key
        String themeKey = (email != null)
                ? "theme_mode_" + email
                : "theme_mode_default";

        //Read that user’s theme or system default
        SharedPreferences settingsPrefs = getSharedPreferences("settings", MODE_PRIVATE);
        int mode = settingsPrefs.getInt(
                themeKey,
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        );

        AppCompatDelegate.setDefaultNightMode(mode);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepg);

        // Toolbar
        MaterialToolbar toolbar = findViewById(R.id.homeToolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("");

        // Bottom navigation
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);

        bottomNav.setOnItemSelectedListener(item -> {
            Fragment fragment = null;
            int id = item.getItemId();

            if (id == R.id.nav_home) {
                fragment = new HomeFragment();
            } else if (id == R.id.nav_history) {
                fragment = new HistoryFragment();
            } else if (id == R.id.nav_settings) {
                fragment = new SettingsFragment();
            }

            if (fragment != null) {
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.homeFragmentContainer, fragment)
                        .commit();
                return true;
            }
            return false;
        });

        // Show Home tab by default on first creation
        if (savedInstanceState == null) {
            bottomNav.setSelectedItemId(R.id.nav_home);
        }
    }
}
