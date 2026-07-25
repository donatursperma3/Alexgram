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
                try {
                    File backupFile = SettingsBackupHelper.backupUserConfigZip(getParentActivity(), account, password);
                    tw.nekomimi.nekogram.utils.ShareUtil.shareFile(getParentActivity(), backupFile);
                } catch (Exception e) {
                    AlertUtil.showSimpleAlert(getParentActivity(), e);
                }
            });
        } else {
            try {
                File backupFile = SettingsBackupHelper.backupUserConfigZip(getParentActivity(), account, null);
                tw.nekomimi.nekogram.utils.ShareUtil.shareFile(getParentActivity(), backupFile);
            } catch (Exception e) {
                AlertUtil.showSimpleAlert(getParentActivity(), e);
            }
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
        try {
            int count = SettingsBackupHelper.importUserConfig(getParentActivity(), uri, password);
            if (count == 1) {
                AlertUtil.showSimpleAlert(getParentActivity(), getString(R.string.AccountRestoreSuccess, 1));
            } else {
                AlertUtil.showSimpleAlert(getParentActivity(), getString(R.string.AccountRestoreCountSuccess, count));
            }
        } catch (SettingsBackupHelper.BackupPasswordRequiredException e) {
            promptPassword(getString(R.string.AccountDecryptPasswordTitle), getString(R.string.AccountBackupPasswordHint), pwd -> attemptImportAccountBackup(uri, pwd));
        } catch (SettingsBackupHelper.BackupPasswordInvalidException e) {
            AlertUtil.showSimpleAlert(getParentActivity(), new IllegalArgumentException(getString(R.string.AccountBackupPasswordInvalid)));
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(getParentActivity(), e);
        }
    }

    private void attemptAppendAccountBackup(Uri uri, String password) {
        if (getParentActivity() == null) {
            return;
        }
        try {
            int account = org.telegram.messenger.UserConfig.selectedAccount;
            File backupFile = SettingsBackupHelper.appendUserConfigToZip(getParentActivity(), account, uri, password);
            tw.nekomimi.nekogram.utils.ShareUtil.shareFile(getParentActivity(), backupFile);
        } catch (SettingsBackupHelper.BackupPasswordRequiredException e) {
            promptPassword(getString(R.string.AccountDecryptPasswordTitle), getString(R.string.AccountBackupPasswordHint), pwd -> attemptAppendAccountBackup(uri, pwd));
        } catch (SettingsBackupHelper.BackupPasswordInvalidException e) {
            AlertUtil.showSimpleAlert(getParentActivity(), new IllegalArgumentException(getString(R.string.AccountBackupPasswordInvalid)));
        } catch (Exception e) {
            AlertUtil.showSimpleAlert(getParentActivity(), e);
        }
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