package com.example.finalyearproject.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.CreateHistoryRequest;
import com.example.finalyearproject.data.HistoryItem;
import com.example.finalyearproject.data.RetrofitClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomeFragment extends Fragment {

    private TextView selectedFileTV;
    private Button pickFileBtn, uploadBtn;
    private ProgressBar uploadProgress;
    private RecyclerView previousFilesRV;
    // parameters for the file selected by user
    private Uri selectedFileUri = null;
    private String selectedFileName;
    // Data for RecyclerView
    private final List<HistoryItem> historyItems = new ArrayList<>();
    private HistoryAdapter historyAdapter;
    private ApiService api;
    private android.media.MediaPlayer mediaPlayer;

    private ActivityResultLauncher<Intent> pickFileLauncher;
    private static final String PREF_SETTINGS = "settings";
    private static final String KEY_TTS_VOICE_PREFIX = "tts_voice_";
    private static final String DEFAULT_TTS_VOICE = "female";
    private static final String BASE_URL_FOR_MEDIA = "http://10.0.2.2:8080";

    public HomeFragment() {
        // required empty  constructor
    }

    //Lifecycle

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // This uses fragment_home.xml
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        selectedFileTV   = view.findViewById(R.id.selectedFileTV);
        pickFileBtn      = view.findViewById(R.id.pickFileBtn);
        uploadBtn        = view.findViewById(R.id.uploadBtn);
        uploadProgress   = view.findViewById(R.id.uploadProgress);
        previousFilesRV  = view.findViewById(R.id.previousFilesRV);

        previousFilesRV.setLayoutManager(
                new LinearLayoutManager(requireContext()));
        historyAdapter = new HistoryAdapter(historyItems, item -> {
            onHistoryItemClicked(item);});
        previousFilesRV.setAdapter(historyAdapter);

        api = RetrofitClient.getApiService();
        setupFilePicker();

        pickFileBtn.setOnClickListener(v -> openFilePicker());
        uploadBtn.setOnClickListener(v -> onUploadClicked());

        // Load history for the logged-in user
        String email = getLoggedInEmail();
        if (email != null) {
            getHistory(email);
        } else {
            toast("No logged in user");
        }
    }

    // Helpers

    private String getLoggedInEmail() {
        SharedPreferences prefs =
                requireActivity().getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("email", null);
    }

    // how we receive picked file
    private void setupFilePicker() {
        pickFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == AppCompatActivity.RESULT_OK &&
                            result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            selectedFileUri = uri;
                            selectedFileName = getFileNameFromUri(uri);
                            selectedFileTV.setText("Selected: " + selectedFileName);
                        }
                    }
                }
        );
    }

    // launch system file picker
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // allowing multiple file types to be uploaded
        intent.setType("*/*");
        String[] mimeTypes = new String[]{
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/plain"
        };

        intent.putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes);
        pickFileLauncher.launch(intent);
    }

    // method to start upload of file and generate audio
    private void onUploadClicked() {
        if (selectedFileUri == null) {
            toast("Please choose a file first.");
            return;
        }
        createHistory();
    }

    private String getFileNameFromUri(Uri uri) {
        String result = null;
        ContentResolver cr = requireContext().getContentResolver();
        Cursor cursor = cr.query(uri, null, null, null, null);
        try {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0) {
                    result = cursor.getString(nameIndex);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result != null ? result : "Unnamed File";
    }

    private void createHistory() {
        if (selectedFileUri == null) {
            toast("Please choose a file first");
            return;
        }

        String userEmail = getLoggedInEmail();
        if (userEmail == null) {
            toast("Session expired. Please sign in again.");
            // optionally navigate to MainActivity
            return;
        }

        //Get file metadata from the Uri
        String fileName = getFileNameFromUri(selectedFileUri);   // helper
        String fileType = requireContext().getContentResolver().getType(selectedFileUri); // e.g. "application/pdf"

        if (fileType == null) {
            // Fallback to generic type if MIME type is unknown
            fileType = "application/octet-stream";
        }

        long fileSize = 0L;
        Cursor c = null;
        try {
            c = requireContext().getContentResolver().query(
                    selectedFileUri,
                    new String[]{OpenableColumns.SIZE},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                int sizeIndex = c.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    fileSize = c.getLong(sizeIndex);
                }
            }
        } finally {
            if (c != null) c.close();
        }

        String lang = "en-GB";
        String voice = getSavedTtsVoiceForUser(userEmail);
        if (voice == null || voice.isBlank()) voice = "female";
        voice = voice.trim().toLowerCase();
        //build request object
        CreateHistoryRequest req = new CreateHistoryRequest(
                userEmail,
                fileName,
                fileType,
                fileSize,
                lang,
                voice);

        uploadProgress.setVisibility(View.VISIBLE);

        api.createHistory(req).enqueue(new Callback<HistoryItem>() {
            @Override
            public void onResponse(Call<HistoryItem> call, Response<HistoryItem> response) {
                uploadProgress.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    HistoryItem created = response.body();
                    // Add the new item to the top of the list
                    historyItems.add(0, created);
                    historyAdapter.notifyItemInserted(0);
                    toast("History updated for " + created.fileName);
                    uploadSelectedFile(created.id);
                } else {
                    toast("Failed to create history entry");
                }
            }

            @Override
            public void onFailure(Call<HistoryItem> call, Throwable t) {
                uploadProgress.setVisibility(View.GONE);
                toast("Network error: " + t.getMessage());
            }
        });
    }

    private void getHistory(String userEmail) {
        if (userEmail == null) {
            toast("No logged in user");
            return;
        }

        uploadProgress.setVisibility(View.VISIBLE);

        api.getHistory(userEmail).enqueue(new Callback<List<HistoryItem>>() {
            @Override
            public void onResponse(Call<List<HistoryItem>> call,
                                   Response<List<HistoryItem>> response) {
                uploadProgress.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    historyItems.clear();
                    historyItems.addAll(response.body());
                    historyAdapter.notifyDataSetChanged();
                } else {
                    toast("Failed to load history");
                }
            }

            @Override
            public void onFailure(Call<List<HistoryItem>> call, Throwable t) {
                uploadProgress.setVisibility(View.GONE);
                toast("Network error: " + t.getMessage());
            }
        });
    }

    private String getSavedTtsVoiceForUser(String email) {
        SharedPreferences prefs =
                requireActivity().getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE);
        String key = (email != null) ? KEY_TTS_VOICE_PREFIX + email : KEY_TTS_VOICE_PREFIX + "default";
        return prefs.getString(key, DEFAULT_TTS_VOICE);
    }

    private void uploadSelectedFile(String historyId){
        if (selectedFileUri == null) {
            toast("No file selected");
            return;
        }
        try {
            // Read bytes from Uri
            ContentResolver cr = requireContext().getContentResolver();
            String fileName = getFileNameFromUri(selectedFileUri);

            byte[] bytes;
            try (InputStream in = cr.openInputStream(selectedFileUri)) {
                if (in == null) {
                    toast("Failed to read file");
                    return;
                }
                bytes = readAllBytes(in);
            }

            RequestBody reqBody = RequestBody.create(bytes, okhttp3.MediaType.parse("application/octet-stream"));
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", fileName, reqBody);

            uploadProgress.setVisibility(View.VISIBLE);
            api.uploadFile(historyId, part).enqueue(new Callback<HistoryItem>() {
                @Override
                public void onResponse(Call<HistoryItem> call, Response<HistoryItem> response) {
                    uploadProgress.setVisibility(View.GONE);
                    if (response.isSuccessful() && response.body() != null) {
                        toast("Upload started. Processing will run in background.");
                        String email = getLoggedInEmail();
                        if (email != null) getHistory(email);
                    } else {
                        toast("Upload failed");
                    }
                }
                @Override
                public void onFailure(Call<HistoryItem> call, Throwable t) {
                    uploadProgress.setVisibility(View.GONE);
                    toast("Upload error: " + t.getMessage());
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
            toast("Upload error: " + e.getMessage());
        }
    }
    private static byte[] readAllBytes(java.io.InputStream in) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int nRead;
        while ((nRead = in.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    private void onHistoryItemClicked(HistoryItem item) {
        if (item == null) return;

        if (!"READY".equalsIgnoreCase(item.audioStatus) || item.audioUrl == null) {
            toast("Audio not ready yet");
            return;
        }
        playAudioFromUrl(item.audioUrl);
    }
    private void playAudioFromUrl(String audioUrlPath) {
        String fullUrl = audioUrlPath.startsWith("http")
                ? audioUrlPath
                : BASE_URL_FOR_MEDIA + audioUrlPath;
        startMediaPlayer(fullUrl);
    }
    private void startMediaPlayer(String url) {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            uploadProgress.setVisibility(View.VISIBLE);
            toast("Loading audio...");

            mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(url);

            mediaPlayer.setOnPreparedListener(mp -> {
                uploadProgress.setVisibility(View.GONE);
                mp.start();
                toast("Playing");
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                toast("Finished");
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                uploadProgress.setVisibility(View.GONE);
                toast("Audio playback error");
                return true;
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            uploadProgress.setVisibility(View.GONE);
            e.printStackTrace();
            toast("Failed to play audio: " + e.getMessage());
        }
    }
    @Override
    public void onStop() {
        super.onStop();
        if (mediaPlayer != null) {
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
