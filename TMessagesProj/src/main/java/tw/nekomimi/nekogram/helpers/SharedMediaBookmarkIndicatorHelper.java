package tw.nekomimi.nekogram.helpers;

public final class SharedMediaBookmarkIndicatorHelper {
    private SharedMediaBookmarkIndicatorHelper() {
    }

    public static boolean shouldShowBookmarkIndicator(boolean featureEnabled, boolean indicatorEnabled, boolean isBookmarked) {
        return featureEnabled && indicatorEnabled && isBookmarked;
    }
}
