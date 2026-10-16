package io.github.s1ddhants1.unhinge.hook.ui.sheet

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.ai.AiWingmanHelper
import io.github.s1ddhants1.unhinge.ai.PromptEntry
import io.github.s1ddhants1.unhinge.data.HostCandidateReader
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.ui.theme.HingeFonts
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.ThemeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Compact inline AI Opener Card styled directly after Hinge's native AI Prompt Feedback
 * and Analysis UI for own profile prompts.
 *
 * Renders inline over the host screen without modal dimming or fullscreen sheets.
 * Provides the generated opener, instant prompt cycling, one-tap copy, and prominent retry.
 */
@Composable
fun InlineAiOpenerCard(
    prefs: PreferencesManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    candidate: CachedCandidateProfile? = null,
    screenClues: HostCandidateReader.ScreenClues? = null,
    isChatMode: Boolean = false,
    hostActivity: Activity? = null,
    initialChatContext: HostCandidateReader.MatchChatContext? = null,
    onInsert: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isDark = when (prefs.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val cardBg = if (isDark) {
        if (prefs.pureBlack) Color.Black else HingeDesignTokens.DarkCard
    } else HingeDesignTokens.LightCard

    val cardBorder = if (isDark) HingeDesignTokens.DarkBorder else HingeDesignTokens.LightBorder
    val commentBg = if (isDark) HingeDesignTokens.DarkComment else HingeDesignTokens.LightComment
    val textPrimary = if (isDark) HingeDesignTokens.DarkTextPrimary else HingeDesignTokens.LightTextPrimary
    val textSecondary = if (isDark) HingeDesignTokens.DarkTextSecondary else HingeDesignTokens.LightTextSecondary
    val actionBtnBg = if (isDark) HingeDesignTokens.DarkPillSelected else HingeDesignTokens.LightPillSelected
    val actionBtnText = if (isDark) Color(0xFF161616) else Color(0xFFFFFFFF)
    val pillBg = if (isDark) HingeDesignTokens.DarkPillUnselected else HingeDesignTokens.LightPillUnselected
    val pillBorder = if (isDark) HingeDesignTokens.DarkPillBorder else HingeDesignTokens.LightPillBorder
    val sparkleColor = if (isDark) Color(0xFFB388FF) else Color(0xFF711328)

    val modernEraMedium = remember(context) { HingeFonts.modernEraMedium(context) }
    val modernEraBold = remember(context) { HingeFonts.modernEraBold(context) }

    var loadedCandidate by remember { mutableStateOf(candidate) }
    var loadedChatContext by remember { mutableStateOf(initialChatContext) }

    LaunchedEffect(candidate, screenClues, isChatMode) {
        if (isChatMode) {
            if (initialChatContext != null) {
                loadedChatContext = initialChatContext
            } else {
                val chat = HostCandidateReader.readCurrentMatchChat(context, screenClues)
                loadedChatContext = chat
            }
        } else {
            if (candidate != null) {
                loadedCandidate = candidate
            } else {
                val current = HostCandidateReader.readTargetCandidate(context, screenClues)
                    ?: HostCandidateReader.readActiveCandidates(context).firstOrNull()
                loadedCandidate = current
            }
        }
    }

    val activeCandidate = if (isChatMode) (loadedChatContext?.candidate ?: loadedCandidate) else loadedCandidate
    val chatContext = loadedChatContext
    val chatMessages = chatContext?.messages.orEmpty()
    val matchDisplayName = chatContext?.match?.firstName?.ifBlank { activeCandidate?.firstName.orEmpty() }
        ?: activeCandidate?.firstName.orEmpty()

    val chatArchetypes = remember {
        listOf(
            "Playful Tease / Banter",
            "Date Pitch / Move to Meet",
            "Witty Callback",
            "Casual / Low-Key",
        )
    }

    val promptEntries = remember(activeCandidate?.userId, activeCandidate?.prompts, isChatMode, chatContext?.match?.subjectId) {
        if (isChatMode) {
            chatArchetypes.map { PromptEntry(text = it) }
        } else {
            val prompts = activeCandidate?.prompts.orEmpty()
            if (prompts.isNotEmpty()) {
                prompts.map {
                    val cleanAnswer = it.answer.replace("\\n", "\n")
                    PromptEntry(text = "${it.question}: $cleanAnswer")
                }
            } else if (activeCandidate != null) {
                val bio = listOfNotNull(
                    activeCandidate.jobTitle.takeIf { it.isNotBlank() }?.let { "Job: $it" },
                    activeCandidate.location.takeIf { it.isNotBlank() }?.let { "Location: $it" },
                    activeCandidate.datingIntention.takeIf { it.isNotBlank() }?.let { "Looking for: $it" },
                ).joinToString(", ")
                if (bio.isNotBlank()) listOf(PromptEntry(text = bio)) else emptyList()
            } else {
                emptyList()
            }
        }
    }

    var selectedIndex by remember(activeCandidate?.userId, isChatMode) { mutableIntStateOf(0) }
    val safeIndex = selectedIndex.coerceIn(0, (promptEntries.size - 1).coerceAtLeast(0))
    val activeEntry = promptEntries.getOrNull(safeIndex)

    val activeReply by (activeEntry?.suggestedReplyFlow?.collectAsState() ?: remember { mutableStateOf(null) })
    val isEntryGenerating by (activeEntry?.isGeneratingFlow?.collectAsState() ?: remember { mutableStateOf(false) })
    val status by AiWingmanHelper.status.collectAsState()

    val isApiKeyRequired = AiWingmanHelper.isApiKeyRequired(prefs.aiProvider, prefs.openRouterModel)
    val isAiReady = !isApiKeyRequired || prefs.openRouterApiKey.isNotBlank()

    fun triggerGeneration(forceRefresh: Boolean = false) {
        if (!isAiReady || activeEntry == null) return
        if (isChatMode) {
            val profileBlock = activeCandidate?.let(::buildProfileBlock) ?: matchDisplayName
            AiWingmanHelper.generateChatReplies(
                matchName = matchDisplayName,
                profileBlock = profileBlock,
                conversationHistory = chatMessages,
                prompts = listOf(activeEntry),
                apiKey = prefs.openRouterApiKey,
                baseUrl = prefs.openRouterBaseUrl,
                model = prefs.openRouterModel,
                scope = coroutineScope,
                context = context,
                prefs = prefs,
                provider = prefs.aiProvider,
                useStreaming = true,
                forceRefresh = forceRefresh,
                temperature = prefs.aiTemperature,
                topP = prefs.aiTopP,
                reasoningEffort = prefs.aiReasoningEffort,
            )
        } else {
            AiWingmanHelper.generateReplies(
                prompts = listOf(activeEntry),
                apiKey = prefs.openRouterApiKey,
                baseUrl = prefs.openRouterBaseUrl,
                model = prefs.openRouterModel,
                scope = coroutineScope,
                context = context,
                prefs = prefs,
                provider = prefs.aiProvider,
                useStreaming = true,
                systemPrompt = prefs.aiCustomSystemPrompt,
                profileBlock = activeCandidate?.let(::buildProfileBlock).orEmpty(),
                forceRefresh = forceRefresh,
                temperature = prefs.aiTemperature,
                topP = prefs.aiTopP,
                reasoningEffort = prefs.aiReasoningEffort,
            )
        }
    }

    LaunchedEffect(activeEntry, isAiReady) {
        if (isAiReady && activeEntry != null && activeReply.isNullOrBlank() && !isEntryGenerating) {
            triggerGeneration(forceRefresh = false)
        }
    }

    var isCopied by remember { mutableStateOf(false) }

    fun copyOpener(text: String) {
        if (text.isBlank()) return
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Unhinge Opener", text))
        isCopied = true
        Toast.makeText(context, "Copied opener to clipboard", Toast.LENGTH_SHORT).show()
        coroutineScope.launch {
            delay(2000)
            isCopied = false
        }
    }

    // Active prompt label & answer snippet for context
    val activePromptItem = if (!isChatMode) activeCandidate?.prompts?.getOrNull(safeIndex) else null

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = 10.dp,
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Header: Sparkle + "AI Opener Review" + prompt pagination + close (✕)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = sparkleColor,
                    )
                    Text(
                        text = if (isChatMode) "Wingman Reply" else "Prompt Review",
                        fontFamily = modernEraBold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )

                    // Multiple prompts selector pill
                    if (promptEntries.size > 1) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = pillBg,
                            border = BorderStroke(0.5.dp, pillBorder),
                            modifier = Modifier.height(22.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ChevronLeft,
                                    contentDescription = "Previous Prompt",
                                    tint = if (safeIndex > 0) textPrimary else textSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable(enabled = safeIndex > 0) {
                                            selectedIndex = safeIndex - 1
                                        },
                                )
                                Text(
                                    text = "${safeIndex + 1}/${promptEntries.size}",
                                    fontFamily = modernEraBold,
                                    fontSize = 10.sp,
                                    color = textSecondary,
                                    modifier = Modifier.padding(horizontal = 2.dp),
                                )
                                Icon(
                                    imageVector = Icons.Default.ChevronRight,
                                    contentDescription = "Next Prompt",
                                    tint = if (safeIndex < promptEntries.size - 1) textPrimary else textSecondary.copy(alpha = 0.4f),
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable(enabled = safeIndex < promptEntries.size - 1) {
                                            selectedIndex = safeIndex + 1
                                        },
                                )
                            }
                        }
                    }
                }

                // Close ✕ button
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss",
                        tint = textSecondary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Target context chip
            if (activePromptItem != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = commentBg.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "${activePromptItem.question}: \"${activePromptItem.answer.take(65)}\"",
                        fontFamily = modernEraMedium,
                        fontSize = 11.sp,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            } else if (isChatMode && chatMessages.isNotEmpty()) {
                val lastMsg = chatMessages.lastOrNull()
                if (lastMsg != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = commentBg.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val senderName = if (!lastMsg.sentBySubject) "You" else matchDisplayName
                        Text(
                            text = "$senderName: \"${lastMsg.body.take(65)}\"",
                            fontFamily = modernEraMedium,
                            fontSize = 11.sp,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        )
                    }
                }
            }

            // Opener body
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = commentBg,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                ) {
                    when {
                        !isAiReady -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "API Key required to generate openers",
                                    fontFamily = modernEraMedium,
                                    fontSize = 12.sp,
                                    color = textSecondary,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = actionBtnBg,
                                    modifier = Modifier.clickable {
                                        openUnhingeSettings(context)
                                    },
                                ) {
                                    Text(
                                        text = "Setup",
                                        fontFamily = modernEraBold,
                                        fontSize = 11.sp,
                                        color = actionBtnText,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }

                        isEntryGenerating && activeReply.isNullOrBlank() -> {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.padding(vertical = 4.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = sparkleColor,
                                )
                                Text(
                                    text = "Crafting opener...",
                                    fontFamily = modernEraMedium,
                                    fontSize = 13.sp,
                                    color = textSecondary,
                                )
                            }
                        }

                        !activeReply.isNullOrBlank() -> {
                            Text(
                                text = activeReply.orEmpty(),
                                fontFamily = modernEraMedium,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                color = textPrimary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { copyOpener(activeReply.orEmpty()) },
                            )
                        }

                        status is AiWingmanHelper.WingmanStatus.Error -> {
                            Text(
                                text = (status as AiWingmanHelper.WingmanStatus.Error).message,
                                fontFamily = modernEraMedium,
                                fontSize = 12.sp,
                                color = Color(0xFFD32F2F),
                            )
                        }

                        else -> {
                            Text(
                                text = "Tap Retry to generate an opener",
                                fontFamily = modernEraMedium,
                                fontSize = 13.sp,
                                color = textSecondary,
                            )
                        }
                    }
                }
            }

            // Bottom Actions: Retry (Try another) and Copy
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Retry / Try Another
                Surface(
                    shape = RoundedCornerShape(50),
                    color = pillBg,
                    border = BorderStroke(1.dp, pillBorder),
                    modifier = Modifier
                        .height(34.dp)
                        .clickable(enabled = isAiReady && !isEntryGenerating) {
                            triggerGeneration(forceRefresh = true)
                        },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp),
                    ) {
                        if (isEntryGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 2.dp,
                                color = textSecondary,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = "Try another",
                            fontFamily = modernEraMedium,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Optional Insert Button (for chat / comment input)
                    if (onInsert != null && !activeReply.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = pillBg,
                            border = BorderStroke(1.dp, pillBorder),
                            modifier = Modifier
                                .height(34.dp)
                                .clickable {
                                    activeReply?.let(onInsert)
                                },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Insert",
                                    tint = textPrimary,
                                    modifier = Modifier.size(12.dp),
                                )
                                Text(
                                    text = "Insert",
                                    fontFamily = modernEraMedium,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textPrimary,
                                )
                            }
                        }
                    }

                    // Copy Button
                    val copyBg = if (isCopied) HingeDesignTokens.SuccessGreen else actionBtnBg
                    val copyTextColor = if (isCopied) Color.White else actionBtnText

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = copyBg,
                        modifier = Modifier
                            .height(34.dp)
                            .clickable(enabled = !activeReply.isNullOrBlank()) {
                                activeReply?.let(::copyOpener)
                            },
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = if (isCopied) "Copied" else "Copy",
                                tint = copyTextColor,
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = if (isCopied) "Copied!" else "Copy",
                                fontFamily = modernEraMedium,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = copyTextColor,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildProfileBlock(candidate: CachedCandidateProfile): String {
    val work = listOf(candidate.jobTitle, candidate.employer).filter { it.isNotBlank() }.joinToString(" at ")
    return listOfNotNull(
        buildString {
            append(candidate.firstName.ifBlank { "Candidate" })
            if (candidate.age > 0) append(", ${candidate.age}")
        }.takeIf { candidate.firstName.isNotBlank() || candidate.age > 0 },
        work.takeIf { it.isNotBlank() }?.let { "Work: $it" },
        candidate.location.takeIf { it.isNotBlank() }?.let { "Location: $it" },
    ).joinToString("\n")
}

private fun openUnhingeSettings(context: Context) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage("io.github.s1ddhants1.unhinge")
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Open Unhinge to configure AI settings", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Open Unhinge to configure AI settings", Toast.LENGTH_SHORT).show()
    }
}
