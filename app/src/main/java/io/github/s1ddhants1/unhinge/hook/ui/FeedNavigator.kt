package io.github.s1ddhants1.unhinge.hook.ui

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.view.View
import android.view.ViewGroup
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.util.attempt
import java.io.File
import java.lang.ref.WeakReference
import java.lang.reflect.Modifier
import java.util.concurrent.atomic.AtomicInteger

object FeedNavigator {

    private val navOffset = AtomicInteger(0)
    @Volatile private var cachedTotal: Int = -1
    @Volatile private var dbRef: WeakReference<SQLiteDatabase>? = null
    @Volatile private var roomDbRef: WeakReference<Any>? = null
    @Volatile private var invalidationTrackerRef: WeakReference<Any>? = null
    @Volatile private var trackerInstance: Any? = null
    @Volatile private var roomDbInstance: Any? = null

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
        if (roomDbInstance === db && trackerInstance != null) return
        roomDbInstance = db
        roomDbRef = WeakReference(db)
        try {
            var tracker: Any? = null
            val trackerMethod = db.javaClass.methods.firstOrNull { it.name == "getInvalidationTracker" }
            if (trackerMethod != null) {
                tracker = trackerMethod.invoke(db)
            }
            if (tracker == null) {
                var c: Class<*>? = db.javaClass
                while (c != null && tracker == null) {
                    val f = c.declaredFields.firstOrNull { it.name == "internalTracker" || it.name == "mInvalidationTracker" }
                    if (f != null) {
                        f.isAccessible = true
                        tracker = f.get(db)
                    }
                    c = c.superclass
                }
            }
            if (tracker != null) {
                trackerInstance = tracker
                invalidationTrackerRef = WeakReference(tracker)
                Log.i(Consts.TAG, "FeedNavigator: captured Room InvalidationTracker successfully (${tracker.javaClass.name})")
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

        val tracker = getOrFindTracker(act)
        if (tracker != null) {
            attempt("notify Room InvalidationTracker", silent = false) {
                notifyTableInvalidation(tracker)
            }
        }

        val db = getWritableDb(act)
        if (db != null && db.isOpen && !db.isReadOnly) {
            isInternalQuery.set(true)
            try {
                db.execSQL("UPDATE discover_subject SET batchId = batchId WHERE rowid = (SELECT MIN(rowid) FROM discover_subject)")
            } catch (_: Throwable) {}
            finally {
                isInternalQuery.set(false)
            }
        }

        if (act != null) {
            act.runOnUiThread {
                attempt("request ComposeView invalidation", silent = true) {
                    val decor = act.window?.decorView ?: return@attempt
                    decor.post {
                        requestComposeInvalidation(decor)
                    }
                }
            }
        }
    }

    private fun bumpStateFlowVersions(obj: Any, visited: MutableSet<Int> = mutableSetOf(), depth: Int = 0): Boolean {
        if (depth > 4) return false
        val id = System.identityHashCode(obj)
        if (!visited.add(id)) return false

        for (field in obj.javaClass.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) continue
            try {
                field.isAccessible = true
                val value = field.get(obj) ?: continue
                val getValueMethod = value.javaClass.methods.firstOrNull { it.name == "getValue" && it.parameterTypes.isEmpty() }
                if (getValueMethod != null) {
                    val current = getValueMethod.invoke(value)
                    if (current is IntArray) {
                        val bumped = current.clone()
                        for (i in bumped.indices) {
                            bumped[i]++
                        }
                        val setValueMethod = value.javaClass.methods.firstOrNull {
                            it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(IntArray::class.java)
                        } ?: value.javaClass.declaredMethods.firstOrNull {
                            it.parameterTypes.size == 1 && it.parameterTypes[0].isAssignableFrom(IntArray::class.java)
                        }
                        if (setValueMethod != null) {
                            setValueMethod.isAccessible = true
                            setValueMethod.invoke(value, bumped)
                            Log.i(Consts.TAG, "FeedNavigator: bumped Room StateFlow table versions (${bumped.size} tables)")
                            return true
                        }
                    }
                }
                if (depth < 3 && !value.javaClass.name.startsWith("java.") && !value.javaClass.name.startsWith("android.")) {
                    if (bumpStateFlowVersions(value, visited, depth + 1)) {
                        return true
                    }
                }
            } catch (_: Throwable) {}
        }
        return false
    }

    private fun notifyTableInvalidation(tracker: Any) {
        val tableNames = arrayOf("discover_subject", "discover_batch", "profiles", "subject_media", "pending_ratings")
        val tableSet = tableNames.toSet()
        val intSet = (0..256).toSet()

        bumpStateFlowVersions(tracker)

        for (m in tracker.javaClass.methods + tracker.javaClass.declaredMethods) {
            if (m.name in setOf("equals", "hashCode", "toString", "wait", "notify", "notifyAll")) continue
            if (m.parameterTypes.size == 1) {
                val p = m.parameterTypes[0]
                try {
                    if (p == Array<String>::class.java) {
                        m.isAccessible = true
                        m.invoke(tracker, tableNames)
                        Log.i(Consts.TAG, "FeedNavigator: notified via ${m.name}(Array<String>)")
                    } else if (p == Set::class.java || p == Collection::class.java) {
                        m.isAccessible = true
                        m.invoke(tracker, tableSet)
                        m.invoke(tracker, intSet)
                        Log.i(Consts.TAG, "FeedNavigator: notified via ${m.name}(Set)")
                    }
                } catch (_: Throwable) {}
            } else if (m.parameterTypes.size == 2 &&
                Function0::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                Function0::class.java.isAssignableFrom(m.parameterTypes[1])) {
                try {
                    m.isAccessible = true
                    m.invoke(tracker, { }, { })
                    Log.i(Consts.TAG, "FeedNavigator: invoked tracker 2-arg refresh (${m.name})")
                } catch (_: Throwable) {}
            }
        }

        for (field in tracker.javaClass.declaredFields) {
            try {
                field.isAccessible = true
                val value = field.get(tracker) ?: continue
                when (value) {
                    is Function1<*, *> -> {
                        try {
                            @Suppress("UNCHECKED_CAST")
                            val fn = value as ((Any) -> Any?)
                            fn.invoke(tableSet)
                            fn.invoke(intSet)
                            Log.i(Consts.TAG, "FeedNavigator: notified via tracker field ${field.name} (Function1)")
                        } catch (_: Throwable) {}
                    }
                    is Function0<*> -> {
                        try {
                            value.invoke()
                            Log.i(Consts.TAG, "FeedNavigator: ran tracker field ${field.name} (Function0)")
                        } catch (_: Throwable) {}
                    }
                    is Runnable -> {
                        try {
                            value.run()
                            Log.i(Consts.TAG, "FeedNavigator: ran tracker field ${field.name} (Runnable)")
                        } catch (_: Throwable) {}
                    }
                    is Map<*, *> -> {
                        notifyWrappers(value, tableSet, intSet)
                    }
                    else -> {
                        for (m in value.javaClass.methods + value.javaClass.declaredMethods) {
                            if (m.name in setOf("equals", "hashCode", "toString", "wait", "notify", "notifyAll")) continue
                            if (m.parameterTypes.size == 2 &&
                                Function0::class.java.isAssignableFrom(m.parameterTypes[0]) &&
                                Function0::class.java.isAssignableFrom(m.parameterTypes[1])) {
                                try {
                                    m.isAccessible = true
                                    m.invoke(value, { }, { })
                                    Log.i(Consts.TAG, "FeedNavigator: invoked subField ${field.name} refresh (${m.name})")
                                } catch (_: Throwable) {}
                            }
                        }
                        for (subField in value.javaClass.declaredFields) {
                            try {
                                subField.isAccessible = true
                                val subValue = subField.get(value) ?: continue
                                when (subValue) {
                                    is Function1<*, *> -> {
                                        try {
                                            @Suppress("UNCHECKED_CAST")
                                            val fn = subValue as ((Any) -> Any?)
                                            fn.invoke(tableSet)
                                            fn.invoke(intSet)
                                            Log.i(Consts.TAG, "FeedNavigator: notified via subField ${field.name}.${subField.name} (Function1)")
                                        } catch (_: Throwable) {}
                                    }
                                    is Function0<*> -> {
                                        try {
                                            subValue.invoke()
                                            Log.i(Consts.TAG, "FeedNavigator: notified via subField ${field.name}.${subField.name} (Function0)")
                                        } catch (_: Throwable) {}
                                    }
                                    is Map<*, *> -> {
                                        notifyWrappers(subValue, tableSet, intSet)
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun notifyWrappers(map: Map<*, *>, tableSet: Set<String>, intSet: Set<Int>) {
        val wrappers = map.values.toList()
        for (wrapper in wrappers) {
            if (wrapper == null) continue
            for (wm in wrapper.javaClass.methods + wrapper.javaClass.declaredMethods) {
                if (wm.name in setOf("equals", "hashCode", "toString", "wait", "notify", "notifyAll")) continue
                if (wm.parameterTypes.size == 1 &&
                    (wm.parameterTypes[0] == Set::class.java || wm.parameterTypes[0] == Collection::class.java)) {
                    try {
                        wm.isAccessible = true
                        wm.invoke(wrapper, intSet)
                        wm.invoke(wrapper, tableSet)
                        Log.i(Consts.TAG, "FeedNavigator: notified wrapper ${wrapper.javaClass.simpleName}#${wm.name}")
                    } catch (_: Throwable) {}
                }
            }
            for (wf in wrapper.javaClass.declaredFields) {
                try {
                    wf.isAccessible = true
                    val target = wf.get(wrapper) ?: continue
                    for (tm in target.javaClass.methods + target.javaClass.declaredMethods) {
                        if (tm.name in setOf("equals", "hashCode", "toString", "wait", "notify", "notifyAll")) continue
                        if (tm.parameterTypes.size == 1 &&
                            (tm.parameterTypes[0] == Set::class.java || tm.parameterTypes[0] == Collection::class.java)) {
                            tm.isAccessible = true
                            tm.invoke(target, tableSet)
                            tm.invoke(target, intSet)
                        }
                    }
                } catch (_: Throwable) {}
            }
        }
    }

    private fun requestComposeInvalidation(view: View) {
        if (view.javaClass.name.contains("ComposeView")) {
            view.invalidate()
            view.requestLayout()
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                requestComposeInvalidation(view.getChildAt(i))
            }
        }
    }

    fun getOrFindTracker(activity: Activity? = null): Any? {
        val existing = invalidationTrackerRef?.get()
        if (existing != null) return existing

        val roomDb = roomDbRef?.get()
        if (roomDb != null) {
            setRoomDatabase(roomDb)
            val resolved = invalidationTrackerRef?.get()
            if (resolved != null) return resolved
        }

        val act = activity ?: HostAppAiFab.currentActivityRef?.get() ?: return null
        val fragment = findDiscoverFragment(act)
        if (fragment != null) {
            searchAndCaptureRoom(fragment, mutableSetOf(), 0, 3)
        }
        if (invalidationTrackerRef?.get() == null) {
            searchAndCaptureRoom(act, mutableSetOf(), 0, 2)
        }
        return invalidationTrackerRef?.get()
    }

    private fun searchAndCaptureRoom(obj: Any, visited: MutableSet<Int>, depth: Int, maxDepth: Int) {
        if (depth > maxDepth || invalidationTrackerRef?.get() != null) return
        val id = System.identityHashCode(obj)
        if (!visited.add(id)) return
        val cls = obj.javaClass
        if (cls.name == "co.hinge.common.storage.db.Database_Impl" || cls.name.endsWith(".Database_Impl")) {
            setRoomDatabase(obj)
            return
        }
        for (field in cls.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) continue
            val type = field.type
            if (type.isPrimitive || type == String::class.java) continue
            try {
                field.isAccessible = true
                val child = field.get(obj) ?: continue
                if (child.javaClass.name.contains("Database_Impl")) {
                    setRoomDatabase(child)
                    return
                }
                searchAndCaptureRoom(child, visited, depth + 1, maxDepth)
            } catch (_: Throwable) {}
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
}
