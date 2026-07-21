package org.telegram.ui.bugtest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Standalone static-analysis exploration test for the shared-media-download-filter bug.
 *
 * Run directly with: javac StandaloneExplorationTest.java && java ... StandaloneExplorationTest
 * No Android SDK required — only reads and analyzes Java source files.
 *
 * Validates: Requirements 1.1, 1.2
 *
 * EXPECTED OUTCOME: Tests FAIL on unfixed code, confirming the bug exists.
 */
public class StandaloneExplorationTest {

    private static final String PROFILE_ACTIVITY_PATH =
            "TMessagesProj/src/main/java/org/telegram/ui/ProfileActivity.java";
    private static final String SHARED_MEDIA_LAYOUT_PATH =
            "TMessagesProj/src/main/java/org/telegram/ui/Components/SharedMediaLayout.java";

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws IOException {
        System.out.println("=".repeat(70));
        System.out.println("BUG CONDITION EXPLORATION TEST");
        System.out.println("Spec: shared-media-download-filter  |  Task 1");
        System.out.println("Validates: Requirements 1.1, 1.2");
        System.out.println("=".repeat(70));
        System.out.println();
        System.out.println("EXPECTED: All tests FAIL on unfixed code (failure = bug confirmed)");
        System.out.println();

        StandaloneExplorationTest t = new StandaloneExplorationTest();
        t.test1_getSearchOptionsItemReturnsWrongView();
        t.test2_photoVideoOptionsItemOverlapsWithOptionsSearchImageView();
        t.test3_falseVisibleBranchUsesWrongMediaOptionsItem();
        t.test4_trueVisibleBranchSetsWrongViewVisible();

        System.out.println();
        System.out.println("=".repeat(70));
        System.out.printf("RESULTS: %d passed, %d failed%n", passed, failed);
        System.out.println("=".repeat(70));

        if (failed > 0) {
            System.out.println();
            System.out.println("✅ EXPECTED OUTCOME ACHIEVED: Tests failed as expected.");
            System.out.println("   This CONFIRMS the bug exists in the unfixed code.");
            System.out.println("   After the fix is applied, these tests should PASS.");
        } else {
            System.out.println();
            System.out.println("⚠️  UNEXPECTED: All tests passed. This may indicate:");
            System.out.println("   1) The fix has already been applied, OR");
            System.out.println("   2) The test logic needs adjustment.");
        }

        // Exit with failure if all tests passed (unexpected for unfixed code)
        System.exit(failed > 0 ? 1 : 0);
    }

    // =========================================================================
    // TEST 1
    // BUG: getSearchOptionsItem() returns optionsSearchImageView, not photoVideoOptionsItem
    // =========================================================================
    private void test1_getSearchOptionsItemReturnsWrongView() throws IOException {
        String testName = "test1_getSearchOptionsItem_returnsWrongView";
        System.out.println("--- " + testName);

        String sharedMediaSource = readFile(SHARED_MEDIA_LAYOUT_PATH);

        // Confirm: getSearchOptionsItem() returns optionsSearchImageView
        boolean returnsWrongType = sharedMediaSource.contains("public RLottieImageView getSearchOptionsItem()") &&
                sharedMediaSource.contains("return optionsSearchImageView;");

        String profileSource = readFile(PROFILE_ACTIVITY_PATH);
        String methodBody = extractMethodBody(profileSource, "setMediaHeaderVisible");

        if (methodBody == null) {
            fail(testName, "INCONCLUSIVE: setMediaHeaderVisible() method not found in ProfileActivity");
            return;
        }

        // BUG ASSERTION: setMediaHeaderVisible() should NOT call getSearchOptionsItem()
        // On unfixed code: this is true (method DOES call it) → assertion fails → BUG CONFIRMED
        boolean methodCallsGetSearchOptionsItem = methodBody.contains("getSearchOptionsItem()");

        if (methodCallsGetSearchOptionsItem && returnsWrongType) {
            // Expected failure on unfixed code
            fail(testName,
                    "COUNTEREXAMPLE: setMediaHeaderVisible() calls getSearchOptionsItem() which returns\n" +
                    "  optionsSearchImageView (RLottieImageView — animation view), NOT photoVideoOptionsItem.\n" +
                    "  → isBugCondition(ProfileActivity, TAB_FILES, mediaHeaderVisible=true) = TRUE\n" +
                    "  → Expected: photoVideoOptionsItem.getVisibility() == View.VISIBLE\n" +
                    "  → Actual: optionsSearchImageView is set VISIBLE via mediaOptionsItem (WRONG VIEW)\n" +
                    "  FIX: Remove 'getSearchOptionsItem()' from setMediaHeaderVisible()"
            );
        } else if (!methodCallsGetSearchOptionsItem) {
            pass(testName, "getSearchOptionsItem() not found in setMediaHeaderVisible() — fix may already be applied");
        } else {
            fail(testName, "getSearchOptionsItem() is called but returns correct view — unexpected");
        }
    }

    // =========================================================================
    // TEST 2
    // BUG: photoVideoOptionsItem and optionsSearchImageView added at SAME position
    // =========================================================================
    private void test2_photoVideoOptionsItemOverlapsWithOptionsSearchImageView() throws IOException {
        String testName = "test2_photoVideoOptionsItem_overlapsWith_optionsSearchImageView";
        System.out.println("--- " + testName);

        List<String> lines = readLines(SHARED_MEDIA_LAYOUT_PATH);

        String photoVideoAddView = null;
        String optionsSearchAddView = null;
        int photoVideoLineNum = -1;
        int optionsSearchLineNum = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("addView(photoVideoOptionsItem") && photoVideoAddView == null) {
                photoVideoAddView = line.trim();
                photoVideoLineNum = i + 1;
            }
            if (line.contains("addView(optionsSearchImageView") && optionsSearchAddView == null) {
                optionsSearchAddView = line.trim();
                optionsSearchLineNum = i + 1;
            }
        }

        if (photoVideoAddView == null || optionsSearchAddView == null) {
            fail(testName, "Could not find addView calls for both views");
            return;
        }

        System.out.println("  photoVideoOptionsItem addView (line " + photoVideoLineNum + "): " + photoVideoAddView);
        System.out.println("  optionsSearchImageView addView (line " + optionsSearchLineNum + "): " + optionsSearchAddView);

        // BUG: both use same gravity with no rightMargin offset
        // Fixed: photoVideoOptionsItem should have rightMargin=48 to avoid overlap with otherItem
        boolean photoVideoHasRightMarginOffset =
                photoVideoAddView.matches(".*createFrame\\s*\\(\\s*48\\s*,\\s*56\\s*,.*,\\s*0\\s*,\\s*0\\s*,\\s*48\\s*,\\s*0\\s*\\).*") ||
                photoVideoAddView.contains(", 0, 0, 48, 0)");

        if (!photoVideoHasRightMarginOffset) {
            fail(testName,
                    "COUNTEREXAMPLE: photoVideoOptionsItem and optionsSearchImageView are both added to\n" +
                    "  actionBar at Gravity.RIGHT|BOTTOM with rightMargin=0 (same position → OVERLAP).\n" +
                    "  photoVideoOptionsItem addView: [" + photoVideoAddView + "]\n" +
                    "  optionsSearchImageView addView: [" + optionsSearchAddView + "]\n" +
                    "  optionsSearchImageView is added AFTER photoVideoOptionsItem → renders ON TOP.\n" +
                    "  When bug causes optionsSearchImageView to be set VISIBLE, it covers\n" +
                    "  photoVideoOptionsItem (download filter button) → user cannot see filter button.\n" +
                    "  FIX: Add rightMargin=48dp to photoVideoOptionsItem addView call:\n" +
                    "       createFrame(48, 56, Gravity.RIGHT|Gravity.BOTTOM, 0, 0, 48, 0)"
            );
        } else {
            pass(testName, "photoVideoOptionsItem has rightMargin offset — overlap fix applied");
        }
    }

    // =========================================================================
    // TEST 3
    // BUG: setMediaHeaderVisible(false) hides optionsSearchImageView via mediaOptionsItem,
    //      NOT photoVideoOptionsItem — leaving photoVideoOptionsItem in inconsistent state
    // =========================================================================
    private void test3_falseVisibleBranchUsesWrongMediaOptionsItem() throws IOException {
        String testName = "test3_setMediaHeaderVisible_false_usesWrongView";
        System.out.println("--- " + testName);

        String profileSource = readFile(PROFILE_ACTIVITY_PATH);
        String methodBody = extractMethodBody(profileSource, "setMediaHeaderVisible");

        if (methodBody == null) {
            fail(testName, "setMediaHeaderVisible() not found");
            return;
        }

        String falseBranch = extractFalseBranch(methodBody);
        if (falseBranch == null) {
            fail(testName, "false (mediaHeaderVisible=false) branch not found");
            return;
        }

        // BUG: false-branch uses mediaOptionsItem (= optionsSearchImageView) to set GONE
        // This hides the animation view but leaves photoVideoOptionsItem in inconsistent state
        boolean hasMediaOptionsItemInFalseBranch = falseBranch.contains("mediaOptionsItem");

        System.out.println("  false-branch contains 'mediaOptionsItem': " + hasMediaOptionsItemInFalseBranch);

        if (hasMediaOptionsItemInFalseBranch) {
            fail(testName,
                    "COUNTEREXAMPLE: setMediaHeaderVisible(false) branch calls\n" +
                    "  mediaOptionsItem.setVisibility(View.GONE) where mediaOptionsItem = getSearchOptionsItem()\n" +
                    "  = optionsSearchImageView (WRONG VIEW — only hides animation view).\n" +
                    "  photoVideoOptionsItem (download filter button) is NOT explicitly set GONE/INVISIBLE\n" +
                    "  in this branch → inconsistent visibility state.\n" +
                    "  → After setMediaHeaderVisible(false): photoVideoOptionsItem may remain VISIBLE\n" +
                    "    while otherItem (three-dot menu) is also set VISIBLE → OVERLAP in action bar.\n" +
                    "  FIX: Remove 'mediaOptionsItem' usage from setMediaHeaderVisible(); SharedMediaLayout\n" +
                    "       already manages optionsSearchImageView via animateSearchToOptions()"
            );
        } else {
            pass(testName, "mediaOptionsItem not in false-branch — fix may already be applied");
        }
    }

    // =========================================================================
    // TEST 4 (Primary Bug)
    // BUG: setMediaHeaderVisible(true) explicitly sets optionsSearchImageView VISIBLE
    //      via mediaOptionsItem, causing it to overlap photoVideoOptionsItem
    // =========================================================================
    private void test4_trueVisibleBranchSetsWrongViewVisible() throws IOException {
        String testName = "test4_setMediaHeaderVisible_true_setsWrongViewVisible";
        System.out.println("--- " + testName);

        String profileSource = readFile(PROFILE_ACTIVITY_PATH);
        String methodBody = extractMethodBody(profileSource, "setMediaHeaderVisible");

        if (methodBody == null) {
            fail(testName, "setMediaHeaderVisible() not found");
            return;
        }

        String trueBranch = extractTrueBranch(methodBody);
        if (trueBranch == null) {
            fail(testName, "true (mediaHeaderVisible=true) branch not found");
            return;
        }

        boolean hasBugPattern =
                trueBranch.contains("mediaOptionsItem") &&
                trueBranch.contains("setVisibility(View.VISIBLE)");

        System.out.println("  true-branch has mediaOptionsItem.setVisibility(VISIBLE): " + hasBugPattern);

        if (hasBugPattern) {
            fail(testName,
                    "COUNTEREXAMPLE (PRIMARY BUG): setMediaHeaderVisible(true) branch calls\n" +
                    "  mediaOptionsItem.setVisibility(View.VISIBLE) where mediaOptionsItem = \n" +
                    "  sharedMediaLayout.getSearchOptionsItem() = optionsSearchImageView (RLottieImageView).\n" +
                    "  This explicitly sets the animation view VISIBLE at same position as photoVideoOptionsItem.\n" +
                    "  Context that triggers this bug:\n" +
                    "    hostActivity = ProfileActivity\n" +
                    "    currentTab = TAB_FILES (isOptionsItemVisible() = true)\n" +
                    "    mediaHeaderVisible transitions from false → true\n" +
                    "  Expected (after fix): photoVideoOptionsItem.getVisibility() == View.VISIBLE\n" +
                    "                        optionsSearchImageView managed only by SharedMediaLayout\n" +
                    "  Actual (before fix):  optionsSearchImageView.setVisibility(VISIBLE) called from ProfileActivity\n" +
                    "                        → overlaps photoVideoOptionsItem → filter button NOT visible to user\n" +
                    "  FIX: Remove 'ImageView mediaOptionsItem = sharedMediaLayout.getSearchOptionsItem()'\n" +
                    "       and all mediaOptionsItem.setVisibility() calls from setMediaHeaderVisible()"
            );
        } else {
            pass(testName, "mediaOptionsItem.setVisibility(VISIBLE) not in true-branch — fix may already be applied");
        }
    }

    // =========================================================================
    // TEST RESULT HELPERS
    // =========================================================================

    private void pass(String testName, String message) {
        passed++;
        System.out.println("  ✅ PASS: " + message);
        System.out.println();
    }

    private void fail(String testName, String message) {
        failed++;
        System.out.println("  ❌ FAIL (expected on unfixed code): " + message);
        System.out.println();
    }

    // =========================================================================
    // FILE READING HELPERS
    // =========================================================================

    private String readFile(String relativePath) throws IOException {
        File root = findWorkspaceRoot();
        File file = new File(root, relativePath);
        if (!file.exists()) {
            throw new IOException("File not found: " + file.getAbsolutePath());
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private List<String> readLines(String relativePath) throws IOException {
        File root = findWorkspaceRoot();
        File file = new File(root, relativePath);
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = r.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private File findWorkspaceRoot() {
        File dir = new File(System.getProperty("user.dir"));
        while (dir != null) {
            if (new File(dir, "TMessagesProj").exists()) {
                return dir;
            }
            dir = dir.getParentFile();
        }
        return new File(System.getProperty("user.dir"));
    }

    // =========================================================================
    // METHOD BODY EXTRACTION HELPERS
    // =========================================================================

    private String extractMethodBody(String source, String methodName) {
        int methodStart = source.indexOf("void " + methodName + "(");
        if (methodStart < 0) methodStart = source.indexOf("private " + methodName + "(");
        if (methodStart < 0) return null;

        int braceStart = source.indexOf("{", methodStart);
        if (braceStart < 0) return null;

        int depth = 0, i = braceStart;
        while (i < source.length()) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return source.substring(braceStart, i + 1); }
            i++;
        }
        return null;
    }

    private String extractFalseBranch(String methodBody) {
        int ifStart = methodBody.indexOf("if (!mediaHeaderVisible)");
        if (ifStart < 0) ifStart = methodBody.indexOf("if (!visible)");
        if (ifStart < 0) return null;

        int braceStart = methodBody.indexOf("{", ifStart);
        if (braceStart < 0) return null;

        int depth = 0, i = braceStart;
        while (i < methodBody.length()) {
            char c = methodBody.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { depth--; if (depth == 0) return methodBody.substring(braceStart, i + 1); }
            i++;
        }
        return null;
    }

    private String extractTrueBranch(String methodBody) {
        // Find "if (!mediaHeaderVisible)" block first, skip it, then find "} else {"
        int falseIfStart = methodBody.indexOf("if (!mediaHeaderVisible)");
        if (falseIfStart < 0) return null;

        int braceStart = methodBody.indexOf("{", falseIfStart);
        if (braceStart < 0) return null;

        int depth = 0, i = braceStart;
        while (i < methodBody.length()) {
            char c = methodBody.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    // Find "} else {" after this closing brace
                    String remaining = methodBody.substring(i);
                    int elseIdx = remaining.indexOf("} else {");
                    if (elseIdx < 0) elseIdx = remaining.indexOf("else {");
                    if (elseIdx < 0) return null;

                    int elseBraceStart = methodBody.indexOf("{", i + elseIdx);
                    if (elseBraceStart < 0) return null;

                    int depth2 = 0, j = elseBraceStart;
                    while (j < methodBody.length()) {
                        char c2 = methodBody.charAt(j);
                        if (c2 == '{') depth2++;
                        else if (c2 == '}') { depth2--; if (depth2 == 0) return methodBody.substring(elseBraceStart, j + 1); }
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
