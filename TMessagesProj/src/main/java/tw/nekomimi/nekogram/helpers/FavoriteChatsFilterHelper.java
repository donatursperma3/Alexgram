package tw.nekomimi.nekogram.helpers;

import android.content.SharedPreferences;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.NotificationCenter;

import java.util.HashSet;
import java.util.Set;

public final class FavoriteChatsFilterHelper {
    public static final int FAVORITE_FILTER_FLAG = 0x04000000;
    private static final String PREFS_NAME = "favorite_chats_config";
    private static final String PREFS_KEY_PREFIX = "favorite_ids_";

    private FavoriteChatsFilterHelper() {
    }

    public static SharedPreferences getPreferences() {
        if (ApplicationLoader.applicationContext == null) {
            return null;
        }
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, 0);
    }

    public static boolean isFavorite(int account, long dialogId) {
        if (dialogId == 0) {
            return false;
        }
        Set<String> ids = getSavedIds(account);
        return ids.contains(String.valueOf(dialogId));
    }

    public static void toggleFavorite(int account, long dialogId) {
        if (dialogId == 0) {
            return;
        }
        Set<String> ids = getSavedIds(account);
        String value = String.valueOf(dialogId);
        if (ids.contains(value)) {
            ids.remove(value);
        } else {
            ids.add(value);
        }
        saveIds(account, ids);
        notifyRefresh(account);
    }

    public static void setFavorite(int account, long dialogId, boolean favorite) {
        if (dialogId == 0) {
            return;
        }
        Set<String> ids = getSavedIds(account);
        String value = String.valueOf(dialogId);
        if (favorite) {
            if (ids.add(value)) {
                saveIds(account, ids);
                notifyRefresh(account);
            }
        } else if (ids.remove(value)) {
            saveIds(account, ids);
            notifyRefresh(account);
        }
    }

    public static boolean shouldInclude(int flags, boolean isFavorite) {
        return isFavorite && (flags & FAVORITE_FILTER_FLAG) != 0;
    }

    public static boolean shouldInclude(int flags, int account, long dialogId) {
        return shouldInclude(flags, isFavorite(account, dialogId));
    }

    private static Set<String> getSavedIds(int account) {
        SharedPreferences preferences = getPreferences();
        if (preferences == null) {
            return new HashSet<>();
        }
        Set<String> ids = preferences.getStringSet(PREFS_KEY_PREFIX + account, new HashSet<>());
        return new HashSet<>(ids != null ? ids : new HashSet<>());
    }

    private static void saveIds(int account, Set<String> ids) {
        SharedPreferences preferences = getPreferences();
        if (preferences == null) {
            return;
        }
        preferences.edit().putStringSet(PREFS_KEY_PREFIX + account, new HashSet<>(ids)).apply();
    }

    private static void notifyRefresh(int account) {
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogsNeedReload);
        NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.dialogFiltersUpdated);
    }
}
