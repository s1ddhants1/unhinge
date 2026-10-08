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
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.data.HostCandidateReader
import io.github.s1ddhants1.unhinge.ui.theme.Theme
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.attempt
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

    private val layoutListeners = WeakHashMap<View, ViewTreeObserver.OnGlobalLayoutListener>()
    private val preDrawListeners = WeakHashMap<View, ViewTreeObserver.OnPreDrawListener>()
    private val likesObservers = WeakHashMap<Activity, (HostLikesReader.LikesState) -> Unit>()
    internal var currentActivityRef: WeakReference<Activity>? = null
    private var currentPrefsRef: WeakReference<PreferencesManager>? = null

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
                    val attached = findAttachedContainer(activity)
                    val contentFrame = (activity.findViewById(android.R.id.content) as? ViewGroup)
                        ?: (decor as? ViewGroup)

                    if (passRect != null) {
                        val container = if (attached != null && attached.parent === contentFrame) {
                            attached
                        } else {
                            attached?.let { (it.parent as? ViewGroup)?.removeView(it) }
                            val newContainer = createContainerView(activity, prefs)
                            contentFrame?.addView(newContainer)
                            bindLayoutListener(newContainer, activity, prefs)
                            newContainer
                        }
                        updateControlsState(container, activity, prefs, passRect)
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
                Log.i(Consts.TAG, "HostAppAiFab clicked! Launching showAiSheet...")
                showAiSheet(activity, prefs)
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

    private fun updateControlsState(container: View, activity: Activity, prefs: PreferencesManager, passRect: Rect? = null) {
        if (activity.isFinishing || activity.isDestroyed) return

        val rect = passRect ?: findPassButtonBounds(container.rootView)

        val hasActiveCandidate = rect != null
        if (!hasActiveCandidate && FeedNavigator.isNavigated) {
            FeedNavigator.reset(activity)
        }
        if (hasActiveCandidate && prefs.enableFeedNavigation) {
            FeedNavigator.refreshTotal(activity)
        }
        val totalCandidates = FeedNavigator.totalCandidates
        Log.i(Consts.TAG, "updateControlsState: passRect=$rect, hasActive=$hasActiveCandidate, totalCandidates=$totalCandidates")

        val screenClues = extractScreenClues(activity)
        val activeCandidate = HostCandidateReader.readTargetCandidateSync(activity, screenClues)
        val isStandoutCandidate = screenClues.activeContext == HostCandidateReader.ScreenContext.STANDOUTS ||
                activeCandidate?.isStandout == true

        val shouldShowFab = hasActiveCandidate && prefs.showHostAppFab
        val showNav = !isStandoutCandidate && hasActiveCandidate && prefs.enableFeedNavigation && (totalCandidates > 1 || FeedNavigator.isNavigated)
        val showLikes = hasActiveCandidate && prefs.showAvailableLikes

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

        val shouldShowContainer = shouldShowFab || showNav || showLikes
        container.visibility = if (shouldShowContainer) View.VISIBLE else View.GONE

        Log.i(Consts.TAG, "updateControlsState: passRect=$rect, hasActive=$hasActiveCandidate, total=$totalCandidates, fab=$shouldShowFab, nav=$showNav, likes=$showLikes, standout=$isStandoutCandidate, containerVis=${if (shouldShowContainer) "VISIBLE" else "GONE"}")

        if (shouldShowContainer) {
            updateControlsPosition(container, activity, rect)
        }
    }

    private fun updateControlsPosition(container: View, activity: Activity, passRect: Rect? = null) {
        val density = activity.resources.displayMetrics.density
        val (screenWidth, screenHeight) = getWindowDimensions(activity)
        val containerWidth = container.width.takeIf { it > 0 } ?: screenWidth

        val rect = passRect ?: findPassButtonBounds(container.rootView)

        val loc = IntArray(2)
        container.getLocationOnScreen(loc)
        val containerScreenX = loc[0]
        val containerScreenY = loc[1]

        val fabSize: Int
        val targetX: Float
        val targetY: Float

        if (rect != null && rect.width() > 0 && rect.height() > 0) {

            fabSize = rect.width()
            val leftMargin = rect.left - containerScreenX
            targetX = (containerWidth - leftMargin - fabSize).toFloat()
            targetY = (rect.top - containerScreenY).toFloat()
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
            updateNavCounter(container)
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

        val activeContext = when {
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

    private fun showAiSheet(activity: Activity, prefs: PreferencesManager) {
        if (activity.isFinishing || activity.isDestroyed) return
        try {
            prefs.ensureBackupPrefs(activity)
            val screenClues = extractScreenClues(activity)
            val activityWindow = activity.window
            val insetsController = if (activityWindow != null) {
                WindowCompat.getInsetsController(activityWindow, activityWindow.decorView)
            } else null
            val wasLightStatusBar = insetsController?.isAppearanceLightStatusBars
            val wasLightNavBar = insetsController?.isAppearanceLightNavigationBars

            val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            val window = dialog.window
            window?.setGravity(Gravity.BOTTOM)
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0.5f)
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            window?.setWindowAnimations(android.R.style.Animation_InputMethod)
            dialog.setCanceledOnTouchOutside(true)

            val lifecycleOwner = ComposeDialogLifecycleOwner()

            val composeView = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner))
                setContent {
                    Theme(
                        themeMode = prefs.themeMode,
                        pureBlack = prefs.pureBlack,
                        themeColor = androidx.compose.ui.graphics.Color(prefs.themeColor),
                        setSystemBars = false
                    ) {
                        HostAppAiSheetContent(
                            candidateList = null,
                            screenClues = screenClues,
                            prefs = prefs,
                            onDismiss = { dialog.dismiss() },
                            showDragHandle = true
                        )
                    }
                }
            }

            composeView.setViewTreeLifecycleOwner(lifecycleOwner)
            composeView.setViewTreeViewModelStoreOwner(lifecycleOwner)
            composeView.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            val decor = window?.decorView
            if (decor != null) {
                decor.setViewTreeLifecycleOwner(lifecycleOwner)
                decor.setViewTreeViewModelStoreOwner(lifecycleOwner)
                decor.setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }

            dialog.setContentView(composeView)

            dialog.setOnShowListener {
                lifecycleOwner.handleStart()
            }
            dialog.setOnDismissListener {
                lifecycleOwner.handleDestroy()
                if (insetsController != null) {
                    if (wasLightStatusBar != null) {
                        insetsController.isAppearanceLightStatusBars = wasLightStatusBar
                    }
                    if (wasLightNavBar != null) {
                        insetsController.isAppearanceLightNavigationBars = wasLightNavBar
                    }
                }
            }

            dialog.show()
        } catch (t: Throwable) {
            Log.e(Consts.TAG, "Failed to display HostAppAiSheet: ${t.message}", t)
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
