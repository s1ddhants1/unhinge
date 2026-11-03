package io.github.s1ddhants1.unhinge.hook.ui

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.util.attempt
import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

object FeedNavigator {

    private val navOffset = AtomicInteger(0)
    @Volatile private var cachedTotal: Int = -1
    @Volatile private var dbRef: WeakReference<SQLiteDatabase>? = null
    @Volatile private var roomDbRef: WeakReference<Any>? = null
    @Volatile private var invalidationTrackerRef: WeakReference<Any>? = null

    val isInternalQuery: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    val currentOffset: Int get() = navOffset.get()
    val totalCandidates: Int get() = cachedTotal

    val displayPosition: Int get() = currentOffset + 1

    val isNavigated: Boolean get() = currentOffset > 0

    fun setDbReference(db: SQLiteDatabase) {
        if (!db.isReadOnly && db.isOpen) {
            dbRef = WeakReference(db)
        }
    }

    fun setRoomDatabase(db: Any) {
        if (roomDbRef?.get() === db) return
        roomDbRef = WeakReference(db)
        try {
            val trackerMethod = db.javaClass.methods.firstOrNull { it.name == "getInvalidationTracker" }
            val tracker = trackerMethod?.invoke(db)
                ?: db.javaClass.declaredFields.firstOrNull { it.name == "mInvalidationTracker" }?.apply { isAccessible = true }?.get(db)
            if (tracker != null) {
                invalidationTrackerRef = WeakReference(tracker)
                Log.i(Consts.TAG, "FeedNavigator: captured Room InvalidationTracker successfully")
            }
        } catch (e: Exception) {
            Log.w(Consts.TAG, "FeedNavigator: failed to extract InvalidationTracker: ${e.message}")
        }
    }

    internal fun getWritableDb(context: Context? = null): SQLiteDatabase? {
        val captured = dbRef?.get()
        if (captured != null && captured.isOpen && !captured.isReadOnly) return captured

        val targetPath = when {
            context != null && context.getDatabasePath("db")?.exists() == true -> context.getDatabasePath("db").absolutePath
            File("/data/data/co.hinge.app/databases/db").exists() -> "/data/data/co.hinge.app/databases/db"
            File("/data/user/0/co.hinge.app/databases/db").exists() -> "/data/user/0/co.hinge.app/databases/db"
            else -> null
        } ?: return null

        return try {
            val opened = SQLiteDatabase.openDatabase(targetPath, null, SQLiteDatabase.OPEN_READWRITE)
            dbRef = WeakReference(opened)
            opened
        } catch (e: Exception) {
            Log.w(Consts.TAG, "FeedNavigator: getWritableDb fallback open failed: ${e.message}")
            null
        }
    }

    fun navigateForward(context: Context? = null): Boolean {
        val total = refreshTotal(context)
        if (total > 0 && currentOffset >= total - 1) {
            Log.i(Consts.TAG, "FeedNavigator: forward blocked - at end ($displayPosition/$total)")
            return false
        }

        val newOffset = navOffset.incrementAndGet()
        Log.i(Consts.TAG, "FeedNavigator: navigateForward to offset $newOffset (pos ${newOffset + 1}/$total)")
        val activity = context as? Activity
        triggerHingeRefresh(activity)
        HostAppAiFab.notifyNavUpdated(activity)
        return true
    }

    fun navigateBack(context: Context? = null): Boolean {
        if (currentOffset <= 0) {
            return false
        }

        val newOffset = navOffset.decrementAndGet()
        Log.i(Consts.TAG, "FeedNavigator: navigateBack to offset $newOffset (pos ${newOffset + 1}/$cachedTotal)")
        val activity = context as? Activity
        triggerHingeRefresh(activity)
        HostAppAiFab.notifyNavUpdated(activity)
        return true
    }

    fun reset(context: Context? = null) {
        val prev = navOffset.getAndSet(0)
        if (prev > 0) {
            Log.i(Consts.TAG, "FeedNavigator: reset offset from $prev to 0")
            val activity = context as? Activity
            triggerHingeRefresh(activity)
            HostAppAiFab.notifyNavUpdated(activity)
        }
    }

    fun onRatingInserted(context: Context? = null) {
        val total = refreshTotal(context)
        if (currentOffset > 0 && currentOffset >= total) {
            navOffset.set(maxOf(0, total - 1))
        }
        val activity = context as? Activity
        HostAppAiFab.notifyNavUpdated(activity)
    }

    fun refreshTotal(context: Context? = null): Int {
        val db = dbRef?.get()
        if (db != null && db.isOpen) {
            isInternalQuery.set(true)
            try {
                try {
                    db.rawQuery(
                        "SELECT COUNT(*) FROM discover_subject WHERE userId NOT IN " +
                        "(SELECT subjectId FROM pending_ratings WHERE subjectId IS NOT NULL)",
                        null
                    ).use { c ->
                        if (c.moveToFirst()) {
                            cachedTotal = c.getInt(0)
                            clampOffsetIfNeeded()
                            return cachedTotal
                        }
                    }
                } catch (_: Exception) {}

                db.rawQuery("SELECT COUNT(*) FROM discover_subject", null).use { c ->
                    if (c.moveToFirst()) {
                        cachedTotal = c.getInt(0)
                        clampOffsetIfNeeded()
                        return cachedTotal
                    }
                }
            } catch (e: Exception) {
                Log.w(Consts.TAG, "FeedNavigator: refreshTotal on dbRef failed: ${e.message}")
            } finally {
                isInternalQuery.set(false)
            }
        }

        if (context != null) {
            try {
                val dbFile = context.getDatabasePath("db")
                val targetPath = when {
                    dbFile != null && dbFile.exists() -> dbFile.absolutePath
                    File("/data/data/co.hinge.app/databases/db").exists() -> "/data/data/co.hinge.app/databases/db"
                    File("/data/user/0/co.hinge.app/databases/db").exists() -> "/data/user/0/co.hinge.app/databases/db"
                    else -> null
                }
                if (targetPath != null) {
                    SQLiteDatabase.openDatabase(targetPath, null, SQLiteDatabase.OPEN_READONLY).use { readOnlyDb ->
                        try {
                            readOnlyDb.rawQuery(
                                "SELECT COUNT(*) FROM discover_subject WHERE userId NOT IN " +
                                "(SELECT subjectId FROM pending_ratings WHERE subjectId IS NOT NULL)",
                                null
                            ).use { c ->
                                if (c.moveToFirst()) {
                                    cachedTotal = c.getInt(0)
                                    clampOffsetIfNeeded()
                                    return cachedTotal
                                }
                            }
                        } catch (_: Exception) {}

                        readOnlyDb.rawQuery("SELECT COUNT(*) FROM discover_subject", null).use { c ->
                            if (c.moveToFirst()) {
                                cachedTotal = c.getInt(0)
                                clampOffsetIfNeeded()
                                return cachedTotal
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(Consts.TAG, "FeedNavigator: refreshTotal read-only fallback failed: ${e.message}")
            }
        }

        return cachedTotal
    }

    private fun clampOffsetIfNeeded() {
        val total = cachedTotal
        if (total > 0 && navOffset.get() >= total) {
            navOffset.set(maxOf(0, total - 1))
        }
    }

    fun triggerHingeRefresh(activity: Activity? = null) {
        val act = activity ?: HostAppAiFab.currentActivityRef?.get()

        // 1. Room InvalidationTracker table observer trigger across all discover-related tables
        val tracker = invalidationTrackerRef?.get()
        if (tracker != null) {
            try {
                val notifyMethod = tracker.javaClass.methods.firstOrNull {
                    it.name == "notifyObserversByTableNames" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Array<String>::class.java
                }
                notifyMethod?.invoke(tracker, arrayOf("discover_subject", "discover_batch", "profiles", "subject_media", "pending_ratings"))

                val refreshAsync = tracker.javaClass.methods.firstOrNull { it.name == "refreshVersionsAsync" }
                refreshAsync?.invoke(tracker)
                Log.i(Consts.TAG, "FeedNavigator: Room InvalidationTracker notified across discover tables")
            } catch (e: Exception) {
                Log.w(Consts.TAG, "FeedNavigator: InvalidationTracker trigger failed: ${e.message}")
            }
        }

        // 2. Touch discover_subject in writable DB
        val db = getWritableDb(act)
        if (db != null && db.isOpen && !db.isReadOnly) {
            isInternalQuery.set(true)
            try {
                db.execSQL("UPDATE discover_subject SET batchId = batchId WHERE rowid = (SELECT MIN(rowid) FROM discover_subject)")
            } catch (_: Exception) {}
            finally {
                isInternalQuery.set(false)
            }
        }

        // 3. Dispatch native refresh mechanisms on the UI thread of the active Hinge Activity
        if (act != null) {
            act.runOnUiThread {
                attempt("trigger Hinge native feed refresh", silent = false) {
                    var refreshed = false

                    // Layer A: DiscoverActor Direct Event Dispatch (defpackage.w65.a / ReloadPotential)
                    val discoverFragment = findDiscoverFragment(act)
                    if (discoverFragment != null) {
                        refreshed = dispatchReloadPotential(discoverFragment)
                        if (refreshed) {
                            Log.i(Consts.TAG, "FeedNavigator: Successfully triggered DiscoverActor ReloadPotential event")
                        }

                        // Also attempt capturing RoomDatabase from repository if not already captured
                        attempt("capture Room from DiscoverFragment", silent = true) {
                            captureRoomFromFragment(discoverFragment)
                        }

                        // Layer B: Fragment Re-attachment with action=refresh parameter
                        reAttachDiscoverFragment(discoverFragment)
                    }

                    // Layer C: Discover Bottom Navigation Tab Trigger / Reselect
                    triggerDiscoverTabReselect(act)
                }
            }
        }
    }

    fun findDiscoverFragment(activity: Activity): Any? {
        return attempt("find DiscoverFragment in activity", silent = true) {
            val getFmMethod = activity.javaClass.methods.firstOrNull { it.name == "getSupportFragmentManager" }
                ?: activity.javaClass.methods.firstOrNull { it.name == "getFragmentManager" }
            val fm = getFmMethod?.invoke(activity)
            if (fm != null) findFragmentReflectively(fm) else null
        }
    }

    private fun findFragmentReflectively(fm: Any): Any? {
        try {
            val getFragmentsMethod = fm.javaClass.methods.firstOrNull { it.name == "getFragments" }
            val list = getFragmentsMethod?.invoke(fm) as? List<*> ?: return null
            for (item in list) {
                if (item == null) continue
                val name = item.javaClass.name
                if (name.endsWith("DiscoverFragment") || name.contains(".DiscoverFragment")) {
                    return item
                }
                val getChildFm = item.javaClass.methods.firstOrNull { it.name == "getChildFragmentManager" }
                val childFm = getChildFm?.invoke(item)
                if (childFm != null) {
                    val found = findFragmentReflectively(childFm)
                    if (found != null) return found
                }
            }
        } catch (_: Throwable) {}
        return null
    }

    fun dispatchReloadPotential(fragment: Any): Boolean {
        return attempt("dispatch ReloadPotential event", silent = true) {
            val classLoader = fragment.javaClass.classLoader ?: return@attempt false
            val reloadEvent = resolveReloadPotentialEvent(classLoader)

            val viewModel = resolveDiscoverViewModel(fragment) ?: return@attempt false
            val controller = resolveDiscoverController(viewModel) ?: return@attempt false

            if (reloadEvent != null) {
                // Method U(n75) on controller (u85)
                val uMethod = controller.javaClass.methods.firstOrNull {
                    it.name == "U" && it.parameterTypes.size == 1
                }
                if (uMethod != null) {
                    uMethod.invoke(controller, reloadEvent)
                    return@attempt true
                }

                // Field C1 (eea) on controller (u85)
                val c1Field = controller.javaClass.fields.firstOrNull { it.name == "C1" }
                    ?: controller.javaClass.declaredFields.firstOrNull { it.name == "C1" }?.apply { isAccessible = true }
                val c1 = c1Field?.get(controller)
                if (c1 != null) {
                    val fMethod = c1.javaClass.methods.firstOrNull {
                        it.name == "f" && it.parameterTypes.size == 1
                    }
                    if (fMethod != null) {
                        fMethod.invoke(c1, reloadEvent)
                        return@attempt true
                    }
                }
            }
            false
        } ?: false
    }

    private fun resolveReloadPotentialEvent(classLoader: ClassLoader): Any? {
        // Direct known R8 class in target Hinge build
        try {
            val clazz = classLoader.loadClass("defpackage.w65")
            val instance = clazz.getField("a").get(null)
            if (instance != null) return instance
        } catch (_: Throwable) {}

        // Obfuscation fallback: scan common event classes extending n75 or returning "ReloadPotential"
        for (className in listOf("defpackage.w65", "defpackage.v65", "defpackage.t65", "defpackage.w55")) {
            try {
                val clazz = classLoader.loadClass(className)
                val instance = clazz.getField("a").get(null)
                if (instance?.toString() == "ReloadPotential") return instance
            } catch (_: Throwable) {}
        }
        return null
    }

    private fun resolveDiscoverViewModel(fragment: Any): Any? {
        try {
            val y0Method = fragment.javaClass.methods.firstOrNull { it.name == "y0" }
            if (y0Method != null) {
                return y0Method.invoke(fragment)
            }
        } catch (_: Throwable) {}

        try {
            val o1Field = fragment.javaClass.fields.firstOrNull { it.name == "O1" }
                ?: fragment.javaClass.declaredFields.firstOrNull { it.name == "O1" }?.apply { isAccessible = true }
            val lazyVal = o1Field?.get(fragment)
            if (lazyVal != null) {
                val getValMethod = lazyVal.javaClass.methods.firstOrNull { it.name == "getValue" }
                if (getValMethod != null) {
                    return getValMethod.invoke(lazyVal)
                }
            }
        } catch (_: Throwable) {}

        return null
    }

    private fun resolveDiscoverController(viewModel: Any): Any? {
        try {
            val eField = viewModel.javaClass.fields.firstOrNull { it.name == "e" }
                ?: viewModel.javaClass.declaredFields.firstOrNull { it.name == "e" }?.apply { isAccessible = true }
            val candidate = eField?.get(viewModel)
            if (candidate != null) return candidate
        } catch (_: Throwable) {}

        for (f in viewModel.javaClass.declaredFields) {
            try {
                f.isAccessible = true
                val candidate = f.get(viewModel) ?: continue
                if (candidate.javaClass.methods.any { it.name == "U" && it.parameterTypes.size == 1 } ||
                    candidate.javaClass.declaredFields.any { it.name == "C1" }) {
                    return candidate
                }
            } catch (_: Throwable) {}
        }
        return null
    }

    fun reAttachDiscoverFragment(fragment: Any) {
        attempt("reAttach DiscoverFragment", silent = true) {
            val isAddedMethod = fragment.javaClass.methods.firstOrNull { it.name == "isAdded" }
            val isAdded = isAddedMethod?.invoke(fragment) as? Boolean ?: false
            if (isAdded) {
                val getArgsMethod = fragment.javaClass.methods.firstOrNull { it.name == "getArguments" }
                var args = getArgsMethod?.invoke(fragment) as? Bundle
                if (args == null) {
                    args = Bundle()
                    val setArgsMethod = fragment.javaClass.methods.firstOrNull {
                        it.name == "setArguments" && it.parameterTypes.size == 1 && it.parameterTypes[0] == Bundle::class.java
                    }
                    setArgsMethod?.invoke(fragment, args)
                }
                args.putString("action", "refresh")

                val getParentFm = fragment.javaClass.methods.firstOrNull { it.name == "getParentFragmentManager" }
                    ?: fragment.javaClass.methods.firstOrNull { it.name == "getFragmentManager" }
                val parentFm = getParentFm?.invoke(fragment)
                if (parentFm != null) {
                    val beginTx = parentFm.javaClass.methods.firstOrNull { it.name == "beginTransaction" }
                    val tx = beginTx?.invoke(parentFm)
                    if (tx != null) {
                        val detachMethod = tx.javaClass.methods.firstOrNull { it.name == "detach" && it.parameterTypes.size == 1 }
                        val attachMethod = tx.javaClass.methods.firstOrNull { it.name == "attach" && it.parameterTypes.size == 1 }
                        val commitMethod = tx.javaClass.methods.firstOrNull { it.name == "commitAllowingStateLoss" }
                            ?: tx.javaClass.methods.firstOrNull { it.name == "commit" }
                        detachMethod?.invoke(tx, fragment)
                        attachMethod?.invoke(tx, fragment)
                        commitMethod?.invoke(tx)
                        Log.i(Consts.TAG, "FeedNavigator: DiscoverFragment re-attached cleanly with action=refresh")
                    }
                }
            }
        }
    }

    private fun captureRoomFromFragment(fragment: Any) {
        if (invalidationTrackerRef?.get() != null) return
        val viewModel = resolveDiscoverViewModel(fragment) ?: return
        for (field in viewModel.javaClass.declaredFields) {
            try {
                field.isAccessible = true
                val repo = field.get(viewModel) ?: continue
                for (repoField in repo.javaClass.declaredFields) {
                    try {
                        repoField.isAccessible = true
                        val obj = repoField.get(repo) ?: continue
                        val trackerMethod = obj.javaClass.methods.firstOrNull { it.name == "getInvalidationTracker" }
                        val tracker = trackerMethod?.invoke(obj)
                            ?: obj.javaClass.declaredFields.firstOrNull { it.name == "mInvalidationTracker" }?.apply { isAccessible = true }?.get(obj)
                        if (tracker != null) {
                            invalidationTrackerRef = WeakReference(tracker)
                            roomDbRef = WeakReference(obj)
                            Log.i(Consts.TAG, "FeedNavigator: Captured Room InvalidationTracker from repository")
                            return
                        }
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
    }

    fun triggerDiscoverTabReselect(activity: Activity) {
        attempt("trigger Discover tab reselect", silent = true) {
            val decor = activity.window?.decorView ?: return@attempt
            val screenHeight = activity.resources.displayMetrics.heightPixels
            val screenWidth = activity.resources.displayMetrics.widthPixels

            // 1. Accessibility click on "Discover" node
            val provider = decor.accessibilityNodeProvider
            if (provider != null) {
                val clicked = clickTabNode(provider, AccessibilityNodeProvider.HOST_VIEW_ID, "Discover")
                if (clicked) {
                    Log.i(Consts.TAG, "FeedNavigator: Discover tab clicked via accessibility provider")
                    return@attempt
                }
            }

            // 2. Synthetic tap fallback on Discover tab coordinates
            val bottomNavTop = HostAppAiFab.findBottomNavTop(decor, screenHeight)
            if (bottomNavTop != null) {
                val targetX = screenWidth * 0.10f
                val targetY = (bottomNavTop + screenHeight) / 2f
                val downTime = SystemClock.uptimeMillis()
                val down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, targetX, targetY, 0)
                val up = MotionEvent.obtain(downTime, downTime + 30, MotionEvent.ACTION_UP, targetX, targetY, 0)
                decor.dispatchTouchEvent(down)
                decor.dispatchTouchEvent(up)
                down.recycle()
                up.recycle()
                Log.i(Consts.TAG, "FeedNavigator: Dispatched tap to Discover tab coordinates ($targetX, $targetY)")
            }
        }
    }

    private fun clickTabNode(
        provider: AccessibilityNodeProvider,
        virtualId: Int,
        tabLabel: String,
        visited: MutableSet<Int> = mutableSetOf(),
        depth: Int = 0
    ): Boolean {
        if (depth > 30 || !visited.add(virtualId)) return false
        val node = try {
            provider.createAccessibilityNodeInfo(virtualId)
        } catch (_: Throwable) { null } ?: return false

        val desc = node.contentDescription?.toString()?.trim()
        val text = node.text?.toString()?.trim()
        if ((desc != null && desc.equals(tabLabel, ignoreCase = true)) ||
            (text != null && text.equals(tabLabel, ignoreCase = true))) {
            val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            if (clicked) return true
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val childId = HostAppAiFab.getChildVirtualId(node, i) ?: continue
            if (clickTabNode(provider, childId, tabLabel, visited, depth + 1)) {
                return true
            }
        }
        return false
    }
}
