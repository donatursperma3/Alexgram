package org.telegram.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.FileLog;
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.SharedMediaLayout;
import tw.nekomimi.nekogram.NekoConfig;

public class FileManagerActivity extends AppCompatActivity {

    private static final int ITEM_TYPE_PHOTO = 0;
    private static final int ITEM_TYPE_FILE = 1;
    private static final int ITEM_TYPE_AUDIO = 2;
    private static final int ITEM_TYPE_LINK = 3;

    private final String[] mediaLabels = {
            "Photos",
            "Files",
            "Voice",
            "Links",
            "GIFs",
            "Polls",
            "Stories"
    };
    private final int[] mediaTabs = {
            SharedMediaLayout.TAB_PHOTOVIDEO,
            SharedMediaLayout.TAB_FILES,
            SharedMediaLayout.TAB_VOICE,
            SharedMediaLayout.TAB_LINKS,
            SharedMediaLayout.TAB_GIF,
            SharedMediaLayout.TAB_POLL,
            SharedMediaLayout.TAB_STORIES
    };
    private final String[] chatLabels = {"All chats", "Private", "Groups", "Channels"};
    private final String[] statusLabels = {"All", "Downloaded", "Pending"};
    private final String[] itemLabels = {
            "IMG-001.jpg",
            "Document.pdf",
            "Voice note.m4a",
            "IMG-002.jpg",
            "Link • shared from chat"
    };
    private final int[] itemTypes = {
            ITEM_TYPE_PHOTO,
            ITEM_TYPE_FILE,
            ITEM_TYPE_AUDIO,
            ITEM_TYPE_PHOTO,
            ITEM_TYPE_LINK
    };
    private final long[] itemSizes = {
            2_400_000L,
            180 * 1024L,
            1_100_000L,
            3_700_000L,
            0L
    };

    private TextView[] mediaChips;
    private TextView[] chatChips;
    private TextView[] statusChips;
    private LinearLayout mediaChipContainer;
    private Toolbar toolbar;
    private TextView summaryText;
    private TextView selectionStatus;
    private Button selectSingleButton;
    private Button selectRangeButton;
    private Button forwardButton;
    private Button detailButton;
    private Button unselectButton;
    private final List<TextView> itemViews = new ArrayList<>();
    private final Set<Integer> selectedItems = new LinkedHashSet<>();
    private boolean selectionMode = false;
    private boolean rangeSelectionMode = false;
    private int selectionAnchorIndex = -1;

    private int selectedMediaType = 0;
    private int selectedChatType = 0;
    private int selectedStatus = 0;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_file_manager);

            toolbar = findViewById(R.id.fm_toolbar);
            if (toolbar != null) {
                setSupportActionBar(toolbar);
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                    getSupportActionBar().setDisplayShowHomeEnabled(true);
                    getSupportActionBar().setTitle("File Manager");
                }
            }
            summaryText = findViewById(R.id.fm_selection_summary);
            selectionStatus = findViewById(R.id.fm_selection_status);
            selectSingleButton = findViewById(R.id.fm_select_single_button);
            selectRangeButton = findViewById(R.id.fm_select_range_button);
            forwardButton = findViewById(R.id.fm_forward_button);
            detailButton = findViewById(R.id.fm_detail_button);
            unselectButton = findViewById(R.id.fm_unselect_button);

            mediaChipContainer = findViewById(R.id.fm_media_chip_container);
            try {
                buildMediaChips();
            } catch (Exception e) {
                FileLog.e("FileManagerActivity", e);
            }
            chatChips = new TextView[] {
                    findViewById(R.id.fm_chat_chip_all),
                    findViewById(R.id.fm_chat_chip_private),
                    findViewById(R.id.fm_chat_chip_groups),
                    findViewById(R.id.fm_chat_chip_channels)
            };
            statusChips = new TextView[] {
                    findViewById(R.id.fm_status_chip_all),
                    findViewById(R.id.fm_status_chip_downloaded),
                    findViewById(R.id.fm_status_chip_pending)
            };

            for (int i = 0; i < mediaChips.length; i++) {
                final int index = i;
                TextView chip = mediaChips[i];
                if (chip != null) {
                    chip.setOnClickListener(v -> {
                        selectedMediaType = index;
                        updateMediaSelection();
                    });
                }
            }

            for (int i = 0; i < chatChips.length; i++) {
                final int index = i;
                TextView chip = chatChips[i];
                if (chip != null) {
                    chip.setOnClickListener(v -> {
                        selectedChatType = index;
                        updateChatSelection();
                    });
                }
            }

            for (int i = 0; i < statusChips.length; i++) {
                final int index = i;
                TextView chip = statusChips[i];
                if (chip != null) {
                    chip.setOnClickListener(v -> {
                        selectedStatus = index;
                        updateStatusSelection();
                    });
                }
            }

            try {
                int[] itemIds = {
                        R.id.fm_item_0,
                        R.id.fm_item_1,
                        R.id.fm_item_2,
                        R.id.fm_item_3,
                        R.id.fm_item_4
                };
                for (int itemId : itemIds) {
                    TextView item = findViewById(itemId);
                    if (item != null) {
                        itemViews.add(item);
                    }
                }
            } catch (Exception e) {
                FileLog.e("FileManagerActivity", e);
            }

            for (int i = 0; i < itemViews.size(); i++) {
                final int index = i;
                TextView itemView = itemViews.get(i);
                if (itemView != null) {
                    itemView.setOnClickListener(v -> toggleItemSelection(index));
                }
            }

            selectSingleButton.setOnClickListener(v -> {
                selectionMode = true;
                rangeSelectionMode = false;
                selectionAnchorIndex = 0;
                selectedItems.clear();
                selectedItems.add(0);
                updateSelectionUi();
            });

            selectRangeButton.setOnClickListener(v -> {
                selectionMode = true;
                rangeSelectionMode = true;
                selectionAnchorIndex = 0;
                selectedItems.clear();
                selectedItems.addAll(Arrays.asList(0, 1, 2));
                updateSelectionUi();
            });

            forwardButton.setOnClickListener(v -> {
                if (selectedItems.isEmpty()) {
                    Toast.makeText(this, "Select at least one item to forward", Toast.LENGTH_SHORT).show();
                } else {
                    openForwardTargetSelector();
                }
            });

            detailButton.setOnClickListener(v -> {
                if (selectedItems.isEmpty()) {
                    Toast.makeText(this, "Select an item to view details", Toast.LENGTH_SHORT).show();
                } else {
                    showDetailDialog();
                }
            });

            unselectButton.setOnClickListener(v -> {
                selectionMode = false;
                rangeSelectionMode = false;
                selectionAnchorIndex = -1;
                selectedItems.clear();
                updateSelectionUi();
            });

            Button refresh = findViewById(R.id.fm_refresh_button);
            refresh.setOnClickListener(v -> {
                try {
                    openMediaScreen();
                } catch (Exception ex) {
                    Toast.makeText(FileManagerActivity.this, "Unable to open media view", Toast.LENGTH_SHORT).show();
                }
            });

            Button goToMessage = findViewById(R.id.fm_gotomessage_button);
            goToMessage.setOnClickListener(v -> {
                try {
                    promptAndOpenMessage();
                } catch (Exception ex) {
                    Toast.makeText(FileManagerActivity.this, "Action failed", Toast.LENGTH_SHORT).show();
                }
            });

            updateMediaSelection();
            updateChatSelection();
            updateStatusSelection();
            refreshItemLabels();
            updateSelectionUi();
        } catch (Exception e) {
            FileLog.e("FileManagerActivity", e);
            Toast.makeText(this, "Unable to open File Manager", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void buildMediaChips() {
        try {
            if (mediaChipContainer == null) {
                return;
            }
            mediaChipContainer.removeAllViews();
            mediaChips = new TextView[mediaLabels.length];

            for (int i = 0; i < mediaLabels.length; i++) {
                TextView chip = new TextView(this);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        AndroidUtilities.dp(40)
                );
                lp.setMarginEnd(AndroidUtilities.dp(6));
                chip.setLayoutParams(lp);
                chip.setMinWidth(AndroidUtilities.dp(72));
                chip.setBackgroundResource(R.drawable.bg_file_manager_chip);
                chip.setGravity(android.view.Gravity.CENTER);
                chip.setPadding(AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12), 0);
                chip.setText(mediaLabels[i]);
                chip.setTextColor(Color.parseColor("#354B63"));
                chip.setTextSize(12);
                chip.setAllCaps(false);
                chip.setSingleLine();
                chip.setMaxLines(1);
                mediaChipContainer.addView(chip);
                mediaChips[i] = chip;
            }
        } catch (Exception e) {
            FileLog.e("FileManagerActivity", e);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openForwardTargetSelector() {
        if (LaunchActivity.instance == null) {
            Toast.makeText(this, "Unable to open target chooser", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Bundle args = new Bundle();
            args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
            args.putBoolean("onlySelect", true);
            args.putBoolean("checkCanWrite", false);
            args.putString("selectAlertString", "Select a destination chat");
            args.putString("selectAlertStringGroup", "Select a destination group or channel");
            args.putLong("dialog_id", UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId());

            DialogsActivity dialogsActivity = new DialogsActivity(args);
            dialogsActivity.setDelegate((fragment, dids, message, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
                if (dids == null || dids.isEmpty()) {
                    return true;
                }

                long selectedDialogId = dids.get(0).dialogId;
                try {
                    Toast.makeText(FileManagerActivity.this,
                            "Selected destination: " + selectedDialogId,
                            Toast.LENGTH_SHORT).show();
                } catch (Exception ignored) {
                }

                dialogsActivity.finishFragment();
                finish();
                return true;
            });

            LaunchActivity.instance.presentFragment(dialogsActivity);
        } catch (Exception e) {
            FileLog.e("FileManagerActivity", e);
            Toast.makeText(this, "Unable to open target chooser", Toast.LENGTH_SHORT).show();
        }
    }

    private void openMediaScreen() {
        if (LaunchActivity.instance == null) {
            Toast.makeText(this, "Unable to open media view", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Bundle args = new Bundle();
            args.putLong("dialog_id", 0L);
            args.putInt("type", MediaActivity.TYPE_MEDIA);
            args.putInt("start_from", getInitialTab());
            LaunchActivity.instance.presentFragment(new MediaActivity(args, null));
            finish();
        } catch (Exception e) {
            FileLog.e("FileManagerActivity", e);
            Toast.makeText(this, "Unable to open media view", Toast.LENGTH_SHORT).show();
        }
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
        return mediaTabs[selectedMediaType];
    }

    private void updateMediaSelection() {
        for (int i = 0; i < mediaChips.length; i++) {
            applyChipState(mediaChips[i], i == selectedMediaType);
        }
        updateSummary();
    }

    private void updateChatSelection() {
        for (int i = 0; i < chatChips.length; i++) {
            applyChipState(chatChips[i], i == selectedChatType);
        }
        updateSummary();
    }

    private void updateStatusSelection() {
        for (int i = 0; i < statusChips.length; i++) {
            applyChipState(statusChips[i], i == selectedStatus);
        }
        updateSummary();
    }

    private void updateSummary() {
        summaryText.setText("Showing " + mediaLabels[selectedMediaType] + " • " + chatLabels[selectedChatType] + " • " + statusLabels[selectedStatus]);
    }

    private void applyChipState(TextView chip, boolean selected) {
        chip.setBackgroundResource(selected ? R.drawable.bg_file_manager_chip_selected : R.drawable.bg_file_manager_chip);
        chip.setTextColor(selected ? Color.WHITE : Color.parseColor("#354B63"));
    }

    private void toggleItemSelection(int index) {
        selectionMode = true;
        if (rangeSelectionMode) {
            if (selectionAnchorIndex < 0) {
                selectionAnchorIndex = index;
            }
            selectedItems.clear();
            int start = Math.min(selectionAnchorIndex, index);
            int end = Math.max(selectionAnchorIndex, index);
            for (int i = start; i <= end; i++) {
                selectedItems.add(i);
            }
        } else {
            if (selectedItems.contains(index)) {
                selectedItems.remove(index);
            } else {
                selectedItems.add(index);
            }
            selectionAnchorIndex = index;
        }
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        for (int i = 0; i < itemViews.size(); i++) {
            boolean selected = selectedItems.contains(i);
            itemViews.get(i).setBackgroundResource(selected ? R.drawable.bg_file_manager_item_selected : R.drawable.bg_file_manager_chip);
            itemViews.get(i).setTextColor(selected ? Color.parseColor("#2F6FED") : Color.parseColor("#354B63"));
        }

        boolean hasSelection = !selectedItems.isEmpty();
        forwardButton.setEnabled(hasSelection);
        detailButton.setEnabled(hasSelection);
        unselectButton.setEnabled(hasSelection || selectionMode);
        forwardButton.setAlpha(hasSelection ? 1f : 0.55f);
        detailButton.setAlpha(hasSelection ? 1f : 0.55f);
        unselectButton.setAlpha(hasSelection || selectionMode ? 1f : 0.55f);

        if (selectionMode && hasSelection) {
            String modeText = rangeSelectionMode ? "Range" : "Selection";
            selectionStatus.setText(modeText + " • " + selectedItems.size() + " item(s)");
        } else {
            selectionStatus.setText("No items selected");
        }
    }

    private void refreshItemLabels() {
        for (int i = 0; i < itemViews.size(); i++) {
            itemViews.get(i).setText(buildItemLabel(i));
        }
    }

    private String buildItemLabel(int index) {
        if (index < 0 || index >= itemLabels.length) {
            return "";
        }

        String label = itemLabels[index];
        int type = itemTypes[index];
        long size = itemSizes[index];

        if (size <= 0) {
            return label;
        }

        boolean showSize = false;
        switch (type) {
            case ITEM_TYPE_PHOTO:
                showSize = NekoConfig.showSharedMediaPhotoSize.Bool();
                break;
            case ITEM_TYPE_AUDIO:
                showSize = NekoConfig.showSharedMediaVideoSize.Bool();
                break;
            case ITEM_TYPE_LINK:
                showSize = false;
                break;
            default:
                break;
        }

        return showSize ? label + " • " + AndroidUtilities.formatFileSize(size) : label;
    }

    private void showDetailDialog() {
        StringBuilder details = new StringBuilder();
        for (Integer index : selectedItems) {
            if (index != null && index >= 0 && index < itemLabels.length) {
                details.append("• ").append(buildItemLabel(index)).append("\n");
            }
        }

        new AlertDialog.Builder(this)
                .setTitle("Item details")
                .setMessage(details.length() == 0 ? "No items selected" : details.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

}
