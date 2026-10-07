package io.github.s1ddhants1.unhinge.hook.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import kotlin.math.roundToInt
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import io.github.s1ddhants1.unhinge.ai.AiWingmanHelper
import io.github.s1ddhants1.unhinge.ai.OpenRouterStreamingService
import io.github.s1ddhants1.unhinge.ai.PromptEntry
import io.github.s1ddhants1.unhinge.ai.WingmanPrompts
import io.github.s1ddhants1.unhinge.data.HostCandidateReader
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.ui.theme.HingeFonts
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.ThemeMode
import io.github.s1ddhants1.unhinge.util.UnhingeImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private object HingeDesignTokens {
    val LightBg = Color(0xFFF9F9F8)
    val LightCard = Color(0xFFFFFFFF)
    val LightBorder = Color(0xFFEBEBEA)
    val LightComment = Color(0xFFF4F4F2)
    val LightTextPrimary = Color(0xFF161616)
    val LightTextSecondary = Color(0xFF757575)
    val LightPillUnselected = Color(0xFFF0F0EE)
    val LightPillBorder = Color(0xFFE0E0DC)
    val LightPillSelected = Color(0xFF161616)

    val DarkBg = Color(0xFF141414)
    val DarkCard = Color(0xFF1E1E1E)
    val DarkBorder = Color(0xFF2B2B2B)
    val DarkComment = Color(0xFF262626)
    val DarkTextPrimary = Color(0xFFF4F4F4)
    val DarkTextSecondary = Color(0xFF949494)
    val DarkPillUnselected = Color(0xFF242424)
    val DarkPillBorder = Color(0xFF333333)
    val DarkPillSelected = Color(0xFFFFFFFF)

    val VerifiedBlue = Color(0xFF2B82EB)
    val SuccessGreen = Color(0xFF2E7D32)
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
    val habits = listOf(
        candidate.smoking.takeIf { it.isNotBlank() }?.let { "Smoking: $it" },
        candidate.drinking.takeIf { it.isNotBlank() }?.let { "Drinking: $it" },
        candidate.marijuana.takeIf { it.isNotBlank() }?.let { "Marijuana: $it" },
    ).filterNotNull().joinToString(", ")
    return listOfNotNull(
        buildString {
            append(candidate.firstName.ifBlank { "Candidate" })
            if (candidate.age > 0) append(", ${candidate.age}")
        }.takeIf { candidate.firstName.isNotBlank() || candidate.age > 0 },
        work.takeIf { it.isNotBlank() }?.let { "Work: $it" },
        candidate.school.takeIf { it.isNotBlank() }?.let { "School: $it" },
        candidate.location.takeIf { it.isNotBlank() }?.let { "Location: $it" },
        candidate.hometown.takeIf { it.isNotBlank() }?.let { "Hometown: $it" },
        candidate.datingIntention.takeIf { it.isNotBlank() }?.let { "Dating intention: $it" },
        candidate.relationshipType.takeIf { it.isNotBlank() }?.let { "Relationship type: $it" },
        habits.takeIf { it.isNotBlank() },
        candidate.pet.takeIf { it.isNotBlank() }?.let { "Pet: $it" },
        candidate.zodiac.takeIf { it.isNotBlank() }?.let { "Zodiac: $it" },
        "Just joined Hinge".takeIf { candidate.isNewHere },
    ).joinToString("\n")
}

data class CustomAiInteraction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val query: String,
    val reply: String = "",
    val isStreaming: Boolean = true,
    val error: String? = null,
)

@Composable
fun HostAppAiSheetContent(
    prefs: PreferencesManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    candidateList: List<CachedCandidateProfile>? = null,
    screenClues: HostCandidateReader.ScreenClues? = null,
    showDragHandle: Boolean = false,
    standalone: Boolean = true,
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
    var isLoadingCandidate by remember { mutableStateOf(candidateList == null) }

    LaunchedEffect(candidateList, screenClues) {
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

    val candidate = loadedCandidate

    val status by AiWingmanHelper.status.collectAsState()

    val promptEntries = remember(candidate?.userId, candidate?.prompts) {
        val prompts = candidate?.prompts.orEmpty()
        if (prompts.isNotEmpty()) {
            prompts.map { PromptEntry(text = "${it.question}: ${it.answer.replace("\\n", "\n")}") }
        } else if (candidate != null) {
            val bio = listOfNotNull(
                candidate.jobTitle.takeIf { it.isNotBlank() }?.let { "Job: $it" },
                candidate.location.takeIf { it.isNotBlank() }?.let { "Location: $it" },
                candidate.datingIntention.takeIf { it.isNotBlank() }?.let { "Dating Intentions: $it" }
            ).joinToString(", ")
            if (bio.isNotBlank()) listOf(PromptEntry(text = bio)) else emptyList()
        } else {
            emptyList()
        }
    }

    val isApiKeyRequired = AiWingmanHelper.isApiKeyRequired(prefs.aiProvider, prefs.openRouterModel)
    val isAiReady = !isApiKeyRequired || prefs.openRouterApiKey.isNotBlank()

    fun requestWingmanGeneration(
        targets: List<PromptEntry> = promptEntries,
        forceRefresh: Boolean = false
    ) {
        if (!isAiReady || targets.isEmpty()) return
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
        )
    }

    LaunchedEffect(candidate?.userId, prefs.openRouterApiKey, prefs.aiProvider, prefs.openRouterModel) {
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

    val promptCardsCount = remember(candidate?.userId, candidate?.prompts, promptEntries.size) {
        if (candidate?.prompts.isNullOrEmpty()) {
            if (promptEntries.isNotEmpty()) 1 else 0
        } else {
            candidate.prompts.size
        }
    }
    val totalCards = (promptCardsCount + customInteractions.size).coerceAtLeast(1)
    val pagerState = rememberPagerState(initialPage = 0) { totalCards }

    LaunchedEffect(candidate?.userId) {
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
            if (candidate != null) {
                append("Name: ${candidate.firstName.ifBlank { "Candidate" }}")
                if (candidate.age > 0) append(", Age: ${candidate.age}")
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
                        append("\n${i + 1}. ${p.question}: ${p.answer.replace("\\n", "\n")}")
                    }
                }
            }
        }

        val systemPrompt = WingmanPrompts.askAiSystemPrompt(
            profileSummary = candidateProfileSummary,
            custom = prefs.aiCustomSystemPrompt,
            prefs = prefs,
            context = context,
        )

        coroutineScope.launch {
            try {
                val contentAccumulator = StringBuilder()
                AiWingmanHelper.streamCustomChat(
                    userPrompt = query,
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
                                    isStreaming = true
                                )
                            }
                        }
                        is OpenRouterStreamingService.ChatStreamChunk.Complete -> {
                            val idx = customInteractions.indexOfFirst { it.id == newInteraction.id }
                            if (idx != -1) {
                                customInteractions[idx] = customInteractions[idx].copy(
                                    reply = chunk.fullText.ifBlank { contentAccumulator.toString() },
                                    isStreaming = false
                                )
                            }
                        }
                        is OpenRouterStreamingService.ChatStreamChunk.Error -> {
                            val idx = customInteractions.indexOfFirst { it.id == newInteraction.id }
                            if (idx != -1) {
                                customInteractions[idx] = customInteractions[idx].copy(
                                    error = chunk.message,
                                    isStreaming = false
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
                        isStreaming = false
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
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
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
                    animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                )
                onDismiss()
            } else {
                offsetY.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
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
        }
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
                source: NestedScrollSource
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
        if (showDragHandle && standalone) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(28.dp)
                    .padding(horizontal = 18.dp)
                    .then(dragModifier),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isDark) Color(0xFF383838) else Color(0xFFD6D6D4))
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (candidate != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .then(dragModifier)
                ) {
                    val photoUrl = candidate.photos.firstOrNull()
                    if (photoUrl != null) {
                        SubcomposeAsyncImage(
                            model = photoUrl,
                            imageLoader = imageLoader,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(cardBg)
                        )
                    } else {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = candidate.firstName.take(1).ifBlank { "?" },
                                    fontFamily = modernEraBold,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            }
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val title = buildString {
                                append(candidate.firstName.ifBlank { "Candidate" })
                                if (candidate.age > 0) {
                                    append(", ${candidate.age}")
                                }
                            }
                            Text(
                                text = title,
                                fontFamily = modernEraBold,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        val subtitle = listOfNotNull(
                            candidate.jobTitle.takeIf { it.isNotBlank() },
                            candidate.location.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")

                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                fontFamily = modernEraRegular,
                                fontSize = 13.sp,
                                color = textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = "AI Wingman",
                    fontFamily = modernEraBold,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .then(dragModifier)
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (prefs.showAvailableLikes) {
                    var likesState by remember { mutableStateOf(HostLikesReader.getLikesInfo(context)) }
                    DisposableEffect(context) {
                        val observer: (HostLikesReader.LikesState) -> Unit = { updated ->
                            likesState = updated
                        }
                        HostLikesReader.registerObserver(context, observer)
                        onDispose {
                            HostLikesReader.unregisterObserver(observer)
                        }
                    }

                    val heartTint = when {
                        likesState.availableLikes == 0 -> Color(0xFFE53935)
                        likesState.availableLikes in 1..2 -> Color(0xFFFFA000)
                        else -> Color(0xFFED5564)
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier
                            .height(36.dp)
                            .clickable {
                                Toast.makeText(context, likesState.formattedSummary, Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Icon(
                                imageVector = HingeIcons.HeartVector,
                                contentDescription = "Available Likes",
                                tint = heartTint,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = likesState.displayLikes,
                                fontFamily = modernEraBold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = if (likesState.availableLikes == 0) Color(0xFFE53935) else textPrimary
                            )
                            if (likesState.availableSuperlikes > 0) {
                                Text(
                                    text = "•",
                                    fontSize = 11.sp,
                                    color = textSecondary
                                )
                                Icon(
                                    imageVector = HingeIcons.RoseVector,
                                    contentDescription = "Available Roses",
                                    tint = Color(0xFFF06292),
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = likesState.displaySuperlikes,
                                    fontFamily = modernEraBold,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = textPrimary
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(
                        onClick = { dismissWithAnimation() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (isLoadingCandidate) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp)
                    .then(dragModifier),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp,
                        color = actionBtnBg
                    )
                    Text(
                        text = "Reading active candidate...",
                        fontFamily = modernEraRegular,
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                }
            }
            return@Column
        }

        if (candidate == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp)
                    .then(dragModifier),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PersonSearch,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = textSecondary
                        )
                        Text(
                            text = "No candidate on screen",
                            fontFamily = modernEraBold,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "Navigate to Hinge's Discover or Likes You feed, then tap the AI button to load openers for the active profile.",
                            fontFamily = modernEraRegular,
                            fontSize = 13.5.sp,
                            color = textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 19.sp
                        )
                    }
                }
            }
            return@Column
        }

        if (!isAiReady) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 18.dp, bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Setup required",
                        fontFamily = modernEraBold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textSecondary
                    )
                    Text(
                        text = "Connect your AI API token to craft tailored openers.",
                        fontFamily = modernEraBold,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                        lineHeight = 24.sp
                    )
                    Text(
                        text = if (prefs.aiProvider.equals("Zen", ignoreCase = true)) {
                            "An API key is required for paid model ${prefs.openRouterModel}. Switch to a free model (e.g. muse-spark-1.3-contributor-free) or configure your key in Settings."
                        } else {
                            "Configure your ${prefs.aiProvider} token in Unhinge Settings to generate conversation starters for ${candidate.firstName.ifBlank { "this profile" }}."
                        },
                        fontFamily = modernEraRegular,
                        fontSize = 13.sp,
                        color = textSecondary,
                        lineHeight = 18.sp
                    )
                    Button(
                        onClick = { launchUnhingeSettings(context) },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = actionBtnBg,
                            contentColor = actionBtnText
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        Text(
                            text = "Configure in Settings",
                            fontFamily = modernEraMedium,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 28.dp),
            pageSpacing = 14.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (page < promptCardsCount) {
                if (candidate.prompts.isEmpty()) {

                    val fallbackEntry = promptEntries.firstOrNull()
                    val reply by (fallbackEntry?.suggestedReplyFlow?.collectAsState() ?: remember { mutableStateOf(null) })

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cardBg,
                        border = BorderStroke(
                            width = if (pagerState.currentPage == page) 1.5.dp else 1.dp,
                            color = if (pagerState.currentPage == page) (if (isDark) Color(0xFF4A4A4A) else Color(0xFFC0C0BE)) else cardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(18.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "About ${candidate.firstName.ifBlank { "Candidate" }}",
                                    fontFamily = modernEraBold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textSecondary
                                )

                                val bioText = listOfNotNull(
                                    candidate.jobTitle.takeIf { it.isNotBlank() }?.let { "Works as $it" },
                                    candidate.school.takeIf { it.isNotBlank() }?.let { "Studied at $it" },
                                    candidate.hometown.takeIf { it.isNotBlank() }?.let { "From $it" },
                                    candidate.location.takeIf { it.isNotBlank() }?.let { "Lives in $it" },
                                    candidate.datingIntention.takeIf { it.isNotBlank() }?.let { "Looking for $it" }
                                ).joinToString("\n")

                                Text(
                                    text = bioText.ifBlank { "Active candidate in Hinge feed" },
                                    fontFamily = tiemposRegular,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Normal,
                                    lineHeight = 32.sp,
                                    color = textPrimary
                                )
                            }

                            Spacer(Modifier.height(14.dp))

                            NativeHingeCommentBubble(
                                entry = fallbackEntry,
                                replyText = reply,
                                status = status,
                                isCopied = copiedIndex == 0,
                                commentBg = commentBg,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                actionBtnBg = actionBtnBg,
                                actionBtnText = actionBtnText,
                                onCopy = { copyOpener(0, it) },
                                onRegenerate = { fallbackEntry?.let { requestWingmanGeneration(listOf(it), forceRefresh = true) } },
                                onOpenSettings = { launchUnhingeSettings(context) }
                            )
                        }
                    }
                } else {

                    val promptItem = candidate.prompts.getOrNull(page)
                    val entry = promptEntries.getOrNull(page)
                    val reply by (entry?.suggestedReplyFlow?.collectAsState() ?: remember { mutableStateOf(null) })

                    if (promptItem != null) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = cardBg,
                            border = BorderStroke(
                                width = if (pagerState.currentPage == page) 1.5.dp else 1.dp,
                                color = if (pagerState.currentPage == page) (if (isDark) Color(0xFF4A4A4A) else Color(0xFFC0C0BE)) else cardBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(18.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Prompt ${page + 1} of $promptCardsCount",
                                            fontFamily = modernEraBold,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = textSecondary
                                        )

                                        Surface(
                                            shape = RoundedCornerShape(50),
                                            color = pillBg,
                                            border = BorderStroke(1.dp, pillBorder),
                                            modifier = Modifier.clickable {
                                                submitAskAi("Focus on her prompt \"${promptItem.question}\": \"${promptItem.answer}\" and give me a sharp, funny angle.")
                                            }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = textSecondary,
                                                    modifier = Modifier.size(11.dp)
                                                )
                                                Text(
                                                    text = "Ask AI",
                                                    fontFamily = modernEraMedium,
                                                    fontSize = 11.sp,
                                                    color = textSecondary
                                                )
                                            }
                                        }
                                    }

                                    Text(
                                        text = promptItem.question,
                                        fontFamily = modernEraBold,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = textPrimary
                                    )

                                    Text(
                                        text = promptItem.answer.replace("\\n", "\n"),
                                        fontFamily = tiemposRegular,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Normal,
                                        lineHeight = 32.sp,
                                        color = textPrimary
                                    )
                                }

                                Spacer(Modifier.height(14.dp))

                                NativeHingeCommentBubble(
                                    entry = entry,
                                    replyText = reply,
                                    status = status,
                                    isCopied = copiedIndex == page,
                                    commentBg = commentBg,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    actionBtnBg = actionBtnBg,
                                    actionBtnText = actionBtnText,
                                    onCopy = { copyOpener(page, it) },
                                    onRegenerate = { entry?.let { requestWingmanGeneration(listOf(it), forceRefresh = true) } },
                                    onOpenSettings = { launchUnhingeSettings(context) }
                                )
                            }
                        }
                    }
                }
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
                        }
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
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until totalCards) {
                    val isSelected = pagerState.currentPage == i
                    val dotColor by animateColorAsState(
                        targetValue = if (isSelected) actionBtnBg
                        else (if (isDark) Color(0xFF383838) else Color(0xFFD6D6D4)),
                        label = "pagerDotColor"
                    )
                    Box(
                        modifier = Modifier
                            .size(width = 12.dp, height = 16.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                coroutineScope.launch { pagerState.animateScrollToPage(i) }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionChip(
                label = "Vibe check",
                bg = pillBg,
                border = pillBorder,
                textColor = textPrimary,
                font = modernEraMedium
            ) {
                val candidateName = candidate.firstName.ifBlank { "her" }
                submitAskAi("Give me a sharp, 2-sentence vibe check and best conversational angle for $candidateName.")
            }

            QuickActionChip(
                label = "Playful roast",
                bg = pillBg,
                border = pillBorder,
                textColor = textPrimary,
                font = modernEraMedium
            ) {
                submitAskAi("Give me a light, teasing roast about one of her prompt answers that invites a laugh.")
            }

            QuickActionChip(
                label = "First date pitch",
                bg = pillBg,
                border = pillBorder,
                textColor = textPrimary,
                font = modernEraMedium
            ) {
                val candidateName = candidate.firstName.ifBlank { "her" }
                submitAskAi("Suggest a low-pressure, tailored first date idea based on $candidateName's bio and interests.")
            }

            QuickActionChip(
                label = "Hidden hook",
                bg = pillBg,
                border = pillBorder,
                textColor = textPrimary,
                font = modernEraMedium
            ) {
                val candidateName = candidate.firstName.ifBlank { "her" }
                submitAskAi("Spot an understated, witty detail in $candidateName's answers that most matches miss.")
            }

            QuickActionChip(
                label = "Green & red flags",
                bg = pillBg,
                border = pillBorder,
                textColor = textPrimary,
                font = modernEraMedium
            ) {
                val candidateName = candidate.firstName.ifBlank { "her" }
                submitAskAi("Highlight 2 major green flags and any subtle friction points in $candidateName's profile.")
            }
        }

        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (isDark) Color(0xFF242424) else Color(0xFFEFEFEF))
                    .border(
                        BorderStroke(1.dp, if (isDark) Color(0xFF383838) else Color(0xFFE0E0DE)),
                        RoundedCornerShape(26.dp)
                    )
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Ask AI",
                    tint = if (askAiQuery.isNotBlank() || isAskAiGenerating) actionBtnBg else textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(10.dp))

                BasicTextField(
                    value = askAiQuery,
                    onValueChange = { askAiQuery = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    textStyle = TextStyle(
                        fontFamily = modernEraRegular,
                        fontSize = 14.5.sp,
                        color = textPrimary
                    ),
                    singleLine = false,
                    maxLines = 3,
                    cursorBrush = SolidColor(actionBtnBg),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { submitAskAi() }
                    ),
                    decorationBox = { innerTextField ->
                        if (askAiQuery.isEmpty()) {
                            Text(
                                text = "Ask AI",
                                fontFamily = modernEraRegular,
                                fontSize = 14.5.sp,
                                color = textSecondary.copy(alpha = 0.7f)
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(Modifier.width(6.dp))

                Surface(
                    shape = CircleShape,
                    color = if (askAiQuery.isNotBlank() && !isAskAiGenerating) actionBtnBg else Color.Transparent,
                    modifier = Modifier.size(34.dp)
                ) {
                    IconButton(
                        onClick = { submitAskAi() },
                        enabled = askAiQuery.isNotBlank() && !isAskAiGenerating,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isAskAiGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = actionBtnBg
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = if (askAiQuery.isNotBlank()) actionBtnText else textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomAiResponseCard(
    interaction: CustomAiInteraction,
    index: Int,
    isSelected: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    actionBtnBg: Color,
    actionBtnText: Color,
    commentBg: Color,
    pillBg: Color,
    pillBorder: Color,
    isDark: Boolean = false,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
    onRefine: (String) -> Unit,
) {
    val context = LocalContext.current
    val modernEraRegular = remember(context) { HingeFonts.modernEraRegular(context) }
    val modernEraMedium = remember(context) { HingeFonts.modernEraMedium(context) }
    val modernEraBold = remember(context) { HingeFonts.modernEraBold(context) }

    var isCopied by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) (if (isDark) Color(0xFF4A4A4A) else Color(0xFFC0C0BE)) else cardBorder
        ),
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = actionBtnBg,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Ask AI #$index",
                            fontFamily = modernEraBold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = actionBtnBg
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(24.dp)
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = textSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = commentBg.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "“${interaction.query}”",
                        fontFamily = modernEraMedium,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        color = textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }

                when {
                    interaction.error != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = interaction.error,
                                fontFamily = modernEraRegular,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            TextButton(
                                onClick = onRegenerate,
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(
                                    text = "Retry",
                                    fontFamily = modernEraMedium,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = actionBtnBg
                                )
                            }
                        }
                    }
                    interaction.isStreaming && interaction.reply.isBlank() -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 6.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = actionBtnBg
                            )
                            Text(
                                text = "Crafting custom response...",
                                fontFamily = modernEraRegular,
                                fontSize = 13.5.sp,
                                color = textSecondary
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = interaction.reply,
                            fontFamily = modernEraMedium,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 22.sp,
                            color = textPrimary
                        )
                    }
                }
            }

            if (interaction.reply.isNotBlank() && interaction.error == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {

                    if (!interaction.isStreaming) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            RefinePill("Shorter", pillBg, pillBorder, textPrimary, modernEraMedium) {
                                onRefine("Make this shorter and under 12 words: \"${interaction.reply}\"")
                            }
                            RefinePill("Bolder", pillBg, pillBorder, textPrimary, modernEraMedium) {
                                onRefine("Make this bolder, flirtier, and more playful: \"${interaction.reply}\"")
                            }
                            RefinePill("Teasing", pillBg, pillBorder, textPrimary, modernEraMedium) {
                                onRefine("Add playful teasing and dry humor to this: \"${interaction.reply}\"")
                            }
                            RefinePill("Date pitch", pillBg, pillBorder, textPrimary, modernEraMedium) {
                                onRefine("Convert this into a smooth, low-pressure first date invitation: \"${interaction.reply}\"")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isCopied) HingeDesignTokens.VerifiedBlue else actionBtnBg,
                            modifier = Modifier
                                .height(34.dp)
                                .clickable(enabled = interaction.reply.isNotBlank()) {
                                    onCopy(interaction.reply)
                                    isCopied = true
                                    coroutineScope.launch {
                                        delay(2000)
                                        isCopied = false
                                    }
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = actionBtnText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = if (isCopied) "Copied!" else "Copy",
                                    fontFamily = modernEraMedium,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = actionBtnText
                                )
                            }
                        }

                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionChip(
    label: String,
    bg: Color,
    border: Color,
    textColor: Color,
    font: FontFamily,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontFamily = font,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun RefinePill(
    label: String,
    bg: Color,
    border: Color,
    textColor: Color,
    font: FontFamily,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = label,
            fontFamily = font,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun NativeHingeCommentBubble(
    entry: PromptEntry?,
    replyText: String?,
    status: AiWingmanHelper.WingmanStatus,
    isCopied: Boolean,
    commentBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    actionBtnBg: Color,
    actionBtnText: Color,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val context = LocalContext.current
    val modernEraRegular = remember(context) { HingeFonts.modernEraRegular(context) }
    val modernEraMedium = remember(context) { HingeFonts.modernEraMedium(context) }
    val modernEraBold = remember(context) { HingeFonts.modernEraBold(context) }

    val replies by (entry?.repliesFlow?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val activeIndex by (entry?.activeReplyIndexFlow?.collectAsState() ?: remember { mutableIntStateOf(0) })
    val isEntryGenerating by (entry?.isGeneratingFlow?.collectAsState() ?: remember { mutableStateOf(false) })

    val activeReply = replies.getOrNull(activeIndex) ?: replyText

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = commentBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = textSecondary
                    )
                    Text(
                        text = "Wingman opener",
                        fontFamily = modernEraBold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (replies.size > 1) {
                        IconButton(
                            onClick = { entry?.selectPreviousReply() },
                            enabled = activeIndex > 0,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = "Previous opener",
                                modifier = Modifier.size(16.dp),
                                tint = if (activeIndex > 0) textPrimary else textSecondary.copy(alpha = 0.3f)
                            )
                        }

                        Text(
                            text = "${activeIndex + 1} of ${replies.size}",
                            fontFamily = modernEraMedium,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary
                        )

                        IconButton(
                            onClick = { entry?.selectNextReply() },
                            enabled = activeIndex < replies.size - 1,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = "Next opener",
                                modifier = Modifier.size(16.dp),
                                tint = if (activeIndex < replies.size - 1) textPrimary else textSecondary.copy(alpha = 0.3f)
                            )
                        }

                        Spacer(Modifier.width(2.dp))
                    }

                    if (isEntryGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp),
                            strokeWidth = 1.8.dp,
                            color = actionBtnBg
                        )
                    } else if (!activeReply.isNullOrBlank() || replies.isNotEmpty()) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate opener",
                                modifier = Modifier.size(15.dp),
                                tint = textSecondary
                            )
                        }
                    }
                }
            }

            if (!activeReply.isNullOrBlank()) {
                Text(
                    text = activeReply,
                    fontFamily = modernEraMedium,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = textPrimary,
                    modifier = Modifier.clickable { onCopy(activeReply) }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val btnBg = if (isCopied) HingeDesignTokens.SuccessGreen else actionBtnBg
                    val btnContent = if (isCopied) Color.White else actionBtnText

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = btnBg,
                        modifier = Modifier
                            .height(34.dp)
                            .clickable { onCopy(activeReply) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            Icon(
                                imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = null,
                                tint = btnContent,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (isCopied) "Copied!" else "Copy",
                                fontFamily = modernEraMedium,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = btnContent
                            )
                        }
                    }
                }
            } else {
                when (status) {
                    is AiWingmanHelper.WingmanStatus.Generating -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.8.dp,
                                color = textSecondary
                            )
                            Text(
                                text = "Crafting reply...",
                                fontFamily = modernEraRegular,
                                fontSize = 13.sp,
                                color = textSecondary
                            )
                        }
                    }
                    is AiWingmanHelper.WingmanStatus.Error -> {
                        val errorMsg = status.message
                        val isAuthError = errorMsg.contains("auth", ignoreCase = true) ||
                                errorMsg.contains("API key", ignoreCase = true) ||
                                errorMsg.contains("401", ignoreCase = true)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isAuthError) Modifier.clickable { onOpenSettings() } else Modifier)
                        ) {
                            Text(
                                text = errorMsg,
                                fontFamily = modernEraRegular,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (isAuthError) {
                                Text(
                                    text = "Tap here to update your token in Settings",
                                    fontFamily = modernEraBold,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Ask AI below or select a tone to craft conversation starters.",
                            fontFamily = modernEraRegular,
                            fontSize = 13.sp,
                            color = textSecondary.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
