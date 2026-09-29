package io.github.s1ddhants1.unhinge.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Launch
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.ui.component.*
import io.github.s1ddhants1.unhinge.ui.theme.AccentSuccess
import io.github.s1ddhants1.unhinge.ui.theme.Theme
import io.github.s1ddhants1.unhinge.util.AppActions
import io.github.s1ddhants1.unhinge.util.LSPatchHelper
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import coil3.compose.setSingletonImageLoaderFactory
import io.github.s1ddhants1.unhinge.util.UnhingeImageLoader
import io.github.s1ddhants1.unhinge.util.attempt
import kotlinx.coroutines.launch

enum class AppTab(val title: String) {
    Candidates("Candidate Feed"),
    Matches("Matches & Chats"),
    Home("Home"),
    Profile("My Profile Data"),
    Explorer("Raw Storage Explorer")
}

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val coroutineScope = rememberCoroutineScope()
            val snackbarHostState = remember { SnackbarHostState() }
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            var currentTab by remember { mutableStateOf(AppTab.Home) }
            var showSettings by remember { mutableStateOf(false) }
            var settingsSubpage by remember { mutableStateOf<io.github.s1ddhants1.unhinge.ui.component.settings.SettingsSubpage?>(null) }

            LaunchedEffect(intent) {
                val subpage = intent?.getStringExtra("subpage") ?: intent?.getStringExtra("navigate_to")
                if (subpage == "ai_wingman" || subpage == "settings_ai") {
                    showSettings = true
                    settingsSubpage = io.github.s1ddhants1.unhinge.ui.component.settings.SettingsSubpage.AI_WINGMAN
                }
            }

            val localPrefs = remember { getSharedPreferences(Consts.PREFS_SETTINGS, Context.MODE_PRIVATE) }
            val prefsState = remember {
                val mgr = PreferencesManager(localPrefs)
                mgr.loadFromFallbackStorage(this@MainActivity)
                mgr.onPreferenceChanged = { mgr.saveToFallbackStorageAsync(this@MainActivity) }
                mutableStateOf(mgr)
            }
            val prefs = prefsState.value

            // Handle service push and framework status monitoring
            LaunchedEffect(Unit) {
                LSPatchHelper.requestServicePush(this@MainActivity)
                viewModel.loadInsights()

                App.serviceState.collect { service ->
                    val evaluation = LSPatchHelper.evaluateFrameworkStatus(this@MainActivity, service)
                    viewModel.updateFrameworkEvaluation(evaluation)
                    if (service != null && !evaluation.isIntegrated) {
                        attempt("retrieve remote preferences from XposedService") {
                            val remotePrefs = service.getRemotePreferences(Consts.PREFS_SETTINGS)
                            syncPreferences(localPrefs, remotePrefs)
                            val remoteMgr = PreferencesManager(remotePrefs, backupPrefs = localPrefs)
                            remoteMgr.onPreferenceChanged = {
                                remoteMgr.saveToFallbackStorageAsync(this@MainActivity)
                            }
                            prefsState.value = remoteMgr
                        }
                    }
                }
            }

            BackHandler(enabled = showSettings || currentTab != AppTab.Home) {
                if (showSettings) {
                    if (settingsSubpage != null) {
                        settingsSubpage = null
                    } else {
                        showSettings = false
                    }
                } else {
                    currentTab = AppTab.Home
                }
            }

            var showMenu by remember { mutableStateOf(false) }

            val topBarTitle = when {
                !showSettings -> currentTab.title
                settingsSubpage == io.github.s1ddhants1.unhinge.ui.component.settings.SettingsSubpage.APPEARANCE -> "Appearance"
                settingsSubpage == io.github.s1ddhants1.unhinge.ui.component.settings.SettingsSubpage.AI_WINGMAN -> "AI Prompt Wingman"
                settingsSubpage == io.github.s1ddhants1.unhinge.ui.component.settings.SettingsSubpage.PRIVACY -> "Privacy & Telemetry"
                else -> "Settings"
            }

            Theme(
                themeMode = prefs.themeMode,
                pureBlack = prefs.pureBlack,
                themeColor = androidx.compose.ui.graphics.Color(prefs.themeColor)
            ) {
                setSingletonImageLoaderFactory { context ->
                    UnhingeImageLoader.get(context)
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    text = topBarTitle,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                if (showSettings) {
                                    IconButton(
                                        onClick = {
                                            if (settingsSubpage != null) {
                                                settingsSubpage = null
                                            } else {
                                                showSettings = false
                                            }
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                } else if (currentTab != AppTab.Home) {
                                    IconButton(
                                        onClick = {
                                            currentTab = AppTab.Home
                                        }
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                    }
                                }
                            },
                            actions = {
                                if (!showSettings) {
                                    IconButton(
                                        onClick = {
                                            showMenu = true
                                        }
                                    ) {
                                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                                    }

                                    DropdownMenu(
                                        expanded = showMenu,
                                        onDismissRequest = { showMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Settings") },
                                            onClick = {
                                                showMenu = false
                                                showSettings = true
                                                settingsSubpage = null
                                            },
                                            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) }
                                        )
                                    }
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        if (showSettings) {
                            Surface(tonalElevation = 3.dp, shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .navigationBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    ActionButton(
                                        text = "Force Stop",
                                        icon = Icons.Default.PowerSettingsNew,
                                        containerColor = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        coroutineScope.launch {
                                            val stopped = AppActions.forceStopTargetApp(context)
                                            snackbarHostState.showSnackbar(
                                                if (stopped) "Hinge force stopped"
                                                else "Opened Hinge app settings"
                                            )
                                        }
                                    }
                                    ActionButton(
                                        text = "Open Hinge",
                                        icon = Icons.AutoMirrored.Filled.Launch,
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        AppActions.launchTargetApp(context)
                                    }
                                }
                            }
                        } else {
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
                                    state.completeData.matches.count { !it.chatEnded && it.messageCount > 0 }
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
                                            Icon(Icons.Default.People, contentDescription = "Candidates")
                                        }
                                    },
                                    label = { Text("Candidates") }
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
                                            Icon(Icons.Default.ChatBubble, contentDescription = "Matches")
                                        }
                                    },
                                    label = { Text("Matches") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == AppTab.Home,
                                    onClick = { selectTab(AppTab.Home) },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Home") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == AppTab.Profile,
                                    onClick = { selectTab(AppTab.Profile) },
                                    icon = { Icon(Icons.Default.AccountCircle, contentDescription = "Profile") },
                                    label = { Text("Profile") }
                                )
                                NavigationBarItem(
                                    selected = currentTab == AppTab.Explorer,
                                    onClick = { selectTab(AppTab.Explorer) },
                                    icon = { Icon(Icons.Default.FolderOpen, contentDescription = "Explorer") },
                                    label = { Text("Explorer") }
                                )
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
                            io.github.s1ddhants1.unhinge.ui.component.settings.SettingsScreen(
                                currentSubpage = settingsSubpage,
                                onNavigateToSubpage = { settingsSubpage = it },
                                prefs = prefs
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
                                            Column(modifier = Modifier.fillMaxSize()) {
                                                FrameworkStatusBanner(
                                                    isConnected = state.isFrameworkConnected,
                                                    isInjectable = state.isInjectable,
                                                    title = state.bannerTitle,
                                                    desc = state.bannerDesc
                                                )
                                                TelemetryScreen(
                                                    data = state.completeData,
                                                    isRefreshing = state.isRefreshing,
                                                    onForceStop = {
                                                        coroutineScope.launch {
                                                            val stopped = AppActions.forceStopTargetApp(context)
                                                            snackbarHostState.showSnackbar(
                                                                if (stopped) "Hinge force stopped"
                                                                else "Opened Hinge app settings"
                                                            )
                                                        }
                                                    },
                                                    onOpenApp = {
                                                        AppActions.launchTargetApp(context)
                                                    }
                                                )
                                            }
                                        }
                                        AppTab.Profile -> ProfileRawScreen(data = state.completeData)
                                        AppTab.Candidates -> CandidatesScreen(candidates = state.completeData.candidates, prefs = prefs)
                                        AppTab.Matches -> MatchesScreen(matches = state.completeData.matches)
                                        AppTab.Explorer -> RawExplorerScreen(
                                            prefFiles = state.completeData.allPrefFiles,
                                            dbTables = state.completeData.databaseTables,
                                            telemetry = state.completeData.telemetry
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadInsights()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun syncPreferences(localPrefs: android.content.SharedPreferences, remotePrefs: android.content.SharedPreferences) {
        val localTime = localPrefs.getLong("updated_at", 0L)
        val remoteTime = remotePrefs.getLong("updated_at", 0L)

        val localMap = localPrefs.all
        val remoteMap = remotePrefs.all

        val remoteEditor = remotePrefs.edit()
        val localEditor = localPrefs.edit()

        val localProvider = localPrefs.getString(Consts.PREF_AI_PROVIDER, "") ?: ""
        val remoteProvider = remotePrefs.getString(Consts.PREF_AI_PROVIDER, "") ?: ""

        if (localTime >= remoteTime || remoteMap.isEmpty() || (remoteProvider.isBlank() && localProvider.isNotBlank())) {
            for ((k, v) in localMap) {
                when (v) {
                    is Boolean -> remoteEditor.putBoolean(k, v)
                    is String -> remoteEditor.putString(k, v)
                    is Int -> remoteEditor.putInt(k, v)
                    is Long -> remoteEditor.putLong(k, v)
                    is Float -> remoteEditor.putFloat(k, v)
                }
            }
        } else {
            for ((k, v) in remoteMap) {
                when (v) {
                    is Boolean -> localEditor.putBoolean(k, v)
                    is String -> localEditor.putString(k, v)
                    is Int -> localEditor.putInt(k, v)
                    is Long -> localEditor.putLong(k, v)
                    is Float -> localEditor.putFloat(k, v)
                }
            }
        }

        for ((k, v) in localMap) {
            if (!remotePrefs.contains(k)) {
                when (v) {
                    is Boolean -> remoteEditor.putBoolean(k, v)
                    is String -> remoteEditor.putString(k, v)
                    is Int -> remoteEditor.putInt(k, v)
                    is Long -> remoteEditor.putLong(k, v)
                    is Float -> remoteEditor.putFloat(k, v)
                }
            }
        }
        for ((k, v) in remoteMap) {
            if (!localPrefs.contains(k)) {
                when (v) {
                    is Boolean -> localEditor.putBoolean(k, v)
                    is String -> localEditor.putString(k, v)
                    is Int -> localEditor.putInt(k, v)
                    is Long -> localEditor.putLong(k, v)
                    is Float -> localEditor.putFloat(k, v)
                }
            }
        }

        remoteEditor.apply()
        localEditor.apply()
        android.util.Log.i(Consts.TAG, "syncPreferences: local keys=${localMap.size}, remote keys=${remoteMap.size}")
    }
}
