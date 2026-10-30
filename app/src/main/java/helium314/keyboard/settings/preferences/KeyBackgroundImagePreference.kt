// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun KeyBackgroundImagePreference(setting: Setting, fileSuffix: String = "") {
    var showSelectionDialog by rememberSaveable { mutableStateOf(false) }
    var showErrorDialog by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope { Dispatchers.IO }
    val imageFile = Settings.getCustomKeyBackgroundFile(context, fileSuffix)
    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType("image/*")
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
        showSelectionDialog = false
        scope.launch(Dispatchers.IO) {
            if (!copyKeyBackground(context, uri, imageFile)) {
                showErrorDialog = true
            } else {
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
            }
        }
    }
    Preference(
        name = setting.title,
        onClick = {
            if (imageFile.exists()) showSelectionDialog = true else launcher.launch(intent)
        }
    )
    if (showSelectionDialog) {
        ConfirmationDialog(
            onDismissRequest = { showSelectionDialog = false },
            title = { Text(stringResource(R.string.customize_key_background)) },
            confirmButtonText = stringResource(R.string.button_load_custom),
            onConfirmed = { launcher.launch(intent) },
            neutralButtonText = stringResource(R.string.delete),
            onNeutral = {
                imageFile.delete()
                KeyboardSwitcher.getInstance().setThemeNeedsReload()
                showSelectionDialog = false
            },
        )
    }
    if (showErrorDialog) {
        InfoDialog(stringResource(R.string.file_read_error)) { showErrorDialog = false }
    }
}

private fun copyKeyBackground(context: Context, uri: Uri, destination: File): Boolean {
    return try {
        FileUtils.copyContentUriToNewFile(uri, context, destination)
        if (BitmapFactory.decodeFile(destination.absolutePath) == null) {
            destination.delete()
            false
        } else true
    } catch (_: Exception) {
        destination.delete()
        false
    }
}
