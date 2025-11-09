package barilyuk.texetescribe;

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

import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

    private static final int REQUEST_STORAGE_PERMISSION = 100;

    private void ensureStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                    || ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                }, REQUEST_STORAGE_PERMISSION);
            }
        }
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);  // Make sure to create this layout
        getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE); //prevents the keyboard from covering the EditText on smaller screens



        editText = findViewById(R.id.editText);
        saveButton = findViewById(R.id.saveButton);
        cancelButton = findViewById(R.id.cancelButton);
        deleteButton = findViewById(R.id.deleteButton);
        TextView fileNameTextView = findViewById(R.id.fileNameTextView);

        // Get file name and folder URI from the intent
        Intent intent = getIntent();
        Uri uri = intent.getData();

        // Take and persist URI permissions if available
        if (uri != null) {
            final int takeFlags = intent.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            try {
                getContentResolver().takePersistableUriPermission(uri, takeFlags);
            } catch (SecurityException e) {
                // ignore if already granted
            }
        }

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

        // Request legacy storage permissions if needed
        ensureStoragePermissions();

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
                        .setTitle(R.string.exit_without_saving)
                        .setMessage(R.string.unsaved_changes)
                        .setPositiveButton(R.string.yes, (dialog, which) -> {
                            finish();
                        })
                        .setNegativeButton(R.string.no, null)  // Do nothing if 'No' is clicked
                        .show();
            } else {
                finish();
            }
        });

        deleteButton.setOnClickListener(v -> showDeleteConfirmation());
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION) {
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, R.string.storage_permission_denied, Toast.LENGTH_LONG).show();
                    return;
                }
            }
            Toast.makeText(this, R.string.storage_permissions_granted, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (isEdited) {
            new AlertDialog.Builder(EditActivity.this)
                    .setTitle(R.string.exit_without_saving)
                    .setMessage(R.string.unsaved_changes)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        finish();
                    })
                    .setNegativeButton(R.string.no, null)  // Do nothing if 'No' is clicked
                    .show();
        } else {
            super.onBackPressed();  // Call the default back button behavior
        }
    }

    private void loadFileContent() {
        Intent intent = getIntent();
        Uri uri = intent.getData();
        java.io.File filePath = null;
        DocumentFile file = null;

        try {
            if (uri != null && "file".equals(uri.getScheme())) {
                filePath = new java.io.File(uri.getPath());
            } else if (uri != null && "content".equals(uri.getScheme())) {
                // Try reading directly for "Open with" URIs
                InputStream directStream = getContentResolver().openInputStream(uri);
                if (directStream != null) {
                    byte[] buf = new byte[4096];
                    int nread = directStream.read(buf);
                    String encoding = detectEncodingWithFallback(java.util.Arrays.copyOf(buf, Math.max(0, nread)));
                    directStream.close();

                    TextView encodingTextView = findViewById(R.id.fileEncodingTextView);
                    encodingTextView.setText(getString(R.string.encoding_label, encoding));

                    InputStream directStream2 = getContentResolver().openInputStream(uri);
                    BufferedReader reader = new BufferedReader(new InputStreamReader(directStream2, encoding));
                    StringBuilder content = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        content.append(line).append("\n");
                    }
                    reader.close();
                    editText.setText(content.toString().trim());
                    return;
                }
            } else {
                DocumentFile directory = DocumentFile.fromTreeUri(this, folderUri);
                if (directory != null) file = directory.findFile(fileName);
            }

            if ((file != null && file.isFile()) || (filePath != null && filePath.isFile())) {
                String encoding = "UTF-8";
                InputStream inputStream;
                if (file != null) inputStream = getContentResolver().openInputStream(file.getUri());
                else inputStream = new java.io.FileInputStream(filePath);

                byte[] buf = new byte[4096];
                int nread = inputStream.read(buf);
                if (nread > 0) encoding = detectEncodingWithFallback(java.util.Arrays.copyOf(buf, nread));
                inputStream.close();

                TextView encodingTextView = findViewById(R.id.fileEncodingTextView);
                encodingTextView.setText(getString(R.string.encoding_label, encoding));

                InputStream inputStream2;
                if (file != null) inputStream2 = getContentResolver().openInputStream(file.getUri());
                else inputStream2 = new java.io.FileInputStream(filePath);

                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream2, encoding));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();
                editText.setText(content.toString().trim());
            }

        } catch (Exception e) {
            Toast.makeText(this, R.string.error_loading_file, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveFileContent() {
        if (!isEdited) {
            Toast.makeText(this, R.string.no_changes_to_save, Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = getIntent();
        Uri uri = intent.getData();
        java.io.File filePath = null;
        DocumentFile file = null;

        try {
            OutputStream outputStream = null;

            if (uri != null && "content".equals(uri.getScheme())) {
                // Handles "Open with" and SAF documents
                outputStream = getContentResolver().openOutputStream(uri, "wt");
            } else if (uri != null && "file".equals(uri.getScheme())) {
                filePath = new java.io.File(uri.getPath());
                outputStream = new java.io.FileOutputStream(filePath, false);
            } else {
                DocumentFile directory = DocumentFile.fromTreeUri(this, folderUri);
                if (directory != null) file = directory.findFile(fileName);
                if (file != null) outputStream = getContentResolver().openOutputStream(file.getUri(), "wt");
            }

            if (outputStream == null) {
                Toast.makeText(this, R.string.file_not_found, Toast.LENGTH_SHORT).show();
                return;
            }

            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream));
            writer.write(editText.getText().toString());
            writer.close();

            Toast.makeText(this, R.string.file_saved, Toast.LENGTH_SHORT).show();
            isEdited = false;
            setResult(RESULT_OK);
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_saving_file, Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_file)
                .setMessage(R.string.delete_confirmation)
                .setPositiveButton(R.string.yes, (dialog, which) -> deleteFile())
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void deleteFile() {
        Intent intent = getIntent();
        Uri uri = intent.getData();
        java.io.File filePath = null;
        DocumentFile file = null;

        try {
            if (uri != null && "content".equals(uri.getScheme())) {
                file = DocumentFile.fromSingleUri(this, uri);
            } else if (uri != null && "file".equals(uri.getScheme())) {
                filePath = new java.io.File(uri.getPath());
            } else {
                DocumentFile directory = DocumentFile.fromTreeUri(this, folderUri);
                if (directory != null) file = directory.findFile(fileName);
            }

            boolean deleted = false;
            if (file != null && file.isFile()) {
                deleted = file.delete();
            } else if (filePath != null && filePath.isFile()) {
                deleted = filePath.delete();
            }

            if (deleted) {
                Toast.makeText(this, R.string.file_deleted, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            } else {
                Toast.makeText(this, R.string.error_deleting_file, Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_deleting_file, Toast.LENGTH_SHORT).show();
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
