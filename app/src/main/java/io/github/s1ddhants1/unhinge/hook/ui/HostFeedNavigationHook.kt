package io.github.s1ddhants1.unhinge.hook.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.hook.hookTracked
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.attempt

/**
 * Intercepts [SQLiteDatabase.rawQueryWithFactory] to inject SQL OFFSET clauses
 * into Hinge's Room-generated `discover_subject` queries based on
 * [FeedNavigator.currentOffset], enabling free browsing of cached Discover
 * candidates without consuming likes or passes.
 *
 * Targets the framework-stable [SQLiteDatabase] API (not obfuscated R8 classes)
 * to ensure resilience across Hinge updates. The hook is lightweight on the fast
 * path: a [ThreadLocal] check and an [AtomicInteger] read (~10ns) when no
 * navigation is active.
 */
object HostFeedNavigationHook : HookHandler {

    /**
     * Matches `LIMIT <n>` at the tail of an SQL statement, optionally followed
     * by an existing OFFSET clause and trailing whitespace. Only the last
     * occurrence is captured to avoid rewriting subquery LIMITs.
     */
    private val LIMIT_TAIL_REGEX = Regex(
        """\bLIMIT\s+(\d+)\s*(?:OFFSET\s+\d+\s*)?$""",
        RegexOption.IGNORE_CASE
    )

    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        if (!prefs.enableFeedNavigation) return

        val method = attempt("find rawQueryWithFactory 5-arg", silent = true) {
            SQLiteDatabase::class.java.declaredMethods.firstOrNull {
                it.name == "rawQueryWithFactory" && it.parameterTypes.size == 5
            }
        } ?: attempt("find rawQueryWithFactory 4-arg", silent = true) {
            SQLiteDatabase::class.java.declaredMethods.firstOrNull {
                it.name == "rawQueryWithFactory" && it.parameterTypes.size == 4
            }
        }

        if (method == null) {
            Log.w(Consts.TAG, "HostFeedNavigationHook: rawQueryWithFactory not found")
            return
        }

        module.hookTracked(method, idPrefix = "feed-nav-rawquery", deoptimize = false)
            .intercept { chain ->
                if (FeedNavigator.isInternalQuery.get() == true) {
                    return@intercept chain.proceed()
                }

                val offset = FeedNavigator.currentOffset
                if (offset == 0) return@intercept chain.proceed()

                val sqlArgIndex = 1
                val sql = chain.args[sqlArgIndex] as? String
                    ?: return@intercept chain.proceed()

                if (!isTargetQuery(sql)) return@intercept chain.proceed()

                val db = chain.thisObject as? SQLiteDatabase
                if (db != null) FeedNavigator.setDbReference(db)

                val modifiedSql = injectOffset(sql, offset)
                if (modifiedSql != sql) {
                    chain.args[sqlArgIndex] = modifiedSql
                    Log.d(
                        Consts.TAG,
                        "FeedNav: OFFSET $offset injected into discover_subject query"
                    )
                }
                chain.proceed()
            }

        Log.i(Consts.TAG, "HostFeedNavigationHook installed on rawQueryWithFactory")
    }

    /**
     * Returns true only for SELECT queries on `discover_subject` that are NOT
     * aggregate counts (which FeedNavigator uses internally).
     */
    private fun isTargetQuery(sql: String): Boolean {
        if (!sql.contains("discover_subject", ignoreCase = true)) return false
        if (!sql.trimStart().startsWith("SELECT", ignoreCase = true)) return false
        if (sql.contains("COUNT(", ignoreCase = true)) return false
        return true
    }

    /**
     * Injects an OFFSET clause into a discover_subject SQL query.
     *
     * - Has `LIMIT N` at tail → replaces with `LIMIT N OFFSET <offset>`
     * - Has `ORDER BY` but no LIMIT → appends `LIMIT -1 OFFSET <offset>`
     * - Neither → returns original SQL unmodified
     */
    fun injectOffset(sql: String, offset: Int): String {
        val limitMatch = LIMIT_TAIL_REGEX.find(sql)
        if (limitMatch != null) {
            val limitValue = limitMatch.groupValues[1]
            return sql.substring(0, limitMatch.range.first) + "LIMIT $limitValue OFFSET $offset"
        }

        if (sql.contains("ORDER BY", ignoreCase = true)) {
            return "$sql LIMIT -1 OFFSET $offset"
        }

        return sql
    }
}
