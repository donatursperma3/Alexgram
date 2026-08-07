package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.LocaleController.getString;
import static org.telegram.messenger.TranslateController.UNKNOWN_LANGUAGE;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.DatePicker;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.radolyn.ayugram.utils.AyuState;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteException;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BaseController;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LanguageDetector;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ColoredImageSpan;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.EditTextBoldCursor;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.filters.AyuFilter;
import xyz.nextalone.nagram.NaConfig;

public class MessageHelper extends BaseController {

    private static final MessageHelper[] Instance = new MessageHelper[UserConfig.MAX_ACCOUNT_COUNT];
    private static final CharsetDecoder utf8Decoder = StandardCharsets.UTF_8.newDecoder();
    private static final String DELETE_OWN_LIMIT_KEY = "delete_own_limit";
    private static final String DELETE_OWN_ORDER_KEY = "delete_own_order";
    private static final String DELETE_OWN_FILTER_KEY = "delete_own_filter";
    private static final int DELETE_ORDER_NEWEST = 0;
    private static final int DELETE_ORDER_OLDEST = 1;
    
    // Message type filter constants
    private static final int FILTER_ALL = 0;
    private static final int FILTER_TEXT = 1;
    private static final int FILTER_VIDEO = 2;
    private static final int FILTER_IMAGE = 3;
    private static final int FILTER_STICKER = 4;
    private static final int FILTER_FILES = 5;
    private static final int FILTER_GIF = 6;
    private static final int FILTER_POLL = 7;
    private static final int FILTER_LOCATION = 8;
    private static final int FILTER_CONTACT = 9;
    private static final int FILTER_OTHER = 10;
    private static final int FILTER_REACTIONS = 11;
    private static final int FILTER_PHOTO_VIDEO = 12;

    public MessageHelper(int num) {
        super(num);
    }

    public static MessageHelper getInstance(int num) {
        MessageHelper localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (MessageHelper.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new MessageHelper(num);
                }
            }
        }
        return localInstance;
    }

    public static String getPathToMessage(MessageObject messageObject) {
        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File f = new File(path);
            if (!f.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(UserConfig.selectedAccount).getPathToMessage(messageObject.messageOwner).toString();
            File f = new File(path);
            if (!f.exists() || f.getAbsolutePath().endsWith("/cache")) {
                path = null;
            }
            if (TextUtils.isEmpty(path)) {
                String fileName = f.getName();
                    if (fileName != null) {
                        fileName = "ttl_" + messageObject.getDialogId() + "_" + messageObject.getId() + "_" + fileName;
                        // found = AyuMessageUtils.findExistingFileByBaseNameFast(fileName);
                    }
            }
        }
        if (TextUtils.isEmpty(path)) {
            File f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.getDocument(), true);
            if (f.exists() && !f.getAbsolutePath().endsWith("/cache")) {
                path = f.getAbsolutePath();
            } else {
                f = FileLoader.getInstance(UserConfig.selectedAccount).getPathToAttach(messageObject.getDocument());
                if (f.exists()) {
                    path = f.getAbsolutePath();
                }
            }
        }
        return path != null && !path.endsWith("/cache") ? path : null;
    }

    public void resetMessageContent(long dialog_id, MessageObject messageObject) {
        TLRPC.Message message = messageObject.messageOwner;

        MessageObject obj = new MessageObject(currentAccount, message, true, true);

        ArrayList<MessageObject> arrayList = new ArrayList<>();
        arrayList.add(obj);
        getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList, false, true);
    }

    public void resetMessageContent(long dialog_id, ArrayList<MessageObject> messageObjects) {
        ArrayList<MessageObject> arrayList = new ArrayList<>();
        for (MessageObject messageObject : messageObjects) {
            MessageObject obj = new MessageObject(currentAccount, messageObject.messageOwner, true, true);
            arrayList.add(obj);
        }
        getNotificationCenter().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList, false);
    }

    public interface FilteredMessageCallback {
        void onLoaded(MessageObject result);
    }

    public void loadLastMessageSkippingFilteredAsync(long dialogId, FilteredMessageCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            MessageObject result = getLastMessageSkippingFiltered(dialogId);
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.onLoaded(result));
            }
        });
    }

    public MessageObject getLastMessageSkippingFiltered(long dialogId) {
        SQLiteCursor cursor = null;
        NativeByteBuffer data = null;
        try {
            boolean ignoreBlocked = NekoConfig.ignoreBlocked.Bool();
            long currentUserId = UserConfig.getInstance(currentAccount).clientUserId;
            HashMap<Long, HashMap<Long, TLRPC.Message>> replyMessageCache = ignoreBlocked ? new HashMap<>() : null;
            String query = ignoreBlocked
                ? String.format(Locale.US, "SELECT data,send_state,mid,date,replydata FROM messages_v2 WHERE uid = %d ORDER BY date DESC LIMIT %d,%d", dialogId, 0, 20)
                : String.format(Locale.US, "SELECT data,send_state,mid,date FROM messages_v2 WHERE uid = %d ORDER BY date DESC LIMIT %d,%d", dialogId, 0, 20);
            cursor = getMessagesStorage().getDatabase().queryFinalized(query);
            while (cursor.next()) {
                data = cursor.byteBufferValue(0);
                if (data == null) {
                    continue;
                }
                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                if (message == null) {
                    data.reuse();
                    data = null;
                    continue;
                }
                message.send_state = cursor.intValue(1);
                message.id = cursor.intValue(2);
                message.date = cursor.intValue(3);
                message.dialog_id = dialogId;
                message.readAttachPath(data, currentUserId);
                data.reuse();
                data = null;

                if (ignoreBlocked) {
                    long fromId = MessageObject.getFromChatId(message);
                    if (isBlockedUser(fromId) || AyuFilter.isBlockedChannel(fromId)) {
                        continue;
                    }
                    if (message.reply_to != null && message.reply_to.reply_to_msg_id != 0) {
                        if (!cursor.isNull(4)) {
                            NativeByteBuffer replyData = cursor.byteBufferValue(4);
                            if (replyData != null) {
                                message.replyMessage = TLRPC.Message.TLdeserialize(replyData, replyData.readInt32(false), false);
                                if (message.replyMessage != null) {
                                    message.replyMessage.readAttachPath(replyData, currentUserId);
                                }
                                replyData.reuse();
                            }
                        }
                        if (message.replyMessage == null) {
                            long replyDialogId = MessageObject.getReplyToDialogId(message);
                            if (replyDialogId == 0) {
                                replyDialogId = dialogId;
                            }
                            long replyMsgId = message.reply_to.reply_to_msg_id;
                            HashMap<Long, TLRPC.Message> dialogCache = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N ? replyMessageCache.computeIfAbsent(replyDialogId, k -> new HashMap<>()) : null;
                            if (dialogCache != null) {
                                if (dialogCache.containsKey(replyMsgId)) {
                                    message.replyMessage = dialogCache.get(replyMsgId);
                                } else {
                                    message.replyMessage = getMessage(replyDialogId, replyMsgId);
                                    dialogCache.put(replyMsgId, message.replyMessage);
                                }
                            }
                        }
                        if (message.replyMessage != null) {
                            fromId = MessageObject.getFromChatId(message.replyMessage);
                            if (isBlockedUser(fromId) || AyuFilter.isBlockedChannel(fromId)) {
                                continue;
                            }
                        }
                    }
                }

                MessageObject obj = new MessageObject(currentAccount, message, false, false);
                if (AyuFilter.isFiltered(obj, null)) {
                    continue;
                }
                if (getMessagesController().getUser(obj.getSenderId()) == null) {
                    TLRPC.User user = getMessagesStorage().getUser(obj.getSenderId());
                    if (user != null) {
                        getMessagesController().putUser(user, true);
                    }
                }
                return obj;
            }
        } catch (SQLiteException e) {
            FileLog.e("RegexFilter, SQLiteException when reading last unfiltered message", e);
        } finally {
            if (data != null) {
                data.reuse();
            }
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return null;
    }

    public TLRPC.Message getMessage(long dialogId, long msgId) {
        TLRPC.Message message = null;
        SQLiteCursor cursor = null;
        try {
            cursor = getMessagesStorage().getDatabase().queryFinalized("SELECT data FROM messages_v2 WHERE uid = " + dialogId + " AND mid = " + msgId + " LIMIT 1");
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message != null) {
                        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                    }
                    data.reuse();
                }
            }
            cursor.dispose();
            cursor = null;
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return message;
    }

    public ArrayList<TLRPC.Message> getMessagesStorageMessages(long dialogId, ArrayList<Integer> messageIds) {
        ArrayList<TLRPC.Message> messages = null;
        SQLiteCursor cursor = null;
        try {
            String ids = TextUtils.join(",", messageIds);
            cursor = getMessagesStorage().getDatabase().queryFinalized(String.format(Locale.US,"SELECT data FROM messages_v2 WHERE uid = %d AND mid IN (%s)", dialogId, ids));
            while (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    if (message != null) {
                        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                    }
                    data.reuse();
                    if (messages == null) {
                        messages = new ArrayList<>();
                    }
                    messages.add(message);
                }
            }
            cursor.dispose();
            cursor = null;
        } catch (SQLiteException e) {
            FileLog.e(e);
        } finally {
            if (cursor != null) {
                cursor.dispose();
            }
        }
        return messages;
    }

    public void saveStickerToGallery(Context context, MessageObject messageObject) {
        if (messageObject.isAnimatedSticker()) return;
        // Animated Sticker is not supported.

        String path = messageObject.messageOwner.attachPath;
        if (!TextUtils.isEmpty(path)) {
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(currentAccount).getPathToMessage(messageObject.messageOwner).toString();
            File temp = new File(path);
            if (!temp.exists()) {
                path = null;
            }
        }
        if (TextUtils.isEmpty(path)) {
            path = FileLoader.getInstance(currentAccount).getPathToAttach(messageObject.getDocument(), true).toString();
        }
        if (!TextUtils.isEmpty(path)) {
            if (messageObject.isVideoSticker()) {
                MediaController.saveFile(path, context, 1, null, null);
            } else {
                try {
                    Bitmap image = BitmapFactory.decodeFile(path);
                    FileOutputStream stream = new FileOutputStream(path + ".png");
                    image.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    stream.close();
                    MediaController.saveFile(path + ".png", context, 0, null, null);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public void saveStickerToGallery(Context context, TLRPC.Document document) {
        String path = FileLoader.getInstance(currentAccount).getPathToAttach(document, true).toString();

        if (!TextUtils.isEmpty(path)) {
            if (MessageObject.isVideoSticker(document)) {
                MediaController.saveFile(path, context, 1, null, document.mime_type);
            } else {
                try {
                    Bitmap image = BitmapFactory.decodeFile(path);
                    FileOutputStream stream = new FileOutputStream(path + ".png");
                    image.compress(Bitmap.CompressFormat.PNG, 100, stream);
                    stream.close();
                    MediaController.saveFile(path + ".png", context, 0, null, null);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public void addStickerToClipboard(TLRPC.Document document, Runnable callback) {
        String path = FileLoader.getInstance(currentAccount).getPathToAttach(document, true).toString();

        if (TextUtils.isEmpty(path)) {
            return;
        }
        if (MessageObject.isVideoSticker(document)) {
            return;
        }
        File file = new File(path);
        if (file.exists()) {
            addFileToClipboard(file, callback);
        }
    }

    public MessageObject getMessageForRepeat(MessageObject selectedObject, MessageObject.GroupedMessages selectedObjectGroup) {
        MessageObject messageObject = null;
        if (selectedObjectGroup != null && !selectedObjectGroup.isDocuments) {
            messageObject = getTargetMessageObjectFromGroup(selectedObjectGroup);
        } else if (!TextUtils.isEmpty(selectedObject.messageOwner.message) || selectedObject.isAnyKindOfSticker()) {
            messageObject = selectedObject;
        }
        return messageObject;
    }

    private MessageObject getTargetMessageObjectFromGroup(MessageObject.GroupedMessages selectedObjectGroup) {
        MessageObject messageObject = null;
        for (MessageObject object : selectedObjectGroup.messages) {
            if (!TextUtils.isEmpty(object.messageOwner.message)) {
                if (messageObject != null) {
                    messageObject = null;
                    break;
                } else {
                    messageObject = object;
                }
            }
        }
        return messageObject;
    }

    public void createDeleteHistoryAlert(BaseFragment fragment, TLRPC.Chat chat, TLRPC.TL_forumTopic forumTopic, long mergeDialogId, Theme.ResourcesProvider resourcesProvider) {
        createDeleteHistoryAlert(fragment, chat, forumTopic, mergeDialogId, -1, resourcesProvider);
    }

    private void createDeleteHistoryAlert(BaseFragment fragment, TLRPC.Chat chat, TLRPC.TL_forumTopic forumTopic, long mergeDialogId, int before, Theme.ResourcesProvider resourcesProvider) {
        if (fragment == null || fragment.getParentActivity() == null || chat == null) {
            return;
        }

        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context, resourcesProvider);

        CheckBoxCell cell = before == -1 && forumTopic == null && ChatObject.isChannel(chat) && ChatObject.canUserDoAction(chat, ChatObject.ACTION_DELETE_MESSAGES) ? new CheckBoxCell(context, 1, resourcesProvider) : null;

        TextView messageTextView = new TextView(context);
        messageTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        messageTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        messageTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);

        FrameLayout frameLayout = new FrameLayout(context) {
            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
                if (cell != null) {
                    setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + cell.getMeasuredHeight() + AndroidUtilities.dp(7));
                }
            }
        };
        builder.setView(frameLayout);

        // Limit input field (0 = unlimited)
        EditTextBoldCursor limitInput = new EditTextBoldCursor(context);
        limitInput.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        limitInput.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        limitInput.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteHintText));
        limitInput.setBackground(null);
        limitInput.setSingleLine(true);
        limitInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        limitInput.setHint("0 = unlimited");
        try {
            int savedLimit = NekoConfig.getPreferences().getInt(DELETE_OWN_LIMIT_KEY, 0);
            if (savedLimit > 0) limitInput.setText(String.valueOf(savedLimit));
        } catch (Exception e) {
            FileLog.e(e);
        }

        final int labelWidth = AndroidUtilities.dp(52);

        TextView limitLabel = new TextView(context);
        limitLabel.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        limitLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        limitLabel.setGravity(Gravity.CENTER_VERTICAL);
        limitLabel.setIncludeFontPadding(false);
        limitLabel.setText("Max:");

        LinearLayout limitRow = new LinearLayout(context);
        limitRow.setOrientation(LinearLayout.HORIZONTAL);
        limitRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
            labelWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        labelParams.setMargins(0, 0, AndroidUtilities.dp(8), 0);
        limitRow.addView(limitLabel, labelParams);

        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
            0,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            1f
        );
        limitInput.setGravity(Gravity.CENTER_VERTICAL);
        limitInput.setIncludeFontPadding(false);
        limitInput.setPadding(0, 0, 0, 0);
        limitRow.addView(limitInput, inputParams);

        TextView orderLabel = new TextView(context);
        orderLabel.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        orderLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        orderLabel.setGravity(Gravity.CENTER_VERTICAL);
        orderLabel.setIncludeFontPadding(false);
        orderLabel.setText("Mode:");

        final boolean[] deleteNewestFirst = new boolean[1];
        try {
            int savedOrder = NekoConfig.getPreferences().getInt(DELETE_OWN_ORDER_KEY, DELETE_ORDER_NEWEST);
            deleteNewestFirst[0] = savedOrder != DELETE_ORDER_OLDEST;
        } catch (Exception e) {
            FileLog.e(e);
            deleteNewestFirst[0] = true;
        }

        TextView orderValue = new TextView(context);
        orderValue.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        orderValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        orderValue.setGravity(Gravity.CENTER_VERTICAL);
        orderValue.setSingleLine(true);
        orderValue.setEllipsize(TextUtils.TruncateAt.END);
        orderValue.setText(deleteNewestFirst[0] ? "Newest" : "Oldest");
        orderValue.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        orderValue.setOnClickListener(v -> {
            deleteNewestFirst[0] = !deleteNewestFirst[0];
            orderValue.setText(deleteNewestFirst[0] ? "Newest" : "Oldest");
        });

        LinearLayout orderRow = new LinearLayout(context);
        orderRow.setOrientation(LinearLayout.HORIZONTAL);
        orderRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams orderLabelParams = new LinearLayout.LayoutParams(
            labelWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        orderLabelParams.setMargins(0, 0, AndroidUtilities.dp(6), 0);
        orderRow.addView(orderLabel, orderLabelParams);

        LinearLayout.LayoutParams orderValueParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        orderRow.addView(orderValue, orderValueParams);

        // Message type filter UI
        TextView filterLabel = new TextView(context);
        filterLabel.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        filterLabel.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        filterLabel.setGravity(Gravity.CENTER_VERTICAL);
        filterLabel.setIncludeFontPadding(false);
        filterLabel.setText("Type:");

        final int[] selectedFilter = new int[1];
        try {
            int savedFilter = NekoConfig.getPreferences().getInt(DELETE_OWN_FILTER_KEY, FILTER_ALL);
            selectedFilter[0] = savedFilter;
        } catch (Exception e) {
            FileLog.e(e);
            selectedFilter[0] = FILTER_ALL;
        }

        final String[] filterNames = {
            "All", "Text", "Video", "Image", "Sticker", 
            "Files", "GIF", "Poll", "Location", "Contact", "Other", "Reactions", "Photo/Video"
        };

        TextView filterValue = new TextView(context);
        filterValue.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider));
        filterValue.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        filterValue.setGravity(Gravity.CENTER_VERTICAL);
        filterValue.setSingleLine(true);
        filterValue.setEllipsize(TextUtils.TruncateAt.END);
        filterValue.setText(filterNames[selectedFilter[0]]);
        filterValue.setPadding(0, 0, AndroidUtilities.dp(4), 0);
        filterValue.setOnClickListener(v -> {
            AlertDialog.Builder filterBuilder = new AlertDialog.Builder(context);
            filterBuilder.setTitle("Select Message Type");
            filterBuilder.setItems(filterNames, (dialog, which) -> {
                selectedFilter[0] = which;
                filterValue.setText(filterNames[which]);
            });
            filterBuilder.show();
        });

        LinearLayout filterRow = new LinearLayout(context);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout.LayoutParams filterLabelParams = new LinearLayout.LayoutParams(
            labelWidth,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        filterLabelParams.setMargins(0, 0, AndroidUtilities.dp(6), 0);
        filterRow.addView(filterLabel, filterLabelParams);

        LinearLayout.LayoutParams filterValueParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        filterRow.addView(filterValue, filterValueParams);

        LinearLayout contentLayout = new LinearLayout(context);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        contentLayout.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(120), AndroidUtilities.dp(24), 0);
        contentLayout.addView(limitRow);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.topMargin = AndroidUtilities.dp(8);
        contentLayout.addView(orderRow, rowParams);
        rowParams.topMargin = AndroidUtilities.dp(8);
        contentLayout.addView(filterRow, rowParams);
        frameLayout.addView(contentLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 0, 0, 0, 0));

        AvatarDrawable avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(12));
        avatarDrawable.setInfo(chat);

        BackupImageView imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(20));
        if (forumTopic != null) {
            if (forumTopic.id == 1) {
                imageView.setImageDrawable(ForumUtilities.createGeneralTopicDrawable(context, 0.75f, Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider), false));
            } else {
                ForumUtilities.setTopicIcon(imageView, forumTopic, false, true, resourcesProvider);
            }
        } else {
            imageView.setForUserOrChat(chat, avatarDrawable);
        }
        frameLayout.addView(imageView, LayoutHelper.createFrame(40, 40, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 22, 5, 22, 0));

        TextView textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_actionBarDefaultSubmenuItem, resourcesProvider));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        textView.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setText(getString(R.string.DeleteAllFromSelf));

        frameLayout.addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, (LocaleController.isRTL ? 21 : 76), 11, (LocaleController.isRTL ? 76 : 21), 0));
        frameLayout.addView(messageTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 24, 57, 24, 9));

        if (cell != null) {
            boolean sendAs = ChatObject.getSendAsPeerId(chat, getMessagesController().getChatFull(chat.id), true) != getUserConfig().getClientUserId();
            cell.setBackground(Theme.getSelectorDrawable(false));
            cell.setText(getString(R.string.DeleteAllFromSelfAdmin), "", !ChatObject.shouldSendAnonymously(chat) && !sendAs, false);
            cell.setPadding(LocaleController.isRTL ? AndroidUtilities.dp(16) : AndroidUtilities.dp(8), 0, LocaleController.isRTL ? AndroidUtilities.dp(8) : AndroidUtilities.dp(16), 0);
            frameLayout.addView(cell, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 48, Gravity.BOTTOM | Gravity.LEFT, 0, 0, 0, 0));
            cell.setOnClickListener(v -> {
                CheckBoxCell cell1 = (CheckBoxCell) v;
                cell1.setChecked(!cell1.isChecked(), true);
            });
        }

        if (before > 0) {
            messageTextView.setText(AndroidUtilities.replaceTags(LocaleController.formatString(R.string.DeleteAllFromSelfAlertBefore, LocaleController.formatDateForBan(before))));
        } else {
            messageTextView.setText(AndroidUtilities.replaceTags(getString(R.string.DeleteAllFromSelfAlert)));
        }

        builder.setNeutralButton(getString(R.string.DeleteAllFromSelfBefore), (dialog, which) -> showBeforeDatePickerAlert(fragment, before1 -> createDeleteHistoryAlert(fragment, chat, forumTopic, mergeDialogId, before1, resourcesProvider)));
        builder.setPositiveButton(getString(R.string.DeleteAll), (dialogInterface, i) -> {
            int limit = 0;
            try {
                String txt = limitInput.getText().toString().trim();
                if (!txt.isEmpty()) {
                    limit = Integer.parseInt(txt);
                    if (limit < 0) limit = 0;
                }
                NekoConfig.getPreferences().edit()
                    .putInt(DELETE_OWN_LIMIT_KEY, limit)
                    .putInt(DELETE_OWN_ORDER_KEY, deleteNewestFirst[0] ? DELETE_ORDER_NEWEST : DELETE_ORDER_OLDEST)
                    .putInt(DELETE_OWN_FILTER_KEY, selectedFilter[0])
                    .apply();
            } catch (Exception e) {
                FileLog.e(e);
                limit = 0;
                deleteNewestFirst[0] = true;
            }

            final boolean newestFirst = deleteNewestFirst[0];
            final int messageTypeFilter = selectedFilter[0];
            FileLog.d("DeleteMyMessages", "Delete requested. chatId=" + chat.id + ", limit=" + limit + ", newestFirst=" + newestFirst + ", filter=" + messageTypeFilter + ", adminMode=" + (cell != null && cell.isChecked()));
            if (cell != null && cell.isChecked()) {
                final int finalLimit = limit;
                showDeleteHistoryBulletin(fragment, 0, false, () -> {
                    if (finalLimit <= 0 && messageTypeFilter == FILTER_ALL) {
                        // Use direct API call only if no limit and no specific filter
                        FileLog.d("DeleteMyMessages", "Using direct API call (admin mode, no limit, all messages)");
                        getMessagesController().deleteUserChannelHistory(chat, getUserConfig().getCurrentUser(), null, 0);
                    } else {
                        // Use search-based deletion to respect limit and/or filter
                        FileLog.d("DeleteMyMessages", "Using search-based deletion (admin mode with limit/filter)");
                        deleteUserHistoryWithSearch(fragment, -chat.id, forumTopic != null ? forumTopic.id : 0, mergeDialogId, before == -1 ? getConnectionsManager().getCurrentTime() : before, finalLimit, newestFirst, messageTypeFilter, (count, deleteAction) -> deleteAction.run());
                    }
                }, resourcesProvider);
            } else {
                FileLog.d("DeleteMyMessages", "Using search-based deletion (normal mode)");
                deleteUserHistoryWithSearch(fragment, -chat.id, forumTopic != null ? forumTopic.id : 0, mergeDialogId, before == -1 ? getConnectionsManager().getCurrentTime() : before, limit, newestFirst, messageTypeFilter, (count, deleteAction) -> showDeleteHistoryBulletin(fragment, count, true, deleteAction, resourcesProvider));
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        AlertDialog alertDialog = builder.create();
        fragment.showDialog(alertDialog);
        TextView button = (TextView) alertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        if (button != null) {
            button.setTextColor(Theme.getColor(Theme.key_text_RedBold, resourcesProvider));
        }
    }

    private void showBeforeDatePickerAlert(BaseFragment fragment, Utilities.Callback<Integer> callback) {
        Context context = fragment.getParentActivity();
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.DeleteAllFromSelfBefore));
        builder.setItems(new CharSequence[]{
                LocaleController.formatPluralString("Days", 1),
                LocaleController.formatPluralString("Weeks", 1),
                LocaleController.formatPluralString("Months", 1),
                getString(R.string.UserRestrictionsCustom)
        }, (dialog1, which) -> {
            switch (which) {
                case 0:
                    callback.run(getConnectionsManager().getCurrentTime() - 60 * 60 * 24);
                    break;
                case 1:
                    callback.run(getConnectionsManager().getCurrentTime() - 60 * 60 * 24 * 7);
                    break;
                case 2:
                    callback.run(getConnectionsManager().getCurrentTime() - 60 * 60 * 24 * 30);
                    break;
                case 3: {
                    DatePickerDialog dateDialog = getDatePickerDialog(fragment, callback, context);

                    final DatePicker datePicker = dateDialog.getDatePicker();

                    datePicker.setMinDate(1375315200000L);
                    datePicker.setMaxDate(System.currentTimeMillis());

                    dateDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.Set), dateDialog);
                    dateDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.Cancel), (dialog2, which2) -> {
                    });
                    dateDialog.setOnShowListener(dialog12 -> {
                        int count = datePicker.getChildCount();
                        for (int b = 0; b < count; b++) {
                            View child = datePicker.getChildAt(b);
                            ViewGroup.LayoutParams layoutParams = child.getLayoutParams();
                            layoutParams.width = LayoutHelper.MATCH_PARENT;
                            child.setLayoutParams(layoutParams);
                        }
                    });
                    fragment.showDialog(dateDialog);
                    break;
                }
            }
            builder.getDismissRunnable().run();
        });
        fragment.showDialog(builder.create());
    }

    @NonNull
    private static DatePickerDialog getDatePickerDialog(BaseFragment fragment, Utilities.Callback<Integer> callback, Context context) {
        Calendar calendar = Calendar.getInstance();
        return new DatePickerDialog(context, (view1, year1, month, dayOfMonth1) -> {
            TimePickerDialog timeDialog = new TimePickerDialog(context, (view11, hourOfDay, minute) -> {
                calendar.set(year1, month, dayOfMonth1, hourOfDay, minute);
                callback.run((int) (calendar.getTimeInMillis() / 1000));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true);
            timeDialog.setButton(DialogInterface.BUTTON_POSITIVE, getString(R.string.Set), timeDialog);
            timeDialog.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.Cancel), (dialog3, which3) -> {
            });
            fragment.showDialog(timeDialog);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
    }

    public static void showDeleteHistoryBulletin(BaseFragment fragment, int count, boolean search, Runnable delayedAction, Theme.ResourcesProvider resourcesProvider) {
        if (fragment.getParentActivity() == null) {
            if (delayedAction != null) {
                delayedAction.run();
            }
            return;
        }
        Bulletin.ButtonLayout buttonLayout;
        if (search) {
            final Bulletin.TwoLineLottieLayout layout = new Bulletin.TwoLineLottieLayout(fragment.getParentActivity(), resourcesProvider);
            layout.titleTextView.setText(getString(R.string.DeleteAllFromSelfDone));
            layout.subtitleTextView.setText(LocaleController.formatPluralString("MessagesDeletedHint", count));
            layout.setTimer();
            buttonLayout = layout;
        } else {
            final Bulletin.LottieLayout layout = new Bulletin.LottieLayout(fragment.getParentActivity(), resourcesProvider);
            layout.textView.setText(getString(R.string.DeleteAllFromSelfDone));
            layout.setTimer();
            buttonLayout = layout;
        }
        buttonLayout.setButton(new Bulletin.UndoButton(fragment.getParentActivity(), true, resourcesProvider).setDelayedAction(delayedAction));
        Bulletin.make(fragment, buttonLayout, Bulletin.DURATION_PROLONG).show();
    }

    private void deleteUserHistoryWithSearch(BaseFragment fragment, final long dialogId, int replyMessageId, final long mergeDialogId, int before, int limit, boolean newestFirst, int messageTypeFilter, SearchMessagesResultCallback callback) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                ArrayList<Integer> messageIds = new ArrayList<>();
                var latch = new CountDownLatch(1);
                var peer = getMessagesController().getInputPeer(dialogId);
                var fromId = MessagesController.getInputPeer(getUserConfig().getCurrentUser());
                FileLog.d("DeleteMyMessages", "Starting search for messages to delete. dialogId=" + dialogId + ", limit=" + limit + ", filter=" + messageTypeFilter);
                doSearchMessages(fragment, latch, messageIds, peer, replyMessageId, fromId, before, Integer.MAX_VALUE, 0, limit, newestFirst, messageTypeFilter);
                try {
                    latch.await();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                FileLog.d("DeleteMyMessages", "Search completed. Found " + messageIds.size() + " messages to delete");
                if (!messageIds.isEmpty()) {
                    if (!newestFirst) {
                        Collections.reverse(messageIds);
                    }
                    if (limit > 0 && messageIds.size() > limit) {
                        messageIds = new ArrayList<>(messageIds.subList(0, limit));
                    }

                    ArrayList<ArrayList<Integer>> lists = new ArrayList<>();
                    final int N = messageIds.size();
                    for (int i = 0; i < N; i += 100) {
                        lists.add(new ArrayList<>(messageIds.subList(i, Math.min(N, i + 100))));
                    }
                    final ArrayList<ArrayList<Integer>> finalLists = lists;
                    final int finalMessageCount = messageIds.size();
                    Runnable deleteAction = () -> {
                        try {
                            FileLog.d("DeleteMyMessages", "Starting deletion of " + finalMessageCount + " messages in " + finalLists.size() + " batches");
                            for (ArrayList<Integer> list : finalLists) {
                                for (int msgId : list) {
                                    AyuState.permitDeleteMessage(dialogId, msgId);
                                }
                                getMessagesController().deleteMessages(list, null, null, dialogId, 0, true, 0);
                            }
                            FileLog.d("DeleteMyMessages", "Deletion completed successfully");
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                    };
                    final int deletedCount = messageIds.size();
                    Runnable result = callback != null ? () -> callback.run(deletedCount, deleteAction) : deleteAction;
                    AndroidUtilities.runOnUIThread(result);
                } else {
                    FileLog.d("DeleteMyMessages", "No messages found to delete. Showing alert to user.");
                    AndroidUtilities.runOnUIThread(() -> {
                        if (fragment != null && fragment.getParentActivity() != null) {
                            AlertsCreator.showSimpleAlert(fragment, getString(R.string.NoResult));
                        }
                    });
                }
                if (mergeDialogId != 0) {
                    deleteUserHistoryWithSearch(fragment, mergeDialogId, 0, 0, before, 0, newestFirst, messageTypeFilter, null);
                }
            } catch (Exception e) {
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    if (fragment != null && fragment.getParentActivity() != null) {
                        AlertsCreator.showSimpleAlert(fragment, getString(R.string.ErrorOccurred));
                    }
                });
            }
        });
    }

    private interface SearchMessagesResultCallback {
        void run(int count, Runnable deleteAction);
    }

    private void doSearchMessages(BaseFragment fragment, CountDownLatch latch, ArrayList<Integer> messageIds, TLRPC.InputPeer peer, int replyMessageId, TLRPC.InputPeer fromId, int before, int offsetId, long hash, int limit, boolean newestFirst, int messageTypeFilter) {
        var req = new TLRPC.TL_messages_search();
        req.peer = peer;
        req.limit = 100;
        req.q = "";
        req.offset_id = offsetId;
        
        // Apply message type filter based on selection
        req.filter = getMessageTypeFilter(messageTypeFilter);
        
        long dialogId = DialogObject.getPeerDialogId(peer);
        boolean isMonoForum = getMessagesStorage().isMonoForum(dialogId);
        if (!isMonoForum) {
            req.from_id = fromId;
            req.flags |= 1;
        }
        if (replyMessageId != 0) {
            if (isMonoForum) {
                req.saved_peer_id = getMessagesController().getInputPeer(replyMessageId);
                req.flags |= 4;
            } else {
                req.top_msg_id = replyMessageId;
                req.flags |= 2;
            }
        }
        req.hash = hash;
        FileLog.d("DeleteMyMessages", "Sending search request. dialogId=" + dialogId + ", filter=" + messageTypeFilter + ", offsetId=" + offsetId);
        getConnectionsManager().sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.messages_Messages res) {
                FileLog.d("DeleteMyMessages", "Search response received. Messages count: " + res.messages.size());
                if (response instanceof TLRPC.TL_messages_messagesNotModified || res.messages.isEmpty()) {
                    latch.countDown();
                    return;
                }
                var newOffsetId = offsetId;
                for (TLRPC.Message message : res.messages) {
                    newOffsetId = Math.min(newOffsetId, message.id);
                    if (!message.out || message.post || message.date >= before) {
                        continue;
                    }
                    // Apply local filtering for message types that don't have dedicated Telegram filters
                    if (!matchesMessageTypeFilter(message, messageTypeFilter)) {
                        continue;
                    }
                    messageIds.add(message.id);
                    if (!newestFirst && limit > 0 && messageIds.size() > limit) {
                        // keep only the oldest messages when searching from newest to oldest
                        messageIds.remove(0);
                    }
                    if (newestFirst && limit > 0 && messageIds.size() >= limit) {
                        // for newest-first mode, we can stop once we have enough messages
                        latch.countDown();
                        return;
                    }
                }
                FileLog.d("DeleteMyMessages", "Current batch: " + messageIds.size() + " messages found. Continuing search...");
                doSearchMessages(fragment, latch, messageIds, peer, replyMessageId, fromId, before, newOffsetId, calcMessagesHash(res.messages), limit, newestFirst, messageTypeFilter);
            } else {
                if (error != null) {
                    FileLog.d("DeleteMyMessages", "Search error: " + error.text);
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.showSimpleAlert(fragment, getString(R.string.ErrorOccurred) + "\n" + error.text));
                }
                latch.countDown();
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }
    
    private TLRPC.MessagesFilter getMessageTypeFilter(int filterType) {
        try {
            switch (filterType) {
                case FILTER_TEXT:
                    return new TLRPC.TL_inputMessagesFilterEmpty(); // Text messages with empty filter
                case FILTER_VIDEO:
                    return new TLRPC.TL_inputMessagesFilterVideo();
                case FILTER_IMAGE:
                    return new TLRPC.TL_inputMessagesFilterPhotos();
                case FILTER_PHOTO_VIDEO:
                    return new TLRPC.TL_inputMessagesFilterPhotoVideo();
                case FILTER_STICKER:
                    return new TLRPC.TL_inputMessagesFilterEmpty(); // Stickers with empty filter
                case FILTER_FILES:
                    return new TLRPC.TL_inputMessagesFilterDocument();
                case FILTER_GIF:
                    return new TLRPC.TL_inputMessagesFilterGif();
                case FILTER_POLL:
                    return new TLRPC.TL_inputMessagesFilterPoll();
                case FILTER_LOCATION:
                    return new TLRPC.TL_inputMessagesFilterGeo();
                case FILTER_CONTACT:
                    return new TLRPC.TL_inputMessagesFilterContacts();
                case FILTER_REACTIONS:
                    // Telegram doesn't have a dedicated reactions filter, use empty filter and filter locally
                    return new TLRPC.TL_inputMessagesFilterEmpty();
                case FILTER_OTHER:
                    return new TLRPC.TL_inputMessagesFilterChatPhotos(); // Fallback for other
                case FILTER_ALL:
                default:
                    return new TLRPC.TL_inputMessagesFilterEmpty();
            }
        } catch (Exception e) {
            FileLog.e(e);
            return new TLRPC.TL_inputMessagesFilterEmpty(); // Fallback to empty filter on error
        }
    }
    
    private boolean matchesMessageTypeFilter(TLRPC.Message message, int filterType) {
        if (filterType == FILTER_ALL) {
            return true;
        }
        
        try {
            // Additional local filtering for message types that don't have dedicated Telegram filters
            if (filterType == FILTER_TEXT) {
                // Text messages: no media or has message text
                return message.media == null && message.message != null && !message.message.isEmpty();
            }
            
            if (filterType == FILTER_STICKER) {
                // Stickers: document with sticker attribute
                if (message.media == null || message.media.document == null || message.media.document.attributes == null) {
                    return false;
                }
                for (TLRPC.DocumentAttribute attr : message.media.document.attributes) {
                    if (attr instanceof TLRPC.TL_documentAttributeSticker) {
                        return true;
                    }
                }
                return false;
            }
            
            if (filterType == FILTER_POLL) {
                if (message.media == null) return false;
                if (message.media instanceof TLRPC.TL_messageMediaPoll) {
                    return ((TLRPC.TL_messageMediaPoll) message.media).poll != null;
                }
                return false;
            }
            
            if (filterType == FILTER_REACTIONS) {
                return message.reactions != null && !message.reactions.results.isEmpty();
            }
            
            // Local filtering for PHOTO_VIDEO since it's combined in Telegram filter
            if (filterType == FILTER_PHOTO_VIDEO) {
                if (message.media == null) return false;
                return message.media.photo != null || message.media.document != null && (message.media.document.mime_type != null && message.media.document.mime_type.startsWith("video/"));
            }
            
            return true; // For other types, rely on the Telegram filter
        } catch (Exception e) {
            FileLog.e(e);
            return true; // Fail-safe: include message if filtering fails
        }
    }

    private long calcMessagesHash(ArrayList<TLRPC.Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        long acc = 0;
        for (TLRPC.Message message : messages) {
            acc = MediaDataController.calcHash(acc, message.id);
        }
        return acc;
    }

    public static String getTextOrBase64(byte[] data) {
        try {
            return utf8Decoder.decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return Base64.encodeToString(data, Base64.NO_PADDING | Base64.NO_WRAP);
        }
    }

    public void clearMessageFiles(MessageObject messageObject, Runnable done) {
        Utilities.globalQueue.postRunnable(() -> {
            try {
                var files = getFilesToMessage(messageObject);
                for (File file : files) {
                    if (file.exists() && !file.delete()) {
                        file.deleteOnExit();
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            messageObject.checkMediaExistance();
            AndroidUtilities.runOnUIThread(done);
        });
    }

    public ArrayList<File> getFilesToMessage(MessageObject messageObject) {
        ArrayList<File> files = new ArrayList<>();
        files.add(new File(messageObject.messageOwner.attachPath));
        files.add(getFileLoader().getPathToMessage(messageObject.messageOwner));
        var document = messageObject.getDocument();
        if (document != null) {
            files.add(getFileLoader().getPathToAttach(document, false));
            files.add(getFileLoader().getPathToAttach(document, true));
        }
        var media = messageObject.messageOwner.media;
        if (media != null && !media.alt_documents.isEmpty()) {
            media.alt_documents.forEach(doc -> {
                files.add(getFileLoader().getPathToAttach(doc, false));
                files.add(getFileLoader().getPathToAttach(doc, true));
            });
        }
        return files;
    }

    public static boolean shouldSkipTranslation(String message) {
        if (TextUtils.isEmpty(message)) {
            return true;
        }
        final int len = message.length();
        int wordStart = 0;
        for (int i = 0; i <= len; i++) {
            if (i == len || Character.isWhitespace(message.charAt(i))) {
                if (wordStart < i) {
                    if (!isSkippedWord(message, wordStart, i)) {
                        return false;
                    }
                }
                wordStart = i + 1;
            }
        }
        return true;
    }

    private static boolean isSkippedWord(String message, int start, int end) {
        int wordLen = end - start;
        if (wordLen == 0) return true;
        char firstChar = message.charAt(start);
        return switch (firstChar) {
            case '@', '#', '/' -> true;
            case 'h' -> {
                if (wordLen >= 8) {
                    yield message.startsWith("http://", start) || message.startsWith("https://", start);
                } else if (wordLen == 7) {
                    yield message.startsWith("http://", start);
                }
                yield false;
            }
            case 'f' -> {
                if (wordLen >= 6) {
                    yield message.startsWith("ftp://", start);
                }
                yield false;
            }
            default -> false;
        };
    }

    public void detectLanguageNow(MessageObject messageObject) {
        final long dialogId = messageObject.getDialogId();
        LanguageDetector.detectLanguage(messageObject.messageOwner.message, lng -> AndroidUtilities.runOnUIThread(() -> {
            String detectedLanguage = lng;
            if (detectedLanguage == null) {
                detectedLanguage = UNKNOWN_LANGUAGE;
            }
            messageObject.messageOwner.originalLanguage = detectedLanguage;
            getMessagesStorage().updateMessageCustomParams(dialogId, messageObject.messageOwner);
        }), err -> AndroidUtilities.runOnUIThread(() -> {
            messageObject.messageOwner.originalLanguage = UNKNOWN_LANGUAGE;
            getMessagesStorage().updateMessageCustomParams(dialogId, messageObject.messageOwner);
        }));
    }

    public static String getMessagePlainText(MessageObject messageObject, MessageObject.GroupedMessages messageGroup) {
        if (messageGroup != null) {
            MessageObject captionMessage = messageGroup.findCaptionMessageObject();
            if (captionMessage != null && !TextUtils.isEmpty(captionMessage.caption)) {
                return captionMessage.caption.toString();
            }
        }
        if (messageObject == null) {
            return null;
        }
        if (messageObject.isPoll()) {
            TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll;
            StringBuilder pollText = new StringBuilder(poll.question.text).append("\n");
            for (TLRPC.PollAnswer answer : poll.answers) {
                pollText.append("\n\uD83D\uDD18 ");
                pollText.append(answer.text.text);
            }
            return pollText.toString();
        } else if (!TextUtils.isEmpty(messageObject.getVoiceTranscription())) {
            return messageObject.messageOwner.voiceTranscription;
        }
        return messageObject.messageOwner.message;
    }

    public static CharSequence getMessagePlainTextFull(MessageObject messageObject, MessageObject.GroupedMessages messageGroup) {
        StringBuilder text = new StringBuilder();
        if (messageGroup != null) {
            for (var groupedMessage : messageGroup.messages) {
                text.append(getMessagePlainText(groupedMessage, null));
            }
        }
        if (messageObject != null && messageObject.messageOwner != null) {
            if (messageObject.isPoll()) {
                TLRPC.Poll poll = ((TLRPC.TL_messageMediaPoll) messageObject.messageOwner.media).poll;
                StringBuilder pollText = new StringBuilder(poll.question.text).append("\n");
                for (TLRPC.PollAnswer answer : poll.answers) {
                    pollText.append("\n\uD83D\uDD18 ");
                    pollText.append(answer.text.text);
                }
                text.append(pollText);
            } else if (!TextUtils.isEmpty(messageObject.getVoiceTranscription())) {
                text.append(messageObject.messageOwner.voiceTranscription);
            } else {
                text.append(messageObject.messageOwner.message);
            }
        }
        return text.toString();
    }

    public static boolean messageObjectIsFile(int type, MessageObject messageObject) {
        boolean canSave = (type == 4 || type == 5 || type == 6 || type == 10);
        boolean downloading = messageObject.loadedFileSize > 0;
        if (type == 4 && messageObject.getDocument() == null) {
            return false;
        }
        return canSave || downloading;
    }

    // Merged from xyz.nextalone.nagram.helper.MessageHelper.kt

    private static final SpannableStringBuilder[] spannedStrings = new SpannableStringBuilder[5];
    private static final Pattern ZALGO_PATTERN = Pattern.compile("\\p{M}{4}");
    private static final Pattern ZALGO_CLEANUP = Pattern.compile("\\p{M}+");

    public static void addMessageToClipboard(MessageObject selectedObject, Runnable callback) {
        String path = getPathToMessage(selectedObject);
        if (!TextUtils.isEmpty(path)) {
            File file = new File(path);
            if (file.exists()) {
                addFileToClipboard(file, callback);
            }
        }
    }

    public static void addMessageToClipboardAsSticker(MessageObject selectedObject, Runnable callback) {
        String path = getPathToMessage(selectedObject);
        try {
            if (!TextUtils.isEmpty(path)) {
                Bitmap image = BitmapFactory.decodeFile(path);
                if (image != null) {
                    File file2 = path.endsWith(".jpg") ? new File(path.replace(".jpg", ".webp")) : new File(path + ".webp");
                    FileOutputStream stream = new FileOutputStream(file2);
                    if (Build.VERSION.SDK_INT >= 30) {
                        image.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, stream);
                    } else {
                        image.compress(Bitmap.CompressFormat.WEBP, 100, stream);
                    }
                    stream.close();
                    addFileToClipboard(file2, callback);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public static void addFileToClipboard(File file, Runnable callback) {
        try {
            Context context = ApplicationLoader.applicationContext;
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            Uri uri = FileProvider.getUriForFile(context, ApplicationLoader.getApplicationId() + ".provider", file);
            ClipData clip = ClipData.newUri(context.getContentResolver(), "label", uri);
            clipboard.setPrimaryClip(clip);
            if (callback != null) callback.run();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static String showForwardDate(MessageObject obj, String orig) {
        long date = obj.messageOwner != null && obj.messageOwner.fwd_from != null ? obj.messageOwner.fwd_from.date : 0;
        String day = LocaleController.formatDate(date);
        String time = LocaleController.getInstance().getFormatterDay().format(new Date(date * 1000L));
        boolean enabled = NaConfig.INSTANCE.getDateOfForwardedMsg().Bool();
        if (!enabled || date == 0) {
            return orig;
        } else {
            if (day.equals(time)) {
                return orig + " · " + day;
            } else {
                return orig + " · " + day + ' ' + time;
            }
        }
    }

    public static String zalgoFilter(String text) {
        CharSequence res = zalgoFilter((CharSequence) text);
        return res == null ? "" : res.toString();
    }

    public static CharSequence zalgoFilter(CharSequence text) {
        if (TextUtils.isEmpty(text)) return "";
        if (!NaConfig.INSTANCE.getZalgoFilter().Bool()) return text;
        if (text.length() < 4 || text.length() > 2048) return text;
        if (!ZALGO_PATTERN.matcher(text).find()) return text;

        if (!(text instanceof Spannable)) {
            return ZALGO_CLEANUP.matcher(text).replaceAll("");
        }

        SpannableStringBuilder ssb = new SpannableStringBuilder(text);
        Matcher matcher = ZALGO_CLEANUP.matcher(ssb);
        List<int[]> ranges = new ArrayList<>();

        while (matcher.find()) {
            ranges.add(new int[]{matcher.start(), matcher.end()});
        }

        for (int i = ranges.size() - 1; i >= 0; i--) {
            int[] range = ranges.get(i);
            ssb.delete(range[0], range[1]);
        }

        return ssb;
    }

    public static boolean containsMarkdown(CharSequence text) {
        CharSequence newText = AndroidUtilities.getTrimmedString(text);
        var message = new CharSequence[]{AndroidUtilities.getTrimmedString(newText)};
        var entities = MediaDataController.getInstance(UserConfig.selectedAccount).getEntities(message, true);
        return entities != null && !entities.isEmpty();
    }

    public static boolean canSendAsDice(String text, ChatActivity parentFragment, long dialog_id) {
        boolean canSendGames = true;
        if (DialogObject.isChatDialog(dialog_id)) {
            TLRPC.Chat chat = parentFragment.getMessagesController().getChat(-dialog_id);
            canSendGames = ChatObject.canSendStickers(chat);
        }
        // noinspection UnnecessaryUnicodeEscape
        return canSendGames && parentFragment.getMessagesController().diceEmojies.contains(text.replace("\ufe0f", ""));
    }

    private static String formatTime(int timestamp) {
        return LocaleController.formatString(R.string.formatDateAtTime,
                LocaleController.getInstance().getFormatterYear().format(new Date(timestamp * 1000L)),
                LocaleController.getInstance().getFormatterDay().format(new Date(timestamp * 1000L)));
    }

    public static CharSequence getTimeHintText(MessageObject messageObject) {
        SpannableStringBuilder text = new SpannableStringBuilder();
        if (spannedStrings[3] == null) {
            spannedStrings[3] = new SpannableStringBuilder("\u200B");
            spannedStrings[3].setSpan(new ColoredImageSpan(Theme.chat_timeHintSentDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        text.append(spannedStrings[3]);
        text.append(' ');
        text.append(formatTime(messageObject.messageOwner.date));
        if (messageObject.messageOwner.edit_date != 0) {
            text.append("\n");
            if (spannedStrings[1] == null) {
                spannedStrings[1] = new SpannableStringBuilder("\u200B");
                spannedStrings[1].setSpan(new ColoredImageSpan(Theme.chat_editDrawable), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            text.append(spannedStrings[1]);
            text.append(' ');
            text.append(formatTime(messageObject.messageOwner.edit_date));
        }
        if (messageObject.messageOwner.fwd_from != null && messageObject.messageOwner.fwd_from.date != 0) {
            text.append("\n");
            if (spannedStrings[4] == null) {
                spannedStrings[4] = new SpannableStringBuilder("\u200B");
                ColoredImageSpan span = new ColoredImageSpan(Theme.chat_timeHintForwardDrawable);
                span.setSize(AndroidUtilities.dp(12f));
                spannedStrings[4].setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            text.append(spannedStrings[4]);
            text.append(' ');
            text.append(formatTime(messageObject.messageOwner.fwd_from.date));
        }
        return text;
    }

    public boolean isBlockedUser(long senderId) {
        if (!NekoConfig.ignoreBlocked.Bool()) {
            return false;
        }
        return getMessagesController().blockePeers.indexOfKey(senderId) >= 0 || AyuFilter.isCustomFilteredPeer(senderId);
    }

    public boolean isBlockedOrFiltered(TLRPC.Message message) {
        if (message == null) {
            return false;
        }
        long fromId = MessageObject.getFromChatId(message);
        boolean blocked =  isBlockedUser(fromId) || AyuFilter.isBlockedChannel(fromId);
        return blocked || AyuFilter.isFiltered(new MessageObject(currentAccount, message, false, false), null);
    }

    public static void copyVideoFrameToClipboard(File videoFile, long positionMs, View bulletinContainer, Theme.ResourcesProvider resourcesProvider, Runnable fallbackAction) {
        Utilities.globalQueue.postRunnable(() -> {
            Bitmap bitmap = null;
            if (videoFile != null && videoFile.exists()) {
                bitmap = createVideoFrameBitmap(videoFile, positionMs);
            }
            if (bitmap == null) {
                if (fallbackAction != null) {
                    AndroidUtilities.runOnUIThread(fallbackAction);
                }
                return;
            }
            saveFrameBitmapToClipboard(bitmap, bulletinContainer, resourcesProvider);
        });
    }

    public static Bitmap createVideoFrameBitmap(File videoFile, long positionMs) {
        Bitmap bitmap = null;
        AnimatedFileDrawable fileDrawable = null;
        try {
            fileDrawable = new AnimatedFileDrawable(videoFile, true, 0, 0, null, null, null, 0, 0, true, null);
            bitmap = fileDrawable.getFrameAtTime(positionMs, true);
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (fileDrawable != null) {
                fileDrawable.recycle();
            }
        }
        if (bitmap == null) {
            bitmap = SendMessagesHelper.createVideoThumbnailAtTime(videoFile.getAbsolutePath(), positionMs * 1000);
        }
        return bitmap;
    }

    public static void saveFrameBitmapToClipboard(Bitmap bitmap, View bulletinContainer, Theme.ResourcesProvider resourcesProvider) {
        if (bitmap == null) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            File tempFile = null;
            boolean saved = false;
            try {
                File cacheDir = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE);
                tempFile = new File(cacheDir, "frame_" + System.currentTimeMillis() + ".png");
                try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                    saved = bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
            } catch (Exception e) {
                FileLog.e(e);
            } finally {
                try {
                    bitmap.recycle();
                } catch (Exception ignore) {
                }
            }
            if (!saved) {
                if (tempFile != null) {
                    try {
                        if (!tempFile.delete()) {
                            tempFile.deleteOnExit();
                        }
                    } catch (Exception ignore) {
                    }
                }
                return;
            }
            final File finalTempFile = tempFile;
            AndroidUtilities.runOnUIThread(() -> {
                Runnable callback = null;
                if (bulletinContainer instanceof FrameLayout) {
                    FrameLayout container = (FrameLayout) bulletinContainer;
                    callback = () -> BulletinFactory.of(container, resourcesProvider).createCopyBulletin(getString(R.string.PhotoCopied)).show();
                }
                addFileToClipboard(finalTempFile, callback);
            });
        });
    }

    public static ArrayList<TLRPC.MessageEntity> reparseMessageEntities(ArrayList<TLRPC.MessageEntity> translatedEntities) {
        if (translatedEntities == null) {
            return null;
        }
        if (!NaConfig.INSTANCE.getTranslatorKeepMarkdown().Bool()) {
            ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
            for (TLRPC.MessageEntity entity : translatedEntities) {
                boolean isMarkdownEntity = entity instanceof TLRPC.TL_messageEntitySpoiler;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityBold;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityItalic;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityCode;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityStrike;
                isMarkdownEntity |= entity instanceof TLRPC.TL_messageEntityUnderline;
                if (!isMarkdownEntity) {
                    entities.add(entity);
                }
            }
            return entities;
        }
        return translatedEntities;
    }

    public static ArrayList<TLRPC.MessageEntity> getEntitiesForText(MessageObject messageObject, CharSequence text, boolean summarized) {
        if (messageObject == null || messageObject.messageOwner == null) {
            return null;
        }
        final TLRPC.Message messageOwner = messageObject.messageOwner;
        if (summarized) {
            if (messageOwner.translated && messageOwner.translatedSummaryText != null) {
                return messageOwner.translatedSummaryText.entities;
            } else if (messageOwner.summaryText != null) {
                return messageOwner.summaryText.entities;
            }
            return null;
        }
        if (messageObject.translated) {
            if (messageOwner.voiceTranscriptionOpen) {
                return messageOwner.translatedVoiceTranscription != null ? messageOwner.translatedVoiceTranscription.entities : null;
            } else {
                return messageOwner.translatedText != null ? reparseMessageEntities(messageOwner.translatedText.entities) : null;
            }
        }
        if (messageOwner.translated && messageOwner.translatedText != null) {
            return mergeAppendTranslatedEntities(messageOwner.entities, messageOwner.translatedText, text);
        }
        return messageOwner.entities;
    }

    public static ArrayList<TLRPC.MessageEntity> mergeAppendTranslatedEntities(ArrayList<TLRPC.MessageEntity> baseEntities, TLRPC.TL_textWithEntities translatedText, CharSequence fullText) {
        if (fullText == null || translatedText == null || TextUtils.isEmpty(translatedText.text) || translatedText.entities == null) {
            return baseEntities;
        }
        final int fullLen = fullText.length();
        final int translatedLen = translatedText.text.length();
        if (translatedLen == 0 || fullLen < translatedLen) {
            return baseEntities;
        }
        final int translatedOffset = fullLen - translatedLen;
        final ArrayList<TLRPC.MessageEntity> translatedEntities = reparseMessageEntities(translatedText.entities);
        if (translatedEntities == null) {
            return baseEntities;
        }
        if (translatedOffset == 0) {
            return translatedEntities;
        }
        if (!TextUtils.regionMatches(fullText, translatedOffset, translatedText.text, 0, translatedLen)) {
            return baseEntities;
        }
        final ArrayList<TLRPC.MessageEntity> merged = new ArrayList<>();
        if (baseEntities != null) {
            merged.addAll(baseEntities);
        }
        for (int i = 0, size = translatedEntities.size(); i < size; i++) {
            final TLRPC.MessageEntity entity = translatedEntities.get(i);
            if (entity == null) {
                continue;
            }
            final TLRPC.MessageEntity copied = copyMessageEntity(entity);
            if (copied == null) {
                continue;
            }
            copied.offset += translatedOffset;
            if (copied.length <= 0 || copied.offset < 0 || copied.offset >= fullLen) {
                continue;
            }
            if (copied.offset + copied.length > fullLen) {
                copied.length = fullLen - copied.offset;
            }
            merged.add(copied);
        }
        return merged;
    }

    public static TLRPC.MessageEntity copyMessageEntity(TLRPC.MessageEntity entity) {
        if (entity == null) {
            return null;
        }
        NativeByteBuffer data = null;
        try {
            data = new NativeByteBuffer(entity.getObjectSize());
            entity.serializeToStream(data);
            data.rewind();
            final int constructor = data.readInt32(true);
            final TLRPC.MessageEntity result = TLRPC.MessageEntity.TLdeserialize(data, constructor, true);
            if (result instanceof TLRPC.TL_messageEntityCustomEmoji && entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                ((TLRPC.TL_messageEntityCustomEmoji) result).document = ((TLRPC.TL_messageEntityCustomEmoji) entity).document;
            }
            return result;
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            if (data != null) {
                data.reuse();
            }
        }
        return null;
    }

    public static final String TRANSLATION_SEPARATOR = "\n\n--------\n\n";

    public static boolean shouldKeepOriginalForManualTranslation(int translatorMode) {
        return translatorMode == 1 || translatorMode == 2;
    }

    public static String buildTranslatedDisplayText(String original, Object translated, boolean keepOriginal) {
        String translatedText;
        if (translated instanceof TLRPC.TL_textWithEntities) {
            translatedText = ((TLRPC.TL_textWithEntities) translated).text;
        } else {
            translatedText = String.valueOf(translated);
        }
        if (keepOriginal && !TextUtils.isEmpty(original)) {
            return translatedText + TRANSLATION_SEPARATOR + original;
        }
        return translatedText;
    }

    public static boolean isLegacyTranslatedSummary(TLRPC.TL_textWithEntities original, TLRPC.TL_textWithEntities translated) {
        if (original == null || translated == null || TextUtils.isEmpty(translated.text)) {
            return false;
        }
        return translated.text.contains(TRANSLATION_SEPARATOR);
    }

    public static boolean shouldKeepOriginalForDisplay(int translatorMode, boolean manualTranslated, boolean autoTranslated) {
        return translatorMode == 2 || (translatorMode == 1 && manualTranslated);
    }

    public boolean shouldKeepLocalMessageOnRestrictedEdit(TLRPC.Message oldMessage, TLRPC.Message message) {
        return false;
    }

    public void saveStickerToGallery(Context context, MessageObject messageObject, Utilities.Callback<Uri> callback) {
        saveStickerToGallery(context, messageObject);
        if (callback != null) {
            callback.run(null);
        }
    }

    public void saveStickerToGallery(Context context, TLRPC.Document document, Utilities.Callback<Uri> callback) {
        saveStickerToGallery(context, document);
        if (callback != null) {
            callback.run(null);
        }
    }
}

