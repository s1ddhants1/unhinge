package io.github.s1ddhants1.unhinge.ui.component

import io.github.s1ddhants1.unhinge.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import coil3.compose.SubcomposeAsyncImage
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

private data class CandidateFilterOption(
    val id: String,
    val labelRes: Int,
    val count: Int,
    val icon: ImageVector
)

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
    var selectedFilter by rememberSaveable { mutableStateOf("In Feed") }
    var inFeedSubFilter by rememberSaveable { mutableStateOf("Both") }
    var inFeedDropdownExpanded by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var previewCandidateId by rememberSaveable { mutableStateOf<String?>(null) }
    var previewPhotoIndex by rememberSaveable { mutableIntStateOf(0) }
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

    val likedYouCount = remember(searchMatchedCandidates) { searchMatchedCandidates.count { it.isIncomingLike } }
    val inFeedCount = remember(searchMatchedCandidates) { searchMatchedCandidates.count { it.isLiveInFeed } }
    val inFeedDiscoverCount = remember(searchMatchedCandidates) {
        searchMatchedCandidates.count { it.isLiveInFeed && (it.isDiscover || !it.isStandout) }
    }
    val inFeedStandoutCount = remember(searchMatchedCandidates) {
        searchMatchedCandidates.count { it.isLiveInFeed && it.isStandout }
    }
    val pastCount = remember(searchMatchedCandidates) { searchMatchedCandidates.count { !it.isLiveInFeed } }
    val discoverCount = remember(searchMatchedCandidates) { searchMatchedCandidates.count { it.isDiscover } }
    val standoutCount = remember(searchMatchedCandidates) { searchMatchedCandidates.count { it.isStandout } }
    val likedCount = remember(searchMatchedCandidates) { searchMatchedCandidates.count { it.ratingStatus.equals("Liked", true) } }
    val withCommentCount = remember(searchMatchedCandidates) { searchMatchedCandidates.count { it.likeComment.isNotBlank() } }
    val passedCount = remember(searchMatchedCandidates) { searchMatchedCandidates.count { it.ratingStatus.equals("Passed", true) } }

    val otherFilterOptions = remember(
        searchMatchedCandidates.size,
        likedYouCount,
        likedCount,
        passedCount,
        pastCount,
        standoutCount,
        withCommentCount
    ) {
        listOf(
            CandidateFilterOption("All", R.string.filter_all, searchMatchedCandidates.size, Icons.Default.People),
            CandidateFilterOption("Liked You", R.string.filter_liked_you, likedYouCount, HingeIcons.HeartVector),
            CandidateFilterOption("Liked", R.string.filter_liked, likedCount, Icons.Default.ThumbUp),
            CandidateFilterOption("Passed", R.string.filter_passed, passedCount, Icons.Default.ThumbDown),
            CandidateFilterOption("Archived Past", R.string.filter_archived_past, pastCount, Icons.Default.Archive),
            CandidateFilterOption("Standouts", R.string.filter_standouts, standoutCount, Icons.Default.Star),
            CandidateFilterOption("With Comment", R.string.filter_with_comment, withCommentCount, Icons.AutoMirrored.Filled.Comment)
        )
    }

    val filtered = remember(searchMatchedCandidates, selectedFilter, inFeedSubFilter) {
        searchMatchedCandidates.filter { c ->
            when (selectedFilter) {
                "In Feed" -> when (inFeedSubFilter) {
                    "Discover" -> c.isLiveInFeed && (c.isDiscover || !c.isStandout)
                    "Standouts" -> c.isLiveInFeed && c.isStandout
                    else -> c.isLiveInFeed
                }
                "Liked You" -> c.isIncomingLike
                "Archived Past" -> !c.isLiveInFeed
                "Discover" -> c.isDiscover
                "Standouts" -> c.isStandout
                "Liked" -> c.ratingStatus.equals("Liked", true)
                "With Comment" -> c.likeComment.isNotBlank()
                "Passed" -> c.ratingStatus.equals("Passed", true)
                else -> true
            }
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
                val inFeedLabel = when (inFeedSubFilter) {
                    "Discover" -> stringResource(R.string.filter_in_feed_discover, inFeedDiscoverCount)
                    "Standouts" -> stringResource(R.string.filter_in_feed_standouts, inFeedStandoutCount)
                    else -> stringResource(R.string.filter_in_feed_both, inFeedCount)
                }
                val inFeedIcon = when (inFeedSubFilter) {
                    "Discover" -> Icons.Default.Explore
                    "Standouts" -> Icons.Default.Star
                    else -> Icons.Default.DynamicFeed
                }

                FilterChip(
                    selected = selectedFilter == "In Feed",
                    onClick = {
                        if (selectedFilter != "In Feed") {
                            selectedFilter = "In Feed"
                        } else {
                            inFeedDropdownExpanded = !inFeedDropdownExpanded
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = inFeedIcon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(inFeedLabel) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (inFeedDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.cd_filter_in_feed),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                DropdownMenu(
                    expanded = inFeedDropdownExpanded,
                    onDismissRequest = { inFeedDropdownExpanded = false },
                    modifier = Modifier.widthIn(min = 230.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DynamicFeed,
                                contentDescription = null,
                                tint = if (inFeedSubFilter == "Both") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.filter_both_full),
                                fontWeight = if (inFeedSubFilter == "Both") FontWeight.SemiBold else FontWeight.Normal,
                                color = if (inFeedSubFilter == "Both") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "($inFeedCount)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (inFeedSubFilter == "Both") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (inFeedSubFilter == "Both") {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.cd_filter_active),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            inFeedSubFilter = "Both"
                            selectedFilter = "In Feed"
                            inFeedDropdownExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Explore,
                                contentDescription = null,
                                tint = if (inFeedSubFilter == "Discover") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.filter_discover_feed),
                                fontWeight = if (inFeedSubFilter == "Discover") FontWeight.SemiBold else FontWeight.Normal,
                                color = if (inFeedSubFilter == "Discover") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "($inFeedDiscoverCount)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (inFeedSubFilter == "Discover") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (inFeedSubFilter == "Discover") {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.cd_filter_active),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            inFeedSubFilter = "Discover"
                            selectedFilter = "In Feed"
                            inFeedDropdownExpanded = false
                        }
                    )

                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (inFeedSubFilter == "Standouts") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.filter_standouts),
                                fontWeight = if (inFeedSubFilter == "Standouts") FontWeight.SemiBold else FontWeight.Normal,
                                color = if (inFeedSubFilter == "Standouts") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "($inFeedStandoutCount)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (inFeedSubFilter == "Standouts") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (inFeedSubFilter == "Standouts") {
                                    Spacer(Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = stringResource(R.string.cd_filter_active),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            inFeedSubFilter = "Standouts"
                            selectedFilter = "In Feed"
                            inFeedDropdownExpanded = false
                        }
                    )
                }
            }

            Box {
                val isOtherSelected = selectedFilter != "In Feed"
                val activeOption = otherFilterOptions.find { it.id == selectedFilter }
                val dropdownLabel = if (isOtherSelected) {
                    activeOption?.let { stringResource(R.string.filter_option_count_format, stringResource(it.labelRes), it.count) } ?: selectedFilter
                } else {
                    stringResource(R.string.filter_more)
                }

                FilterChip(
                    selected = isOtherSelected,
                    onClick = {
                        dropdownExpanded = !dropdownExpanded
                    },
                    leadingIcon = {
                        val icon = activeOption?.icon ?: Icons.Default.FilterList
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(dropdownLabel) },
                    trailingIcon = {
                        Icon(
                            imageVector = if (dropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = stringResource(R.string.cd_filter_options),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                DropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.widthIn(min = 220.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    otherFilterOptions.forEach { option ->
                        val isSelected = selectedFilter == option.id
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = option.icon,
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "(${option.count})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isSelected) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = stringResource(R.string.cd_filter_active),
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            },
                            onClick = {
                                selectedFilter = option.id
                                dropdownExpanded = false
                            }
                        )
                    }

                    if (isOtherSelected) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.DynamicFeed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.filter_in_feed_reset_both),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                Text(
                                    text = "($inFeedCount)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                selectedFilter = "In Feed"
                                inFeedSubFilter = "Both"
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.filter_in_feed_reset_discover),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                Text(
                                    text = "($inFeedDiscoverCount)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                selectedFilter = "In Feed"
                                inFeedSubFilter = "Discover"
                                dropdownExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.filter_in_feed_reset_standouts),
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            trailingIcon = {
                                Text(
                                    text = "($inFeedStandoutCount)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            },
                            onClick = {
                                selectedFilter = "In Feed"
                                inFeedSubFilter = "Standouts"
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selectedFilter == "In Feed",
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.filter_feed_prefix),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 2.dp)
                )

                FilterChip(
                    selected = inFeedSubFilter == "Both",
                    onClick = { inFeedSubFilter = "Both" },
                    label = { Text(stringResource(R.string.feed_both_format, inFeedCount)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.DynamicFeed,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                )

                FilterChip(
                    selected = inFeedSubFilter == "Discover",
                    onClick = { inFeedSubFilter = "Discover" },
                    label = { Text(stringResource(R.string.feed_discover_format, inFeedDiscoverCount)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                )

                FilterChip(
                    selected = inFeedSubFilter == "Standouts",
                    onClick = { inFeedSubFilter = "Standouts" },
                    label = { Text(stringResource(R.string.feed_standouts_format, inFeedStandoutCount)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }

        if (filtered.isEmpty()) {
            val emptyTitle = if (candidates.isEmpty()) {
                "No Profiles Archived Yet"
            } else if (selectedFilter == "In Feed") {
                when (inFeedSubFilter) {
                    "Discover" -> "No Discover Profiles In Feed"
                    "Standouts" -> "No Standouts In Feed"
                    else -> "No Candidates In Feed"
                }
            } else {
                "No Matching Candidates"
            }

            val emptyDescription = if (candidates.isEmpty()) {
                "Candidate profiles will appear here as they are discovered in your Hinge feed or Standouts."
            } else if (selectedFilter == "In Feed") {
                when (inFeedSubFilter) {
                    "Discover" -> "No active Discover queue profiles found. Try checking Standouts or Both."
                    "Standouts" -> "No active Standout profiles found in the feed. Check back when Hinge refreshes Standouts."
                    else -> "No active in-feed profiles found. You can view all saved profiles or check other filters."
                }
            } else if (selectedFilter == "With Comment") {
                "No candidates found with an outgoing like comment."
            } else {
                "No candidates matched your search or active filter."
            }

            val actionLabel = if (candidates.isEmpty()) {
                null
            } else if (selectedFilter == "In Feed" && inFeedSubFilter != "Both") {
                "Show Both Feeds"
            } else if (selectedFilter == "In Feed" && candidates.isNotEmpty()) {
                "View All Candidates"
            } else if (searchQuery.isNotBlank() || selectedFilter != "In Feed") {
                "Reset to In Feed"
            } else {
                null
            }

            EmptyStateView(
                icon = if (candidates.isEmpty()) Icons.Default.PeopleOutline else Icons.Default.FilterListOff,
                title = emptyTitle,
                description = emptyDescription,
                actionLabel = actionLabel,
                onAction = {
                    if (selectedFilter == "In Feed" && inFeedSubFilter != "Both") {
                        inFeedSubFilter = "Both"
                    } else if (selectedFilter == "In Feed" && candidates.isNotEmpty()) {
                        selectedFilter = "All"
                    } else {
                        searchQuery = ""
                        selectedFilter = "In Feed"
                        inFeedSubFilter = "Both"
                        focusManager.clearFocus()
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
                    items = filtered,
                    key = { candidate ->
                        candidate.userId.ifBlank { "cand_${candidate.hashCode()}" }
                    }
                ) { candidate ->
                    CandidateCard(
                        candidate = candidate,
                        onPhotoClick = { photoIdx ->
                            previewCandidateId = candidate.userId
                            previewPhotoIndex = photoIdx
                        },
                        onBadgeClick = { filter ->
                            when (filter) {
                                "Discover" -> {
                                    if (selectedFilter == "In Feed" && inFeedSubFilter == "Discover") {
                                        inFeedSubFilter = "Both"
                                    } else {
                                        selectedFilter = "In Feed"
                                        inFeedSubFilter = "Discover"
                                    }
                                }
                                "Standouts" -> {
                                    if (selectedFilter == "In Feed" && inFeedSubFilter == "Standouts") {
                                        inFeedSubFilter = "Both"
                                    } else {
                                        selectedFilter = "In Feed"
                                        inFeedSubFilter = "Standouts"
                                    }
                                }
                                else -> {
                                    selectedFilter = if (selectedFilter == filter) "In Feed" else filter
                                }
                            }
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

    val previewCandidate = previewCandidateId?.let { id -> candidates.find { it.userId == id } }
    if (previewCandidate != null && previewCandidate.photos.isNotEmpty()) {
        val subtitle = buildString {
            if (previewCandidate.age > 0) append("${previewCandidate.age}")
            if (previewCandidate.location.isNotBlank()) {
                if (isNotEmpty()) append(" • ")
                append(previewCandidate.location)
            }
        }.ifBlank { null }

        HingePhotoViewer(
            photos = previewCandidate.photos,
            initialIndex = previewPhotoIndex,
            title = previewCandidate.firstName.ifBlank { stringResource(R.string.candidate_fallback_name) },
            subtitle = subtitle,
            onDismiss = { previewCandidateId = null },
            onCopyUrl = { copy(context.getString(R.string.copy_photo_url), it) }
        )
    }
}

@Composable
private fun CandidateCard(
    candidate: CachedCandidateProfile,
    onPhotoClick: (Int) -> Unit,
    onBadgeClick: (String) -> Unit,
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
                    if (candidate.isIncomingLike) {
                        val isRose = candidate.incomingLikeType.contains("rose", true) || candidate.incomingLikeType.contains("super", true)
                        UnhingeBadge(
                            label = if (isRose) stringResource(R.string.tag_rose) else stringResource(R.string.tag_liked_you),
                            icon = if (isRose) HingeIcons.RoseVector else HingeIcons.HeartVector,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { onBadgeClick("Liked You") }
                        )
                    }
                    if (candidate.ratingStatus.isNotBlank()) {
                        val isLiked = candidate.ratingStatus.equals("Liked", true)
                        UnhingeBadge(
                            label = candidate.ratingStatus,
                            containerColor = if (isLiked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick(if (isLiked) "Liked" else "Passed") }
                        )
                    }
                    if (candidate.likeComment.isNotBlank()) {
                        UnhingeBadge(
                            label = stringResource(R.string.tag_note),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick("With Comment") }
                        )
                    }

                    if (candidate.isStandout) {
                        UnhingeBadge(
                            label = stringResource(R.string.tag_standout),
                            containerColor = AccentGold.copy(alpha = 0.18f),
                            contentColor = AccentGold,
                            onClick = { onBadgeClick("Standouts") }
                        )
                    } else if (candidate.isLiveInFeed) {
                        UnhingeBadge(
                            label = stringResource(R.string.tag_discover),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick("Discover") }
                        )
                    }
                }
            }

            if (candidate.photos.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(
                        items = candidate.photos,
                        key = { idx, url -> "${candidate.userId}_${idx}_$url" }
                    ) { idx, photoUrl ->
                        Box(
                            modifier = Modifier
                                .size(width = 135.dp, height = 180.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .bouncyClickable { onPhotoClick(idx) }
                        ) {
                            SubcomposeAsyncImage(
                                model = photoUrl,
                                contentDescription = stringResource(R.string.photo_of_format, idx + 1, candidate.firstName.ifBlank { stringResource(R.string.candidate_fallback_name) }),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    SkeletonBox(
                                        modifier = Modifier.fillMaxSize(),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                },
                                error = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                }
                            )
                            Surface(
                                modifier = Modifier
                                    .padding(6.dp)
                                    .align(Alignment.BottomEnd),
                                shape = RoundedCornerShape(6.dp),
                                color = Color.Black.copy(alpha = 0.65f)
                            ) {
                                Text(
                                    text = "${idx + 1}/${candidate.photos.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
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
