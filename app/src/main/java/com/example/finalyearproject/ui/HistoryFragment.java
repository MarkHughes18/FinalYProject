package com.example.finalyearproject.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.HistoryItem;
import com.example.finalyearproject.data.RetrofitClient;

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
    private final List<HistoryItem> historyItems = new ArrayList<>();

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
        historyAdapter = new HistoryAdapter(historyItems);
        historyRecyclerView.setAdapter(historyAdapter);

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
}
