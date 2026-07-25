/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.Emoji;
import com.radolyn.ayugram.utils.LastSeenHelper;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.NotificationsController;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.LocaleController;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import tw.nekomimi.nekogram.NekoConfig;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.EmojiTextView;
import org.telegram.ui.Components.GroupCreateCheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.Premium.PremiumGradient;
import xyz.nextalone.nagram.NaConfig;

public class DrawerUserCell extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {

    private final SimpleTextView textView;
    private final SimpleTextView subtitleTextView;
    private final BackupImageView imageView;
    private final AvatarDrawable avatarDrawable;
    private final GroupCreateCheckBox checkBox;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable botVerification;
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable status;
    private final Paint selectedBackgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint avatarRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int accountNumber;
    private final RectF rect = new RectF();

    public DrawerUserCell(Context context) {
        super(context);

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(dp(20));

        // Fix: Standardize drawer account list text alignment to 72dp grid to match DrawerActionCell
        int avatarStart = AndroidUtilities.dp(16);
        int textStart = AndroidUtilities.dp(72);
        // Fix: Position account checkbox badge on bottom-right of avatar (36dp start)
        int checkBoxStart = AndroidUtilities.dp(36);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(dp(18));
        addView(imageView, LayoutHelper.createFrame(36, 36,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                LocaleController.isRTL ? 0 : avatarStart,
                6,
                LocaleController.isRTL ? avatarStart : 0,
                0));

        textView = new SimpleTextView(context);
        textView.setPadding(0, dp(4), 0, dp(4));
        textView.setTextColor(Theme.getColor(Theme.key_chats_menuItemText));
        textView.setTextSize(15);
        textView.setTypeface(AndroidUtilities.bold());
        textView.setMaxLines(1);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        textView.setEllipsizeByGradient(24);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                LocaleController.isRTL ? 0 : textStart,
                4,
                LocaleController.isRTL ? textStart : 14,
                0));

        subtitleTextView = new SimpleTextView(context);
        subtitleTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        subtitleTextView.setTextSize(12);
        subtitleTextView.setMaxLines(1);
        subtitleTextView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        subtitleTextView.setEllipsizeByGradient(24);
        subtitleTextView.setVisibility(GONE);
        addView(subtitleTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                LocaleController.isRTL ? 0 : textStart,
                31,
                LocaleController.isRTL ? textStart : 14,
                0));

        botVerification = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(textView, dp(18));
        status = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(textView, dp(20));
        textView.setRightDrawable(status);

        checkBox = new GroupCreateCheckBox(context);
        checkBox.setChecked(true, false);
        checkBox.setCheckScale(0.9f);
        checkBox.setInnerRadDiff(dp(1.5f));
        checkBox.setColorKeysOverrides(Theme.key_chats_unreadCounterText, Theme.key_chats_unreadCounter, Theme.key_chats_menuBackground);
        addView(checkBox, LayoutHelper.createFrame(18, 18,
                (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP,
                LocaleController.isRTL ? 0 : checkBoxStart,
                24,
                LocaleController.isRTL ? checkBoxStart : 0,
                0));

        setWillNotDraw(false);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(60), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        applyTextColors();
        status.attach();
        botVerification.attach();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++){
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            NotificationCenter.getInstance(i).addObserver(this, NotificationCenter.updateInterfaces);
        }
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.emojiLoaded);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        status.detach();
        botVerification.detach();
        for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++){
            NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.currentUserPremiumStatusChanged);
            NotificationCenter.getInstance(i).removeObserver(this, NotificationCenter.updateInterfaces);
        }
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.emojiLoaded);

        if (textView.getRightDrawable() instanceof AnimatedEmojiDrawable.WrapSizeDrawable) {
            Drawable drawable = ((AnimatedEmojiDrawable.WrapSizeDrawable) textView.getRightDrawable()).getDrawable();
            if (drawable instanceof AnimatedEmojiDrawable) {
                ((AnimatedEmojiDrawable) drawable).removeView(textView);
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.currentUserPremiumStatusChanged) {
            if (account == accountNumber) {
                setAccount(accountNumber);
            }
        } else if (id == NotificationCenter.emojiLoaded) {
            textView.invalidate();
        } else if (id == NotificationCenter.updateInterfaces) {
            if (((int) args[0] & MessagesController.UPDATE_MASK_EMOJI_STATUS) > 0) {
                setAccount(accountNumber);
            }
        }
    }

    public void setAccount(int account) {
        setAccount(account, -1);
    }
    
    // [Alexgram: Account Numbers] - Start
    public void setAccount(int account, int position) {
        accountNumber = account;
        final TLRPC.User user = UserConfig.getInstance(accountNumber).getCurrentUser();
        if (user == null) {
            return;
        }
        avatarDrawable.setInfo(account, user);
        CharSequence text = ContactsController.formatName(user.first_name, user.last_name);
        if (position >= 0 && NaConfig.INSTANCE.getShowAccountNumbers().Bool()) {
            text = String.format("%d. %s", position + 1, text);
        }
        try {
            text = Emoji.replaceEmoji(text, textView.getPaint().getFontMetricsInt(), false);
        } catch (Exception ignore) {}
        textView.setText(text);
        applyTextColors();
        subtitleTextView.setVisibility(GONE);
        if (NaConfig.INSTANCE.getShowLastSeenOnAccountRows().Bool()) {
            try {
                String lastSeenText = LastSeenHelper.getFormattedLastSeenOrDefault(user, null, "");
                if (lastSeenText != null && !lastSeenText.isEmpty()) {
                    subtitleTextView.setText(lastSeenText);
                    subtitleTextView.setVisibility(VISIBLE);
                } else {
                    subtitleTextView.setVisibility(GONE);
                }
            } catch (Exception e) {
                subtitleTextView.setVisibility(GONE);
                org.telegram.messenger.FileLog.e(e);
            }
        } else {
            subtitleTextView.setVisibility(GONE);
        }
        
        final Long emojiStatusId = UserObject.getEmojiStatusDocumentId(user);
        if (emojiStatusId != null) {
            textView.setDrawablePadding(dp(4));
            status.set(emojiStatusId, true);
            status.setParticles(DialogObject.isEmojiStatusCollectible(user.emoji_status), true);
            textView.setRightDrawableOutside(true);
        } else if (MessagesController.getInstance(account).isPremiumUser(user) && !NekoConfig.hidePremiumIcon.Bool()) {
            textView.setDrawablePadding(dp(6));
            status.set(PremiumGradient.getInstance().premiumStarDrawableMini, true);
            status.setParticles(false, true);
            textView.setRightDrawableOutside(true);
        } else {
            status.set((Drawable) null, true);
            status.setParticles(false, true);
            textView.setRightDrawableOutside(false);
        }
        final long botVerificationId = DialogObject.getBotVerificationIcon(user);
        if (botVerificationId == 0 || ConnectionsManager.getInstance(account).isTestBackend() != ConnectionsManager.getInstance(UserConfig.selectedAccount).isTestBackend()) {
            botVerification.set((Drawable) null, false);
            textView.setLeftDrawable(null);
        } else {
            botVerification.set(botVerificationId, false);
            botVerification.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
            textView.setLeftDrawable(botVerification);
        }
        status.setColor(Theme.getColor(Theme.key_chats_verifiedBackground));
        imageView.getImageReceiver().setCurrentAccount(account);
        imageView.setForUserOrChat(user, avatarDrawable);
        checkBox.setVisibility(account == UserConfig.selectedAccount ? VISIBLE : INVISIBLE);
    }

    public int getAccountNumber() {
        return accountNumber;
    }

    private void applyTextColors() {
        int mainTextColor = Theme.getColor(Theme.key_chats_menuItemText);
        textView.setTextColor(mainTextColor);
        subtitleTextView.setTextColor(Theme.isCurrentThemeDark()
                ? Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2)
                : Theme.multAlpha(mainTextColor, 0.78f));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawSelectedAccountBackground(canvas);

        if (UserConfig.getActivatedAccountsCount() <= 1 || !NotificationsController.getInstance(accountNumber).showBadgeNumber) {
            textView.setRightPadding(0);
            return;
        }
        final int counter = MessagesStorage.getInstance(accountNumber).getMainUnreadCount();
        if (counter <= 0) {
            textView.setRightPadding(0);
            return;
        }

        final String text = String.format("%d", counter);
        final int countTop = dp(12.5f);
        final int textWidth = (int) Math.ceil(Theme.dialogs_countTextPaint.measureText(text));
        final int countWidth = Math.max(dp(10), textWidth);
        final int countRight = getMeasuredWidth() - dp(18);
        final int x = countRight - countWidth - dp(14);
        rect.set(x, countTop, countRight, countTop + dp(23));
        canvas.drawRoundRect(rect, 11.5f * AndroidUtilities.density, 11.5f * AndroidUtilities.density, Theme.dialogs_countPaint);

        canvas.drawText(text, rect.left + (rect.width() - textWidth) / 2, countTop + dp(16), Theme.dialogs_countTextPaint);

        textView.setRightPadding(countWidth + dp(28));
    }

    private void drawSelectedAccountBackground(Canvas canvas) {
        if (accountNumber != UserConfig.selectedAccount) {
            return;
        }
        int menuBackground = Theme.getColor(Theme.key_chats_menuBackground);
        int selectorOverlay = Theme.multAlpha(
                Theme.getColor(Theme.key_listSelector),
                Theme.isCurrentThemeDark() ? 0.70f : 0.45f
        );
        selectedBackgroundPaint.setColor(Theme.blendOver(menuBackground, selectorOverlay));
        rect.set(dp(8), dp(2), getMeasuredWidth() - dp(8), getMeasuredHeight() - dp(2));
        canvas.drawRoundRect(rect, dp(13), dp(13), selectedBackgroundPaint);

        avatarRingPaint.setStyle(Paint.Style.STROKE);
        avatarRingPaint.setStrokeWidth(AndroidUtilities.dpf2(2));
        avatarRingPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton));
        canvas.drawCircle(
                imageView.getLeft() + imageView.getMeasuredWidth() / 2f,
                imageView.getTop() + imageView.getMeasuredHeight() / 2f,
                dp(20),
                avatarRingPaint
        );
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
}
