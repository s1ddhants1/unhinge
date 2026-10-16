package io.github.s1ddhants1.unhinge.hook.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.setSingletonImageLoaderFactory
import io.github.s1ddhants1.unhinge.ai.AiWingmanHelper
import io.github.s1ddhants1.unhinge.ai.OpenRouterStreamingService
import io.github.s1ddhants1.unhinge.ai.PromptEntry
import io.github.s1ddhants1.unhinge.ai.WingmanPrompts
import io.github.s1ddhants1.unhinge.data.HostCandidateReader
import io.github.s1ddhants1.unhinge.hook.ui.sheet.CandidatePromptCard
import io.github.s1ddhants1.unhinge.hook.ui.sheet.ChatArchetypeInfo
import io.github.s1ddhants1.unhinge.hook.ui.sheet.CustomAiInteraction
import io.github.s1ddhants1.unhinge.hook.ui.sheet.CustomAiResponseCard
import io.github.s1ddhants1.unhinge.hook.ui.sheet.HingeDesignTokens
import io.github.s1ddhants1.unhinge.hook.ui.sheet.MatchChatReplyCard
import io.github.s1ddhants1.unhinge.hook.ui.sheet.SheetBottomBar
import io.github.s1ddhants1.unhinge.hook.ui.sheet.SheetHeader
import io.github.s1ddhants1.unhinge.hook.ui.sheet.SheetLoadingState
import io.github.s1ddhants1.unhinge.hook.ui.sheet.SheetNoCandidateState
import io.github.s1ddhants1.unhinge.hook.ui.sheet.SheetNoMatchChatState
import io.github.s1ddhants1.unhinge.hook.ui.sheet.SheetSetupRequiredState
import io.github.s1ddhants1.unhinge.hook.ui.sheet.VibeChip
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.ui.theme.HingeFonts
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.ThemeMode
import io.github.s1ddhants1.unhinge.util.UnhingeImageLoader
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HostAppAiSheetContent(
    prefs: PreferencesManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    candidateList: List<CachedCandidateProfile>? = null,
    screenClues: HostCandidateReader.ScreenClues? = null,
    showDragHandle: Boolean = false,
    standalone: Boolean = true,
    isChatMode: Boolean = false,
    hostActivity: Activity? = null,
    initialChatContext: HostCandidateReader.MatchChatContext? = null,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val isDark = when (prefs.themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val sheetBg = if (isDark) {
        if (prefs.pureBlack) Color.Black else HingeDesignTokens.DarkBg
    } else HingeDesignTokens.LightBg

    val cardBg = if (isDark) HingeDesignTokens.DarkCard else HingeDesignTokens.LightCard
    val cardBorder = if (isDark) HingeDesignTokens.DarkBorder else HingeDesignTokens.LightBorder
    val commentBg = if (isDark) HingeDesignTokens.DarkComment else HingeDesignTokens.LightComment
    val textPrimary = if (isDark) HingeDesignTokens.DarkTextPrimary else HingeDesignTokens.LightTextPrimary
    val textSecondary = if (isDark) HingeDesignTokens.DarkTextSecondary else HingeDesignTokens.LightTextSecondary
    val actionBtnBg = if (isDark) HingeDesignTokens.DarkPillSelected else HingeDesignTokens.LightPillSelected
    val actionBtnText = if (isDark) Color(0xFF161616) else Color(0xFFFFFFFF)
    val pillBg = if (isDark) HingeDesignTokens.DarkPillUnselected else HingeDesignTokens.LightPillUnselected
    val pillBorder = if (isDark) HingeDesignTokens.DarkPillBorder else HingeDesignTokens.LightPillBorder

    val tiemposRegular = remember(context) { HingeFonts.tiemposRegular(context) }
    val modernEraRegular = remember(context) { HingeFonts.modernEraRegular(context) }
    val modernEraMedium = remember(context) { HingeFonts.modernEraMedium(context) }
    val modernEraBold = remember(context) { HingeFonts.modernEraBold(context) }

    var loadedCandidate by remember { mutableStateOf(candidateList?.firstOrNull()) }
    var isLoadingCandidate by remember { mutableStateOf(!isChatMode && candidateList == null) }
    var loadedChatContext by remember { mutableStateOf(initialChatContext) }
    var isLoadingChat by remember { mutableStateOf(isChatMode && initialChatContext == null) }

    LaunchedEffect(candidateList, screenClues, isChatMode) {
        if (isChatMode) {
            if (initialChatContext != null) {
                loadedChatContext = initialChatContext
                isLoadingChat = false
            } else {
                isLoadingChat = true
                val chat = HostCandidateReader.readCurrentMatchChat(context, screenClues)
                loadedChatContext = chat
                isLoadingChat = false
            }
        } else {
            if (candidateList != null) {
                loadedCandidate = candidateList.firstOrNull()
                isLoadingCandidate = false
            } else {
                isLoadingCandidate = true
                val current = HostCandidateReader.readTargetCandidate(context, screenClues)
                    ?: HostCandidateReader.readActiveCandidates(context).firstOrNull()
                loadedCandidate = current
                isLoadingCandidate = false
            }
        }
    }

    val chatContext = loadedChatContext
    val chatMatch = chatContext?.match
    val candidate = if (isChatMode) (chatContext?.candidate ?: loadedCandidate) else loadedCandidate
    val chatMessages = chatContext?.messages.orEmpty()
    val chatLastMessage = chatContext?.lastMessage
    val isMyTurn = chatContext?.isMyTurn ?: true

    val matchDisplayName = chatMatch?.firstName?.ifBlank { candidate?.firstName.orEmpty() }
        ?: candidate?.firstName.orEmpty()
    val matchAge = if (chatMatch != null && chatMatch.age > 0) chatMatch.age else (candidate?.age ?: 0)
    val matchPhotoUrl = chatMatch?.photoUrl?.ifBlank { candidate?.photos?.firstOrNull().orEmpty() }
        ?: candidate?.photos?.firstOrNull().orEmpty()

    val status by AiWingmanHelper.status.collectAsState()

    val chatArchetypes = remember {
        listOf(
            ChatArchetypeInfo(
                title = "Playful Tease / Banter",
                subtitle = "Playful push-pull banter",
                description = "Light mock-challenge or push-pull that invites a fun, defensive laugh.",
            ),
            ChatArchetypeInfo(
                title = "Date Pitch / Move to Meet",
                subtitle = "Transition chat to meeting up",
                description = "Natural, low-pressure transition from the topic into grabbing drinks or food.",
            ),
            ChatArchetypeInfo(
                title = "Witty Callback / Story",
                subtitle = "Clever callback & relatable take",
                description = "Tie their comment back to a shared interest, prior message, or short quip.",
            ),
            ChatArchetypeInfo(
                title = "Casual / Low-Key",
                subtitle = "Effortless, relaxed momentum",
                description = "Low-investment, smooth 1-2 sentence response keeping the conversation flowing.",
            ),
        )
    }

    val chatPromptEntries = remember(chatMatch?.subjectId, chatMessages.size) {
        if (isChatMode) {
            chatArchetypes.map { archetype ->
                PromptEntry(text = archetype.title)
            }
        } else {
            emptyList()
        }
    }

    val promptEntries = if (isChatMode) {
        chatPromptEntries
    } else {
        remember(candidate?.userId, candidate?.prompts) {
            val prompts = candidate?.prompts.orEmpty()
            if (prompts.isNotEmpty()) {
                prompts.map {
                    val cleanAnswer = it.answer.replace("\\n", "\n")
                    PromptEntry(text = "${it.question}: $cleanAnswer")
                }
            } else if (candidate != null) {
                val bio = listOfNotNull(
                    candidate.jobTitle.takeIf { it.isNotBlank() }?.let { "Job: $it" },
                    candidate.location.takeIf { it.isNotBlank() }?.let { "Location: $it" },
                    candidate.datingIntention.takeIf { it.isNotBlank() }?.let { "Dating Intentions: $it" },
                ).joinToString(", ")
                if (bio.isNotBlank()) listOf(PromptEntry(text = bio)) else emptyList()
            } else {
                emptyList()
            }
        }
    }

    val isApiKeyRequired = AiWingmanHelper.isApiKeyRequired(prefs.aiProvider, prefs.openRouterModel)
    val isAiReady = !isApiKeyRequired || prefs.openRouterApiKey.isNotBlank()

    var selectedVibe by remember { mutableStateOf("Auto") }

    fun requestWingmanGeneration(
        targets: List<PromptEntry> = promptEntries,
        forceRefresh: Boolean = false,
        vibe: String = selectedVibe,
    ) {
        if (!isAiReady || targets.isEmpty()) return
        val directionalStimulus = if (vibe == "Auto") "" else vibe
        if (isChatMode) {
            val profileBlock = candidate?.let(::openerProfileBlock) ?: (matchDisplayName.takeIf { it.isNotBlank() } ?: "")
            AiWingmanHelper.generateChatReplies(
                matchName = matchDisplayName,
                profileBlock = profileBlock,
                conversationHistory = chatMessages,
                prompts = targets,
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
                directionalStimulus = directionalStimulus,
            )
        } else {
            AiWingmanHelper.generateReplies(
                prompts = targets,
                apiKey = prefs.openRouterApiKey,
                baseUrl = prefs.openRouterBaseUrl,
                model = prefs.openRouterModel,
                scope = coroutineScope,
                context = context,
                prefs = prefs,
                provider = prefs.aiProvider,
                useStreaming = true,
                systemPrompt = prefs.aiCustomSystemPrompt,
                profileBlock = candidate?.let(::openerProfileBlock).orEmpty(),
                forceRefresh = forceRefresh,
                temperature = prefs.aiTemperature,
                topP = prefs.aiTopP,
                reasoningEffort = prefs.aiReasoningEffort,
                directionalStimulus = directionalStimulus,
            )
        }
    }

    LaunchedEffect(
        isChatMode,
        if (isChatMode) chatMatch?.subjectId ?: chatContext?.candidate?.userId else candidate?.userId,
        prefs.openRouterApiKey,
        prefs.aiProvider,
        prefs.openRouterModel,
    ) {
        if (isAiReady && promptEntries.isNotEmpty()) {
            requestWingmanGeneration()
        }
    }

    var copiedIndex by remember { mutableStateOf<Int?>(null) }

    fun copyOpener(index: Int, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Unhinge Opener", text))
        copiedIndex = index
        Toast.makeText(context, "Opener copied to clipboard", Toast.LENGTH_SHORT).show()
        coroutineScope.launch {
            delay(2000)
            if (copiedIndex == index) {
                copiedIndex = null
            }
        }
    }

    var askAiQuery by remember { mutableStateOf("") }
    var isAskAiGenerating by remember { mutableStateOf(false) }
    val customInteractions = remember { mutableStateListOf<CustomAiInteraction>() }

    val promptCardsCount = if (isChatMode) {
        chatArchetypes.size
    } else {
        remember(candidate?.userId, candidate?.prompts, promptEntries.size) {
            if (candidate?.prompts.isNullOrEmpty()) {
                if (promptEntries.isNotEmpty()) 1 else 0
            } else {
                candidate.prompts.size
            }
        }
    }
    val totalCards = (promptCardsCount + customInteractions.size).coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = 0) { totalCards }

    LaunchedEffect(candidate?.userId, chatMatch?.subjectId) {
        selectedVibe = "Auto"
        customInteractions.clear()
        if (pagerState.currentPage != 0) {
            pagerState.scrollToPage(0)
        }
    }

    fun submitAskAi(queryText: String = askAiQuery) {
        val query = queryText.trim()
        if (query.isBlank()) return

        val activeKey = prefs.openRouterApiKey
        if (!isAiReady) {
            launchUnhingeSettings(context)
            return
        }

        askAiQuery = ""

        val newInteraction = CustomAiInteraction(query = query, isStreaming = true)
        customInteractions.add(newInteraction)
        isAskAiGenerating = true

        coroutineScope.launch {
            delay(50)
            val targetPage = promptCardsCount + customInteractions.size - 1
            if (targetPage >= 0) {
                pagerState.animateScrollToPage(targetPage)
            }
        }

        val candidateProfileSummary = buildString {
            if (isChatMode) {
                append("Name: ${matchDisplayName.ifBlank { "Candidate" }}")
                if (matchAge > 0) append(", Age: $matchAge")
            }
            if (candidate != null) {
                if (!isChatMode) {
                    append("Name: ${candidate.firstName.ifBlank { "Candidate" }}")
                    if (candidate.age > 0) append(", Age: ${candidate.age}")
                }
                if (candidate.jobTitle.isNotBlank()) append("\nWork: ${candidate.jobTitle}")
                if (candidate.employer.isNotBlank()) append(" at ${candidate.employer}")
                if (candidate.school.isNotBlank()) append("\nSchool: ${candidate.school}")
                if (candidate.location.isNotBlank()) append("\nLocation: ${candidate.location}")
                if (candidate.hometown.isNotBlank()) append("\nHometown: ${candidate.hometown}")
                if (candidate.datingIntention.isNotBlank()) append("\nDating Intention: ${candidate.datingIntention}")
                if (candidate.relationshipType.isNotBlank()) append("\nRelationship Type: ${candidate.relationshipType}")
                if (candidate.religion.isNotBlank()) append("\nReligion: ${candidate.religion}")
                if (candidate.politics.isNotBlank()) append("\nPolitics: ${candidate.politics}")
                if (candidate.prompts.isNotEmpty()) {
                    append("\n\nPrompts:")
                    candidate.prompts.forEachIndexed { i, p ->
                        val cleanAns = p.answer.replace("\\n", "\n")
                        append("\n${i + 1}. ${p.question}: $cleanAns")
                    }
                }
            }
        }

        val historyText = if (isChatMode && chatMessages.isNotEmpty()) {
            AiWingmanHelper.formatConversationHistory(matchDisplayName, chatMessages)
        } else {
            "No messages yet."
        }

        val systemPrompt = if (isChatMode) {
            WingmanPrompts.chatReplySystemPrompt(
                custom = prefs.aiCustomSystemPrompt,
                prefs = prefs,
                context = context,
            )
        } else {
            WingmanPrompts.askAiSystemPrompt(
                profileSummary = candidateProfileSummary,
                custom = prefs.aiCustomSystemPrompt,
                prefs = prefs,
                context = context,
            )
        }

        val userPrompt = if (isChatMode) {
            """
Match: $matchDisplayName${if (matchAge > 0) ", $matchAge" else ""}
Bio Details:
$candidateProfileSummary

Recent Conversation:
$historyText

User Request / Question:
$query
            """.trimIndent()
        } else {
            query
        }

        coroutineScope.launch {
            try {
                val contentAccumulator = StringBuilder()
                AiWingmanHelper.streamCustomChat(
                    userPrompt = userPrompt,
                    systemPrompt = systemPrompt,
                    apiKey = activeKey,
                    baseUrl = prefs.openRouterBaseUrl,
                    model = prefs.openRouterModel,
                    provider = prefs.aiProvider,
                    temperature = prefs.aiTemperature,
                    topP = prefs.aiTopP,
                    reasoningEffort = prefs.aiReasoningEffort,
                ).collect { chunk ->
                    when (chunk) {
                        is OpenRouterStreamingService.ChatStreamChunk.Content -> {
                            contentAccumulator.append(chunk.text)
                            val idx = customInteractions.indexOfFirst { it.id == newInteraction.id }
                            if (idx != -1) {
                                customInteractions[idx] = customInteractions[idx].copy(
                                    reply = contentAccumulator.toString(),
                                    isStreaming = true,
                                )
                            }
                        }
                        is OpenRouterStreamingService.ChatStreamChunk.Complete -> {
                            val idx = customInteractions.indexOfFirst { it.id == newInteraction.id }
                            if (idx != -1) {
                                customInteractions[idx] = customInteractions[idx].copy(
                                    reply = chunk.fullText.ifBlank { contentAccumulator.toString() },
                                    isStreaming = false,
                                )
                            }
                        }
                        is OpenRouterStreamingService.ChatStreamChunk.Error -> {
                            val idx = customInteractions.indexOfFirst { it.id == newInteraction.id }
                            if (idx != -1) {
                                customInteractions[idx] = customInteractions[idx].copy(
                                    error = chunk.message,
                                    isStreaming = false,
                                )
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                val idx = customInteractions.indexOfFirst { it.id == newInteraction.id }
                if (idx != -1) {
                    customInteractions[idx] = customInteractions[idx].copy(
                        error = e.message ?: "Failed to generate reply",
                        isStreaming = false,
                    )
                }
            } finally {
                isAskAiGenerating = false
            }
        }
    }

    val imageLoader = remember(context) { UnhingeImageLoader.get(context) }
    setSingletonImageLoaderFactory { ctx ->
        UnhingeImageLoader.get(ctx)
    }

    val offsetY = remember { Animatable(0f) }
    var sheetHeightPx by remember { mutableFloatStateOf(0f) }

    fun dismissWithAnimation() {
        coroutineScope.launch {
            offsetY.animateTo(
                targetValue = if (sheetHeightPx > 0f) sheetHeightPx else 2000f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            )
            onDismiss()
        }
    }

    val handleDragStopped: (Float) -> Unit = { velocity ->
        coroutineScope.launch {
            val dismissThreshold = if (sheetHeightPx > 0f) sheetHeightPx * 0.25f else 300f
            if (velocity > 1000f || offsetY.value > dismissThreshold) {
                offsetY.animateTo(
                    targetValue = if (sheetHeightPx > 0f) sheetHeightPx else 2000f,
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                )
                onDismiss()
            } else {
                offsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                )
            }
        }
    }

    val dragModifier = Modifier.draggable(
        state = rememberDraggableState { delta ->
            coroutineScope.launch {
                offsetY.snapTo((offsetY.value + delta).coerceAtLeast(0f))
            }
        },
        orientation = Orientation.Vertical,
        onDragStopped = { velocity ->
            handleDragStopped(velocity)
        },
    )

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val delta = available.y
                if (delta < 0f && offsetY.value > 0f) {
                    val newOffset = (offsetY.value + delta).coerceAtLeast(0f)
                    val consumed = newOffset - offsetY.value
                    coroutineScope.launch {
                        offsetY.snapTo(newOffset)
                    }
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                val delta = available.y
                if (delta > 0f) {
                    val newOffset = offsetY.value + delta
                    coroutineScope.launch {
                        offsetY.snapTo(newOffset)
                    }
                    return Offset(0f, delta)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (offsetY.value > 0f) {
                    handleDragStopped(available.y)
                    return available
                }
                return Velocity.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > 0f || offsetY.value > 0f) {
                    handleDragStopped(available.y)
                    return available
                }
                return Velocity.Zero
            }
        }
    }

    val sheetModifier = if (standalone) {
        modifier
            .fillMaxWidth()
            .fillMaxHeight(0.90f)
            .offset { IntOffset(0, offsetY.value.roundToInt().coerceAtLeast(0)) }
            .onGloballyPositioned { coordinates ->
                sheetHeightPx = coordinates.size.height.toFloat()
            }
            .nestedScroll(nestedScrollConnection)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(sheetBg)
            .padding(top = 10.dp, bottom = 8.dp)
    } else {
        modifier
            .fillMaxWidth()
            .background(sheetBg)
            .padding(top = 4.dp, bottom = 8.dp)
    }

    Column(modifier = sheetModifier) {
        SheetHeader(
            isChatMode = isChatMode,
            candidate = candidate,
            chatMatch = chatMatch,
            chatMessages = chatMessages,
            chatLastMessage = chatLastMessage,
            isMyTurn = isMyTurn,
            matchDisplayName = matchDisplayName,
            matchAge = matchAge,
            matchPhotoUrl = matchPhotoUrl,
            showDragHandle = showDragHandle,
            standalone = standalone,
            isDark = isDark,
            cardBg = cardBg,
            cardBorder = cardBorder,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            modernEraBold = modernEraBold,
            modernEraRegular = modernEraRegular,
            imageLoader = imageLoader,
            showAvailableLikes = prefs.showAvailableLikes,
            dragModifier = dragModifier,
            onDismiss = { dismissWithAnimation() },
        )

        Spacer(Modifier.height(14.dp))

        if (isChatMode && isLoadingChat) {
            SheetLoadingState(
                text = "Reading match chat...",
                actionBtnBg = actionBtnBg,
                textSecondary = textSecondary,
                modernEraRegular = modernEraRegular,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp)
                    .then(dragModifier),
            )
            return@Column
        } else if (!isChatMode && isLoadingCandidate) {
            SheetLoadingState(
                text = "Reading active candidate...",
                actionBtnBg = actionBtnBg,
                textSecondary = textSecondary,
                modernEraRegular = modernEraRegular,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp)
                    .then(dragModifier),
            )
            return@Column
        }

        if (isChatMode && chatContext == null && candidate == null) {
            SheetNoMatchChatState(
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                modernEraBold = modernEraBold,
                modernEraRegular = modernEraRegular,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp)
                    .then(dragModifier),
            )
            return@Column
        } else if (!isChatMode && candidate == null) {
            SheetNoCandidateState(
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                modernEraBold = modernEraBold,
                modernEraRegular = modernEraRegular,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp)
                    .then(dragModifier),
            )
            return@Column
        }

        if (!isAiReady) {
            SheetSetupRequiredState(
                cardBg = cardBg,
                cardBorder = cardBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                actionBtnBg = actionBtnBg,
                actionBtnText = actionBtnText,
                modernEraBold = modernEraBold,
                modernEraMedium = modernEraMedium,
                modernEraRegular = modernEraRegular,
                provider = prefs.aiProvider,
                model = prefs.openRouterModel,
                candidateName = candidate?.firstName?.ifBlank { matchDisplayName.ifBlank { "this profile" } } ?: matchDisplayName.ifBlank { "this profile" },
                onConfigure = { launchUnhingeSettings(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
            )
        } else {
            val vibes = remember(isChatMode) {
                if (isChatMode) {
                    listOf("Auto", "Playful Tease", "Date Pitch", "Witty Banter", "Casual", "Bold")
                } else {
                    listOf("Auto", "Playful Tease", "Micro-Debate", "Chaos Story", "Collaborative")
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                vibes.forEach { vibe ->
                    val isSelected = selectedVibe == vibe
                    VibeChip(
                        label = vibe,
                        isSelected = isSelected,
                        selectedBg = actionBtnBg,
                        selectedText = actionBtnText,
                        unselectedBg = pillBg,
                        unselectedBorder = pillBorder,
                        unselectedText = textSecondary,
                        font = modernEraMedium,
                        onClick = {
                            selectedVibe = vibe
                            requestWingmanGeneration(
                                targets = promptEntries,
                                forceRefresh = true,
                                vibe = vibe,
                            )
                        },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            if (isChatMode && chatContext != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = commentBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 2.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isMyTurn) Color(0xFF2E7D32) else textSecondary),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = if (chatLastMessage != null) {
                                        if (chatLastMessage.sentBySubject) "${matchDisplayName.ifBlank { "Match" }}'s last message" else "Your last message"
                                    } else "Conversation",
                                    fontFamily = modernEraBold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary,
                                )
                                Text(
                                    text = if (isMyTurn) "Your turn" else "Waiting on reply",
                                    fontFamily = modernEraMedium,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isMyTurn) Color(0xFF2E7D32) else textSecondary,
                                )
                            }
                            val previewBody = when {
                                chatLastMessage == null -> "Fresh match. Send the first message to kick off the chat."
                                chatLastMessage.isVoiceNote && chatLastMessage.voiceTranscript.isNotBlank() -> "[Voice note]: \"${chatLastMessage.voiceTranscript}\""
                                chatLastMessage.isVoiceNote -> "[Sent a voice note]"
                                else -> chatLastMessage.body.ifBlank { "Sent a message" }
                            }
                            Text(
                                text = previewBody,
                                fontFamily = tiemposRegular,
                                fontSize = 14.sp,
                                color = textPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 28.dp),
            pageSpacing = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { page ->
            if (isChatMode && page < promptCardsCount) {
                val archetype = chatArchetypes.getOrNull(page)
                val entry = chatPromptEntries.getOrNull(page)
                if (archetype != null) {
                    MatchChatReplyCard(
                        archetype = archetype,
                        page = page,
                        totalCount = chatArchetypes.size,
                        isCurrentPage = pagerState.currentPage == page,
                        promptEntry = entry,
                        status = status,
                        isCopied = copiedIndex == page,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        commentBg = commentBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        actionBtnBg = actionBtnBg,
                        actionBtnText = actionBtnText,
                        pillBg = pillBg,
                        pillBorder = pillBorder,
                        isDark = isDark,
                        modernEraBold = modernEraBold,
                        modernEraMedium = modernEraMedium,
                        modernEraRegular = modernEraRegular,
                        matchDisplayName = matchDisplayName,
                        onCopy = { copyOpener(page, it) },
                        onRegenerate = { entry?.let { requestWingmanGeneration(listOf(it), forceRefresh = true) } },
                        onOpenSettings = { launchUnhingeSettings(context) },
                        onAskAi = { submitAskAi(it) },
                        onSteer = { instruction ->
                            if (entry != null) {
                                val activeOpener = entry.repliesFlow.value.getOrNull(entry.activeReplyIndexFlow.value)
                                    ?: entry.suggestedReplyFlow.value.orEmpty()
                                val profileBlock = candidate?.let(::openerProfileBlock) ?: (matchDisplayName.takeIf { it.isNotBlank() } ?: "")
                                AiWingmanHelper.steerOpener(
                                    entry = entry,
                                    currentOpener = activeOpener,
                                    steerInstruction = instruction,
                                    profileBlock = profileBlock,
                                    apiKey = prefs.openRouterApiKey,
                                    baseUrl = prefs.openRouterBaseUrl,
                                    model = prefs.openRouterModel,
                                    scope = coroutineScope,
                                    context = context,
                                    prefs = prefs,
                                    provider = prefs.aiProvider,
                                    temperature = prefs.aiTemperature,
                                    topP = prefs.aiTopP,
                                )
                            }
                        },
                        onInsert = if (hostActivity != null) {
                            { replyTextToInsert ->
                                HostAppAiFab.insertReplyIntoChatInput(hostActivity, replyTextToInsert)
                                copyOpener(page, replyTextToInsert)
                                Toast.makeText(context, "Inserted reply into chat", Toast.LENGTH_SHORT).show()
                                dismissWithAnimation()
                            }
                        } else null,
                    )
                }
            } else if (!isChatMode && page < promptCardsCount && candidate != null) {
                val entry = promptEntries.getOrNull(page)
                CandidatePromptCard(
                    candidate = candidate,
                    page = page,
                    promptCardsCount = promptCardsCount,
                    isCurrentPage = pagerState.currentPage == page,
                    promptEntry = entry,
                    status = status,
                    isCopied = copiedIndex == page,
                    cardBg = cardBg,
                    cardBorder = cardBorder,
                    commentBg = commentBg,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    actionBtnBg = actionBtnBg,
                    actionBtnText = actionBtnText,
                    pillBg = pillBg,
                    pillBorder = pillBorder,
                    isDark = isDark,
                    modernEraBold = modernEraBold,
                    modernEraMedium = modernEraMedium,
                    tiemposRegular = tiemposRegular,
                    onCopy = { copyOpener(page, it) },
                    onRegenerate = { entry?.let { requestWingmanGeneration(listOf(it), forceRefresh = true) } },
                    onOpenSettings = { launchUnhingeSettings(context) },
                    onAskAi = { submitAskAi(it) },
                    onSteer = { instruction ->
                        if (entry != null) {
                            val activeOpener = entry.repliesFlow.value.getOrNull(entry.activeReplyIndexFlow.value)
                                ?: entry.suggestedReplyFlow.value.orEmpty()
                            AiWingmanHelper.steerOpener(
                                entry = entry,
                                currentOpener = activeOpener,
                                steerInstruction = instruction,
                                profileBlock = openerProfileBlock(candidate),
                                apiKey = prefs.openRouterApiKey,
                                baseUrl = prefs.openRouterBaseUrl,
                                model = prefs.openRouterModel,
                                scope = coroutineScope,
                                context = context,
                                prefs = prefs,
                                provider = prefs.aiProvider,
                                temperature = prefs.aiTemperature,
                                topP = prefs.aiTopP,
                            )
                        }
                    },
                )
            } else {
                val interactionIndex = page - promptCardsCount
                val item = customInteractions.getOrNull(interactionIndex)
                if (item != null) {
                    CustomAiResponseCard(
                        interaction = item,
                        index = interactionIndex + 1,
                        isSelected = pagerState.currentPage == page,
                        cardBg = cardBg,
                        cardBorder = cardBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        actionBtnBg = actionBtnBg,
                        actionBtnText = actionBtnText,
                        commentBg = commentBg,
                        pillBg = pillBg,
                        pillBorder = pillBorder,
                        isDark = isDark,
                        onCopy = { text ->
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Unhinge AI Response", text))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        },
                        onRegenerate = {
                            customInteractions.removeAll { it.id == item.id }
                            submitAskAi(item.query)
                        },
                        onDismiss = {
                            val removedIdx = customInteractions.indexOfFirst { it.id == item.id }
                            customInteractions.removeAll { it.id == item.id }
                            if (removedIdx != -1) {
                                coroutineScope.launch {
                                    val maxPage = (promptCardsCount + customInteractions.size - 1).coerceAtLeast(0)
                                    if (pagerState.currentPage > maxPage) {
                                        pagerState.animateScrollToPage(maxPage)
                                    }
                                }
                            }
                        },
                        onRefine = { refinementQuery ->
                            submitAskAi(refinementQuery)
                        },
                        onInsert = if (hostActivity != null && isChatMode) {
                            { textToInsert ->
                                HostAppAiFab.insertReplyIntoChatInput(hostActivity, textToInsert)
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Unhinge AI Response", textToInsert))
                                Toast.makeText(context, "Inserted reply into chat", Toast.LENGTH_SHORT).show()
                                dismissWithAnimation()
                            }
                        } else null,
                    )
                }
            }
        }

        if (totalCards > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until totalCards) {
                    val isSelected = pagerState.currentPage == i
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) actionBtnBg
                        else (if (isDark) Color(0xFF383838) else Color(0xFFD6D6D4)),
                        label = "pagerDotColor",
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) {
                                coroutineScope.launch { pagerState.animateScrollToPage(i) }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor),
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        SheetBottomBar(
            isChatMode = isChatMode,
            candidate = candidate,
            matchDisplayName = matchDisplayName,
            askAiQuery = askAiQuery,
            isAskAiGenerating = isAskAiGenerating,
            pillBg = pillBg,
            pillBorder = pillBorder,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            actionBtnBg = actionBtnBg,
            actionBtnText = actionBtnText,
            isDark = isDark,
            modernEraMedium = modernEraMedium,
            modernEraRegular = modernEraRegular,
            onQueryChange = { askAiQuery = it },
            onSubmitAskAi = { submitAskAi(it) },
        )
    }
}

private fun launchUnhingeSettings(context: Context) {
    try {
        val intent = context.packageManager.getLaunchIntentForPackage("io.github.s1ddhants1.unhinge")?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra("subpage", "ai_wingman")
        }
        if (intent != null) {
            context.startActivity(intent)
        } else {
            Toast.makeText(context, "Open Unhinge to configure AI settings", Toast.LENGTH_SHORT).show()
        }
    } catch (_: Exception) {
        Toast.makeText(context, "Open Unhinge to configure AI settings", Toast.LENGTH_SHORT).show()
    }
}

private fun openerProfileBlock(candidate: CachedCandidateProfile): String {
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
