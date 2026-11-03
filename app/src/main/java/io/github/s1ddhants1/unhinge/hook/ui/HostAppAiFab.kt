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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.toColorInt
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
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
import io.github.s1ddhants1.unhinge.util.attempt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object HostAppAiFab {
    internal const val TAG_FAB_CONTAINER = "unhinge_host_fab_container"
    internal const val TAG_FAB_BUTTON = "unhinge_host_fab_button"
    internal const val TAG_NAV_CONTAINER = "unhinge_nav_container"
    internal const val TAG_NAV_BACK = "unhinge_nav_back"
    internal const val TAG_NAV_FORWARD = "unhinge_nav_forward"
    internal const val TAG_NAV_COUNTER = "unhinge_nav_counter"
    internal const val TAG_AVAILABLE_LIKES = "unhinge_available_likes"
    internal const val TAG_INLINE_OPENER_CARD = "unhinge_inline_opener_card"
    internal const val TAG_PROMPT_SECTION_CARD_PREFIX = "unhinge_prompt_section_card_"
    internal const val MAX_SECTION_CARDS = 3

    data class CommentCardInfo(
        val commentInputRect: Rect,
        val virtualId: Int,
        val provider: AccessibilityNodeProvider?,
        val targetPrompt: PromptEntry?,
        val promptText: String?,
    )

    data class PromptSectionInfo(
        val promptIndex: Int,
        val targetPrompt: PromptEntry,
        val bounds: Rect,
        val virtualId: Int,
        val provider: AccessibilityNodeProvider?,
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
    private var currentPromptSections: List<PromptSectionInfo> = emptyList()

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
    private val preDrawListeners = WeakHashMap<View, ViewTreeObserver.OnPreDrawListener>()
    private val likesObservers = WeakHashMap<Activity, (HostLikesReader.LikesState) -> Unit>()
    private val overlayLifecycleOwners = WeakHashMap<View, ComposeDialogLifecycleOwner>()
    internal var currentActivityRef: WeakReference<Activity>? = null
    private var currentPrefsRef: WeakReference<PreferencesManager>? = null
    private var currentCommentCardInfo: CommentCardInfo? = null
    private var lastCommentTargetKey: String? = null
    private var isInlineOpenerManuallyToggled: Boolean = false
    private var isCommentCardDismissed: Boolean = false

    fun attach(activity: Activity, prefs: PreferencesManager) {
        currentActivityRef = WeakReference(activity)
        currentPrefsRef = WeakReference(prefs)
        activity.runOnUiThread {
            try {
                ensureAccessibilityEnabled(activity)
                ensureUnlimitedUndos(activity)
                prefs.ensureBackupPrefs(activity)
                activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

                val anyFeatureEnabled = prefs.showHostAppFab || prefs.showAvailableLikes || prefs.enableFeedNavigation
                if (!anyFeatureEnabled) {
                    remove(activity)
                    return@runOnUiThread
                }

                bindDecorLayoutListener(activity, prefs)

                val observer: (HostLikesReader.LikesState) -> Unit = { state ->
                    val container = findAttachedContainer(activity)
                    if (container != null) {
                        updateLikesPill(container, state)
                    }
                }
                likesObservers[activity] = observer
                HostLikesReader.registerObserver(activity, observer)

                Log.d(Consts.TAG, "HostAppAiFab attached cleanly to ${activity.localClassName}")
            } catch (e: Throwable) {
                Log.e(Consts.TAG, "HostAppAiFab attach failed: ${e.message}", e)
            }
        }
    }

    fun remove(activity: Activity) {
        if (currentActivityRef?.get() === activity) {
            currentActivityRef = null
            currentPrefsRef = null
        }
        currentCommentCardInfo = null
        lastCommentTargetKey = null
        isInlineOpenerManuallyToggled = false
        currentPromptSections = emptyList()
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
                    val preDraw = preDrawListeners.remove(decor)
                    if (preDraw != null) {
                        decor.viewTreeObserver.removeOnPreDrawListener(preDraw)
                    }
                }

                findAttachedContainer(activity)?.let { container ->
                    val inlineCard = container.findViewWithTag<View>(TAG_INLINE_OPENER_CARD)
                    if (inlineCard != null) {
                        overlayLifecycleOwners.remove(inlineCard)?.handleDestroy()
                    }
                    for (i in 0 until MAX_SECTION_CARDS) {
                        val card = container.findViewWithTag<View>("$TAG_PROMPT_SECTION_CARD_PREFIX$i")
                        if (card != null) {
                            overlayLifecycleOwners.remove(card)?.handleDestroy()
                        }
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

    private fun bindDecorLayoutListener(activity: Activity, prefs: PreferencesManager) {
        val decor = activity.window?.decorView ?: return
        if (layoutListeners.containsKey(decor)) return

        var lastCheckTime = 0L
        val updateCheck = {
            val now = SystemClock.uptimeMillis()
            if (now - lastCheckTime >= 100) {
                lastCheckTime = now
                try {
                    activity.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    val passRect = findPassButtonBounds(decor)
                    val chatInputRect = if (passRect == null) findChatInputBounds(decor) else null
                    val commentInfo = findCommentCardInfo(decor)
                    val hasTarget = passRect != null || chatInputRect != null || commentInfo != null
                    val attached = findAttachedContainer(activity)
                    val contentFrame = (activity.findViewById(android.R.id.content) as? ViewGroup)
                        ?: (decor as? ViewGroup)

                    if (hasTarget) {
                        val container = if (attached != null && attached.parent === contentFrame) {
                            attached
                        } else {
                            attached?.let { (it.parent as? ViewGroup)?.removeView(it) }
                            val newContainer = createContainerView(activity, prefs)
                            contentFrame?.addView(newContainer)
                            bindLayoutListener(newContainer, activity, prefs)
                            newContainer
                        }
                        updateControlsState(container, activity, prefs, passRect, chatInputRect, commentInfo)
                    } else {
                        attached?.visibility = View.GONE
                    }
                } catch (e: Throwable) {
                    Log.w(Consts.TAG, "decor layout error: ${e.message}", e)
                }
            }
        }

        val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updateCheck() }
        val preDrawListener = ViewTreeObserver.OnPreDrawListener {
            val container = findAttachedContainer(activity)
            if (container != null && container.visibility == View.VISIBLE) {
                val density = activity.resources.displayMetrics.density
                val loc = IntArray(2)
                container.getLocationOnScreen(loc)
                val containerScreenY = loc[1]
                val (_, screenHeight) = getWindowDimensions(activity)
                val horizontalMargin = (16 * density).toInt()

                val info = currentCommentCardInfo
                val inlineCard = container.findViewWithTag<View>(TAG_INLINE_OPENER_CARD)
                if (info != null && inlineCard != null && inlineCard.visibility == View.VISIBLE) {
                    if (info.provider != null && info.virtualId != -1) {
                        try {
                            val node = info.provider.createAccessibilityNodeInfo(info.virtualId)
                            if (node != null) {
                                node.getBoundsInScreen(info.commentInputRect)
                            }
                        } catch (_: Throwable) {}
                    }
                    updateControlsPosition(container, activity)
                } else if (currentPromptSections.isNotEmpty()) {
                    val rect = Rect()
                    for (section in currentPromptSections) {
                        val cardTag = "$TAG_PROMPT_SECTION_CARD_PREFIX${section.promptIndex}"
                        val card = container.findViewWithTag<View>(cardTag) ?: continue
                        if (section.provider != null && section.virtualId != -1) {
                            try {
                                val node = section.provider.createAccessibilityNodeInfo(section.virtualId)
                                if (node != null) {
                                    node.getBoundsInScreen(rect)
                                    if (rect.width() > 0 && rect.height() > 0) {
                                        card.x = horizontalMargin.toFloat()
                                        card.y = rect.bottom - containerScreenY + (8 * density)
                                        val isVisible = rect.bottom > 0 && rect.top < screenHeight
                                        card.visibility = if (isVisible) View.VISIBLE else View.GONE
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }
            updateCheck()
            true
        }

        decor.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
        decor.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        layoutListeners[decor] = layoutListener
        preDrawListeners[decor] = preDrawListener
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
                val isChat = findPassButtonBounds(activity.window?.decorView) == null &&
                        findChatInputBounds(activity.window?.decorView) != null
                Log.i(Consts.TAG, "HostAppAiFab clicked! Toggling inline opener (isChatMode=$isChat)...")
                toggleInlineOpenerCard(activity, prefs, isChatMode = isChat)
            }
        }
        container.addView(fab)

        val inlineLifecycleOwner = ComposeDialogLifecycleOwner()
        inlineLifecycleOwner.handleStart()
        container.setViewTreeLifecycleOwner(inlineLifecycleOwner)
        container.setViewTreeViewModelStoreOwner(inlineLifecycleOwner)
        container.setViewTreeSavedStateRegistryOwner(inlineLifecycleOwner)

        val inlineCard = ComposeView(activity).apply {
            tag = TAG_INLINE_OPENER_CARD
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(inlineLifecycleOwner))
            setViewTreeLifecycleOwner(inlineLifecycleOwner)
            setViewTreeViewModelStoreOwner(inlineLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(inlineLifecycleOwner)
        }
        overlayLifecycleOwners[inlineCard] = inlineLifecycleOwner
        container.addView(inlineCard)

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
        chatInputRect: Rect? = null,
        detectedCommentInfo: CommentCardInfo? = null,
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val pRect = passRect ?: findPassButtonBounds(container.rootView)
        val cRect = if (pRect == null) (chatInputRect ?: findChatInputBounds(container.rootView)) else null

        val screenClues = extractScreenClues(activity)
        val isFeedMode = pRect != null || screenClues.activeContext == HostCandidateReader.ScreenContext.DISCOVER || screenClues.activeContext == HostCandidateReader.ScreenContext.STANDOUTS
        val isChatMode = !isFeedMode && (cRect != null || screenClues.activeContext == HostCandidateReader.ScreenContext.MATCH_CHAT)

        if (!isFeedMode && FeedNavigator.isNavigated) {
            FeedNavigator.reset(activity)
        }
        if (isFeedMode && prefs.enableFeedNavigation) {
            FeedNavigator.refreshTotal(activity)
        }
        val totalCandidates = FeedNavigator.totalCandidates
        Log.i(Consts.TAG, "updateControlsState: passRect=$pRect, chatInputRect=$cRect, isFeed=$isFeedMode, isChat=$isChatMode, total=$totalCandidates")

        val activeCandidate = if (isFeedMode) HostCandidateReader.readTargetCandidateSync(activity, screenClues) else null
        val isStandoutCandidate = screenClues.activeContext == HostCandidateReader.ScreenContext.STANDOUTS ||
                activeCandidate?.isStandout == true

        val commentInfo = detectedCommentInfo ?: findCommentCardInfo(container.rootView, activeCandidate)
        currentCommentCardInfo = commentInfo

        val candidateEntries = if (activeCandidate != null) {
            getOrCreateCandidateEntries(activeCandidate)
        } else emptyList()

        // Asynchronously trigger batch generation for all prompts of active candidate so each prompt has its opener ready
        if (activeCandidate != null && candidateEntries.isNotEmpty() && prefs.showHostAppFab) {
            val needsGeneration = candidateEntries.any { it.suggestedReplyFlow.value.isNullOrBlank() && !it.isGeneratingFlow.value }
            val isAiReady = !AiWingmanHelper.isApiKeyRequired(prefs.aiProvider, prefs.openRouterModel) || prefs.openRouterApiKey.isNotBlank()
            if (needsGeneration && isAiReady) {
                val profileBlock = buildProfileBlock(activeCandidate)
                AiWingmanHelper.generateReplies(
                    prompts = candidateEntries,
                    apiKey = prefs.openRouterApiKey,
                    baseUrl = prefs.openRouterBaseUrl,
                    model = prefs.openRouterModel,
                    scope = fabScope,
                    context = activity,
                    prefs = prefs,
                    provider = prefs.aiProvider,
                    useStreaming = true,
                    systemPrompt = prefs.aiCustomSystemPrompt,
                    profileBlock = profileBlock,
                    forceRefresh = false,
                    temperature = prefs.aiTemperature,
                    topP = prefs.aiTopP,
                    reasoningEffort = prefs.aiReasoningEffort,
                )
            }
        }

        val shouldShowFab = (isFeedMode || isChatMode) && prefs.showHostAppFab
        val showNav = isFeedMode && !isStandoutCandidate && prefs.enableFeedNavigation && (totalCandidates > 1 || FeedNavigator.isNavigated)
        val showLikes = isFeedMode && prefs.showAvailableLikes

        val fab = container.findViewWithTag<View>(TAG_FAB_BUTTON)
        val navCapsule = container.findViewWithTag<View>(TAG_NAV_CONTAINER)
        val likesPill = container.findViewWithTag<View>(TAG_AVAILABLE_LIKES)
        val inlineCard = container.findViewWithTag<ComposeView>(TAG_INLINE_OPENER_CARD)

        fab?.visibility = if (shouldShowFab) View.VISIBLE else View.GONE
        navCapsule?.visibility = if (showNav) View.VISIBLE else View.GONE
        likesPill?.visibility = if (showLikes) View.VISIBLE else View.GONE

        if (showNav) {
            updateNavCounter(container)
        }
        if (showLikes) {
            updateLikesPill(container, HostLikesReader.getLikesInfo(activity))
        }

        // Detect prompt sections on screen in feed mode
        val detectedSections = if (isFeedMode && commentInfo == null) {
            findPromptSections(container.rootView, activeCandidate, candidateEntries)
        } else emptyList()
        currentPromptSections = detectedSections

        // Manage in-section ComposeViews for each prompt section
        for (i in 0 until MAX_SECTION_CARDS) {
            val cardTag = "$TAG_PROMPT_SECTION_CARD_PREFIX$i"
            var card = container.findViewWithTag<ComposeView>(cardTag)
            val section = detectedSections.find { it.promptIndex == i }

            if (section != null && isFeedMode && commentInfo == null) {
                if (card == null) {
                    val owner = ComposeDialogLifecycleOwner().also { it.handleStart() }
                    card = ComposeView(activity).apply {
                        tag = cardTag
                        visibility = View.GONE
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply { gravity = Gravity.TOP or Gravity.START }
                        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(owner))
                        setViewTreeLifecycleOwner(owner)
                        setViewTreeViewModelStoreOwner(owner)
                        setViewTreeSavedStateRegistryOwner(owner)
                    }
                    overlayLifecycleOwners[card] = owner
                    (container as? ViewGroup)?.addView(card)
                }

                val entry = section.targetPrompt
                card.setContent {
                    Theme(
                        themeMode = prefs.themeMode,
                        pureBlack = prefs.pureBlack,
                        themeColor = androidx.compose.ui.graphics.Color(prefs.themeColor),
                        setSystemBars = false,
                    ) {
                        InlineAiOpenerCard(
                            prefs = prefs,
                            targetPrompt = entry,
                            isCommentMode = false,
                            isSectionCard = true,
                            candidate = activeCandidate,
                            screenClues = screenClues,
                            isChatMode = false,
                            hostActivity = activity,
                            onDismiss = null,
                            onInsert = null,
                        )
                    }
                }
            } else {
                card?.visibility = View.GONE
            }
        }

        if (commentInfo != null) {
            val targetKey = commentInfo.targetPrompt?.text ?: commentInfo.promptText ?: "comment_active"
            val isNewTarget = lastCommentTargetKey != targetKey
            if (isNewTarget) {
                lastCommentTargetKey = targetKey
                isCommentCardDismissed = false
            }

            if (!isCommentCardDismissed) {
                if (inlineCard != null && (isNewTarget || inlineCard.visibility != View.VISIBLE)) {
                    val owner = overlayLifecycleOwners[inlineCard] ?: ComposeDialogLifecycleOwner().also {
                        overlayLifecycleOwners[inlineCard] = it
                    }
                    owner.handleStart()
                    container.setViewTreeLifecycleOwner(owner)
                    container.setViewTreeViewModelStoreOwner(owner)
                    container.setViewTreeSavedStateRegistryOwner(owner)
                    inlineCard.setViewTreeLifecycleOwner(owner)
                    inlineCard.setViewTreeViewModelStoreOwner(owner)
                    inlineCard.setViewTreeSavedStateRegistryOwner(owner)

                    inlineCard.setContent {
                        Theme(
                            themeMode = prefs.themeMode,
                            pureBlack = prefs.pureBlack,
                            themeColor = androidx.compose.ui.graphics.Color(prefs.themeColor),
                            setSystemBars = false,
                        ) {
                            InlineAiOpenerCard(
                                prefs = prefs,
                                targetPrompt = commentInfo.targetPrompt,
                                isCommentMode = true,
                                isSectionCard = false,
                                candidate = activeCandidate,
                                screenClues = screenClues,
                                isChatMode = false,
                                hostActivity = activity,
                                onDismiss = {
                                    activity.runOnUiThread {
                                        inlineCard.visibility = View.GONE
                                        isInlineOpenerManuallyToggled = false
                                        isCommentCardDismissed = true
                                    }
                                },
                                onInsert = { text ->
                                    insertTextIntoComment(activity, commentInfo, text)
                                },
                            )
                        }
                    }
                    inlineCard.visibility = View.VISIBLE
                }
            } else if (inlineCard != null && inlineCard.visibility == View.VISIBLE) {
                inlineCard.visibility = View.GONE
            }
        } else if (!isInlineOpenerManuallyToggled) {
            if (inlineCard != null && inlineCard.visibility == View.VISIBLE) {
                inlineCard.visibility = View.GONE
            }
            lastCommentTargetKey = null
            isCommentCardDismissed = false
        }

        val hasVisibleSectionCards = detectedSections.any { section ->
            container.findViewWithTag<View>("$TAG_PROMPT_SECTION_CARD_PREFIX${section.promptIndex}")?.visibility == View.VISIBLE
        }
        val shouldShowContainer = shouldShowFab || showNav || showLikes || (commentInfo != null) || (inlineCard?.visibility == View.VISIBLE) || detectedSections.isNotEmpty() || hasVisibleSectionCards
        container.visibility = if (shouldShowContainer) View.VISIBLE else View.GONE

        Log.i(Consts.TAG, "updateControlsState: pRect=$pRect, cRect=$cRect, fab=$shouldShowFab, nav=$showNav, likes=$showLikes, standout=$isStandoutCandidate, commentInfo=${commentInfo != null}, sections=${detectedSections.size}, containerVis=${if (shouldShowContainer) "VISIBLE" else "GONE"}")

        if (shouldShowContainer) {
            updateControlsPosition(container, activity, pRect, cRect)
        }
    }

    private fun updateControlsPosition(
        container: View,
        activity: Activity,
        passRect: Rect? = null,
        chatInputRect: Rect? = null,
    ) {
        val density = activity.resources.displayMetrics.density
        val (screenWidth, screenHeight) = getWindowDimensions(activity)
        val containerWidth = container.width.takeIf { it > 0 } ?: screenWidth

        val pRect = passRect ?: findPassButtonBounds(container.rootView)
        val cRect = if (pRect == null) (chatInputRect ?: findChatInputBounds(container.rootView)) else null

        val loc = IntArray(2)
        container.getLocationOnScreen(loc)
        val containerScreenX = loc[0]
        val containerScreenY = loc[1]

        val fabSize: Int
        val targetX: Float
        val targetY: Float

        if (pRect != null && pRect.width() > 0 && pRect.height() > 0) {
            fabSize = pRect.width()
            val leftMargin = pRect.left - containerScreenX
            targetX = (containerWidth - leftMargin - fabSize).toFloat()
            targetY = (pRect.top - containerScreenY).toFloat()
        } else if (cRect != null && cRect.width() > 0 && cRect.height() > 0) {
            fabSize = (48 * density).toInt()
            val margin = (16 * density).toInt()
            targetX = (containerWidth - margin - fabSize).toFloat()
            targetY = (cRect.top - containerScreenY) - (8 * density) - fabSize
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
            fab.layoutParams.width = fabSize
            fab.layoutParams.height = fabSize
            fab.x = targetX
            fab.y = targetY
        }

        val likesPill = container.findViewWithTag<View>(TAG_AVAILABLE_LIKES)
        if (likesPill != null && likesPill.visibility == View.VISIBLE) {
            val pillHeight = (26 * density).toInt()
            likesPill.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(pillHeight, View.MeasureSpec.EXACTLY)
            )
            val pillW = maxOf(likesPill.measuredWidth, (36 * density).toInt())
            if (fab != null && fab.visibility == View.VISIBLE) {
                likesPill.x = targetX + (fabSize - pillW) / 2f
                likesPill.y = targetY - pillHeight - (6 * density)
            } else {
                likesPill.x = containerWidth - (20 * density) - pillW
                likesPill.y = targetY + (fabSize - pillHeight) / 2f
            }
        }

        val navCapsule = container.findViewWithTag<View>(TAG_NAV_CONTAINER)
        if (navCapsule != null && navCapsule.visibility == View.VISIBLE) {
            val navHeight = (36 * density).toInt()
            navCapsule.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(navHeight, View.MeasureSpec.EXACTLY)
            )
            val navW = navCapsule.measuredWidth
            navCapsule.x = (containerWidth - navW) / 2f
            navCapsule.y = targetY + (fabSize - navHeight) / 2f
        }

        val commentInfo = currentCommentCardInfo ?: findCommentCardInfo(container.rootView)
        val inlineCard = container.findViewWithTag<View>(TAG_INLINE_OPENER_CARD)
        if (inlineCard != null && inlineCard.visibility == View.VISIBLE) {
            if (commentInfo != null) {
                // In comment mode: Render directly on the add comment textbox!
                val commentRect = commentInfo.commentInputRect
                val targetWidth = commentRect.width().coerceAtLeast((200 * density).toInt())
                inlineCard.layoutParams.width = targetWidth
                inlineCard.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                inlineCard.measure(
                    View.MeasureSpec.makeMeasureSpec(targetWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )

                val cardX = (commentRect.left - containerScreenX).toFloat()
                val cardY = (commentRect.top - containerScreenY).toFloat()
                val minY = getStatusBarHeight(activity) + (8 * density)

                inlineCard.x = cardX
                inlineCard.y = maxOf(cardY, minY)
            } else {
                val horizontalMargin = (12 * density).toInt()
                val cardWidth = (containerWidth - (horizontalMargin * 2)).coerceAtLeast((200 * density).toInt())
                inlineCard.measure(
                    View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )
                val cardHeight = inlineCard.measuredHeight

                val bottomAnchor = when {
                    pRect != null && pRect.top > 0 -> pRect.top - containerScreenY - (10 * density).toInt()
                    cRect != null && cRect.top > 0 -> cRect.top - containerScreenY - (10 * density).toInt()
                    else -> {
                        val bottomNavTop = findBottomNavTop(container.rootView, screenHeight)
                            ?: (screenHeight - (76 * density).toInt())
                        bottomNavTop - containerScreenY - (10 * density).toInt()
                    }
                }

                inlineCard.layoutParams.width = cardWidth
                inlineCard.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                inlineCard.x = horizontalMargin.toFloat()
                inlineCard.y = (bottomAnchor - cardHeight).coerceAtLeast((40 * density).toInt()).toFloat()
            }
        }

        // In feed mode without comment box: position each prompt section card below its prompt section!
        if (commentInfo == null && currentPromptSections.isNotEmpty()) {
            val horizontalMargin = (16 * density).toInt()
            val cardWidth = (containerWidth - (horizontalMargin * 2)).coerceAtLeast((200 * density).toInt())
            for (section in currentPromptSections) {
                val cardTag = "$TAG_PROMPT_SECTION_CARD_PREFIX${section.promptIndex}"
                val card = container.findViewWithTag<View>(cardTag) ?: continue
                val promptRect = section.bounds

                card.layoutParams.width = cardWidth
                card.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                card.measure(
                    View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                )

                card.x = horizontalMargin.toFloat()
                card.y = promptRect.bottom - containerScreenY + (8 * density)

                val isVisible = promptRect.bottom > 0 && promptRect.top < screenHeight
                card.visibility = if (isVisible) View.VISIBLE else View.GONE
            }
        }
    }

    internal fun findPassButtonBounds(rootView: View?): Rect? {
        if (rootView == null) return null
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
        val isPassLabel = (desc != null && (desc.startsWith("Skip", ignoreCase = true) || desc.equals("Skip", ignoreCase = true) || desc.equals("Pass", ignoreCase = true))) ||
                (text != null && (text.startsWith("Skip", ignoreCase = true) || text.equals("Skip", ignoreCase = true) || text.equals("Pass", ignoreCase = true)))

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
            val promptNode = nodesAbove.maxByOrNull { it.rect.bottom }
            if (promptNode != null && promptNode.text.isNotBlank()) {
                foundPromptText = promptNode.text
                val match = promptRegex.find(promptNode.text)
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
                    PromptEntry(text = promptNode.text)
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

    internal fun findPromptSections(
        rootView: View?,
        candidate: CachedCandidateProfile?,
        entries: List<PromptEntry>,
    ): List<PromptSectionInfo> {
        if (rootView == null) return emptyList()
        val provider = rootView.accessibilityNodeProvider
        if (provider != null) {
            val found = searchPromptSectionsInProvider(provider, rootView, candidate, entries)
            if (found.isNotEmpty()) return found
        }
        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                val found = findPromptSections(rootView.getChildAt(i), candidate, entries)
                if (found.isNotEmpty()) return found
            }
        }
        return emptyList()
    }

    private fun searchPromptSectionsInProvider(
        provider: AccessibilityNodeProvider,
        hostView: View,
        candidate: CachedCandidateProfile?,
        entries: List<PromptEntry>,
    ): List<PromptSectionInfo> {
        val density = hostView.resources.displayMetrics.density
        val scanned = mutableListOf<ScannedNode>()

        fun traverse(virtualId: Int, visited: MutableSet<Int>, depth: Int) {
            if (depth > 40 || !visited.add(virtualId)) return
            val node = try {
                provider.createAccessibilityNodeInfo(virtualId)
            } catch (_: Throwable) { null } ?: return

            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            val text = node.text?.toString()?.trim().orEmpty()
            val rect = Rect()
            node.getBoundsInScreen(rect)

            if (rect.width() > 0 && rect.height() > 0 && (text.isNotBlank() || desc.isNotBlank() || node.isClickable)) {
                scanned.add(ScannedNode(virtualId, text, desc, rect, node.isEditable))
            }

            val count = node.childCount
            for (i in 0 until count) {
                val childId = getChildVirtualId(node, i) ?: continue
                traverse(childId, visited, depth + 1)
            }
        }

        traverse(AccessibilityNodeProvider.HOST_VIEW_ID, mutableSetOf(), 0)

        val matchedSections = mutableListOf<PromptSectionInfo>()
        val candidatePrompts = candidate?.prompts.orEmpty()

        if (candidatePrompts.isNotEmpty()) {
            for (idx in 0 until minOf(candidatePrompts.size, MAX_SECTION_CARDS)) {
                val cp = candidatePrompts[idx]
                val cleanA = cp.answer.trim().lowercase()
                val cleanQ = cp.question.trim().lowercase()

                var bestNode: ScannedNode? = null
                var highestScore = 0f

                for (sNode in scanned) {
                    val t = sNode.text.lowercase()
                    val d = sNode.desc.lowercase()
                    val combined = "$t $d"
                    var score = 0f

                    if (cleanA.isNotBlank() && (combined.contains(cleanA) || (cleanA.length >= 10 && combined.contains(cleanA.take(20))))) {
                        score += 100f
                    } else if (cleanA.isNotBlank() && t.length >= 6 && cleanA.contains(t)) {
                        score += 50f
                    }

                    if (cleanQ.isNotBlank() && (combined.contains(cleanQ) || (cleanQ.length >= 10 && combined.contains(cleanQ.take(20))))) {
                        score += 60f
                    } else if (cleanQ.isNotBlank() && t.length >= 6 && cleanQ.contains(t)) {
                        score += 30f
                    }

                    if (combined.contains("prompt:") && (combined.contains(cleanQ.take(15)) || (cleanA.isNotBlank() && combined.contains(cleanA.take(15))))) {
                        score += 80f
                    }

                    if (score > highestScore && sNode.rect.width() > (100 * density) && sNode.rect.height() > 0) {
                        highestScore = score
                        bestNode = sNode
                    }
                }

                if (bestNode != null && highestScore > 0f) {
                    val nearbyLikeButton = scanned.find { other ->
                        val isLikeBtn = other.desc.contains("like", ignoreCase = true) || other.desc.contains("rose", ignoreCase = true)
                        isLikeBtn && other.rect.left >= bestNode.rect.left &&
                            other.rect.top >= bestNode.rect.top - (20 * density) &&
                            other.rect.bottom <= bestNode.rect.bottom + (80 * density)
                    }
                    val finalRect = Rect(bestNode.rect)
                    if (nearbyLikeButton != null) {
                        finalRect.bottom = maxOf(finalRect.bottom, nearbyLikeButton.rect.bottom)
                        finalRect.right = maxOf(finalRect.right, nearbyLikeButton.rect.right)
                    }

                    val nearbyQuestionNode = scanned.find { other ->
                        cleanQ.isNotBlank() && other.text.lowercase().contains(cleanQ) &&
                            other.rect.bottom <= bestNode.rect.top + (20 * density) &&
                            other.rect.top >= bestNode.rect.top - (120 * density)
                    }
                    if (nearbyQuestionNode != null) {
                        finalRect.top = minOf(finalRect.top, nearbyQuestionNode.rect.top)
                    }

                    val targetPrompt = entries.getOrNull(idx) ?: PromptEntry(text = "${cp.question}: ${cp.answer}")
                    matchedSections.add(
                        PromptSectionInfo(
                            promptIndex = idx,
                            targetPrompt = targetPrompt,
                            bounds = finalRect,
                            virtualId = bestNode.virtualId,
                            provider = provider,
                        )
                    )
                }
            }
        }

        if (matchedSections.isEmpty()) {
            val promptRegex = Regex("""Prompt:\s*(.+?)\.\s*Answer:\s*(.+)""", RegexOption.DOT_MATCHES_ALL)
            val promptNodes = scanned.filter {
                promptRegex.containsMatchIn(it.text) || promptRegex.containsMatchIn(it.desc)
            }.distinctBy { it.rect.top }

            promptNodes.take(MAX_SECTION_CARDS).forEachIndexed { idx, pNode ->
                val text = pNode.text.ifBlank { pNode.desc }
                val match = promptRegex.find(text)
                val entryText = if (match != null) {
                    "${match.groupValues[1].trim()}: ${match.groupValues[2].trim()}"
                } else text

                val targetPrompt = entries.getOrNull(idx) ?: PromptEntry(text = entryText)
                matchedSections.add(
                    PromptSectionInfo(
                        promptIndex = idx,
                        targetPrompt = targetPrompt,
                        bounds = Rect(pNode.rect),
                        virtualId = pNode.virtualId,
                        provider = provider,
                    )
                )
            }
        }

        return matchedSections
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
                val res = info.provider.performAction(info.virtualId, AccessibilityNodeInfo.ACTION_SET_TEXT, args)
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
        act.runOnUiThread {
            val container = findAttachedContainer(act) ?: return@runOnUiThread
            val prefs = currentPrefsRef?.get() ?: return@runOnUiThread
            updateNavCounter(container)
            val screenClues = extractScreenClues(act)
            val passRect = findPassButtonBounds(act.window?.decorView)
            val chatInputRect = if (passRect == null) findChatInputBounds(act.window?.decorView) else null
            val commentInfo = findCommentCardInfo(act.window?.decorView)
            updateControlsState(container, act, prefs, passRect, chatInputRect, commentInfo)
        }
    }

    fun ensureUnlimitedUndos(context: Context) {
        attempt("ensure unlimited undos in default prefs", silent = true) {
            val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
            val current = prefs.getStringSet("USER_PERMISSIONS", null) ?: emptySet()
            if (!current.contains("undo_skip_replenish_unlimited")) {
                val updated = current.toMutableSet().apply {
                    add("undo_skip_replenish_unlimited")
                }
                prefs.edit().putStringSet("USER_PERMISSIONS", updated).apply()
                Log.i(Consts.TAG, "HostAppAiFab: granted undo_skip_replenish_unlimited in default prefs")
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

        val isStandoutsScreen = visibleTexts.any {
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
            it.contains("No matches right now", ignoreCase = true)
        }
        val isProfileScreen = visibleTexts.any {
            it.contains("Check your Signals", ignoreCase = true) ||
            it.contains("HingeX", ignoreCase = true) ||
            (it.contains("Get more", ignoreCase = true) && it.contains("Safety", ignoreCase = true))
        }

        val isChatScreen = visibleTexts.any {
            it.equals("Send a message", ignoreCase = true) ||
            it.equals("Type a message", ignoreCase = true) ||
            it.equals("Record voice note", ignoreCase = true) ||
            it.contains("messageComposition", ignoreCase = true)
        } || (visibleTexts.any { it.equals("Chat", ignoreCase = true) } && visibleTexts.any { it.equals("Profile", ignoreCase = true) })

        val activeContext = when {
            isChatScreen -> HostCandidateReader.ScreenContext.MATCH_CHAT
            isStandoutsScreen -> HostCandidateReader.ScreenContext.STANDOUTS
            isLikesYouScreen -> HostCandidateReader.ScreenContext.LIKES_YOU
            isMatchesScreen || isProfileScreen -> HostCandidateReader.ScreenContext.UNKNOWN
            else -> HostCandidateReader.ScreenContext.DISCOVER
        }

        Log.d(Consts.TAG, "extractScreenClues: activeContext=$activeContext, textsCount=${visibleTexts.size}")
        return HostCandidateReader.ScreenClues(
            visibleTexts = visibleTexts,
            activeContext = activeContext
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

    fun toggleInlineOpenerCard(activity: Activity, prefs: PreferencesManager, isChatMode: Boolean = false) {
        activity.runOnUiThread {
            val container = findAttachedContainer(activity) ?: return@runOnUiThread
            val inlineCard = container.findViewWithTag<ComposeView>(TAG_INLINE_OPENER_CARD) ?: return@runOnUiThread
            if (inlineCard.visibility == View.VISIBLE) {
                inlineCard.visibility = View.GONE
                isInlineOpenerManuallyToggled = false
                isCommentCardDismissed = true
                lastCommentTargetKey = null
            } else {
                isInlineOpenerManuallyToggled = true
                isCommentCardDismissed = false
                val owner = overlayLifecycleOwners[inlineCard] ?: ComposeDialogLifecycleOwner().also {
                    overlayLifecycleOwners[inlineCard] = it
                }
                owner.handleStart()
                container.setViewTreeLifecycleOwner(owner)
                container.setViewTreeViewModelStoreOwner(owner)
                container.setViewTreeSavedStateRegistryOwner(owner)
                inlineCard.setViewTreeLifecycleOwner(owner)
                inlineCard.setViewTreeViewModelStoreOwner(owner)
                inlineCard.setViewTreeSavedStateRegistryOwner(owner)

                val screenClues = extractScreenClues(activity)
                val activeCandidate = if (!isChatMode) HostCandidateReader.readTargetCandidateSync(activity, screenClues) else null
                val commentInfo = findCommentCardInfo(container.rootView, activeCandidate)
                currentCommentCardInfo = commentInfo

                inlineCard.setContent {
                    Theme(
                        themeMode = prefs.themeMode,
                        pureBlack = prefs.pureBlack,
                        themeColor = androidx.compose.ui.graphics.Color(prefs.themeColor),
                        setSystemBars = false,
                    ) {
                        InlineAiOpenerCard(
                            prefs = prefs,
                            targetPrompt = commentInfo?.targetPrompt,
                            isCommentMode = commentInfo != null,
                            candidate = activeCandidate,
                            screenClues = screenClues,
                            isChatMode = isChatMode,
                            hostActivity = activity,
                            onDismiss = {
                                activity.runOnUiThread {
                                    inlineCard.visibility = View.GONE
                                    isInlineOpenerManuallyToggled = false
                                    isCommentCardDismissed = true
                                    lastCommentTargetKey = null
                                }
                            },
                            onInsert = { text ->
                                if (commentInfo != null) {
                                    insertTextIntoComment(activity, commentInfo, text)
                                } else if (isChatMode) {
                                    insertReplyIntoChatInput(activity, text)
                                } else {
                                    copyOpenerFallback(activity, text)
                                }
                            },
                        )
                    }
                }
                inlineCard.visibility = View.VISIBLE
                updateControlsPosition(container, activity)
            }
        }
    }

    fun showAiSheet(activity: Activity, prefs: PreferencesManager, isChatMode: Boolean = false) {
        toggleInlineOpenerCard(activity, prefs, isChatMode)
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

    backBtn?.alpha = if (canGoBack) 1.0f else 0.35f
    backBtn?.isEnabled = canGoBack
    forwardBtn?.alpha = if (canGoForward) 1.0f else 0.35f
    forwardBtn?.isEnabled = canGoForward
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
    likesPill.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(pillHeight, View.MeasureSpec.EXACTLY)
    )
    val pillW = maxOf(likesPill.measuredWidth, (36 * density).toInt())
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
