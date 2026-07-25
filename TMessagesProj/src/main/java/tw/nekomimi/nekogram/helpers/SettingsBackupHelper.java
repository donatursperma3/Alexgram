package tw.nekomimi.nekogram.helpers;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.LinearLayout;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.radolyn.ayugram.messages.AyuSavePreferences;
import com.radolyn.ayugram.utils.AyuGhostPreferences;

import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.CheckBoxCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.LaunchActivity;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import kotlin.text.StringsKt;
import org.telegram.messenger.Utilities;
import tw.nekomimi.nekogram.DialogConfig;
import tw.nekomimi.nekogram.NekoConfig;
import tw.nekomimi.nekogram.utils.AlertUtil;
import tw.nekomimi.nekogram.utils.FileUtil;
import tw.nekomimi.nekogram.utils.GsonUtil;
import tw.nekomimi.nekogram.utils.ShareUtil;
import xyz.nextalone.nagram.NaConfig;
import xyz.nextalone.nagram.helper.BookmarksHelper;
import xyz.nextalone.nagram.helper.LocalPeerColorHelper;
import xyz.nextalone.nagram.helper.LocalPremiumStatusHelper;

public final class SettingsBackupHelper {
    public static String backupSettingsJson(boolean isCloud, int indentSpaces) throws JSONException {
        return backupSettingsJson(isCloud, indentSpaces, true);
    }

    public static String backupSettingsJson(boolean isCloud, int indentSpaces, boolean includeApiKeys) throws JSONException {

        JSONObject configJson = new JSONObject();

        ArrayList<String> userconfig = new ArrayList<>();
        userconfig.add("saveIncomingPhotos");
        userconfig.add("passcodeHash");
        userconfig.add("passcodeType");
        userconfig.add("passcodeHash");
        userconfig.add("autoLockIn");
        userconfig.add("useFingerprint");
        spToJSON("userconfing", configJson, userconfig::contains, isCloud);

        ArrayList<String> mainconfig = new ArrayList<>();
        mainconfig.add("saveToGallery");
        mainconfig.add("autoplayGifs");
        mainconfig.add("autoplayVideo");
        mainconfig.add("mapPreviewType");
        mainconfig.add("raiseToSpeak");
        mainconfig.add("customTabs");
        mainconfig.add("directShare");
        mainconfig.add("shuffleMusic");
        mainconfig.add("playOrderReversed");
        mainconfig.add("inappCamera");
        mainconfig.add("repeatMode");
        mainconfig.add("fontSize");
        mainconfig.add("bubbleRadius");
        mainconfig.add("ivFontSize");
        mainconfig.add("allowBigEmoji");
        mainconfig.add("streamMedia");
        mainconfig.add("saveStreamMedia");
        mainconfig.add("smoothKeyboard");
        mainconfig.add("pauseMusicOnRecord");
        mainconfig.add("streamAllVideo");
        mainconfig.add("streamMkv");
        mainconfig.add("suggestStickers");
        mainconfig.add("sortContactsByName");
        mainconfig.add("sortFilesByName");
        mainconfig.add("noSoundHintShowed");
        mainconfig.add("directShareHash");
        mainconfig.add("useThreeLinesLayout");
        mainconfig.add("archiveHidden");
        mainconfig.add("distanceSystemType");
        mainconfig.add("loopStickers");
        mainconfig.add("keepMedia");
        mainconfig.add("noStatusBar");
        mainconfig.add("lastKeepMediaCheckTime");
        mainconfig.add("searchMessagesAsListHintShows");
        mainconfig.add("searchMessagesAsListUsed");
        mainconfig.add("stickersReorderingHintUsed");
        mainconfig.add("textSelectionHintShows");
        mainconfig.add("scheduledOrNoSoundHintShows");
        mainconfig.add("lockRecordAudioVideoHint");
        mainconfig.add("disableVoiceAudioEffects");
        mainconfig.add("chatSwipeAction");

        if (!isCloud) mainconfig.add("theme");
        mainconfig.add("selectedAutoNightType");
        mainconfig.add("autoNightScheduleByLocation");
        mainconfig.add("autoNightBrighnessThreshold");
        mainconfig.add("autoNightDayStartTime");
        mainconfig.add("autoNightDayEndTime");
        mainconfig.add("autoNightSunriseTime");
        mainconfig.add("autoNightCityName");
        mainconfig.add("autoNightSunsetTime");
        mainconfig.add("autoNightLocationLatitude3");
        mainconfig.add("autoNightLocationLongitude3");
        mainconfig.add("autoNightLastSunCheckDay");

        mainconfig.add("lang_code");

        mainconfig.add("web_restricted_domains2");

        spToJSON("mainconfig", configJson, mainconfig::contains);
        if (!isCloud) spToJSON("themeconfig", configJson, null);
        spToJSON("nkmrcfg", configJson, null, includeApiKeys);

        return configJson.toString(indentSpaces);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter) throws JSONException {
        spToJSON(sp, object, filter, true);
    }

    private static void spToJSON(String sp, JSONObject object, Function<String, Boolean> filter, boolean includeApiKeys) throws JSONException {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(sp, Activity.MODE_PRIVATE);
        JSONObject jsonConfig = new JSONObject();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            if (!includeApiKeys && (key.endsWith("Key") || key.contains("Token") || key.contains("AccountID"))) {
                continue;
            }
            if (filter != null && !filter.apply(key)) {
                continue;
            }
            if (entry.getValue() instanceof Long) {
                key = key + "_long";
            } else if (entry.getValue() instanceof Float) {
                key = key + "_float";
            }
            jsonConfig.put(key, entry.getValue());
        }
        object.put(sp, jsonConfig);
    }

    public static void importSettings(Context context, File settingsFile) {
        AlertUtil.showConfirm(context,
                getString(R.string.ImportSettingsAlert),
                R.drawable.msg_photo_settings_solar,
                getString(R.string.Import),
                true,
                () -> importSettingsConfirmed(context, settingsFile));
    }

    public static void importSettingsConfirmed(Context context, File settingsFile) {
        try {
            JsonObject configJson = GsonUtil.toJsonObject(FileUtil.readUtf8String(settingsFile));
            importSettings(configJson);

            AlertDialog restart = new AlertDialog(context, 0);
            restart.setTitle(getString(R.string.NagramX));
            restart.setMessage(getString(R.string.RestartAppToTakeEffect));
            restart.setPositiveButton(getString(R.string.OK), (__, ___) -> AppRestartHelper.triggerRebirth(context, new Intent(context, LaunchActivity.class)));
            restart.show();
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(context, e);
        }
    }

    @SuppressLint("ApplySharedPref")
    public static void importSettings(JsonObject configJson) throws JSONException {
        Set<String> allowedKeys = new HashSet<>();
        try {
            allowedKeys.addAll(NekoConfig.getAllKeys());
            allowedKeys.addAll(NaConfig.INSTANCE.getAllKeys());
        } catch (Throwable ignore) {
        }
        String[] preservePrefixes = {
                AyuGhostPreferences.ghostReadExclusionPrefix,
                AyuGhostPreferences.ghostTypingExclusionPrefix,
                AyuSavePreferences.saveExclusionPrefix,
                LocalNameHelper.chatNameOverridePrefix,
                LocalNameHelper.userNameOverridePrefix,
                DialogConfig.customForumTabPrefix,
                LocalPeerColorHelper.KEY_PREFIX,
                LocalPremiumStatusHelper.KEY_PREFIX,
                BookmarksHelper.KEY_PREFIX
        };

        for (Map.Entry<String, JsonElement> element : configJson.entrySet()) {
            String spName = element.getKey();
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences(spName, Activity.MODE_PRIVATE);
            SharedPreferences.Editor editor = preferences.edit();
            for (Map.Entry<String, JsonElement> config : ((JsonObject) element.getValue()).entrySet()) {
                String key = config.getKey();
                JsonPrimitive value = (JsonPrimitive) config.getValue();
                if ("nkmrcfg".equals(spName)) {
                    boolean shouldSkip = true;
                    for (String prefix : preservePrefixes) {
                        if (key.startsWith(prefix)) {
                            shouldSkip = false;
                            break;
                        }
                    }
                    if (shouldSkip) {
                        String actualKey = key;
                        if (key.endsWith("_long")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_long", key);
                        } else if (key.endsWith("_float")) {
                            actualKey = StringsKt.substringBeforeLast(key, "_float", key);
                        }
                        shouldSkip = !allowedKeys.contains(actualKey);
                    }
                    if (shouldSkip) {
                        continue;
                    }
                }
                if (value.isBoolean()) {
                    editor.putBoolean(key, value.getAsBoolean());
                } else if (value.isNumber()) {
                    boolean isLong = false;
                    boolean isFloat = false;
                    if (key.endsWith("_long")) {
                        key = StringsKt.substringBeforeLast(key, "_long", key);
                        isLong = true;
                    } else if (key.endsWith("_float")) {
                        key = StringsKt.substringBeforeLast(key, "_float", key);
                        isFloat = true;
                    }
                    if (isLong) {
                        editor.putLong(key, value.getAsLong());
                    } else if (isFloat) {
                        editor.putFloat(key, value.getAsFloat());
                    } else {
                        editor.putInt(key, value.getAsInt());
                    }
                } else {
                    editor.putString(key, value.getAsString());
                }
            }
            editor.commit();
        }
    }

    public static JsonObject backupUserConfig(int account) {
        JsonObject data = new JsonObject();
        SharedPreferences preferences = UserConfig.getInstance(account).getPreferences();
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                data.addProperty(key, (Boolean) value);
            } else if (value instanceof Float) {
                data.addProperty(key, (Float) value);
            } else if (value instanceof Long) {
                data.addProperty(key, (Long) value);
            } else if (value instanceof Double) {
                data.addProperty(key, (Double) value);
            } else if (value instanceof Integer) {
                data.addProperty(key, (Integer) value);
            } else if (value instanceof String) {
                data.addProperty(key, (String) value);
            } else if (value instanceof java.util.Set) {
                JsonArray array = new JsonArray();
                for (Object item : (java.util.Set<?>) value) {
                    if (item != null) {
                        array.add(item.toString());
                    }
                }
                data.add(key, array);
            } else if (value != null) {
                data.addProperty(key, value.toString());
            }
        }
        JsonObject result = new JsonObject();
        result.addProperty("format", "AlexgramAccountBackup");
        result.addProperty("version", 1);
        result.addProperty("account", account);
        result.add("data", data);
        return result;
    }

    public static File backupUserConfig(Context context, int account) throws Exception {
        JsonObject backupObject = backupUserConfig(account);
        File cacheFile = new File(AndroidUtilities.getCacheDir(), String.format("alexgram-account-%d-%d.json", account, System.currentTimeMillis()));
        FileUtil.writeUtf8String(backupObject.toString(), cacheFile);
        return cacheFile;
    }

    public static int importUserConfig(Context context, android.net.Uri uri) throws Exception {
        return importUserConfig(context, uri, null);
    }

    public static int importUserConfig(Context context, android.net.Uri uri, String password) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("Invalid import parameters");
        }
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IOException("Unable to open file");
            }
            byte[] fileBytes = is.readAllBytes();
            if (fileBytes.length >= 2 && fileBytes[0] == 'P' && fileBytes[1] == 'K') {
                return importUserConfigFromZip(fileBytes, password);
            }
            String text = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
            JsonObject root = GsonUtil.toJsonObject(text);
            return importUserConfig(root);
        }
    }

    private static int importUserConfigFromZip(byte[] zipBytes, String password) throws Exception {
        int importCount = 0;
        try (ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(zipBytes)))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                byte[] entryBytes = readAllBytes(zipInput);
                if (name.endsWith(".json.enc")) {
                    if (password == null) {
                        throw new BackupPasswordRequiredException();
                    }
                    entryBytes = decryptBackupData(entryBytes, password);
                }
                if (!name.endsWith(".json") && !name.endsWith(".json.enc")) {
                    continue;
                }
                String text = new String(entryBytes, java.nio.charset.StandardCharsets.UTF_8);
                JsonObject root = GsonUtil.toJsonObject(text);
                importUserConfig(root);
                importCount++;
            }
        }
        if (importCount == 0) {
            throw new Exception("No account backup found in ZIP");
        }
        return importCount;
    }

    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = inputStream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    public static File backupUserConfigZip(Context context, int account, String password) throws Exception {
        JsonObject backupObject = backupUserConfig(account);
        byte[] data = backupObject.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String entryName = String.format("alexgram-account-%d-%d.json", account, System.currentTimeMillis());
        if (password != null && !password.isEmpty()) {
            data = encryptBackupData(data, password);
            entryName += ".enc";
        }
        File cacheFile = new File(AndroidUtilities.getCacheDir(), String.format("alexgram-account-%d-%d.zip", account, System.currentTimeMillis()));
        try (FileOutputStream fos = new FileOutputStream(cacheFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry zipEntry = new ZipEntry(entryName);
            zos.putNextEntry(zipEntry);
            zos.write(data);
            zos.closeEntry();
        }
        return cacheFile;
    }

    public static File appendUserConfigToZip(Context context, int account, android.net.Uri uri, String password) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("Invalid append parameters");
        }
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IOException("Unable to open file");
            }
            byte[] existingZip = is.readAllBytes();
            if (existingZip.length < 2 || existingZip[0] != 'P' || existingZip[1] != 'K') {
                throw new IllegalArgumentException("Selected file is not a ZIP archive");
            }
            byte[] newEntryBytes = backupUserConfig(account).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            String newEntryName = String.format("alexgram-account-%d-%d.json", account, System.currentTimeMillis());
            if (password != null && !password.isEmpty()) {
                newEntryBytes = encryptBackupData(newEntryBytes, password);
                newEntryName += ".enc";
            }
            File cacheFile = new File(AndroidUtilities.getCacheDir(), String.format("alexgram-account-append-%d-%d.zip", account, System.currentTimeMillis()));
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(existingZip)));
                 FileOutputStream fos = new FileOutputStream(cacheFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 ZipOutputStream zos = new ZipOutputStream(bos)) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    ZipEntry copyEntry = new ZipEntry(entry.getName());
                    copyEntry.setTime(entry.getTime());
                    zos.putNextEntry(copyEntry);
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        zos.write(buffer, 0, count);
                    }
                    zos.closeEntry();
                    zis.closeEntry();
                }
                ZipEntry newEntry = new ZipEntry(newEntryName);
                zos.putNextEntry(newEntry);
                zos.write(newEntryBytes);
                zos.closeEntry();
            }
            return cacheFile;
        }
    }

    private static byte[] encryptBackupData(byte[] plaintext, String password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        byte[] keyBytes = Utilities.computePBKDF2(password.getBytes(java.nio.charset.StandardCharsets.UTF_8), salt);
        byte[] key = Arrays.copyOf(keyBytes, 32);
        byte[] iv = new byte[12];
        random.nextBytes(iv);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        byte[] ciphertext = cipher.doFinal(plaintext);
        byte[] output = new byte[16 + 12 + ciphertext.length];
        System.arraycopy(salt, 0, output, 0, 16);
        System.arraycopy(iv, 0, output, 16, 12);
        System.arraycopy(ciphertext, 0, output, 28, ciphertext.length);
        return output;
    }

    private static byte[] decryptBackupData(byte[] encryptedData, String password) throws Exception {
        if (encryptedData.length < 28) {
            throw new BackupPasswordInvalidException();
        }
        byte[] salt = Arrays.copyOfRange(encryptedData, 0, 16);
        byte[] iv = Arrays.copyOfRange(encryptedData, 16, 28);
        byte[] ciphertext = Arrays.copyOfRange(encryptedData, 28, encryptedData.length);
        byte[] keyBytes = Utilities.computePBKDF2(password.getBytes(java.nio.charset.StandardCharsets.UTF_8), salt);
        byte[] key = Arrays.copyOf(keyBytes, 32);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        try {
            return cipher.doFinal(ciphertext);
        } catch (AEADBadTagException e) {
            throw new BackupPasswordInvalidException();
        }
    }

    public static class BackupPasswordRequiredException extends Exception {
        public BackupPasswordRequiredException() {
            super("Password required for encrypted backup file");
        }
    }

    public static class BackupPasswordInvalidException extends Exception {
        public BackupPasswordInvalidException() {
            super("Invalid password or corrupted backup file");
        }
    }

    public static int importUserConfig(JsonObject root) throws Exception {
        if (root == null) {
            throw new IllegalArgumentException("Backup file is empty");
        }
        JsonObject data = root;
        if (root.has("data") && root.get("data").isJsonObject()) {
            data = root.getAsJsonObject("data");
        }
        int targetAccount = findNextAvailableAccount();
        if (targetAccount < 0) {
            throw new Exception("No free account slot available.");
        }
        importUserConfigToAccount(targetAccount, data);
        return targetAccount;
    }

    private static int findNextAvailableAccount() {
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (!AccountInstance.getInstance(a).getUserConfig().isClientActivated()) {
                return a;
            }
        }
        return -1;
    }

    @SuppressLint("ApplySharedPref")
    private static void importUserConfigToAccount(int account, JsonObject data) {
        SharedPreferences preferences = UserConfig.getInstance(account).getPreferences();
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            String key = entry.getKey();
            JsonElement element = entry.getValue();
            if (element.isJsonNull()) {
                editor.remove(key);
            } else if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) {
                    editor.putBoolean(key, primitive.getAsBoolean());
                } else if (primitive.isNumber()) {
                    String value = primitive.getAsString();
                    if (value.contains(".")) {
                        try {
                            editor.putFloat(key, primitive.getAsFloat());
                        } catch (NumberFormatException e) {
                            editor.putString(key, value);
                        }
                    } else {
                        try {
                            editor.putLong(key, primitive.getAsLong());
                        } catch (NumberFormatException e) {
                            editor.putInt(key, primitive.getAsInt());
                        }
                    }
                } else {
                    editor.putString(key, primitive.getAsString());
                }
            } else if (element.isJsonArray()) {
                java.util.Set<String> set = new java.util.HashSet<>();
                for (JsonElement item : element.getAsJsonArray()) {
                    if (item.isJsonPrimitive()) {
                        set.add(item.getAsString());
                    }
                }
                editor.putStringSet(key, set);
            }
        }
        editor.commit();
        UserConfig.getInstance(account).reloadConfig();
    }

    public static void backupSettings(Context context, Theme.ResourcesProvider resourceProvider) {
        if (context == null) return;

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(getString(R.string.BackupSettings));

        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);

        CheckBoxCell checkBoxCell = new CheckBoxCell(context, CheckBoxCell.TYPE_CHECK_BOX_DEFAULT, resourceProvider);
        checkBoxCell.setBackground(Theme.getSelectorDrawable(false));
        checkBoxCell.setText(getString(R.string.ExportSettingsIncludeApiKeys), "", true, false);
        checkBoxCell.setPadding(LocaleController.isRTL ? dp(16) : dp(8), 0, LocaleController.isRTL ? dp(8) : dp(16), 0);
        checkBoxCell.setChecked(true, false);
        checkBoxCell.setOnClickListener(v -> {
            CheckBoxCell cell = (CheckBoxCell) v;
            cell.setChecked(!cell.isChecked(), true);
        });
        linearLayout.addView(checkBoxCell, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 48));

        builder.setView(linearLayout);
        builder.setPositiveButton(getString(R.string.ExportTheme), (dialog, which) -> {
            boolean includeApiKeys = checkBoxCell.isChecked();
            try {
                File cacheFile = new File(AndroidUtilities.getCacheDir(), new Date().toLocaleString() + ".nekox-settings.json");
                FileUtil.writeUtf8String(SettingsBackupHelper.backupSettingsJson(false, 4, includeApiKeys), cacheFile);
                ShareUtil.shareFile(context, cacheFile);
            } catch (Exception e) {
                AlertUtil.showSimpleAlert(context, e);
            }
        });
        builder.setNegativeButton(getString(R.string.Cancel), null);
        builder.show();
    }
}
