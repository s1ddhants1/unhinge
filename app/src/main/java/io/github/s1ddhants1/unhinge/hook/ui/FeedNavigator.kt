package io.github.s1ddhants1.unhinge.hook.ui

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.github.s1ddhants1.unhinge.Consts
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

/**
 * Maintains a virtual navigation offset for browsing cached Discover candidates
 * without consuming likes or passes.
 *
 * When the user taps forward/back arrows, the offset shifts which profile Hinge's
 * internal Room queries resolve as "current" by cooperating with
 * [HostFeedNavigationHook]'s SQL OFFSET injection. After updating the offset,
 * [triggerHingeRefresh] executes a no-op UPDATE through Hinge's own writable
 * database connection, firing Room's TEMP InvalidationTracker triggers and
 * causing all LiveData/Flow observers of `discover_subject` to re-execute.
 */
object FeedNavigator {

    private val offset = AtomicInteger(0)
    @Volatile private var cachedTotal: Int = 0
    @Volatile private var dbRef: WeakReference<SQLiteDatabase>? = null

    /**
     * Re-entrancy guard set to `true` on the calling thread when FeedNavigator
     * runs its own internal DB queries (COUNT, UPDATE). [HostFeedNavigationHook]
     * checks this flag to skip OFFSET injection for FeedNavigator's own operations.
     */
    val isInternalQuery: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }

    val currentOffset: Int get() = offset.get()
    val totalCandidates: Int get() = cachedTotal

    /** 1-indexed display position for the UI counter badge. */
    val displayPosition: Int get() = offset.get() + 1

    /** True when the user has navigated away from Hinge's natural first candidate. */
    val isNavigated: Boolean get() = offset.get() > 0

    /**
     * Captures Hinge's active writable [SQLiteDatabase] connection from the query hook.
     * Only stores writable connections so [triggerHingeRefresh] can execute the no-op UPDATE.
     */
    fun setDbReference(db: SQLiteDatabase) {
        if (!db.isReadOnly) {
            dbRef = WeakReference(db)
        }
    }

    fun navigateForward(): Boolean {
        val total = refreshTotal()
        if (total <= 1) return false
        val current = offset.get()
        if (current >= total - 1) return false
        offset.incrementAndGet()
        Log.i(Consts.TAG, "FeedNavigator: forward → offset=${offset.get()}/$total")
        triggerHingeRefresh()
        return true
    }

    fun navigateBack(): Boolean {
        if (offset.get() <= 0) return false
        offset.decrementAndGet()
        Log.i(Consts.TAG, "FeedNavigator: back → offset=${offset.get()}/$cachedTotal")
        triggerHingeRefresh()
        return true
    }

    fun reset() {
        val wasNavigated = offset.getAndSet(0) != 0
        if (wasNavigated) {
            Log.d(Consts.TAG, "FeedNavigator: reset to natural position")
            triggerHingeRefresh()
        }
    }

    /**
     * Counts available (non-pending) discover candidates.
     * Tries the filtered count first (excluding pending_ratings), falling back
     * to a raw COUNT(*) if the pending_ratings table is unavailable.
     */
    fun refreshTotal(): Int {
        val db = dbRef?.get() ?: return cachedTotal
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
                        return cachedTotal
                    }
                }
            } catch (_: Exception) {}

            db.rawQuery("SELECT COUNT(*) FROM discover_subject", null).use { c ->
                if (c.moveToFirst()) {
                    cachedTotal = c.getInt(0)
                }
            }
        } catch (e: Exception) {
            Log.w(Consts.TAG, "FeedNavigator: refreshTotal failed: ${e.message}")
        } finally {
            isInternalQuery.set(false)
        }
        return cachedTotal
    }

    /**
     * Forces Hinge to re-fetch the current discover candidate by executing a
     * no-op UPDATE on `discover_subject` through Hinge's own writable connection.
     * The UPDATE changes no data but fires Room's TEMP `AFTER UPDATE` triggers,
     * which mark `discover_subject` as invalidated in `room_table_modification_log`,
     * causing all LiveData/Flow observers to re-execute their queries.
     */
    private fun triggerHingeRefresh() {
        val db = dbRef?.get()
        if (db == null) {
            Log.w(Consts.TAG, "FeedNavigator: no DB reference for refresh trigger")
            return
        }
        if (db.isReadOnly) {
            Log.w(Consts.TAG, "FeedNavigator: DB is read-only, cannot trigger refresh")
            return
        }
        isInternalQuery.set(true)
        try {
            db.execSQL(
                "UPDATE discover_subject SET batchId = batchId " +
                "WHERE rowid = (SELECT MIN(rowid) FROM discover_subject)"
            )
            Log.d(Consts.TAG, "FeedNavigator: Room invalidation triggered via no-op UPDATE")
        } catch (e: Exception) {
            Log.w(Consts.TAG, "FeedNavigator: triggerHingeRefresh failed: ${e.message}")
        } finally {
            isInternalQuery.set(false)
        }
    }
}
