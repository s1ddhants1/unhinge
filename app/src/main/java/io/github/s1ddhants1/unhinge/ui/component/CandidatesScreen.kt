package io.github.s1ddhants1.unhinge.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
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
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
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
    val label: String,
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
        Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
    }

    // 1. Filter candidates by multi-token search query
    val searchMatchedCandidates = remember(candidates, searchQuery) {
        if (searchQuery.isBlank()) candidates else {
            candidates.filter { c -> matchesCandidateSearch(c, searchQuery) }
        }
    }

    // 2. Dynamic facet counts based on current search matches
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
            CandidateFilterOption("All", "All", searchMatchedCandidates.size, Icons.Default.People),
            CandidateFilterOption("Liked You", "Liked You", likedYouCount, Icons.Default.Favorite),
            CandidateFilterOption("Liked", "Liked", likedCount, Icons.Default.ThumbUp),
            CandidateFilterOption("Passed", "Passed", passedCount, Icons.Default.ThumbDown),
            CandidateFilterOption("Archived Past", "Archived Past", pastCount, Icons.Default.Archive),
            CandidateFilterOption("Standouts", "Standouts", standoutCount, Icons.Default.Star),
            CandidateFilterOption("With Comment", "With Comment", withCommentCount, Icons.AutoMirrored.Filled.Comment)
        )
    }

    // 3. Final filtered candidate list matching the active filter chip
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
        // Search Bar with clear icon and IME action
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search candidates, prompts, jobs, intent...") },
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

        // Filter Chips: In Feed primary + Dropdown chip for remaining options
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Primary In Feed Filter Chip with Dropdown Sub-menu for Discover, Standouts, or Both
            Box {
                val inFeedLabel = when (inFeedSubFilter) {
                    "Discover" -> "In Feed: Discover ($inFeedDiscoverCount)"
                    "Standouts" -> "In Feed: Standouts ($inFeedStandoutCount)"
                    else -> "In Feed ($inFeedCount)"
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
                            contentDescription = "In Feed filter options",
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
                                text = "Both (Discover & Standouts)",
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
                                        contentDescription = "Active Filter",
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
                                text = "Discover Feed",
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
                                        contentDescription = "Active Filter",
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
                                text = "Standouts",
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
                                        contentDescription = "Active Filter",
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

            // Dropdown Chip for remaining filter options
            Box {
                val isOtherSelected = selectedFilter != "In Feed"
                val activeOption = otherFilterOptions.find { it.id == selectedFilter }
                val dropdownLabel = if (isOtherSelected) {
                    activeOption?.let { "${it.label} (${it.count})" } ?: selectedFilter
                } else {
                    "More Filters"
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
                            contentDescription = "Filter options",
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
                                    text = option.label,
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
                                            contentDescription = "Active Filter",
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
                                    text = "In Feed (Both)",
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
                                    text = "In Feed (Discover)",
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
                                    text = "In Feed (Standouts)",
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

        // Sub-filter selector when "In Feed" is active
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
                    text = "Feed:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 2.dp)
                )

                FilterChip(
                    selected = inFeedSubFilter == "Both",
                    onClick = { inFeedSubFilter = "Both" },
                    label = { Text("Both ($inFeedCount)") },
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
                    label = { Text("Discover ($inFeedDiscoverCount)") },
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
                    label = { Text("Standouts ($inFeedStandoutCount)") },
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

        // Search & Filter Status Row
        if (searchQuery.isNotBlank() || selectedFilter != "In Feed" || inFeedSubFilter != "Both") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = if (selectedFilter == "In Feed" && inFeedSubFilter != "Both") {
                    "Showing ${filtered.size} of ${candidates.size} candidates (In Feed · $inFeedSubFilter)"
                } else {
                    "Showing ${filtered.size} of ${candidates.size} candidates"
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        searchQuery = ""
                        selectedFilter = "In Feed"
                        inFeedSubFilter = "Both"
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
                contentPadding = PaddingValues(bottom = 88.dp)
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
                onDismiss = { selectedAiCandidate = null }
            )
        }
    }

    // Fullscreen Photo Modal with Gallery Carousel & Pinch-to-Zoom
    val previewCandidate = previewCandidateId?.let { id -> candidates.find { it.userId == id } }
    if (previewCandidate != null && previewCandidate.photos.isNotEmpty()) {
        val photos = previewCandidate.photos
        val safePhotoIndex = previewPhotoIndex.coerceIn(0, photos.size - 1)
        val currentPhotoUrl = photos[safePhotoIndex]

        var zoomScale by remember { mutableFloatStateOf(1f) }
        var panOffset by remember { mutableStateOf(Offset.Zero) }

        LaunchedEffect(safePhotoIndex, previewCandidateId) {
            zoomScale = 1f
            panOffset = Offset.Zero
        }

        Dialog(
            onDismissRequest = {
                zoomScale = 1f
                panOffset = Offset.Zero
                previewCandidateId = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .wrapContentHeight(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(scrollState, enabled = zoomScale <= 1.05f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header with Candidate Name, Photo Counter & Close
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = previewCandidate.firstName.ifBlank { "Candidate" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Photo ${safePhotoIndex + 1} of ${photos.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            previewCandidateId = null
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Preview")
                        }
                    }

                    // Main Photo Display Box with Pinch-to-Zoom & Pan
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 280.dp, max = 460.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(safePhotoIndex) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (zoomScale > 1.05f) {
                                                zoomScale = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                zoomScale = 2.5f
                                            }
                                        }
                                    )
                                }
                                .pointerInput(safePhotoIndex) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                        zoomScale = newScale
                                        if (newScale > 1.05f) {
                                            val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                            val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                            panOffset = Offset(
                                                x = (panOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                                y = (panOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                        } else {
                                            panOffset = Offset.Zero
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = zoomScale
                                    scaleY = zoomScale
                                    translationX = panOffset.x
                                    translationY = panOffset.y
                                }
                        ) {
                            SubcomposeAsyncImage(
                                model = currentPhotoUrl,
                                contentDescription = "Photo ${safePhotoIndex + 1} of ${previewCandidate.firstName.ifBlank { "Candidate" }}",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                                loading = {
                                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(32.dp))
                                    }
                                },
                                error = {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.BrokenImage,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Text(
                                            "Failed to load image",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            )
                        }

                        // Previous / Next Buttons overlay (if multiple photos and not zoomed in)
                        if (photos.size > 1 && zoomScale <= 1.05f) {
                            if (safePhotoIndex > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 8.dp)
                                        .size(36.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                            previewPhotoIndex = safePhotoIndex - 1
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Previous Photo",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                            if (safePhotoIndex < photos.size - 1) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.75f),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 8.dp)
                                        .size(36.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            zoomScale = 1f
                                            panOffset = Offset.Zero
                                            previewPhotoIndex = safePhotoIndex + 1
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = "Next Photo",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Zoom Reset Badge when zoomed in
                        if (zoomScale > 1.05f) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(8.dp)
                                    .clickable {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ZoomOutMap,
                                        contentDescription = "Reset Zoom",
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", zoomScale)}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }

                    // Action Buttons Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { copy("Photo URL", currentPhotoUrl) }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy URL")
                        }
                        TextButton(onClick = {
                            zoomScale = 1f
                            panOffset = Offset.Zero
                            previewCandidateId = null
                        }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
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
    var showAllDetails by remember { mutableStateOf(false) }

    UnhingeDoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // Header Row: Left Column (Name, Age, Height, Verified, Job) & Right Badges
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
                            text = candidate.firstName.ifBlank { "Candidate" },
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
                                contentDescription = "Selfie Verified Profile",
                                tint = AccentSuccess,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        if (candidate.isCircleMember) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest
                            ) {
                                Text(
                                    "Circle",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
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

                // Interactive / Quick-filter Status Badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (candidate.isIncomingLike) {
                        val isRose = candidate.incomingLikeType.contains("rose", true) || candidate.incomingLikeType.contains("super", true)
                        StatusTag(
                            label = if (isRose) "Rose" else "Liked You",
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            onClick = { onBadgeClick("Liked You") }
                        )
                    }
                    if (candidate.ratingStatus.isNotBlank()) {
                        val isLiked = candidate.ratingStatus.equals("Liked", true)
                        StatusTag(
                            label = candidate.ratingStatus,
                            containerColor = if (isLiked) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick(if (isLiked) "Liked" else "Passed") }
                        )
                    }
                    if (candidate.likeComment.isNotBlank()) {
                        StatusTag(
                            label = "Note",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick("With Comment") }
                        )
                    }

                    if (candidate.isStandout) {
                        StatusTag(
                            label = "Standout",
                            containerColor = AccentGold.copy(alpha = 0.18f),
                            contentColor = AccentGold,
                            onClick = { onBadgeClick("Standouts") }
                        )
                    } else if (candidate.isLiveInFeed) {
                        StatusTag(
                            label = "Discover",
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            onClick = { onBadgeClick("Discover") }
                        )
                    }
                }
            }

            // Photo Gallery (LazyRow with smooth corner radii)
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
                                contentDescription = "Photo ${idx + 1} of ${candidate.firstName.ifBlank { "Candidate" }}",
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

            // Incoming "Liked You" Paywall Reveal Banner
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
                            imageVector = Icons.Default.Favorite,
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
                                    text = if (isRose) "Sent You a Rose" else "Liked Your Profile",
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

            // Primary Profile Details with Progressive Disclosure
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
                            text = if (showAllDetails) "Show less" else "+ ${secondaryDetails.size} more details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showAllDetails = !showAllDetails }
                        )
                    }
                }
            }

            // Sent Like Comment Banner (displays the outgoing comment sent with like)
            if (candidate.likeComment.isNotBlank()) {
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
                            Icons.Default.ChatBubble,
                            contentDescription = "Your Sent Comment",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp).padding(top = 2.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Your Sent Comment",
                                style = MaterialTheme.typography.labelSmall,
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

            // Prompts (Question Header + Answer)
            if (candidate.prompts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                candidate.prompts.forEach { item ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
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
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
            }

            // Footer: Photo count & Archival timestamps & Clean Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "${candidate.photos.size} Photos in Archive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val seenTimes = mutableListOf<String>()
                    if (candidate.firstSeenTimestamp > 0) {
                        seenTimes.add("First: ${formatArchiveDate(candidate.firstSeenTimestamp)}")
                    }
                    if (candidate.lastSeenTimestamp > candidate.firstSeenTimestamp && candidate.lastSeenTimestamp > 0) {
                        seenTimes.add("Last: ${formatArchiveDate(candidate.lastSeenTimestamp)}")
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
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("AI Wingman", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(
                        onClick = {
                            val parts = mutableListOf<String>()
                            val header = buildString {
                                append(candidate.firstName.ifBlank { "Candidate" })
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
                        Icon(Icons.Default.ContentCopy, contentDescription = "Copy Profile Info", modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
    }
}

@Composable
internal fun StatusTag(
    label: String,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier.height(26.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
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

/**
 * Normalizes text for search by stripping diacritical marks (accents),
 * lowercasing, and normalizing punctuation and quotes.
 */
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

/**
 * Builds a comprehensive search corpus containing all candidate attributes,
 * aliases, badges, and outgoing like comments.
 */
internal fun buildSearchCorpus(candidate: CachedCandidateProfile): String {
    val builder = StringBuilder()

    // Core profile text
    builder.append(candidate.firstName).append(' ')
    builder.append(candidate.jobTitle).append(' ')
    builder.append(candidate.location).append(' ')
    builder.append(candidate.hometown).append(' ')
    builder.append(candidate.datingIntention).append(' ')
    builder.append(candidate.relationshipType).append(' ')
    builder.append(candidate.religion).append(' ')
    builder.append(candidate.ethnicity).append(' ')
    builder.append(candidate.userId).append(' ')

    // Age representations: "27", "27yo", "age 27"
    if (candidate.age > 0) {
        builder.append(candidate.age).append(' ')
        builder.append(candidate.age).append("yo ")
        builder.append(candidate.age).append(" yrs ")
        builder.append("age ").append(candidate.age).append(' ')
    }

    // Height representations: "175", "175cm"
    if (candidate.height > 0) {
        builder.append(candidate.height).append(' ')
        builder.append(candidate.height).append("cm ")
    }

    // Prompts (both question titles and response answers)
    for (p in candidate.prompts) {
        builder.append(p.question).append(' ')
        builder.append(p.answer).append(' ')
    }

    // Sent like comment & note tags
    if (candidate.likeComment.isNotBlank()) {
        builder.append("comment note with comment ").append(candidate.likeComment).append(' ')
    }

    // Statuses & Badges
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

/**
 * Checks whether a candidate matches a search query using multi-token conjunction (AND).
 * Each token (separated by spaces or commas) must be present in the candidate corpus.
 */
internal fun matchesCandidateSearch(candidate: CachedCandidateProfile, query: String): Boolean {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return true

    val normalizedQuery = normalizeSearchText(trimmed)
    val tokens = normalizedQuery.split(Regex("[\\s,;]+")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true

    val corpus = buildSearchCorpus(candidate)
    return tokens.all { token -> corpus.contains(token) }
}
