package io.github.s1ddhants1.unhinge.ui.component

import io.github.s1ddhants1.unhinge.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.hook.ui.HingeIcons
import io.github.s1ddhants1.unhinge.hook.ui.HostAppAiSheetContent
import io.github.s1ddhants1.unhinge.ui.theme.AccentGold
import io.github.s1ddhants1.unhinge.ui.theme.AccentSuccess
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import androidx.compose.ui.graphics.vector.ImageVector
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.*

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
                1 -> 0 // Active now
                2 -> 1 // Active today
                else -> 2 // Others
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
    ALL(R.string.filter_all, Icons.Default.People),
    DISCOVER(R.string.filter_discover, Icons.Default.Explore),
    STANDOUTS(R.string.filter_standouts, Icons.Default.Star),
    ACTIVE_NOW(R.string.filter_active_now, Icons.Default.FiberManualRecord),
    ACTIVE_TODAY(R.string.filter_active_today, Icons.Default.AccessTime),
    LIKED_YOU(R.string.filter_liked_you, HingeIcons.HeartVector),
    WITH_COMMENT(R.string.filter_with_comment, Icons.AutoMirrored.Filled.Comment),
    LIKED(R.string.filter_liked, Icons.Default.ThumbUp),
    PASSED(R.string.filter_passed, Icons.Default.ThumbDown),
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
        LIKED -> candidate.ratingStatus.equals("Liked", ignoreCase = true)
        PASSED -> candidate.ratingStatus.equals("Passed", ignoreCase = true)
        ARCHIVED -> !candidate.isLiveInFeed
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CandidatesScreen(
    candidates: List<CachedCandidateProfile>,
    prefs: PreferencesManager,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val candidateFallbackName = stringResource(R.string.candidate_fallback_name)
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(CandidateFilter.IN_FEED) }
    var selectedSort by rememberSaveable { mutableStateOf(CandidateSortOption.ACTIVE_STATUS) }
    var sortDropdownExpanded by remember { mutableStateOf(false) }
    var selectedAiCandidate by remember { mutableStateOf<CachedCandidateProfile?>(null) }

    fun copy(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, context.getString(R.string.toast_copied_format, label), Toast.LENGTH_SHORT).show()
    }

    val searchMatchedCandidates = remember(candidates, searchQuery) {
        if (searchQuery.isBlank()) candidates else {
            candidates.filter { c -> matchesCandidateSearch(c, searchQuery) }
        }
    }

    val filterCounts = remember(searchMatchedCandidates) {
        CandidateFilter.entries.associateWith { filter ->
            searchMatchedCandidates.count { filter.matches(it) }
        }
    }

    val filtered = remember(searchMatchedCandidates, selectedFilter) {
        searchMatchedCandidates.filter { selectedFilter.matches(it) }
    }

    fun toggleFilter(filter: CandidateFilter) {
        selectedFilter = when {
            selectedFilter == filter && filter == CandidateFilter.IN_FEED -> CandidateFilter.ALL
            selectedFilter == filter -> CandidateFilter.IN_FEED
            else -> filter
        }
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

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {

        UnhingeSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = stringResource(R.string.search_candidates_hint)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                FilterChip(
                    selected = selectedSort != CandidateSortOption.ACTIVE_STATUS,
                    onClick = { sortDropdownExpanded = !sortDropdownExpanded },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.SwapVert,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(stringResource(selectedSort.labelRes)) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (sortDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.cd_sort_options),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = ShapeTokens.Pill
                )

                DropdownMenu(
                    expanded = sortDropdownExpanded,
                    onDismissRequest = { sortDropdownExpanded = false },
                    modifier = Modifier.widthIn(min = 190.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    CandidateSortOption.entries.forEach { option ->
                        val isSelected = selectedSort == option
                        DropdownMenuItem(
                            leadingIcon = {
                                val optIcon = when (option) {
                                    CandidateSortOption.ACTIVE_STATUS -> Icons.Default.Bolt
                                    CandidateSortOption.RECENT -> Icons.Default.AccessTime
                                    CandidateSortOption.NAME -> Icons.Default.SortByAlpha
                                    CandidateSortOption.AGE -> Icons.Default.FormatListNumbered
                                }
                                Icon(
                                    imageVector = optIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(option.labelRes),
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.cd_filter_active),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            onClick = {
                                selectedSort = option
                                sortDropdownExpanded = false
                            }
                        )
                    }
                }
            }

            VerticalDivider(
                modifier = Modifier.height(24.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            CandidateFilter.entries.forEach { filter ->
                val count = filterCounts[filter] ?: 0
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { toggleFilter(filter) },
                    leadingIcon = filter.icon?.let { icon ->
                        {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(
                                R.string.filter_option_count_format,
                                stringResource(filter.labelRes),
                                count
                            )
                        )
                    },
                    shape = ShapeTokens.Pill
                )
            }
        }

        if (searchQuery.isNotBlank() || selectedFilter != CandidateFilter.IN_FEED) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.candidates_showing_format, filtered.size, candidates.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        searchQuery = ""
                        selectedFilter = CandidateFilter.IN_FEED
                        focusManager.clearFocus()
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_reset_filters), style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (sortedCandidates.isEmpty()) {
            val emptyTitle = when {
                candidates.isEmpty() -> stringResource(R.string.candidates_empty_archived_title)
                searchQuery.isNotBlank() -> stringResource(R.string.empty_candidates_search)
                selectedFilter == CandidateFilter.IN_FEED -> stringResource(R.string.empty_candidates_in_feed)
                selectedFilter == CandidateFilter.DISCOVER -> stringResource(R.string.candidates_empty_discover_title)
                selectedFilter == CandidateFilter.STANDOUTS -> stringResource(R.string.candidates_empty_standouts_title)
                selectedFilter == CandidateFilter.ACTIVE_NOW -> stringResource(R.string.empty_candidates_active_now)
                selectedFilter == CandidateFilter.ACTIVE_TODAY -> stringResource(R.string.empty_candidates_active_today)
                selectedFilter == CandidateFilter.LIKED_YOU -> stringResource(R.string.empty_candidates_liked_you)
                selectedFilter == CandidateFilter.WITH_COMMENT -> stringResource(R.string.empty_candidates_with_note)
                selectedFilter == CandidateFilter.LIKED -> stringResource(R.string.empty_candidates_liked)
                selectedFilter == CandidateFilter.PASSED -> stringResource(R.string.empty_candidates_passed)
                selectedFilter == CandidateFilter.ARCHIVED -> stringResource(R.string.empty_candidates_archived)
                else -> stringResource(R.string.empty_candidates_default)
            }

            val emptyDescription = when {
                candidates.isEmpty() -> stringResource(R.string.candidates_empty_archived_desc)
                selectedFilter == CandidateFilter.DISCOVER -> stringResource(R.string.candidates_empty_discover_desc)
                selectedFilter == CandidateFilter.STANDOUTS -> stringResource(R.string.candidates_empty_standouts_desc)
                else -> stringResource(R.string.empty_candidates_filter_desc)
            }

            val actionLabel = when {
                candidates.isEmpty() -> null
                selectedFilter != CandidateFilter.IN_FEED || searchQuery.isNotBlank() -> stringResource(R.string.action_reset_filters)
                else -> stringResource(R.string.filter_all)
            }

            EmptyStateView(
                icon = if (candidates.isEmpty()) Icons.Default.PeopleOutline else Icons.Default.FilterListOff,
                title = emptyTitle,
                description = emptyDescription,
                actionLabel = actionLabel,
                onAction = {
                    if (selectedFilter != CandidateFilter.IN_FEED || searchQuery.isNotBlank()) {
                        searchQuery = ""
                        selectedFilter = CandidateFilter.IN_FEED
                        focusManager.clearFocus()
                    } else {
                        selectedFilter = CandidateFilter.ALL
                    }
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(
                    items = sortedCandidates,
                    key = { candidate ->
                        candidate.userId.ifBlank { "cand_${candidate.hashCode()}" }
                    }
                ) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        onBadgeClick = { filter ->
                            toggleFilter(filter)
                        },
                        onAiClick = {
                            selectedAiCandidate = candidate
                        },
                        onCopy = { copy("${candidate.firstName.ifBlank { "Candidate" }}'s details", it) }
                    )
                }
            }
        }
    }
    }

    if (selectedAiCandidate != null) {
        AppBottomSheet(
            onDismissRequest = { selectedAiCandidate = null }
        ) {
            HostAppAiSheetContent(
                candidateList = listOfNotNull(selectedAiCandidate),
                prefs = prefs,
                onDismiss = { selectedAiCandidate = null },
                standalone = false
            )
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: CachedCandidateProfile,
    onBadgeClick: (CandidateFilter) -> Unit,
    onAiClick: () -> Unit = {},
    onCopy: (String) -> Unit
) {
    val context = LocalContext.current
    var showAllDetails by remember { mutableStateOf(false) }

    UnhingeDoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeTokens.Card,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = candidate.firstName.ifBlank { stringResource(R.string.candidate_fallback_name) },
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
                        if (candidate.height > 0) {
                            Text(
                                text = "• ${candidate.height} cm",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (candidate.isSelfieVerified) {
                            Icon(
                                Icons.Default.Verified,
                                contentDescription = stringResource(R.string.cd_selfie_verified),
                                tint = AccentSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (candidate.isCircleMember) {
                            UnhingeBadge(
                                label = stringResource(R.string.tag_circle),
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (candidate.jobTitle.isNotBlank()) {
                        Text(
                            candidate.jobTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (candidate.lastActiveStatusId == 1) {
                        UnhingeBadge(
                            label = stringResource(R.string.tag_active_now),
                            containerColor = Color(0xFF1B5E20).copy(alpha = 0.14f),
                            contentColor = Color(0xFF2E7D32),
                            showPulseDot = true,
                            pulseColor = Color(0xFF2E7D32),
                            onClick = { onBadgeClick(CandidateFilter.ACTIVE_NOW) }
                        )
                    } else if (candidate.lastActiveStatusId == 2) {
                        UnhingeBadge(
                            label = stringResource(R.string.tag_active_today),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick(CandidateFilter.ACTIVE_TODAY) }
                        )
                    }

                    if (candidate.isIncomingLike) {
                        val isRose = candidate.incomingLikeType.contains("rose", true) || candidate.incomingLikeType.contains("super", true)
                        UnhingeBadge(
                            label = if (isRose) stringResource(R.string.tag_rose) else stringResource(R.string.tag_liked_you),
                            icon = if (isRose) HingeIcons.RoseVector else HingeIcons.HeartVector,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { onBadgeClick(CandidateFilter.LIKED_YOU) }
                        )
                    }
                    if (candidate.ratingStatus.isNotBlank()) {
                        val isLiked = candidate.ratingStatus.equals("Liked", true)
                        UnhingeBadge(
                            label = candidate.ratingStatus,
                            containerColor = if (isLiked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick(if (isLiked) CandidateFilter.LIKED else CandidateFilter.PASSED) }
                        )
                    }
                    if (candidate.likeComment.isNotBlank()) {
                        UnhingeBadge(
                            label = stringResource(R.string.tag_note),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick(CandidateFilter.WITH_COMMENT) }
                        )
                    }

                    if (candidate.isStandout) {
                        UnhingeBadge(
                            label = stringResource(R.string.tag_standout),
                            containerColor = AccentGold.copy(alpha = 0.18f),
                            contentColor = AccentGold,
                            onClick = { onBadgeClick(CandidateFilter.STANDOUTS) }
                        )
                    } else if (candidate.isLiveInFeed) {
                        UnhingeBadge(
                            label = stringResource(R.string.tag_discover),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick(CandidateFilter.DISCOVER) }
                        )
                    }
                }
            }

            val validPhotos = remember(candidate.photos) { candidate.photos.filter { it.isNotBlank() } }
            if (validPhotos.isNotEmpty()) {
                HingePhotoCarousel(
                    photos = validPhotos,
                    candidateName = candidate.firstName.ifBlank { stringResource(R.string.candidate_fallback_name) },
                    onCopyUrl = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.copy_photo_url), it))
                        Toast.makeText(context, context.getString(R.string.toast_copied_format, context.getString(R.string.copy_photo_url)), Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (candidate.isIncomingLike) {
                val isRose = candidate.incomingLikeType.contains("rose", true) || candidate.incomingLikeType.contains("super", true)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isRose) HingeIcons.RoseVector else HingeIcons.HeartVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isRose) stringResource(R.string.candidates_tag_sent_rose) else stringResource(R.string.candidates_tag_liked_profile),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (candidate.incomingTimestamp > 0L) {
                                    val locale = LocalLocale.current.platformLocale
                                    val formattedDate = SimpleDateFormat("MMM d, h:mm a", locale).format(Date(candidate.incomingTimestamp))
                                    Text(
                                        text = formattedDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (candidate.incomingComment.isNotBlank()) {
                                Text(
                                    text = "\"${candidate.incomingComment}\"",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }

            val primaryDetails = listOfNotNull(
                candidate.location.takeIf { it.isNotBlank() },
                candidate.school.takeIf { it.isNotBlank() },
                candidate.employer.takeIf { it.isNotBlank() && it != candidate.jobTitle }
            )
            val secondaryDetails = listOfNotNull(
                candidate.hometown.takeIf { it.isNotBlank() }?.let { "From $it" },
                candidate.datingIntention.takeIf { it.isNotBlank() }?.let { "Intent: $it" },
                candidate.relationshipType.takeIf { it.isNotBlank() },
                candidate.politics.takeIf { it.isNotBlank() },
                candidate.religion.takeIf { it.isNotBlank() },
                candidate.ethnicity.takeIf { it.isNotBlank() },
                candidate.drinking.takeIf { it.isNotBlank() }?.let { "Drinks: $it" },
                candidate.smoking.takeIf { it.isNotBlank() }?.let { "Smokes: $it" },
                candidate.marijuana.takeIf { it.isNotBlank() }?.let { "Weed: $it" },
                (candidate.kids.ifBlank { candidate.familyPlans }).takeIf { it.isNotBlank() },
                candidate.pet.takeIf { it.isNotBlank() }?.let { "Pet: $it" },
                candidate.zodiac.takeIf { it.isNotBlank() }?.let { "Zodiac: $it" }
            )

            if (primaryDetails.isNotEmpty() || secondaryDetails.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (primaryDetails.isNotEmpty()) {
                        Text(
                            text = primaryDetails.joinToString("  •  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (secondaryDetails.isNotEmpty()) {
                        AnimatedVisibility(visible = showAllDetails) {
                            Text(
                                text = secondaryDetails.joinToString("  •  "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = if (showAllDetails) stringResource(R.string.candidates_action_show_less) else stringResource(R.string.candidates_action_more_details_format, secondaryDetails.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showAllDetails = !showAllDetails }
                        )
                    }
                }
            }

            if (candidate.likeComment.isNotBlank()) {
                Surface(
                    shape = ShapeTokens.Pill,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.ChatBubble,
                            contentDescription = stringResource(R.string.cd_sent_comment),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = stringResource(R.string.candidates_label_sent_comment),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "\"${candidate.likeComment}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            if (candidate.prompts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                candidate.prompts.forEach { item ->
                    Surface(
                        shape = ShapeTokens.Pill,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (item.question.isNotBlank()) {
                                Text(
                                    text = item.question,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = item.answer,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.candidates_photos_count_format, candidate.photos.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val seenTimes = mutableListOf<String>()
                    if (candidate.firstSeenTimestamp > 0) {
                        seenTimes.add(stringResource(R.string.candidates_meta_first_format, formatArchiveDate(candidate.firstSeenTimestamp)))
                    }
                    if (candidate.lastSeenTimestamp > candidate.firstSeenTimestamp && candidate.lastSeenTimestamp > 0) {
                        seenTimes.add(stringResource(R.string.candidates_meta_last_format, formatArchiveDate(candidate.lastSeenTimestamp)))
                    }
                    if (seenTimes.isNotEmpty()) {
                        Text(
                            text = seenTimes.joinToString("  •  "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = onAiClick,
                        shape = ShapeTokens.Pill,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.ai_wingman_label), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(
                        onClick = {
                            val parts = mutableListOf<String>()
                            val header = buildString {
                                append(candidate.firstName.ifBlank { context.getString(R.string.candidate_fallback_name) })
                                if (candidate.age > 0) append(", ${candidate.age}")
                                if (candidate.height > 0) append(" (${candidate.height}cm)")
                                val status = if (candidate.isLiveInFeed) "In Feed" else "Archived"
                                append(" [$status]")
                                if (candidate.ratingStatus.isNotBlank()) append(" - ${candidate.ratingStatus}")
                            }
                            parts.add(header)
                            if (candidate.likeComment.isNotBlank()) parts.add("Your Sent Comment: \"${candidate.likeComment}\"")
                            if (candidate.jobTitle.isNotBlank()) parts.add("Job: ${candidate.jobTitle}")
                            if (candidate.location.isNotBlank()) parts.add("Location: ${candidate.location}")
                            if (candidate.hometown.isNotBlank()) parts.add("Hometown: ${candidate.hometown}")
                            if (candidate.datingIntention.isNotBlank()) parts.add("Intent: ${candidate.datingIntention}")
                            if (candidate.relationshipType.isNotBlank()) parts.add("Relationship: ${candidate.relationshipType}")
                            if (candidate.religion.isNotBlank()) parts.add("Religion: ${candidate.religion}")
                            if (candidate.ethnicity.isNotBlank()) parts.add("Ethnicity: ${candidate.ethnicity}")
                            if (candidate.prompts.isNotEmpty()) {
                                val promptSummary = candidate.prompts.joinToString("\n") { p ->
                                    if (p.question.isNotBlank()) "• ${p.question}\n  ${p.answer}" else "• ${p.answer}"
                                }
                                parts.add("Prompts:\n$promptSummary")
                            }
                            if (candidate.photos.isNotEmpty()) {
                                parts.add("Photos (${candidate.photos.size}):\n" + candidate.photos.joinToString("\n"))
                            }
                            if (candidate.userId.isNotBlank()) parts.add("ID: ${candidate.userId}")
                            onCopy(parts.joinToString("\n\n"))
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.cd_copy_profile), modifier = Modifier.size(17.dp))
                    }
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
    } catch (e: Exception) {
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
