package com.example.finalyearproject.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.HistoryItem;
import com.example.finalyearproject.data.RetrofitClient;
import com.example.finalyearproject.ui.HomeFragment;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HistoryFragment extends Fragment {

    private RecyclerView historyRecyclerView;
    private ProgressBar historyProgress;
    private TextView historyEmptyTV;

    private HistoryAdapter historyAdapter;
    private android.media.MediaPlayer mediaPlayer;
    private BottomSheetDialog playerSheet;
    private Handler playerHandler = new Handler(Looper.getMainLooper());
    private Runnable playerTick;
    private SeekBar bsSeekBar;
    private TextView bsTimeTV;
    private Button bsPlayPauseBtn;
    private SearchView historySearchView;
    private final List<HistoryItem> historyItems = new ArrayList<>();
    private final List<HistoryItem> allHistoryItems = new ArrayList<>();
    private static final String MEDIA_BASE_URL = "http://10.0.2.2:8080";

    private ApiService api;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Bind views
        historyRecyclerView = view.findViewById(R.id.historyRecyclerView);
        historyProgress     = view.findViewById(R.id.historyProgress);
        historyEmptyTV      = view.findViewById(R.id.historyEmptyTV);

        // Setup RecyclerView
        historyRecyclerView.setLayoutManager(
                new LinearLayoutManager(requireContext())
        );
        historyAdapter = new HistoryAdapter(historyItems, item -> onHistoryItemClicked(item)
        , item -> confirmDelete(item));
        historyRecyclerView.setAdapter(historyAdapter);

        historySearchView = view.findViewById(R.id.historySearchView);
        historySearchView.setIconifiedByDefault(false);
        historySearchView.setIconified(false);
        historySearchView.setMaxWidth(Integer.MAX_VALUE);
        historySearchView.clearFocus();
        historySearchView.setQueryHint("Search uploads...");
        TextView searchText = historySearchView.findViewById(androidx.appcompat.R.id.search_src_text);
        if (searchText != null) {
            searchText.setHint("Search uploads...");
            searchText.setHintTextColor(getResources().getColor(android.R.color.darker_gray));
            searchText.setTextColor(getResources().getColor(android.R.color.black));
            searchText.setTextSize(16);
        }
        historySearchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterHistory(query);
                return true;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                filterHistory(newText);
                return true;
            }
        });

        // Retrofit
        api = RetrofitClient.getApiService();

        // Get logged in user's email from SharedPreferences
        String email = getLoggedInEmail();
        if (email == null) {
            Toast.makeText(requireContext(),
                    "No logged in user. Please sign in again.",
                    Toast.LENGTH_SHORT).show();
            historyEmptyTV.setVisibility(View.VISIBLE);
            historyEmptyTV.setText("Please sign in to see your uploads.");
            return;
        }

        // Load history from backend
        loadHistory(email);
    }

    private String getLoggedInEmail() {
        Context ctx = requireContext();
        SharedPreferences prefs = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE);
        return prefs.getString("email", null);
    }

    private void loadHistory(String email) {
        historyProgress.setVisibility(View.VISIBLE);
        historyEmptyTV.setVisibility(View.GONE);

        api.getHistory(email).enqueue(new Callback<List<HistoryItem>>() {
            @Override
            public void onResponse(@NonNull Call<List<HistoryItem>> call,
                                   @NonNull Response<List<HistoryItem>> response) {
                historyProgress.setVisibility(View.GONE);

                if (response.isSuccessful() && response.body() != null) {
                    allHistoryItems.clear();
                    allHistoryItems.addAll(response.body());
                    historyItems.clear();
                    historyItems.addAll(response.body());
                    historyAdapter.notifyDataSetChanged();

                    if (historyItems.isEmpty()) {
                        historyEmptyTV.setVisibility(View.VISIBLE);
                        historyEmptyTV.setText("No uploads yet.");
                    } else {
                        historyEmptyTV.setVisibility(View.GONE);
                    }
                } else {
                    historyEmptyTV.setVisibility(View.VISIBLE);
                    historyEmptyTV.setText("Failed to load history.");
                    Toast.makeText(requireContext(),
                            "Failed to load history",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<HistoryItem>> call,
                                  @NonNull Throwable t) {
                historyProgress.setVisibility(View.GONE);
                historyEmptyTV.setVisibility(View.VISIBLE);
                historyEmptyTV.setText("Network error.");
                Toast.makeText(requireContext(),
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
    private void filterHistory(String query) {
        String q = (query == null) ? "" : query.trim().toLowerCase();

        historyItems.clear();
        if (q.isEmpty()) {
            historyItems.addAll(allHistoryItems);
        } else {
            for (HistoryItem item : allHistoryItems) {
                String name = item.fileName == null ? "" : item.fileName.toLowerCase();
                String type = item.fileType == null ? "" : item.fileType.toLowerCase();

                if (name.contains(q) || type.contains(q)) {
                    historyItems.add(item);
                }
            }
        }

        historyAdapter.notifyDataSetChanged();

        if (historyItems.isEmpty()) {
            historyEmptyTV.setVisibility(View.VISIBLE);
            historyEmptyTV.setText("No matches found.");
        } else {
            historyEmptyTV.setVisibility(View.GONE);
        }
    }

    private void onHistoryItemClicked(HistoryItem item) {
        if (item == null) return;

        showHistoryActionSheet(item);
    }

    private void showHistoryActionSheet(HistoryItem item) {
        View sheetView = LayoutInflater.from(requireContext())
                .inflate(R.layout.bottomsheet_history_actions, null, false);

        TextView titleTv = sheetView.findViewById(R.id.actionTitleTV);
        View playAudioBtn = sheetView.findViewById(R.id.actionPlayAudio);
        View openStudyPackBtn = sheetView.findViewById(R.id.actionOpenStudyPack);
        View cancelBtn = sheetView.findViewById(R.id.actionCancel);

        titleTv.setText(item.fileName != null ? item.fileName : "Choose action");

        BottomSheetDialog dialog = new BottomSheetDialog(requireContext());
        dialog.setContentView(sheetView);

        playAudioBtn.setOnClickListener(v -> {
            dialog.dismiss();
            handlePlayAudio(item);
        });

        openStudyPackBtn.setOnClickListener(v -> {
            dialog.dismiss();
            handleOpenStudyPack(item);
        });

        cancelBtn.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void handlePlayAudio(HistoryItem item) {
        if (item == null) return;

        if (!"READY".equalsIgnoreCase(item.audioStatus)) {
            toast("Audio not ready yet: " + item.audioStatus);
            return;
        }
        if (item.audioUrl == null || item.audioUrl.isBlank()) {
            toast("Audio URL missing");
            return;
        }

        String fullUrl = item.audioUrl.startsWith("http")
                ? item.audioUrl
                : MEDIA_BASE_URL + item.audioUrl;

        showPlayerBottomSheet(item.fileName, fullUrl);
    }

    private void handleOpenStudyPack(HistoryItem item) {
        if (item == null || item.id == null || item.id.isBlank()) {
            toast("Study pack unavailable: missing history id");
            return;
        }

        android.content.Intent intent = new android.content.Intent(requireContext(), StudyPackActivity.class);
        intent.putExtra("historyId", item.id);
        intent.putExtra("fileName", item.fileName);
        startActivity(intent);
    }
    private void showPlayerBottomSheet(String title, String url) {

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
                bsSeekBar.setProgress(bsSeekBar.getMax());
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

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) { }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
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
        if (mediaPlayer == null) return;

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
        if (target > dur) target = dur;

        mediaPlayer.seekTo(target);
        bsSeekBar.setProgress(target);
    }

    private void startPlayerTick() {
        stopPlayerTick();

        playerTick = new Runnable() {
            @Override
            public void run() {
                if (mediaPlayer != null) {
                    int pos = mediaPlayer.getCurrentPosition();
                    int dur = mediaPlayer.getDuration();

                    bsSeekBar.setProgress(pos);
                    bsTimeTV.setText(fmtTime(pos) + " / " + fmtTime(dur));

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

    @Override
    public void onStop() {
        super.onStop();
        stopPlayer();
    }

    private void confirmDelete(HistoryItem item) {
        if (item == null || item.id == null || item.id.isBlank()) {
            toast("Cannot delete: missing id");
            return;
        }
        String name = (item.fileName != null && !item.fileName.isBlank())
                ? item.fileName
                : "this upload";

        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Delete upload")
                .setMessage("Delete \"" + name + "\" and its generated audio? This cannot be undone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> deleteHistoryItem(item))
                .show();
    }

    private void deleteHistoryItem(HistoryItem item) {
        api.deleteHistoryItem(item.id).enqueue(new retrofit2.Callback<Void>() {
            @Override
            public void onResponse(Call<Void> call, Response<Void> response) {
                if (response.isSuccessful()) {
                    toast("Deleted");

                    // remove from BOTH lists so search + list stay consistent
                    removeById(allHistoryItems, item.id);
                    removeById(historyItems, item.id);
                    historyAdapter.notifyDataSetChanged();

                } else {
                    toast("Delete failed: " + response.code());
                }
            }
            @Override
            public void onFailure(Call<Void> call, Throwable t) {
                toast("Network error: " + t.getMessage());
            }
        });
    }
    private static void removeById(List<HistoryItem> list, String id) {
        for (int i = list.size() - 1; i >= 0; i--) {
            HistoryItem it = list.get(i);
            if (it != null && id.equals(it.id)) {
                list.remove(i);
            }
        }
    }
    private void toast(String msg) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
