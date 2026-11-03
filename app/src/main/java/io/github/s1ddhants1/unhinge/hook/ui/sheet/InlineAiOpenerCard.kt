package io.github.s1ddhants1.unhinge.hook.ui.sheet

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.s1ddhants1.unhinge.ai.AiWingmanHelper
import io.github.s1ddhants1.unhinge.ai.PromptEntry
import io.github.s1ddhants1.unhinge.data.HostCandidateReader
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.ui.theme.HingeFonts
import io.github.s1ddhants1.unhinge.util.PreferencesManager
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

    val textPrimary = HingeDesignTokens.LightTextPrimary
    val textSecondary = HingeDesignTokens.LightTextSecondary
    val plumColor = HingeDesignTokens.HingePlum
    val cardBg = HingeDesignTokens.LightCard
    val cardBorderInactive = HingeDesignTokens.LightBorder
    val dragHandleColor = HingeDesignTokens.DragHandleLight
    val dotInactiveColor = HingeDesignTokens.DotInactiveLight

    val tiemposRegular = remember(context) { HingeFonts.tiemposRegular(context) }
    val modernEraRegular = remember(context) { HingeFonts.modernEraRegular(context) }
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

    val entries = remember(promptEntries) {
        if (promptEntries.isNotEmpty()) promptEntries else listOf(PromptEntry(text = "AI Wingman: Personalized Opener"))
    }

    val defaultIndex = remember(targetPrompt, entries) {
        if (targetPrompt != null) {
            val idx = entries.indexOfFirst {
                it.text.equals(targetPrompt.text, ignoreCase = true) ||
                    it.text.startsWith(targetPrompt.text.take(20), ignoreCase = true)
            }
            if (idx >= 0) idx else 0
        } else 0
    }

    val pagerState = rememberPagerState(initialPage = defaultIndex.coerceIn(0, (entries.size - 1).coerceAtLeast(0))) { entries.size }
    val safeIndex = pagerState.currentPage.coerceIn(0, (entries.size - 1).coerceAtLeast(0))
    val activeEntry = entries.getOrNull(safeIndex)

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
            val promptsToGen = if (targetPrompt == null && !forceRefresh && entries.size > 1) {
                entries
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
            delay(900)
            isCopied = false
            if (isCommentMode || isChatMode) {
                onDismiss?.invoke()
            }
        }
    }

    val handleInteractionSource = remember { MutableInteractionSource() }
    val isHandlePressed by handleInteractionSource.collectIsPressedAsState()

    BackHandler(enabled = onDismiss != null) {
        onDismiss?.invoke()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .wrapContentHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showDragHandle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(30.dp)
                    .clip(RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                    .background(if (isHandlePressed) Color(0xFFEFEEEC) else Color.Transparent)
                    .clickable(
                        interactionSource = handleInteractionSource,
                        indication = null,
                    ) {
                        onDismiss?.invoke()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 68.dp, height = 5.5.dp)
                        .clip(CircleShape)
                        .background(if (isHandlePressed) Color(0xFF8E8E8E) else dragHandleColor)
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        } else {
            Spacer(modifier = Modifier.height(14.dp))
        }

        val headerTitle = if (isChatMode) {
            if (matchDisplayName.isNotBlank()) "Keep the conversation\ngoing with $matchDisplayName" else "Keep the conversation\ngoing"
        } else {
            val name = activeCandidate?.firstName?.ifBlank { null }
            if (name != null) "Catch ${name}'s eye by\nsending an opener" else "Catch their eye with\na crafted opener"
        }

        Text(
            text = headerTitle,
            fontFamily = tiemposRegular,
            fontSize = 25.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Normal,
            textAlign = TextAlign.Center,
            color = textPrimary,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        val headerSubtitle = if (isChatMode) {
            "Smart banter and tailored replies crafted to match their energy and lead to a date."
        } else {
            "Personalized openers crafted from profile prompts to make starting a conversation effortless."
        }

        Text(
            text = headerSubtitle,
            fontFamily = modernEraRegular,
            fontSize = 13.5.sp,
            lineHeight = 18.5.sp,
            textAlign = TextAlign.Center,
            color = textSecondary,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(18.dp))

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 52.dp),
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxWidth(),
        ) { page ->
            val isSelected = pagerState.currentPage == page
            val entry = entries.getOrNull(page)
            val entryReply by (entry?.suggestedReplyFlow?.collectAsState() ?: remember { mutableStateOf(null) })
            val entryGenerating by (entry?.isGeneratingFlow?.collectAsState() ?: remember { mutableStateOf(false) })

            val colonIdx = entry?.text?.indexOf(":") ?: -1
            val cardQuestion = if (colonIdx != -1) entry?.text?.substring(0, colonIdx)?.trim().orEmpty() else ""
            val cardAnswer = if (colonIdx != -1) entry?.text?.substring(colonIdx + 1)?.trim().orEmpty() else entry?.text.orEmpty()

            val cardTitle = if (isChatMode) {
                entry?.text.orEmpty()
            } else if (cardQuestion.isNotBlank()) {
                cardQuestion
            } else {
                "Opener ${page + 1}"
            }

            val cardSub = if (isChatMode) {
                "Tailored to conversation"
            } else if (cardAnswer.isNotBlank() && cardQuestion.isNotBlank()) {
                "\"${cardAnswer.replace("\n", " ").trim()}\""
            } else {
                "Tailored to profile"
            }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, if (isSelected) plumColor else cardBorderInactive),
                shadowElevation = 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(330.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable(enabled = isAiReady && !entryGenerating) {
                                    triggerGeneration(forceRefresh = true)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate",
                                tint = textSecondary.copy(alpha = 0.5f),
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 22.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = cardTitle,
                                fontFamily = tiemposRegular,
                                fontSize = 23.sp,
                                lineHeight = 27.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                color = textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = cardSub,
                                fontFamily = modernEraBold,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                color = textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clickable(enabled = !entryReply.isNullOrBlank()) {
                                    entryReply?.let(::copyOpener)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                !isAiReady -> {
                                    Text(
                                        text = "API Key required in Unhinge settings",
                                        fontFamily = modernEraRegular,
                                        fontSize = 13.sp,
                                        color = textSecondary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                entryGenerating && entryReply.isNullOrBlank() -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = plumColor,
                                        )
                                        Text(
                                            text = "Crafting opener...",
                                            fontFamily = modernEraRegular,
                                            fontSize = 13.5.sp,
                                            color = textSecondary,
                                        )
                                    }
                                }
                                !entryReply.isNullOrBlank() -> {
                                    Text(
                                        text = entryReply.orEmpty(),
                                        fontFamily = modernEraRegular,
                                        fontSize = 13.5.sp,
                                        lineHeight = 19.5.sp,
                                        textAlign = TextAlign.Center,
                                        color = Color(0xFF4A4A4A),
                                        maxLines = 5,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                status is AiWingmanHelper.WingmanStatus.Error -> {
                                    Text(
                                        text = (status as AiWingmanHelper.WingmanStatus.Error).message,
                                        fontFamily = modernEraRegular,
                                        fontSize = 12.5.sp,
                                        color = Color(0xFFD32F2F),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                                else -> {
                                    Text(
                                        text = "Tap Select below to craft a tailored opener.",
                                        fontFamily = modernEraRegular,
                                        fontSize = 13.sp,
                                        color = textSecondary,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (entries.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 18.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                entries.indices.forEach { idx ->
                    val isActive = idx == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(6.5.dp)
                            .clip(CircleShape)
                            .background(if (isActive) textPrimary else dotInactiveColor)
                            .clickable {
                                coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                            }
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.height(18.dp))
        }

        Surface(
            shape = CircleShape,
            color = plumColor,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .height(52.dp)
                .clickable {
                    if (!isAiReady) {
                        openUnhingeSettings(context)
                        return@clickable
                    }
                    val current = activeReply
                    if (current.isNullOrBlank()) {
                        triggerGeneration(forceRefresh = true)
                    } else {
                        copyOpener(current)
                    }
                },
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    isCopied -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = if (isCommentMode) "Inserted" else "Selected",
                                fontFamily = modernEraBold,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                    isEntryGenerating && activeReply.isNullOrBlank() -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                            Text(
                                text = "Crafting...",
                                fontFamily = modernEraBold,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                    !isAiReady -> {
                        Text(
                            text = "Setup AI Settings",
                            fontFamily = modernEraBold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                    else -> {
                        Text(
                            text = "Select",
                            fontFamily = modernEraBold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.navigationBarsPadding().padding(bottom = 12.dp))
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
