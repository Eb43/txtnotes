package com.txtnotes;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

class FileCardAdapter extends BaseAdapter {
    private Context context;
    private ArrayList<HashMap<String, String>> fileList;
    private LayoutInflater inflater;
    private SimpleDateFormat dateFormat;

    public FileCardAdapter(Context context, ArrayList<HashMap<String, String>> fileList) {
        this.context = context;
        this.fileList = fileList;
        this.inflater = LayoutInflater.from(context);
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    }

    @Override
    public int getCount() {
        return fileList.size();
    }

    @Override
    public Object getItem(int position) {
        return fileList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        ViewHolder holder;

        if (convertView == null) {
            convertView = inflater.inflate(R.layout.file_item_card, parent, false);
            holder = new ViewHolder();
            holder.fileNameHeader = convertView.findViewById(R.id.fileNameHeader);
            holder.filePreview = convertView.findViewById(R.id.filePreview);
            holder.lastModified = convertView.findViewById(R.id.lastModified);
            convertView.setTag(holder);
        } else {
            holder = (ViewHolder) convertView.getTag();
        }

        HashMap<String, String> fileData = fileList.get(position);

        // Set file name in header - cache to avoid repeated string operations
        String fileName = fileData.get("file_name");
        if (fileName != null && !fileName.equals(holder.lastFileName)) {
            holder.fileNameHeader.setText(fileName);
            holder.lastFileName = fileName;
        }

        // Set file preview content - only update if changed
        String content = fileData.get("file_content");
        if (content != null && !content.equals(holder.lastContent)) {
            if (content.equals("Loading...")) {
                holder.filePreview.setText("Loading preview...");
                holder.filePreview.setTextColor(0xFF999999);
            } else if (content.equals("Error reading file")) {
                holder.filePreview.setText("⚠️ Error reading file");
                holder.filePreview.setTextColor(0xFFFF6B6B);
            } else {
                holder.filePreview.setText(content.isEmpty() ? "📝 Empty file" : content);
                holder.filePreview.setTextColor(0xFF666666);
            }
            holder.lastContent = content;
        }

        // Set last modified date - cache formatted dates
        String lastModifiedStr = fileData.get("last_modified");
        if (lastModifiedStr != null && !lastModifiedStr.equals(holder.lastModifiedStr)) {
            if (!lastModifiedStr.equals("0")) {
                try {
                    long lastModified = Long.parseLong(lastModifiedStr);
                    Date date = new Date(lastModified);
                    holder.lastModified.setText("📅 " + dateFormat.format(date));
                } catch (NumberFormatException e) {
                    holder.lastModified.setText("📅 Unknown date");
                }
            } else {
                holder.lastModified.setText("📅 Unknown date");
            }
            holder.lastModifiedStr = lastModifiedStr;
        }

        return convertView;
    }

    static class ViewHolder {
        TextView fileNameHeader;
        TextView filePreview;
        TextView lastModified;
        // Cache last values to avoid unnecessary updates
        String lastFileName;
        String lastContent;
        String lastModifiedStr;
    }

    // Method to update the adapter data
    public void updateData(ArrayList<HashMap<String, String>> newFileList) {
        this.fileList = newFileList;
        notifyDataSetChanged();
    }
}