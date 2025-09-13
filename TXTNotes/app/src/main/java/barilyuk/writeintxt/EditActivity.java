package barilyuk.writeintxt;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.documentfile.provider.DocumentFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

import android.view.View;

import org.mozilla.universalchardet.UniversalDetector;

public class EditActivity extends AppCompatActivity {
    private EditText editText;
    private Button cancelButton;
    private ImageButton saveButton;
    private ImageView deleteButton;
    private String fileName;
    private Uri folderUri;
    private boolean isEdited = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);  // Make sure to create this layout

        editText = findViewById(R.id.editText);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        deleteButton = findViewById(R.id.deleteButton);
        TextView fileNameTextView = findViewById(R.id.fileNameTextView);

        // Get file name and folder URI from the intent
        Intent intent = getIntent();
        Uri uri = intent.getData();

        if (uri != null) {
            // External launch: user tapped a .txt file
            folderUri = uri;
            DocumentFile file = DocumentFile.fromSingleUri(this, uri);
            fileName = (file != null) ? file.getName() : "unknown.txt";
        } else {
            // Internal launch: app passed extras
            fileName = intent.getStringExtra("file_name");
            folderUri = Uri.parse(intent.getStringExtra("folder_uri"));
        }

        // Set the file name to the TextView displayed at the page top
        fileNameTextView.setText(fileName);

        // Load the content of the selected file
        loadFileContent();

        // Set text change listener to detect edits
        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                isEdited = true;
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });

        editText.setVerticalScrollBarEnabled(true);
        editText.setScrollContainer(true);

        saveButton.setOnClickListener(v -> saveFileContent());

        //cancelButton.setOnClickListener(v -> finish()); // Exit without saving
        cancelButton.setOnClickListener(v -> {
            if (isEdited) {
                // Show a confirmation dialog if there are unsaved changes
                new AlertDialog.Builder(EditActivity.this)
                        .setTitle("Exit Without Saving")
                        .setMessage("You have unsaved changes. Exit without saving?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            finish();
                        })
                        .setNegativeButton("No", null)  // Do nothing if 'No' is clicked
                        .show();
            } else {
                finish();
            }
        });

        deleteButton.setOnClickListener(v -> showDeleteConfirmation());
    }

    @Override
    public void onBackPressed() {
        if (isEdited) {
            new AlertDialog.Builder(EditActivity.this)
                    .setTitle("Exit Without Saving")
                    .setMessage("You have unsaved changes. Exit without saving?")
                    .setPositiveButton("Yes", (dialog, which) -> {
                        finish();
                    })
                    .setNegativeButton("No", null)  // Do nothing if 'No' is clicked
                    .show();
        } else {
            super.onBackPressed();  // Call the default back button behavior
        }
    }

    private void loadFileContent() {
        Intent intent = getIntent();
        Uri uri = intent.getData();

        DocumentFile file;
        if (uri != null) {
            // External launch: direct document URI
            file = DocumentFile.fromSingleUri(this, uri);
        } else {
            // Internal launch: tree URI + filename
            DocumentFile directory = DocumentFile.fromTreeUri(this, folderUri);
            if (directory != null) {
                file = directory.findFile(fileName);
            } else {
                file = null;
            }
        }

        if (file != null && file.isFile()) {
            String encoding = "Unknown encoding";
            try (InputStream inputStream = getContentResolver().openInputStream(file.getUri())) {
                // Detect encoding
                UniversalDetector detector = new UniversalDetector(null);
                byte[] buf = new byte[4096];
                int nread = inputStream.read(buf);
                if (nread > 0) {
                    byte[] sample = java.util.Arrays.copyOf(buf, nread);
                    encoding = detectEncodingWithFallback(sample);
                } else {
                    encoding = "UTF-8"; // Default encoding for empty files
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error detecting encoding", Toast.LENGTH_SHORT).show();
            }

            // Show encoding label
            TextView encodingTextView = findViewById(R.id.fileEncodingTextView);
            encodingTextView.setText("Charset: " + encoding);

            // Now read the file using the detected encoding (or UTF-8 fallback)
            try (InputStream inputStream2 = getContentResolver().openInputStream(file.getUri());
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream2, encoding))) {
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                editText.setText(content.toString().trim());
            } catch (Exception e) {
                Toast.makeText(this, "Error loading file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void saveFileContent() {
        if (!isEdited) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = getIntent();
        Uri uri = intent.getData();

        DocumentFile file;
        if (uri != null) {
            // External launch: direct document URI
            file = DocumentFile.fromSingleUri(this, uri);
        } else {
            // Internal launch: tree URI + filename
            DocumentFile directory = DocumentFile.fromTreeUri(this, folderUri);
            if (directory != null) {
                file = directory.findFile(fileName);
            } else {
                file = null;
            }
        }

        if (file != null && file.isFile()) {
            try (OutputStream outputStream = getContentResolver().openOutputStream(file.getUri());
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                writer.write(editText.getText().toString());
                Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show();
                isEdited = false;
                setResult(RESULT_OK);
            } catch (Exception e) {
                Toast.makeText(this, "Error saving file", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Are you sure you want to delete this file?")
                .setPositiveButton("Yes", (dialog, which) -> deleteFile())
                .setNegativeButton("No", null)
                .show();
    }

    private void deleteFile() {
        Intent intent = getIntent();
        Uri uri = intent.getData();

        DocumentFile file;
        if (uri != null) {
            // External launch: direct document URI
            file = DocumentFile.fromSingleUri(this, uri);
        } else {
            // Internal launch: tree URI + filename
            DocumentFile directory = DocumentFile.fromTreeUri(this, folderUri);
            if (directory != null) {
                file = directory.findFile(fileName);
            } else {
                file = null;
            }
        }

        if (file != null && file.isFile()) {
            if (file.delete()) {
                Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, "Error deleting file", Toast.LENGTH_SHORT).show();
            }
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
}
