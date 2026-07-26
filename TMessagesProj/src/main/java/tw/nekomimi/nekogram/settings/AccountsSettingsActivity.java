// [Alexgram: Accounts Settings] - Start
package tw.nekomimi.nekogram.settings;

import static org.telegram.messenger.LocaleController.getString;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.text.method.PasswordTransformationMethod;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;

import java.io.File;
import java.util.function.Consumer;

import org.telegram.messenger.R;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UndoView;

import tw.nekomimi.nekogram.config.CellGroup;
import tw.nekomimi.nekogram.config.cell.AbstractConfigCell;
import tw.nekomimi.nekogram.config.cell.ConfigCellDivider;
import tw.nekomimi.nekogram.config.cell.ConfigCellHeader;
import tw.nekomimi.nekogram.config.cell.ConfigCellNumberPicker;
import tw.nekomimi.nekogram.config.cell.ConfigCellText;
import tw.nekomimi.nekogram.config.cell.ConfigCellTextCheck;
import tw.nekomimi.nekogram.ui.HiddenAccountsPasscodeActivity;
import tw.nekomimi.nekogram.utils.AlertUtil;
import tw.nekomimi.nekogram.helpers.HiddenAccountsController;
import tw.nekomimi.nekogram.helpers.SettingsBackupHelper;
import xyz.nextalone.nagram.NaConfig;

@SuppressLint("RtlHardcoded")
@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class AccountsSettingsActivity extends BaseNekoXSettingsActivity {

    private static final int REQUEST_CODE_RESTORE_ACCOUNT_FILE = 1025;
    private static final int REQUEST_CODE_APPEND_ACCOUNT_FILE = 1026;

    private ListAdapter listAdapter;

    private final CellGroup cellGroup = new CellGroup(this);

    // Account Limits section
    private final AbstractConfigCell headerLimits = cellGroup.appendCell(
            new ConfigCellHeader(getString(R.string.AccountLimitsHeader)));

    private final AbstractConfigCell maxAccountCountRow = cellGroup.appendCell(
            new ConfigCellNumberPicker("MaxAccountCount",
                    NaConfig.INSTANCE.getMaxAccountCount(), 1, 100));

    private final AbstractConfigCell maxActiveAccountsRow = cellGroup.appendCell(
            new ConfigCellNumberPicker("MaxActiveAccounts",
                    NaConfig.INSTANCE.getMaxActiveAccounts(), 1, 100));

    private final AbstractConfigCell headerRows = cellGroup.appendCell(
            new ConfigCellHeader(getString(R.string.AccountRowsHeader)));

    private final AbstractConfigCell showLastSeenOnAccountRowsRow = cellGroup.appendCell(
            new ConfigCellTextCheck(
                    NaConfig.INSTANCE.getShowLastSeenOnAccountRows(),
                    getString(R.string.ShowLastSeenOnAccountRowsDesc),
                    getString(R.string.ShowLastSeenOnAccountRows)));

    private final AbstractConfigCell showAccountNumbersRow = cellGroup.appendCell(
            new ConfigCellTextCheck(NaConfig.INSTANCE.getShowAccountNumbers(), null, getString(R.string.ShowAccountNumbers)));
    private final AbstractConfigCell autoCollapseAccountTabsRow = cellGroup.appendCell(
            new ConfigCellTextCheck(NaConfig.INSTANCE.getAutoCollapseAccountTabs(), null, getString(R.string.AutoCollapseAccountTabs)));

    private final AbstractConfigCell dividerLimits = cellGroup.appendCell(new ConfigCellDivider());

    // Startup Performance section
    private final AbstractConfigCell headerStartup = cellGroup.appendCell(
            new ConfigCellHeader(getString(R.string.StartupPerformanceHeader)));

    private final AbstractConfigCell startupActiveAccountsRow = cellGroup.appendCell(
            new ConfigCellNumberPicker("StartupActiveAccounts",
                    NaConfig.INSTANCE.getStartupActiveAccounts(), 1, 100));

    private final AbstractConfigCell dividerStartup = cellGroup.appendCell(new ConfigCellDivider());

    private final AbstractConfigCell headerBackup = cellGroup.appendCell(
            new ConfigCellHeader(getString(R.string.AccountBackupHeader)));

    private final AbstractConfigCell backupCurrentAccountRow = cellGroup.appendCell(
            new ConfigCellText("BackupCurrentAccount", this::backupCurrentAccount));

    private final AbstractConfigCell backupCurrentAccountZipRow = cellGroup.appendCell(
            new ConfigCellText("BackupCurrentAccountZip", this::backupCurrentAccountZip));

    private final AbstractConfigCell backupCurrentAccountEncryptedZipRow = cellGroup.appendCell(
            new ConfigCellText("BackupCurrentAccountEncryptedZip", this::backupCurrentAccountEncryptedZip));

    private final AbstractConfigCell backupAllAccountsZipRow = cellGroup.appendCell(
            new ConfigCellText("BackupAllAccountsZip", this::backupAllAccountsZip));

    private final AbstractConfigCell backupAllAccountsEncryptedZipRow = cellGroup.appendCell(
            new ConfigCellText("BackupAllAccountsEncryptedZip", this::backupAllAccountsEncryptedZip));

    private final AbstractConfigCell appendCurrentAccountToZipRow = cellGroup.appendCell(
            new ConfigCellText("AppendCurrentAccountToZip", this::appendCurrentAccountToZip));

    private final AbstractConfigCell restoreAccountRow = cellGroup.appendCell(
            new ConfigCellText("RestoreAccountFromBackupFile", this::restoreAccountFromFile));

    private final AbstractConfigCell dividerBackup = cellGroup.appendCell(new ConfigCellDivider());

    // [Alexgram: Hidden Accounts] - Start
    // Hidden Accounts section
    private final AbstractConfigCell headerHidden = cellGroup.appendCell(
            new ConfigCellHeader(getString(R.string.HiddenAccountsHeader)));

    private final AbstractConfigCell hiddenAccountsRow = cellGroup.appendCell(
            new ConfigCellText("HiddenAccountsTitle", () -> {
                // Resolved at click time inside onClick, but we need the fragment reference
            }));

    private final AbstractConfigCell dividerHidden = cellGroup.appendCell(new ConfigCellDivider());
    // [Alexgram: Hidden Accounts] - End

    @Override
    protected RecyclerListView.SelectionAdapter getListAdapter() {
        return listAdapter;
    }

    @Override
    protected CellGroup getCellGroup() {
        return cellGroup;
    }

    @Override
    protected String getSettingsPrefix() {
        return "accounts";
    }

    @Override
    public String getTitle() {
        return getString(R.string.AccountsSettings);
    }

    @Override
    public View createView(Context context) {
        View superView = super.createView(context);

        listAdapter = new ListAdapter(context);
        listView.setAdapter(listAdapter);
        listView.invalidateItemDecorations();

        setupDefaultListeners();
        addRowsToMap(cellGroup);

        cellGroup.callBackSettingsChanged = (key, newValue) -> {
            if (key.equals(NaConfig.INSTANCE.getMaxAccountCount().getKey())
                    || key.equals(NaConfig.INSTANCE.getMaxActiveAccounts().getKey())
                    || key.equals(NaConfig.INSTANCE.getStartupActiveAccounts().getKey())) {
                tooltip.showWithAction(0, UndoView.ACTION_NEED_RESTART, null, null);
            }
        };

        return superView;
    }

    private void backupCurrentAccount() {
        try {
            if (getParentActivity() == null) {
                return;
            }
            int account = org.telegram.messenger.UserConfig.selectedAccount;
            File backupFile = SettingsBackupHelper.backupUserConfig(getParentActivity(), account);
            if (backupFile != null) {
                tw.nekomimi.nekogram.utils.ShareUtil.shareFile(getParentActivity(), backupFile);
            }
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(getParentActivity(), e);
        }
    }

    private void backupCurrentAccountZip() {
        backupAccountZip(false);
    }

    private void backupCurrentAccountEncryptedZip() {
        backupAccountZip(true);
    }

    private void backupAllAccountsZip() {
        backupAllAccounts(false);
    }

    private void backupAllAccountsEncryptedZip() {
        backupAllAccounts(true);
    }

    private interface AsyncAction<T> {
        T run(SettingsBackupHelper.ProgressListener listener) throws Exception;
    }

    private <T> void runAsyncBackupTask(String initialMessage, AsyncAction<T> action, Consumer<T> onSuccess) {
        if (getParentActivity() == null) {
            return;
        }
        org.telegram.ui.ActionBar.AlertDialog progressDialog = new org.telegram.ui.ActionBar.AlertDialog(getParentActivity(), 3);
        progressDialog.setMessage(initialMessage + " (0%)");
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();

        org.telegram.messenger.Utilities.globalQueue.postRunnable(() -> {
            try {
                T result = action.run((percent, statusText) -> AndroidUtilities.runOnUIThread(() -> {
                    if (progressDialog.isShowing()) {
                        progressDialog.setMessage(statusText + " (" + percent + "%)");
                    }
                }));
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                    } catch (Exception ignore) {
                    }
                    if (onSuccess != null && getParentActivity() != null) {
                        onSuccess.accept(result);
                    }
                });
            } catch (Exception e) {
                org.telegram.messenger.FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        if (progressDialog.isShowing()) {
                            progressDialog.dismiss();
                        }
                    } catch (Exception ignore) {
                    }
                    if (getParentActivity() != null) {
                        if (e instanceof SettingsBackupHelper.BackupPasswordRequiredException) {
                            promptPassword(getString(R.string.AccountDecryptPasswordTitle), getString(R.string.AccountBackupPasswordHint), pwd -> {
                                if (pwd == null || pwd.isEmpty()) {
                                    AlertUtil.showSimpleAlert(getParentActivity(), new IllegalArgumentException(getString(R.string.AccountBackupPasswordRequired)));
                                    return;
                                }
                                runAsyncBackupTask(initialMessage, listener -> action.run(listener), onSuccess);
                            });
                        } else if (e instanceof SettingsBackupHelper.BackupPasswordInvalidException) {
                            AlertUtil.showSimpleAlert(getParentActivity(), new IllegalArgumentException(getString(R.string.AccountBackupPasswordInvalid)));
                        } else {
                            AlertUtil.showSimpleAlert(getParentActivity(), e);
                        }
                    }
                });
            }
        });
    }

    private void backupAllAccounts(boolean encrypted) {
        if (getParentActivity() == null) {
            return;
        }
        if (encrypted) {
            promptPassword(getString(R.string.AccountBackupPasswordTitle), getString(R.string.AccountBackupPasswordHint), password -> {
                if (password == null || password.isEmpty()) {
                    AlertUtil.showSimpleAlert(getParentActivity(), new IllegalArgumentException(getString(R.string.AccountBackupPasswordRequired)));
                    return;
                }
                runAsyncBackupTask("Backing up all accounts...", listener -> SettingsBackupHelper.backupAllAccountsZip(getParentActivity(), password, listener), backupFile -> {
                    if (backupFile != null) {
                        tw.nekomimi.nekogram.utils.ShareUtil.shareFile(getParentActivity(), backupFile);
                    }
                });
            });
        } else {
            runAsyncBackupTask("Backing up all accounts...", listener -> SettingsBackupHelper.backupAllAccountsZip(getParentActivity(), null, listener), backupFile -> {
                if (backupFile != null) {
                    tw.nekomimi.nekogram.utils.ShareUtil.shareFile(getParentActivity(), backupFile);
                }
            });
        }
    }

    private void backupAccountZip(boolean encrypted) {
        if (getParentActivity() == null) {
            return;
        }
        int account = org.telegram.messenger.UserConfig.selectedAccount;
        if (encrypted) {
            promptPassword(getString(R.string.AccountBackupPasswordTitle), getString(R.string.AccountBackupPasswordHint), password -> {
                if (password == null || password.isEmpty()) {
                    AlertUtil.showSimpleAlert(getParentActivity(), new IllegalArgumentException(getString(R.string.AccountBackupPasswordRequired)));
                    return;
                }
                runAsyncBackupTask("Backing up account...", listener -> SettingsBackupHelper.backupUserConfigZip(getParentActivity(), account, password, listener), backupFile -> {
                    if (backupFile != null) {
                        tw.nekomimi.nekogram.utils.ShareUtil.shareFile(getParentActivity(), backupFile);
                    }
                });
            });
        } else {
            runAsyncBackupTask("Backing up account...", listener -> SettingsBackupHelper.backupUserConfigZip(getParentActivity(), account, null, listener), backupFile -> {
                if (backupFile != null) {
                    tw.nekomimi.nekogram.utils.ShareUtil.shareFile(getParentActivity(), backupFile);
                }
            });
        }
    }

    private void appendCurrentAccountToZip() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/zip");
            startActivityForResult(intent, REQUEST_CODE_APPEND_ACCOUNT_FILE);
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(getParentActivity(), e);
        }
    }

    private void restoreAccountFromFile() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "application/zip"});
            startActivityForResult(intent, REQUEST_CODE_RESTORE_ACCOUNT_FILE);
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(getParentActivity(), e);
        }
    }

    private void promptPassword(String title, String message, Consumer<String> callback) {
        if (getParentActivity() == null) {
            return;
        }
        LinearLayout layout = new LinearLayout(getParentActivity());
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = AndroidUtilities.dp(14);
        layout.setPadding(padding, padding, padding, padding);

        EditText passwordEdit = new EditText(getParentActivity());
        passwordEdit.setHint(getString(R.string.AccountBackupPasswordHint));
        passwordEdit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        passwordEdit.setTransformationMethod(PasswordTransformationMethod.getInstance());
        layout.addView(passwordEdit, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(getParentActivity())
                .setTitle(title)
                .setMessage(message)
                .setView(layout)
                .setPositiveButton(getString(R.string.OK), (d, which) -> callback.accept(passwordEdit.getText().toString()))
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        dialog.show();
    }

    private void attemptImportAccountBackup(Uri uri, String password) {
        if (getParentActivity() == null) {
            return;
        }
        runAsyncBackupTask("Restoring account backup...", listener -> SettingsBackupHelper.importUserConfig(getParentActivity(), uri, password, listener), count -> {
            if (count != null && count >= 1) {
                String successMsg = (count == 1)
                        ? getParentActivity().getString(R.string.AccountRestoreSuccess, 1)
                        : getParentActivity().getString(R.string.AccountRestoreCountSuccess, count);
                org.telegram.ui.ActionBar.AlertDialog restartDialog = new org.telegram.ui.ActionBar.AlertDialog(getParentActivity(), 0);
                restartDialog.setTitle(getString(R.string.NagramX));
                restartDialog.setMessage(successMsg + "\n\n" + getString(R.string.RestartAppToTakeEffect));
                restartDialog.setPositiveButton(getString(R.string.OK), (__, ___) -> tw.nekomimi.nekogram.helpers.AppRestartHelper.triggerRebirth(getParentActivity(), new Intent(getParentActivity(), org.telegram.ui.LaunchActivity.class)));
                restartDialog.show();
            }
        });
    }

    private void attemptAppendAccountBackup(Uri uri, String password) {
        if (getParentActivity() == null) {
            return;
        }
        int account = org.telegram.messenger.UserConfig.selectedAccount;
        runAsyncBackupTask("Appending account to backup...", listener -> SettingsBackupHelper.appendUserConfigToZip(getParentActivity(), account, uri, password, listener), backupFile -> {
            if (backupFile != null) {
                tw.nekomimi.nekogram.utils.ShareUtil.shareFile(getParentActivity(), backupFile);
            }
        });
    }

    @Override
    public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_CODE_RESTORE_ACCOUNT_FILE || requestCode == REQUEST_CODE_APPEND_ACCOUNT_FILE)
                && resultCode == Activity.RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (getParentActivity() != null) {
                try {
                    getParentActivity().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (Exception ignore) {
                }
            }
            if (requestCode == REQUEST_CODE_RESTORE_ACCOUNT_FILE) {
                attemptImportAccountBackup(uri, null);
            } else {
                attemptAppendAccountBackup(uri, null);
            }
        }
        super.onActivityResultFragment(requestCode, resultCode, data);
    }

    // [Alexgram: Hidden Accounts] - Start
    @Override
    protected void handleCellClick(View view, int position, float x, float y) {
        // Intercept Hidden Accounts row click before base class handles it
        if (position == cellGroup.rows.indexOf(hiddenAccountsRow)) {
            HiddenAccountsController ctrl = HiddenAccountsController.getInstance();
            if (ctrl.hasPin()) {
                presentFragment(new HiddenAccountsPasscodeActivity(
                        HiddenAccountsPasscodeActivity.MODE_UNLOCK_SETTINGS));
            } else {
                presentFragment(new HiddenAccountsPasscodeActivity(
                        HiddenAccountsPasscodeActivity.MODE_SETUP_PIN));
            }
            return;
        }
        super.handleCellClick(view, position, x, y);
    }
    // [Alexgram: Hidden Accounts] - End

    private class ListAdapter extends BaseListAdapter {
        public ListAdapter(Context context) {
            super(context);
        }
    }
}
// [Alexgram: Accounts Settings] - End