package com.example.finalyearproject.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.RetrofitClient;
import com.example.finalyearproject.data.UserProfile;

import retrofit2.Call;
import retrofit2.Response;

public class SettingsFragment extends Fragment {

    private TextView emailTV, fullNameTV, dobTV;
    private Button logoutBtn;
    private RadioGroup themeGroup, voiceGroup;
    private RadioButton rbSystem, rbLight, rbDark, rbVoiceFemale, rbVoiceMale;
    private Button clearHistoryBtn;

    private ApiService api;

    private static final String PREF_SETTINGS = "settings";
    private static final String DEFAULT_TTS_VOICE = "female";

    public SettingsFragment() { }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        emailTV = view.findViewById(R.id.settingsEmailTV);
        fullNameTV = view.findViewById(R.id.settingsFullNameTV);
        dobTV = view.findViewById(R.id.settingsDobTV);
        logoutBtn = view.findViewById(R.id.settingsLogoutBtn);
        themeGroup = view.findViewById(R.id.themeRadioGroup);
        rbSystem = view.findViewById(R.id.rbThemeSystem);
        rbLight = view.findViewById(R.id.rbThemeLight);
        rbDark = view.findViewById(R.id.rbThemeDark);
        clearHistoryBtn = view.findViewById(R.id.settingsClearHistoryBtn);
        voiceGroup = view.findViewById(R.id.voiceRadioGroup);
        rbVoiceFemale = view.findViewById(R.id.rbVoiceFemale);
        rbVoiceMale = view.findViewById(R.id.rbVoiceMale);

        api = RetrofitClient.getApiService();

        //Show logged-in email
        String email = getLoggedInEmail();
        if (email != null) {
            emailTV.setText("Logged in as: " + email);
            loadUserProfile(email);
        } else {
            emailTV.setText("Logged in as: (none)");
        }

        //Theme selection
        int savedMode = getSavedThemeModeForCurrentUser();
        applyThemeSelectionToUI(savedMode);

        themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int mode;
            if (checkedId == R.id.rbThemeLight) {
                mode = AppCompatDelegate.MODE_NIGHT_NO;
            } else if (checkedId == R.id.rbThemeDark) {
                mode = AppCompatDelegate.MODE_NIGHT_YES;
            } else {
                mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            }

            saveThemeModeForCurrentUser(mode);
            AppCompatDelegate.setDefaultNightMode(mode);
        });
        String savedVoice = getSavedTtsVoiceForCurrentUser();
        applyVoiceSelectionToUI(savedVoice);
        voiceGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String voice;
            if (checkedId == R.id.rbVoiceMale) {
                voice = "male";
            } else {
                voice = "female";
            }
            saveTtsVoiceForCurrentUser(voice);
        });

        //Logout
        logoutBtn.setOnClickListener(v -> {
            clearAuthPrefs();
            AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            );

            Toast.makeText(requireContext(),
                    "Logged out",
                    Toast.LENGTH_SHORT).show();

            // Go back to sign-in screen
            Intent i = new Intent(requireActivity(), MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
        });

        //Clear upload history
        clearHistoryBtn.setOnClickListener(v -> {
            if (email == null) {
                Toast.makeText(requireContext(),
                        "No logged in user",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle("Clear Upload History")
                    .setMessage("Are you sure you want to delete all uploaded files? This cannot be undone.")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        api.clearHistory(email).enqueue(new retrofit2.Callback<Void>() {
                            @Override
                            public void onResponse(Call<Void> call, Response<Void> response) {

                                if (response.isSuccessful()) {
                                    Toast.makeText(requireContext(),
                                            "Upload history cleared!",
                                            Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(requireContext(),
                                            "Failed to clear history",
                                            Toast.LENGTH_SHORT).show();
                                }
                            }

                            @Override
                            public void onFailure(Call<Void> call, Throwable t) {
                                Toast.makeText(requireContext(),
                                        "Network error: " + t.getMessage(),
                                        Toast.LENGTH_SHORT).show();
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    // helpers
    private void loadUserProfile(String email) {
        api.getUserProfile(email).enqueue(new retrofit2.Callback<com.example.finalyearproject.data.UserProfile>() {
            @Override
            public void onResponse(Call<UserProfile> call,
                                   Response<UserProfile> response) {
                if (response.isSuccessful() && response.body() != null) {
                    UserProfile profile = response.body();
                    fullNameTV.setText("Full name: " + profile.fullName);
                    dobTV.setText("DOB: " + profile.dob);
                } else {
                    Toast.makeText(requireContext(), "No User Info Available", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<UserProfile> call, Throwable t) {
                Toast.makeText(requireContext(), "No User Info Available", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String getLoggedInEmail() {
        SharedPreferences prefs =
                requireActivity().getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("email", null);
    }

    private String getThemeKeyForCurrentUser() {
        String email = getLoggedInEmail();
        return (email != null) ? "theme_mode_" + email : "theme_mode_default";
    }

    private void clearAuthPrefs() {
        SharedPreferences prefs =
                requireActivity().getSharedPreferences("auth", Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }

    private int getSavedThemeModeForCurrentUser() {
        SharedPreferences prefs =
                requireActivity().getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
        String key = getThemeKeyForCurrentUser();
        return prefs.getInt(key, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private void saveThemeModeForCurrentUser(int mode) {
        SharedPreferences prefs =
                requireActivity().getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
        String key = getThemeKeyForCurrentUser();
        prefs.edit().putInt(key, mode).apply();
    }

    private void applyThemeSelectionToUI(int mode) {
        switch (mode) {
            case AppCompatDelegate.MODE_NIGHT_NO:
                rbLight.setChecked(true);
                break;
            case AppCompatDelegate.MODE_NIGHT_YES:
                rbDark.setChecked(true);
                break;
            default:
                rbSystem.setChecked(true);
                break;
        }
    }
    private String getTtsVoiceKeyForCurrentUser() {
        String email = getLoggedInEmail();
        return (email != null) ? "tts_voice_" + email : "tts_voice_default";
    }

    private String getSavedTtsVoiceForCurrentUser() {
        SharedPreferences prefs =
                requireActivity().getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
        String key = getTtsVoiceKeyForCurrentUser();
        return prefs.getString(key, DEFAULT_TTS_VOICE);
    }

    private void saveTtsVoiceForCurrentUser(String voice) {
        SharedPreferences prefs =
                requireActivity().getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
        String key = getTtsVoiceKeyForCurrentUser();
        prefs.edit().putString(key, voice).apply();
    }

    private void applyVoiceSelectionToUI(String voice) {
        if ("male".equalsIgnoreCase(voice)) {
            rbVoiceMale.setChecked(true);
        } else {
            rbVoiceFemale.setChecked(true);
        }
    }
}
