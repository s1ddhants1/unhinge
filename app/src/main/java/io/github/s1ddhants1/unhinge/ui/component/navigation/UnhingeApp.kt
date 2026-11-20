package io.github.s1ddhants1.unhinge.ui.component.navigation

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import io.github.s1ddhants1.unhinge.R
import io.github.s1ddhants1.unhinge.ui.AppTab
import io.github.s1ddhants1.unhinge.ui.MainUiState
import io.github.s1ddhants1.unhinge.ui.MainViewModel
import io.github.s1ddhants1.unhinge.ui.component.AppQuickControls
import io.github.s1ddhants1.unhinge.ui.component.CandidatesScreen
import io.github.s1ddhants1.unhinge.ui.component.FrameworkStatusBanner
import io.github.s1ddhants1.unhinge.ui.component.MatchesScreen
import io.github.s1ddhants1.unhinge.ui.component.ProfileRawScreen
import io.github.s1ddhants1.unhinge.ui.component.RootStatusBanner
import io.github.s1ddhants1.unhinge.ui.component.TelemetryScreen
import io.github.s1ddhants1.unhinge.ui.component.settings.SettingsScreen
import io.github.s1ddhants1.unhinge.ui.component.settings.SettingsSubpage
import io.github.s1ddhants1.unhinge.util.AppActions
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnhingeApp(
    viewModel: MainViewModel,
    state: MainUiState,
    prefs: PreferencesManager,
    intent: Intent? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentTab by remember { mutableStateOf(AppTab.Home) }
    var showSettings by remember { mutableStateOf(false) }
    var settingsSubpage by remember { mutableStateOf<SettingsSubpage?>(null) }

    LaunchedEffect(intent) {
        val subpage = intent?.getStringExtra("subpage") ?: intent?.getStringExtra("navigate_to")
        if (subpage == "ai_wingman" || subpage == "settings_ai") {
            showSettings = true
            settingsSubpage = SettingsSubpage.AI_WINGMAN
        }
    }

    fun handleBack() {
        if (showSettings) {
            if (settingsSubpage != null) {
                settingsSubpage = null
            } else {
                showSettings = false
            }
        } else if (currentTab != AppTab.Home) {
            currentTab = AppTab.Home
        }
    }

    BackHandler(enabled = showSettings || currentTab != AppTab.Home) {
        handleBack()
    }

    val quickForceStoppedMsg = stringResource(R.string.quick_force_stopped)
    val quickOpenedSettingsMsg = stringResource(R.string.quick_opened_settings)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            AppTopBar(
                currentTab = currentTab,
                showSettings = showSettings,
                settingsSubpage = settingsSubpage,
                onBack = { handleBack() },
                onNavigateToSettings = {
                    showSettings = true
                    settingsSubpage = null
                }
            )
        },
        bottomBar = {
            if (!showSettings) {
                Column {
                    AppQuickControls(
                        onForceStop = {
                            coroutineScope.launch {
                                val stopped = AppActions.forceStopTargetApp(context)
                                snackbarHostState.showSnackbar(
                                    if (stopped) quickForceStoppedMsg
                                    else quickOpenedSettingsMsg
                                )
                            }
                        },
                        onOpenApp = {
                            AppActions.launchTargetApp(context)
                        }
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        fun selectTab(tab: AppTab) {
                            if (currentTab != tab) {
                                currentTab = tab
                            }
                        }

                        val incomingCount = state.completeData.incomingLikes.totalLikes
                        val activeChatsCount = remember(state.completeData.matches) {
                            state.completeData.matches
                                .filter { it.subjectId.isNotBlank() }
                                .distinctBy { it.subjectId }
                                .count { !it.chatEnded && !it.isUnmatched && it.isLiveMatch && it.messageCount > 0 }
                        }

                        NavigationBarItem(
                            selected = currentTab == AppTab.Candidates,
                            onClick = { selectTab(AppTab.Candidates) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (incomingCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.tertiary,
                                                contentColor = MaterialTheme.colorScheme.onTertiary
                                            ) {
                                                Text("$incomingCount")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.People,
                                        contentDescription = stringResource(R.string.cd_candidates_tab)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_label_candidates)) }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.Matches,
                            onClick = { selectTab(AppTab.Matches) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (activeChatsCount > 0) {
                                            Badge(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ) {
                                                Text("$activeChatsCount")
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ChatBubble,
                                        contentDescription = stringResource(R.string.cd_matches_tab)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_label_matches)) }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.Home,
                            onClick = { selectTab(AppTab.Home) },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = stringResource(R.string.cd_home_tab)
                                )
                            },
                            label = { Text(stringResource(R.string.nav_label_home)) }
                        )
                        NavigationBarItem(
                            selected = currentTab == AppTab.Profile,
                            onClick = { selectTab(AppTab.Profile) },
                            icon = {
                                val playerPhotoUrl = state.completeData.playerMedia.firstOrNull { it.photoUrl.isNotBlank() }?.photoUrl
                                if (!playerPhotoUrl.isNullOrBlank()) {
                                    SubcomposeAsyncImage(
                                        model = playerPhotoUrl,
                                        contentDescription = stringResource(R.string.cd_profile_tab),
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .then(
                                                if (currentTab == AppTab.Profile) {
                                                    Modifier.border(
                                                        width = 1.5.dp,
                                                        color = MaterialTheme.colorScheme.primary,
                                                        shape = CircleShape
                                                    )
                                                } else {
                                                    Modifier
                                                }
                                            ),
                                        loading = {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = stringResource(R.string.cd_profile_tab)
                                            )
                                        },
                                        error = {
                                            Icon(
                                                imageVector = Icons.Default.AccountCircle,
                                                contentDescription = stringResource(R.string.cd_profile_tab)
                                            )
                                        }
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AccountCircle,
                                        contentDescription = stringResource(R.string.cd_profile_tab)
                                    )
                                }
                            },
                            label = { Text(stringResource(R.string.nav_label_profile)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showSettings) {
                SettingsScreen(
                    currentSubpage = settingsSubpage,
                    onNavigateToSubpage = { settingsSubpage = it },
                    prefs = prefs,
                    onDataRestored = { viewModel.loadInsights() }
                )
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = { viewModel.loadInsights() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            if (targetState.ordinal > initialState.ordinal) {
                                (slideInHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    initialOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() }
                                ) + fadeIn(animationSpec = tween(240))) togetherWith
                                        (slideOutHorizontally(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            targetOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() }
                                        ) + fadeOut(animationSpec = tween(180)))
                            } else {
                                (slideInHorizontally(
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioLowBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    ),
                                    initialOffsetX = { fullWidth -> (-fullWidth * 0.25f).toInt() }
                                ) + fadeIn(animationSpec = tween(240))) togetherWith
                                        (slideOutHorizontally(
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            ),
                                            targetOffsetX = { fullWidth -> (fullWidth * 0.25f).toInt() }
                                        ) + fadeOut(animationSpec = tween(180)))
                            }
                        },
                        label = "TabTransition"
                    ) { tab ->
                        when (tab) {
                            AppTab.Home -> {
                                TelemetryScreen(
                                    data = state.completeData,
                                    isRefreshing = state.isRefreshing,
                                    headerContent = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            RootStatusBanner(
                                                isRootGranted = state.isRootGranted,
                                                modifier = Modifier.weight(1f)
                                            )
                                            FrameworkStatusBanner(
                                                isConnected = state.isFrameworkConnected,
                                                isInjectable = state.isInjectable,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                )
                            }
                            AppTab.Profile -> ProfileRawScreen(data = state.completeData)
                            AppTab.Candidates -> CandidatesScreen(candidates = state.completeData.candidates, prefs = prefs)
                            AppTab.Matches -> MatchesScreen(matches = state.completeData.matches, prefs = prefs)
                        }
                    }
                }
            }
        }
    }
}
