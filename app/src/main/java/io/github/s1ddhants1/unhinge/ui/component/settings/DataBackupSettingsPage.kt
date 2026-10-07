package io.github.s1ddhants1.unhinge.ui.component.settings

import io.github.s1ddhants1.unhinge.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.data.BackupRestoreManager
import io.github.s1ddhants1.unhinge.data.CandidateArchiveDb
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ExportTarget {
    FULL,
    SETTINGS_ONLY,
    CANDIDATES_ONLY
}

@Composable
fun DataBackupSettingsPage(
    prefs: PreferencesManager,
    onDataRestored: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val archiveDb = remember { CandidateArchiveDb(context) }
    var archiveCount by remember { mutableIntStateOf(archiveDb.getArchiveCount()) }
    var showClearDialog by remember { mutableStateOf(false) }
    var currentExportTarget by remember { mutableStateOf(ExportTarget.FULL) }

    fun formattedTimestamp(): String {
        return SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val jsonString = withContext(Dispatchers.IO) {
                    when (currentExportTarget) {
                        ExportTarget.FULL -> BackupRestoreManager.exportToJson(
                            prefs = prefs,
                            candidateArchiveDb = archiveDb,
                            includeSettings = true,
                            includeCandidates = true
                        )
                        ExportTarget.SETTINGS_ONLY -> BackupRestoreManager.exportToJson(
                            prefs = prefs,
                            candidateArchiveDb = archiveDb,
                            includeSettings = true,
                            includeCandidates = false
                        )
                        ExportTarget.CANDIDATES_ONLY -> BackupRestoreManager.exportToJson(
                            prefs = prefs,
                            candidateArchiveDb = archiveDb,
                            includeSettings = false,
                            includeCandidates = true
                        )
                    }
                }
                val success = withContext(Dispatchers.IO) {
                    BackupRestoreManager.writeToUri(context, uri, jsonString)
                }
                if (success) {
                    Toast.makeText(context, context.getString(R.string.backup_export_ok), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, context.getString(R.string.backup_export_fail), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val jsonString = withContext(Dispatchers.IO) {
                    BackupRestoreManager.readFromUri(context, uri)
                }
                if (jsonString.isNullOrBlank()) {
                    Toast.makeText(context, context.getString(R.string.backup_read_fail), Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val result = withContext(Dispatchers.IO) {
                    BackupRestoreManager.importFromJson(context, jsonString, prefs, archiveDb)
                }
                if (result.success) {
                    archiveCount = withContext(Dispatchers.IO) { archiveDb.getArchiveCount() }
                    onDataRestored()
                    val msg = buildString {
                        append(context.getString(R.string.backup_restore_prefix))
                        if (result.settingsRestored) append(context.getString(R.string.backup_restore_settings))
                        if (result.candidatesImported > 0) append(context.resources.getQuantityString(R.plurals.backup_restore_candidates, result.candidatesImported, result.candidatesImported))
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                } else {
                    val errorMsg = result.errorMessage ?: context.getString(R.string.backup_import_fail)
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.backup_clear_title)) },
            text = { Text(pluralStringResource(R.plurals.backup_clear_archive_message, archiveCount, archiveCount)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                archiveDb.clearArchive()
                            }
                            archiveCount = 0
                            onDataRestored()
                            Toast.makeText(context, context.getString(R.string.backup_cleared), Toast.LENGTH_SHORT).show()
                            showClearDialog = false
                        }
                    }
                ) {
                    Text(stringResource(R.string.action_clear_archive), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SettingsSectionCard(title = stringResource(R.string.backup_section_export)) {
            SettingsActionRow(
                title = stringResource(R.string.backup_export_full),
                icon = Icons.Default.Backup,
                onClick = {
                    currentExportTarget = ExportTarget.FULL
                    exportLauncher.launch("unhinge_backup_${formattedTimestamp()}.json")
                }
            )
            SettingsDivider()

            SettingsActionRow(
                title = stringResource(R.string.backup_export_settings),
                icon = Icons.Default.Tune,
                onClick = {
                    currentExportTarget = ExportTarget.SETTINGS_ONLY
                    exportLauncher.launch("unhinge_settings_${formattedTimestamp()}.json")
                }
            )
            SettingsDivider()

            SettingsActionRow(
                title = stringResource(R.string.backup_export_candidates),
                icon = Icons.Default.People,
                onClick = {
                    currentExportTarget = ExportTarget.CANDIDATES_ONLY
                    exportLauncher.launch("unhinge_candidates_${formattedTimestamp()}.json")
                }
            )
        }

        SettingsSectionCard(title = stringResource(R.string.backup_section_import)) {
            SettingsActionRow(
                title = stringResource(R.string.backup_import_file),
                icon = Icons.Default.FileDownload,
                onClick = {
                    importLauncher.launch(arrayOf("application/json", "*/*"))
                }
            )
        }

        SettingsSectionCard(title = stringResource(R.string.backup_section_storage)) {
            SettingsActionRow(
                title = stringResource(R.string.backup_archived_profiles),
                icon = Icons.Default.Storage,
                trailingContent = {
                    Text(
                        text = stringResource(R.string.backup_archived_saved_format, archiveCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                onClick = {}
            )
            SettingsDivider()

            SettingsActionRow(
                title = stringResource(R.string.backup_clear_row),
                icon = Icons.Default.DeleteOutline,
                contentColor = MaterialTheme.colorScheme.error,
                enabled = archiveCount > 0,
                onClick = { showClearDialog = true }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
