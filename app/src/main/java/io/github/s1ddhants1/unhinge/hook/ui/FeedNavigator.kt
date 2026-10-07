package io.github.s1ddhants1.unhinge.hook.ui

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import android.util.Log
import io.github.s1ddhants1.unhinge.Consts
import java.io.File
import java.lang.ref.WeakReference
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

object FeedNavigator {

    private val virtualSkipStack = ArrayDeque<String>()
    private val offset = AtomicInteger(0)
    @Volatile private var cachedTotal: Int = -1
    @Volatile private var dbRef: WeakReference<SQLiteDatabase>? = null
    @Volatile private var roomDbRef: WeakReference<Any>? = null
    @Volatile private var invalidationTrackerRef: WeakReference<Any>? = null
    @Volatile private var virtualBrowsingUntil = 0L
    @Volatile var isReloading = false

    var isVirtualBrowsing: Boolean
        get() = SystemClock.uptimeMillis() < virtualBrowsingUntil
        set(value) {
            virtualBrowsingUntil = if (value) SystemClock.uptimeMillis() + 2500L else 0L
        }

    val isInternalQuery: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    val currentOffset: Int get() = maxOf(virtualSkipStack.size, offset.get())
    val totalCandidates: Int get() = cachedTotal

    val displayPosition: Int get() = currentOffset + 1

    val isNavigated: Boolean get() = currentOffset > 0

    fun setDbReference(db: SQLiteDatabase) {
        if (!db.isReadOnly && db.isOpen) {
            val prev = dbRef?.get()
            dbRef = WeakReference(db)
            if (prev !== db) {
                cleanOrphanedVirtualRatings(db)
            }
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

    fun cleanOrphanedVirtualRatings(db: SQLiteDatabase) {
        val toClean = ArrayList(virtualSkipStack)
        try {
            var purged = 0
            for (userId in toClean) {
                purged += try {
                    db.delete("pending_ratings", "subjectId = ? AND rating = 'skip'", arrayOf(userId))
                } catch (_: Exception) { 0 }
            }
            Log.i(Consts.TAG, "FeedNavigator: purged $purged orphaned virtual ratings")
        } catch (e: Exception) {
            Log.w(Consts.TAG, "FeedNavigator: cleanOrphanedVirtualRatings failed: ${e.message}")
        } finally {
            virtualSkipStack.clear()
            offset.set(0)
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

    fun onVirtualSkipInserted(userId: String) {
        if (!virtualSkipStack.contains(userId)) {
            virtualSkipStack.addLast(userId)
            offset.set(virtualSkipStack.size)
            Log.i(Consts.TAG, "FeedNavigator: virtual skip recorded for $userId. Stack size: ${virtualSkipStack.size}")
            HostAppAiFab.notifyNavUpdated()
        }
    }

    fun navigateForward(context: Context? = null): Boolean {
        val activity = context as? Activity ?: return false
        if (isReloading) return false
        val total = refreshTotal(activity)
        if (total in 1..displayPosition) {
            Log.i(Consts.TAG, "FeedNavigator: forward blocked - at end ($displayPosition/$total)")
            return false
        }

        isReloading = true
        isVirtualBrowsing = true
        val stackSizeBefore = virtualSkipStack.size
        val knownIds = virtualSkipStack.toHashSet()
        Log.i(Consts.TAG, "FeedNavigator: forward tap initiated at pos=$displayPosition/$total")

        val dispatched = HostAppAiFab.dispatchPassTap(activity) {
            isVirtualBrowsing = false
            activity.window?.decorView?.postDelayed({
                if (virtualSkipStack.size <= stackSizeBefore) {
                    val recovered = recoverUnrecordedSkip(activity, knownIds)
                    if (recovered != null) {
                        onVirtualSkipInserted(recovered)
                        Log.w(Consts.TAG, "FeedNavigator: forward recovered untracked skip for $recovered")
                    } else {
                        Log.w(Consts.TAG, "FeedNavigator: forward completed with no recorded skip")
                    }
                }
                isReloading = false
                HostAppAiFab.notifyNavUpdated(activity)
            }, 150)
        }

        if (!dispatched) {
            isReloading = false
            isVirtualBrowsing = false
        }
        return dispatched
    }

    fun navigateBack(context: Context? = null): Boolean {
        val activity = context as? Activity ?: return false
        val targetUserId = virtualSkipStack.peekLast() ?: return false
        if (isReloading) return false

        isReloading = true
        isVirtualBrowsing = true
        Log.i(Consts.TAG, "FeedNavigator: back tap initiated at pos=$displayPosition/$cachedTotal")

        val dispatched = HostAppAiFab.dispatchUndoTap(activity) {
            isVirtualBrowsing = false
            activity.window?.decorView?.postDelayed({
                val rowGone = !isSubjectRated(activity, targetUserId)
                if (confirmBackNavigation(targetUserId, rowGone)) {
                    Log.i(Consts.TAG, "FeedNavigator: back confirmed, returned to $targetUserId")
                } else {
                    Log.w(Consts.TAG, "FeedNavigator: undo not verified for $targetUserId, keeping nav position")
                }
                isReloading = false
                HostAppAiFab.notifyNavUpdated(activity)
            }, 150)
        }

        if (!dispatched) {
            isReloading = false
            isVirtualBrowsing = false
        }

        return dispatched
    }

    internal fun confirmBackNavigation(targetUserId: String?, undoSucceeded: Boolean): Boolean {
        if (targetUserId.isNullOrBlank() || !undoSucceeded) return false
        val removed = if (virtualSkipStack.peekLast() == targetUserId) {
            virtualSkipStack.removeLast()
            true
        } else {
            virtualSkipStack.remove(targetUserId)
        }
        if (removed) {
            offset.set(virtualSkipStack.size)
            HostAppAiFab.notifyNavUpdated()
        }
        return removed
    }

    internal fun selectUnknownId(knownIds: Set<String>, latestIds: List<String>): String? {
        return latestIds.firstOrNull { it.isNotBlank() && it !in knownIds }
    }

    internal fun isSubjectRated(context: Context?, userId: String): Boolean {
        if (userId.isBlank()) return false
        val captured = dbRef?.get()
        if (captured != null && captured.isOpen) {
            try {
                captured.rawQuery(
                    "SELECT 1 FROM pending_ratings WHERE subjectId = ? LIMIT 1",
                    arrayOf(userId)
                ).use { c ->
                    if (c.moveToFirst()) return true
                }
            } catch (_: Exception) {}
        }
        resolveDbPath(context)?.let { targetPath ->
            try {
                SQLiteDatabase.openDatabase(targetPath, null, SQLiteDatabase.OPEN_READONLY).use { readOnlyDb ->
                    readOnlyDb.rawQuery(
                        "SELECT 1 FROM pending_ratings WHERE subjectId = ? LIMIT 1",
                        arrayOf(userId)
                    ).use { c ->
                        if (c.moveToFirst()) return true
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    internal fun latestUnsyncedSkipIds(context: Context?, limit: Int = 5): List<String> {
        val ids = ArrayList<String>()
        val sql = "SELECT subjectId FROM pending_ratings WHERE rating = 'skip' " +
            "AND sentTime IS NULL ORDER BY id DESC LIMIT $limit"
        val captured = dbRef?.get()
        if (captured != null && captured.isOpen) {
            try {
                captured.rawQuery(sql, null).use { c ->
                    while (c.moveToNext()) ids.add(c.getString(0) ?: "")
                }
                if (ids.isNotEmpty()) return ids
            } catch (_: Exception) {}
        }
        resolveDbPath(context)?.let { targetPath ->
            try {
                SQLiteDatabase.openDatabase(targetPath, null, SQLiteDatabase.OPEN_READONLY).use { readOnlyDb ->
                    readOnlyDb.rawQuery(sql, null).use { c ->
                        while (c.moveToNext()) ids.add(c.getString(0) ?: "")
                    }
                }
            } catch (_: Exception) {}
        }
        return ids
    }

    internal fun recoverUnrecordedSkip(context: Context?, knownIds: Set<String>): String? {
        return selectUnknownId(knownIds, latestUnsyncedSkipIds(context))
    }

    private fun resolveDbPath(context: Context?): String? {
        val dbFile = context?.getDatabasePath("db")
        return when {
            dbFile != null && dbFile.exists() -> dbFile.absolutePath
            File("/data/data/co.hinge.app/databases/db").exists() -> "/data/data/co.hinge.app/databases/db"
            File("/data/user/0/co.hinge.app/databases/db").exists() -> "/data/user/0/co.hinge.app/databases/db"
            else -> null
        }
    }

    fun reset(context: Context? = null) {
        val wasNavigated = virtualSkipStack.isNotEmpty() || offset.getAndSet(0) != 0
        val toClean = ArrayList(virtualSkipStack)
        virtualSkipStack.clear()
        offset.set(0)
        isVirtualBrowsing = false
        isReloading = false

        val db = getWritableDb(context)
        if (db != null && db.isOpen && !db.isReadOnly && toClean.isNotEmpty()) {
            isInternalQuery.set(true)
            try {
                for (userId in toClean) {
                    db.delete("pending_ratings", "subjectId = ? AND rating = 'skip'", arrayOf(userId))
                }
                Log.d(Consts.TAG, "FeedNavigator: reset purged ${toClean.size} virtual skips")
            } catch (e: Exception) {
                Log.w(Consts.TAG, "FeedNavigator: reset delete failed: ${e.message}")
            } finally {
                isInternalQuery.set(false)
            }
        }

        if (wasNavigated && context is Activity) {
            triggerHingeRefresh(context)
        }
    }

    fun refreshTotal(context: Context? = null): Int {
        val virtualCount = virtualSkipStack.size
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
                            cachedTotal = c.getInt(0) + virtualCount
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
                                    cachedTotal = c.getInt(0) + virtualCount
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
        if (total > 0 && virtualSkipStack.size >= total) {
            while (virtualSkipStack.size >= total && virtualSkipStack.isNotEmpty()) {
                virtualSkipStack.removeLast()
            }
            offset.set(virtualSkipStack.size)
        }
    }

    fun triggerHingeRefresh(activity: Activity? = null) {
        val tracker = invalidationTrackerRef?.get()
        if (tracker != null) {
            try {
                val notifyMethod = tracker.javaClass.methods.firstOrNull {
                    it.name == "notifyObserversByTableNames" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Array<String>::class.java
                }
                notifyMethod?.invoke(tracker, arrayOf("pending_ratings", "discover_subject"))

                val refreshAsync = tracker.javaClass.methods.firstOrNull { it.name == "refreshVersionsAsync" }
                refreshAsync?.invoke(tracker)
                Log.i(Consts.TAG, "FeedNavigator: Room InvalidationTracker notified")
            } catch (e: Exception) {
                Log.w(Consts.TAG, "FeedNavigator: InvalidationTracker trigger failed: ${e.message}")
            }
        }

        val db = getWritableDb(activity)
        if (db != null && db.isOpen && !db.isReadOnly) {
            isInternalQuery.set(true)
            try {
                db.execSQL("UPDATE discover_subject SET batchId = batchId WHERE rowid = (SELECT MIN(rowid) FROM discover_subject)")
            } catch (_: Exception) {}
            finally {
                isInternalQuery.set(false)
            }
        }
    }
}
