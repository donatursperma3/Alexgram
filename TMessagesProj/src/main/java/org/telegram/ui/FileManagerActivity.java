package org.telegram.ui;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

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
import org.telegram.ui.Components.MediaActivity;
import org.telegram.ui.Components.SharedMediaLayout;
import tw.nekomimi.nekogram.NekoConfig;

public class FileManagerActivity extends AppCompatActivity {

    private static final int ITEM_TYPE_PHOTO = 0;
    private static final int ITEM_TYPE_FILE = 1;
    private static final int ITEM_TYPE_AUDIO = 2;
    private static final int ITEM_TYPE_LINK = 3;

    private final String[] mediaLabels = {"Photos", "Files", "Audio", "Links"};
    private final int[] mediaTabs = {
            SharedMediaLayout.TAB_PHOTOVIDEO,
            SharedMediaLayout.TAB_FILES,
            SharedMediaLayout.TAB_AUDIO,
            SharedMediaLayout.TAB_LINKS
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
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open File Manager", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setTitle("File Manager");
        summaryText = findViewById(R.id.fm_selection_summary);
        selectionStatus = findViewById(R.id.fm_selection_status);
        selectSingleButton = findViewById(R.id.fm_select_single_button);
        selectRangeButton = findViewById(R.id.fm_select_range_button);
        forwardButton = findViewById(R.id.fm_forward_button);
        detailButton = findViewById(R.id.fm_detail_button);
        unselectButton = findViewById(R.id.fm_unselect_button);

        mediaChips = new TextView[] {
                findViewById(R.id.fm_media_chip_photos),
                findViewById(R.id.fm_media_chip_files),
                findViewById(R.id.fm_media_chip_audio),
                findViewById(R.id.fm_media_chip_links)
        };
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
            mediaChips[i].setOnClickListener(v -> {
                selectedMediaType = index;
                updateMediaSelection();
            });
        }

        for (int i = 0; i < chatChips.length; i++) {
            final int index = i;
            chatChips[i].setOnClickListener(v -> {
                selectedChatType = index;
                updateChatSelection();
            });
        }

        for (int i = 0; i < statusChips.length; i++) {
            final int index = i;
            statusChips[i].setOnClickListener(v -> {
                selectedStatus = index;
                updateStatusSelection();
            });
        }

        itemViews.addAll(Arrays.asList(
                findViewById(R.id.fm_item_0),
                findViewById(R.id.fm_item_1),
                findViewById(R.id.fm_item_2),
                findViewById(R.id.fm_item_3),
                findViewById(R.id.fm_item_4)
        ));

        for (int i = 0; i < itemViews.size(); i++) {
            final int index = i;
            itemViews.get(i).setOnClickListener(v -> toggleItemSelection(index));
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
                showForwardDialog();
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

    private void showForwardDialog() {
        String message = "Forward " + selectedItems.size() + " selected item(s) to the next destination?";
        new AlertDialog.Builder(this)
                .setTitle("Forward items")
                .setMessage(message)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    Toast.makeText(this, "Forward request prepared for " + selectedItems.size() + " item(s)", Toast.LENGTH_SHORT).show();
                    selectionMode = false;
                    selectedItems.clear();
                    updateSelectionUi();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
