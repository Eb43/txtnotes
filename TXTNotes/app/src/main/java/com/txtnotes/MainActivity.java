package com.txtnotes;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;
import android.icu.text.SimpleDateFormat;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

import android.widget.TextView;
import android.util.Log;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
    private static final String PREFS_NAME = "TXTNotesPrefs";
    private static final String SELECTED_FOLDER_KEY = "selectedFolder";
    private static final int REQUEST_CODE_PERMISSIONS = 2;
    private static final long REFRESH_INTERVAL_MS = 60000; // 60 seconds

    private static final int PreviewFirstCharactersFromFile = 250; //Show first N characters in preview
    private TextView statusTextView;
    private TextView currentDirectoryPath;
    private ListView fileListView;
    private Uri selectedFolderUri;
    private Handler handler;
    private Runnable rescanRunnable;
    private FileCardAdapter fileCardAdapter; // Custom card adapter

    // Background executor for async operations
    private ExecutorService backgroundExecutor;

    // Main thread handler for UI updates
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    // Track if we're currently loading to prevent multiple concurrent loads
    private volatile boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize executor
        backgroundExecutor = Executors.newSingleThreadExecutor();

        fileListView = findViewById(R.id.fileListView);
        statusTextView = findViewById(R.id.statusTextView);
        currentDirectoryPath = findViewById(R.id.currentDirectoryPath);

        Button selectFolderButton = findViewById(R.id.selectFolderButton);
        selectFolderButton.setOnClickListener(v -> openFolderChooser());

        Button refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> {
            if (!isLoading && selectedFolderUri != null) {
                displayTxtFiles(selectedFolderUri);
            }
        });

        FloatingActionButton newFileButton = findViewById(R.id.newFileButton);
        newFileButton.setOnClickListener(v -> createNewFile());

        // Initialize the handler and rescan folder
        handler = new Handler(Looper.getMainLooper());
        rescanRunnable = new Runnable() {
            @Override
            public void run() {
                if (selectedFolderUri != null && !isLoading) {
                    displayTxtFiles(selectedFolderUri);
                }
                // Schedule next refresh
                handler.postDelayed(this, REFRESH_INTERVAL_MS);
            }
        };

        // Check for permissions on first launch
        if (checkPermissions()) {
            loadSelectedFolder();
        } else {
            requestPermissions();
        }

        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            HashMap<String, String> selectedFile = (HashMap<String, String>) parent.getItemAtPosition(position);
            String fileName = selectedFile.get("file_name");

            // Log file name and folder URI to verify
            Log.d("MainActivity", "File selected: " + fileName);
            Log.d("MainActivity", "Folder URI: " + selectedFolderUri.toString());

            // Start EditActivity and pass the file name and folder URI
            Intent intent = new Intent(MainActivity.this, EditActivity.class);
            intent.putExtra("file_name", fileName);
            intent.putExtra("folder_uri", selectedFolderUri.toString());
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("MainActivity", "onResume called");

        // Recreate executor if it was shut down
        if (backgroundExecutor == null || backgroundExecutor.isShutdown()) {
            backgroundExecutor = Executors.newSingleThreadExecutor();
        }

        // Start rescanning when the activity resumes (with delay to avoid immediate load)
        if (selectedFolderUri != null) {
            handler.postDelayed(() -> {
                if (!isLoading) {
                    displayTxtFiles(selectedFolderUri);
                }
            }, 500); // Small delay to let UI settle
        }

        // Start periodic refresh
        handler.postDelayed(rescanRunnable, REFRESH_INTERVAL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d("MainActivity", "onPause called");
        // Stop rescanning when the activity pauses
        handler.removeCallbacks(rescanRunnable);

        // Cancel any pending UI updates
        handler.removeCallbacksAndMessages(null);
        mainHandler.removeCallbacksAndMessages(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d("MainActivity", "onDestroy called");

        // Clean up handlers
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }

        // Shutdown executor
        if (backgroundExecutor != null && !backgroundExecutor.isShutdown()) {
            backgroundExecutor.shutdownNow(); // Use shutdownNow() for immediate termination
        }
    }

    private boolean checkPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_CODE_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @Nullable String[] permissions, @Nullable int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults != null && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadSelectedFolder();
            } else {
                Toast.makeText(this, "Permission denied to read external storage.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openFolderChooser() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        startActivityForResult(intent, REQUEST_CODE_OPEN_DIRECTORY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK) {
            if (data != null) {
                selectedFolderUri = data.getData();
                if (selectedFolderUri != null) {
                    saveSelectedFolder(selectedFolderUri);
                    getContentResolver().takePersistableUriPermission(selectedFolderUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    displayTxtFiles(selectedFolderUri);
                    currentDirectoryPath.setText(selectedFolderUri.getPath());
                }
            }
        }
    }

    private void loadSelectedFolder() {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String uriString = sharedPreferences.getString(SELECTED_FOLDER_KEY, null);
        if (uriString != null) {
            selectedFolderUri = Uri.parse(uriString);
            if (isUriAccessible(selectedFolderUri)) {
                displayTxtFiles(selectedFolderUri);
                currentDirectoryPath.setText(selectedFolderUri.getPath());
            } else {
                Toast.makeText(this, "Access to the selected folder is no longer available. Please select it again.", Toast.LENGTH_SHORT).show();
                openFolderChooser();
            }
        }
    }

    private boolean isUriAccessible(Uri uri) {
        try {
            DocumentFile directory = DocumentFile.fromTreeUri(this, uri);
            return directory != null && directory.isDirectory();
        } catch (Exception e) {
            return false;
        }
    }

    private void saveSelectedFolder(Uri uri) {
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SELECTED_FOLDER_KEY, uri.toString());
        editor.apply();
    }

    private void displayTxtFiles(Uri uri) {
        // Prevent multiple concurrent loads
        if (isLoading) {
            Log.d("MainActivity", "Already loading, skipping...");
            return;
        }

        isLoading = true;
        Log.d("MainActivity", "Starting to load files...");

        // Check if executor is available
        if (backgroundExecutor == null || backgroundExecutor.isShutdown()) {
            Log.w("MainActivity", "Executor unavailable, recreating...");
            backgroundExecutor = Executors.newSingleThreadExecutor();
        }

        backgroundExecutor.execute(() -> {
            try {
                ArrayList<HashMap<String, String>> fileList = new ArrayList<>();
                ArrayList<DocumentFile> txtFiles = new ArrayList<>();
                DocumentFile directory = DocumentFile.fromTreeUri(this, uri);

                if (directory != null && directory.isDirectory()) {
                    DocumentFile[] files = directory.listFiles();

                    if (files != null) {
                        for (DocumentFile file : files) {
                            if (file.getName() != null && file.getName().toLowerCase().endsWith(".txt")) {
                                HashMap<String, String> map = new HashMap<>();
                                String fileName = file.getName();
                                long lastModified = file.lastModified();

                                map.put("file_name", fileName);
                                map.put("file_content", "Loading...");
                                map.put("last_modified", String.valueOf(lastModified));
                                fileList.add(map);
                                txtFiles.add(file);
                            }
                        }

                        // Sort both lists together to maintain correspondence
                        for (int i = 0; i < fileList.size() - 1; i++) {
                            for (int j = i + 1; j < fileList.size(); j++) {
                                long time1 = Long.parseLong(fileList.get(i).get("last_modified"));
                                long time2 = Long.parseLong(fileList.get(j).get("last_modified"));
                                if (time1 < time2) { // Newer files first
                                    Collections.swap(fileList, i, j);
                                    Collections.swap(txtFiles, i, j);
                                }
                            }
                        }
                    }
                }

                // Update UI on main thread - check if activity is still valid
                if (!isFinishing() && !isDestroyed()) {
                    mainHandler.post(() -> {
                        try {
                            updateFileListUI(fileList);
                            loadFilePreviewsAsync(fileList, txtFiles);
                        } catch (Exception e) {
                            Log.e("MainActivity", "Error updating UI", e);
                        } finally {
                            isLoading = false;
                        }
                    });
                } else {
                    isLoading = false;
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error in displayTxtFiles", e);
                isLoading = false;
            }
        });
    }

    private void updateFileListUI(ArrayList<HashMap<String, String>> fileList) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        if (fileList.isEmpty()) {
            statusTextView.setVisibility(View.VISIBLE);
        } else {
            statusTextView.setVisibility(View.GONE);
        }

        // Create and set the custom card adapter
        if (fileCardAdapter == null) {
            fileCardAdapter = new FileCardAdapter(this, fileList);
            fileListView.setAdapter(fileCardAdapter);
        } else {
            fileCardAdapter.updateData(fileList);
        }
    }

    private void loadFilePreviewsAsync(ArrayList<HashMap<String, String>> fileList, ArrayList<DocumentFile> txtFiles) {
        if (backgroundExecutor == null || backgroundExecutor.isShutdown()) {
            return;
        }

        backgroundExecutor.execute(() -> {
            try {
                final int BATCH_SIZE = 3; // Reduced batch size for better performance
                for (int i = 0; i < fileList.size() && i < txtFiles.size(); i += BATCH_SIZE) {
                    // Check if we should continue
                    if (isFinishing() || isDestroyed()) {
                        break;
                    }

                    final int startIndex = i;
                    final int endIndex = Math.min(i + BATCH_SIZE, fileList.size());

                    // Process a batch of files
                    for (int j = startIndex; j < endIndex; j++) {
                        if (j >= fileList.size() || j >= txtFiles.size()) break;

                        HashMap<String, String> fileMap = fileList.get(j);
                        DocumentFile file = txtFiles.get(j);

                        String preview = getFirstCharactersFromFile(file, PreviewFirstCharactersFromFile); // Further reduced preview size
                        fileMap.put("file_content", preview);
                    }

                    // Update UI after each batch instead of each file
                    if (!isFinishing() && !isDestroyed()) {
                        mainHandler.post(() -> {
                            try {
                                if (fileCardAdapter != null && !isFinishing() && !isDestroyed()) {
                                    fileCardAdapter.notifyDataSetChanged();
                                }
                            } catch (Exception e) {
                                Log.e("MainActivity", "Error updating adapter", e);
                            }
                        });
                    }

                    // Longer delay between batches to prevent overwhelming
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error in loadFilePreviewsAsync", e);
            }
        });
    }

    private String getFirstCharactersFromFile(DocumentFile file, int n) {
        StringBuilder contentBuilder = new StringBuilder();
        try {
            InputStream inputStream = getContentResolver().openInputStream(file.getUri());
            if (inputStream == null) {
                return "Error reading file";
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            char[] buffer = new char[n];
            int charsRead = reader.read(buffer, 0, n);

            if (charsRead != -1) {
                contentBuilder.append(buffer, 0, charsRead);
            }

            reader.close();
            inputStream.close();
        } catch (Exception e) {
            Log.e("MainActivity", "Error reading file: " + file.getName(), e);
            return "Error reading file";
        }

        return contentBuilder.toString().trim();
    }

    private String getFirstLinesFromFile(DocumentFile file, int n) {
        StringBuilder contentBuilder = new StringBuilder();
        try {
            InputStream inputStream = getContentResolver().openInputStream(file.getUri());
            if (inputStream == null) {
                return "Error reading file";
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < n) {
                contentBuilder.append(line).append("\n");
                lineCount++;
            }
            reader.close();
            inputStream.close();
        } catch (Exception e) {
            Log.e("MainActivity", "Error reading file: " + file.getName(), e);
            return "Error reading file";
        }
        return contentBuilder.toString().trim();
    }

    private void createNewFile() {
        if (selectedFolderUri != null) {
            DocumentFile directory = DocumentFile.fromTreeUri(this, selectedFolderUri);
            if (directory != null && directory.isDirectory()) {
                String timeStamp = new SimpleDateFormat("ddMMyyyyHHmmss", Locale.getDefault()).format(new Date());
                String fileName = timeStamp + ".txt";

                DocumentFile newFile = directory.createFile("text/plain", fileName);

                if (newFile != null) {
                    Intent intent = new Intent(MainActivity.this, EditActivity.class);
                    intent.putExtra("file_name", newFile.getName());
                    intent.putExtra("folder_uri", selectedFolderUri.toString());
                    startActivity(intent);
                } else {
                    Toast.makeText(this, "Failed to create file.", Toast.LENGTH_SHORT).show();
                }
            }
        } else {
            Toast.makeText(this, "No folder selected. Please select a folder first.", Toast.LENGTH_SHORT).show();
        }
    }
}