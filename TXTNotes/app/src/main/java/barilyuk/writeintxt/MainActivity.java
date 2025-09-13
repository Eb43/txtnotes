package barilyuk.writeintxt;

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

import org.mozilla.universalchardet.UniversalDetector;

import java.util.List;
import java.util.concurrent.Future;


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
    private FloatingActionButton refreshButton;
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
        backgroundExecutor = Executors.newFixedThreadPool(8); // 8 threads

        fileListView = findViewById(R.id.fileListView);
        statusTextView = findViewById(R.id.statusTextView);
        currentDirectoryPath = findViewById(R.id.currentDirectoryPath);

        Button selectFolderButton = findViewById(R.id.selectFolderButton);
        selectFolderButton.setOnClickListener(v -> openFolderChooser());

        refreshButton = findViewById(R.id.refreshButton);
        refreshButton.setOnClickListener(v -> {
            if (!isLoading && selectedFolderUri != null) {
                Toast.makeText(MainActivity.this, "Rescanning files...", Toast.LENGTH_SHORT).show();
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
            startActivityForResult(intent, 2001);  // <- use requestCode 2001
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("MainActivity", "onResume called");

        // Recreate executor if it was shut down
        if (backgroundExecutor == null || backgroundExecutor.isShutdown()) {
            backgroundExecutor = Executors.newFixedThreadPool(8); // 8 threads
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

        if (requestCode == 2001 && resultCode == RESULT_OK) {
            // Trigger the refresh button to reuse existing logic
            refreshButton.performClick();
        }

        // Existing folder chooser code stays unchanged
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
            backgroundExecutor = Executors.newFixedThreadPool(8); // 8 threads
        }

        backgroundExecutor.execute(() -> {
            try {
                ArrayList<HashMap<String, String>> fileList = new ArrayList<>();
                ArrayList<DocumentFile> txtFiles = new ArrayList<>();
                DocumentFile directory = DocumentFile.fromTreeUri(this, uri);

                if (directory != null && directory.isDirectory()) {
                    DocumentFile[] files = directory.listFiles();

                    if (files != null) {
                        // Pre-populate file list with basic info (fast operation)
// First pass - collect .txt files without metadata
                        ArrayList<DocumentFile> tempTxtFiles = new ArrayList<>();
                        for (DocumentFile file : files) {
                            if (file.getName() != null && file.getName().toLowerCase().endsWith(".txt")) {
                                tempTxtFiles.add(file);
                            }
                        }

// Parallel metadata collection
                        List<Future<FileMetadata>> futures = new ArrayList<>();
                        for (DocumentFile file : tempTxtFiles) {
                            futures.add(backgroundExecutor.submit(() -> {
                                return new FileMetadata(file.getName(), file.lastModified(), file);
                            }));
                        }

// Collect results
                        for (Future<FileMetadata> future : futures) {
                            try {
                                FileMetadata metadata = future.get(); // Wait for completion
                                HashMap<String, String> map = new HashMap<>();
                                map.put("file_name", metadata.fileName);
                                map.put("file_content", "Loading...");
                                map.put("last_modified", String.valueOf(metadata.lastModified));
                                map.put("file_encoding", "UTF-8");
                                fileList.add(map);
                                txtFiles.add(metadata.documentFile);
                            } catch (Exception e) {
                                Log.e("MainActivity", "Error getting file metadata", e);
                            }
                        }

                        // Sort once upfront
// Create paired list for sorting both together
                        ArrayList<Integer> indices = new ArrayList<>();
                        for (int i = 0; i < fileList.size(); i++) {
                            indices.add(i);
                        }

// Sort indices based on modification time
                        Collections.sort(indices, (i, j) -> {
                            long time1 = Long.parseLong(fileList.get(i).get("last_modified"));
                            long time2 = Long.parseLong(fileList.get(j).get("last_modified"));
                            return Long.compare(time2, time1); // Newest first
                        });

// Reorder both lists based on sorted indices
                        ArrayList<HashMap<String, String>> sortedFileList = new ArrayList<>();
                        ArrayList<DocumentFile> sortedTxtFiles = new ArrayList<>();
                        for (int index : indices) {
                            sortedFileList.add(fileList.get(index));
                            sortedTxtFiles.add(txtFiles.get(index));
                        }

                        fileList.clear();
                        fileList.addAll(sortedFileList);
                        txtFiles.clear();
                        txtFiles.addAll(sortedTxtFiles);
                    }
                }

                // Show UI immediately with file list
                if (!isFinishing() && !isDestroyed()) {
                    mainHandler.post(() -> {
                        try {
                            updateFileListUI(fileList);
                            // Start parallel preview loading
                            loadFilePreviewsParallel(fileList, txtFiles);
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

    private void loadFilePreviewsParallel(ArrayList<HashMap<String, String>> fileList, ArrayList<DocumentFile> txtFiles) {
        if (backgroundExecutor == null || backgroundExecutor.isShutdown()) {
            return;
        }

        final int BATCH_SIZE = 10; // Larger batches for parallel processing
        final int totalFiles = Math.min(fileList.size(), txtFiles.size());

        for (int i = 0; i < totalFiles; i += BATCH_SIZE) {
            final int startIndex = i;
            final int endIndex = Math.min(i + BATCH_SIZE, totalFiles);

            // Submit each batch as a separate task for parallel execution
            backgroundExecutor.execute(() -> {
                try {
                    boolean hasUpdates = false;

                    for (int j = startIndex; j < endIndex; j++) {
                        if (j >= fileList.size() || j >= txtFiles.size()) break;
                        if (isFinishing() || isDestroyed()) return;

                        HashMap<String, String> fileMap = fileList.get(j);
                        DocumentFile file = txtFiles.get(j);

                        // Process encoding and preview in one pass
                        String[] result = getFilePreviewWithEncoding(file, PreviewFirstCharactersFromFile);
                        if (result != null) {
                            fileMap.put("file_content", result[0]); // preview
                            fileMap.put("file_encoding", result[1]); // encoding
                            hasUpdates = true;
                        }
                    }

                    // Update UI after processing the entire batch
                    if (hasUpdates && !isFinishing() && !isDestroyed()) {
                        mainHandler.post(() -> {
                            try {
                                if (fileCardAdapter != null) {
                                    fileCardAdapter.notifyDataSetChanged();
                                }
                            } catch (Exception e) {
                                Log.e("MainActivity", "Error updating adapter", e);
                            }
                        });
                    }
                } catch (Exception e) {
                    Log.e("MainActivity", "Error in parallel preview loading", e);
                }
            });
        }
    }

    private String[] getFilePreviewWithEncoding(DocumentFile file, int previewLength) {
        try {
            // Read sample for encoding detection
            byte[] sample = readFileSample(file, 4096);
            String encoding = "UTF-8";
            if (sample.length > 0) {
                encoding = detectEncodingWithFallback(sample);
            }

            // Now read preview with detected encoding
            try (InputStream is = getContentResolver().openInputStream(file.getUri());
                 InputStreamReader reader = new InputStreamReader(is, encoding);
                 BufferedReader br = new BufferedReader(reader)) {

                char[] chars = new char[previewLength];
                int read = br.read(chars, 0, previewLength);
                if (read <= 0) return new String[]{"", encoding};

                String preview = new String(chars, 0, read).trim();
                return new String[]{preview, encoding};
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error reading file: " + file.getName(), e);
            return new String[]{"Error reading file", "UTF-8"};
        }
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

    // --- Encoding detection with fallback ---

    private String detectEncodingWithFallback(byte[] data) {
        UniversalDetector detector = new UniversalDetector(null);
        detector.handleData(data, 0, data.length);
        detector.dataEnd();
        String detectedCharset = detector.getDetectedCharset();
        detector.reset();

        if (detectedCharset == null) {
            detectedCharset = "UTF-8"; // safe default
        }

        // Stricter ISO-8859-8 → Windows-1251 check
        if ("ISO-8859-8".equalsIgnoreCase(detectedCharset)) {
            try {
                String sampleText1251 = new String(data, "windows-1251");
                if (containsCyrillic(sampleText1251)) {
                    detectedCharset = "windows-1251"; // force Cyrillic
                } else {
                    detectedCharset = "ISO-8859-8"; // keep Hebrew if no Cyrillic letters
                }
            } catch (Exception e) {
                // fallback safely
                detectedCharset = "ISO-8859-8";
            }
        }

        // Prefer UTF-8 if round-trips correctly
        if (!"UTF-8".equalsIgnoreCase(detectedCharset) && looksLikeUTF8(data)) {
            detectedCharset = "UTF-8";
        }

        return detectedCharset;
    }

    private boolean containsCyrillic(String text) {
        for (char c : text.toCharArray()) {
            if (c >= 0x0400 && c <= 0x04FF) {
                return true;
            }
        }
        return false;
    }

    private boolean looksLikeUTF8(byte[] data) {
        try {
            String utf8 = new String(data, java.nio.charset.StandardCharsets.UTF_8);
            byte[] back = utf8.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return java.util.Arrays.equals(data, back);
        } catch (Exception e) {
            return false;
        }
    }

    private static class FileMetadata {
        String fileName;
        long lastModified;
        DocumentFile documentFile;

        FileMetadata(String fileName, long lastModified, DocumentFile documentFile) {
            this.fileName = fileName;
            this.lastModified = lastModified;
            this.documentFile = documentFile;
        }
    }
    private byte[] readFileSample(DocumentFile file, int maxBytes) {
        try (InputStream inputStream = getContentResolver().openInputStream(file.getUri())) {
            if (inputStream == null) return new byte[0];

            byte[] buffer = new byte[maxBytes];
            int totalRead = 0;
            int read;
            while (totalRead < maxBytes && (read = inputStream.read(buffer, totalRead, maxBytes - totalRead)) != -1) {
                totalRead += read;
            }

            if (totalRead < maxBytes) {
                byte[] trimmed = new byte[totalRead];
                System.arraycopy(buffer, 0, trimmed, 0, totalRead);
                return trimmed;
            }

            return buffer;
        } catch (Exception e) {
            Log.e("MainActivity", "Error reading file sample: " + file.getName(), e);
            return new byte[0];
        }
    }
}