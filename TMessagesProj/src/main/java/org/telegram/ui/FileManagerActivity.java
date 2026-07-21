package org.telegram.ui;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.Components.MediaActivity;

public class FileManagerActivity extends AppCompatActivity {

    private Spinner mediaTypeSpinner;
    private Spinner chatTypeSpinner;
    private Spinner downloadFilterSpinner;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(org.telegram.messenger.R.layout.activity_file_manager);
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open File Manager", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        mediaTypeSpinner = findViewById(R.id.fm_media_type_spinner);
        chatTypeSpinner = findViewById(R.id.fm_chat_type_spinner);
        downloadFilterSpinner = findViewById(R.id.fm_download_filter_spinner);

        ArrayAdapter<String> mediaTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[] {
                "Photos & Videos",
                "Files",
                "Audio",
                "Links"
        });
        mediaTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mediaTypeSpinner.setAdapter(mediaTypeAdapter);

        ArrayAdapter<String> chatTypeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[] {
                "All chats",
                "Private chats",
                "Groups",
                "Channels"
        });
        chatTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        chatTypeSpinner.setAdapter(chatTypeAdapter);

        ArrayAdapter<String> downloadFilterAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[] {
                "All",
                "Downloaded",
                "Pending"
        });
        downloadFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        downloadFilterSpinner.setAdapter(downloadFilterAdapter);

        Button refresh = findViewById(R.id.fm_refresh_button);
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    openMediaScreen();
                } catch (Exception ex) {
                    Toast.makeText(FileManagerActivity.this, "Refresh failed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button goToMessage = findViewById(R.id.fm_gotomessage_button);
        goToMessage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    promptAndOpenMessage();
                } catch (Exception ex) {
                    Toast.makeText(FileManagerActivity.this, "Action failed", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void openMediaScreen() {
        if (LaunchActivity.instance == null) {
            Toast.makeText(this, "Unable to open media view", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle args = new Bundle();
        args.putLong("dialog_id", UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());
        args.putInt("type", MediaActivity.TYPE_MEDIA);
        args.putInt("start_from", getInitialTab());
        LaunchActivity.instance.presentFragment(new MediaActivity(args, null));
        finish();
    }

    private void promptAndOpenMessage() {
        if (LaunchActivity.instance == null) {
            Toast.makeText(this, "Unable to open message", Toast.LENGTH_SHORT).show();
            return;
        }

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setHint("Message ID");

        new AlertDialog.Builder(this)
                .setTitle("Go to message")
                .setView(input)
                .setPositiveButton("Open", (dialog, which) -> {
                    try {
                        int messageId = Integer.parseInt(input.getText().toString().trim());
                        if (messageId <= 0) {
                            Toast.makeText(this, "Enter a valid message ID", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        long dialogId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
                        LaunchActivity.instance.openMessage(dialogId, messageId, null, null, 0, 0, null, null);
                        finish();
                    } catch (Exception e) {
                        Toast.makeText(this, "Unable to open message", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int getInitialTab() {
        switch (mediaTypeSpinner.getSelectedItemPosition()) {
            case 1:
                return org.telegram.ui.Components.SharedMediaLayout.TAB_FILES;
            case 2:
                return org.telegram.ui.Components.SharedMediaLayout.TAB_AUDIO;
            case 3:
                return org.telegram.ui.Components.SharedMediaLayout.TAB_LINKS;
            default:
                return org.telegram.ui.Components.SharedMediaLayout.TAB_PHOTOVIDEO;
        }
    }
}
