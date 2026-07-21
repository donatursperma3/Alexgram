package org.telegram.ui.bugtest;

import org.junit.Test;
import org.junit.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * BUG CONDITION EXPLORATION TEST
 *
 * Spec: shared-media-download-filter
 * Task: 1. Write bug condition exploration test
 *
 * Validates: Requirements 1.1, 1.2
 *
 * ---
 * BUG SUMMARY
 * -----------
 * In ProfileActivity.setMediaHeaderVisible(), the variable `mediaOptionsItem` is assigned
 * from `sharedMediaLayout.getSearchOptionsItem()`, which returns `optionsSearchImageView`
 * (a RLottieImageView animation view), NOT `photoVideoOptionsItem` (the actual download
 * filter button, a plain ImageView).
 *
 * When setMediaHeaderVisible(true) is called with tab = TAB_FILES:
 *   1. mediaOptionsItem (= optionsSearchImageView) is set VISIBLE — but this is the WRONG view.
 *   2. photoVideoOptionsItem IS set VISIBLE in the same branch (correct logic exists),
 *      BUT optionsSearchImageView is added to actionBar at the SAME position (Gravity.RIGHT|BOTTOM),
 *      so it overlaps/covers photoVideoOptionsItem.
 *   3. When setMediaHeaderVisible(false) is called, only mediaOptionsItem.setVisibility(GONE)
 *      is called — which hides optionsSearchImageView, not photoVideoOptionsItem. This leaves
 *      photoVideoOptionsItem in an inconsistent visibility state.
 *
 * ---
 * BUG CONDITION (isBugCondition):
 *   hostActivity IS ProfileActivity
 *   AND mediaHeaderVisible = true
 *   AND isOptionsItemVisible(currentTab) = true
 *   (isOptionsItemVisible = true for TAB_FILES, TAB_PHOTOVIDEO, TAB_STORIES, etc.)
 *
 * ---
 * EXPECTED TEST OUTCOME:
 *   This test MUST FAIL on unfixed code — failure confirms the bug exists.
 *   When the bug is fixed, this test will PASS.
 *
 * ---
 * COUNTEREXAMPLE FOUND:
 *   setMediaHeaderVisible(true) called with TAB_FILES active:
 *   → mediaOptionsItem = getSearchOptionsItem() returns optionsSearchImageView (RLottieImageView)
 *   → mediaOptionsItem.setVisibility(View.VISIBLE) sets optionsSearchImageView VISIBLE
 *   → optionsSearchImageView and photoVideoOptionsItem both added at same actionBar position
 *     (Gravity.RIGHT | Gravity.BOTTOM, rightMargin=0) → they OVERLAP
 *   → optionsSearchImageView (being added AFTER photoVideoOptionsItem) is rendered ON TOP
 *   → The actual download filter button (photoVideoOptionsItem) is visually HIDDEN/COVERED
 *   → ADDITIONALLY: when setMediaHeaderVisible(false) fires, only optionsSearchImageView
 *     is set GONE via mediaOptionsItem.setVisibility(GONE); photoVideoOptionsItem remains
 *     VISIBLE or in incorrect state, causing potential overlap with otherItem (three-dot menu)
 */
public class SharedMediaDownloadFilterBugExplorationTest {

    // Paths are relative to workspace root
    private static final String PROFILE_ACTIVITY_PATH =
            "TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java";
    private static final String SHARED_MEDIA_LAYOUT_PATH =
            "TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java";

    // =========================================================================
    // HELPER: read source file lines
    // =========================================================================

    private List<String> readSourceFile(String relativePath) throws IOException {
        // Resolve file relative to workspace root (parent of TMessagesProj)
        File workspaceRoot = findWorkspaceRoot();
        File file = new File(workspaceRoot, relativePath);
        Assert.assertTrue("Source file must exist: " + file.getAbsolutePath(), file.exists());

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private File findWorkspaceRoot() {
        // Try to find workspace root by looking for build.gradle in parent chain
        File dir = new File(System.getProperty("user.dir"));
        while (dir != null) {
            if (new File(dir, "TMessagesProj").exists()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        // fallback: use user.dir (which may already be workspace root)
        return new File(System.getProperty("user.dir"));
    }

    private String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append("\n");
        }
        return sb.toString();
    }

    // =========================================================================
    // TEST 1 — Bug Condition: getSearchOptionsItem() returns optionsSearchImageView
    //          NOT photoVideoOptionsItem
    //
    // EXPECTED: FAIL on unfixed code (confirms bug)
    // PROPERTY: For any context where isBugCondition = true, calling
    //           setMediaHeaderVisible(true) should result in photoVideoOptionsItem
    //           being VISIBLE without obstruction — but because getSearchOptionsItem()
    //           returns the wrong view (optionsSearchImageView), this guarantee is broken.
    //
    // Validates: Requirements 1.1, 1.2
    // =========================================================================

    @Test
    public void test_getSearchOptionsItem_doesNotReturnPhotoVideoOptionsItem() throws IOException {
        /*
         * ASSERTION:
         * getSearchOptionsItem() in SharedMediaLayout MUST return optionsSearchImageView,
         * NOT photoVideoOptionsItem.
         * This proves that ProfileActivity.setMediaHeaderVisible() which calls
         * getSearchOptionsItem() is operating on the WRONG object.
         *
         * COUNTEREXAMPLE:
         *   getSearchOptionsItem() { return optionsSearchImageView; }
         *   → This is RLottieImageView (animation view), not the filter button ImageView
         *   → When ProfileActivity calls mediaOptionsItem = getSearchOptionsItem(), it
         *     gets the animation view, NOT photoVideoOptionsItem (the download filter button)
         */
        List<String> lines = readSourceFile(SHARED_MEDIA_LAYOUT_PATH);
        String source = joinLines(lines);

        // VERIFY THE BUG EXISTS: getSearchOptionsItem() must return optionsSearchImageView
        // (this confirms the wrong object is returned)
        boolean returnsOptionsSearchImageView = source.contains(
                "public RLottieImageView getSearchOptionsItem()") &&
                source.contains("return optionsSearchImageView;");

        Assert.assertTrue(
                "COUNTEREXAMPLE: getSearchOptionsItem() returns optionsSearchImageView " +
                "(RLottieImageView animation view), NOT photoVideoOptionsItem (ImageView filter button). " +
                "This proves that ProfileActivity.setMediaHeaderVisible() operates on wrong view.",
                returnsOptionsSearchImageView
        );

        // NOW ASSERT THE EXPECTED (CORRECT) BEHAVIOR — this will FAIL on unfixed code:
        // getSearchOptionsItem() should NOT be called in setMediaHeaderVisible()
        // because it returns the wrong view.
        // The correct approach is to use sharedMediaLayout.photoVideoOptionsItem directly.
        List<String> profileLines = readSourceFile(PROFILE_ACTIVITY_PATH);
        String profileSource = joinLines(profileLines);

        // Extract just the setMediaHeaderVisible method body for analysis
        String methodBody = extractMethodBody(profileSource, "setMediaHeaderVisible");

        Assert.assertNotNull("setMediaHeaderVisible method must exist in ProfileActivity", methodBody);

        // THIS ASSERTION WILL FAIL ON UNFIXED CODE:
        // The method should NOT reference getSearchOptionsItem() at all.
        // On unfixed code: methodBody CONTAINS "getSearchOptionsItem()" → assertion FAILS → BUG CONFIRMED
        Assert.assertFalse(
                "BUG CONFIRMED — COUNTEREXAMPLE: setMediaHeaderVisible() calls " +
                "sharedMediaLayout.getSearchOptionsItem() which returns optionsSearchImageView " +
                "(NOT photoVideoOptionsItem). This causes the wrong view to be manipulated. " +
                "FIX REQUIRED: Remove getSearchOptionsItem() usage from setMediaHeaderVisible(); " +
                "use sharedMediaLayout.photoVideoOptionsItem directly.",
                methodBody.contains("getSearchOptionsItem()")
        );
    }

    // =========================================================================
    // TEST 2 — Bug Condition: optionsSearchImageView and photoVideoOptionsItem
    //          are added at the SAME position in actionBar (Gravity.RIGHT|BOTTOM, no offset)
    //
    // EXPECTED: FAIL on unfixed code (confirms overlap bug)
    // PROPERTY: photoVideoOptionsItem must be positioned so it does NOT overlap with
    //           optionsSearchImageView or otherItem.
    //
    // Validates: Requirements 1.1, 1.2, 1.3
    // =========================================================================

    @Test
    public void test_photoVideoOptionsItem_overlapsWithOptionsSearchImageView() throws IOException {
        /*
         * ASSERTION:
         * In SharedMediaLayout constructor, both photoVideoOptionsItem and optionsSearchImageView
         * are added to actionBar with IDENTICAL gravity (Gravity.RIGHT | Gravity.BOTTOM)
         * and NO horizontal offset (rightMargin = 0).
         * This means they occupy the same visual space — they OVERLAP.
         * When the bug in setMediaHeaderVisible() causes optionsSearchImageView to be set VISIBLE,
         * it covers photoVideoOptionsItem, making the download filter button invisible to the user.
         *
         * COUNTEREXAMPLE:
         *   actionBar.addView(photoVideoOptionsItem, createFrame(48, 56, RIGHT|BOTTOM))
         *   actionBar.addView(optionsSearchImageView, createFrame(48, 56, RIGHT|BOTTOM))
         *   → Both at same position, optionsSearchImageView added AFTER → rendered ON TOP
         *   → photoVideoOptionsItem (download filter button) is visually COVERED
         */
        List<String> lines = readSourceFile(SHARED_MEDIA_LAYOUT_PATH);

        // Find the addView calls for both views
        String photoVideoAddView = null;
        String optionsSearchAddView = null;
        boolean inAddViewBlock = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("addView(photoVideoOptionsItem")) {
                photoVideoAddView = line.trim();
            }
            if (line.contains("addView(optionsSearchImageView")) {
                optionsSearchAddView = line.trim();
            }
        }

        Assert.assertNotNull("photoVideoOptionsItem addView call must exist", photoVideoAddView);
        Assert.assertNotNull("optionsSearchImageView addView call must exist", optionsSearchAddView);

        // THIS ASSERTION WILL FAIL ON UNFIXED CODE:
        // On unfixed code, both use Gravity.RIGHT | Gravity.BOTTOM with rightMargin = 0
        // → they overlap → assertion FAILS → BUG CONFIRMED
        //
        // On fixed code, photoVideoOptionsItem should have rightMargin=48 to avoid overlap
        // e.g., createFrame(48, 56, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 48, 0)
        boolean photoVideoHasOffset = photoVideoAddView.contains("48, 0)") ||
                photoVideoAddView.matches(".*createFrame\\(48,\\s*56,.*,\\s*0,\\s*0,\\s*48,\\s*0\\).*");

        Assert.assertTrue(
                "BUG CONFIRMED — COUNTEREXAMPLE: photoVideoOptionsItem and optionsSearchImageView " +
                "are both added to actionBar at Gravity.RIGHT|BOTTOM with NO horizontal offset (rightMargin=0). " +
                "Current photoVideoOptionsItem addView: [" + photoVideoAddView + "] — " +
                "They OVERLAP because optionsSearchImageView is added after photoVideoOptionsItem at the same position. " +
                "FIX REQUIRED: Add rightMargin=48dp to photoVideoOptionsItem: " +
                "createFrame(48, 56, Gravity.RIGHT|Gravity.BOTTOM, 0, 0, 48, 0)",
                photoVideoHasOffset
        );
    }

    // =========================================================================
    // TEST 3 — Bug Condition: setMediaHeaderVisible(false) leaves photoVideoOptionsItem
    //          in inconsistent visibility state
    //
    // EXPECTED: FAIL on unfixed code (confirms bug in hide path)
    // PROPERTY: When setMediaHeaderVisible(false) is called, BOTH optionsSearchImageView
    //           AND photoVideoOptionsItem should be explicitly set to GONE/INVISIBLE.
    //
    // Validates: Requirements 1.2, 1.4
    // =========================================================================

    @Test
    public void test_setMediaHeaderVisible_false_doesNotHidePhotoVideoOptionsItem() throws IOException {
        /*
         * ASSERTION:
         * In setMediaHeaderVisible(false), when mediaHeaderVisible = false:
         *   - mediaOptionsItem.setVisibility(View.GONE) is called
         *   - But mediaOptionsItem = getSearchOptionsItem() = optionsSearchImageView
         *   - So ONLY optionsSearchImageView is set GONE
         *   - photoVideoOptionsItem is NOT set GONE in this branch
         *   - photoVideoOptionsItem visibility depends on animation state → INCONSISTENT
         *
         * COUNTEREXAMPLE:
         *   setMediaHeaderVisible(false) with TAB_FILES, previously mediaHeaderVisible=true:
         *   → mediaOptionsItem.setVisibility(GONE) hides optionsSearchImageView only
         *   → photoVideoOptionsItem remains VISIBLE (or at some alpha from animation)
         *   → otherItem is set VISIBLE simultaneously
         *   → photoVideoOptionsItem and otherItem OVERLAP in action bar (both rightmost position)
         */
        List<String> profileLines = readSourceFile(PROFILE_ACTIVITY_PATH);
        String profileSource = joinLines(profileLines);

        String methodBody = extractMethodBody(profileSource, "setMediaHeaderVisible");
        Assert.assertNotNull("setMediaHeaderVisible method must exist", methodBody);

        // Extract the !mediaHeaderVisible branch (false-visible path)
        String falseVisibleBranch = extractFalseBranch(methodBody);
        Assert.assertNotNull("false branch must exist in setMediaHeaderVisible", falseVisibleBranch);

        // THIS ASSERTION WILL FAIL ON UNFIXED CODE:
        // On unfixed code, the false-branch uses mediaOptionsItem.setVisibility(GONE)
        // which operates on optionsSearchImageView, NOT photoVideoOptionsItem.
        // photoVideoOptionsItem is not explicitly managed in the false-branch.
        //
        // The fix should REMOVE the mediaOptionsItem.setVisibility(GONE) call entirely
        // (since optionsSearchImageView is managed by SharedMediaLayout internally).
        boolean falseVisibleBranchHasMediaOptionsItem = falseVisibleBranch.contains("mediaOptionsItem");

        Assert.assertFalse(
                "BUG CONFIRMED — COUNTEREXAMPLE: setMediaHeaderVisible(false) branch contains " +
                "'mediaOptionsItem.setVisibility(View.GONE)' where mediaOptionsItem = " +
                "getSearchOptionsItem() = optionsSearchImageView (wrong view). " +
                "This hides optionsSearchImageView but leaves photoVideoOptionsItem in an " +
                "inconsistent state, potentially visible and overlapping with otherItem. " +
                "FIX REQUIRED: Remove mediaOptionsItem usage from setMediaHeaderVisible(); " +
                "let SharedMediaLayout manage optionsSearchImageView via animateSearchToOptions().",
                falseVisibleBranchHasMediaOptionsItem
        );
    }

    // =========================================================================
    // TEST 4 — Bug Condition: setMediaHeaderVisible(true) sets optionsSearchImageView VISIBLE
    //          (interfering with SharedMediaLayout's own management of the view)
    //
    // EXPECTED: FAIL on unfixed code
    // PROPERTY: ProfileActivity should NOT call setVisibility on optionsSearchImageView.
    //           SharedMediaLayout already manages optionsSearchImageView via animateSearchToOptions().
    //
    // Validates: Requirements 1.2, 2.2
    // =========================================================================

    @Test
    public void test_setMediaHeaderVisible_true_setsWrongViewVisible() throws IOException {
        /*
         * ASSERTION:
         * In setMediaHeaderVisible(true) branch, mediaOptionsItem.setVisibility(View.VISIBLE)
         * is called. Since mediaOptionsItem = optionsSearchImageView, this explicitly sets
         * optionsSearchImageView VISIBLE — but SharedMediaLayout already manages this view's
         * visibility through animateSearchToOptions(). ProfileActivity should NOT manipulate it.
         *
         * COUNTEREXAMPLE:
         *   setMediaHeaderVisible(true) with TAB_FILES active:
         *   → mediaOptionsItem.setVisibility(VISIBLE) sets optionsSearchImageView VISIBLE
         *   → animateSearchToOptions(true, false) is called immediately after
         *   → optionsSearchImageView is at Gravity.RIGHT|BOTTOM (same as photoVideoOptionsItem)
         *   → optionsSearchImageView renders ON TOP of photoVideoOptionsItem
         *   → User sees optionsSearchImageView (animation icon) instead of photoVideoOptionsItem
         *     (download filter button) → download filter button not visible to user
         *   → BUG CONDITION: isBugCondition(ProfileActivity, TAB_FILES, mediaHeaderVisible=true)
         *     ASSERT photoVideoOptionsItem.getVisibility() == View.VISIBLE → FAILS
         *     because optionsSearchImageView is covering it
         */
        List<String> profileLines = readSourceFile(PROFILE_ACTIVITY_PATH);
        String profileSource = joinLines(profileLines);

        String methodBody = extractMethodBody(profileSource, "setMediaHeaderVisible");
        Assert.assertNotNull("setMediaHeaderVisible method must exist", methodBody);

        // Extract the mediaHeaderVisible = true branch
        String trueVisibleBranch = extractTrueBranch(methodBody);
        Assert.assertNotNull("true branch must exist in setMediaHeaderVisible", trueVisibleBranch);

        // THIS ASSERTION WILL FAIL ON UNFIXED CODE:
        // On unfixed code, the true-branch sets mediaOptionsItem (= optionsSearchImageView) VISIBLE
        // This is the root cause of the bug.
        boolean trueVisibleBranchHasMediaOptionsItemSetVisible =
                trueVisibleBranch.contains("mediaOptionsItem") &&
                trueVisibleBranch.contains("setVisibility(View.VISIBLE)");

        Assert.assertFalse(
                "BUG CONFIRMED — COUNTEREXAMPLE (PRIMARY BUG): " +
                "setMediaHeaderVisible(true) branch contains 'mediaOptionsItem.setVisibility(View.VISIBLE)' " +
                "where mediaOptionsItem = getSearchOptionsItem() = optionsSearchImageView (RLottieImageView). " +
                "Setting optionsSearchImageView VISIBLE overlaps photoVideoOptionsItem since both are at " +
                "Gravity.RIGHT|BOTTOM with no offset. " +
                "isBugCondition(ProfileActivity, TAB_FILES, mediaHeaderVisible=true) = TRUE → " +
                "photoVideoOptionsItem.getVisibility() should be VISIBLE but is COVERED by optionsSearchImageView. " +
                "FIX REQUIRED: Remove 'ImageView mediaOptionsItem = sharedMediaLayout.getSearchOptionsItem()' " +
                "and all mediaOptionsItem.setVisibility() calls from setMediaHeaderVisible().",
                trueVisibleBranchHasMediaOptionsItemSetVisible
        );
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    /**
     * Extracts the body of a method from source code.
     * Simple brace-counting approach.
     */
    private String extractMethodBody(String source, String methodName) {
        int methodStart = source.indexOf("void " + methodName + "(");
        if (methodStart < 0) {
            methodStart = source.indexOf("private " + methodName + "(");
        }
        if (methodStart < 0) return null;

        int braceStart = source.indexOf("{", methodStart);
        if (braceStart < 0) return null;

        int depth = 0;
        int i = braceStart;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(braceStart, i + 1);
                }
            }
            i++;
        }
        return null;
    }

    /**
     * Extracts the !mediaHeaderVisible (false) branch from setMediaHeaderVisible body.
     * Looks for the `if (!mediaHeaderVisible)` block.
     */
    private String extractFalseBranch(String methodBody) {
        // Find "if (!mediaHeaderVisible) {" or "if (mediaHeaderVisible == visible)" — look for false path
        int ifStart = methodBody.indexOf("if (!mediaHeaderVisible)");
        if (ifStart < 0) {
            // alternative pattern
            ifStart = methodBody.indexOf("if (!visible)");
        }
        if (ifStart < 0) return null;

        int braceStart = methodBody.indexOf("{", ifStart);
        if (braceStart < 0) return null;

        int depth = 0;
        int i = braceStart;
        while (i < methodBody.length()) {
            char c = methodBody.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return methodBody.substring(braceStart, i + 1);
                }
            }
            i++;
        }
        return null;
    }

    /**
     * Extracts the else branch (mediaHeaderVisible = true path) from setMediaHeaderVisible.
     * Looks for the `} else {` following the false-branch.
     */
    private String extractTrueBranch(String methodBody) {
        int falseIfStart = methodBody.indexOf("if (!mediaHeaderVisible)");
        if (falseIfStart < 0) return null;

        int braceStart = methodBody.indexOf("{", falseIfStart);
        if (braceStart < 0) return null;

        // Skip past the false-branch to find } else {
        int depth = 0;
        int i = braceStart;
        while (i < methodBody.length()) {
            char c = methodBody.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    // Now look for } else {
                    int elseStart = methodBody.indexOf("} else {", i);
                    if (elseStart < 0) {
                        elseStart = methodBody.indexOf("else {", i);
                    }
                    if (elseStart < 0) return null;

                    int elseBraceStart = methodBody.indexOf("{", elseStart);
                    if (elseBraceStart < 0) return null;

                    // Extract the else body
                    int depth2 = 0;
                    int j = elseBraceStart;
                    while (j < methodBody.length()) {
                        char c2 = methodBody.charAt(j);
                        if (c2 == '{') depth2++;
                        else if (c2 == '}') {
                            depth2--;
                            if (depth2 == 0) {
                                return methodBody.substring(elseBraceStart, j + 1);
                            }
                        }
                        j++;
                    }
                    return null;
                }
            }
            i++;
        }
        return null;
    }
}
