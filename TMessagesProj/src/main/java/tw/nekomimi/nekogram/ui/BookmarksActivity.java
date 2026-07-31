package tw.nekomimi.nekogram.ui;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.radolyn.ayugram.database.entities.DeletedMessageFull;
import com.radolyn.ayugram.messages.AyuMessagesController;
import com.radolyn.ayugram.proprietary.AyuMessageUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.ActionBarMenuSubItem;
import org.telegram.ui.ActionBar.ActionBarPopupWindow;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatActionCell;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.Forum.ForumUtilities;
import org.telegram.ui.Components.NumberTextView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ChatScrimPopupContainerLayout;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SizeNotifierFrameLayout;
import org.telegram.ui.Components.blur3.BlurredBackgroundDrawableViewFactory;
import org.telegram.ui.Components.blur3.drawable.color.BlurredBackgroundColorProviderThemed;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceColor;
import org.telegram.ui.Components.chat.layouts.ChatActivitySideControlsButtonsLayout;
import org.telegram.ui.Components.inset.WindowInsetsStateHolder;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.SpecialForwardActivity;

import androidx.collection.LongSparseArray;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

import org.telegram.tgnet.TLObject;

import kotlin.Unit;
import xyz.nextalone.nagram.NaConfig;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.helpers.MessageHelper;
import tw.nekomimi.nekogram.llm.LlmConfig;
import tw.nekomimi.nekogram.translate.Translator;
import tw.nekomimi.nekogram.ui.cells.NekoMessageCell;
import xyz.nextalone.nagram.helper.BookmarksHelper;

public class BookmarksActivity extends NekoDelegateFragment {
    private static final int OPTION_SHOW_IN_CHAT = 1;
    private static final int OPTION_DELETE_BOOKMARK = 2;
    private static final int OPTION_COPY = 3;
    private static final int OPTION_COPY_PHOTO = 4;
    private static final int OPTION_COPY_PHOTO_AS_STICKER = 5;
    private static final int OPTION_DETAILS = 6;
    private static final int OPTION_SAVE_TO_GALLERY = 7;
    private static final int OPTION_SAVE_TO_DOWNLOADS = 8;
    private static final int OPTION_TRANSLATE = 9;
    private static final int OPTION_GO_TO_FIRST_MESSAGE = 10;
    private static final int OPTION_MENU_OTHER = 11;
    private static final int LOAD_BATCH_SIZE = 100;
    private static final int LOAD_MORE_THRESHOLD = 10;
    private static final int ACTION_MODE_FORWARD = 1001;
    private static final int ACTION_MODE_SPECIAL_FORWARD = 1002;
    private static final int ACTION_MODE_SELECT_BETWEEN = 1003;
    private static final int ACTION_MODE_REMOVE_BOOKMARK = 1004;
    private static final int ACTION_MODE_COPY = 1005;
    private static final int ACTION_MODE_OTHER = 1006;
    private static final int ACTION_MODE_FORWARD_NOQUOTE = 1007;
    private static final int ACTION_MODE_DETAILS = 1008;
    private static final int MAX_SELECTED_MESSAGES = 100;

    private final long dialogId;
    private final ArrayList<MessageObject> bookmarkedMessages = new ArrayList<>();
    private final ArrayList<MessageObject> filteredMessages = new ArrayList<>();
    private final SparseArray<MessageObject>[] selectedMessagesIds = new SparseArray[]{new SparseArray<>(), new SparseArray<>()};
    private final HashSet<Integer> missingMessageIds = new HashSet<>();
    private int[] allMessageIds = new int[0];
    private int loadedFromIndex;
    private boolean loadingMore;
    private int bookmarksLoadToken;

    private int rowCount;
    private RecyclerListView listView;
    private ChatActivitySideControlsButtonsLayout sideControlsButtonsLayout;
    private boolean pagedownButtonManuallyHidden;
    private ActionBarPopupWindow scrimPopupWindow;
    private ChatActionCell floatingDateView;
    private TextView emptyView;
    private Runnable showEmptyViewRunnable;
    private ActionBarMenuItem searchItem;
    private String searchQuery = "";
    private AnimatorSet floatingDateAnimation;
    private boolean scrollingFloatingDate;
    private final Runnable updateFloatingDateRunnable = this::updateFloatingDateView;
    private final WindowInsetsStateHolder windowInsetsStateHolder = new WindowInsetsStateHolder(this::checkInsets);
    private NumberTextView selectedMessagesCountTextView;
    private ActionBarMenuItem actionModeOtherItem;

    public BookmarksActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    protected RecyclerListView getMessageListView() {
        return listView;
    }

    private void checkInsets() {
        if (listView != null) {
            listView.setPadding(0, 0, 0, windowInsetsStateHolder.getCurrentNavigationBarInset() + dp(8));
        }
        updatePagedownButtonPosition();
    }

    private void updatePagedownButtonPosition() {
        if (sideControlsButtonsLayout == null) {
            return;
        }
        ViewGroup.LayoutParams lp = sideControlsButtonsLayout.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams params)) {
            return;
        }
        int bottomMargin = windowInsetsStateHolder.getCurrentNavigationBarInset() + dp(16);
        if (params.bottomMargin != bottomMargin) {
            params.bottomMargin = bottomMargin;
            sideControlsButtonsLayout.setLayoutParams(params);
        }
    }

    private void updatePagedownButtonVisibility(boolean animated) {
        if (sideControlsButtonsLayout == null || listView == null) {
            return;
        }
        boolean canScrollDown = rowCount > 0 && listView.canScrollVertically(1);
        if (!canScrollDown) {
            pagedownButtonManuallyHidden = false;
        }
        boolean show = canScrollDown && !pagedownButtonManuallyHidden;
        sideControlsButtonsLayout.showButton(ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN, show, animated);
    }

    private void onPageDownClicked() {
        if (listView == null || rowCount <= 0) {
            return;
        }
        pagedownButtonManuallyHidden = true;
        updatePagedownButtonVisibility(true);
        listView.smoothScrollToPosition(rowCount - 1);
    }

    private final RecyclerView.OnScrollListener listScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
            if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                pagedownButtonManuallyHidden = false;
                scrollingFloatingDate = true;
                updateFloatingDateView();
                showFloatingDateView();
            } else if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                pagedownButtonManuallyHidden = false;
                scrollingFloatingDate = false;
                hideFloatingDateView(true);
            }
            updatePagedownButtonVisibility(true);
            updateVisibleMessageCells();
        }

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            updateFloatingDateView();
            updatePagedownButtonVisibility(true);
            updateVisibleMessageCells();
            checkLoadMore();
        }
    };

    private void updateVisibleMessageCells() {
        if (listView != null) {
            updateVisibleChatMessageCells(listView);
        }
    }

    private static boolean hasAyuDeletedContent(DeletedMessageFull messageFull) {
        return messageFull != null && messageFull.message != null && (!TextUtils.isEmpty(messageFull.message.text) || !TextUtils.isEmpty(messageFull.message.mediaPath) || messageFull.message.documentSerialized != null);
    }

    private void updateBookmarks() {
        updateBookmarks(null);
    }

    private boolean loadingMissing;

    private void updateBookmarks(Runnable onComplete) {
        int accountId = getCurrentAccount();
        long userId = getUserConfig().getClientUserId();
        int token = ++bookmarksLoadToken;
        loadingMore = false;
        missingMessageIds.clear();
        Utilities.globalQueue.postRunnable(() -> {
            int[] messageIds = BookmarksHelper.getBookmarkedMessageIds(accountId, dialogId);
            Arrays.sort(messageIds);
            int startIndex = Math.max(0, messageIds.length - LOAD_BATCH_SIZE);
            ArrayList<MessageObject> loaded = loadMessages(accountId, userId, messageIds, startIndex, messageIds.length);

            AndroidUtilities.runOnUIThread(() -> {
                if (token != bookmarksLoadToken) {
                    return;
                }
                allMessageIds = messageIds;
                loadedFromIndex = startIndex;
                bookmarkedMessages.clear();
                bookmarkedMessages.addAll(loaded);
                applySearchFilter();
                if (onComplete != null) {
                    onComplete.run();
                }
                loadMissingMessagesServer();
            });
        });
    }

    private void loadMissingMessagesServer() {
        if (missingMessageIds.isEmpty() || loadingMissing) {
            return;
        }
        loadingMissing = true;
        int accountId = getCurrentAccount();
        ArrayList<Integer> idsToFetch = new ArrayList<>(missingMessageIds);
        if (idsToFetch.size() > 100) {
            idsToFetch = new ArrayList<>(idsToFetch.subList(0, 100));
        }
        final ArrayList<Integer> currentBatch = idsToFetch;

        Utilities.globalQueue.postRunnable(() -> {
            TLObject request;
            TLRPC.Chat chat = null;
            if (DialogObject.isChatDialog(dialogId)) {
                chat = getMessagesController().getChat(-dialogId);
                if (chat == null) {
                    chat = getMessagesStorage().getChatSync(-dialogId);
                    if (chat != null) {
                        getMessagesController().putChat(chat, true);
                    }
                }
            }
            if (ChatObject.isChannel(chat)) {
                TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                req.channel = getMessagesController().getInputChannel(chat);
                req.id.addAll(currentBatch);
                request = req;
            } else {
                TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                req.id.addAll(currentBatch);
                request = req;
            }

            getConnectionsManager().sendRequest(request, (response, error) -> {
                if (response instanceof TLRPC.messages_Messages messagesRes) {
                    getMessagesController().putUsers(messagesRes.users, false);
                    getMessagesController().putChats(messagesRes.chats, false);
                    getMessagesStorage().putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                    getMessagesStorage().putMessages(messagesRes, dialogId, -1, 0, false, 0, 0);

                    LongSparseArray<TLRPC.User> usersLocal = new LongSparseArray<>();
                    for (int a = 0; a < messagesRes.users.size(); a++) {
                        TLRPC.User u = messagesRes.users.get(a);
                        usersLocal.put(u.id, u);
                    }
                    LongSparseArray<TLRPC.Chat> chatsLocal = new LongSparseArray<>();
                    for (int a = 0; a < messagesRes.chats.size(); a++) {
                        TLRPC.Chat c = messagesRes.chats.get(a);
                        chatsLocal.put(c.id, c);
                    }

                    HashMap<Integer, MessageObject> fetchedMap = new HashMap<>();
                    for (int a = 0; a < messagesRes.messages.size(); a++) {
                        TLRPC.Message message = messagesRes.messages.get(a);
                        if (message != null && !(message instanceof TLRPC.TL_messageEmpty)) {
                            message.dialog_id = dialogId;
                            MessageObject obj = new MessageObject(accountId, message, usersLocal, chatsLocal, false, true);
                            obj.forceAvatar = true;
                            fetchedMap.put(message.id, obj);
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        loadingMissing = false;
                        boolean updated = false;
                        for (int i = 0; i < bookmarkedMessages.size(); i++) {
                            MessageObject current = bookmarkedMessages.get(i);
                            if (current != null && currentBatch.contains(current.getId())) {
                                MessageObject fetched = fetchedMap.get(current.getId());
                                if (fetched != null) {
                                    missingMessageIds.remove(current.getId());
                                    bookmarkedMessages.set(i, fetched);
                                    updated = true;
                                }
                            }
                        }
                        if (updated) {
                            applySearchFilter();
                        }
                        if (!missingMessageIds.isEmpty()) {
                            loadMissingMessagesServer();
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(() -> loadingMissing = false);
                }
            });
        });
    }

    private MessageObject createMissingMessagePlaceholder(int accountId, long userId, int messageId) {
        if (messageId == 0) {
            return null;
        }
        missingMessageIds.add(messageId);

        TLRPC.TL_message tl = new TLRPC.TL_message();
        tl.id = messageId;
        tl.dialog_id = dialogId;
        tl.date = ConnectionsManager.getInstance(accountId).getCurrentTime();
        tl.out = true;

        tl.from_id = new TLRPC.TL_peerUser();
        tl.from_id.user_id = userId;

        tl.peer_id = createPeerId(dialogId);

        String link = buildMessageLink(dialogId, messageId);
        tl.message = link == null ? getString(R.string.ShowInChat) : (getString(R.string.ShowInChat) + "\n" + link);
        if (link != null) {
            int offset = tl.message.indexOf(link);
            if (offset >= 0) {
                TLRPC.TL_messageEntityUrl entity = new TLRPC.TL_messageEntityUrl();
                entity.offset = offset;
                entity.length = link.length();
                tl.entities.add(entity);
            }
        }

        return new MessageObject(accountId, tl, false, true);
    }

    private ArrayList<MessageObject> loadMessages(int accountId, long userId, int[] messageIds, int startIndex, int endIndex) {
        int count = Math.max(0, endIndex - startIndex);
        ArrayList<MessageObject> loaded = new ArrayList<>(count);
        for (int i = startIndex; i < endIndex; i++) {
            int messageId = messageIds[i];
            TLRPC.Message message = MessagesStorage.getInstance(accountId).getMessage(dialogId, messageId);
            MessageObject messageObject = null;
            if (message != null) {
                messageObject = new MessageObject(accountId, message, false, true);
                if (messageObject.messageOwner.media != null) {
                    messageObject.messageOwner.media.ttl_seconds = 0;
                }
            } else {
                DeletedMessageFull deleted = AyuMessagesController.getInstance().getMessage(userId, dialogId, messageId);
                if (hasAyuDeletedContent(deleted)) {
                    var base = deleted.message;
                    var tl = new TLRPC.TL_message();
                    AyuMessageUtils.map(base, tl, accountId);
                    AyuMessageUtils.mapMedia(base, tl, accountId);
                    tl.ayuDeleted = true;
                    messageObject = new MessageObject(accountId, tl, false, true);
                }
            }
            if (messageObject == null) {
                messageObject = createMissingMessagePlaceholder(accountId, userId, messageId);
            }
            if (messageObject != null) {
                messageObject.forceAvatar = true;
                loaded.add(messageObject);
            }
        }
        loaded.sort(Comparator.comparingInt(MessageObject::getId));
        return loaded;
    }

    private TLRPC.Peer createPeerId(long dialogId) {
        TLRPC.Peer peerId = null;
        var peer = getMessagesController().getUserOrChat(dialogId);
        if (peer instanceof TLRPC.User user) {
            TLRPC.TL_peerUser p = new TLRPC.TL_peerUser();
            p.user_id = user.id;
            peerId = p;
        } else if (peer instanceof TLRPC.Chat chat) {
            if (ChatObject.isChannel(chat)) {
                TLRPC.TL_peerChannel p = new TLRPC.TL_peerChannel();
                p.channel_id = chat.id;
                peerId = p;
            } else {
                TLRPC.TL_peerChat p = new TLRPC.TL_peerChat();
                p.chat_id = chat.id;
                peerId = p;
            }
        }

        if (peerId != null) {
            return peerId;
        }

        if (DialogObject.isEncryptedDialog(dialogId)) {
            TLRPC.TL_peerUser p = new TLRPC.TL_peerUser();
            p.user_id = getUserConfig().getClientUserId();
            return p;
        } else if (dialogId > 0) {
            TLRPC.TL_peerUser p = new TLRPC.TL_peerUser();
            p.user_id = dialogId;
            return p;
        } else {
            TLRPC.TL_peerChat p = new TLRPC.TL_peerChat();
            p.chat_id = -dialogId;
            return p;
        }
    }

    private String buildMessageLink(long dialogId, int messageId) {
        if (messageId == 0 || DialogObject.isEncryptedDialog(dialogId)) {
            return null;
        }

        var peer = getMessagesController().getUserOrChat(dialogId);
        if (peer instanceof TLRPC.Chat chat) {
            if (!TextUtils.isEmpty(chat.username)) {
                return "https://t.me/" + chat.username + "/" + messageId;
            }
            if (ChatObject.isChannel(chat)) {
                return "https://t.me/c/" + chat.id + "/" + messageId;
            }
            return "tg://openmessage?chat_id=" + chat.id + "&message_id=" + messageId;
        } else if (peer instanceof TLRPC.User user) {
            return "tg://openmessage?user_id=" + user.id + "&message_id=" + messageId;
        }

        if (dialogId > 0) {
            return "tg://openmessage?user_id=" + dialogId + "&message_id=" + messageId;
        }
        return "https://t.me/c/" + (-dialogId) + "/" + messageId;
    }

    @Override
    public View createView(Context context) {
        var peer = getMessagesController().getUserOrChat(dialogId);
        String name;
        if (peer == null) {
            name = getString(R.string.BookmarksManager);
        } else if (peer instanceof TLRPC.User) {
            name = ((TLRPC.User) peer).first_name;
        } else if (peer instanceof TLRPC.Chat) {
            name = ((TLRPC.Chat) peer).title;
        } else {
            name = getString(R.string.BookmarksManager);
        }

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(name);
        updateActionBarCount();
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (actionBar.isActionModeShowed()) {
                        hideActionMode();
                    } else {
                        finishFragment();
                    }
                } else if (id == OPTION_GO_TO_FIRST_MESSAGE) {
                    scrollToFirstMessage();
                } else if (id == ACTION_MODE_FORWARD) {
                    openForward(false);
                } else if (id == ACTION_MODE_SPECIAL_FORWARD) {
                    openSpecialForwardActivity();
                } else if (id == ACTION_MODE_SELECT_BETWEEN) {
                    performSelectBetweenMessages();
                } else if (id == ACTION_MODE_REMOVE_BOOKMARK) {
                    removeSelectedBookmarks();
                } else if (id == ACTION_MODE_COPY) {
                    copySelectedMessages();
                } else if (id == ACTION_MODE_FORWARD_NOQUOTE) {
                    openForward(true);
                } else if (id == ACTION_MODE_DETAILS) {
                    openSelectedMessageDetails();
                }
            }
        });

        createActionMode();
        ActionBarMenu menu = actionBar.createMenu();
        searchItem = menu.addItem(0, R.drawable.ic_ab_search_solar).setIsSearchField(true);
        searchItem.setSearchFieldHint(getString(R.string.Search));
        searchItem.setActionBarMenuItemSearchListener(new ActionBarMenuItem.ActionBarMenuItemSearchListener() {
            @Override
            public void onSearchExpand() {
                searchItem.getSearchField().setText(searchQuery);
                searchItem.getSearchField().setSelection(searchItem.getSearchField().length());
            }

            @Override
            public void onSearchCollapse() {
                searchQuery = "";
                applySearchFilter();
            }

            @Override
            public void onTextChanged(EditText editText) {
                String newQuery = editText.getText().toString();
                if (!TextUtils.equals(searchQuery, newQuery)) {
                    searchQuery = newQuery;
                    applySearchFilter();
                }
            }

            @Override
            public void onSearchPressed(EditText editText) {
                searchQuery = editText.getText().toString();
                applySearchFilter();
            }
        });

        ActionBarMenuItem otherItem = menu.addItem(OPTION_MENU_OTHER, R.drawable.ic_ab_other);
        otherItem.setContentDescription(getString(R.string.AccDescrMoreOptions));
        otherItem.addSubItem(OPTION_GO_TO_FIRST_MESSAGE, R.drawable.msg_go_up, getString(R.string.ToTheMessage));

        SizeNotifierFrameLayout frameLayout = new ScrimFrameLayout(context) {
            @Override
            protected boolean isActionBarVisible() {
                return false;
            }

            @Override
            protected boolean isStatusBarVisible() {
                return false;
            }

            @Override
            protected boolean useRootView() {
                return false;
            }
        };

        fragmentView = frameLayout;
        frameLayout.setOccupyStatusBar(false);
        frameLayout.setBackgroundImage(Theme.getCachedWallpaper(), Theme.isWallpaperMotion());
        ViewCompat.setOnApplyWindowInsetsListener(fragmentView, (v, insets) -> {
            windowInsetsStateHolder.setInsets(insets);
            return WindowInsetsCompat.CONSUMED;
        });

        listView = new RecyclerListView(context);
        listView.setLayoutAnimation(null);

        LinearLayoutManager layoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        layoutManager.setStackFromEnd(true);

        listView.setLayoutManager(layoutManager);
        listView.setVerticalScrollBarEnabled(true);
        listView.setAdapter(new ListAdapter(context, getCurrentAccount()));
        setupMessageListItemAnimator(listView);
        listView.setSelectorType(9);
        listView.setSelectorDrawableColor(0);
        listView.setClipToPadding(false);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position, x, y) -> {
            if (view instanceof NekoMessageCell) {
                if (actionBar.isActionModeShowed()) {
                    MessageObject messageObject = ((NekoMessageCell) view).getMessageObject();
                    toggleSelectedMessage(messageObject, true);
                } else {
                    createMenu(view, x, y, position);
                }
            }
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (!(view instanceof NekoMessageCell) || position < 0 || position >= filteredMessages.size()) {
                return false;
            }
            MessageObject messageObject = ((NekoMessageCell) view).getMessageObject();
            if (messageObject == null) {
                return false;
            }
            if (!actionBar.isActionModeShowed()) {
                AndroidUtilities.hideKeyboard(fragmentView.findFocus());
                actionBar.showActionMode();
            }
            toggleSelectedMessage(messageObject, false);
            startMultiselect(position);
            return true;
        });
        listView.addOnScrollListener(listScrollListener);

        floatingDateView = new ChatActionCell(context) {
            @Override
            public boolean isFloating() {
                return true;
            }
        };
        floatingDateView.setCustomDate((int) (System.currentTimeMillis() / 1000), false, false);
        floatingDateView.setAlpha(0.0f);
        floatingDateView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        floatingDateView.setInvalidateColors(true);
        frameLayout.addView(floatingDateView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, 4, 0, 0));

        emptyView = new AppCompatTextView(context) {
            @Override
            protected void onDraw(Canvas canvas) {
                Theme.applyServiceShaderMatrix(getMeasuredWidth(), frameLayout.getBackgroundSizeY(), getX(), getY());
                Paint backgroundPaint = getThemedPaint(Theme.key_paint_chatActionBackground);
                AndroidUtilities.rectTmp.set(0, 0, getWidth(), getHeight());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(30), dp(30), backgroundPaint);
                if (Theme.hasGradientService()) {
                    canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(30), dp(30), Theme.getThemePaint(Theme.key_paint_chatActionBackgroundDarken, getResourceProvider()));
                }
                super.onDraw(canvas);
            }
        };
        emptyView.setText(getString(R.string.NoBookmarks));
        emptyView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
        emptyView.setTypeface(AndroidUtilities.bold());
        emptyView.setTextColor(Theme.getColor(Theme.key_chat_serviceText, getResourceProvider()));
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(View.GONE);
        emptyView.setPadding(dp(20), dp(4), dp(20), dp(6));
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        BlurredBackgroundSourceColor pagedownSourceColor = new BlurredBackgroundSourceColor();
        pagedownSourceColor.setColor(Color.TRANSPARENT);
        BlurredBackgroundDrawableViewFactory pagedownBackgroundDrawableFactory = new BlurredBackgroundDrawableViewFactory(pagedownSourceColor);
        BlurredBackgroundColorProviderThemed pagedownColorProvider = new BlurredBackgroundColorProviderThemed(getResourceProvider(), Theme.key_chat_messagePanelBackground);
        sideControlsButtonsLayout = new ChatActivitySideControlsButtonsLayout(context, getResourceProvider(), pagedownColorProvider, pagedownBackgroundDrawableFactory);
        sideControlsButtonsLayout.setOnClickListener((buttonId, v) -> {
            if (buttonId == ChatActivitySideControlsButtonsLayout.BUTTON_PAGE_DOWN) {
                onPageDownClicked();
            }
        });
        frameLayout.addView(sideControlsButtonsLayout, LayoutHelper.createFrame(57, 300, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 0, 16));
        updatePagedownButtonPosition();

        listView.post(updateFloatingDateRunnable);

        updateBookmarks(() -> {
            if (rowCount > 0 && listView != null) {
                listView.scrollToPosition(rowCount - 1);
                listView.post(this::updateVisibleMessageCells);
            }
            updatePagedownButtonVisibility(false);
        });

        return fragmentView;
    }

    private void createActionMode() {
        final ActionBarMenu actionMode = actionBar.createActionMode();
        actionMode.setBackgroundColor(0);

        selectedMessagesCountTextView = new NumberTextView(actionMode.getContext());
        selectedMessagesCountTextView.setTextSize(18);
        selectedMessagesCountTextView.setTypeface(AndroidUtilities.bold());
        selectedMessagesCountTextView.setTextColor(getThemedColor(Theme.key_actionBarActionModeDefaultIcon));
        selectedMessagesCountTextView.setOnTouchListener((v, event) -> true);
        actionMode.addView(selectedMessagesCountTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 65, 0, 0, 0));

        if (NaConfig.INSTANCE.getActionBarButtonSelectBetween().Bool()) {
            actionMode.addItemWithWidth(ACTION_MODE_SELECT_BETWEEN, R.drawable.ic_select_between, dp(54), getString(R.string.SelectBetween));
        }
        if (NaConfig.INSTANCE.getActionBarButtonCopy().Bool()) {
            actionMode.addItemWithWidth(ACTION_MODE_COPY, R.drawable.msg_copy, dp(54), getString(R.string.Copy));
        }
        if (NaConfig.INSTANCE.getActionBarButtonForward().Bool()) {
            actionMode.addItemWithWidth(ACTION_MODE_FORWARD, R.drawable.msg_forward, dp(54), getString(R.string.Forward));
        }
        if (NaConfig.INSTANCE.getSpecialForward().Bool()) {
            actionMode.addItemWithWidth(ACTION_MODE_SPECIAL_FORWARD, R.drawable.nk_special_forward, dp(54), getString(R.string.SpecialForward));
        }
        if (NaConfig.INSTANCE.getShowAddToBookmark().Bool()) {
            actionMode.addItemWithWidth(ACTION_MODE_REMOVE_BOOKMARK, R.drawable.msg_unfave, dp(54), getString(R.string.RemoveBookmark));
        }

        actionModeOtherItem = actionMode.addItemWithWidth(ACTION_MODE_OTHER, R.drawable.ic_ab_other, dp(54), getString(R.string.MessageMenu));
        if (actionModeOtherItem != null) {
            actionModeOtherItem.addSubItem(ACTION_MODE_FORWARD_NOQUOTE, R.drawable.msg_forward_noquote, getString(R.string.NoQuoteForward));
            actionModeOtherItem.addSubItem(ACTION_MODE_SPECIAL_FORWARD, R.drawable.nk_special_forward, getString(R.string.SpecialForward));
            actionModeOtherItem.addSubItem(ACTION_MODE_DETAILS, R.drawable.msg_info, getString(R.string.MessageDetails));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (fragmentView instanceof SizeNotifierFrameLayout) {
            ((SizeNotifierFrameLayout) fragmentView).onResume();
        }

        Bulletin.addDelegate(this, new Bulletin.Delegate() {
            @Override
            public int getBottomOffset(int tag) {
                return windowInsetsStateHolder.getCurrentNavigationBarInset();
            }
        });

        updateActionBarCount();
        updateBookmarks();
    }

    @Override
    public void onPause() {
        super.onPause();

        if (fragmentView instanceof SizeNotifierFrameLayout) {
            ((SizeNotifierFrameLayout) fragmentView).onPause();
        }

        Bulletin.removeDelegate(this);

        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            scrimPopupWindow = null;
        }

        if (actionBar != null && actionBar.isActionModeShowed()) {
            hideActionMode();
        }
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();

        Bulletin.removeDelegate(this);

        if (scrimPopupWindow != null) {
            scrimPopupWindow.dismiss();
            scrimPopupWindow = null;
        }

        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }

        if (showEmptyViewRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(showEmptyViewRunnable);
            showEmptyViewRunnable = null;
        }

        AndroidUtilities.cancelRunOnUIThread(updateFloatingDateRunnable);

        if (listView != null) {
            listView.removeCallbacks(updateFloatingDateRunnable);
            listView.removeOnScrollListener(listScrollListener);
            listView.setAdapter(null);
        }

        if (searchItem != null) {
            searchItem.setActionBarMenuItemSearchListener(null);
            searchItem = null;
        }
    }

    private void startMultiselect(int position) {
        if (listView == null) {
            return;
        }
        listView.startMultiselect(position, false, new RecyclerListView.onMultiSelectionChanged() {
            @Override
            public void onSelectionChanged(int position, boolean selected, float x, float y) {
                if (position < 0 || position >= filteredMessages.size()) {
                    return;
                }
                MessageObject messageObject = filteredMessages.get(position);
                if (messageObject == null) {
                    return;
                }
                if (selected) {
                    if (isSelectionLimitReached()) {
                        return;
                    }
                    selectedMessagesIds[0].put(messageObject.getId(), messageObject);
                } else {
                    selectedMessagesIds[0].remove(messageObject.getId());
                }
                updateActionModeTitle(true);
                updateVisibleSelection(true);
            }

            @Override
            public boolean canSelect(int position) {
                return position >= 0 && position < filteredMessages.size();
            }

            @Override
            public int checkPosition(int position, boolean selectionFromTop) {
                return position;
            }

            @Override
            public boolean limitReached() {
                return isSelectionLimitReached();
            }

            @Override
            public void getPaddings(int[] paddings) {
                paddings[0] = 0;
                paddings[1] = windowInsetsStateHolder.getCurrentNavigationBarInset();
            }

            @Override
            public void scrollBy(int dy) {
                if (listView != null) {
                    listView.scrollBy(0, dy);
                }
            }
        });
    }

    private boolean isSelectionLimitReached() {
        if (NekoConfig.unlimitedMessageSelection.Bool()) {
            return false;
        }
        return selectedMessagesIds[0].size() + selectedMessagesIds[1].size() >= MAX_SELECTED_MESSAGES;
    }

    private void toggleSelectedMessage(MessageObject messageObject, boolean animated) {
        if (messageObject == null) {
            return;
        }
        int messageId = messageObject.getId();
        if (messageId == 0) {
            return;
        }
        if (selectedMessagesIds[0].indexOfKey(messageId) >= 0) {
            selectedMessagesIds[0].remove(messageId);
        } else {
            if (isSelectionLimitReached()) {
                if (selectedMessagesCountTextView != null) {
                    AndroidUtilities.shakeView(selectedMessagesCountTextView);
                }
                return;
            }
            selectedMessagesIds[0].put(messageId, messageObject);
        }

        if (actionBar.isActionModeShowed() && getSelectedCount() == 0) {
            hideActionMode();
            return;
        }

        if (!actionBar.isActionModeShowed() && getSelectedCount() > 0) {
            AndroidUtilities.hideKeyboard(fragmentView.findFocus());
            actionBar.showActionMode();
        }
        updateActionModeTitle(animated);
        updateVisibleSelection(animated);
    }

    private int getSelectedCount() {
        return selectedMessagesIds[0].size() + selectedMessagesIds[1].size();
    }

    private void hideActionMode() {
        actionBar.hideActionMode();
        selectedMessagesIds[0].clear();
        selectedMessagesIds[1].clear();
        updateVisibleSelection(true);
    }

    private void updateActionModeTitle(boolean animated) {
        if (selectedMessagesCountTextView != null) {
            selectedMessagesCountTextView.setNumber(getSelectedCount(), animated);
        }
        ActionBarMenu actionMode = actionBar.getActionMode();
        if (actionMode != null) {
            ActionBarMenuItem selectBetweenItem = actionMode.getItem(ACTION_MODE_SELECT_BETWEEN);
            if (selectBetweenItem != null) {
                boolean canSelect = canSelectBetweenMessages();
                selectBetweenItem.setEnabled(canSelect);
                selectBetweenItem.setAlpha(canSelect ? 1f : 0.5f);
            }
            ActionBarMenuItem copyItem = actionMode.getItem(ACTION_MODE_COPY);
            if (copyItem != null) {
                boolean canCopy = canCopySelectedMessages();
                copyItem.setEnabled(canCopy);
                copyItem.setAlpha(canCopy ? 1f : 0.5f);
            }
            ActionBarMenuItem removeBookmarkItem = actionMode.getItem(ACTION_MODE_REMOVE_BOOKMARK);
            if (removeBookmarkItem != null) {
                boolean canRemove = getSelectedCount() > 0;
                removeBookmarkItem.setEnabled(canRemove);
                removeBookmarkItem.setAlpha(canRemove ? 1f : 0.5f);
            }
        }

        if (actionModeOtherItem != null) {
            ActionBarMenuSubItem noQuoteItem = actionModeOtherItem.getSubItem(ACTION_MODE_FORWARD_NOQUOTE);
            if (noQuoteItem != null) {
                boolean canShow = NaConfig.INSTANCE.getShowNoQuoteForward().Bool() && canForwardSelectedMessages();
                noQuoteItem.setVisibility(canShow ? View.VISIBLE : View.GONE);
            }
            ActionBarMenuSubItem specialForwardItem = actionModeOtherItem.getSubItem(ACTION_MODE_SPECIAL_FORWARD);
            if (specialForwardItem != null) {
                specialForwardItem.setVisibility(NaConfig.INSTANCE.getSpecialForward().Bool() ? View.VISIBLE : View.GONE);
            }
            ActionBarMenuSubItem detailsItem = actionModeOtherItem.getSubItem(ACTION_MODE_DETAILS);
            if (detailsItem != null) {
                detailsItem.setVisibility(getSelectedCount() == 1 ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void updateVisibleSelection(boolean animated) {
        if (listView == null) {
            return;
        }
        boolean show = actionBar.isActionModeShowed();
        for (int i = 0, count = listView.getChildCount(); i < count; i++) {
            View child = listView.getChildAt(i);
            if (!(child instanceof ChatMessageCell cell)) {
                continue;
            }
            MessageObject messageObject = cell.getMessageObject();
            boolean checked = messageObject != null && selectedMessagesIds[0].indexOfKey(messageObject.getId()) >= 0;
            cell.setCheckBoxVisible(show, animated);
            cell.setChecked(checked, checked, animated);
        }
    }

    private boolean isSelectableBetweenMessage(MessageObject message, int begin, int end) {
        if (message == null) {
            return false;
        }
        int msgId = message.getId();
        if (msgId <= begin || msgId >= end || msgId == 0) {
            return false;
        }
        return selectedMessagesIds[0].indexOfKey(msgId) < 0;
    }

    private boolean canSelectBetweenMessages() {
        int[] bounds = tw.nekomimi.nekogram.helpers.ChatsHelper.getSelectBetweenBounds(selectedMessagesIds);
        if (bounds == null) {
            return false;
        }
        int begin = bounds[0];
        int end = bounds[1];
        for (int i = 0; i < filteredMessages.size(); i++) {
            if (isSelectableBetweenMessage(filteredMessages.get(i), begin, end)) {
                return true;
            }
        }
        return false;
    }

    private void performSelectBetweenMessages() {
        int[] bounds = tw.nekomimi.nekogram.helpers.ChatsHelper.getSelectBetweenBounds(selectedMessagesIds);
        if (bounds == null) {
            return;
        }
        int begin = bounds[0];
        int end = bounds[1];
        for (int i = 0; i < filteredMessages.size(); i++) {
            MessageObject message = filteredMessages.get(i);
            if (!isSelectableBetweenMessage(message, begin, end)) {
                continue;
            }
            if (isSelectionLimitReached()) {
                break;
            }
            selectedMessagesIds[0].put(message.getId(), message);
        }
        if (!actionBar.isActionModeShowed() && getSelectedCount() > 0) {
            actionBar.showActionMode();
        }
        updateActionModeTitle(true);
        updateVisibleSelection(true);
    }

    private ArrayList<MessageObject> getSelectedMessagesSorted() {
        ArrayList<MessageObject> result = new ArrayList<>(selectedMessagesIds[0].size());
        for (int i = 0; i < selectedMessagesIds[0].size(); i++) {
            MessageObject messageObject = selectedMessagesIds[0].valueAt(i);
            if (messageObject != null) {
                result.add(messageObject);
            }
        }
        result.sort(Comparator.comparingInt(MessageObject::getId));
        return result;
    }

    private ArrayList<MessageObject> getSelectedCopyableMessagesSorted() {
        ArrayList<MessageObject> selected = getSelectedMessagesSorted();
        if (selected.isEmpty()) {
            return selected;
        }
        ArrayList<MessageObject> result = new ArrayList<>(selected.size());
        for (int i = 0; i < selected.size(); i++) {
            MessageObject msg = selected.get(i);
            if (msg == null || msg.getId() == 0) {
                continue;
            }
            if (missingMessageIds.contains(msg.getId())) {
                continue;
            }
            if (msg.messageOwner != null && msg.messageOwner.ayuDeleted) {
                continue;
            }
            String text = msg.messageOwner != null ? msg.messageOwner.message : null;
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            result.add(msg);
        }
        result.sort(Comparator.comparingInt(MessageObject::getId));
        return result;
    }

    private boolean canCopySelectedMessages() {
        return !getSelectedCopyableMessagesSorted().isEmpty();
    }

    private void copySelectedMessages() {
        ArrayList<MessageObject> list = getSelectedCopyableMessagesSorted();
        if (list.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            MessageObject msg = list.get(i);
            String text = msg != null && msg.messageOwner != null ? msg.messageOwner.message : null;
            if (TextUtils.isEmpty(text)) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("\n\n");
            }
            sb.append(text);
        }
        if (sb.length() <= 0) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
            return;
        }
        AndroidUtilities.addToClipboard(sb.toString());
        BulletinFactory.of(this).createCopyBulletin(getString(R.string.MessageCopied)).show();
    }

    private void removeSelectedBookmarks() {
        if (getParentActivity() == null) {
            return;
        }
        if (getSelectedCount() <= 0) {
            return;
        }
        try {
            int accountId = getCurrentAccount();
            HashSet<Integer> removeIds = new HashSet<>();
            for (int i = 0; i < selectedMessagesIds[0].size(); i++) {
                int messageId = selectedMessagesIds[0].keyAt(i);
                if (messageId == 0) {
                    continue;
                }
                removeIds.add(messageId);
            }
            if (removeIds.isEmpty()) {
                return;
            }
            for (Integer messageId : removeIds) {
                BookmarksHelper.removeBookmark(accountId, dialogId, messageId);
                removeMessageIdFromAllIds(messageId);
                missingMessageIds.remove(messageId);
            }
            bookmarkedMessages.removeIf(m -> m != null && removeIds.contains(m.getId()));
            filteredMessages.removeIf(m -> m != null && removeIds.contains(m.getId()));
            rowCount = filteredMessages.size();
            notifyAdapterDataChanged();
            updateActionBarCount();
            updateEmptyView(false);
            if (listView != null) {
                listView.post(() -> {
                    updatePagedownButtonVisibility(false);
                    updateVisibleMessageCells();
                });
            } else {
                updatePagedownButtonVisibility(false);
            }
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
        } finally {
            hideActionMode();
        }
    }

    private ArrayList<MessageObject> getSelectedForwardableMessages(boolean includeGroups) {
        ArrayList<MessageObject> selected = getSelectedMessagesSorted();
        if (selected.isEmpty()) {
            return selected;
        }

        ArrayList<MessageObject> resolved = new ArrayList<>();
        HashSet<Integer> addedIds = new HashSet<>();
        for (int i = 0; i < selected.size(); i++) {
            MessageObject msg = selected.get(i);
            if (msg == null || msg.getId() == 0) {
                continue;
            }
            if (missingMessageIds.contains(msg.getId())) {
                continue;
            }
            if (msg.messageOwner != null && msg.messageOwner.ayuDeleted) {
                continue;
            }
            long groupId = msg.getGroupId();
            if (includeGroups && groupId != 0) {
                for (int j = 0; j < bookmarkedMessages.size(); j++) {
                    MessageObject candidate = bookmarkedMessages.get(j);
                    if (candidate != null && candidate.getGroupId() == groupId) {
                        int id = candidate.getId();
                        if (id != 0 && !missingMessageIds.contains(id) && (candidate.messageOwner == null || !candidate.messageOwner.ayuDeleted) && addedIds.add(id)) {
                            resolved.add(candidate);
                        }
                    }
                }
                continue;
            }
            if (addedIds.add(msg.getId())) {
                resolved.add(msg);
            }
        }
        resolved.sort(Comparator.comparingInt(MessageObject::getId));
        return resolved;
    }

    private boolean hasSelectedNoForwardsMessage(ArrayList<MessageObject> selected) {
        for (int i = 0; i < selected.size(); i++) {
            MessageObject msg = selected.get(i);
            if (msg != null && msg.messageOwner != null && msg.messageOwner.noforwards) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSelectedAyuDeletedMessage(ArrayList<MessageObject> selected) {
        for (int i = 0; i < selected.size(); i++) {
            MessageObject msg = selected.get(i);
            if (msg != null && msg.messageOwner != null && msg.messageOwner.ayuDeleted) {
                return true;
            }
        }
        return false;
    }

    private boolean canForwardSelectedMessages() {
        ArrayList<MessageObject> forwardMessages = getSelectedForwardableMessages(true);
        if (forwardMessages.isEmpty()) {
            return false;
        }
        boolean peerNoForwards = getMessagesController().isPeerNoForwards(dialogId, true);
        boolean hasNoForwards = hasSelectedNoForwardsMessage(forwardMessages);
        boolean hasAyuDeleted = hasSelectedAyuDeletedMessage(forwardMessages);

        boolean blockForward = (peerNoForwards || hasNoForwards || hasAyuDeleted) && !NaConfig.INSTANCE.getAllowForwardingRestriction().Bool();
        if (NaConfig.INSTANCE.getAllowForwardingRestriction().Bool()) {
            blockForward = hasAyuDeleted;
        }
        return !blockForward;
    }

    private void openForward(boolean noQuote) {
        if (getParentActivity() == null) {
            return;
        }
        ArrayList<MessageObject> forwardMessages = getSelectedForwardableMessages(true);
        if (forwardMessages.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
            return;
        }

        boolean peerNoForwards = getMessagesController().isPeerNoForwards(dialogId, true);
        boolean hasNoForwards = hasSelectedNoForwardsMessage(forwardMessages);
        boolean hasAyuDeleted = hasSelectedAyuDeletedMessage(forwardMessages);

        boolean blockForward = (peerNoForwards || hasNoForwards || hasAyuDeleted) && !NaConfig.INSTANCE.getAllowForwardingRestriction().Bool();
        if (NaConfig.INSTANCE.getAllowForwardingRestriction().Bool()) {
            blockForward = hasAyuDeleted;
        }
        if (blockForward) {
            String str;
            if (peerNoForwards) {
                if (dialogId > 0) {
                    str = getString(R.string.ForwardsRestrictedInfoUser);
                } else {
                    TLRPC.Chat chat = getMessagesController().getChat(-dialogId);
                    if (chat != null && ChatObject.isChannel(chat) && !chat.megagroup) {
                        str = getString(R.string.ForwardsRestrictedInfoChannel);
                    } else {
                        str = getString(R.string.ForwardsRestrictedInfoGroup);
                    }
                }
            } else {
                str = getString(R.string.ForwardsRestrictedInfoBot);
                if (hasAyuDeleted) {
                    str = getString(R.string.ForwardsRestrictedInfoAyuDeleted);
                }
            }
            BulletinFactory.of(this).createErrorBulletin(str).show();
            return;
        }

        Bundle args = new Bundle();
        args.putBoolean("onlySelect", true);
        args.putBoolean("canSelectTopics", true);
        args.putInt("dialogsType", DialogsActivity.DIALOGS_TYPE_FORWARD);
        args.putInt("messagesCount", forwardMessages.size());
        DialogsActivity fragment = new DialogsActivity(args);
        fragment.setDelegate((fragment1, dids, msgText, param, notify, scheduleDate, scheduleRepeatPeriod, topicsFragment) -> {
            try {
                if (dids.size() > 1 || dids.get(0).dialogId == getUserConfig().getClientUserId() || msgText != null) {
                    for (int a = 0; a < dids.size(); a++) {
                        long did = dids.get(a).dialogId;
                        if (msgText != null) {
                            SendMessagesHelper.getInstance(getCurrentAccount()).sendMessage(msgText.toString(), did, null, null, null, true, null, null, null, notify, scheduleDate, 0, null, false);
                        }
                        SendMessagesHelper.getInstance(getCurrentAccount()).sendMessage(forwardMessages, did, noQuote, false, notify, scheduleDate, 0);
                    }
                    fragment1.finishFragment();
                } else {
                    long did = dids.get(0).dialogId;
                    Bundle args1 = new Bundle();
                    args1.putBoolean("scrollToTopOnResume", true);
                    args1.putBoolean("forward_noquote", noQuote);
                    if (DialogObject.isEncryptedDialog(did)) {
                        args1.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                    } else {
                        if (DialogObject.isUserDialog(did)) {
                            args1.putLong("user_id", did);
                        } else {
                            args1.putLong("chat_id", -did);
                        }
                        if (!getMessagesController().checkCanOpenChat(args1, fragment1)) {
                            return true;
                        }
                    }
                    getNotificationCenter().postNotificationName(NotificationCenter.closeChats);
                    ChatActivity chatActivity = new ChatActivity(args1);
                    ForumUtilities.applyTopic(chatActivity, dids.get(0));
                    fragment1.presentFragment(chatActivity, true);
                    chatActivity.showFieldPanelForForward(true, forwardMessages);
                }
            } catch (Exception e) {
                FileLog.e(e);
                BulletinFactory.of(this).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
            }
            hideActionMode();
            return true;
        });
        presentFragment(fragment);
    }

    private void openSpecialForwardActivity() {
        ArrayList<MessageObject> messages = getSelectedForwardableMessages(true);
        if (messages.isEmpty()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
            return;
        }
        boolean hasAyuDeleted = hasSelectedAyuDeletedMessage(messages);
        if (hasAyuDeleted && !NaConfig.INSTANCE.getAllowForwardingRestriction().Bool()) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ForwardsRestrictedInfoAyuDeleted)).show();
            return;
        }
        try {
            presentFragment(new SpecialForwardActivity(messages));
            hideActionMode();
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
        }
    }

    private void openSelectedMessageDetails() {
        if (getSelectedCount() != 1) {
            return;
        }
        MessageObject msg = selectedMessagesIds[0].size() == 1 ? selectedMessagesIds[0].valueAt(0) : null;
        if (msg == null) {
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
            return;
        }
        try {
            presentFragment(new MessageDetailsActivity(msg, null));
            hideActionMode();
        } catch (Exception e) {
            FileLog.e(e);
            BulletinFactory.of(this).createErrorBulletin(getString(R.string.ErrorOccurred)).show();
        }
    }

    private void createMenu(View v, float x, float y, int position) {
        final MessageObject msg = (v instanceof ChatMessageCell) ? ((ChatMessageCell) v).getMessageObject() : null;
        if (msg == null || getParentActivity() == null) {
            return;
        }

        ArrayList<CharSequence> items = new ArrayList<>();
        ArrayList<Integer> options = new ArrayList<>();
        ArrayList<Integer> icons = new ArrayList<>();

        items.add(getString(R.string.ShowInChat));
        icons.add(R.drawable.msg_openin);
        options.add(OPTION_SHOW_IN_CHAT);

        items.add(getString(R.string.RemoveBookmark));
        icons.add(R.drawable.msg_unfave);
        options.add(OPTION_DELETE_BOOKMARK);

        String textToCopy = msg.messageOwner != null ? msg.messageOwner.message : null;
        if (!TextUtils.isEmpty(textToCopy)) {
            items.add(getString(R.string.Copy));
            icons.add(R.drawable.msg_copy);
            options.add(OPTION_COPY);
        }

        boolean isStaticSticker = msg.isSticker() && !msg.isAnimatedSticker() && !msg.isVideoSticker();
        if ((msg.isPhoto() || isStaticSticker) && !msg.needDrawBluredPreview()) {
            if (msg.isPhoto()) {
                items.add(getString(R.string.CopyPhoto));
            } else {
                items.add(getString(R.string.CopySticker));
            }
            icons.add(R.drawable.msg_copy_photo);
            options.add(OPTION_COPY_PHOTO);

            if (msg.isPhoto()) {
                items.add(getString(R.string.CopyPhotoAsSticker));
                icons.add(R.drawable.msg_copy_photo);
                options.add(OPTION_COPY_PHOTO_AS_STICKER);
            }
        }

        if ((msg.isPhoto() || msg.isVideo() || msg.isGif()) && !msg.needDrawBluredPreview()) {
            items.add(getString(R.string.SaveToGallery));
            icons.add(R.drawable.msg_gallery);
            options.add(OPTION_SAVE_TO_GALLERY);
        }

        if (msg.isDocument() || msg.isMusic() || msg.isVoice()) {
            items.add(msg.isMusic() ? getString(R.string.SaveToMusic) : getString(R.string.SaveToDownloads));
            icons.add(R.drawable.msg_download);
            options.add(OPTION_SAVE_TO_DOWNLOADS);
        }

        String textToTranslate = msg.messageOwner != null ? msg.messageOwner.message : null;
        if (!TextUtils.isEmpty(textToTranslate) || msg.isPoll()) {
            boolean translated = msg.messageOwner != null && (msg.messageOwner.translated || msg.messageOwner.translatedPoll != null);
            items.add(getString(translated ? R.string.HideTranslation : R.string.Translate));
            icons.add(LlmConfig.llmIsDefaultProvider() ? R.drawable.magic_stick_solar : R.drawable.ic_translate);
            options.add(OPTION_TRANSLATE);
        }

        items.add(getString(R.string.MessageDetails));
        icons.add(R.drawable.msg_info);
        options.add(OPTION_DETAILS);

        ActionBarPopupWindow.ActionBarPopupWindowLayout popupLayout = new ActionBarPopupWindow.ActionBarPopupWindowLayout(getParentActivity(), R.drawable.popup_fixed_alert4, getResourceProvider(), 0);
        popupLayout.setMinimumWidth(dp(200));
        popupLayout.setBackgroundColor(getThemedColor(Theme.key_actionBarDefaultSubmenuBackground));

        for (int a = 0, N = items.size(); a < N; ++a) {
            ActionBarMenuSubItem cell = new ActionBarMenuSubItem(getParentActivity(), a == 0, a == N - 1, getResourceProvider());
            cell.setMinimumWidth(dp(200));
            cell.setTextAndIcon(items.get(a), icons.get(a));
            final Integer option = options.get(a);
            popupLayout.addView(cell);
            cell.setOnClickListener(v1 -> {
                if (option == OPTION_SHOW_IN_CHAT) {
                    Bundle args = new Bundle();
                    long did = msg.getDialogId();
                    if (DialogObject.isEncryptedDialog(did)) {
                        args.putInt("enc_id", DialogObject.getEncryptedChatId(did));
                    } else if (DialogObject.isUserDialog(did)) {
                        args.putLong("user_id", did);
                    } else {
                        TLRPC.Chat chat = getMessagesController().getChat(-did);
                        if (chat != null && chat.migrated_to != null) {
                            args.putLong("migrated_to", did);
                            did = -chat.migrated_to.channel_id;
                        }
                        args.putLong("chat_id", -did);
                    }
                    args.putInt("message_id", msg.getId());
                    NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.closeChats);
                    presentFragment(new ChatActivity(args), false, false);
                } else if (option == OPTION_DELETE_BOOKMARK) {
                    BookmarksHelper.removeBookmark(getCurrentAccount(), dialogId, msg.getId());
                    removeMessageIdFromAllIds(msg.getId());
                    if (position >= 0 && position < filteredMessages.size()) {
                        MessageObject toRemove = filteredMessages.remove(position);
                        bookmarkedMessages.remove(toRemove);
                        rowCount = filteredMessages.size();
                        notifyMessageListItemRemoved(listView, position);
                        updateActionBarCount();
                        updateEmptyView(rowCount == 0);
                        if (listView != null) {
                            listView.post(() -> {
                                updatePagedownButtonVisibility(false);
                                updateVisibleMessageCells();
                            });
                        } else {
                            updatePagedownButtonVisibility(false);
                        }
                    } else {
                        updateBookmarks();
                    }
                } else if (option == OPTION_COPY) {
                    String text = msg.messageOwner != null ? msg.messageOwner.message : null;
                    if (!TextUtils.isEmpty(text)) {
                        AndroidUtilities.addToClipboard(text);
                        BulletinFactory.of(this).createCopyBulletin(getString(R.string.MessageCopied)).show();
                    }
                } else if (option == OPTION_COPY_PHOTO) {
                    MessageHelper.addMessageToClipboard(msg, () -> BulletinFactory.of(this).createCopyBulletin(getString(R.string.PhotoCopied)).show());
                } else if (option == OPTION_COPY_PHOTO_AS_STICKER) {
                    MessageHelper.addMessageToClipboardAsSticker(msg, () -> BulletinFactory.of(this).createCopyBulletin(getString(R.string.PhotoCopied)).show());
                } else if (option == OPTION_SAVE_TO_GALLERY) {
                    String path = null;
                    if (!TextUtils.isEmpty(msg.messageOwner.attachPath)) {
                        File temp = new File(msg.messageOwner.attachPath);
                        if (temp.exists()) {
                            path = msg.messageOwner.attachPath;
                        }
                    }
                    if (TextUtils.isEmpty(path)) {
                        File f = FileLoader.getInstance(getCurrentAccount()).getPathToMessage(msg.messageOwner);
                        if (f != null && f.exists()) {
                            path = f.getPath();
                        }
                    }
                    if (!TextUtils.isEmpty(path)) {
                        MediaController.saveFile(msg, path, getParentActivity(), msg.isVideo() ? 1 : 0, null, null, uri -> {
                            if (getParentActivity() != null) {
                                BulletinFactory.of(this).createDownloadBulletin(
                                        msg.isVideo() ? BulletinFactory.FileType.VIDEO : BulletinFactory.FileType.PHOTO,
                                        getResourceProvider()
                                ).show();
                            }
                        });
                    }
                } else if (option == OPTION_SAVE_TO_DOWNLOADS) {
                    ArrayList<MessageObject> messageObjects = new ArrayList<>();
                    messageObjects.add(msg);
                    MediaController.saveFilesFromMessages(getParentActivity(), getAccountInstance(), messageObjects, (count) -> {
                        if (count > 0) {
                            BulletinFactory.of(this).createDownloadBulletin(
                                    msg.isMusic() ? BulletinFactory.FileType.AUDIOS : BulletinFactory.FileType.UNKNOWNS,
                                    count,
                                    getResourceProvider()
                            ).show();
                        }
                    });
                } else if (option == OPTION_DETAILS) {
                    presentFragment(new MessageDetailsActivity(msg, null));
                } else if (option == OPTION_TRANSLATE) {
                    toggleOrTranslate((ChatMessageCell) v, msg, null);
                }

                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                }
            });
            if (option == OPTION_TRANSLATE) {
                cell.setOnLongClickListener(v1 -> {
                    if (msg.messageOwner != null && (msg.messageOwner.translated || msg.messageOwner.translatedPoll != null)) {
                        return true;
                    }
                    Translator.showTargetLangSelect(cell, false, false, (locale) -> {
                        if (scrimPopupWindow != null) {
                            scrimPopupWindow.dismiss();
                            scrimPopupWindow = null;
                        }
                        toggleOrTranslate((ChatMessageCell) v, msg, locale);
                        return Unit.INSTANCE;
                    });
                    return true;
                });
            }
        }

        ChatScrimPopupContainerLayout scrimPopupContainerLayout = new ChatScrimPopupContainerLayout(fragmentView.getContext()) {
            @Override
            public boolean dispatchKeyEvent(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
                    closeMenu();
                }
                return super.dispatchKeyEvent(event);
            }

            @Override
            public boolean dispatchTouchEvent(MotionEvent ev) {
                boolean b = super.dispatchTouchEvent(ev);
                if (ev.getAction() == MotionEvent.ACTION_DOWN && !b) {
                    closeMenu();
                }
                return b;
            }

            private void closeMenu() {
                if (scrimPopupWindow != null) {
                    scrimPopupWindow.dismiss();
                }
            }
        };
        scrimPopupContainerLayout.addView(popupLayout, LayoutHelper.createLinearRelatively(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT, 0, 0, 0, 0));
        scrimPopupContainerLayout.setPopupWindowLayout(popupLayout);

        scrimPopupWindow = new ActionBarPopupWindow(scrimPopupContainerLayout, LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT) {
            @Override
            public void dismiss() {
                super.dismiss();
                if (scrimPopupWindow != this) {
                    return;
                }
                Bulletin.hideVisible();
                scrimPopupWindow = null;
                dimBehindView(false);
            }
        };
        scrimPopupWindow.setPauseNotifications(true);
        scrimPopupWindow.setDismissAnimationDuration(220);
        scrimPopupWindow.setOutsideTouchable(true);
        scrimPopupWindow.setClippingEnabled(true);
        scrimPopupWindow.setAnimationStyle(R.style.PopupContextAnimation);
        scrimPopupWindow.setFocusable(true);
        scrimPopupContainerLayout.measure(View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(dp(1000), View.MeasureSpec.AT_MOST));
        scrimPopupWindow.setInputMethodMode(ActionBarPopupWindow.INPUT_METHOD_NOT_NEEDED);
        scrimPopupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
        scrimPopupWindow.getContentView().setFocusableInTouchMode(true);
        popupLayout.setFitItems(true);

        int[] listLocation = new int[2];
        listView.getLocationInWindow(listLocation);

        int popupX = listLocation[0] + v.getLeft() + (int) x - scrimPopupContainerLayout.getMeasuredWidth() - dp(28);
        if (popupX < dp(6)) {
            popupX = dp(6);
        } else if (popupX > listView.getMeasuredWidth() - dp(6) - scrimPopupContainerLayout.getMeasuredWidth()) {
            popupX = listView.getMeasuredWidth() - dp(6) - scrimPopupContainerLayout.getMeasuredWidth();
        }

        int height = scrimPopupContainerLayout.getMeasuredHeight();
        int totalHeight = fragmentView.getHeight();
        int popupY;
        if (height < totalHeight) {
            popupY = listLocation[1] + v.getTop() + (int) y - height - dp(8);
            if (popupY < dp(24)) {
                popupY = dp(24);
            } else if (popupY > totalHeight - height - dp(8)) {
                popupY = totalHeight - height - dp(8);
            }
        } else {
            popupY = AndroidUtilities.getStatusBarHeight(getContext());
        }

        scrimPopupContainerLayout.setMaxHeight(totalHeight - popupY);
        scrimPopupWindow.showAtLocation(listView, Gravity.LEFT | Gravity.TOP, popupX, popupY);
        dimBehindView(v, true);
    }

    private void updateActionBarCount() {
        if (actionBar == null) {
            return;
        }
        int count = allMessageIds != null ? allMessageIds.length : bookmarkedMessages.size();
        actionBar.setSubtitle(getString(R.string.BookmarksManager) + " (" + count + ")");
    }

    private void updateFloatingDateView() {
        if (floatingDateView == null || listView == null) {
            return;
        }
        MessageObject messageObject = getTopVisibleMessageObject();
        if (messageObject == null || messageObject.messageOwner == null) {
            hideFloatingDateView(false);
            return;
        }
        floatingDateView.setCustomDate(messageObject.messageOwner.date, false, true);
        if (scrollingFloatingDate) {
            showFloatingDateView();
        }
    }

    private MessageObject getTopVisibleMessageObject() {
        if (listView == null) {
            return null;
        }
        MessageObject result = null;
        int minTop = Integer.MAX_VALUE;
        for (int i = 0, count = listView.getChildCount(); i < count; i++) {
            View child = listView.getChildAt(i);
            if (!(child instanceof ChatMessageCell)) {
                continue;
            }
            int top = child.getTop();
            if (top < minTop) {
                minTop = top;
                result = ((ChatMessageCell) child).getMessageObject();
            }
        }
        return result;
    }

    private void showFloatingDateView() {
        if (floatingDateView == null) {
            return;
        }
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }
        if (floatingDateView.getTag() != null) {
            floatingDateView.setAlpha(1f);
            return;
        }
        floatingDateView.setTag(1);
        floatingDateAnimation = new AnimatorSet();
        floatingDateAnimation.setDuration(150);
        floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 1f));
        floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == floatingDateAnimation) {
                    floatingDateAnimation = null;
                }
            }
        });
        floatingDateAnimation.start();
    }

    private void hideFloatingDateView(boolean animated) {
        if (floatingDateView == null || floatingDateView.getTag() == null) {
            return;
        }
        floatingDateView.setTag(null);
        if (floatingDateAnimation != null) {
            floatingDateAnimation.cancel();
            floatingDateAnimation = null;
        }
        if (animated) {
            floatingDateAnimation = new AnimatorSet();
            floatingDateAnimation.setDuration(150);
            floatingDateAnimation.playTogether(ObjectAnimator.ofFloat(floatingDateView, View.ALPHA, 0f));
            floatingDateAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation == floatingDateAnimation) {
                        floatingDateAnimation = null;
                    }
                }
            });
            floatingDateAnimation.setStartDelay(200);
            floatingDateAnimation.start();
        } else {
            floatingDateView.setAlpha(0f);
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private void notifyAdapterDataChanged() {
        var adapter = listView == null ? null : listView.getAdapter();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void applySearchFilter() {
        filteredMessages.clear();
        if (TextUtils.isEmpty(searchQuery)) {
            filteredMessages.addAll(bookmarkedMessages);
        } else {
            String q = searchQuery.toLowerCase(Locale.getDefault());
            for (MessageObject msg : bookmarkedMessages) {
                String text = msg.messageOwner != null ? msg.messageOwner.message : null;
                if (!TextUtils.isEmpty(text) && text.toLowerCase(Locale.getDefault()).contains(q)) {
                    filteredMessages.add(msg);
                    continue;
                }
                String attachPath = msg.messageOwner != null ? msg.messageOwner.attachPath : null;
                if (!TextUtils.isEmpty(attachPath) && attachPath.toLowerCase(Locale.getDefault()).contains(q)) {
                    filteredMessages.add(msg);
                }
            }
        }
        rowCount = filteredMessages.size();
        notifyAdapterDataChanged();
        updateActionBarCount();
        updateEmptyView();
        if (listView != null) {
            listView.post(() -> {
                updatePagedownButtonVisibility(false);
                updateVisibleMessageCells();
            });
        } else {
            updatePagedownButtonVisibility(false);
        }
    }

    private void updateEmptyView() {
        updateEmptyView(false);
    }

    private void updateEmptyView(boolean delayIfEmpty) {
        showEmptyViewRunnable = updateListEmptyView(() -> emptyView, () -> listView, rowCount == 0, delayIfEmpty, showEmptyViewRunnable, () -> showEmptyViewRunnable = null);
    }

    private void removeMessageIdFromAllIds(int messageId) {
        if (messageId == 0 || allMessageIds == null || allMessageIds.length == 0) {
            return;
        }
        int index = Arrays.binarySearch(allMessageIds, messageId);
        if (index < 0) {
            return;
        }
        int[] newIds = new int[allMessageIds.length - 1];
        if (index > 0) {
            System.arraycopy(allMessageIds, 0, newIds, 0, index);
        }
        if (index < newIds.length) {
            System.arraycopy(allMessageIds, index + 1, newIds, index, newIds.length - index);
        }
        allMessageIds = newIds;
        if (index < loadedFromIndex) {
            loadedFromIndex = Math.max(0, loadedFromIndex - 1);
        }
    }

    private void checkLoadMore() {
        if (loadingMore || listView == null || loadedFromIndex <= 0 || allMessageIds == null || allMessageIds.length == 0 || !TextUtils.isEmpty(searchQuery)) {
            return;
        }
        RecyclerView.LayoutManager lm = listView.getLayoutManager();
        if (!(lm instanceof LinearLayoutManager layoutManager)) {
            return;
        }
        int firstVisible = layoutManager.findFirstVisibleItemPosition();
        if (firstVisible <= LOAD_MORE_THRESHOLD) {
            loadOlderBatch(layoutManager, firstVisible);
        }
    }

    private void loadOlderBatch(LinearLayoutManager layoutManager, int firstVisible) {
        if (loadingMore || listView == null || loadedFromIndex <= 0 || allMessageIds == null) {
            return;
        }
        int oldStart = loadedFromIndex;
        int newStart = Math.max(0, oldStart - LOAD_BATCH_SIZE);
        if (newStart >= oldStart) {
            return;
        }
        loadingMore = true;
        int token = bookmarksLoadToken;
        View firstView = layoutManager.findViewByPosition(firstVisible);
        int topOffset = firstView != null ? firstView.getTop() : 0;
        int accountId = getCurrentAccount();
        long userId = getUserConfig().getClientUserId();
        int[] ids = allMessageIds;
        Utilities.globalQueue.postRunnable(() -> {
            ArrayList<MessageObject> olderLoaded = loadMessages(accountId, userId, ids, newStart, oldStart);
            AndroidUtilities.runOnUIThread(() -> {
                if (token != bookmarksLoadToken || listView == null) {
                    loadingMore = false;
                    return;
                }
                loadedFromIndex = newStart;
                loadingMore = false;
                if (olderLoaded.isEmpty()) {
                    return;
                }
                bookmarkedMessages.addAll(0, olderLoaded);
                filteredMessages.addAll(0, olderLoaded);
                rowCount = filteredMessages.size();
                RecyclerView.Adapter<?> adapter = listView.getAdapter();
                if (adapter != null) {
                    adapter.notifyItemRangeInserted(0, olderLoaded.size());
                }
                layoutManager.scrollToPositionWithOffset(firstVisible + olderLoaded.size(), topOffset);
                updateActionBarCount();
                updateEmptyView(false);
                listView.post(() -> {
                    updatePagedownButtonVisibility(false);
                    updateVisibleMessageCells();
                });
                loadMissingMessagesServer();
            });
        });
    }

    private void scrollToFirstMessage() {
        try {
            if (listView == null || rowCount <= 0 || filteredMessages.isEmpty()) {
                return;
            }

            if (loadedFromIndex > 0 && allMessageIds != null && allMessageIds.length > 0) {
                int accountId = getCurrentAccount();
                long userId = getUserConfig().getClientUserId();
                int token = bookmarksLoadToken;
                int oldStart = loadedFromIndex;
                int[] ids = allMessageIds;

                Utilities.globalQueue.postRunnable(() -> {
                    try {
                        ArrayList<MessageObject> olderLoaded = loadMessages(accountId, userId, ids, 0, oldStart);
                        AndroidUtilities.runOnUIThread(() -> {
                            try {
                                if (token != bookmarksLoadToken || listView == null) {
                                    return;
                                }
                                loadedFromIndex = 0;
                                if (!olderLoaded.isEmpty()) {
                                    bookmarkedMessages.addAll(0, olderLoaded);
                                    applySearchFilter();
                                }
                                if (listView != null && !filteredMessages.isEmpty()) {
                                    if (filteredMessages.size() > 50) {
                                        listView.scrollToPosition(0);
                                    } else {
                                        listView.smoothScrollToPosition(0);
                                    }
                                    updateVisibleMessageCells();
                                }
                                loadMissingMessagesServer();
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        });
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
            } else {
                if (filteredMessages.size() > 50) {
                    listView.scrollToPosition(0);
                } else {
                    listView.smoothScrollToPosition(0);
                }
                updateVisibleMessageCells();
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {

    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context context;
        private final int currentAccount;

        public ListAdapter(Context context, int currentAccount) {
            this.context = context;
            this.currentAccount = currentAccount;
        }

        @Override
        public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
            if (holder.itemView instanceof NekoMessageCell) {
                ((NekoMessageCell) holder.itemView).setAyuDelegate(null);
            }
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new NekoMessageCell(context, currentAccount));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            if (holder.getItemViewType() == 1) {
                var cell = (NekoMessageCell) holder.itemView;
                var msg = filteredMessages.get(position);
                msg.forceAvatar = true;
                cell.setAyuDelegate(BookmarksActivity.this);
                cell.setMessageObject(msg, null, false, false, false);
                boolean show = actionBar != null && actionBar.isActionModeShowed();
                boolean checked = msg != null && selectedMessagesIds[0].indexOfKey(msg.getId()) >= 0;
                cell.setCheckBoxVisible(show, false);
                cell.setChecked(checked, checked, false);
                cell.setAlpha(1f);
                cell.setId(position);
            }
        }

        @Override
        public int getItemViewType(int position) {
            return position >= 0 && position < filteredMessages.size() ? 1 : 0;
        }
    }
}
