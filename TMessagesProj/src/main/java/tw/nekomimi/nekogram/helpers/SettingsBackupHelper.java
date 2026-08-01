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
import org.telegram.messenger.FileLog;
import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
        UserConfig userConfig = UserConfig.getInstance(account);
        if (!userConfig.isConfigLoaded()) {
            userConfig.loadConfig();
        }
        JsonObject data = new JsonObject();
        JsonObject types = new JsonObject();
        SharedPreferences preferences = userConfig.getPreferences();
        spToJson(preferences, data, types);

        JsonObject mainData = new JsonObject();
        JsonObject mainTypes = new JsonObject();
        SharedPreferences mainPref = getMainPreferences(account);
        spToJson(mainPref, mainData, mainTypes);

        JsonObject result = new JsonObject();
        result.addProperty("format", "AlexgramAccountBackup");
        result.addProperty("version", 2);
        result.addProperty("account", account);
        result.add("data", data);
        result.add("__types__", types);
        result.add("mainconfig", mainData);
        result.add("__mainconfig_types__", mainTypes);
        return result;
    }

    private static SharedPreferences getMainPreferences(int account) {
        if (account == 0) {
            return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        } else {
            return ApplicationLoader.applicationContext.getSharedPreferences("mainconfig" + account, Activity.MODE_PRIVATE);
        }
    }

    private static void spToJson(SharedPreferences preferences, JsonObject data, JsonObject types) {
        if (preferences == null) return;
        for (Map.Entry<String, ?> entry : preferences.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                data.addProperty(key, (Boolean) value);
                types.addProperty(key, "boolean");
            } else if (value instanceof Float) {
                data.addProperty(key, (Float) value);
                types.addProperty(key, "float");
            } else if (value instanceof Long) {
                data.addProperty(key, (Long) value);
                types.addProperty(key, "long");
            } else if (value instanceof Double) {
                data.addProperty(key, (Double) value);
                types.addProperty(key, "float");
            } else if (value instanceof Integer) {
                data.addProperty(key, (Integer) value);
                types.addProperty(key, "int");
            } else if (value instanceof String) {
                data.addProperty(key, (String) value);
                types.addProperty(key, "string");
            } else if (value instanceof java.util.Set) {
                JsonArray array = new JsonArray();
                for (Object item : (java.util.Set<?>) value) {
                    if (item != null) {
                        array.add(item.toString());
                    }
                }
                data.add(key, array);
                types.addProperty(key, "stringset");
            } else if (value != null) {
                data.addProperty(key, value.toString());
                types.addProperty(key, "string");
            }
        }
    }

    public static ArrayList<File> getAccountFiles(int account) {
        ArrayList<File> filesList = new ArrayList<>();
        File dir;
        if (account == 0) {
            dir = ApplicationLoader.getFilesDirFixed();
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        String name = file.getName();
                        if (name.startsWith("tgnet") || name.startsWith("cache4") || name.endsWith(".db") || name.endsWith(".dat")) {
                            filesList.add(file);
                        }
                    }
                }
            }
        } else {
            dir = new File(ApplicationLoader.getFilesDirFixed(), "account" + account);
            if (dir.exists() && dir.isDirectory()) {
                addDirectoryFiles(dir, filesList);
            }
        }
        return filesList;
    }

    private static void addDirectoryFiles(File currentDir, ArrayList<File> filesList) {
        File[] files = currentDir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    filesList.add(file);
                } else if (file.isDirectory()) {
                    addDirectoryFiles(file, filesList);
                }
            }
        }
    }

    public interface ProgressListener {
        void onProgress(int percent, String statusText);
    }

    private static void backupAccountPackageToZip(int account, ZipOutputStream zos, String entryPrefix, String password) throws Exception {
        backupAccountPackageToZip(account, zos, entryPrefix, password, null, 0, 100);
    }

    private static void backupAccountPackageToZip(int account, ZipOutputStream zos, String entryPrefix, String password, ProgressListener listener, int startPercent, int endPercent) throws Exception {
        if (listener != null) listener.onProgress(startPercent, "Saving account configuration...");
        JsonObject backupObject = backupUserConfig(account);
        byte[] jsonBytes = backupObject.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        String configEntryName = entryPrefix + "config.json";
        if (password != null && !password.isEmpty()) {
            jsonBytes = encryptBackupData(jsonBytes, password);
            configEntryName += ".enc";
        }
        ZipEntry configZipEntry = new ZipEntry(configEntryName);
        zos.putNextEntry(configZipEntry);
        zos.write(jsonBytes);
        zos.closeEntry();

        ArrayList<File> filesList = getAccountFiles(account);
        File baseDir = (account == 0) ? ApplicationLoader.getFilesDirFixed() : new File(ApplicationLoader.getFilesDirFixed(), "account" + account);
        int fileCount = filesList.size();
        for (int i = 0; i < fileCount; i++) {
            File file = filesList.get(i);
            int stepPercent = startPercent + (int) (((i + 1) / (float) Math.max(1, fileCount)) * (endPercent - startPercent));
            if (listener != null) listener.onProgress(stepPercent, "Packing " + file.getName() + "...");
            String relativePath;
            if (account == 0) {
                relativePath = file.getName();
            } else {
                relativePath = baseDir.toURI().relativize(file.toURI()).getPath();
            }
            byte[] fileBytes;
            try {
                java.io.FileInputStream fis = new java.io.FileInputStream(file);
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int read;
                while ((read = fis.read(buf)) != -1) {
                    bos.write(buf, 0, read);
                }
                fis.close();
                fileBytes = bos.toByteArray();
            } catch (Exception e) {
                FileLog.e(e);
                continue;
            }
            if (fileBytes == null) continue;
            String fileEntryName = entryPrefix + "files/" + relativePath;
            if (password != null && !password.isEmpty()) {
                fileBytes = encryptBackupData(fileBytes, password);
                fileEntryName += ".enc";
            }
            ZipEntry zipEntry = new ZipEntry(fileEntryName);
            zos.putNextEntry(zipEntry);
            zos.write(fileBytes);
            zos.closeEntry();
        }
    }

    public static File backupUserConfig(Context context, int account) throws Exception {
        JsonObject backupObject = backupUserConfig(account);
        File cacheFile = new File(AndroidUtilities.getCacheDir(), String.format("alexgram-account-%d-%d.json", account, System.currentTimeMillis()));
        FileUtil.writeUtf8String(backupObject.toString(), cacheFile);
        return cacheFile;
    }

    public static int importUserConfig(Context context, android.net.Uri uri) throws Exception {
        return importUserConfig(context, uri, null, null);
    }

    public static int importUserConfig(Context context, android.net.Uri uri, String password) throws Exception {
        return importUserConfig(context, uri, password, null);
    }

    public static int importUserConfig(Context context, android.net.Uri uri, String password, ProgressListener listener) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("Invalid import parameters");
        }
        if (listener != null) listener.onProgress(5, "Reading backup file...");
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IOException("Unable to open file");
            }
            byte[] fileBytes = readAllBytes(is);
            if (fileBytes.length >= 2 && fileBytes[0] == 'P' && fileBytes[1] == 'K') {
                return importUserConfigFromZip(fileBytes, password, listener);
            }
            if (listener != null) listener.onProgress(40, "Parsing account JSON...");
            String text = new String(fileBytes, java.nio.charset.StandardCharsets.UTF_8);
            JsonObject root = GsonUtil.toJsonObject(text);
            if (listener != null) listener.onProgress(80, "Restoring configuration...");
            int res = importUserConfig(root);
            if (listener != null) listener.onProgress(100, "Restore complete.");
            return 1;
        }
    }

    private static class ZipPackage {
        String configName;
        byte[] configBytes;
        java.util.Map<String, byte[]> filesMap = new java.util.HashMap<>();
    }

    private static int importUserConfigFromZip(byte[] zipBytes, String password) throws Exception {
        return importUserConfigFromZip(zipBytes, password, null);
    }

    private static int importUserConfigFromZip(byte[] zipBytes, String password, ProgressListener listener) throws Exception {
        Map<String, byte[]> allEntries = new HashMap<>();
        if (listener != null) listener.onProgress(10, "Extracting ZIP archive entries...");
        try (ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(zipBytes)))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    allEntries.put(entry.getName(), readAllBytes(zipInput));
                }
            }
        }

        if (allEntries.isEmpty()) {
            throw new Exception("ZIP archive is empty");
        }

        List<String> configPaths = new ArrayList<>();
        for (String path : allEntries.keySet()) {
            String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
            if (fileName.equals("config.json") || fileName.equals("config.json.enc") ||
                fileName.equals("userconfig.json") || fileName.equals("userconfig.json.enc") ||
                fileName.equals("settings.json") || fileName.equals("settings.json.enc")) {
                configPaths.add(path);
            }
        }

        if (configPaths.isEmpty()) {
            for (String path : allEntries.keySet()) {
                if (!path.contains("/") && (path.endsWith(".json") || path.endsWith(".json.enc") || path.endsWith(".enc"))) {
                    configPaths.add(path);
                }
            }
        }

        if (configPaths.isEmpty()) {
            throw new Exception("No account backup configuration found in ZIP");
        }

        Map<String, ZipPackage> packages = new java.util.LinkedHashMap<>();
        for (String configPath : configPaths) {
            String dirPrefix = configPath.contains("/") ? configPath.substring(0, configPath.lastIndexOf('/') + 1) : "";
            String configFileName = configPath.contains("/") ? configPath.substring(configPath.lastIndexOf('/') + 1) : configPath;

            ZipPackage pkg = new ZipPackage();
            pkg.configName = configFileName;
            pkg.configBytes = allEntries.get(configPath);

            for (Map.Entry<String, byte[]> entry : allEntries.entrySet()) {
                String entryPath = entry.getKey();
                if (entryPath.equals(configPath)) continue;

                if (dirPrefix.isEmpty() || entryPath.startsWith(dirPrefix)) {
                    String relInPkg = dirPrefix.isEmpty() ? entryPath : entryPath.substring(dirPrefix.length());
                    String fileRelPath;
                    if (relInPkg.contains("/files/")) {
                        fileRelPath = relInPkg.substring(relInPkg.indexOf("/files/") + 7);
                    } else if (relInPkg.startsWith("files/")) {
                        fileRelPath = relInPkg.substring("files/".length());
                    } else {
                        fileRelPath = relInPkg;
                    }
                    if (!fileRelPath.isEmpty() && !fileRelPath.endsWith("/")) {
                        pkg.filesMap.put(fileRelPath, entry.getValue());
                    }
                }
            }
            packages.put(configPath, pkg);
        }

        int importedCount = 0;
        int totalPkgs = packages.size();
        int pkgIndex = 0;
        for (ZipPackage pkg : packages.values()) {
            if (pkg.configBytes == null) continue;
            pkgIndex++;
            int pkgStartP = 20 + (int) (((pkgIndex - 1) / (float) Math.max(1, totalPkgs)) * 75);
            int pkgEndP = 20 + (int) ((pkgIndex / (float) Math.max(1, totalPkgs)) * 75);
            if (listener != null) listener.onProgress(pkgStartP, String.format("Restoring account %d of %d...", pkgIndex, totalPkgs));

            byte[] configBytes = pkg.configBytes;
            if (pkg.configName != null && pkg.configName.endsWith(".enc")) {
                if (password == null || password.isEmpty()) throw new BackupPasswordRequiredException();
                try {
                    configBytes = decryptBackupData(configBytes, password);
                } catch (BackupPasswordRequiredException e) {
                    throw e;
                } catch (Exception e) {
                    throw new BackupPasswordInvalidException();
                }
            }
            String text = new String(configBytes, java.nio.charset.StandardCharsets.UTF_8);
            JsonObject root = GsonUtil.toJsonObject(text);
            if (root == null) continue;

            int targetAccount = findTargetAccountSlot(root);
            if (targetAccount < 0) {
                throw new Exception("No free account slot available.");
            }

            File targetDir = (targetAccount == 0) ? ApplicationLoader.getFilesDirFixed() : new File(ApplicationLoader.getFilesDirFixed(), "account" + targetAccount);
            targetDir.mkdirs();

            int totalFiles = pkg.filesMap.size();
            int fileIndex = 0;
            for (Map.Entry<String, byte[]> fileEntry : pkg.filesMap.entrySet()) {
                fileIndex++;
                int filePercent = pkgStartP + (int) ((fileIndex / (float) Math.max(1, totalFiles)) * (pkgEndP - pkgStartP));
                if (listener != null) listener.onProgress(filePercent, "Restoring " + fileEntry.getKey() + "...");

                String fileName = fileEntry.getKey();
                byte[] fBytes = fileEntry.getValue();
                if (fileName.endsWith(".enc")) {
                    if (password == null || password.isEmpty()) throw new BackupPasswordRequiredException();
                    try {
                        fBytes = decryptBackupData(fBytes, password);
                    } catch (BackupPasswordRequiredException e) {
                        throw e;
                    } catch (Exception e) {
                        throw new BackupPasswordInvalidException();
                    }
                    fileName = fileName.substring(0, fileName.length() - ".enc".length());
                }
                File destFile = new File(targetDir, fileName);
                File parent = destFile.getParentFile();
                if (parent != null) parent.mkdirs();
                try {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(destFile);
                    fos.write(fBytes);
                    fos.close();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            importUserConfigToAccount(targetAccount, root);
            importedCount++;
        }

        if (listener != null) listener.onProgress(100, "Restore complete.");
        return importedCount;
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
        return backupUserConfigZip(context, account, password, null);
    }

    public static File backupUserConfigZip(Context context, int account, String password, ProgressListener listener) throws Exception {
        if (listener != null) listener.onProgress(5, "Preparing account files...");
        File cacheFile = new File(AndroidUtilities.getCacheDir(), String.format("alexgram-account-%d-%d.zip", account, System.currentTimeMillis()));
        try (FileOutputStream fos = new FileOutputStream(cacheFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            backupAccountPackageToZip(account, zos, String.format("account_%d/", account), password, listener, 10, 95);
        }
        if (listener != null) listener.onProgress(100, "Backup complete.");
        return cacheFile;
    }

    public static File backupAllAccountsZip(Context context, String password) throws Exception {
        return backupAllAccountsZip(context, password, null);
    }

    public static File backupAllAccountsZip(Context context, String password, ProgressListener listener) throws Exception {
        if (listener != null) listener.onProgress(5, "Scanning active accounts...");
        ArrayList<Integer> activeAccounts = new ArrayList<>();
        for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
            if (AccountInstance.getInstance(a).getUserConfig().isClientActivated()) {
                activeAccounts.add(a);
            }
        }
        if (activeAccounts.isEmpty()) {
            throw new Exception("No active accounts found to backup");
        }
        File cacheFile = new File(AndroidUtilities.getCacheDir(), String.format("alexgram-all-accounts-%d.zip", System.currentTimeMillis()));
        try (FileOutputStream fos = new FileOutputStream(cacheFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            int total = activeAccounts.size();
            for (int i = 0; i < total; i++) {
                int account = activeAccounts.get(i);
                int startP = 10 + (int) ((i / (float) total) * 85);
                int endP = 10 + (int) (((i + 1) / (float) total) * 85);
                if (listener != null) listener.onProgress(startP, String.format("Backing up account %d of %d...", (i + 1), total));
                backupAccountPackageToZip(account, zos, String.format("account_%d/", account), password, listener, startP, endP);
            }
        }
        if (listener != null) listener.onProgress(100, "Backup complete.");
        return cacheFile;
    }

    public static File appendUserConfigToZip(Context context, int account, android.net.Uri uri, String password) throws Exception {
        return appendUserConfigToZip(context, account, uri, password, null);
    }

    public static File appendUserConfigToZip(Context context, int account, android.net.Uri uri, String password, ProgressListener listener) throws Exception {
        if (context == null || uri == null) {
            throw new IllegalArgumentException("Invalid append parameters");
        }
        if (listener != null) listener.onProgress(5, "Opening target ZIP file...");
        long accountUserId = UserConfig.getInstance(account).getClientUserId();
        String newPrefix = String.format("account_%d/", accountUserId != 0 ? accountUserId : account);

        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                throw new IOException("Unable to open file");
            }
            byte[] existingZip = readAllBytes(is);
            if (existingZip.length < 2 || existingZip[0] != 'P' || existingZip[1] != 'K') {
                throw new IllegalArgumentException("Selected file is not a ZIP archive");
            }
            boolean hasEncryptedEntries = false;
            try (ZipInputStream zisCheck = new ZipInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(existingZip)))) {
                ZipEntry eCheck;
                while ((eCheck = zisCheck.getNextEntry()) != null) {
                    if (eCheck.getName().endsWith(".enc")) {
                        hasEncryptedEntries = true;
                        break;
                    }
                }
            } catch (Exception ignore) {
            }
            if (hasEncryptedEntries && (password == null || password.isEmpty())) {
                throw new BackupPasswordRequiredException();
            }
            if (listener != null) listener.onProgress(25, "Processing existing entries...");
            File cacheFile = new File(AndroidUtilities.getCacheDir(), String.format("alexgram-account-append-%d-%d.zip", account, System.currentTimeMillis()));
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new java.io.ByteArrayInputStream(existingZip)));
                 FileOutputStream fos = new FileOutputStream(cacheFile);
                 BufferedOutputStream bos = new BufferedOutputStream(fos);
                 ZipOutputStream zos = new ZipOutputStream(bos)) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    String entryName = entry.getName();
                    if (entryName.startsWith(newPrefix) || entryName.startsWith(String.format("account_%d/", account))) {
                        zis.closeEntry();
                        continue;
                    }
                    ZipEntry copyEntry = new ZipEntry(entryName);
                    copyEntry.setTime(entry.getTime());
                    zos.putNextEntry(copyEntry);
                    int count;
                    while ((count = zis.read(buffer)) != -1) {
                        zos.write(buffer, 0, count);
                    }
                    zos.closeEntry();
                    zis.closeEntry();
                }
                if (listener != null) listener.onProgress(60, "Appending current account data...");
                backupAccountPackageToZip(account, zos, newPrefix, password, listener, 60, 95);
            }
            if (listener != null) listener.onProgress(100, "Append complete.");
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
        if (encryptedData == null || encryptedData.length < 28) {
            throw new BackupPasswordInvalidException();
        }
        if (password == null || password.isEmpty()) {
            throw new BackupPasswordRequiredException();
        }
        byte[] salt = Arrays.copyOfRange(encryptedData, 0, 16);
        byte[] iv = Arrays.copyOfRange(encryptedData, 16, 28);
        byte[] ciphertext = Arrays.copyOfRange(encryptedData, 28, encryptedData.length);
        byte[] keyBytes = Utilities.computePBKDF2(password.getBytes(java.nio.charset.StandardCharsets.UTF_8), salt);
        byte[] key = Arrays.copyOf(keyBytes, 32);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            return cipher.doFinal(ciphertext);
        } catch (Exception e) {
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

    public static long getUserIdFromBackup(JsonObject root) {
        if (root == null) {
            return 0L;
        }
        JsonObject data = root;
        if (root.has("data") && root.get("data").isJsonObject()) {
            data = root.getAsJsonObject("data");
        }

        if (data.has("3clientUserId")) {
            try {
                return data.get("3clientUserId").getAsLong();
            } catch (Exception ignore) {}
        }
        if (data.has("3clientUserId_long")) {
            try {
                return data.get("3clientUserId_long").getAsLong();
            } catch (Exception ignore) {}
        }
        if (data.has("clientUserId")) {
            try {
                return data.get("clientUserId").getAsLong();
            } catch (Exception ignore) {}
        }
        if (data.has("clientUserId_long")) {
            try {
                return data.get("clientUserId_long").getAsLong();
            } catch (Exception ignore) {}
        }
        if (data.has("user_id")) {
            try {
                return data.get("user_id").getAsLong();
            } catch (Exception ignore) {}
        }

        if (data.has("user")) {
            try {
                JsonElement userElem = data.get("user");
                if (userElem.isJsonPrimitive()) {
                    String userBase64 = userElem.getAsString();
                    if (userBase64 != null && !userBase64.isEmpty()) {
                        byte[] bytes = android.util.Base64.decode(userBase64, android.util.Base64.DEFAULT);
                        if (bytes != null && bytes.length > 0) {
                            org.telegram.tgnet.SerializedData sData = new org.telegram.tgnet.SerializedData(bytes);
                            org.telegram.tgnet.TLRPC.User user = org.telegram.tgnet.TLRPC.User.TLdeserialize(sData, sData.readInt32(false), false);
                            sData.cleanup();
                            if (user != null && user.id != 0) {
                                return user.id;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return 0L;
    }

    private static int findTargetAccountSlot(JsonObject root) {
        long backupUserId = getUserIdFromBackup(root);
        if (backupUserId != 0L) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig userConfig = UserConfig.getInstance(a);
                if (!userConfig.isConfigLoaded()) {
                    userConfig.loadConfig();
                }
                if (userConfig.isClientActivated() && userConfig.getClientUserId() == backupUserId) {
                    FileLog.d("SettingsBackupHelper: Account " + backupUserId + " already active at slot " + a + ". Target slot " + a + " to prevent duplicate.");
                    return a;
                }
            }
        }
        return findNextAvailableAccount();
    }

    public static int importUserConfig(JsonObject root) throws Exception {
        if (root == null) {
            throw new IllegalArgumentException("Backup file is empty");
        }
        int targetAccount = findTargetAccountSlot(root);
        if (targetAccount < 0) {
            throw new Exception("No free account slot available.");
        }
        importUserConfigToAccount(targetAccount, root);
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
    private static void importUserConfigToAccount(int account, JsonObject root) {
        JsonObject data = root;
        if (root.has("data") && root.get("data").isJsonObject()) {
            data = root.getAsJsonObject("data");
        }
        JsonObject types = null;
        if (root.has("__types__") && root.get("__types__").isJsonObject()) {
            types = root.getAsJsonObject("__types__");
        }

        SharedPreferences preferences = UserConfig.getInstance(account).getPreferences();
        writePreferences(preferences, data, types);

        if (root.has("mainconfig") && root.get("mainconfig").isJsonObject()) {
            JsonObject mainData = root.getAsJsonObject("mainconfig");
            JsonObject mainTypes = root.has("__mainconfig_types__") && root.get("__mainconfig_types__").isJsonObject()
                    ? root.getAsJsonObject("__mainconfig_types__") : null;
            SharedPreferences mainPreferences = getMainPreferences(account);
            writePreferences(mainPreferences, mainData, mainTypes);
        }

        UserConfig.getInstance(account).reloadConfig();
        AndroidUtilities.runOnUIThread(() -> {
            org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.mainUserInfoChanged);
            org.telegram.messenger.NotificationCenter.getInstance(account).postNotificationName(org.telegram.messenger.NotificationCenter.mainUserInfoChanged);
        });
    }

    @SuppressLint("ApplySharedPref")
    private static void writePreferences(SharedPreferences preferences, JsonObject data, JsonObject types) {
        if (preferences == null || data == null) return;
        SharedPreferences.Editor editor = preferences.edit();
        editor.clear();
        for (Map.Entry<String, JsonElement> entry : data.entrySet()) {
            String key = entry.getKey();
            JsonElement element = entry.getValue();
            if (element.isJsonNull()) {
                editor.remove(key);
            } else if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                String typeHint = (types != null && types.has(key)) ? types.get(key).getAsString() : null;
                if (typeHint != null) {
                    switch (typeHint) {
                        case "boolean":
                            editor.putBoolean(key, primitive.getAsBoolean());
                            break;
                        case "int":
                            editor.putInt(key, primitive.getAsInt());
                            break;
                        case "long":
                            editor.putLong(key, primitive.getAsLong());
                            break;
                        case "float":
                            editor.putFloat(key, primitive.getAsFloat());
                            break;
                        case "string":
                        default:
                            editor.putString(key, primitive.getAsString());
                            break;
                    }
                } else {
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
                                long longValue = primitive.getAsLong();
                                if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
                                    editor.putInt(key, (int) longValue);
                                } else {
                                    editor.putLong(key, longValue);
                                }
                            } catch (NumberFormatException e) {
                                editor.putString(key, value);
                            }
                        }
                    } else {
                        editor.putString(key, primitive.getAsString());
                    }
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
