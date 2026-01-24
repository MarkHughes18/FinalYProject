package com.example.finalyearproject.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.finalyearproject.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class HomePGActivity extends AppCompatActivity {

    private MaterialToolbar homeToolbar;
    private BottomNavigationView homeBottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepg);

        homeToolbar = findViewById(R.id.homeToolbar);
        homeBottomNav = findViewById(R.id.homeBottomNav);

        setSupportActionBar(homeToolbar);
        // Optional explicit title if not set in XML
        getSupportActionBar().setTitle("Unconventional Learning");

        // Default tab = Home
        replaceFragment(new HomeFragment());

        // Bottom navigation behaviour
        homeBottomNav.setOnItemSelectedListener(item -> {
            Fragment f;
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                f = new HomeFragment();
            } else if (id == R.id.nav_history) {
                // TODO: later move history list here if you want a separate screen
                f = new HomeFragment(); // placeholder fragment
                Toast.makeText(this, "History tab (WIP)", Toast.LENGTH_SHORT).show();
            } else if (id == R.id.nav_settings) {
                f = new SettingsFragment(); // simple placeholder fragment
            } else {
                return false;
            }
            replaceFragment(f);
            return true;
        });
    }

    private void replaceFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.homeFragmentContainer, fragment)
                .commit();
    }

    // Toolbar options menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_homepg, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_my_files) {
            Toast.makeText(this, "My Uploads", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_settings) {
            Toast.makeText(this, "Settings", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_logout) {
            Intent intent = new Intent(HomePGActivity.this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
