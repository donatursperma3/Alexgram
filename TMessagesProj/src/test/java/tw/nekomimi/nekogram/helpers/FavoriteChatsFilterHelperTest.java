package tw.nekomimi.nekogram.helpers;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class FavoriteChatsFilterHelperTest {
    @Test
    public void shouldIncludeOnlyWhenFlagSetAndDialogIsFavorite() {
        assertTrue(FavoriteChatsFilterHelper.shouldInclude(FavoriteChatsFilterHelper.FAVORITE_FILTER_FLAG, true));
        assertFalse(FavoriteChatsFilterHelper.shouldInclude(FavoriteChatsFilterHelper.FAVORITE_FILTER_FLAG, false));
        assertFalse(FavoriteChatsFilterHelper.shouldInclude(0, true));
    }
}
