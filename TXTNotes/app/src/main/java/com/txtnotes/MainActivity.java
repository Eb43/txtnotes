package com.txtnotes;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
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


import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;
    private static final String PREFS_NAME = "TXTNotesPrefs";
    private static final String SELECTED_FOLDER_KEY = "selectedFolder";
    private static final int REQUEST_CODE_PERMISSIONS = 2;
    private static final long REFRESH_INTERVAL_MS = 60000; // 60 seconds

    private TextView statusTextView;
    private TextView currentDirectoryPath;
    private ListView fileListView;
    private Uri selectedFolderUri;
    private Handler handler;
    private Runnable rescanRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fileListView = findViewById(R.id.fileListView);
        statusTextView = findViewById(R.id.statusTextView);
        currentDirectoryPath = findViewById(R.id.currentDirectoryPath);

        Button selectFolderButton = findViewById(R.id.selectFolderButton);
        selectFolderButton.setOnClickListener(v -> openFolderChooser());

        Button refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> handler.post(rescanRunnable));

        FloatingActionButton newFileButton = findViewById(R.id.newFileButton);
        newFileButton.setOnClickListener(v -> createNewFile());

        // Initialize the handler and rescan folder
        handler = new Handler();
        rescanRunnable = new Runnable() {
            @Override
            public void run() {
                if (selectedFolderUri != null) {
                    displayTxtFiles(selectedFolderUri); // Rescan the selected directory
                }
                // handler.postDelayed(this, REFRESH_INTERVAL_MS); // Reschedule every 5 seconds
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
        // Start rescanning when the activity resumes
        handler.post(rescanRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop rescanning when the activity pauses
        handler.removeCallbacks(rescanRunnable);
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
                loadSelectedFolder(); // Load the selected folder if permission is granted
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
        ArrayList<HashMap<String, String>> fileList = new ArrayList<>();
        DocumentFile directory = DocumentFile.fromTreeUri(this, uri);

        if (directory != null && directory.isDirectory()) {
            DocumentFile[] files = directory.listFiles();

            if (files != null) {
                ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
                List<Future<HashMap<String, String>>> futures = new ArrayList<>();

                for (DocumentFile file : files) {
                    if (file.getName() != null && file.getName().toLowerCase().endsWith(".txt")) {
                        Future<HashMap<String, String>> future = executor.submit(() -> {
                            HashMap<String, String> map = new HashMap<>();
                            String fileName = file.getName();
                            long lastModified = file.lastModified();

                            map.put("file_name", fileName);

                            // Read first 200 characters in background thread
                            String fileContentPreview = getFirstCharactersFromFile(file, 200);
                            map.put("file_content", fileContentPreview);

                            map.put("last_modified", String.valueOf(lastModified));

                            return map;
                        });

                        futures.add(future);
                    }
                }

                for (Future<HashMap<String, String>> future : futures) {
                    try {
                        fileList.add(future.get());
                    } catch (InterruptedException | ExecutionException e) {
                        e.printStackTrace();
                    }
                }

                executor.shutdown();

                // Sort the fileList by last modified date in descending order (newest first)
                // Parallel sorting using parallelStream
                fileList = fileList.parallelStream()
                        .sorted((file1, file2) -> {
                            long time1 = Long.parseLong(file1.get("last_modified"));
                            long time2 = Long.parseLong(file2.get("last_modified"));
                            return Long.compare(time2, time1);  // Newer files first
                        })
                        .collect(Collectors.toCollection(ArrayList::new));
            }
        }

        if (fileList.isEmpty()) {
            statusTextView.setVisibility(View.VISIBLE);
            // statusTextView.setText("No TXT files found in the selected directory.");
        } else {
            statusTextView.setVisibility(View.GONE);
        }

        // Update SimpleAdapter to handle both file_name and file_content
        SimpleAdapter adapter = new SimpleAdapter(this, fileList,
                android.R.layout.simple_list_item_2,
                new String[]{"file_name", "file_content"},
                new int[]{android.R.id.text1, android.R.id.text2});
        fileListView.setAdapter(adapter);
    }

    private String getFirstCharactersFromFile(DocumentFile file, int n) {
        StringBuilder contentBuilder = new StringBuilder();
        try {
            InputStream inputStream = getContentResolver().openInputStream(file.getUri());
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            // Create a char array to hold up to the first `n` characters
            char[] buffer = new char[n];

            // Read up to `n` characters into the buffer
            int charsRead = reader.read(buffer, 0, n);

            // Append the characters to the StringBuilder
            if (charsRead != -1) {
                contentBuilder.append(buffer, 0, charsRead);
            }

            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error reading file";
        }

        return contentBuilder.toString().trim(); // Remove any trailing whitespace
    }

    // Method to read the first `n` lines from a TXT file
    private String getFirstLinesFromFile(DocumentFile file, int n) {
        StringBuilder contentBuilder = new StringBuilder();
        try {
            InputStream inputStream = getContentResolver().openInputStream(file.getUri());
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));

            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null && lineCount < n) {
                contentBuilder.append(line).append("\n");
                lineCount++;
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error reading file";
        }
        return contentBuilder.toString().trim(); // Remove trailing newline
    }

    private void createNewFile() {
        if (selectedFolderUri != null) {
            DocumentFile directory = DocumentFile.fromTreeUri(this, selectedFolderUri);
            if (directory != null && directory.isDirectory()) {
                // Generate the current timestamp for the file name
                String timeStamp = new SimpleDateFormat("ddMMyyyyHHmmss", Locale.getDefault()).format(new Date());
                String fileName = timeStamp + ".txt";

                // Create the new file in the selected directory
                DocumentFile newFile = directory.createFile("text/plain", fileName);

                if (newFile != null) {
                    // Open EditActivity to edit the new file
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