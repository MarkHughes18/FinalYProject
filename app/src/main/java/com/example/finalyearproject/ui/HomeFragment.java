package com.example.finalyearproject.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.CreateHistoryRequest;
import com.example.finalyearproject.data.HistoryItem;
import com.example.finalyearproject.data.RetrofitClient;
import com.google.android.material.bottomsheet.BottomSheetDialog;

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
    private BottomSheetDialog playerSheet;
    private Handler playerHandler = new Handler(Looper.getMainLooper());
    private Runnable playerTick;
    private SeekBar bsSeekBar;
    private TextView bsTimeTV;
    private Button bsPlayPauseBtn;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;
    private boolean isPolling = false;
    private ActivityResultLauncher<Intent> pickFileLauncher;
    private static final String PREF_SETTINGS = "settings";
    private static final String KEY_TTS_VOICE_PREFIX = "tts_voice_";
    private static final String DEFAULT_TTS_VOICE = "female";
    private static final String BASE_URL_FOR_MEDIA = "http://10.0.2.2:8080";
    private static final long POLL_INTERVAL_MS = 2000;

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
        historyAdapter = new HistoryAdapter(historyItems, item ->
            onHistoryItemClicked(item), null);
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
        Context context = getContext();
        if (context == null) {
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
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

        showLabelSelector(userEmail, fileName, fileType, fileSize);
    }

    private void showLabelSelector(String userEmail,
                                   String fileName,
                                   String fileType,
                                   long fileSize) {

        String[] labels = {"Exam", "Lecture", "Assignment", "Other"};

        new AlertDialog.Builder(requireContext())
                .setTitle("Select Label")
                .setItems(labels, (dialog, which) -> {

                    String selected = labels[which];

                    if ("Other".equals(selected)) {
                        showCustomLabelInput(userEmail, fileName, fileType, fileSize);
                    } else {
                        createHistoryWithLabel(userEmail, fileName, fileType, fileSize, selected);
                    }

                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCustomLabelInput(String userEmail,
                                      String fileName,
                                      String fileType,
                                      long fileSize) {

        EditText input = new EditText(requireContext());
        input.setHint("Enter custom label");

        new AlertDialog.Builder(requireContext())
                .setTitle("Custom Label")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String customLabel = input.getText().toString().trim();

                    if (customLabel.isEmpty()) {
                        toast("Label cannot be empty");
                        return;
                    }

                    customLabel = customLabel.substring(0, 1).toUpperCase() + customLabel.substring(1);
                    createHistoryWithLabel(userEmail, fileName, fileType, fileSize, customLabel);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createHistoryWithLabel(String userEmail,
                                        String fileName,
                                        String fileType,
                                        long fileSize,
                                        String selectedLabel) {

        String lang = "en-GB";
        String voice = getSavedTtsVoiceForUser(userEmail);

        if (voice == null || voice.isBlank()) {
            voice = "female";
        }
        voice = voice.trim().toLowerCase();

        CreateHistoryRequest req = new CreateHistoryRequest(
                userEmail,
                fileName,
                fileType,
                fileSize,
                lang,
                voice,
                selectedLabel
        );

        uploadProgress.setVisibility(View.VISIBLE);

        api.createHistory(req).enqueue(new Callback<HistoryItem>() {
            @Override
            public void onResponse(Call<HistoryItem> call, Response<HistoryItem> response) {
                uploadProgress.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    HistoryItem created = response.body();

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

    private void getHistory(String userEmail){
        getHistory(userEmail, false);
    }
    private void getHistory(String userEmail, boolean silent) {
        if (userEmail == null) {
            toast("No logged in user");
            return;
        }

        if (!silent) uploadProgress.setVisibility(View.VISIBLE);

        api.getHistory(userEmail).enqueue(new Callback<List<HistoryItem>>() {
            @Override
            public void onResponse(Call<List<HistoryItem>> call,
                                   Response<List<HistoryItem>> response) {
                if (!silent) uploadProgress.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    historyItems.clear();
                    historyItems.addAll(response.body());
                    historyAdapter.notifyDataSetChanged();
                    if (hasInProgressItems()){
                        startPolling();
                    }else{
                        stopPolling();
                    }
                } else {
                    if (!silent) toast("Failed to load history");
                }
            }
            @Override
            public void onFailure(Call<List<HistoryItem>> call, Throwable t) {
                if (!silent) uploadProgress.setVisibility(View.GONE);
                if (!silent) toast("Network error: " + t.getMessage());
                stopPolling();
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
                        toast("Upload started. Processing will run in the shadows.");
                        String email = getLoggedInEmail();
                        if (email != null) getHistory(email); startPolling();
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

        if (!"READY".equalsIgnoreCase(item.audioStatus)) {
            toast("Audio not ready yet");
            return;
        }
        if(item.audioUrl == null || item.audioUrl.isBlank()){
            toast("Audio URL missing");
            return;
        }
        String fullUrl = item.audioUrl.startsWith("http")
                ? item.audioUrl : BASE_URL_FOR_MEDIA + item.audioUrl;

        showPlayerBottomSheet(item.fileName, fullUrl);
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
        stopPolling();
        stopPlayer();
    }

    private void showPlayerBottomSheet(String title, String url) {
        // close existing sheet/player if open
        stopPlayer();

        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottomsheet_audio_player, null, false);

        TextView titleTV = sheetView.findViewById(R.id.bsTitleTV);
        bsSeekBar = sheetView.findViewById(R.id.bsSeekBar);
        bsTimeTV = sheetView.findViewById(R.id.bsTimeTV);
        Button back10 = sheetView.findViewById(R.id.bsBack10Btn);
        bsPlayPauseBtn = sheetView.findViewById(R.id.bsPlayPauseBtn);
        Button fwd10 = sheetView.findViewById(R.id.bsFwd10Btn);

        titleTV.setText(title != null ? title : "Now playing");
        bsPlayPauseBtn.setEnabled(false);
        bsPlayPauseBtn.setText("Loading...");

        playerSheet = new BottomSheetDialog(requireContext());
        playerSheet.setContentView(sheetView);
        playerSheet.setOnDismissListener(d -> stopPlayer());
        playerSheet.show();

        try {
            mediaPlayer = new android.media.MediaPlayer();
            mediaPlayer.setAudioStreamType(android.media.AudioManager.STREAM_MUSIC);
            mediaPlayer.setDataSource(url);

            mediaPlayer.setOnPreparedListener(mp -> {
                int dur = mp.getDuration();
                bsSeekBar.setMax(dur);
                bsSeekBar.setProgress(0);
                bsTimeTV.setText(fmtTime(0) + " / " + fmtTime(dur));

                bsPlayPauseBtn.setEnabled(true);
                bsPlayPauseBtn.setText("Pause");

                mp.start();
                startPlayerTick();
            });

            mediaPlayer.setOnCompletionListener(mp -> {
                bsPlayPauseBtn.setText("Play");
                stopPlayerTick();
                if (bsSeekBar != null) bsSeekBar.setProgress(bsSeekBar.getMax());
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                toast("Playback error");
                stopPlayer();
                return true;
            });

            bsPlayPauseBtn.setOnClickListener(v -> togglePlayPause());

            back10.setOnClickListener(v -> seekBy(-10_000));
            fwd10.setOnClickListener(v -> seekBy(10_000));

            bsSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                boolean userSeeking = false;

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                    userSeeking = true;
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                    userSeeking = false;
                    if (mediaPlayer != null) {
                        mediaPlayer.seekTo(seekBar.getProgress());
                    }
                }

                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (fromUser && mediaPlayer != null) {
                        bsTimeTV.setText(fmtTime(progress) + " / " + fmtTime(mediaPlayer.getDuration()));
                    }
                }
            });

            mediaPlayer.prepareAsync();

        } catch (Exception e) {
            e.printStackTrace();
            toast("Failed to play: " + e.getMessage());
            stopPlayer();
        }
    }

    private void togglePlayPause() {
        if (mediaPlayer == null || bsPlayPauseBtn == null) return;

        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            bsPlayPauseBtn.setText("Play");
        } else {
            mediaPlayer.start();
            bsPlayPauseBtn.setText("Pause");
            startPlayerTick();
        }
    }

    private void seekBy(int deltaMs) {
        if (mediaPlayer == null) return;

        int pos = mediaPlayer.getCurrentPosition();
        int target = pos + deltaMs;
        if (target < 0) target = 0;

        int dur = mediaPlayer.getDuration();
        if (dur > 0 && target > dur) target = dur;

        mediaPlayer.seekTo(target);
        if (bsSeekBar != null) bsSeekBar.setProgress(target);
    }

    private void startPlayerTick() {
        stopPlayerTick();

        playerTick = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null && bsSeekBar != null && bsTimeTV != null) {
                    try {
                        int pos = mediaPlayer.getCurrentPosition();
                        int dur = mediaPlayer.getDuration();
                        bsSeekBar.setProgress(pos);
                        bsTimeTV.setText(fmtTime(pos) + " / " + fmtTime(dur));
                    } catch (Exception ignored) {}
                    playerHandler.postDelayed(this, 500);
                }
            }
        };
        playerHandler.post(playerTick);
    }

    private void stopPlayerTick() {
        if (playerTick != null) {
            playerHandler.removeCallbacks(playerTick);
            playerTick = null;
        }
    }

    private void stopPlayer() {
        stopPlayerTick();

        if (mediaPlayer != null) {
            try { mediaPlayer.stop(); } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (playerSheet != null && playerSheet.isShowing()) {
            playerSheet.dismiss();
            playerSheet = null;
        }
    }

    private static String fmtTime(int ms) {
        int totalSec = ms / 1000;
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return String.format("%02d:%02d", min, sec);
    }

    private void startPolling() {
        if (!isAdded() || getContext() == null) {
            return;
        }

        String email = getLoggedInEmail();
        if (email == null) return;

        isPolling = true;

        pollRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isAdded()) return;

                getHistory(email, true); // silent refresh

                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        isPolling = false;
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private boolean hasInProgressItems() {
        for (HistoryItem item : historyItems) {
            if (item != null && item.audioStatus != null) {
                String s = item.audioStatus.toUpperCase();
                if (s.equals("PENDING") || s.equals("PROCESSING")) {
                    return true;
                }
            }
        }
        return false;
    }


    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
