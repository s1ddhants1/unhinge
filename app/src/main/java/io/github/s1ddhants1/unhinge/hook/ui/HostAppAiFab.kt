package io.github.s1ddhants1.unhinge.hook.ui

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.ai.AiWingmanHelper
import io.github.s1ddhants1.unhinge.ai.PromptEntry
import io.github.s1ddhants1.unhinge.data.HostCandidateReader
import io.github.s1ddhants1.unhinge.hook.ui.sheet.InlineAiOpenerCard
import io.github.s1ddhants1.unhinge.hook.ui.sheet.buildProfileBlock
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.model.CandidatePromptItem
import io.github.s1ddhants1.unhinge.ui.theme.Theme
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.ThemeMode
import io.github.s1ddhants1.unhinge.util.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.annotation.SuppressLint
import java.lang.ref.WeakReference
import java.util.WeakHashMap

@SuppressLint("SoonBlockedPrivateApi", "DiscouragedPrivateApi", "InternalInsetResource")
object HostAppAiFab {
    internal const val TAG_FAB_CONTAINER = "unhinge_host_fab_container"
    internal const val TAG_FAB_BUTTON = "unhinge_host_fab_button"
    internal const val TAG_NAV_CONTAINER = "unhinge_nav_container"
    internal const val TAG_NAV_BACK = "unhinge_nav_back"
    internal const val TAG_NAV_FORWARD = "unhinge_nav_forward"
    internal const val TAG_NAV_COUNTER = "unhinge_nav_counter"
    internal const val TAG_AVAILABLE_LIKES = "unhinge_available_likes"
    internal const val TAG_AI_SHEET = "unhinge_ai_sheet"

    data class CommentCardInfo(
        val commentInputRect: Rect,
        val virtualId: Int,
        val provider: AccessibilityNodeProvider?,
        val targetPrompt: PromptEntry?,
        val promptText: String?,
    )

    private data class ScannedNode(
        val virtualId: Int,
        val text: String,
        val desc: String,
        val rect: Rect,
        val isEditable: Boolean = false,
    )

    private val fabScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val candidatePromptEntriesMap = mutableMapOf<String, List<PromptEntry>>()

    private fun getOrCreateCandidateEntries(candidate: CachedCandidateProfile): List<PromptEntry> {
        val key = candidate.userId.ifBlank {
            "${candidate.firstName}_${candidate.prompts.size}_${candidate.prompts.firstOrNull()?.question}"
        }
        return candidatePromptEntriesMap.getOrPut(key) {
            if (candidate.prompts.isNotEmpty()) {
                candidate.prompts.map { p ->
                    val cleanAnswer = p.answer.replace("\\n", "\n")
                    PromptEntry(text = "${p.question}: $cleanAnswer")
                }
            } else {
                val bio = listOfNotNull(
                    candidate.jobTitle.takeIf { it.isNotBlank() }?.let { "Job: $it" },
                    candidate.location.takeIf { it.isNotBlank() }?.let { "Location: $it" },
                    candidate.datingIntention.takeIf { it.isNotBlank() }?.let { "Looking for: $it" },
                ).joinToString(", ")
                if (bio.isNotBlank()) listOf(PromptEntry(text = bio)) else emptyList()
            }
        }
    }

    private val layoutListeners = WeakHashMap<View, ViewTreeObserver.OnGlobalLayoutListener>()
    private val focusListeners = WeakHashMap<View, ViewTreeObserver.OnWindowFocusChangeListener>()
    private val overlayLifecycleOwners = WeakHashMap<View, ComposeDialogLifecycleOwner>()
    private val likesObservers = WeakHashMap<Activity, (HostLikesReader.LikesState) -> Unit>()
    internal var currentActivityRef: WeakReference<Activity>? = null
    private var currentPrefsRef: WeakReference<PreferencesManager>? = null
    private var currentCommentCardInfo: CommentCardInfo? = null
    private var currentTargetCandidate: CachedCandidateProfile? = null
    internal var lastRenderedLikesState: HostLikesReader.LikesState? = null
    internal var lastHeartColor: Int? = null
    private var lastUpdateTime = 0L
    private const val UPDATE_THROTTLE_MS = 350L
    private var lastLoggedContext: HostCandidateReader.ScreenContext? = null
    private var lastLoggedNonCandidate: Boolean? = null
    private var foregroundMonitorJob: kotlinx.coroutines.Job? = null
    private var pendingUpdateRunnable: Runnable? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun attach(activity: Activity, prefs: PreferencesManager) {
        currentActivityRef = WeakReference(activity)
        currentPrefsRef = WeakReference(prefs)
        activity.runOnUiThread {
            try {
                ensureAccessibilityEnabled(activity)
                ensureGatedPermissions(activity)
                prefs.ensureBackupPrefs(activity)
                activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

                val anyFeatureEnabled = prefs.showHostAppFab || prefs.showAvailableLikes || prefs.enableFeedNavigation
                if (!anyFeatureEnabled) {
                    remove(activity)
                    return@runOnUiThread
                }

                bindDecorLayoutListener(activity, prefs)

                if (prefs.showAvailableLikes) {
                    val observer: (HostLikesReader.LikesState) -> Unit = { state ->
                        val container = findAttachedContainer(activity)
                        if (container != null) {
                            updateLikesPill(container, state)
                        }
                    }
                    likesObservers[activity] = observer
                    HostLikesReader.registerObserver(activity, observer)
                }

                startForegroundMonitor(activity, prefs)
                scheduleUpdate(activity, 0)
                scheduleUpdate(activity, 150)
                scheduleUpdate(activity, 400)

                Log.d(Consts.TAG, "HostAppAiFab attached cleanly to ${activity.localClassName}")
            } catch (e: Throwable) {
                Log.e(Consts.TAG, "HostAppAiFab attach failed: ${e.message}", e)
            }
        }
    }

    fun onPause(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            foregroundMonitorJob?.cancel()
            foregroundMonitorJob = null
            pendingUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
            pendingUpdateRunnable = null
        }
    }

    fun onStop(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            onPause(activity)
            activity.runOnUiThread {
                findAttachedContainer(activity)?.visibility = View.GONE
            }
        }
    }

    fun remove(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
            currentPrefsRef = null
        }
        foregroundMonitorJob?.cancel()
        foregroundMonitorJob = null
        pendingUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
        pendingUpdateRunnable = null
        currentCommentCardInfo = null
        currentTargetCandidate = null
        lastRenderedLikesState = null
        lastHeartColor = null
        candidatePromptEntriesMap.clear()
        FeedNavigator.reset(activity)
        likesObservers.remove(activity)?.let { HostLikesReader.unregisterObserver(it) }

        activity.runOnUiThread {
            try {
                activity.window?.decorView?.let { decor ->
                    val listener = layoutListeners.remove(decor)
                    if (listener != null) {
                        decor.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                    }
                    val focusListener = focusListeners.remove(decor)
                    if (focusListener != null) {
                        decor.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener)
                    }
                }

                findAttachedContainer(activity)?.let { container ->
                    val sheetView = container.findViewWithTag<View>(TAG_AI_SHEET)
                    if (sheetView != null) {
                        overlayLifecycleOwners.remove(sheetView)?.handleDestroy()
                    }
                    val listener = layoutListeners.remove(container)
                    if (listener != null) {
                        container.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                    }
                    (container.parent as? ViewGroup)?.removeView(container)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun startForegroundMonitor(activity: Activity, prefs: PreferencesManager) {
        foregroundMonitorJob?.cancel()
        foregroundMonitorJob = fabScope.launch {
            while (currentActivityRef?.get() === activity && !activity.isFinishing && !activity.isDestroyed) {
                kotlinx.coroutines.delay(1000)
                scheduleUpdate(activity, 0)
            }
        }
    }

    fun scheduleUpdate(activity: Activity, delayMs: Long = 0) {
        if (activity.isFinishing || activity.isDestroyed) return
        val prefs = currentPrefsRef?.get() ?: return

        val now = SystemClock.uptimeMillis()
        val timeSinceLast = now - lastUpdateTime
        val waitTime = if (delayMs > 0) {
            maxOf(delayMs, if (timeSinceLast < UPDATE_THROTTLE_MS) UPDATE_THROTTLE_MS - timeSinceLast else 0L)
        } else {
            if (timeSinceLast < UPDATE_THROTTLE_MS) UPDATE_THROTTLE_MS - timeSinceLast else 0L
        }

        if (pendingUpdateRunnable != null && waitTime > 0) {
            return
        }

        pendingUpdateRunnable?.let { mainHandler.removeCallbacks(it) }
        val r = Runnable {
            pendingUpdateRunnable = null
            if (!activity.isFinishing && !activity.isDestroyed) {
                lastUpdateTime = SystemClock.uptimeMillis()
                performUpdate(activity, prefs)
            }
        }
        pendingUpdateRunnable = r
        if (waitTime <= 0) {
            activity.runOnUiThread(r)
        } else {
            mainHandler.postDelayed(r, waitTime)
        }
    }

    private fun bindDecorLayoutListener(activity: Activity, prefs: PreferencesManager) {
        val decor = activity.window?.decorView ?: return
        if (layoutListeners.containsKey(decor)) return

        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
            scheduleUpdate(activity, 50)
        }
        decor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        layoutListeners[decor] = layoutListener

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) {
                scheduleUpdate(activity, 0)
            }
        }
        decor.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)
        focusListeners[decor] = focusListener
    }

    private fun performUpdate(activity: Activity, prefs: PreferencesManager) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            val anyFeatureEnabled = prefs.showHostAppFab || prefs.showAvailableLikes || prefs.enableFeedNavigation
            if (!anyFeatureEnabled) {
                remove(activity)
                return
            }

            if (prefs.showAvailableLikes && !likesObservers.containsKey(activity)) {
                val observer: (HostLikesReader.LikesState) -> Unit = { state ->
                    val c = findAttachedContainer(activity)
                    if (c != null) {
                        updateLikesPill(c, state)
                    }
                }
                likesObservers[activity] = observer
                HostLikesReader.registerObserver(activity, observer)
            } else if (!prefs.showAvailableLikes && likesObservers.containsKey(activity)) {
                likesObservers.remove(activity)?.let { HostLikesReader.unregisterObserver(it) }
            }

            activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            val decor = activity.window?.decorView ?: return
            val passRect = findPassButtonBounds(decor)
            val commentInfo = findCommentCardInfo(decor)

            val screenClues = extractScreenClues(activity)
            val isCandidate = !screenClues.isNonCandidate && (passRect != null || commentInfo != null)

            val attached = findAttachedContainer(activity)
            val rootGroup = (decor as? ViewGroup)
                ?: (activity.findViewById(android.R.id.content) as? ViewGroup)

            if (isCandidate) {
                val container = if (attached != null && attached.parent === rootGroup) {
                    attached
                } else {
                    attached?.let { (it.parent as? ViewGroup)?.removeView(it) }
                    val newContainer = createContainerView(activity, prefs)
                    rootGroup?.addView(newContainer)
                    bindLayoutListener(newContainer, activity, prefs)
                    newContainer
                }
                updateControlsState(container, activity, prefs, passRect, commentInfo, screenClues)
            } else {
                if (attached != null) {
                    val sheetView = attached.findViewWithTag<View>(TAG_AI_SHEET)
                    if (sheetView != null && sheetView.visibility == View.VISIBLE) {
                        closeAiSheet(activity)
                    }
                    attached.visibility = View.GONE
                }
            }
        } catch (e: Throwable) {
            Log.w(Consts.TAG, "decor layout error: ${e.message}", e)
        }
    }

    private fun findAttachedContainer(activity: Activity): View? {
        return activity.window?.decorView?.findViewWithTag(TAG_FAB_CONTAINER)
    }

    private fun createContainerView(activity: Activity, prefs: PreferencesManager): ViewGroup {
        val density = activity.resources.displayMetrics.density
        val fabSize = (60 * density).toInt()

        val container = object : FrameLayout(activity) {
            override fun onTouchEvent(event: MotionEvent): Boolean {

                return false
            }
        }.apply {
            tag = TAG_FAB_CONTAINER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = Gravity.NO_GRAVITY
            }
            visibility = View.VISIBLE
        }

        val surfaceColor = Color.WHITE
        val strokeColor = "#EBEBEB".toColorInt()
        val iconColor = "#1A1A1A".toColorInt()
        val rippleColor = Color.argb(30, 0, 0, 0)

        val fab = FrameLayout(activity).apply {
            tag = TAG_FAB_BUTTON
            layoutParams = FrameLayout.LayoutParams(fabSize, fabSize).apply {
                gravity = Gravity.TOP or Gravity.START
            }

            val bg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(surfaceColor)
            }
            val mask = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
            }
            background = RippleDrawable(ColorStateList.valueOf(rippleColor), bg, mask)
            elevation = 16 * density
            outlineProvider = ViewOutlineProvider.BACKGROUND

            val iconView = ImageView(activity).apply {
                val iconSize = (28 * density).toInt()
                layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER
                }
                setImageDrawable(SparkleIconDrawable(iconColor))
            }
            addView(iconView)

            setOnClickListener {
                onFabClicked(activity, prefs)
            }
        }
        container.addView(fab)

        val pillHeight = (26 * density).toInt()
        val likesPill = LinearLayout(activity).apply {
            tag = TAG_AVAILABLE_LIKES
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                pillHeight
            ).apply { gravity = Gravity.TOP or Gravity.START }
            background = createNavPillDrawable(density, surfaceColor, strokeColor)
            elevation = 12 * density
            outlineProvider = ViewOutlineProvider.BACKGROUND
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)

            val iconSize = (14 * density).toInt()
            val heartIcon = ImageView(activity).apply {
                tag = "unhinge_likes_heart"
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginEnd = (4 * density).toInt()
                }
            }
            val likesText = TextView(activity).apply {
                tag = "unhinge_likes_text"
                textSize = 12f
                setTextColor(iconColor)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            }
            val roseDivider = TextView(activity).apply {
                tag = "unhinge_rose_divider"
                text = "•"
                textSize = 10f
                setTextColor(Color.parseColor("#666666"))
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginStart = (5 * density).toInt()
                    marginEnd = (5 * density).toInt()
                }
                visibility = View.GONE
            }
            val roseIcon = ImageView(activity).apply {
                tag = "unhinge_rose_icon"
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER_VERTICAL
                    marginEnd = (4 * density).toInt()
                }
                visibility = View.GONE
            }
            val roseText = TextView(activity).apply {
                tag = "unhinge_rose_text"
                textSize = 12f
                setTextColor(iconColor)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                visibility = View.GONE
            }

            addView(heartIcon)
            addView(likesText)
            addView(roseDivider)
            addView(roseIcon)
            addView(roseText)

            setOnClickListener {
                val state = HostLikesReader.getLikesInfo(activity)
                Toast.makeText(activity, state.formattedSummary, Toast.LENGTH_SHORT).show()
            }
        }
        container.addView(likesPill)

        val navCapsuleHeight = (36 * density).toInt()
        val navBtnSize = (36 * density).toInt()
        val navIconSize = (18 * density).toInt()

        val navCapsule = LinearLayout(activity).apply {
            tag = TAG_NAV_CONTAINER
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                navCapsuleHeight
            ).apply { gravity = Gravity.TOP or Gravity.START }
            background = createNavPillDrawable(density, surfaceColor, strokeColor)
            elevation = 12 * density
            outlineProvider = ViewOutlineProvider.BACKGROUND

            val backBtn = FrameLayout(activity).apply {
                tag = TAG_NAV_BACK
                layoutParams = LinearLayout.LayoutParams(navBtnSize, navBtnSize)
                val btnMask = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, btnMask)
                addView(ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(navIconSize, navIconSize).apply {
                        gravity = Gravity.CENTER
                    }
                    setImageDrawable(ChevronDrawable(iconColor, ChevronDrawable.Direction.LEFT))
                })
                setOnClickListener {
                    FeedNavigator.navigateBack(activity)
                }
            }

            val counterView = TextView(activity).apply {
                tag = TAG_NAV_COUNTER
                text = "1"
                textSize = 12.5f
                setTextColor(iconColor)
                gravity = Gravity.CENTER
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setPadding((6 * density).toInt(), 0, (6 * density).toInt(), 0)
            }

            val forwardBtn = FrameLayout(activity).apply {
                tag = TAG_NAV_FORWARD
                layoutParams = LinearLayout.LayoutParams(navBtnSize, navBtnSize)
                val btnMask = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.WHITE)
                }
                background = RippleDrawable(ColorStateList.valueOf(rippleColor), null, btnMask)
                addView(ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(navIconSize, navIconSize).apply {
                        gravity = Gravity.CENTER
                    }
                    setImageDrawable(ChevronDrawable(iconColor, ChevronDrawable.Direction.RIGHT))
                })
                setOnClickListener {
                    FeedNavigator.navigateForward(activity)
                }
            }

            addView(backBtn)
            addView(counterView)
            addView(forwardBtn)
        }
        container.addView(navCapsule)

        val sheetView = ComposeView(activity).apply {
            tag = TAG_AI_SHEET
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(sheetView)

        return container
    }

    private fun bindLayoutListener(container: View, activity: Activity, prefs: PreferencesManager) {
        var lastW = 0
        var lastH = 0
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val w = container.width
            val h = container.height
            if (w != lastW || h != lastH) {
                lastW = w
                lastH = h
                updateControlsPosition(container, activity)
            }
        }
        container.viewTreeObserver.addOnGlobalLayoutListener(listener)
        layoutListeners[container] = listener
    }

    private fun updateControlsState(
        container: View,
        activity: Activity,
        prefs: PreferencesManager,
        passRect: Rect? = null,
        detectedCommentInfo: CommentCardInfo? = null,
        clues: HostCandidateReader.ScreenClues? = null,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val screenClues = clues ?: extractScreenClues(activity)
        val pRect = passRect ?: findPassButtonBounds(container.rootView)
        val detectedComment = detectedCommentInfo ?: findCommentCardInfo(container.rootView)
        val isCommentMode = detectedComment != null

        val isCandidate = !screenClues.isNonCandidate && (pRect != null || isCommentMode)
        val sheetView = container.findViewWithTag<View>(TAG_AI_SHEET)
        val isSheetOpen = sheetView != null && sheetView.visibility == View.VISIBLE

        if (!isCandidate) {
            if (isSheetOpen) {
                closeAiSheet(activity)
            }
            container.visibility = View.GONE
            return
        }

        if (FeedNavigator.isNavigated && prefs.enableFeedNavigation) {
            FeedNavigator.refreshTotal(activity)
        }

        val cachedTarget = currentTargetCandidate
        val isTargetStillVisible = cachedTarget != null && (
            screenClues.visibleTexts.any { it.equals(cachedTarget.firstName, ignoreCase = true) } ||
            cachedTarget.prompts.any { prompt ->
                val ans = prompt.answer.trim()
                ans.length >= 4 && screenClues.visibleTexts.any { it.contains(ans, ignoreCase = true) }
            }
        )

        val activeCandidate = if (isTargetStillVisible) {
            cachedTarget
        } else {
            val feedCandidate = if (!isCommentMode) HostCandidateReader.readTargetCandidateSync(activity, screenClues) else null
            if (feedCandidate != null) {
                currentTargetCandidate = feedCandidate
            }
            currentTargetCandidate ?: feedCandidate
        }
        val isStandoutCandidate = screenClues.activeContext == HostCandidateReader.ScreenContext.STANDOUTS ||
                activeCandidate?.isStandout == true

        val commentInfo = if (isCommentMode) findCommentCardInfo(container.rootView, activeCandidate) else null
        currentCommentCardInfo = commentInfo

        val shouldShowFab = !isSheetOpen && prefs.showHostAppFab
        val showNav = !isSheetOpen && !isCommentMode && !isStandoutCandidate && prefs.enableFeedNavigation
        val showLikes = !isSheetOpen && !isCommentMode && prefs.showAvailableLikes

        val fab = container.findViewWithTag<View>(TAG_FAB_BUTTON)
        val navCapsule = container.findViewWithTag<View>(TAG_NAV_CONTAINER)
        val likesPill = container.findViewWithTag<View>(TAG_AVAILABLE_LIKES)

        fab?.visibility = if (shouldShowFab) View.VISIBLE else View.GONE
        navCapsule?.visibility = if (showNav) View.VISIBLE else View.GONE
        likesPill?.visibility = if (showLikes) View.VISIBLE else View.GONE

        if (showNav) {
            updateNavCounter(container)
        }
        if (showLikes) {
            updateLikesPill(container, HostLikesReader.getLikesInfo(activity))
        }

        val shouldShowContainer = shouldShowFab || showNav || showLikes || isSheetOpen
        container.visibility = if (shouldShowContainer) View.VISIBLE else View.GONE

        if (shouldShowContainer) {
            updateControlsPosition(container, activity, pRect, prefs)
        }
    }

    private fun updateControlsPosition(
        container: View,
        activity: Activity,
        passRect: Rect? = null,
        prefs: PreferencesManager? = null,
    ) {
        val density = activity.resources.displayMetrics.density
        val (screenWidth, screenHeight) = getWindowDimensions(activity)
        val containerWidth = container.width.takeIf { it > 0 } ?: screenWidth

        val pRect = passRect ?: findPassButtonBounds(container.rootView)

        val loc = IntArray(2)
        container.getLocationOnScreen(loc)
        val containerScreenX = loc[0]
        val containerScreenY = loc[1]

        val fabSize: Int
        val targetX: Float
        val targetY: Float

        val commentInfo = currentCommentCardInfo ?: findCommentCardInfo(container.rootView)

        if (commentInfo != null && !commentInfo.commentInputRect.isEmpty) {
            fabSize = (48 * density).toInt()
            val commentRect = commentInfo.commentInputRect
            targetX = (commentRect.right - containerScreenX - fabSize).toFloat()
            targetY = (commentRect.top - containerScreenY - fabSize - (8 * density))
        } else if (pRect != null && pRect.width() > 0 && pRect.height() > 0) {
            fabSize = pRect.width()
            val leftMargin = pRect.left - containerScreenX
            targetX = (containerWidth - leftMargin - fabSize).toFloat()
            targetY = (pRect.top - containerScreenY).toFloat()
        } else {
            fabSize = (60 * density).toInt()
            val margin = (20 * density).toInt()
            val bottomNavTop = findBottomNavTop(container.rootView, screenHeight)
                ?: (screenHeight - (76 * density).toInt())
            targetX = (containerWidth - margin - fabSize).toFloat()
            targetY = ((bottomNavTop - containerScreenY) - margin - fabSize).toFloat()
        }

        val fab = container.findViewWithTag<View>(TAG_FAB_BUTTON)
        if (fab != null) {
            if (fab.layoutParams.width != fabSize || fab.layoutParams.height != fabSize) {
                fab.layoutParams.width = fabSize
                fab.layoutParams.height = fabSize
                fab.requestLayout()
            }
            if (fab.x != targetX) fab.x = targetX
            if (fab.y != targetY) fab.y = targetY
        }

        val likesPill = container.findViewWithTag<View>(TAG_AVAILABLE_LIKES)
        if (likesPill != null && likesPill.visibility == View.VISIBLE) {
            val pillHeight = (26 * density).toInt()
            val pillW = if (likesPill.width > 0) {
                likesPill.width
            } else {
                if (likesPill.measuredWidth <= 0) {
                    likesPill.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(pillHeight, View.MeasureSpec.EXACTLY)
                    )
                }
                maxOf(likesPill.measuredWidth, (36 * density).toInt())
            }
            val pillX = targetX + (fabSize - pillW) / 2f
            val pillY = targetY - pillHeight - (6 * density)
            if (likesPill.x != pillX) likesPill.x = pillX
            if (likesPill.y != pillY) likesPill.y = pillY
        }

        val navCapsule = container.findViewWithTag<View>(TAG_NAV_CONTAINER)
        if (navCapsule != null && navCapsule.visibility == View.VISIBLE) {
            val navHeight = (36 * density).toInt()
            val navW = if (navCapsule.width > 0) {
                navCapsule.width
            } else {
                if (navCapsule.measuredWidth <= 0) {
                    navCapsule.measure(
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(navHeight, View.MeasureSpec.EXACTLY)
                    )
                }
                navCapsule.measuredWidth
            }
            val navX = (containerWidth - navW) / 2f
            val navY = targetY + (fabSize - navHeight) / 2f
            if (navCapsule.x != navX) navCapsule.x = navX
            if (navCapsule.y != navY) navCapsule.y = navY
        }
    }

    internal fun findPassButtonBounds(rootView: View?): Rect? {
        if (rootView == null || rootView.visibility != View.VISIBLE) return null
        val desc = rootView.contentDescription?.toString()?.trim()
        if (desc != null && (desc.startsWith("Skip", ignoreCase = true) || desc.startsWith("Pass", ignoreCase = true))) {
            val rect = Rect()
            rootView.getGlobalVisibleRect(rect)
            val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
            val maxLeft = displayMetrics.widthPixels * 0.45f
            val minTop = displayMetrics.heightPixels * 0.45f
            if (rect.width() > 0 && rect.height() > 0 && rect.left < maxLeft && rect.top > minTop) {
                return rect
            }
        }
        val provider = rootView.accessibilityNodeProvider
        if (provider != null) {
            val found = searchForPassButtonNode(provider, AccessibilityNodeProvider.HOST_VIEW_ID)
            if (found != null) return found
        }
        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                val found = findPassButtonBounds(rootView.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    private fun searchForPassButtonNode(
        provider: AccessibilityNodeProvider,
        virtualId: Int,
        visited: MutableSet<Int> = mutableSetOf(),
        depth: Int = 0
    ): Rect? {
        if (depth > 40 || !visited.add(virtualId)) return null
        val node = try {
            provider.createAccessibilityNodeInfo(virtualId)
        } catch (_: Throwable) { null } ?: return null

        val desc = node.contentDescription?.toString()?.trim()
        val text = node.text?.toString()?.trim()
        val isPassLabel = (desc != null && (desc.startsWith("Skip", ignoreCase = true) || desc.startsWith("Pass", ignoreCase = true) || desc.equals("Skip", ignoreCase = true) || desc.equals("Pass", ignoreCase = true))) ||
                (text != null && (text.startsWith("Skip", ignoreCase = true) || text.startsWith("Pass", ignoreCase = true) || text.equals("Skip", ignoreCase = true) || text.equals("Pass", ignoreCase = true)))

        if (isPassLabel) {
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
            val maxLeft = displayMetrics.widthPixels * 0.45f
            val minTop = displayMetrics.heightPixels * 0.45f
            if (rect.width() > 0 && rect.height() > 0 && rect.left < maxLeft && rect.top > minTop) {
                return rect
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val childId = getChildVirtualId(node, i) ?: continue
            val found = searchForPassButtonNode(provider, childId, visited, depth + 1)
            if (found != null) return found
        }
        return null
    }

    internal fun findChatInputBounds(rootView: View?): Rect? {
        if (rootView == null) return null
        val provider = rootView.accessibilityNodeProvider
        if (provider != null) {
            val found = searchForChatInputNode(provider, AccessibilityNodeProvider.HOST_VIEW_ID)
            if (found != null) return found
        }
        val direct = searchForChatInputView(rootView)
        if (direct != null) return direct
        return null
    }

    private fun searchForChatInputNode(
        provider: AccessibilityNodeProvider,
        virtualId: Int,
        visited: MutableSet<Int> = mutableSetOf(),
        depth: Int = 0
    ): Rect? {
        if (depth > 40 || !visited.add(virtualId)) return null
        val node = try {
            provider.createAccessibilityNodeInfo(virtualId)
        } catch (_: Throwable) { null } ?: return null

        val desc = node.contentDescription?.toString()?.trim().orEmpty()
        val text = node.text?.toString()?.trim().orEmpty()
        val viewId = node.viewIdResourceName?.orEmpty() ?: ""

        val isChatInput = viewId.contains("messageComposition", ignoreCase = true) ||
                desc.equals("Record voice note", ignoreCase = true) ||
                desc.equals("Send message", ignoreCase = true) ||
                text.equals("Send a message", ignoreCase = true) ||
                text.equals("Type a message", ignoreCase = true)

        if (isChatInput) {
            val rect = Rect()
            node.getBoundsInScreen(rect)
            if (rect.width() > 0 && rect.height() > 0 && rect.top > 300) {
                return rect
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val childId = getChildVirtualId(node, i) ?: continue
            val found = searchForChatInputNode(provider, childId, visited, depth + 1)
            if (found != null) return found
        }
        return null
    }

    private fun searchForChatInputView(rootView: View): Rect? {
        if (rootView.visibility != View.VISIBLE) return null
        val resName = try {
            if (rootView.id > 0) rootView.resources.getResourceEntryName(rootView.id) else ""
        } catch (_: Throwable) { "" }

        if (resName.contains("messageCompositionContainer", ignoreCase = true) ||
            resName.contains("messageComposition", ignoreCase = true) ||
            resName.contains("sendChatButtonContainer", ignoreCase = true)
        ) {
            val rect = Rect()
            rootView.getGlobalVisibleRect(rect)
            if (rect.width() > 0 && rect.height() > 0 && rect.top > 300) {
                return rect
            }
        }

        if (rootView is android.widget.EditText) {
            val hint = rootView.hint?.toString() ?: ""
            val text = rootView.text?.toString() ?: ""
            if (hint.contains("message", ignoreCase = true) || text.contains("message", ignoreCase = true) ||
                resName.contains("message", ignoreCase = true)
            ) {
                val rect = Rect()
                rootView.getGlobalVisibleRect(rect)
                if (rect.width() > 0 && rect.height() > 0 && rect.top > 300) {
                    return rect
                }
            }
        }

        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                val found = searchForChatInputView(rootView.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    fun insertReplyIntoChatInput(activity: Activity, text: String): Boolean {
        val decor = activity.window?.decorView ?: return false
        val id = try {
            activity.resources.getIdentifier("messageComposition", "id", activity.packageName)
        } catch (_: Throwable) { 0 }
        val direct = if (id > 0) activity.findViewById<android.widget.EditText>(id) else null
        val editText = direct ?: findMessageEditText(decor) ?: return false
        activity.runOnUiThread {
            editText.setText(text)
            editText.setSelection(text.length)
            editText.requestFocus()
        }
        return true
    }

    fun findCommentCardInfo(rootView: View?, activeCandidate: CachedCandidateProfile? = null): CommentCardInfo? {
        if (rootView == null) return null
        val provider = rootView.accessibilityNodeProvider
        if (provider != null) {
            val info = searchForCommentCardInProvider(provider, rootView, activeCandidate)
            if (info != null) return info
        }
        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                val info = findCommentCardInfo(rootView.getChildAt(i), activeCandidate)
                if (info != null) return info
            }
        }
        return null
    }

    private fun searchForCommentCardInProvider(
        provider: AccessibilityNodeProvider,
        hostView: View,
        activeCandidate: CachedCandidateProfile?
    ): CommentCardInfo? {
        val density = hostView.resources.displayMetrics.density
        var commentVirtualId = -1
        val commentRect = Rect()
        var hasSendLikeButton = false

        val scanned = mutableListOf<ScannedNode>()

        fun traverse(virtualId: Int, visited: MutableSet<Int>, depth: Int) {
            if (depth > 40 || !visited.add(virtualId)) return
            val node = try {
                provider.createAccessibilityNodeInfo(virtualId)
            } catch (_: Throwable) { null } ?: return

            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val text = node.text?.toString()?.trim().orEmpty()
            val viewId = node.viewIdResourceName?.orEmpty().orEmpty()
            val rect = Rect()
            node.getBoundsInScreen(rect)

            val isEditable = node.isEditable
            scanned.add(ScannedNode(virtualId, text, desc, rect, isEditable))

            val isSendLike = desc.equals("Send Like", ignoreCase = true) ||
                    desc.equals("Send like", ignoreCase = true) ||
                    text.equals("Send Like", ignoreCase = true) ||
                    text.equals("Send like", ignoreCase = true) ||
                    desc.contains("Send a Rose", ignoreCase = true) ||
                    text.contains("Send a Rose", ignoreCase = true)
            if (isSendLike) {
                hasSendLikeButton = true
            }

            val isCommentInput = (isEditable && !desc.contains("message", ignoreCase = true) && !text.contains("message", ignoreCase = true)) ||
                    desc.equals("Edit comment", ignoreCase = true) ||
                    desc.contains("Add a comment", ignoreCase = true) ||
                    text.equals("Add a comment", ignoreCase = true) ||
                    text.equals("Edit comment", ignoreCase = true) ||
                    viewId.contains("discover_add_a_comment", ignoreCase = true)

            if (isCommentInput && rect.width() > (150 * density) && rect.height() > 0 && commentVirtualId == -1) {
                commentVirtualId = virtualId
                commentRect.set(rect)
            }

            val count = node.childCount
            for (i in 0 until count) {
                val childId = getChildVirtualId(node, i) ?: continue
                traverse(childId, visited, depth + 1)
            }
        }

        traverse(AccessibilityNodeProvider.HOST_VIEW_ID, mutableSetOf(), 0)

        if (commentVirtualId == -1 && hasSendLikeButton) {
            val sendLikeNode = scanned.find {
                it.desc.contains("Send Like", ignoreCase = true) || it.text.contains("Send Like", ignoreCase = true) ||
                it.desc.contains("Send a Rose", ignoreCase = true) || it.text.contains("Send a Rose", ignoreCase = true)
            }
            if (sendLikeNode != null) {
                val above = scanned.filter {
                    it.rect.bottom <= sendLikeNode.rect.top + (10 * density) &&
                    it.rect.bottom >= sendLikeNode.rect.top - (100 * density) &&
                    it.rect.width() > (150 * density)
                }.maxByOrNull { it.rect.bottom }
                if (above != null) {
                    commentVirtualId = above.virtualId
                    commentRect.set(above.rect)
                }
            }
        }

        if (commentVirtualId == -1 || commentRect.isEmpty) {
            return null
        }

        var targetPrompt: PromptEntry? = null
        var foundPromptText: String? = null

        val nodesAbove = scanned.filter {
            it.rect.bottom <= commentRect.top + (30 * density) &&
            it.rect.bottom >= commentRect.top - (500 * density)
        }

        if (activeCandidate != null && activeCandidate.prompts.isNotEmpty()) {
            val candidatePrompts = activeCandidate.prompts
            val entries = getOrCreateCandidateEntries(activeCandidate)

            val scoredPrompts = candidatePrompts.mapIndexed { index, cp ->
                val score = scorePromptProximity(cp, nodesAbove, commentRect.top, density)
                Triple(index, cp, score)
            }.sortedByDescending { it.third }

            val bestMatch = scoredPrompts.firstOrNull { it.third > 0 }
            if (bestMatch != null) {
                val bestIdx = bestMatch.first
                val cp = bestMatch.second
                targetPrompt = entries.getOrNull(bestIdx) ?: PromptEntry(text = "${cp.question}: ${cp.answer}")
                foundPromptText = targetPrompt.text
                Log.i(Consts.TAG, "findCommentCardInfo: Precision targeted prompt[$bestIdx] '${cp.question}' score=${bestMatch.third}")
            }
        }

        if (targetPrompt == null) {
            val promptRegex = Regex("""Prompt:\s*(.+?)\.\s*Answer:\s*(.+)""", RegexOption.DOT_MATCHES_ALL)
            val matchingNode = nodesAbove.find {
                val content = it.desc.ifBlank { it.text }
                content.contains("Prompt:", ignoreCase = true)
            } ?: nodesAbove.maxByOrNull { it.rect.bottom }

            if (matchingNode != null) {
                val rawContent = matchingNode.desc.ifBlank { matchingNode.text }
                if (rawContent.isNotBlank()) {
                    foundPromptText = rawContent
                    val match = promptRegex.find(rawContent)
                    targetPrompt = if (match != null) {
                        val q = match.groupValues[1].trim()
                        val a = match.groupValues[2].trim()
                        val matchedFromCandidate = activeCandidate?.prompts?.find { cp ->
                            a.contains(cp.answer, ignoreCase = true) || cp.answer.contains(a, ignoreCase = true) ||
                            q.contains(cp.question, ignoreCase = true)
                        }
                        if (matchedFromCandidate != null) {
                            PromptEntry(text = "${matchedFromCandidate.question}: ${matchedFromCandidate.answer}")
                        } else {
                            PromptEntry(text = "$q: $a")
                        }
                    } else {
                        PromptEntry(text = rawContent)
                    }
                }
            }
        }

        return CommentCardInfo(
            commentInputRect = commentRect,
            virtualId = commentVirtualId,
            provider = provider,
            targetPrompt = targetPrompt,
            promptText = foundPromptText
        )
    }

    private fun scorePromptProximity(
        candidatePrompt: CandidatePromptItem,
        nodes: List<ScannedNode>,
        referenceY: Int,
        density: Float,
    ): Float {
        var maxScore = 0f
        val cleanQ = candidatePrompt.question.trim().lowercase()
        val cleanA = candidatePrompt.answer.trim().lowercase()

        for (node in nodes) {
            val t = node.text.trim().lowercase()
            val d = node.desc.trim().lowercase()
            val combined = "$t $d"

            var nodeScore = 0f
            if (cleanA.isNotBlank() && (combined.contains(cleanA) || (cleanA.length >= 10 && combined.contains(cleanA.take(20))))) {
                nodeScore += 100f
            } else if (cleanA.isNotBlank() && t.length >= 6 && cleanA.contains(t)) {
                nodeScore += 60f
            }

            if (cleanQ.isNotBlank() && (combined.contains(cleanQ) || (cleanQ.length >= 10 && combined.contains(cleanQ.take(20))))) {
                nodeScore += 50f
            } else if (cleanQ.isNotBlank() && t.length >= 6 && cleanQ.contains(t)) {
                nodeScore += 30f
            }

            if (nodeScore > 0f) {
                val dist = maxOf(0, referenceY - node.rect.bottom)
                val proximityBonus = maxOf(0f, 50f - (dist / (10 * density)))
                nodeScore += proximityBonus
                if (nodeScore > maxScore) {
                    maxScore = nodeScore
                }
            }
        }
        return maxScore
    }

    fun insertTextIntoComment(activity: Activity, info: CommentCardInfo, text: String): Boolean {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Unhinge Opener", text))

        var injected = false
        if (info.provider != null && info.virtualId != -1) {
            try {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                var res = info.provider.performAction(info.virtualId, AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                if (!res) {
                    info.provider.performAction(info.virtualId, AccessibilityNodeInfo.ACTION_FOCUS, null)
                    res = info.provider.performAction(info.virtualId, AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                }
                if (res) {
                    injected = true
                    Log.i(Consts.TAG, "insertTextIntoComment: ACTION_SET_TEXT succeeded on virtualId=${info.virtualId}")
                }
            } catch (e: Throwable) {
                Log.w(Consts.TAG, "insertTextIntoComment performAction failed: ${e.message}")
            }
        }

        if (!injected) {
            val decor = activity.window?.decorView
            if (decor != null) {
                val editTexts = mutableListOf<android.widget.EditText>()
                findEditTexts(decor, editTexts)
                val target = editTexts.firstOrNull { it.isFocused } ?: editTexts.firstOrNull()
                if (target != null) {
                    activity.runOnUiThread {
                        target.setText(text)
                        target.setSelection(text.length)
                        target.requestFocus()
                    }
                    injected = true
                }
            }
        }

        activity.runOnUiThread {
            Toast.makeText(
                activity,
                if (injected) "Inserted opener into comment!" else "Copied opener to clipboard!",
                Toast.LENGTH_SHORT
            ).show()
        }
        return injected
    }

    private fun findEditTexts(view: View, result: MutableList<android.widget.EditText>) {
        if (view is android.widget.EditText) {
            result.add(view)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findEditTexts(view.getChildAt(i), result)
            }
        }
    }

    private fun copyOpenerFallback(activity: Activity, text: String) {
        val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("Unhinge Opener", text))
        Toast.makeText(activity, "Copied opener to clipboard!", Toast.LENGTH_SHORT).show()
    }

    private fun findMessageEditText(view: View): android.widget.EditText? {
        if (view is android.widget.EditText) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findMessageEditText(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    internal fun findBottomNavTop(root: View?, screenHeight: Int): Int? {
        if (root == null || root.visibility != View.VISIBLE) return null
        val density = root.resources.displayMetrics.density
        val minNavHeight = (48 * density).toInt()
        val maxNavHeight = (120 * density).toInt()

        if (root.height in minNavHeight..maxNavHeight && root.width >= (root.resources.displayMetrics.widthPixels * 0.7f).toInt()) {
            val loc = IntArray(2)
            root.getLocationInWindow(loc)
            if (loc[1] > 0 && loc[1] + root.height >= screenHeight - (24 * density).toInt()) {
                return loc[1]
            }
        }

        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                val top = findBottomNavTop(root.getChildAt(i), screenHeight)
                if (top != null) return top
            }
        }
        return null
    }

    fun notifyNavUpdated(activity: Activity? = null) {
        val act = activity ?: currentActivityRef?.get() ?: return
        scheduleUpdate(act, 0)
    }

    fun ensureGatedPermissions(context: Context) {
        attempt("ensure gated permissions in default prefs", silent = true) {
            val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
            val current = prefs.getStringSet("USER_PERMISSIONS", null) ?: emptySet()
            if (!current.containsAll(HostGatedFeaturesHook.UNLOCKED_PERMISSIONS)) {
                val updated = current.toMutableSet().apply {
                    addAll(HostGatedFeaturesHook.UNLOCKED_PERMISSIONS)
                }
                prefs.edit().putStringSet("USER_PERMISSIONS", updated).apply()
                Log.i(Consts.TAG, "HostAppAiFab: granted unlocked permissions in default prefs")
            }
        }
    }

    private fun getStatusBarHeight(activity: Activity): Int {
        val resId = activity.resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (resId > 0) activity.resources.getDimensionPixelSize(resId) else (24 * activity.resources.displayMetrics.density).toInt()
    }

    fun ensureAccessibilityEnabled(context: Context) {
        try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return
            try {
                val isEnabledField = am.javaClass.getDeclaredField("mIsEnabled")
                isEnabledField.isAccessible = true
                if (!isEnabledField.getBoolean(am)) {
                    isEnabledField.setBoolean(am, true)
                }
            } catch (_: Throwable) {}

            try {
                val listenersField = am.javaClass.getDeclaredField("mAccessibilityStateChangeListeners")
                listenersField.isAccessible = true
                val listeners = listenersField.get(am) as? Map<*, *>
                listeners?.keys?.forEach { listener ->
                    if (listener is AccessibilityManager.AccessibilityStateChangeListener) {
                        try {
                            listener.onAccessibilityStateChanged(true)
                        } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {}
        } catch (_: Throwable) {}
    }

    private val getChildIdMethod by lazy {
        try {
            AccessibilityNodeInfo::class.java.getDeclaredMethod("getChildId", Int::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        } catch (e: Throwable) {
            null
        }
    }

    private val childNodeIdsField by lazy {
        try {
            AccessibilityNodeInfo::class.java.getDeclaredField("mChildNodeIds").apply {
                isAccessible = true
            }
        } catch (e: Throwable) {
            null
        }
    }

    private val getVirtualDescendantIdMethod by lazy {
        try {
            AccessibilityNodeInfo::class.java.getMethod("getVirtualDescendantId", Long::class.javaPrimitiveType).apply {
                isAccessible = true
            }
        } catch (e: Throwable) {
            null
        }
    }

    internal fun getChildVirtualId(node: AccessibilityNodeInfo, index: Int): Int? {
        val longId = try {
            getChildIdMethod?.invoke(node, index) as? Long
        } catch (_: Throwable) {
            null
        } ?: try {
            childNodeIdsField?.let { field ->
                val arrayObj = field.get(node)
                if (arrayObj != null) {
                    val getMethod = arrayObj.javaClass.getMethod("get", Int::class.javaPrimitiveType)
                    getMethod.invoke(arrayObj, index) as? Long
                } else null
            }
        } catch (_: Throwable) {
            null
        } ?: return null

        try {
            getVirtualDescendantIdMethod?.let { method ->
                val virtualId = method.invoke(null, longId) as? Int
                if (virtualId != null && virtualId != -1) return virtualId
            }
        } catch (_: Throwable) {}

        val shifted = (longId ushr 32).toInt()
        if (shifted != 0 && shifted != -1) return shifted
        val lower = (longId and 0xFFFFFFFFL).toInt()
        if (lower != -1) return lower
        return null
    }

    fun extractScreenClues(activity: Activity): HostCandidateReader.ScreenClues {
        val visibleTexts = mutableSetOf<String>()
        val scanRoot = activity.window?.decorView

        if (scanRoot != null) {
            collectVisibleTexts(scanRoot, visibleTexts, depth = 0)
        }

        val isCommentSheet = visibleTexts.any {
            it.contains("Send Like", ignoreCase = true) ||
            it.contains("Add a comment", ignoreCase = true) ||
            it.contains("Edit comment", ignoreCase = true)
        }
        val isStandoutsScreen = !isCommentSheet && visibleTexts.any {
            it.startsWith("Roses (", ignoreCase = true) ||
            it.contains("Send a Rose", ignoreCase = true) ||
            it.contains("Roses remaining", ignoreCase = true) ||
            it.contains("Give a Rose", ignoreCase = true)
        }
        val isLikesYouScreen = visibleTexts.any {
            it.contains("They like you", ignoreCase = true) ||
            (it.contains("Likes you", ignoreCase = true) && !it.equals("Likes You", ignoreCase = true))
        }
        val isMatchesScreen = visibleTexts.any {
            it.contains("No matches right now", ignoreCase = true) ||
            it.contains("Boost your profile", ignoreCase = true) ||
            it.contains("Upgrade to HingeX", ignoreCase = true) ||
            it.contains("Conversations", ignoreCase = true) ||
            it.contains("Your turn", ignoreCase = true) ||
            it.contains("Their turn", ignoreCase = true)
        }
        val isProfileScreen = visibleTexts.any {
            it.contains("Check your Signals", ignoreCase = true) ||
            it.contains("My Hinge", ignoreCase = true) ||
            it.contains("Edit Profile", ignoreCase = true)
        }

        val isChatScreen = visibleTexts.any {
            it.equals("Send a message", ignoreCase = true) ||
            it.equals("Type a message", ignoreCase = true) ||
            it.equals("Record voice note", ignoreCase = true) ||
            it.contains("messageComposition", ignoreCase = true)
        }

        val isEmptyFeed = visibleTexts.any {
            it.contains("You've seen everyone", ignoreCase = true) ||
            it.contains("Out of people", ignoreCase = true) ||
            it.contains("You're all caught up", ignoreCase = true) ||
            it.contains("Expand your preferences", ignoreCase = true)
        }

        val isNonCandidate = isMatchesScreen || isProfileScreen || isChatScreen || isEmptyFeed

        val activeContext = when {
            isChatScreen -> HostCandidateReader.ScreenContext.MATCH_CHAT
            isStandoutsScreen -> HostCandidateReader.ScreenContext.STANDOUTS
            isLikesYouScreen -> HostCandidateReader.ScreenContext.LIKES_YOU
            isMatchesScreen || isProfileScreen -> HostCandidateReader.ScreenContext.UNKNOWN
            else -> HostCandidateReader.ScreenContext.DISCOVER
        }

        if (lastLoggedContext != activeContext || lastLoggedNonCandidate != isNonCandidate) {
            lastLoggedContext = activeContext
            lastLoggedNonCandidate = isNonCandidate
            Log.d(Consts.TAG, "extractScreenClues: activeContext=$activeContext, isNonCandidate=$isNonCandidate, textsCount=${visibleTexts.size}")
        }
        return HostCandidateReader.ScreenClues(
            visibleTexts = visibleTexts,
            activeContext = activeContext,
            isNonCandidate = isNonCandidate,
        )
    }

    private fun collectVisibleTexts(view: View, result: MutableSet<String>, depth: Int) {
        if (depth > 35 || view.visibility != View.VISIBLE) return

        view.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        if (view is TextView) {
            view.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        }

        try {
            val provider = view.accessibilityNodeProvider
            if (provider != null) {
                collectVirtualTexts(provider, AccessibilityNodeProvider.HOST_VIEW_ID, result)
            }
        } catch (_: Throwable) {}

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                collectVisibleTexts(view.getChildAt(i), result, depth + 1)
            }
        }
    }

    private fun collectVirtualTexts(
        provider: AccessibilityNodeProvider,
        virtualId: Int,
        result: MutableSet<String>,
        visited: MutableSet<Int> = mutableSetOf(),
        depth: Int = 0
    ) {
        if (depth > 40 || !visited.add(virtualId)) return
        val node = try {
            provider.createAccessibilityNodeInfo(virtualId)
        } catch (_: Throwable) { null } ?: return

        node.text?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { result.add(it) }
        node.contentDescription?.toString()?.trim()?.takeIf { it.isNotBlank() }?.let { result.add(it) }

        val count = node.childCount
        for (i in 0 until count) {
            val childId = getChildVirtualId(node, i) ?: continue
            collectVirtualTexts(provider, childId, result, visited, depth + 1)
        }
    }

    internal fun getWindowDimensions(activity: Activity): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = activity.windowManager.currentWindowMetrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            activity.windowManager.defaultDisplay.getMetrics(dm)
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

    fun openAiSheet(activity: Activity, prefs: PreferencesManager) {
        activity.runOnUiThread {
            val container = findAttachedContainer(activity) ?: return@runOnUiThread
            val sheetView = container.findViewWithTag<ComposeView>(TAG_AI_SHEET) ?: return@runOnUiThread
            val owner = overlayLifecycleOwners.getOrPut(sheetView) {
                ComposeDialogLifecycleOwner()
            }
            owner.handleStart()

            val decor = activity.window?.decorView
            val lifecycleOwner = (activity as? LifecycleOwner) ?: owner
            val viewModelStoreOwner = (activity as? ViewModelStoreOwner) ?: owner
            val savedStateRegistryOwner = (activity as? SavedStateRegistryOwner) ?: owner

            if (decor != null) {
                decor.setViewTreeLifecycleOwner(lifecycleOwner)
                decor.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
                decor.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            }
            container.setViewTreeLifecycleOwner(lifecycleOwner)
            container.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            container.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            sheetView.setViewTreeLifecycleOwner(lifecycleOwner)
            sheetView.setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            sheetView.setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)

            val screenClues = extractScreenClues(activity)
            val isChat = screenClues.activeContext == HostCandidateReader.ScreenContext.MATCH_CHAT
            val candidate = currentTargetCandidate ?: if (!isChat) HostCandidateReader.readTargetCandidateSync(activity, screenClues) else null
            val commentInfo = currentCommentCardInfo ?: findCommentCardInfo(container.rootView, candidate)

            sheetView.setContent {
                val isDark = when (prefs.themeMode) {
                    ThemeMode.DARK -> true
                    ThemeMode.LIGHT -> false
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                }
                val sheetSurfaceColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF1E1E20) else androidx.compose.ui.graphics.Color(0xFFFFFFFF)
                val sheetBorderColor = if (isDark) androidx.compose.ui.graphics.Color(0xFF2C2C2E) else androidx.compose.ui.graphics.Color(0xFFE5E7EB)

                Theme(
                    themeMode = prefs.themeMode,
                    pureBlack = prefs.pureBlack,
                    themeColor = androidx.compose.ui.graphics.Color(prefs.themeColor),
                    setSystemBars = false,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
                            .clickable { closeAiSheet(activity) }
                    ) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .wrapContentHeight()
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                            color = sheetSurfaceColor,
                            border = BorderStroke(1.dp, sheetBorderColor),
                            shadowElevation = 16.dp,
                        ) {
                            InlineAiOpenerCard(
                                prefs = prefs,
                                candidate = candidate,
                                screenClues = screenClues,
                                isChatMode = isChat,
                                targetPrompt = commentInfo?.targetPrompt,
                                isCommentMode = commentInfo != null,
                                hostActivity = activity,
                                showDragHandle = true,
                                onDismiss = { closeAiSheet(activity) },
                                onInsert = { text ->
                                    if (commentInfo != null) {
                                        insertTextIntoComment(activity, commentInfo, text)
                                    } else if (isChat) {
                                        insertReplyIntoChatInput(activity, text)
                                    } else {
                                        copyOpenerFallback(activity, text)
                                    }
                                    closeAiSheet(activity)
                                }
                            )
                        }
                    }
                }
            }
            sheetView.visibility = View.VISIBLE
            container.findViewWithTag<View>(TAG_FAB_BUTTON)?.visibility = View.GONE
            container.findViewWithTag<View>(TAG_NAV_CONTAINER)?.visibility = View.GONE
            container.findViewWithTag<View>(TAG_AVAILABLE_LIKES)?.visibility = View.GONE
        }
    }

    fun closeAiSheet(activity: Activity) {
        activity.runOnUiThread {
            val container = findAttachedContainer(activity) ?: return@runOnUiThread
            val sheetView = container.findViewWithTag<ComposeView>(TAG_AI_SHEET) ?: return@runOnUiThread
            sheetView.visibility = View.GONE
            val prefs = currentPrefsRef?.get()
            if (prefs != null) {
                updateControlsState(container, activity, prefs)
            }
        }
    }

    private fun onFabClicked(activity: Activity, prefs: PreferencesManager) {
        val container = findAttachedContainer(activity)
        val sheetView = container?.findViewWithTag<ComposeView>(TAG_AI_SHEET)
        if (sheetView != null && sheetView.visibility == View.VISIBLE) {
            closeAiSheet(activity)
        } else {
            openAiSheet(activity, prefs)
        }
    }
}

private class ComposeDialogLifecycleOwner :
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateRegistryController = SavedStateRegistryController.create(this)

    init {
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    fun handleStart() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    fun handleDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}

private fun updateNavCounter(container: View) {
    val counterView = container.findViewWithTag<TextView>(HostAppAiFab.TAG_NAV_COUNTER) ?: return
    val backBtn = container.findViewWithTag<View>(HostAppAiFab.TAG_NAV_BACK)
    val forwardBtn = container.findViewWithTag<View>(HostAppAiFab.TAG_NAV_FORWARD)

    val pos = FeedNavigator.displayPosition
    val total = FeedNavigator.totalCandidates
    val newText = if (total > 0) "$pos / $total" else "$pos"
    if (counterView.text != newText) {
        counterView.text = newText
    }

    val canGoBack = FeedNavigator.currentOffset > 0
    val canGoForward = (total < 0) || (total > 1 && FeedNavigator.currentOffset < total - 1)

    if (backBtn?.isEnabled != canGoBack) {
        backBtn?.isEnabled = canGoBack
        backBtn?.alpha = if (canGoBack) 1.0f else 0.35f
    }
    if (forwardBtn?.isEnabled != canGoForward) {
        forwardBtn?.isEnabled = canGoForward
        forwardBtn?.alpha = if (canGoForward) 1.0f else 0.35f
    }
}

private fun updateLikesPill(container: View, state: HostLikesReader.LikesState) {
    val likesPill = container.findViewWithTag<ViewGroup>(HostAppAiFab.TAG_AVAILABLE_LIKES) ?: return
    val density = container.resources.displayMetrics.density
    val defaultTextColor = Color.parseColor("#1A1A1A")

    val heartColor = when {
        state.availableLikes == 0 -> Color.parseColor("#E53935")
        state.availableLikes in 1..2 -> Color.parseColor("#FFA000")
        else -> Color.parseColor("#ED5564")
    }

    if (HostAppAiFab.lastRenderedLikesState == state && HostAppAiFab.lastHeartColor == heartColor) {
        return
    }
    HostAppAiFab.lastRenderedLikesState = state
    HostAppAiFab.lastHeartColor = heartColor

    val roseColor = Color.parseColor("#F06292")

    val heartIcon = likesPill.findViewWithTag<ImageView>("unhinge_likes_heart")
    val likesText = likesPill.findViewWithTag<TextView>("unhinge_likes_text")
    val roseDivider = likesPill.findViewWithTag<TextView>("unhinge_rose_divider")
    val roseIcon = likesPill.findViewWithTag<ImageView>("unhinge_rose_icon")
    val roseText = likesPill.findViewWithTag<TextView>("unhinge_rose_text")

    heartIcon?.setImageDrawable(HingeIcons.getHeartDrawable(container.context, heartColor))

    likesText?.apply {
        text = state.displayLikes
        setTextColor(if (state.availableLikes == 0) Color.parseColor("#D32F2F") else defaultTextColor)
    }

    if (state.availableSuperlikes > 0) {
        roseDivider?.apply {
            visibility = View.VISIBLE
            setTextColor(Color.parseColor("#666666"))
        }
        roseIcon?.apply {
            visibility = View.VISIBLE
            setImageDrawable(HingeIcons.getRoseDrawable(container.context, roseColor))
        }
        roseText?.apply {
            visibility = View.VISIBLE
            text = state.displaySuperlikes
            setTextColor(defaultTextColor)
        }
    } else {
        roseDivider?.visibility = View.GONE
        roseIcon?.visibility = View.GONE
        roseText?.visibility = View.GONE
    }

    val pillHeight = (26 * density).toInt()
    if (likesPill.width <= 0) {
        likesPill.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(pillHeight, View.MeasureSpec.EXACTLY)
        )
    }
    val pillW = if (likesPill.width > 0) likesPill.width else maxOf(likesPill.measuredWidth, (36 * density).toInt())
    val fab = (likesPill.parent as? ViewGroup)?.findViewWithTag<View>(HostAppAiFab.TAG_FAB_BUTTON)
    if (fab != null && fab.visibility == View.VISIBLE) {
        val fabCenterX = fab.x + fab.width / 2f
        likesPill.x = fabCenterX - pillW / 2f
        likesPill.y = fab.y - pillHeight - (6 * density)
    }
}

private fun createNavPillDrawable(
    density: Float,
    surfaceColor: Int,
    strokeColor: Int
): Drawable {
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 18 * density
        setColor(surfaceColor)
        setStroke((1 * density).toInt(), strokeColor)
    }
}

private class ChevronDrawable(
    private val iconColor: Int = Color.BLACK,
    val direction: Direction = Direction.RIGHT
) : Drawable() {
    enum class Direction { LEFT, RIGHT }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = iconColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val chevronPath = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        chevronPath.reset()
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        paint.strokeWidth = minOf(w, h) * 0.13f
        val cx = w / 2f
        val cy = h / 2f
        val arm = minOf(w, h) * 0.28f

        when (direction) {
            Direction.LEFT -> {
                chevronPath.moveTo(cx + arm * 0.45f, cy - arm)
                chevronPath.lineTo(cx - arm * 0.45f, cy)
                chevronPath.lineTo(cx + arm * 0.45f, cy + arm)
            }
            Direction.RIGHT -> {
                chevronPath.moveTo(cx - arm * 0.45f, cy - arm)
                chevronPath.lineTo(cx + arm * 0.45f, cy)
                chevronPath.lineTo(cx - arm * 0.45f, cy + arm)
            }
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(chevronPath, paint)
    }

    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private class SparkleIconDrawable(private val iconColor: Int = Color.BLACK) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = iconColor
        style = Paint.Style.FILL
    }
    private val path = Path()

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        path.reset()
        val w = bounds.width().toFloat()
        val h = bounds.height().toFloat()
        if (w <= 0f || h <= 0f) return

        addSparkle(path, cx = w * 0.44f, cy = h * 0.52f, radius = minOf(w, h) * 0.36f)

        addSparkle(path, cx = w * 0.74f, cy = h * 0.28f, radius = minOf(w, h) * 0.18f)
    }

    private fun addSparkle(p: Path, cx: Float, cy: Float, radius: Float) {
        val r = radius
        val inner = r * 0.24f
        p.moveTo(cx, cy - r)
        p.quadTo(cx + inner, cy - inner, cx + r, cy)
        p.quadTo(cx + inner, cy + inner, cx, cy + r)
        p.quadTo(cx - inner, cy + inner, cx - r, cy)
        p.quadTo(cx - inner, cy - inner, cx, cy - r)
        p.close()
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, paint)
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
