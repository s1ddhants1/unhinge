package io.github.s1ddhants1.unhinge.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import io.github.s1ddhants1.unhinge.model.ChatMessageRecord
import io.github.s1ddhants1.unhinge.model.MatchRecord
import io.github.s1ddhants1.unhinge.ui.theme.AccentGold
import io.github.s1ddhants1.unhinge.ui.theme.AccentSuccess
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MatchesScreen(
    matches: List<MatchRecord>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf("All") }
    var activeChatSubjectId by rememberSaveable { mutableStateOf<String?>(null) }

    fun copy(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
    }

    // 1. Search filtering
    val searchMatchedMatches = remember(matches, searchQuery) {
        if (searchQuery.isBlank()) matches else {
            matches.filter { m -> matchesSearchQuery(m, searchQuery) }
        }
    }

    // 2. Dynamic facet counts based on current search matches
    val activeCount = remember(searchMatchedMatches) { searchMatchedMatches.count { !it.chatEnded && it.messageCount > 0 } }
    val yourTurnCount = remember(searchMatchedMatches) { searchMatchedMatches.count { !it.chatEnded && it.lastMessageSentBySubject && it.messageCount > 0 } }
    val waitingCount = remember(searchMatchedMatches) { searchMatchedMatches.count { !it.chatEnded && !it.lastMessageSentBySubject && it.messageCount > 0 } }
    val phoneCount = remember(searchMatchedMatches) { searchMatchedMatches.count { it.phoneNumberExchanged } }
    val endedCount = remember(searchMatchedMatches) { searchMatchedMatches.count { it.chatEnded } }

    // 3. Final filtered list matching the active filter chip
    val filtered = remember(searchMatchedMatches, selectedFilter) {
        searchMatchedMatches.filter { m ->
            when (selectedFilter) {
                "Active" -> !m.chatEnded && m.messageCount > 0
                "Your Turn" -> !m.chatEnded && m.lastMessageSentBySubject && m.messageCount > 0
                "Waiting" -> !m.chatEnded && !m.lastMessageSentBySubject && m.messageCount > 0
                "Phone Shared" -> m.phoneNumberExchanged
                "Social Shared" -> m.socialMediaExchanged
                "Priority Like" -> m.originatedFromPriorityLike
                "Ended" -> m.chatEnded
                else -> true
            }
        }
    }

    val activeChatMatch = remember(matches, activeChatSubjectId) {
        matches.firstOrNull { it.subjectId == activeChatSubjectId }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // High-level KPI Analytics Row
        MatchAnalyticsBento(matches = matches)

        // Search Bar with clear icon and IME action
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search matches, messages, jobs...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchQuery = ""
                        focusManager.clearFocus()
                    }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Search")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        )

        // Filter Chips (Horizontally Scrollable)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            fun toggleFilter(filterName: String) {
                selectedFilter = if (selectedFilter == filterName) "All" else filterName
            }

            FilterChip(
                selected = selectedFilter == "All",
                onClick = { toggleFilter("All") },
                label = { Text("All (${searchMatchedMatches.size})") },
                shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
                selected = selectedFilter == "Active",
                onClick = { toggleFilter("Active") },
                label = { Text("Active ($activeCount)") },
                shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
                selected = selectedFilter == "Your Turn",
                onClick = { toggleFilter("Your Turn") },
                label = { Text("Your Turn ($yourTurnCount)") },
                shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
                selected = selectedFilter == "Waiting",
                onClick = { toggleFilter("Waiting") },
                label = { Text("Waiting ($waitingCount)") },
                shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
                selected = selectedFilter == "Phone Shared",
                onClick = { toggleFilter("Phone Shared") },
                label = { Text("Phone ($phoneCount)") },
                shape = RoundedCornerShape(12.dp)
            )
            FilterChip(
                selected = selectedFilter == "Ended",
                onClick = { toggleFilter("Ended") },
                label = { Text("Ended ($endedCount)") },
                shape = RoundedCornerShape(12.dp)
            )
        }

        // Status / Reset Bar
        if (searchQuery.isNotBlank() || selectedFilter != "All") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Showing ${filtered.size} of ${matches.size} matches",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        searchQuery = ""
                        selectedFilter = "All"
                        focusManager.clearFocus()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset Filters", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        // Match Cards List
        if (filtered.isEmpty()) {
            EmptyStateView(
                icon = if (matches.isEmpty()) Icons.Default.ChatBubbleOutline else Icons.Default.FilterListOff,
                title = if (matches.isEmpty()) "No Matches Found" else "No Matching Conversations",
                description = if (matches.isEmpty())
                    "Matches and message transcripts from Hinge SQLite storage will appear here."
                else
                    "No matches match current filter or search.",
                actionLabel = if (searchQuery.isNotBlank() || selectedFilter != "All") "Clear Filters" else null,
                onAction = {
                    searchQuery = ""
                    selectedFilter = "All"
                    focusManager.clearFocus()
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(
                    items = filtered,
                    key = { m -> m.subjectId.ifBlank { "match_${m.hashCode()}" } }
                ) { match ->
                    MatchCard(
                        match = match,
                        onOpenChat = {
                            activeChatSubjectId = match.subjectId
                        },
                        onCopyId = { copy("User ID", match.subjectId) }
                    )
                }
            }
        }
    }

    // Full Chat Transcript Dialog
    if (activeChatMatch != null) {
        ChatTranscriptDialog(
            match = activeChatMatch,
            onDismiss = { activeChatSubjectId = null },
            onCopy = { label, text -> copy(label, text) }
        )
    }
}

@Composable
private fun MatchAnalyticsBento(matches: List<MatchRecord>) {
    val total = matches.size
    val active = matches.count { !it.chatEnded && it.messageCount > 0 }
    val contactsExchanged = matches.count { it.phoneNumberExchanged || it.socialMediaExchanged }
    val priorityLikes = matches.count { it.originatedFromPriorityLike }

    UnhingeDoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            KpiItem(title = "Matches", value = "$total", icon = Icons.Default.People)
            KpiItem(title = "Active Chats", value = "$active", icon = Icons.Default.ChatBubble)
            KpiItem(title = "Contacts", value = "$contactsExchanged", icon = Icons.Default.Phone)
            KpiItem(title = "Priority", value = "$priorityLikes", icon = Icons.Default.Star)
        }
    }
}

@Composable
private fun KpiItem(title: String, value: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            }
        }
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MatchCard(
    match: MatchRecord,
    onOpenChat: () -> Unit,
    onCopyId: () -> Unit
) {
    UnhingeDoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onOpenChat
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Photo avatar with online/turn status ring
                val isYourTurn = !match.chatEnded && match.lastMessageSentBySubject && match.messageCount > 0

                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(
                            if (isYourTurn) Modifier.hairlineBorder(CircleShape, AccentSuccess, width = 1.5.dp)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (match.photoUrl.isNotBlank()) {
                        SubcomposeAsyncImage(
                            model = match.photoUrl,
                            contentDescription = match.firstName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                }
                            },
                            error = {
                                Text(
                                    text = match.firstName.take(1).ifBlank { "?" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        )
                    } else {
                        Text(
                            text = match.firstName.take(1).ifBlank { "?" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Name, Age, Job, and Match Date
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = match.firstName.ifBlank { "Matched User" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (match.age > 0) {
                            Text(
                                text = "${match.age}",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (match.jobTitle.isNotBlank()) {
                        Text(
                            text = match.jobTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (match.reciprocatedTimestamp > 0L) {
                        val locale = LocalLocale.current.platformLocale
                        val matchDate = SimpleDateFormat("MMM d, yyyy", locale).format(Date(match.reciprocatedTimestamp))
                        Text(
                            text = "Matched $matchDate",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Chat Messages count badge
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (match.messageCount > 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = "${match.messageCount} msg${if (match.messageCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (match.messageCount > 0) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Intelligence Badges Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (match.phoneNumberExchanged) {
                    StatusTag(
                        label = "Phone Exchanged",
                        containerColor = AccentSuccess.copy(alpha = 0.18f),
                        contentColor = AccentSuccess,
                        onClick = {}
                    )
                }
                if (match.socialMediaExchanged) {
                    StatusTag(
                        label = "Social Exchanged",
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        contentColor = MaterialTheme.colorScheme.primary,
                        onClick = {}
                    )
                }
                if (match.originatedFromPriorityLike) {
                    StatusTag(
                        label = "Priority Like",
                        containerColor = AccentGold.copy(alpha = 0.2f),
                        contentColor = AccentGold,
                        onClick = {}
                    )
                }
                if (match.draftMessage.isNotBlank()) {
                    StatusTag(
                        label = "Draft Saved",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        onClick = {}
                    )
                }
                if (match.chatEnded) {
                    StatusTag(
                        label = if (match.chatEndedByMe) "Ended by you" else "Unmatched",
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                        contentColor = MaterialTheme.colorScheme.error,
                        onClick = {}
                    )
                }
                if (match.replyNudgeDays > 0) {
                    StatusTag(
                        label = "${match.replyNudgeDays}d since reply",
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {}
                    )
                }
            }

            // Last message snippet
            if (match.lastMessageText.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .hairlineBorder(RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (match.lastMessageSentBySubject) "${match.firstName}:" else "You:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = match.lastMessageText,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurface,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Bottom Actions: Copy User ID & Open Chat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ID: ${match.subjectId.take(12)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.clickable { onCopyId() }
                )
                TextButton(
                    onClick = onOpenChat,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("View Transcript", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ChatTranscriptDialog(
    match: MatchRecord,
    onDismiss: () -> Unit,
    onCopy: (String, String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = match.firstName.ifBlank { "Match" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${match.messages.size} total messages recorded",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // Messages list
                if (match.messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No recorded chat messages in local database",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(
                            items = match.messages,
                            key = { index, msg -> if (msg.localId.isNotBlank()) "${msg.localId}_$index" else "msg_$index" }
                        ) { _, msg ->
                            ChatBubble(
                                message = msg,
                                senderName = match.firstName,
                                onCopy = { onCopy("Message", msg.body) }
                            )
                        }
                    }
                }

                // Draft section if exists
                if (match.draftMessage.isNotBlank()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Unsent Saved Draft:",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = match.draftMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessageRecord,
    senderName: String,
    onCopy: () -> Unit
) {
    val isFromMe = !message.sentBySubject
    val bubbleColor = if (isFromMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val textColor = if (isFromMe) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val alignment = if (isFromMe) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromMe) 16.dp else 4.dp,
                bottomEnd = if (isFromMe) 4.dp else 16.dp
            ),
            color = bubbleColor,
            modifier = Modifier
                .widthIn(max = 290.dp)
                .bouncyClickable { onCopy() }
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (message.isVoiceNote) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Voice Note", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    if (message.voiceTranscript.isNotBlank()) {
                        Text(
                            text = "\"${message.voiceTranscript}\"",
                            style = MaterialTheme.typography.bodySmall,
                            color = textColor
                        )
                    }
                }

                if (message.body.isNotBlank()) {
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColor
                    )
                }

                // Time & reactions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.createdTimestamp > 0L) {
                        val locale = LocalLocale.current.platformLocale
                        val timeStr = SimpleDateFormat("MMM d, h:mm a", locale).format(Date(message.createdTimestamp))
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }
                    if (message.reactions.isNotBlank()) {
                        Text(
                            text = message.reactions,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

/**
 * Searches across match name, age, job title, and message transcripts.
 */
private fun matchesSearchQuery(match: MatchRecord, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true

    val normalized = normalizeSearchText(trimmed)
    val tokens = normalized.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true

    val sb = StringBuilder()
    sb.append(match.firstName).append(' ')
    sb.append(match.jobTitle).append(' ')
    if (match.age > 0) sb.append(match.age).append(' ')
    sb.append(match.lastMessageText).append(' ')
    sb.append(match.draftMessage).append(' ')
    for (m in match.messages) {
        sb.append(m.body).append(' ')
        if (m.voiceTranscript.isNotBlank()) sb.append(m.voiceTranscript).append(' ')
    }

    val corpus = normalizeSearchText(sb.toString())
    return tokens.all { token -> corpus.contains(token) }
}
