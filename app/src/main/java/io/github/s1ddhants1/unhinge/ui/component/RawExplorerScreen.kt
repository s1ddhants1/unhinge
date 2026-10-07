package io.github.s1ddhants1.unhinge.ui.component

import io.github.s1ddhants1.unhinge.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.model.DatabaseTableSummary
import io.github.s1ddhants1.unhinge.model.RawHingeTelemetry
import io.github.s1ddhants1.unhinge.model.RawPrefFile
import io.github.s1ddhants1.unhinge.model.StoragePrefEntry
import io.github.s1ddhants1.unhinge.ui.theme.AccentGold
import io.github.s1ddhants1.unhinge.ui.theme.AccentSuccess
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun RawExplorerScreen(
    prefFiles: List<RawPrefFile>,
    dbTables: List<DatabaseTableSummary>,
    telemetry: RawHingeTelemetry,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var explorerMode by rememberSaveable { mutableStateOf("Preferences") }
    var selectedFile by rememberSaveable { mutableStateOf(prefFiles.firstOrNull()?.fileName ?: "default.xml") }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    fun copy(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, context.getString(R.string.toast_copied_format, label), Toast.LENGTH_SHORT).show()
    }

    val currentPrefFile = remember(prefFiles, selectedFile) {
        prefFiles.find { it.fileName == selectedFile } ?: prefFiles.firstOrNull()
    }

    val filteredEntries = remember(currentPrefFile, searchQuery) {
        currentPrefFile?.entries?.filter { e ->
            searchQuery.isBlank() ||
                    e.key.contains(searchQuery, ignoreCase = true) ||
                    e.value.contains(searchQuery, ignoreCase = true)
        } ?: emptyList()
    }

    val filteredTables = remember(dbTables, searchQuery) {
        if (searchQuery.isBlank()) dbTables
        else dbTables.filter { it.tableName.contains(searchQuery, ignoreCase = true) }
    }

    val filteredPermissions = remember(telemetry.userPermissions, searchQuery) {
        if (searchQuery.isBlank()) telemetry.userPermissions
        else telemetry.userPermissions.filter { it.contains(searchQuery, ignoreCase = true) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        PrimaryTabRow(
            selectedTabIndex = when (explorerMode) {
                "Preferences" -> 0
                "Database" -> 1
                else -> 2
            },
            modifier = Modifier
                .fillMaxWidth()
                .hairlineBorder(RoundedCornerShape(16.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(
                        when (explorerMode) {
                            "Preferences" -> 0
                            "Database" -> 1
                            else -> 2
                        }
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    width = 48.dp
                )
            }
        ) {
            Tab(
                selected = explorerMode == "Preferences",
                onClick = {
                    explorerMode = "Preferences"
                },
                text = {
                    Text(
                        text = stringResource(R.string.explorer_tab_prefs, prefFiles.size),
                        fontWeight = if (explorerMode == "Preferences") FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = explorerMode == "Database",
                onClick = {
                    explorerMode = "Database"
                },
                text = {
                    Text(
                        text = stringResource(R.string.explorer_tab_sqlite, dbTables.size),
                        fontWeight = if (explorerMode == "Database") FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
            Tab(
                selected = explorerMode == "Flags",
                onClick = {
                    explorerMode = "Flags"
                },
                text = {
                    Text(
                        text = stringResource(R.string.explorer_tab_flags, telemetry.userPermissions.size),
                        fontWeight = if (explorerMode == "Flags") FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }

        UnhingeSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(
                when (explorerMode) {
                    "Preferences" -> R.string.explorer_search_keys
                    "Database" -> R.string.explorer_search_tables
                    else -> R.string.explorer_search_flags
                }
            ),
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedContent(
            targetState = explorerMode,
            label = "ExplorerModeTransition"
        ) { mode ->
            when (mode) {
                "Preferences" -> {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {

                    if (prefFiles.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(prefFiles, key = { it.fileName }) { file ->
                                val isSelected = file.fileName == currentPrefFile?.fileName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedFile = file.fileName
                                    },
                                    label = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(file.fileName)
                                            Surface(
                                                shape = CircleShape,
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                                            ) {
                                                Text(
                                                    text = "${file.entries.size}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    if (filteredEntries.isEmpty()) {
                        EmptyStateView(
                            icon = Icons.Default.SearchOff,
                            title = stringResource(R.string.explorer_empty_prefs_title),
                            description = if (searchQuery.isNotBlank()) stringResource(R.string.explorer_empty_search_prefs, searchQuery) else stringResource(R.string.explorer_empty_prefs_none),
                            actionLabel = if (searchQuery.isNotBlank()) stringResource(R.string.explorer_clear_filter) else null,
                            onAction = { searchQuery = "" }
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = filteredEntries,
                                key = { "${it.file}_${it.key}" }
                            ) { entry ->
                                PrefEntryCard(
                                    entry = entry,
                                    onCopyKey = { copy("Key", entry.key) },
                                    onCopyValue = { copy("Value", entry.value) }
                                )
                            }
                            item { Spacer(modifier = Modifier.height(24.dp)) }
                        }
                    }
                }
                }
                "Database" -> {

                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (filteredTables.isEmpty()) {
                            EmptyStateView(
                                icon = Icons.Default.Storage,
                                title = stringResource(R.string.explorer_empty_tables_title),
                                description = if (searchQuery.isNotBlank()) stringResource(R.string.explorer_empty_search_tables, searchQuery) else stringResource(R.string.explorer_empty_tables_none),
                                actionLabel = if (searchQuery.isNotBlank()) stringResource(R.string.explorer_clear_filter) else null,
                                onAction = { searchQuery = "" }
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(
                                    items = filteredTables,
                                    key = { it.tableName }
                                ) { table ->
                                    DbTableCard(
                                        table = table,
                                        onCopyName = { copy("Table Name", table.tableName) }
                                    )
                                }
                                item { Spacer(modifier = Modifier.height(24.dp)) }
                            }
                        }
                    }
                }
                else -> {

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        item(key = "server_tokens_header") {
                            UnhingeDoubleBezelCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ShapeTokens.Card,
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.VpnKey,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.explorer_section_ids),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    TokenRow(stringResource(R.string.explorer_server_install_id), telemetry.installId, onCopy = { copy(context.getString(R.string.explorer_server_install_id), telemetry.installId) })
                                    TokenRow(stringResource(R.string.explorer_server_discover_etag), telemetry.discoverCacheEtag, onCopy = { copy(context.getString(R.string.explorer_server_discover_etag), telemetry.discoverCacheEtag) })
                                    TokenRow(stringResource(R.string.explorer_server_first_sync), formatDate(telemetry.firstHingeSync))
                                    TokenRow(stringResource(R.string.explorer_server_last_changed), formatDate(telemetry.userProfileLastChanged))
                                }
                            }
                        }

                        item(key = "permissions_summary_header") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.explorer_section_flags),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    UnhingeBadge(
                                        label = stringResource(R.string.explorer_active_format, telemetry.userPermissions.size),
                                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (telemetry.userPermissions.isNotEmpty()) {
                                    IconButton(
                                        onClick = { copy("All Flags", telemetry.userPermissions.joinToString("\n")) },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.cd_copy_all_flags),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (filteredPermissions.isEmpty()) {
                            item(key = "empty_permissions") {
                                EmptyStateView(
                                    icon = Icons.Default.SearchOff,
                                    title = stringResource(R.string.explorer_empty_flags_title),
                                    description = if (searchQuery.isNotBlank()) stringResource(R.string.explorer_empty_search_flags, searchQuery) else stringResource(R.string.explorer_empty_flags_none),
                                    actionLabel = if (searchQuery.isNotBlank()) stringResource(R.string.explorer_clear_filter) else null,
                                    onAction = { searchQuery = "" }
                                )
                            }
                        } else {
                            items(
                                items = filteredPermissions,
                                key = { it }
                            ) { permission ->
                                PermissionItem(
                                    permission = permission,
                                    onCopy = { copy("Flag", permission) }
                                )
                            }
                        }

                        item(key = "bottom_flags_spacer") {
                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefEntryCard(
    entry: StoragePrefEntry,
    onCopyKey: () -> Unit,
    onCopyValue: () -> Unit
) {
    UnhingeDoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeTokens.Card,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val (typeColor, typeBg) = when (entry.type.lowercase()) {
                        "boolean" -> Pair(AccentSuccess, AccentSuccess.copy(alpha = 0.15f))
                        "long", "int", "integer" -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.15f))
                        "set", "json" -> Pair(AccentGold, AccentGold.copy(alpha = 0.15f))
                        else -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    }

                    UnhingeBadge(
                        label = entry.type.lowercase().replaceFirstChar { it.titlecase() },
                        containerColor = typeBg,
                        contentColor = typeColor
                    )

                    Text(
                        text = entry.key,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(onClick = onCopyKey, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_key),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Surface(
                shape = ShapeTokens.Pill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .hairlineBorder(ShapeTokens.Pill)
                    .bouncyClickable(onClick = onCopyValue)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = entry.value.ifBlank { "(empty)" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_value),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(start = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DbTableCard(
    table: DatabaseTableSummary,
    onCopyName: () -> Unit
) {
    UnhingeDoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeTokens.Card,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(14.dp),
        onClick = onCopyName
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = ShapeTokens.Pill,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.TableChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = table.tableName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stringResource(R.string.explorer_table_title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            UnhingeBadge(
                label = stringResource(R.string.explorer_rows_format, table.rowCount),
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun TokenRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)? = null
) {
    Surface(
        shape = ShapeTokens.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .hairlineBorder(ShapeTokens.Pill)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value.ifBlank { "Not set" },
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onCopy != null && value.isNotBlank()) {
                IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.cd_copy_label_format, label),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionItem(
    permission: String,
    onCopy: () -> Unit
) {
    UnhingeDoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeTokens.CardNested,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        onClick = onCopy
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = ShapeTokens.Badge,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Text(
                    text = permission,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                Icons.Default.ContentCopy,
                contentDescription = stringResource(R.string.cd_copy),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "N/A"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        "N/A"
    }
}
