package io.github.s1ddhants1.unhinge.hook.ui

import android.app.Activity
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.github.s1ddhants1.unhinge.Consts
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
        val tracker = invalidationTrackerRef?.get()
        if (tracker != null) {
            try {
                val notifyMethod = tracker.javaClass.methods.firstOrNull {
                    it.name == "notifyObserversByTableNames" &&
                    it.parameterTypes.size == 1 &&
                    it.parameterTypes[0] == Array<String>::class.java
                }
                notifyMethod?.invoke(tracker, arrayOf("discover_subject"))

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
