package io.github.s1ddhants1.unhinge.hook.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.setSingletonImageLoaderFactory
import io.github.s1ddhants1.unhinge.ai.AiWingmanHelper
import io.github.s1ddhants1.unhinge.ai.PromptEntry
import io.github.s1ddhants1.unhinge.data.HostCandidateReader
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.ThemeMode
import io.github.s1ddhants1.unhinge.util.UnhingeImageLoader
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Authentic Hinge design tokens for surface colors and typography.
 */
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

/**
 * AI Wingman bottom sheet strictly focused on the active candidate:
 * - Editorial serif typography for prompt answers and candidate headlines
 * - Prompt cards styled like native Hinge Discover profile entries
 * - Integrated comment bubble for crafted conversation openers
 * - Capsule pill buttons and category selectors
 * - Clean header without duplicate handles, candidate switcher arrows, or redundant settings icon
 * - Zero emojis and zero haptic feedback
 */
@Composable
fun HostAppAiSheetContent(
    prefs: PreferencesManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    candidateList: List<CachedCandidateProfile>? = null,
    showDragHandle: Boolean = false,
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

    var loadedCandidate by remember { mutableStateOf(candidateList?.firstOrNull()) }
    var isLoadingCandidate by remember { mutableStateOf(candidateList == null) }

    LaunchedEffect(candidateList) {
        if (candidateList != null) {
            loadedCandidate = candidateList.firstOrNull()
            isLoadingCandidate = false
        } else {
            isLoadingCandidate = true
            val current = HostCandidateReader.readCurrentDiscoverCandidate(context)
                ?: HostCandidateReader.readActiveCandidates(context).firstOrNull()
            loadedCandidate = current
            isLoadingCandidate = false
        }
    }

    val candidate = loadedCandidate

    val tones = listOf(
        "Witty" to "Witty & Playful",
        "Flirty" to "Charming & Flirty",
        "Curious" to "Intellectual & Curious",
        "Bold" to "Sarcastic & Bold"
    )

    var currentTone by remember {
        mutableStateOf(prefs.aiResponseTone.ifBlank { "Witty & Playful" })
    }

    val status by AiWingmanHelper.status.collectAsState()

    // Map candidate prompt items to AI PromptEntry carriers
    val promptEntries = remember(candidate?.userId, candidate?.prompts) {
        val prompts = candidate?.prompts.orEmpty()
        if (prompts.isNotEmpty()) {
            prompts.map { PromptEntry(text = "${it.question}: ${it.answer}") }
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

    fun requestWingmanGeneration(targets: List<PromptEntry> = promptEntries) {
        val key = if (prefs.aiProvider == "DeepL") prefs.deeplApiKey else prefs.openRouterApiKey
        if (key.isBlank() || targets.isEmpty()) return
        AiWingmanHelper.generateReplies(
            prompts = targets,
            targetLanguage = "English",
            apiKey = prefs.openRouterApiKey,
            baseUrl = prefs.openRouterBaseUrl,
            model = prefs.openRouterModel,
            mode = currentTone,
            scope = coroutineScope,
            context = context,
            provider = prefs.aiProvider,
            deeplApiKey = prefs.deeplApiKey,
            deeplFormality = prefs.deeplFormality,
            useStreaming = true,
            candidateId = candidate?.userId.orEmpty(),
            systemPrompt = prefs.aiCustomSystemPrompt
        )
    }

    LaunchedEffect(candidate?.userId, prefs.openRouterApiKey, prefs.deeplApiKey, currentTone) {
        val key = if (prefs.aiProvider == "DeepL") prefs.deeplApiKey else prefs.openRouterApiKey
        if (key.isNotBlank() && promptEntries.isNotEmpty()) {
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

    val imageLoader = remember(context) { UnhingeImageLoader.get(context) }
    setSingletonImageLoaderFactory { ctx ->
        UnhingeImageLoader.get(ctx)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.92f)
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(sheetBg)
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        if (showDragHandle) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 2.dp, bottom = 12.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isDark) Color(0xFF383838) else Color(0xFFD6D6D4))
            )
        }

        // Native Hinge Header: Candidate Identity & Single Dismiss Control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (candidate != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
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
                                    fontFamily = FontFamily.Serif,
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
                                fontFamily = FontFamily.Serif,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (candidate.isSelfieVerified) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified Profile",
                                    tint = HingeDesignTokens.VerifiedBlue,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        val subtitle = listOfNotNull(
                            candidate.jobTitle.takeIf { it.isNotBlank() },
                            candidate.location.takeIf { it.isNotBlank() }
                        ).joinToString(" · ")

                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                fontFamily = FontFamily.SansSerif,
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
                    fontFamily = FontFamily.Serif,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary
                )
            }

            // Single clean close button
            Surface(
                shape = CircleShape,
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.size(36.dp)
            ) {
                IconButton(
                    onClick = onDismiss,
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

        Spacer(Modifier.height(14.dp))

        // Hinge Tone Selector Bar (Capsule Pill Tags)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tones.forEach { (label, toneValue) ->
                val isSelected = currentTone == toneValue
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) actionBtnBg else pillBg,
                    border = if (isSelected) null else BorderStroke(1.dp, pillBorder),
                    modifier = Modifier
                        .height(34.dp)
                        .clickable {
                            currentTone = toneValue
                            prefs.aiResponseTone = toneValue
                            requestWingmanGeneration()
                        }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = label,
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) actionBtnText else textSecondary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Loading State: Reading candidate from SQLite database
        if (isLoadingCandidate) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
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
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 14.sp,
                        color = textSecondary
                    )
                }
            }
            return@Column
        }

        // Empty State: No active candidate found
        if (candidate == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
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
                            fontFamily = FontFamily.Serif,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary
                        )
                        Text(
                            text = "Navigate to Hinge's Discover or Likes You feed, then tap the AI button to load openers for the active profile.",
                            fontFamily = FontFamily.SansSerif,
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

        // Setup Required Card: Missing API Token
        val activeApiKey = if (prefs.aiProvider == "DeepL") prefs.deeplApiKey else prefs.openRouterApiKey
        if (activeApiKey.isBlank()) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SETUP REQUIRED",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = textSecondary
                    )
                    Text(
                        text = "Connect your AI API token to craft tailored openers.",
                        fontFamily = FontFamily.Serif,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Medium,
                        color = textPrimary,
                        lineHeight = 25.sp
                    )
                    Text(
                        text = "Configure your ${prefs.aiProvider} token in Unhinge Settings to generate conversation starters for ${candidate.firstName.ifBlank { "this profile" }}.",
                        fontFamily = FontFamily.SansSerif,
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
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Candidate Prompt Cards Feed
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Case 1: Candidate has no text prompts (bio overview card)
            if (candidate.prompts.isEmpty()) {
                item(key = "overview_card") {
                    val fallbackEntry = promptEntries.firstOrNull()
                    val reply by (fallbackEntry?.suggestedReplyFlow?.collectAsState() ?: remember { mutableStateOf(null) })

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "ABOUT ${candidate.firstName.ifBlank { "CANDIDATE" }.uppercase()}",
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp,
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
                                fontFamily = FontFamily.Serif,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 26.sp,
                                color = textPrimary
                            )

                            NativeHingeCommentBubble(
                                replyText = reply,
                                status = status,
                                isCopied = copiedIndex == 0,
                                commentBg = commentBg,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                actionBtnBg = actionBtnBg,
                                actionBtnText = actionBtnText,
                                onCopy = { if (!reply.isNullOrBlank()) copyOpener(0, reply!!) },
                                onRegenerate = { fallbackEntry?.let { requestWingmanGeneration(listOf(it)) } },
                                onOpenSettings = { launchUnhingeSettings(context) }
                            )
                        }
                    }
                }
            } else {
                // Case 2: Candidate has prompts
                itemsIndexed(
                    items = candidate.prompts,
                    key = { _, item -> "${item.question}_${item.answer}" }
                ) { index, promptItem ->
                    val entry = promptEntries.getOrNull(index)
                    val reply by (entry?.suggestedReplyFlow?.collectAsState() ?: remember { mutableStateOf(null) })

                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = promptItem.question.uppercase(),
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.8.sp,
                                color = textSecondary
                            )

                            Text(
                                text = promptItem.answer,
                                fontFamily = FontFamily.Serif,
                                fontSize = 21.sp,
                                fontWeight = FontWeight.Normal,
                                lineHeight = 28.sp,
                                color = textPrimary
                            )

                            NativeHingeCommentBubble(
                                replyText = reply,
                                status = status,
                                isCopied = copiedIndex == index,
                                commentBg = commentBg,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                actionBtnBg = actionBtnBg,
                                actionBtnText = actionBtnText,
                                onCopy = { if (!reply.isNullOrBlank()) copyOpener(index, reply!!) },
                                onRegenerate = { entry?.let { requestWingmanGeneration(listOf(it)) } },
                                onOpenSettings = { launchUnhingeSettings(context) }
                            )
                        }
                    }
                }
            }
        }

        // Native Hinge Bottom Action Bar (Capsule Button)
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp)
        ) {
            val isGenerating = status is AiWingmanHelper.WingmanStatus.Generating

            Button(
                onClick = {
                    val key = if (prefs.aiProvider == "DeepL") prefs.deeplApiKey else prefs.openRouterApiKey
                    if (key.isBlank()) {
                        launchUnhingeSettings(context)
                        return@Button
                    }
                    requestWingmanGeneration()
                },
                enabled = !isGenerating,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = actionBtnBg,
                    contentColor = actionBtnText,
                    disabledContainerColor = actionBtnBg.copy(alpha = 0.6f),
                    disabledContentColor = actionBtnText.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = actionBtnText
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Crafting Openers...",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Generate All Openers",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Drafted comment box designed to look like Hinge's prompt comment input.
 */
@Composable
private fun NativeHingeCommentBubble(
    replyText: String?,
    status: AiWingmanHelper.WingmanStatus,
    isCopied: Boolean,
    commentBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    actionBtnBg: Color,
    actionBtnText: Color,
    onCopy: () -> Unit,
    onRegenerate: () -> Unit,
    onOpenSettings: () -> Unit
) {
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
                        text = "WINGMAN OPENER",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp,
                        color = textSecondary
                    )
                }

                if (!replyText.isNullOrBlank()) {
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

            if (!replyText.isNullOrBlank()) {
                Text(
                    text = replyText,
                    fontFamily = FontFamily.SansSerif,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = textPrimary,
                    modifier = Modifier.clickable { onCopy() }
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
                            .clickable { onCopy() }
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
                                text = if (isCopied) "Copied!" else "Copy Opener",
                                fontFamily = FontFamily.SansSerif,
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
                                fontFamily = FontFamily.SansSerif,
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
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            if (isAuthError) {
                                Text(
                                    text = "Tap here to update your token in Settings",
                                    fontFamily = FontFamily.SansSerif,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Tap 'Generate All Openers' below to craft conversation starters.",
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 13.sp,
                            color = textSecondary.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
