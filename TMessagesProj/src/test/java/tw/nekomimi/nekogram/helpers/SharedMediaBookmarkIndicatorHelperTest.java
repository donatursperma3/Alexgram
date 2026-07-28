package tw.nekomimi.nekogram.helpers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SharedMediaBookmarkIndicatorHelperTest {
    @Test
    public void shouldShowBookmarkIndicatorOnlyWhenFeatureAndToggleAndBookmarkStateAreEnabled() {
        assertTrue(SharedMediaBookmarkIndicatorHelper.shouldShowBookmarkIndicator(true, true, true));
        assertFalse(SharedMediaBookmarkIndicatorHelper.shouldShowBookmarkIndicator(false, true, true));
        assertFalse(SharedMediaBookmarkIndicatorHelper.shouldShowBookmarkIndicator(true, false, true));
        assertFalse(SharedMediaBookmarkIndicatorHelper.shouldShowBookmarkIndicator(true, true, false));
    }

    @Test
    public void shouldNormalizeInvalidPlacementValues() {
        assertTrue(SharedMediaBookmarkIndicatorHelper.normalizePlacement(-1) == SharedMediaBookmarkIndicatorHelper.PLACEMENT_TOP_LEFT);
        assertTrue(SharedMediaBookmarkIndicatorHelper.normalizePlacement(99) == SharedMediaBookmarkIndicatorHelper.PLACEMENT_TOP_LEFT);
        assertTrue(SharedMediaBookmarkIndicatorHelper.normalizePlacement(SharedMediaBookmarkIndicatorHelper.PLACEMENT_TOP_RIGHT) == SharedMediaBookmarkIndicatorHelper.PLACEMENT_TOP_RIGHT);
    }

    @Test
    public void shouldSelectPlacementBasedOnMessageType() {
        assertTrue(SharedMediaBookmarkIndicatorHelper.getPlacementForMessageObject(null, SharedMediaBookmarkIndicatorHelper.PLACEMENT_TOP_RIGHT, SharedMediaBookmarkIndicatorHelper.PLACEMENT_TOP_LEFT, SharedMediaBookmarkIndicatorHelper.PLACEMENT_BOTTOM_LEFT) == SharedMediaBookmarkIndicatorHelper.PLACEMENT_TOP_LEFT);
    }
}
