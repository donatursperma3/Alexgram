package tw.nekomimi.nekogram.helpers;

import org.telegram.messenger.MessageObject;

public final class SharedMediaBookmarkIndicatorHelper {
    public static final int PLACEMENT_TOP_LEFT = 0;
    public static final int PLACEMENT_TOP_RIGHT = 1;
    public static final int PLACEMENT_BOTTOM_LEFT = 2;
    public static final int PLACEMENT_BOTTOM_RIGHT = 3;

    private SharedMediaBookmarkIndicatorHelper() {
    }

    public static boolean shouldShowBookmarkIndicator(boolean featureEnabled, boolean indicatorEnabled, boolean isBookmarked) {
        return featureEnabled && indicatorEnabled && isBookmarked;
    }

    public static int normalizePlacement(int placement) {
        if (placement < PLACEMENT_TOP_LEFT || placement > PLACEMENT_BOTTOM_RIGHT) {
            return PLACEMENT_TOP_LEFT;
        }
        return placement;
    }

    public static int getPlacementForMessageObject(MessageObject messageObject, int photoPlacement, int videoPlacement, int documentPlacement) {
        if (messageObject == null) {
            return PLACEMENT_TOP_LEFT;
        }
        if (messageObject.isVideo() || messageObject.isRoundVideo() || messageObject.isLivePhoto()) {
            return normalizePlacement(videoPlacement);
        }
        if (messageObject.isPhoto()) {
            return normalizePlacement(photoPlacement);
        }
        return normalizePlacement(documentPlacement);
    }
}
