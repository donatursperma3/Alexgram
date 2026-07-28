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
}
