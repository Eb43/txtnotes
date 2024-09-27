package com.txtnotes;

import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
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
        fileName = intent.getStringExtra("file_name");
        folderUri = Uri.parse(intent.getStringExtra("folder_uri"));

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
        DocumentFile directory = DocumentFile.fromTreeUri(this, folderUri);
        if (directory != null) {
            DocumentFile file = directory.findFile(fileName);
            if (file != null && file.isFile()) {
                try (InputStream inputStream = getContentResolver().openInputStream(file.getUri());
                     BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
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
    }

    private void saveFileContent() {
        if (!isEdited) {
            Toast.makeText(this, "No changes to save", Toast.LENGTH_SHORT).show();
            return;
        }

        DocumentFile directory = DocumentFile.fromTreeUri(this, folderUri);
        if (directory != null) {
            DocumentFile file = directory.findFile(fileName);
            if (file != null && file.isFile()) {
                try (OutputStream outputStream = getContentResolver().openOutputStream(file.getUri());
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream))) {
                    writer.write(editText.getText().toString());
                    Toast.makeText(this, "File saved", Toast.LENGTH_SHORT).show();
                    isEdited = false;
                    // finish();  // Close the activity after saving
                } catch (Exception e) {
                    Toast.makeText(this, "Error saving file", Toast.LENGTH_SHORT).show();
                }
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
        DocumentFile directory = DocumentFile.fromTreeUri(this, folderUri);
        if (directory != null) {
            DocumentFile file = directory.findFile(fileName);
            if (file != null && file.isFile()) {
                if (file.delete()) {
                    Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show();
                    finish();  // Close the activity after deletion
                } else {
                    Toast.makeText(this, "Error deleting file", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }
}
