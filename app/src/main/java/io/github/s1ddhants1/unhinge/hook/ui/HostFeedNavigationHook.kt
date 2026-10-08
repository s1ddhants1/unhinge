package io.github.s1ddhants1.unhinge.hook.ui

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteStatement
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.hook.hookTracked
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.attempt
import java.lang.reflect.Field

object HostFeedNavigationHook : HookHandler {

    private val LIMIT_TAIL_REGEX = Regex(
        """\bLIMIT\s+(\?|\d+)(?:\s+OFFSET\s+(\?|\d+)|\s*,\s*(\?|\d+))?\s*;?\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val USER_ID_EQUALITY_REGEX = Regex(
        """[`"]?(?:discover_subject[`"]?\.)?[`"]?userId[`"]?\s*=\s*(?:\?|'[^']*')""",
        RegexOption.IGNORE_CASE
    )

    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        if (!prefs.enableFeedNavigation) return

        val rawQueryMethods = attempt("find rawQueryWithFactory methods", silent = true) {
            SQLiteDatabase::class.java.declaredMethods.filter {
                it.name == "rawQueryWithFactory"
            }
        } ?: emptyList()

        if (rawQueryMethods.isEmpty()) {
            Log.w(Consts.TAG, "HostFeedNavigationHook: no rawQueryWithFactory methods found")
        }

        rawQueryMethods.forEachIndexed { index, method ->
            module.hookTracked(method, idPrefix = "feed-nav-rawquery-$index", deoptimize = false)
                .intercept { chain ->
                    if (FeedNavigator.isInternalQuery.get() == true) {
                        return@intercept chain.proceed()
                    }

                    val db = chain.thisObject as? SQLiteDatabase
                    if (db != null && isHingeDatabase(db)) {
                        FeedNavigator.setDbReference(db)
                    }

                    val sql = chain.args.getOrNull(1) as? String
                    if (sql != null && isTargetQuery(sql)) {
                        val offset = FeedNavigator.currentOffset
                        if (offset > 0) {
                            val modified = injectOffset(sql, offset)
                            if (modified != sql) {
                                chain.args[1] = modified
                                Log.d(Consts.TAG, "FeedNav: OFFSET $offset injected into discover_subject query")
                            }
                        }
                    }

                    chain.proceed()
                }
        }

        attempt<Unit>("hook SQLiteOpenHelper.getWritableDatabase", silent = true) {
            val helperMethod = SQLiteOpenHelper::class.java.getDeclaredMethod("getWritableDatabase")
            module.hookTracked(helperMethod, idPrefix = "feed-nav-helper-writable", deoptimize = true)
                .intercept { chain ->
                    val result = chain.proceed()
                    val db = result as? SQLiteDatabase
                    if (db != null && isHingeDatabase(db)) {
                        FeedNavigator.setDbReference(db)
                    }
                    result
                }
        }

        attempt<Unit>("hook RoomDatabase.init", silent = true) {
            val roomDbClass = Class.forName("androidx.room.RoomDatabase", false, classLoader)
            val configClass = Class.forName("androidx.room.DatabaseConfiguration", false, classLoader)
            val initMethod = roomDbClass.getDeclaredMethod("init", configClass)
            module.hookTracked(initMethod, idPrefix = "feed-nav-room-init", deoptimize = true)
                .intercept { chain ->
                    val result = chain.proceed()
                    val roomDb = chain.thisObject
                    if (roomDb != null) {
                        FeedNavigator.setRoomDatabase(roomDb)
                    }
                    result
                }
        }

        attempt<Unit>("hook RoomDatabase.query for capture", silent = true) {
            val roomDbClass = Class.forName("androidx.room.RoomDatabase", false, classLoader)
            val queryMethods = roomDbClass.declaredMethods.filter { it.name == "query" }
            queryMethods.forEachIndexed { i, m ->
                module.hookTracked(m, idPrefix = "feed-nav-room-query-$i", deoptimize = false)
                    .intercept { chain ->
                        val roomDb = chain.thisObject
                        if (roomDb != null) {
                            FeedNavigator.setRoomDatabase(roomDb)
                        }
                        chain.proceed()
                    }
            }
        }

        attempt<Unit>("hook SQLiteStatement.executeInsert", silent = false) {
            val executeInsertMethod = SQLiteStatement::class.java.getDeclaredMethod("executeInsert")
            module.hookTracked(executeInsertMethod, idPrefix = "feed-nav-stmt-insert", deoptimize = true)
                .intercept { chain ->
                    val stmt = chain.thisObject as? SQLiteStatement
                    val sql = getStatementSql(stmt)
                    val db = getStatementDb(stmt)

                    if (db != null && isHingeDatabase(db)) {
                        FeedNavigator.setDbReference(db)
                    }

                    val isPendingRatings = sql != null && sql.contains("pending_ratings", ignoreCase = true) &&
                            (sql.startsWith("INSERT", ignoreCase = true) || sql.contains("INSERT", ignoreCase = true))

                    val result = chain.proceed()

                    if (isPendingRatings) {
                        FeedNavigator.onRatingInserted()
                    }

                    result
                }
        }

        attempt<Unit>("hook SQLiteStatement.execute", silent = true) {
            val executeMethod = SQLiteStatement::class.java.getDeclaredMethod("execute")
            module.hookTracked(executeMethod, idPrefix = "feed-nav-stmt-execute", deoptimize = true)
                .intercept { chain ->
                    val stmt = chain.thisObject as? SQLiteStatement
                    val sql = getStatementSql(stmt)
                    val db = getStatementDb(stmt)

                    if (db != null && isHingeDatabase(db)) {
                        FeedNavigator.setDbReference(db)
                    }

                    val isPendingRatings = sql != null && sql.contains("pending_ratings", ignoreCase = true) &&
                            (sql.startsWith("INSERT", ignoreCase = true) || sql.contains("INSERT", ignoreCase = true))

                    val result = chain.proceed()

                    if (isPendingRatings) {
                        FeedNavigator.onRatingInserted()
                    }

                    result
                }
        }

        attempt<Unit>("hook SQLiteDatabase.insertWithOnConflict", silent = true) {
            val insertMethod = SQLiteDatabase::class.java.getDeclaredMethod(
                "insertWithOnConflict",
                String::class.java,
                String::class.java,
                android.content.ContentValues::class.java,
                Int::class.javaPrimitiveType
            )
            module.hookTracked(insertMethod, idPrefix = "feed-nav-insert", deoptimize = true)
                .intercept { chain ->
                    val db = chain.thisObject as? SQLiteDatabase
                    if (db != null && isHingeDatabase(db)) {
                        FeedNavigator.setDbReference(db)
                    }

                    val table = chain.args.getOrNull(0) as? String
                    val result = chain.proceed()

                    if (table == "pending_ratings") {
                        FeedNavigator.onRatingInserted()
                    }

                    result
                }
        }

        attempt<Unit>("hook SharedPreferences.getStringSet for unlimited undos", silent = true) {
            val prefsClass = Class.forName("android.app.SharedPreferencesImpl", false, classLoader)
            val getStringSetMethod = prefsClass.getDeclaredMethod("getStringSet", String::class.java, Set::class.java)
            module.hookTracked(getStringSetMethod, idPrefix = "feed-nav-prefs-getstringset", deoptimize = true)
                .intercept { chain ->
                    val result = chain.proceed()
                    val key = chain.args.getOrNull(0) as? String
                    if (key == "USER_PERMISSIONS") {
                        val set = (result as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: mutableSetOf()
                        if (!set.contains("undo_skip_replenish_unlimited")) {
                            set.add("undo_skip_replenish_unlimited")
                            return@intercept set
                        }
                    }
                    result
                }
        }

        Log.i(Consts.TAG, "HostFeedNavigationHook installed on rawQuery + SQLiteStatement + SQLiteOpenHelper + SharedPreferences")
    }

    fun isHingeDatabase(db: SQLiteDatabase?): Boolean {
        if (db == null || !db.isOpen) return false
        val path = db.path ?: return false
        return path.endsWith("/databases/db") ||
                path.endsWith("/databases/db-wal") ||
                path.endsWith("/databases/db-shm") ||
                path == "/data/data/co.hinge.app/databases/db" ||
                path == "/data/user/0/co.hinge.app/databases/db"
    }

    private val sqlField: Field? by lazy {
        attempt("find SQLiteProgram.mSql field", silent = true) {
            findProgramField("mSql")
        }
    }

    private val dbField: Field? by lazy {
        attempt("find SQLiteProgram.mDatabase field", silent = true) {
            findProgramField("mDatabase")
        }
    }

    private fun findProgramField(name: String): Field? {
        var c: Class<*>? = SQLiteStatement::class.java
        while (c != null) {
            try {
                val f = c.getDeclaredField(name)
                f.isAccessible = true
                return f
            } catch (_: NoSuchFieldException) {
                c = c.superclass
            }
        }
        return null
    }

    fun getStatementSql(stmt: Any?): String? {
        if (stmt == null) return null
        val fromField = sqlField?.get(stmt) as? String
        if (!fromField.isNullOrBlank()) return fromField
        val str = stmt.toString()
        return if (str.startsWith("SQLiteProgram: ")) {
            str.substring("SQLiteProgram: ".length).trim()
        } else str
    }

    fun getStatementDb(stmt: Any?): SQLiteDatabase? {
        if (stmt == null) return null
        return (dbField?.get(stmt) as? SQLiteDatabase) ?: FeedNavigator.getWritableDb()
    }

    fun isTargetQuery(sql: String): Boolean {
        if (!sql.contains("discover_subject", ignoreCase = true)) return false
        val clean = sql.trimStart()
        val queryStart = if (clean.startsWith("/*", ignoreCase = true)) {
            clean.substringAfter("*/", "").trimStart()
        } else clean

        if (!queryStart.startsWith("SELECT", ignoreCase = true)) return false

        if (queryStart.startsWith("SELECT EXISTS", ignoreCase = true) ||
            queryStart.startsWith("SELECT COUNT", ignoreCase = true)) {
            return false
        }
        if (sql.contains("COUNT(", ignoreCase = true)) return false

        if (queryStart.startsWith("SELECT DISTINCT", ignoreCase = true) && !sql.contains("ORDER BY", ignoreCase = true)) {
            return false
        }

        if (USER_ID_EQUALITY_REGEX.containsMatchIn(sql)) return false

        return true
    }

    fun injectOffset(sql: String, offset: Int): String {
        if (offset <= 0) return sql

        val trimmed = sql.trimEnd()
        val hasTrailingSemicolon = trimmed.endsWith(";")
        val cleanSql = if (hasTrailingSemicolon) trimmed.removeSuffix(";").trimEnd() else trimmed

        val limitMatch = LIMIT_TAIL_REGEX.find(cleanSql)
        val resultSql = if (limitMatch != null) {
            val limitValue = limitMatch.groupValues[1]
            cleanSql.substring(0, limitMatch.range.first) + "LIMIT $limitValue OFFSET $offset"
        } else {
            "$cleanSql LIMIT -1 OFFSET $offset"
        }

        return if (hasTrailingSemicolon) "$resultSql;" else resultSql
    }
}
