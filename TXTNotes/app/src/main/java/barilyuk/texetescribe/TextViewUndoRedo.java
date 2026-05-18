package barilyuk.texetescribe;

import android.text.Editable;
import android.text.TextWatcher;
import android.widget.EditText;

import java.util.ArrayDeque;

public class TextViewUndoRedo {

    private final EditText editText;

    private final ArrayDeque<EditItem> undoStack = new ArrayDeque<>();
    private final ArrayDeque<EditItem> redoStack = new ArrayDeque<>();

    private static final int MAX_HISTORY = 500;

    private boolean isUndoOrRedo = false;

    private int changeStart = 0;

    private String beforeChange = "";
    private String afterChange = "";

    public TextViewUndoRedo(EditText editText) {

        this.editText = editText;

        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                if (isUndoOrRedo) {
                    return;
                }

                changeStart = start;

                int end = Math.min(start + count, s.length());

                if (start >= 0 && end >= start && end <= s.length()) {
                    beforeChange = s.subSequence(start, end).toString();
                } else {
                    beforeChange = "";
                }
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

                if (isUndoOrRedo) {
                    return;
                }

                int end = Math.min(start + count, s.length());

                if (start >= 0 && end >= start && end <= s.length()) {
                    afterChange = s.subSequence(start, end).toString();
                } else {
                    afterChange = "";
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

                if (isUndoOrRedo) {
                    return;
                }

                if (beforeChange.equals(afterChange)) {
                    return;
                }

                EditItem item = new EditItem(
                        changeStart,
                        beforeChange,
                        afterChange
                );

                undoStack.push(item);

                while (undoStack.size() > MAX_HISTORY) {
                    undoStack.removeLast();
                }

                redoStack.clear();
            }
        });
    }

    public void undo() {

        if (undoStack.isEmpty()) {
            return;
        }

        EditItem item = undoStack.pop();

        Editable editable = editText.getText();

        isUndoOrRedo = true;

        try {

            int start = Math.max(0, item.start);

            int end = Math.min(
                    start + item.after.length(),
                    editable.length()
            );

            editable.replace(
                    start,
                    end,
                    item.before
            );

            redoStack.push(item);

            int cursor = start + item.before.length();

            if (cursor >= 0 && cursor <= editable.length()) {
                editText.setSelection(cursor);
            }

        } catch (Exception e) {
            // keep safe
        }

        isUndoOrRedo = false;
    }

    public void redo() {

        if (redoStack.isEmpty()) {
            return;
        }

        EditItem item = redoStack.pop();

        Editable editable = editText.getText();

        isUndoOrRedo = true;

        try {

            int start = Math.max(0, item.start);

            int end = Math.min(
                    start + item.before.length(),
                    editable.length()
            );

            editable.replace(
                    start,
                    end,
                    item.after
            );

            undoStack.push(item);

            int cursor = start + item.after.length();

            if (cursor >= 0 && cursor <= editable.length()) {
                editText.setSelection(cursor);
            }

        } catch (Exception e) {
            // keep safe
        }

        isUndoOrRedo = false;
    }

    public void clearHistory() {

        undoStack.clear();
        redoStack.clear();
    }

    private static class EditItem {

        private final int start;
        private final String before;
        private final String after;

        EditItem(int start, String before, String after) {

            this.start = start;
            this.before = before;
            this.after = after;
        }
    }
}