package com.example.finalyearproject.ui;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalyearproject.R;
import com.example.finalyearproject.data.ApiService;
import com.example.finalyearproject.data.HistoryItem;
import com.example.finalyearproject.data.CreateHistoryRequest;
import com.example.finalyearproject.data.RetrofitClient;


import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class HomePGActivity extends AppCompatActivity {

    private TextView selectedFileTV;
    private Button pickFileBtn, uploadBtn;
    private ProgressBar uploadProgress;
    private RecyclerView previousFilesRV;

    //parameters for the dile selected by user
    private Uri selectedFileUri = null;
    private String selectedFileName;

    // Data for RecyclerView
    private final List<HistoryItem> historyItems = new ArrayList<>();
    private HistoryAdapter historyAdapter;

    private ApiService api;

    private String getLoggedInEmail() {
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        return prefs.getString("email", null);
    }


    private ActivityResultLauncher<Intent> pickFileLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_homepg);

        selectedFileTV = findViewById(R.id.selectedFileTV);
        pickFileBtn = findViewById(R.id.pickFileBtn);
        uploadBtn = findViewById(R.id.uploadBtn);
        uploadProgress = findViewById(R.id.uploadProgress);
        previousFilesRV = findViewById(R.id.previousFilesRV);
        Toolbar toolbar = findViewById(R.id.homeToolbar);
        setSupportActionBar(toolbar);


        previousFilesRV.setLayoutManager(new LinearLayoutManager(this));
        historyAdapter = new HistoryAdapter(historyItems);
        previousFilesRV.setAdapter(historyAdapter);

        api = RetrofitClient.getApiService();
        setupFilePicker();

        pickFileBtn.setOnClickListener(v -> openFilePicker());
        uploadBtn.setOnClickListener(v -> onUploadClicked());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.menu_homepg, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){
        int id = item.getItemId();
        if (id == R.id.action_my_files) {
            // create a my uploads screen
            Toast.makeText(this, "My Uploads", Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_settings) {
            // create a settings page
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

    //how we receive picked file
    private void setupFilePicker() {
        pickFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null){
                        Uri uri = result.getData().getData();
                        if (uri != null){
                            selectedFileUri = uri;
                            selectedFileName = getFileNameFromUri(uri);
                            selectedFileTV.setText("Selected: " + selectedFileName);
                        }
                    }
                }
        );
    }

    //launch system file picker
    private void openFilePicker(){
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        //allowing multiple file types to be uploaded
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

    //method to start upload of file and generate audio
    private void onUploadClicked() {
        if(selectedFileUri == null){
            toast("Please choose a file first.");
            return;
        }

        //need to send off the selected file to backend using retrofit
        //get back audio file that was generated by the appp
    }

    private String getFileNameFromUri(Uri uri){
        String result = null;
        ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(uri, null, null, null, null);
        try{
            if(cursor != null && cursor.moveToFirst()){
                int nameIndex = cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0){
                    result = cursor.getString(nameIndex);
                }
            }
        }finally {
            if(cursor != null){
                cursor.close();
            }
        }
        if (result == null){
            result = uri.getLastPathSegment();
        }

        return result != null ? result : "Unnamed File";
    }

    private void createHistory() {
        if (selectedFileUri == null) {
            Toast.makeText(this, "Please choose a file first", Toast.LENGTH_SHORT).show();
            return;
        }

        String userEmail = getLoggedInEmail();
        if (userEmail == null) {
            Toast.makeText(this, "Session expired. Please sign in again.", Toast.LENGTH_SHORT).show();
            // optionally navigate back to MainActivity here
            return;
        }

        // ---- Get file metadata from the Uri ----
        String fileName = getFileNameFromUri(selectedFileUri);   // you already have this helper
        String fileType = getContentResolver().getType(selectedFileUri); // e.g. "application/pdf"

        if (fileType == null) {
            // Fallback to extension if MIME type is unknown
            fileType = "application/octet-stream";
        }

        long fileSize = 0L;
        Cursor c = null;
        try {
            c = getContentResolver().query(selectedFileUri,
                    new String[]{android.provider.OpenableColumns.SIZE},
                    null, null, null);
            if (c != null && c.moveToFirst()) {
                int sizeIndex = c.getColumnIndex(android.provider.OpenableColumns.SIZE);
                if (sizeIndex >= 0) {
                    fileSize = c.getLong(sizeIndex);
                }
            }
        } finally {
            if (c != null) c.close();
        }

        // ---- Build request object ----
        CreateHistoryRequest req = new CreateHistoryRequest(
                userEmail,
                fileName,
                fileType,
                fileSize
        );

        // Optionally show a progress bar while we call the backend
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
                    Toast.makeText(HomePGActivity.this,
                            "History updated for " + created.fileName,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(HomePGActivity.this,
                            "Failed to create history entry",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<HistoryItem> call, Throwable t) {
                uploadProgress.setVisibility(View.GONE);
                Toast.makeText(HomePGActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getHistory(String userEmail) {
        if (userEmail == null) {
            Toast.makeText(this, "No logged in user", Toast.LENGTH_SHORT).show();
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
                    Toast.makeText(HomePGActivity.this,
                            "Failed to load history",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<HistoryItem>> call, Throwable t) {
                uploadProgress.setVisibility(View.GONE);
                Toast.makeText(HomePGActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }





    private void toast(String msg){
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
