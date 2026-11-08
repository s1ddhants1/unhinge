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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
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

@Composable
fun InlineAiOpenerCard(
    prefs: PreferencesManager,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null,
    candidate: CachedCandidateProfile? = null,
    screenClues: HostCandidateReader.ScreenClues? = null,
    isChatMode: Boolean = false,
    hostActivity: Activity? = null,
    initialChatContext: HostCandidateReader.MatchChatContext? = null,
    onInsert: ((String) -> Unit)? = null,
    targetPrompt: PromptEntry? = null,
    isCommentMode: Boolean = false,
    isSectionCard: Boolean = false,
    showDragHandle: Boolean = false,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isDark = when (prefs.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val isPromptCardMode = targetPrompt != null || isCommentMode || isSectionCard

    val cardBg = if (isPromptCardMode) {
        if (isDark) Color(0xFF222224) else Color(0xFFF7F7F8)
    } else if (isDark) {
        if (prefs.pureBlack) Color.Black else HingeDesignTokens.DarkCard
    } else HingeDesignTokens.LightCard

    val cardBorder = if (isPromptCardMode) {
        if (isDark) Color(0xFF333336) else Color(0xFFE5E7EB)
    } else if (isDark) HingeDesignTokens.DarkBorder else HingeDesignTokens.LightBorder

    val commentBg = if (isPromptCardMode) {
        if (isDark) Color(0xFF1A1A1C) else Color(0xFFFFFFFF)
    } else if (isDark) HingeDesignTokens.DarkComment else HingeDesignTokens.LightComment

    val textPrimary = if (isDark) HingeDesignTokens.DarkTextPrimary else HingeDesignTokens.LightTextPrimary
    val textSecondary = if (isDark) HingeDesignTokens.DarkTextSecondary else HingeDesignTokens.LightTextSecondary
    val actionBtnBg = if (isDark) HingeDesignTokens.DarkPillSelected else HingeDesignTokens.LightPillSelected
    val actionBtnText = if (isDark) Color(0xFF161616) else Color(0xFFFFFFFF)
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

    val promptEntries = remember(activeCandidate?.userId, activeCandidate?.prompts, isChatMode, chatContext?.match?.subjectId, targetPrompt) {
        if (isChatMode) {
            chatArchetypes.map { PromptEntry(text = it) }
        } else {
            val prompts = activeCandidate?.prompts.orEmpty()
            if (prompts.isNotEmpty()) {
                val list = prompts.map {
                    val cleanAnswer = it.answer.replace("\\n", "\n")
                    PromptEntry(text = "${it.question}: $cleanAnswer")
                }
                if (targetPrompt != null && list.none { it.text.equals(targetPrompt.text, ignoreCase = true) }) {
                    listOf(targetPrompt) + list
                } else {
                    list
                }
            } else if (targetPrompt != null) {
                listOf(targetPrompt)
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

    val defaultIndex = remember(targetPrompt, promptEntries) {
        if (targetPrompt != null) {
            val idx = promptEntries.indexOfFirst {
                it.text.equals(targetPrompt.text, ignoreCase = true) ||
                    it.text.startsWith(targetPrompt.text.take(20), ignoreCase = true)
            }
            if (idx >= 0) idx else 0
        } else 0
    }

    var selectedIndex by remember(activeCandidate?.userId, isChatMode, defaultIndex) { mutableIntStateOf(defaultIndex) }
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
            val promptsToGen = if (targetPrompt == null && !forceRefresh && promptEntries.size > 1) {
                promptEntries
            } else {
                listOf(activeEntry)
            }
            AiWingmanHelper.generateReplies(
                prompts = promptsToGen,
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
        onInsert?.invoke(text)
        Toast.makeText(context, if (isCommentMode) "Inserted into comment" else "Copied opener to clipboard", Toast.LENGTH_SHORT).show()
        coroutineScope.launch {
            delay(800)
            isCopied = false
            if (isCommentMode) {
                onDismiss?.invoke()
            }
        }
    }

    val cardShape = if (isSectionCard) RoundedCornerShape(14.dp) else if (isPromptCardMode) RoundedCornerShape(12.dp) else RoundedCornerShape(20.dp)
    val cardElevation = if (isSectionCard) 0.dp else if (isPromptCardMode) 0.dp else 10.dp
    val cardModifier = if (isPromptCardMode) {
        modifier
            .fillMaxWidth()
            .wrapContentHeight()
    } else {
        modifier
            .fillMaxWidth()
            .wrapContentHeight()
            .shadow(12.dp, RoundedCornerShape(20.dp))
    }

    Surface(
        shape = cardShape,
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        shadowElevation = cardElevation,
        modifier = cardModifier,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showDragHandle) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(textSecondary.copy(alpha = 0.35f))
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = sparkleColor,
                    )
                    val title = if (isChatMode) {
                        if (matchDisplayName.isNotBlank()) "Wingman • $matchDisplayName" else "Wingman Reply"
                    } else {
                        val name = activeCandidate?.firstName?.ifBlank { null }
                        if (name != null) "Wingman • $name" else "AI Opener"
                    }
                    Text(
                        text = title,
                        fontFamily = modernEraBold,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )
                    if (isCopied) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = HingeDesignTokens.SuccessGreen,
                            )
                            Text(
                                text = if (isCommentMode) "Inserted!" else "Copied!",
                                fontFamily = modernEraMedium,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = HingeDesignTokens.SuccessGreen,
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFEFEFF1))
                            .clickable(enabled = isAiReady && !isEntryGenerating) {
                                triggerGeneration(forceRefresh = true)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isEntryGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 1.8.dp,
                                color = sparkleColor,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }

                    if (onDismiss != null) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (isDark) Color(0xFF2C2C2E) else Color(0xFFEFEFF1))
                                .clickable { onDismiss() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = textSecondary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            if (promptEntries.size > 1) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    promptEntries.forEachIndexed { idx, entry ->
                        val isSelected = idx == safeIndex
                        val chipLabel = if (isChatMode) {
                            entry.text
                        } else {
                            val q = entry.text.substringBefore(":").trim()
                            if (q.length > 24) q.take(22) + "…" else q
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) {
                                if (isDark) Color(0xFF38383B) else Color(0xFFE5E7EB)
                            } else {
                                if (isDark) Color(0xFF242426) else Color(0xFFF3F4F6)
                            },
                            border = if (isSelected) BorderStroke(1.dp, sparkleColor.copy(alpha = 0.5f)) else null,
                            modifier = Modifier.clickable {
                                selectedIndex = idx
                            }
                        ) {
                            Text(
                                text = chipLabel,
                                fontFamily = if (isSelected) modernEraBold else modernEraMedium,
                                fontSize = 11.5.sp,
                                color = if (isSelected) textPrimary else textSecondary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }

            if (!isChatMode && activeEntry != null) {
                val colonIdx = activeEntry.text.indexOf(":")
                val question = if (colonIdx != -1) activeEntry.text.substring(0, colonIdx).trim() else ""
                val answer = if (colonIdx != -1) activeEntry.text.substring(colonIdx + 1).trim() else activeEntry.text
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) Color(0xFF1B1B1D) else Color(0xFFF1F1F3),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
                        if (question.isNotBlank()) {
                            Text(
                                text = question,
                                fontFamily = modernEraBold,
                                fontSize = 11.sp,
                                color = textSecondary,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                        }
                        Text(
                            text = answer,
                            fontFamily = modernEraMedium,
                            fontSize = 12.5.sp,
                            color = textPrimary,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isCopied) (if (isDark) Color(0xFF1E2E24) else Color(0xFFF0FDF4)) else commentBg,
                border = if (isCopied) BorderStroke(1.dp, HingeDesignTokens.SuccessGreen.copy(alpha = 0.5f)) else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !activeReply.isNullOrBlank()) {
                        activeReply?.let(::copyOpener)
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
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
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(vertical = 2.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = sparkleColor,
                                )
                                Text(
                                    text = "Crafting opener...",
                                    fontFamily = modernEraMedium,
                                    fontSize = 12.5.sp,
                                    color = textSecondary,
                                )
                            }
                        }

                        !activeReply.isNullOrBlank() -> {
                            Text(
                                text = activeReply.orEmpty(),
                                fontFamily = modernEraMedium,
                                fontSize = 13.5.sp,
                                lineHeight = 18.5.sp,
                                color = textPrimary,
                                modifier = Modifier.fillMaxWidth(),
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
                                fontSize = 12.5.sp,
                                color = textSecondary,
                            )
                        }
                    }
                }
            }

            if (!activeReply.isNullOrBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = actionBtnBg,
                        modifier = Modifier.clickable {
                            activeReply?.let(::copyOpener)
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = actionBtnText,
                            )
                            Text(
                                text = if (isCopied) {
                                    if (isCommentMode) "Inserted!" else "Copied!"
                                } else {
                                    if (isCommentMode) "Insert into Comment" else if (isChatMode) "Insert into Chat" else "Copy Opener"
                                },
                                fontFamily = modernEraBold,
                                fontSize = 11.5.sp,
                                color = actionBtnText,
                            )
                        }
                    }
                }
            }
        }
    }
}

internal fun buildProfileBlock(candidate: CachedCandidateProfile): String {
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
