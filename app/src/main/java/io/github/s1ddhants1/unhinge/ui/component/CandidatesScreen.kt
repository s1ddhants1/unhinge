package io.github.s1ddhants1.unhinge.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import io.github.s1ddhants1.unhinge.R
import io.github.s1ddhants1.unhinge.data.CandidateArchiveDb
import io.github.s1ddhants1.unhinge.hook.ui.HingeIcons
import io.github.s1ddhants1.unhinge.hook.ui.HostAppAiSheetContent
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.model.CandidatePromptItem
import io.github.s1ddhants1.unhinge.ui.theme.AccentGold
import io.github.s1ddhants1.unhinge.ui.theme.HingeFonts
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

private val HingePlum = Color(0xFF682960)

enum class CandidateSortOption(val labelRes: Int) {
    ACTIVE_STATUS(R.string.sort_active_status),
    RECENT(R.string.sort_recent),
    NAME(R.string.sort_name),
    AGE(R.string.sort_age)
}

fun List<CachedCandidateProfile>.sortedByActiveStatus(): List<CachedCandidateProfile> {
    return sortedWith(
        compareBy<CachedCandidateProfile> { candidate ->
            when (candidate.lastActiveStatusId) {
                1 -> 0
                2 -> 1
                else -> 2
            }
        }.thenByDescending { it.isLiveInFeed }
         .thenByDescending { it.lastSeenTimestamp }
    )
}

enum class CandidateFilter(
    val labelRes: Int,
    val icon: ImageVector? = null
) {
    IN_FEED(R.string.filter_in_feed, Icons.Default.DynamicFeed),
    DISCOVER(R.string.filter_discover, Icons.Default.Explore),
    STANDOUTS(R.string.filter_standouts, Icons.Default.Star),
    LIKED_YOU(R.string.filter_liked_you, HingeIcons.HeartVector),
    SAVED(R.string.filter_saved, Icons.Default.Bookmark),
    ACTIVE_NOW(R.string.filter_active_now, Icons.Default.FiberManualRecord),
    ACTIVE_TODAY(R.string.filter_active_today, Icons.Default.AccessTime),
    WITH_COMMENT(R.string.filter_with_comment, Icons.AutoMirrored.Filled.Comment),
    LIKED(R.string.filter_liked, Icons.Default.ThumbUp),
    PASSED(R.string.filter_passed, Icons.Default.ThumbDown),
    ALL(R.string.filter_all, Icons.Default.People),
    ARCHIVED(R.string.filter_archived_past, Icons.Default.Archive);

    fun matches(candidate: CachedCandidateProfile): Boolean = when (this) {
        IN_FEED -> candidate.isLiveInFeed
        ALL -> true
        DISCOVER -> candidate.isDiscover || (candidate.isLiveInFeed && !candidate.isStandout)
        STANDOUTS -> candidate.isStandout
        ACTIVE_NOW -> candidate.lastActiveStatusId == 1
        ACTIVE_TODAY -> candidate.lastActiveStatusId == 2
        LIKED_YOU -> candidate.isIncomingLike
        WITH_COMMENT -> candidate.likeComment.isNotBlank()
        SAVED -> candidate.isSaved
        LIKED -> candidate.ratingStatus.equals("Liked", ignoreCase = true)
        PASSED -> candidate.ratingStatus.equals("Passed", ignoreCase = true)
        ARCHIVED -> !candidate.isLiveInFeed
    }
}

sealed class LikeTarget {
    abstract val candidate: CachedCandidateProfile

    data class Profile(override val candidate: CachedCandidateProfile) : LikeTarget()
    data class Photo(
        override val candidate: CachedCandidateProfile,
        val photoUrl: String,
        val index: Int
    ) : LikeTarget()
    data class Prompt(
        override val candidate: CachedCandidateProfile,
        val prompt: CandidatePromptItem
    ) : LikeTarget()
}

data class CandidateUndoAction(
    val candidate: CachedCandidateProfile,
    val previousStatus: String,
    val previousComment: String,
    val candidateIndex: Int
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatesScreen(
    candidates: List<CachedCandidateProfile>,
    prefs: PreferencesManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val candidateFallbackName = stringResource(R.string.candidate_fallback_name)

    var selectedFilter by rememberSaveable { mutableStateOf(CandidateFilter.IN_FEED) }
    var selectedSort by rememberSaveable { mutableStateOf(CandidateSortOption.ACTIVE_STATUS) }
    var currentIndex by rememberSaveable { mutableIntStateOf(0) }
    var isNavigatingBackwards by remember { mutableStateOf(false) }

    val undoHistory = remember { mutableStateListOf<CandidateUndoAction>() }
    var candidateOverrides by remember { mutableStateOf<Map<String, CachedCandidateProfile>>(emptyMap()) }

    var activeLikeTarget by remember { mutableStateOf<LikeTarget?>(null) }
    var selectedAiCandidate by remember { mutableStateOf<CachedCandidateProfile?>(null) }
    var fullscreenPhotoState by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }
    var showJumpSheet by remember { mutableStateOf(false) }
    var showFilterSheet by remember { mutableStateOf(false) }
    var sortDropdownExpanded by remember { mutableStateOf(false) }

    val effectiveCandidates = remember(candidates, candidateOverrides) {
        if (candidateOverrides.isEmpty()) candidates
        else candidates.map { c -> candidateOverrides[c.userId] ?: c }
    }

    val db = remember { CandidateArchiveDb(context) }

    val filtered = remember(effectiveCandidates, selectedFilter) {
        effectiveCandidates.filter { selectedFilter.matches(it) }
    }

    val sortedCandidates = remember(filtered, selectedSort) {
        when (selectedSort) {
            CandidateSortOption.ACTIVE_STATUS -> filtered.sortedByActiveStatus()
            CandidateSortOption.RECENT -> filtered.sortedWith(
                compareByDescending<CachedCandidateProfile> { it.isLiveInFeed }
                    .thenByDescending { it.lastSeenTimestamp }
            )
            CandidateSortOption.NAME -> filtered.sortedBy { it.firstName.lowercase() }
            CandidateSortOption.AGE -> filtered.sortedWith(
                compareBy<CachedCandidateProfile> { if (it.age > 0) it.age else 999 }
            )
        }
    }

    LaunchedEffect(sortedCandidates.size) {
        if (sortedCandidates.isNotEmpty() && currentIndex >= sortedCandidates.size) {
            currentIndex = sortedCandidates.size - 1
        }
    }

    fun onPassCandidate(candidate: CachedCandidateProfile) {
        isNavigatingBackwards = false
        undoHistory.add(
            CandidateUndoAction(
                candidate = candidate,
                previousStatus = candidate.ratingStatus,
                previousComment = candidate.likeComment,
                candidateIndex = currentIndex
            )
        )
        db.updateRating(candidate.userId, "Passed")
        val updated = candidate.copy(ratingStatus = "Passed")
        candidateOverrides = candidateOverrides + (candidate.userId to updated)

        Toast.makeText(
            context,
            context.getString(R.string.discover_toast_passed, candidate.firstName.ifBlank { candidateFallbackName }),
            Toast.LENGTH_SHORT
        ).show()

        if (currentIndex < sortedCandidates.size) {
            currentIndex++
        }
    }

    fun onLikeCandidate(candidate: CachedCandidateProfile, comment: String = "") {
        isNavigatingBackwards = false
        undoHistory.add(
            CandidateUndoAction(
                candidate = candidate,
                previousStatus = candidate.ratingStatus,
                previousComment = candidate.likeComment,
                candidateIndex = currentIndex
            )
        )
        db.updateRating(candidate.userId, "Liked", comment)
        val updated = candidate.copy(
            ratingStatus = "Liked",
            likeComment = if (comment.isNotBlank()) comment else candidate.likeComment
        )
        candidateOverrides = candidateOverrides + (candidate.userId to updated)

        Toast.makeText(
            context,
            context.getString(R.string.discover_toast_liked, candidate.firstName.ifBlank { candidateFallbackName }),
            Toast.LENGTH_SHORT
        ).show()

        if (currentIndex < sortedCandidates.size) {
            currentIndex++
        }
    }

    fun onUndoAction() {
        if (undoHistory.isEmpty()) return
        isNavigatingBackwards = true
        val lastAction = undoHistory.removeAt(undoHistory.size - 1)
        db.updateRating(lastAction.candidate.userId, lastAction.previousStatus, lastAction.previousComment)

        val reverted = lastAction.candidate.copy(
            ratingStatus = lastAction.previousStatus,
            likeComment = lastAction.previousComment
        )
        candidateOverrides = candidateOverrides + (reverted.userId to reverted)

        currentIndex = lastAction.candidateIndex.coerceIn(0, maxOf(0, sortedCandidates.size - 1))
        Toast.makeText(context, context.getString(R.string.discover_toast_undone), Toast.LENGTH_SHORT).show()
    }

    fun onToggleSave(candidate: CachedCandidateProfile) {
        val newState = db.toggleSaved(candidate.userId)
        val updated = candidate.copy(isSaved = newState)
        candidateOverrides = candidateOverrides + (candidate.userId to updated)
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            HingeDiscoverTopBar(
                selectedFilter = selectedFilter,
                selectedSort = selectedSort,
                currentIndex = currentIndex,
                totalCount = sortedCandidates.size,
                onFilterClick = { showFilterSheet = true },
                onSearchJumpClick = { showJumpSheet = true },
                onSortSelected = { selectedSort = it },
                sortDropdownExpanded = sortDropdownExpanded,
                onSortDropdownChange = { sortDropdownExpanded = it }
            )

            if (sortedCandidates.isEmpty() || currentIndex >= sortedCandidates.size) {
                DiscoverCaughtUpView(
                    filter = selectedFilter,
                    totalCandidatesCount = effectiveCandidates.size,
                    hasPassedHistory = undoHistory.isNotEmpty(),
                    onUndo = { onUndoAction() },
                    onStartOver = { currentIndex = 0 },
                    onSwitchFilter = { newFilter ->
                        selectedFilter = newFilter
                        currentIndex = 0
                    }
                )
            } else {
                val currentCandidate = sortedCandidates[currentIndex]

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    AnimatedContent(
                        targetState = currentCandidate,
                        transitionSpec = {
                            if (isNavigatingBackwards) {
                                (slideInHorizontally(animationSpec = tween(280)) { -it } + fadeIn())
                                    .togetherWith(slideOutHorizontally(animationSpec = tween(280)) { it } + fadeOut())
                            } else {
                                (slideInHorizontally(animationSpec = tween(280)) { it } + fadeIn())
                                    .togetherWith(slideOutHorizontally(animationSpec = tween(280)) { -it } + fadeOut())
                            }
                        },
                        label = "CandidateCardAnimation"
                    ) { candidate ->
                        HingeDiscoverProfileContent(
                            candidate = candidate,
                            onPhotoClick = { index ->
                                val validPhotos = candidate.photos.filter { it.isNotBlank() }
                                if (validPhotos.isNotEmpty()) {
                                    fullscreenPhotoState = validPhotos to index
                                }
                            },
                            onLikePhoto = { photoUrl, index ->
                                activeLikeTarget = LikeTarget.Photo(candidate, photoUrl, index)
                            },
                            onLikePrompt = { prompt ->
                                activeLikeTarget = LikeTarget.Prompt(candidate, prompt)
                            },
                            onToggleSave = { onToggleSave(candidate) }
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                                        MaterialTheme.colorScheme.background
                                    )
                                )
                            )
                    )

                    HingeDiscoverBottomBar(
                        canUndo = undoHistory.isNotEmpty(),
                        isSaved = currentCandidate.isSaved,
                        onPass = { onPassCandidate(currentCandidate) },
                        onUndo = { onUndoAction() },
                        onAiWingman = { selectedAiCandidate = currentCandidate },
                        onToggleSave = { onToggleSave(currentCandidate) },
                        onLike = { activeLikeTarget = LikeTarget.Profile(currentCandidate) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp, start = 20.dp, end = 20.dp)
                    )
                }
            }
        }

        activeLikeTarget?.let { target ->
            HingeSendLikeSheet(
                target = target,
                onDismiss = { activeLikeTarget = null },
                onSendLike = { noteText ->
                    onLikeCandidate(target.candidate, noteText)
                    activeLikeTarget = null
                }
            )
        }

        if (selectedAiCandidate != null) {
            AppBottomSheet(onDismissRequest = { selectedAiCandidate = null }) {
                HostAppAiSheetContent(
                    candidateList = listOfNotNull(selectedAiCandidate),
                    prefs = prefs,
                    onDismiss = { selectedAiCandidate = null },
                    standalone = false
                )
            }
        }

        fullscreenPhotoState?.let { (photos, initialIndex) ->
            HingePhotoViewer(
                photos = photos,
                initialIndex = initialIndex,
                onDismiss = { fullscreenPhotoState = null }
            )
        }

        if (showJumpSheet) {
            CandidateJumpSheet(
                candidates = sortedCandidates,
                currentIndex = currentIndex,
                onDismiss = { showJumpSheet = false },
                onCandidateSelected = { index ->
                    currentIndex = index
                    showJumpSheet = false
                }
            )
        }

        if (showFilterSheet) {
            CandidateFilterSheet(
                selectedFilter = selectedFilter,
                allCandidates = effectiveCandidates,
                onFilterSelected = { filter ->
                    selectedFilter = filter
                    currentIndex = 0
                    showFilterSheet = false
                },
                onDismiss = { showFilterSheet = false }
            )
        }
    }
}

@Composable
private fun HingeDiscoverTopBar(
    selectedFilter: CandidateFilter,
    selectedSort: CandidateSortOption,
    currentIndex: Int,
    totalCount: Int,
    onFilterClick: () -> Unit,
    onSearchJumpClick: () -> Unit,
    onSortSelected: (CandidateSortOption) -> Unit,
    sortDropdownExpanded: Boolean,
    onSortDropdownChange: (Boolean) -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = onFilterClick,
                shape = ShapeTokens.Pill,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedFilter.icon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (selectedFilter == CandidateFilter.LIKED_YOU) HingePlum else MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        text = stringResource(selectedFilter.labelRes),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (totalCount > 0 && currentIndex < totalCount) {
                Surface(
                    shape = ShapeTokens.Pill,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Text(
                        text = "${currentIndex + 1} of $totalCount",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onSearchJumpClick,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = stringResource(R.string.discover_jump_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { onSortDropdownChange(true) },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = stringResource(R.string.cd_sort_options),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = sortDropdownExpanded,
                        onDismissRequest = { onSortDropdownChange(false) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        CandidateSortOption.entries.forEach { option ->
                            val isSelected = selectedSort == option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = stringResource(option.labelRes),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                trailingIcon = {
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    onSortSelected(option)
                                    onSortDropdownChange(false)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HingeDiscoverProfileContent(
    candidate: CachedCandidateProfile,
    onPhotoClick: (Int) -> Unit,
    onLikePhoto: (String, Int) -> Unit,
    onLikePrompt: (CandidatePromptItem) -> Unit,
    onToggleSave: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(candidate.userId) {
        scrollState.scrollTo(0)
    }

    val validPhotos = remember(candidate.photos) { candidate.photos.filter { it.isNotBlank() } }
    val prompts = candidate.prompts

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DiscoverProfileHeader(
            candidate = candidate,
            onToggleSave = onToggleSave
        )

        if (validPhotos.isNotEmpty()) {
            DiscoverPhotoCard(
                photoUrl = validPhotos[0],
                photoIndex = 0,
                candidateName = candidate.firstName,
                onClick = { onPhotoClick(0) },
                onLike = { onLikePhoto(validPhotos[0], 0) }
            )
        }

        DiscoverVitalsCard(candidate = candidate)

        if (prompts.isNotEmpty()) {
            DiscoverPromptCard(
                prompt = prompts[0],
                onLike = { onLikePrompt(prompts[0]) }
            )
        }

        if (validPhotos.size > 1) {
            DiscoverPhotoCard(
                photoUrl = validPhotos[1],
                photoIndex = 1,
                candidateName = candidate.firstName,
                onClick = { onPhotoClick(1) },
                onLike = { onLikePhoto(validPhotos[1], 1) }
            )
        }

        if (prompts.size > 1) {
            DiscoverPromptCard(
                prompt = prompts[1],
                onLike = { onLikePrompt(prompts[1]) }
            )
        }

        if (validPhotos.size > 2) {
            DiscoverPhotoCard(
                photoUrl = validPhotos[2],
                photoIndex = 2,
                candidateName = candidate.firstName,
                onClick = { onPhotoClick(2) },
                onLike = { onLikePhoto(validPhotos[2], 2) }
            )
        }

        DiscoverVirtuesAndVicesCard(candidate = candidate)

        if (prompts.size > 2) {
            DiscoverPromptCard(
                prompt = prompts[2],
                onLike = { onLikePrompt(prompts[2]) }
            )
        }

        for (i in 3 until validPhotos.size) {
            DiscoverPhotoCard(
                photoUrl = validPhotos[i],
                photoIndex = i,
                candidateName = candidate.firstName,
                onClick = { onPhotoClick(i) },
                onLike = { onLikePhoto(validPhotos[i], i) }
            )
        }

        for (i in 3 until prompts.size) {
            DiscoverPromptCard(
                prompt = prompts[i],
                onLike = { onLikePrompt(prompts[i]) }
            )
        }

        DiscoverArchiveMetaCard(candidate = candidate)

        Spacer(modifier = Modifier.height(130.dp))
    }
}

@Composable
private fun DiscoverProfileHeader(
    candidate: CachedCandidateProfile,
    onToggleSave: () -> Unit
) {
    val context = LocalContext.current
    val candidateFallbackName = stringResource(R.string.candidate_fallback_name)
    val tiemposFont = remember(context) { HingeFonts.tiemposRegular(context) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (candidate.isIncomingLike) {
            val isRose = candidate.incomingLikeType.contains("rose", true) || candidate.incomingLikeType.contains("super", true)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isRose) HingeIcons.RoseVector else HingeIcons.HeartVector,
                        contentDescription = null,
                        tint = HingePlum,
                        modifier = Modifier.size(20.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isRose) stringResource(R.string.candidates_tag_sent_rose) else stringResource(R.string.candidates_tag_liked_profile),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (candidate.incomingComment.isNotBlank()) {
                            Text(
                                text = "\"${candidate.incomingComment}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = buildString {
                        append(candidate.firstName.ifBlank { candidateFallbackName })
                        if (candidate.age > 0) append(", ${candidate.age}")
                    },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = tiemposFont,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (candidate.isSelfieVerified) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = stringResource(R.string.cd_selfie_verified),
                        tint = Color(0xFF2196F3),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            IconButton(
                onClick = onToggleSave,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = if (candidate.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = stringResource(if (candidate.isSaved) R.string.cd_unsave_candidate else R.string.cd_save_candidate),
                    tint = if (candidate.isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (candidate.lastActiveStatusId == 1) {
                Surface(
                    shape = ShapeTokens.Pill,
                    color = Color(0xFF1B5E20).copy(alpha = 0.12f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32))
                        )
                        Text(
                            text = stringResource(R.string.tag_active_now),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            } else if (candidate.lastActiveStatusId == 2) {
                Surface(
                    shape = ShapeTokens.Pill,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = stringResource(R.string.tag_active_today),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            if (candidate.isStandout) {
                Surface(
                    shape = ShapeTokens.Pill,
                    color = AccentGold.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = stringResource(R.string.tag_standout),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentGold
                        )
                    }
                }
            }

            if (candidate.ratingStatus.isNotBlank()) {
                val isLiked = candidate.ratingStatus.equals("Liked", true)
                Surface(
                    shape = ShapeTokens.Pill,
                    color = if (isLiked) HingePlum.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = candidate.ratingStatus,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isLiked) HingePlum else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
        }

        val locationParts = listOfNotNull(
            candidate.location.takeIf { it.isNotBlank() },
            candidate.jobTitle.takeIf { it.isNotBlank() }?.let { job ->
                if (candidate.employer.isNotBlank() && candidate.employer != job) "$job at ${candidate.employer}" else job
            }
        )
        if (locationParts.isNotEmpty()) {
            Text(
                text = locationParts.joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiscoverPhotoCard(
    photoUrl: String,
    photoIndex: Int,
    candidateName: String,
    onClick: () -> Unit,
    onLike: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(HINGE_PHOTO_ASPECT_RATIO)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = stringResource(R.string.photo_of_format, photoIndex + 1, candidateName),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
                loading = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            )

            FloatingLikeHeartButton(
                onClick = onLike,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(14.dp)
            )
        }
    }
}

@Composable
private fun DiscoverPromptCard(
    prompt: CandidatePromptItem,
    onLike: () -> Unit
) {
    val context = LocalContext.current
    val tiemposFont = remember(context) { HingeFonts.tiemposRegular(context) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 48.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (prompt.question.isNotBlank()) {
                    Text(
                        text = prompt.question,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = prompt.answer,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontFamily = tiemposFont,
                        fontSize = 22.sp,
                        lineHeight = 28.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            FloatingLikeHeartButton(
                onClick = onLike,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}

@Composable
private fun DiscoverVitalsCard(candidate: CachedCandidateProfile) {
    val vitals = remember(candidate) {
        buildList {
            if (candidate.height > 0) add(Icons.Default.Straighten to "${candidate.height} cm")
            if (candidate.jobTitle.isNotBlank()) add(Icons.Default.Work to candidate.jobTitle)
            if (candidate.employer.isNotBlank() && candidate.employer != candidate.jobTitle) {
                add(Icons.Default.AccountBalance to candidate.employer)
            }
            if (candidate.school.isNotBlank()) add(Icons.Default.School to candidate.school)
            if (candidate.location.isNotBlank()) add(Icons.Default.LocationOn to candidate.location)
            if (candidate.hometown.isNotBlank()) add(Icons.Default.Home to "From ${candidate.hometown}")
            if (candidate.datingIntention.isNotBlank()) add(Icons.Default.Favorite to candidate.datingIntention)
            if (candidate.relationshipType.isNotBlank()) add(Icons.Default.People to candidate.relationshipType)
            if (candidate.religion.isNotBlank()) add(Icons.Default.Church to candidate.religion)
            if (candidate.politics.isNotBlank()) add(Icons.Default.Balance to candidate.politics)
            if (candidate.languagesSpoken.isNotBlank()) add(Icons.Default.Translate to candidate.languagesSpoken)
            if (candidate.pronouns.isNotBlank()) add(Icons.Default.AccountCircle to candidate.pronouns)
        }
    }

    if (vitals.isEmpty()) return

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.discover_my_vitals),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                vitals.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (icon, text) ->
                            Surface(
                                shape = ShapeTokens.Pill,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverVirtuesAndVicesCard(candidate: CachedCandidateProfile) {
    val habits = remember(candidate) {
        buildList {
            if (candidate.drinking.isNotBlank()) add(Icons.Default.LocalBar to "Drinks: ${candidate.drinking}")
            if (candidate.smoking.isNotBlank()) add(Icons.Default.SmokingRooms to "Smokes: ${candidate.smoking}")
            if (candidate.marijuana.isNotBlank()) add(Icons.Default.Spa to "Weed: ${candidate.marijuana}")
            if (candidate.drugs.isNotBlank()) add(Icons.Default.Medication to "Drugs: ${candidate.drugs}")
            val kidText = candidate.kids.ifBlank { candidate.familyPlans }
            if (kidText.isNotBlank()) add(Icons.Default.ChildCare to kidText)
            if (candidate.pet.isNotBlank()) add(Icons.Default.Pets to candidate.pet)
            if (candidate.zodiac.isNotBlank()) add(Icons.Default.AutoAwesome to candidate.zodiac)
        }
    }

    if (habits.isEmpty()) return

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.discover_virtues_and_vices),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                habits.chunked(2).forEach { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowItems.forEach { (icon, text) ->
                            Surface(
                                shape = ShapeTokens.Pill,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = text,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverArchiveMetaCard(candidate: CachedCandidateProfile) {
    val context = LocalContext.current
    val toastCopiedFormat = stringResource(R.string.toast_copied_format)

    fun copyText(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, java.lang.String.format(toastCopiedFormat, label), Toast.LENGTH_SHORT).show()
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Archive Intelligence",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (candidate.userId.isNotBlank()) {
                    TextButton(
                        onClick = { copyText("User ID", candidate.userId) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Copy ID", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            val metaItems = mutableListOf<String>()
            if (candidate.firstSeenTimestamp > 0) {
                metaItems.add("First seen: ${formatArchiveDate(candidate.firstSeenTimestamp)}")
            }
            if (candidate.lastSeenTimestamp > candidate.firstSeenTimestamp && candidate.lastSeenTimestamp > 0) {
                metaItems.add("Last seen: ${formatArchiveDate(candidate.lastSeenTimestamp)}")
            }
            metaItems.add(if (candidate.isLiveInFeed) "In Live Feed" else "Archived Candidate")

            Text(
                text = metaItems.joinToString(" • "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedButton(
                onClick = {
                    val summary = buildString {
                        append("${candidate.firstName}, ${candidate.age}")
                        if (candidate.height > 0) append(" • ${candidate.height}cm")
                        append("\n")
                        if (candidate.jobTitle.isNotBlank()) append("Work: ${candidate.jobTitle}\n")
                        if (candidate.location.isNotBlank()) append("Location: ${candidate.location}\n")
                        if (candidate.hometown.isNotBlank()) append("Hometown: ${candidate.hometown}\n")
                        if (candidate.prompts.isNotEmpty()) {
                            append("\nPrompts:\n")
                            candidate.prompts.forEach { p ->
                                append("Q: ${p.question}\nA: ${p.answer}\n\n")
                            }
                        }
                        if (candidate.photos.isNotEmpty()) {
                            append("Photos:\n" + candidate.photos.joinToString("\n"))
                        }
                    }
                    copyText("${candidate.firstName}'s Profile", summary)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.Pill
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export Profile Summary", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun FloatingLikeHeartButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 5.dp,
        modifier = modifier.size(46.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = HingeIcons.HeartVector,
                contentDescription = stringResource(R.string.discover_action_like),
                tint = HingePlum,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun HingeDiscoverBottomBar(
    canUndo: Boolean,
    isSaved: Boolean,
    onPass: () -> Unit,
    onUndo: () -> Unit,
    onAiWingman: () -> Unit,
    onToggleSave: () -> Unit,
    onLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onPass,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 8.dp,
            modifier = Modifier.size(58.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.discover_action_pass),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Surface(
            onClick = onUndo,
            enabled = canUndo,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Undo,
                    contentDescription = stringResource(R.string.discover_action_undo),
                    tint = if (canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Surface(
            onClick = onAiWingman,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 5.dp,
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.ai_wingman_label),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Surface(
            onClick = onToggleSave,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp,
            modifier = Modifier.size(44.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = stringResource(if (isSaved) R.string.cd_unsave_candidate else R.string.cd_save_candidate),
                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Surface(
            onClick = onLike,
            shape = CircleShape,
            color = HingePlum,
            shadowElevation = 8.dp,
            modifier = Modifier.size(58.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = HingeIcons.HeartVector,
                    contentDescription = stringResource(R.string.discover_action_like),
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HingeSendLikeSheet(
    target: LikeTarget,
    onDismiss: () -> Unit,
    onSendLike: (String) -> Unit
) {
    var noteText by rememberSaveable { mutableStateOf("") }
    val candidate = target.candidate
    val candidateName = candidate.firstName.ifBlank { stringResource(R.string.candidate_fallback_name) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (target) {
                is LikeTarget.Photo -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(54.dp)
                        ) {
                            SubcomposeAsyncImage(
                                model = target.photoUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.discover_liking_photo),
                                style = MaterialTheme.typography.labelMedium,
                                color = HingePlum,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.discover_send_like_to_format, candidateName),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                is LikeTarget.Prompt -> {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = target.prompt.question,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = HingePlum
                            )
                            Text(
                                text = "\"${target.prompt.answer}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                is LikeTarget.Profile -> {
                    Text(
                        text = stringResource(R.string.discover_send_like_to_format, candidateName),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                placeholder = {
                    Text(
                        stringResource(R.string.discover_add_comment),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val suggestions = when (target) {
                    is LikeTarget.Prompt -> listOf(
                        "✨ Icebreaker" to "I have to know more about this!",
                        "💬 Playful" to "Bold take, I respect it 😉",
                        "🎯 Question" to "What inspired this answer?",
                        "💡 Relatable" to "This is so relatable honestly 😂"
                    )
                    is LikeTarget.Photo -> listOf(
                        "✨ Compliment" to "Great photo! Where was this taken?",
                        "💬 Playful" to "Such great energy in this pic ✨",
                        "🎯 Question" to "What's the backstory here?",
                        "🌟 Vibe" to "10/10 aesthetic"
                    )
                    is LikeTarget.Profile -> listOf(
                        "✨ Icebreaker" to "Hey $candidateName, loved your profile!",
                        "💬 Playful" to "You seem like fun, let's chat!",
                        "🎯 Question" to "What's your favorite thing to do lately?",
                        "☕ Date" to "Coffee or drinks this week?"
                    )
                }

                suggestions.forEach { (label, starter) ->
                    SuggestionChip(
                        onClick = { noteText = starter },
                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        shape = ShapeTokens.Pill
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSendLike(noteText) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = ShapeTokens.Pill,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = HingePlum,
                        contentColor = Color.White
                    )
                ) {
                    Icon(HingeIcons.HeartVector, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.discover_send_like),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(
                    onClick = { onSendLike("") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        stringResource(R.string.discover_send_without_note),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateJumpSheet(
    candidates: List<CachedCandidateProfile>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onCandidateSelected: (Int) -> Unit
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val candidateFallbackName = stringResource(R.string.candidate_fallback_name)

    val matchedCandidatesWithIndex = remember(candidates, searchQuery) {
        candidates.mapIndexed { index, candidate -> index to candidate }
            .filter { (_, candidate) -> matchesCandidateSearch(candidate, searchQuery) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.discover_jump_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            UnhingeSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = stringResource(R.string.search_candidates_hint)
            )

            Text(
                text = "${matchedCandidatesWithIndex.size} profiles available",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = matchedCandidatesWithIndex,
                    key = { (idx, c) -> c.userId.ifBlank { "cand_$idx" } }
                ) { (originalIndex, candidate) ->
                    val isCurrent = originalIndex == currentIndex
                    Surface(
                        onClick = { onCandidateSelected(originalIndex) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = if (isCurrent) CardDefaults.outlinedCardBorder() else null,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val photoUrl = candidate.photos.firstOrNull { it.isNotBlank() }
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.size(48.dp)
                            ) {
                                if (photoUrl != null) {
                                    SubcomposeAsyncImage(
                                        model = photoUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = candidate.firstName.ifBlank { candidateFallbackName },
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    if (candidate.age > 0) {
                                        Text(
                                            text = "${candidate.age}",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    if (isCurrent) {
                                        Surface(
                                            shape = ShapeTokens.Pill,
                                            color = MaterialTheme.colorScheme.primary
                                        ) {
                                            Text(
                                                text = "Current",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                val subText = listOfNotNull(
                                    candidate.jobTitle.takeIf { it.isNotBlank() },
                                    candidate.location.takeIf { it.isNotBlank() }
                                ).joinToString(" • ")

                                if (subText.isNotBlank()) {
                                    Text(
                                        text = subText,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CandidateFilterSheet(
    selectedFilter: CandidateFilter,
    allCandidates: List<CachedCandidateProfile>,
    onFilterSelected: (CandidateFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val filterCounts = remember(allCandidates) {
        CandidateFilter.entries.associateWith { filter ->
            allCandidates.count { filter.matches(it) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Select Discover Queue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(CandidateFilter.entries) { filter ->
                    val isSelected = selectedFilter == filter
                    val count = filterCounts[filter] ?: 0

                    Surface(
                        onClick = { onFilterSelected(filter) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                filter.icon?.let { icon ->
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Text(
                                    text = stringResource(filter.labelRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Surface(
                                shape = ShapeTokens.Pill,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    text = "$count",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
private fun DiscoverCaughtUpView(
    filter: CandidateFilter,
    totalCandidatesCount: Int,
    hasPassedHistory: Boolean,
    onUndo: () -> Unit,
    onStartOver: () -> Unit,
    onSwitchFilter: (CandidateFilter) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.discover_caught_up_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.discover_caught_up_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (hasPassedHistory) {
                OutlinedButton(
                    onClick = onUndo,
                    shape = ShapeTokens.Pill,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.discover_undo_action), style = MaterialTheme.typography.labelLarge)
                }
            }

            Button(
                onClick = onStartOver,
                shape = ShapeTokens.Pill,
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.discover_start_over), style = MaterialTheme.typography.labelLarge)
            }

            if (filter != CandidateFilter.IN_FEED) {
                OutlinedButton(
                    onClick = { onSwitchFilter(CandidateFilter.IN_FEED) },
                    shape = ShapeTokens.Pill,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("View In Feed Candidates", style = MaterialTheme.typography.labelLarge)
                }
            }

            if (filter != CandidateFilter.ALL) {
                TextButton(
                    onClick = { onSwitchFilter(CandidateFilter.ALL) },
                    shape = ShapeTokens.Pill,
                    modifier = Modifier.fillMaxWidth().height(48.dp)
                ) {
                    Text("View All Archived ($totalCandidatesCount)", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

private fun formatArchiveDate(timestamp: Long): String {
    if (timestamp <= 0) return "N/A"
    return try {
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (_: Exception) {
        "N/A"
    }
}

internal fun normalizeSearchText(text: String): String {
    if (text.isEmpty()) return ""
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
    return normalized
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .replace("’", "'")
        .replace("“", "\"")
        .replace("”", "\"")
        .lowercase(Locale.ROOT)
}

internal fun buildSearchCorpus(candidate: CachedCandidateProfile): String {
    val builder = StringBuilder()

    builder.append(candidate.firstName).append(' ')
    builder.append(candidate.jobTitle).append(' ')
    builder.append(candidate.location).append(' ')
    builder.append(candidate.hometown).append(' ')
    builder.append(candidate.datingIntention).append(' ')
    builder.append(candidate.relationshipType).append(' ')
    builder.append(candidate.religion).append(' ')
    builder.append(candidate.ethnicity).append(' ')
    builder.append(candidate.userId).append(' ')

    if (candidate.age > 0) {
        builder.append(candidate.age).append(' ')
        builder.append(candidate.age).append("yo ")
        builder.append(candidate.age).append(" yrs ")
        builder.append("age ").append(candidate.age).append(' ')
    }

    if (candidate.height > 0) {
        builder.append(candidate.height).append(' ')
        builder.append(candidate.height).append("cm ")
    }

    for (p in candidate.prompts) {
        builder.append(p.question).append(' ')
        builder.append(p.answer).append(' ')
    }

    if (candidate.likeComment.isNotBlank()) {
        builder.append("comment note with comment ").append(candidate.likeComment).append(' ')
    }

    if (candidate.isLiveInFeed) {
        builder.append("in feed feed live active ")
    } else {
        builder.append("archived past ")
    }
    if (candidate.isStandout) {
        builder.append("standout standouts ")
    }
    if (candidate.isDiscover || (candidate.isLiveInFeed && !candidate.isStandout)) {
        builder.append("discover ")
    }
    if (candidate.ratingStatus.isNotBlank()) {
        builder.append(candidate.ratingStatus).append(' ')
        if (candidate.ratingStatus.equals("Liked", true)) {
            builder.append("like ")
        } else if (candidate.ratingStatus.equals("Passed", true)) {
            builder.append("pass skip ")
        }
    }
    if (candidate.isSelfieVerified) {
        builder.append("verified selfie verified check ")
    }
    if (candidate.isCircleMember) {
        builder.append("circle circle member ")
    }
    if (candidate.school.isNotBlank()) {
        builder.append("school college university ").append(candidate.school).append(' ')
    }
    if (candidate.employer.isNotBlank()) {
        builder.append("company employer work ").append(candidate.employer).append(' ')
    }
    if (candidate.politics.isNotBlank()) {
        builder.append("politics ").append(candidate.politics).append(' ')
    }
    if (candidate.drinking.isNotBlank()) {
        builder.append("drinking drink ").append(candidate.drinking).append(' ')
    }
    if (candidate.smoking.isNotBlank()) {
        builder.append("smoking smoke ").append(candidate.smoking).append(' ')
    }
    if (candidate.marijuana.isNotBlank()) {
        builder.append("marijuana weed ").append(candidate.marijuana).append(' ')
    }
    if (candidate.drugs.isNotBlank()) {
        builder.append("drugs ").append(candidate.drugs).append(' ')
    }
    if (candidate.kids.isNotBlank()) {
        builder.append("kids ").append(candidate.kids).append(' ')
    }
    if (candidate.familyPlans.isNotBlank()) {
        builder.append("family ").append(candidate.familyPlans).append(' ')
    }
    if (candidate.pet.isNotBlank()) {
        builder.append("pet dog cat ").append(candidate.pet).append(' ')
    }
    if (candidate.zodiac.isNotBlank()) {
        builder.append("zodiac ").append(candidate.zodiac).append(' ')
    }
    if (candidate.isNewHere) {
        builder.append("new new here ")
    }
    if (candidate.isYourTypeLately) {
        builder.append("your type lately curated ")
    }
    if (candidate.isSecondChance) {
        builder.append("second chance ")
    }
    if (candidate.isIncomingLike) {
        builder.append("liked you incoming like ")
        if (candidate.incomingLikeType.isNotBlank()) builder.append(candidate.incomingLikeType).append(' ')
        if (candidate.incomingComment.isNotBlank()) builder.append(candidate.incomingComment).append(' ')
    }

    return normalizeSearchText(builder.toString())
}

internal fun matchesCandidateSearch(candidate: CachedCandidateProfile, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true

    val normalizedQuery = normalizeSearchText(trimmed)
    val tokens = normalizedQuery.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true

    val corpus = buildSearchCorpus(candidate)
    return tokens.all { token -> corpus.contains(token) }
}
