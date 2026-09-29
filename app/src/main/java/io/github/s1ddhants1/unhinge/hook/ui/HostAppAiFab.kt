package io.github.s1ddhants1.unhinge.hook.ui

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.graphics.toColorInt
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
import io.github.s1ddhants1.unhinge.ui.theme.UnhingeTheme
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import java.util.WeakHashMap

/**
 * Injects a Floating Action Button (FAB) onto the host app (co.hinge.app)
 * that is visually identical to Hinge's native circular action buttons (such as the pass "X" button),
 * positioned on the same vertical level as the X button but mirrored to the right edge of the screen.
 *
 * Visually identical to Hinge's action buttons:
 * - 60dp circular container with 16dp elevation
 * - Pure white (#FFFFFF) in light mode, dark charcoal (#1A1A1A) in dark mode
 * - 1dp subtle outline stroke (#EBEBEB / #2A2A2A)
 * - Pure black (#1A1A1A) / white (#FFFFFF) AI Sparkle icon centered
 * - 20dp margin matching Hinge's Design System tokens
 * - Exclusively visible on the Discover feed page, automatically hidden on other tabs and fragments.
 */
object HostAppAiFab {
    private const val TAG_FAB_CONTAINER = "unhinge_host_fab_container"
    private const val TAG_FAB_BUTTON = "unhinge_host_fab_button"

    private val layoutListeners = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()

    fun attach(activity: Activity, prefs: PreferencesManager) {
        activity.runOnUiThread {
            try {
                prefs.ensureBackupPrefs(activity)
                if (!prefs.showHostAppFab) {
                    remove(activity)
                    return@runOnUiThread
                }

                val decorView = activity.window?.decorView as? ViewGroup ?: return@runOnUiThread
                if (decorView.findViewWithTag<View>(TAG_FAB_CONTAINER) != null) {
                    updateFabState(activity, prefs)
                    return@runOnUiThread
                }

                val density = activity.resources.displayMetrics.density
                val fabSize = (62 * density).toInt()
                val margin = (20 * density).toInt()

                val container = object : FrameLayout(activity) {
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        // Allow touches outside child views to pass through to the host app
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
                    visibility = if (isDiscoverActive(activity)) View.VISIBLE else View.GONE
                }

                // Native Hinge circular action button aesthetics (matching the pass "X" button)
                val surfaceColor = Color.WHITE
                val strokeColor = "#EBEBEB".toColorInt()
                val iconColor = "#1A1A1A".toColorInt()
                val rippleColor = Color.argb(30, 0, 0, 0)

                val fab = FrameLayout(activity).apply {
                    tag = TAG_FAB_BUTTON
                    layoutParams = FrameLayout.LayoutParams(fabSize, fabSize).apply {
                        gravity = Gravity.TOP or Gravity.START
                    }

                    // Native Hinge circular action button surface
                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(surfaceColor)
                        setStroke((1 * density).toInt(), strokeColor)
                    }

                    val mask = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(Color.WHITE)
                    }

                    background = RippleDrawable(
                        ColorStateList.valueOf(rippleColor),
                        bg,
                        mask
                    )

                    // 16dp elevation matching Hinge's fixed action button elevation token
                    elevation = 16 * density
                    outlineProvider = ViewOutlineProvider.BACKGROUND

                    // Centered AI Sparkle vector icon
                    val iconView = ImageView(activity).apply {
                        val iconSize = (28 * density).toInt()
                        layoutParams = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                            gravity = Gravity.CENTER
                        }
                        setImageDrawable(SparkleIconDrawable(iconColor))
                    }
                    addView(iconView)

                    setOnClickListener {
                        showAiSheet(activity, prefs)
                    }
                }

                container.addView(fab)
                decorView.addView(container)

                // Position immediately
                updateFabPosition(activity, fab, fabSize, margin)

                // Observe layout changes to maintain alignment and visibility as the user navigates
                val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                    updateFabState(activity, prefs)
                }
                decorView.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
                layoutListeners[activity] = layoutListener

                // Recurring poll to sync state across Compose tab switches without requiring Android view layouts
                val pollRunnable = object : Runnable {
                    override fun run() {
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            updateFabState(activity, prefs)
                            container.postDelayed(this, 600)
                        }
                    }
                }
                container.postDelayed(pollRunnable, 300)

                Log.d(Consts.TAG, "HostAppAiFab attached successfully to ${activity.localClassName}")
            } catch (e: Throwable) {
                Log.e(Consts.TAG, "HostAppAiFab attach failed: ${e.message}", e)
            }
        }
    }

    fun remove(activity: Activity) {
        activity.runOnUiThread {
            try {
                val decorView = activity.window?.decorView as? ViewGroup
                val listener = layoutListeners.remove(activity)
                if (listener != null && decorView != null) {
                    decorView.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                }
                val existing = decorView?.findViewWithTag<View>(TAG_FAB_CONTAINER)
                if (existing != null) {
                    decorView.removeView(existing)
                }
            } catch (_: Throwable) {}
        }
    }

    private fun updateFabState(activity: Activity, prefs: PreferencesManager) {
        if (activity.isFinishing || activity.isDestroyed) return
        val decorView = activity.window?.decorView as? ViewGroup ?: return
        val container = decorView.findViewWithTag<View>(TAG_FAB_CONTAINER) ?: return
        val fab = container.findViewWithTag<View>(TAG_FAB_BUTTON) ?: return

        val isDiscover = isDiscoverActive(activity)
        val shouldShow = isDiscover && prefs.showHostAppFab
        val newVisibility = if (shouldShow) View.VISIBLE else View.GONE

        if (container.visibility != newVisibility) {
            container.visibility = newVisibility
            Log.d(Consts.TAG, "HostAppAiFab visibility changed to: ${if (newVisibility == View.VISIBLE) "VISIBLE" else "GONE"}")
        }

        if (shouldShow) {
            container.bringToFront()
            val density = activity.resources.displayMetrics.density
            val fabSize = (62 * density).toInt()
            val margin = (20 * density).toInt()
            updateFabPosition(activity, fab, fabSize, margin)
        }
    }

    /**
     * Positions the FAB to the exact mirrored horizontal coordinate and identical vertical level
     * as Hinge's pass "X" button.
     */
    private fun updateFabPosition(activity: Activity, fab: View, fabSize: Int, margin: Int) {
        val density = activity.resources.displayMetrics.density
        val (screenWidth, screenHeight) = getWindowDimensions(activity)

        val decorView = activity.window?.decorView as? ViewGroup
        val bottomNavTop = findBottomNavTop(decorView, screenHeight)
            ?: (screenHeight - (76 * density).toInt())

        // Mirrored across horizontal axis from left pass "X" button:
        // Pass button: left = margin, top = bottomNavTop - margin - fabSize
        // Mirrored FAB: left = screenWidth - margin - fabSize, top = bottomNavTop - margin - fabSize
        val targetX = (screenWidth - margin - fabSize).toFloat()
        val targetY = (bottomNavTop - margin - fabSize).toFloat()

        if (fab.x != targetX) fab.x = targetX
        if (fab.y != targetY) fab.y = targetY
    }

    private fun findBottomNavTop(root: View?, screenHeight: Int): Int? {
        if (root == null || root.visibility != View.VISIBLE) return null
        val density = root.resources.displayMetrics.density
        val minNavHeight = (48 * density).toInt()
        val maxNavHeight = (120 * density).toInt()

        if (root.height in minNavHeight..maxNavHeight && root.width >= (root.resources.displayMetrics.widthPixels * 0.7f).toInt()) {
            val loc = IntArray(2)
            root.getLocationInWindow(loc)
            // Check if this view is docked to the bottom of the screen
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

    /**
     * Determines whether Hinge is currently showing the Discover feed by inspecting
     * the active FragmentContainerViews in the view hierarchy or the active AndroidX fragment tree.
     */
    private fun isDiscoverActive(activity: Activity): Boolean {
        return try {
            val decorView = activity.window?.decorView as? ViewGroup
            if (decorView != null && isDiscoverInViewTree(decorView)) {
                return true
            }
            val fm = getAndroidXFragmentManager(activity)
            if (fm != null && isDiscoverInFragmentManager(fm)) {
                return true
            }
            false
        } catch (e: Throwable) {
            Log.e(Consts.TAG, "Error checking isDiscoverActive: ${e.message}", e)
            false
        }
    }

    private fun isCandidateFragment(name: String): Boolean {
        return name.contains("Discover", ignoreCase = true) || name.contains("Standouts", ignoreCase = true)
    }

    private fun isDiscoverInViewTree(root: View): Boolean {
        if (root.javaClass.name.contains("FragmentContainerView")) {
            val frag = getFragmentFromContainer(root)
            if (frag != null) {
                val name = frag.javaClass.name
                val isHidden = isFragmentHidden(frag)
                val isResumed = isFragmentResumed(frag)
                if (isCandidateFragment(name) && !isHidden && isResumed) {
                    return true
                }
            }
        }
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                if (isDiscoverInViewTree(root.getChildAt(i))) return true
            }
        }
        return false
    }

    private fun getFragmentFromContainer(view: View): Any? {
        val method = view.javaClass.methods.firstOrNull { it.name == "getFragment" } ?: return null
        return try {
            method.isAccessible = true
            method.invoke(view)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getAndroidXFragmentManager(activity: Activity): Any? {
        var clazz: Class<*>? = activity.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (m in clazz.declaredMethods) {
                if (m.returnType.name == "androidx.fragment.app.FragmentManager") {
                    try {
                        m.isAccessible = true
                        val res = m.invoke(activity)
                        if (res != null) {
                            return res
                        }
                    } catch (_: Throwable) {}
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun isDiscoverInFragmentManager(fm: Any): Boolean {
        val fragments = getFragmentsList(fm) ?: return false
        for (frag in fragments) {
            if (frag == null) continue
            val name = frag.javaClass.name
            val isHidden = isFragmentHidden(frag)
            val isResumed = isFragmentResumed(frag)

            if (isCandidateFragment(name) && !isHidden && isResumed) {
                return true
            }

            // Recursively inspect childFragmentManager
            val childFm = getChildFragmentManager(frag)
            if (childFm != null) {
                if (isDiscoverInFragmentManager(childFm)) {
                    return true
                }
            }
        }
        return false
    }

    private fun getFragmentsList(fm: Any): List<*>? {
        val method = fm.javaClass.methods.firstOrNull { it.name == "getFragments" }
        if (method != null) {
            try {
                method.isAccessible = true
                return method.invoke(fm) as? List<*>
            } catch (e: Throwable) {
                Log.w(Consts.TAG, "getFragments public method failed: ${e.message}")
            }
        }
        var clazz: Class<*>? = fm.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val m = clazz.getDeclaredMethod("getFragments").apply { isAccessible = true }
                return m.invoke(fm) as? List<*>
            } catch (_: NoSuchMethodException) {
                clazz = clazz.superclass
            } catch (e: Throwable) {
                Log.w(Consts.TAG, "getFragments declared method failed: ${e.message}")
                break
            }
        }
        return null
    }

    private fun getChildFragmentManager(frag: Any): Any? {
        val method = frag.javaClass.methods.firstOrNull { it.name == "getChildFragmentManager" }
        if (method != null) {
            try {
                method.isAccessible = true
                return method.invoke(frag)
            } catch (e: Throwable) {
                Log.w(Consts.TAG, "getChildFragmentManager public method failed: ${e.message}")
            }
        }
        var clazz: Class<*>? = frag.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val m = clazz.getDeclaredMethod("getChildFragmentManager").apply { isAccessible = true }
                return m.invoke(frag)
            } catch (_: NoSuchMethodException) {
                clazz = clazz.superclass
            } catch (e: Throwable) {
                Log.w(Consts.TAG, "getChildFragmentManager declared method failed: ${e.message}")
                break
            }
        }
        return null
    }

    private fun isFragmentHidden(frag: Any): Boolean {
        val method = frag.javaClass.methods.firstOrNull { it.name == "isHidden" }
        if (method != null) {
            try {
                method.isAccessible = true
                return (method.invoke(frag) as? Boolean) ?: false
            } catch (_: Throwable) {}
        }
        var clazz: Class<*>? = frag.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val m = clazz.getDeclaredMethod("isHidden").apply { isAccessible = true }
                return (m.invoke(frag) as? Boolean) ?: false
            } catch (_: NoSuchMethodException) {
                clazz = clazz.superclass
            } catch (_: Throwable) {
                break
            }
        }
        return false
    }

    private fun isFragmentResumed(frag: Any): Boolean {
        val method = frag.javaClass.methods.firstOrNull { it.name == "isResumed" }
        if (method != null) {
            try {
                method.isAccessible = true
                return (method.invoke(frag) as? Boolean) ?: true
            } catch (_: Throwable) {}
        }
        var clazz: Class<*>? = frag.javaClass
        while (clazz != null && clazz != Any::class.java) {
            try {
                val m = clazz.getDeclaredMethod("isResumed").apply { isAccessible = true }
                return (m.invoke(frag) as? Boolean) ?: true
            } catch (_: NoSuchMethodException) {
                clazz = clazz.superclass
            } catch (_: Throwable) {
                break
            }
        }
        return true
    }

    private fun getWindowDimensions(activity: Activity): Pair<Int, Int> {
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
            val dialog = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar)
            val window = dialog.window
            window?.setGravity(Gravity.BOTTOM)
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0.5f)

            val lifecycleOwner = ComposeDialogLifecycleOwner()

            val composeView = ComposeView(activity).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnLifecycleDestroyed(lifecycleOwner))
                setContent {
                    UnhingeTheme(
                        darkTheme = isSystemInDarkTheme(),
                        pureBlack = prefs.pureBlack,
                        themeColor = androidx.compose.ui.graphics.Color(prefs.themeColor)
                    ) {
                        HostAppAiSheetContent(
                            candidateList = null,
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
            }

            dialog.show()
        } catch (t: Throwable) {
            Log.e(Consts.TAG, "Failed to display HostAppAiSheet: ${t.message}", t)
        }
    }
}

/**
 * Self-contained LifecycleOwner, ViewModelStoreOwner, and SavedStateRegistryOwner
 * ensuring ComposeView inside the host application's dialog window receives valid
 * lifecycle, ViewModel, and SavedState bindings matching Unhinge's classloader.
 */
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

/**
 * Procedurally drawn 4-pointed starry AI sparkle icon that matches native Hinge aesthetics
 * without relying on target application resources or APK resource injection.
 */
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

        // Primary 4-pointed sparkle (left-center)
        addSparkle(path, cx = w * 0.44f, cy = h * 0.52f, radius = minOf(w, h) * 0.36f)
        // Companion secondary 4-pointed sparkle (top-right)
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
