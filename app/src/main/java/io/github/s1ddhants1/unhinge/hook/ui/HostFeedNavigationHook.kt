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

    private val isReentrant = ThreadLocal<Boolean>()

    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        if (!prefs.enableFeedNavigation && !prefs.unlockGatedFeatures) return

        val rawQueryMethods = attempt("find rawQuery methods", silent = true) {
            SQLiteDatabase::class.java.declaredMethods.filter {
                it.name == "rawQueryWithFactory" || it.name == "rawQuery"
            }
        } ?: emptyList()

        if (rawQueryMethods.isEmpty()) {
            Log.w(Consts.TAG, "HostFeedNavigationHook: no rawQuery methods found")
        }

        rawQueryMethods.forEachIndexed { index, method ->
            val sqlIndex = method.parameterTypes.indexOfFirst { it == String::class.java }
            if (sqlIndex < 0) return@forEachIndexed

            module.hookTracked(method, idPrefix = "feed-nav-rawquery-$index", deoptimize = true)
                .intercept { chain ->
                    if (FeedNavigator.isInternalQuery.get() == true) {
                        return@intercept chain.proceed()
                    }
                    if (isReentrant.get() == true) {
                        return@intercept chain.proceed()
                    }

                    isReentrant.set(true)
                    try {
                        val db = chain.thisObject as? SQLiteDatabase
                        if (db != null && isHingeDatabase(db)) {
                            FeedNavigator.setDbReference(db)
                            if (prefs.unlockGatedFeatures) {
                                attempt("clear hidden likes profiles", silent = true) {
                                    db.delete("sorted_hidden_likes_profiles", null, null)
                                }
                            }
                        }

                        val sql = chain.args.getOrNull(sqlIndex) as? String
                        var modifiedSql = sql
                        if (sql != null) {
                            if (prefs.enableFeedNavigation && isTargetQuery(sql)) {
                                val offset = FeedNavigator.currentOffset
                                if (offset > 0) {
                                    val modified = injectOffset(sql, offset)
                                    if (modified != sql) {
                                        modifiedSql = modified
                                        Log.i(Consts.TAG, "FeedNav: OFFSET $offset injected into discover query (${method.name})")
                                    }
                                }
                            } else if (prefs.unlockGatedFeatures && isLikesYouSortQuery(sql)) {
                                modifiedSql = unlockLikesYouSort(sql)
                            } else if (prefs.unlockGatedFeatures && isDiscoverFilterQuery(sql)) {
                                modifiedSql = unlockDiscoverFilter(sql)
                            }
                        }

                        if (modifiedSql != null && modifiedSql != sql) {
                            val newArgs = chain.args.toTypedArray()
                            newArgs[sqlIndex] = modifiedSql
                            chain.proceed(newArgs)
                        } else {
                            chain.proceed()
                        }
                    } finally {
                        isReentrant.set(false)
                    }
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

        attempt<Unit>("hook Database_Impl for capture and query proxy", silent = true) {
            val dbImplClass = classLoader.loadClass("co.hinge.common.storage.db.Database_Impl")
            dbImplClass.declaredConstructors.forEachIndexed { i, ctor ->
                module.hookTracked(ctor, idPrefix = "feed-nav-dbimpl-ctor-$i", deoptimize = true)
                    .intercept { chain ->
                        val result = chain.proceed()
                        chain.thisObject?.let { FeedNavigator.setRoomDatabase(it) }
                        result
                    }
            }

            val queryMethods = (dbImplClass.methods + (dbImplClass.superclass?.methods ?: emptyArray())).filter {
                it.name == "query"
            }.distinct()

            queryMethods.forEachIndexed { i, m ->
                module.hookTracked(m, idPrefix = "feed-nav-dbimpl-query-$i", deoptimize = true)
                    .intercept { chain ->
                        chain.thisObject?.let { FeedNavigator.setRoomDatabase(it) }
                        if (FeedNavigator.isInternalQuery.get() == true) {
                            return@intercept chain.proceed()
                        }

                        val queryArg = chain.args.getOrNull(0)
                        if (queryArg is String) {
                            var modifiedSql = queryArg
                            if (prefs.enableFeedNavigation && isTargetQuery(queryArg)) {
                                val offset = FeedNavigator.currentOffset
                                if (offset > 0) {
                                    val modified = injectOffset(queryArg, offset)
                                    if (modified != queryArg) {
                                        modifiedSql = modified
                                        Log.i(Consts.TAG, "FeedNav: OFFSET $offset injected via RoomDatabase.query(String)")
                                    }
                                }
                            }
                            if (modifiedSql != queryArg) {
                                val newArgs = chain.args.toTypedArray()
                                newArgs[0] = modifiedSql
                                return@intercept chain.proceed(newArgs)
                            }
                        } else if (queryArg != null) {
                            val getSqlMethod = queryArg.javaClass.methods.firstOrNull {
                                it.returnType == String::class.java && it.parameterTypes.isEmpty()
                            }
                            val sql = getSqlMethod?.invoke(queryArg) as? String
                            if (sql != null && prefs.enableFeedNavigation && isTargetQuery(sql)) {
                                val offset = FeedNavigator.currentOffset
                                if (offset > 0) {
                                    val modifiedSql = injectOffset(sql, offset)
                                    if (modifiedSql != sql && queryArg.javaClass.interfaces.isNotEmpty()) {
                                        val proxy = java.lang.reflect.Proxy.newProxyInstance(
                                            queryArg.javaClass.classLoader,
                                            queryArg.javaClass.interfaces
                                        ) { _, proxyMethod, methodArgs ->
                                            if (proxyMethod.returnType == String::class.java && (methodArgs == null || methodArgs.isEmpty())) {
                                                modifiedSql
                                            } else {
                                                proxyMethod.invoke(queryArg, *(methodArgs ?: emptyArray()))
                                            }
                                        }
                                        Log.i(Consts.TAG, "FeedNav: OFFSET $offset injected via RoomDatabase.query proxy")
                                        val newArgs = chain.args.toTypedArray()
                                        newArgs[0] = proxy
                                        return@intercept chain.proceed(newArgs)
                                    }
                                }
                            }
                        }

                        chain.proceed()
                    }
            }

            val targetMethods = (dbImplClass.methods.filter {
                it.name == "init" || it.name == "clearAllTables"
            } + dbImplClass.declaredMethods.filter {
                it.parameterTypes.isEmpty() && it.returnType != Void.TYPE
            }).distinct()
            targetMethods.forEachIndexed { i, m ->
                module.hookTracked(m, idPrefix = "feed-nav-dbimpl-m-$i", deoptimize = false)
                    .intercept { chain ->
                        val result = chain.proceed()
                        chain.thisObject?.let { FeedNavigator.setRoomDatabase(it) }
                        result
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

        Log.i(Consts.TAG, "HostFeedNavigationHook installed on rawQuery + SQLiteStatement + SQLiteOpenHelper")
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

    fun isLikesYouSortQuery(sql: String): Boolean {
        return sql.contains("likes_you_sort", ignoreCase = true)
    }

    fun unlockLikesYouSort(sql: String): String {
        if (sql.contains("0 AS isLocked", ignoreCase = true)) {
            return sql
        }
        return if (sql.contains("SELECT * FROM likes_you_sort", ignoreCase = true)) {
            sql.replace(Regex("""SELECT\s+\*\s+FROM\s+likes_you_sort""", RegexOption.IGNORE_CASE), "SELECT id, displayName, 0 AS isLocked FROM likes_you_sort")
        } else if (sql.contains("isLocked", ignoreCase = true)) {
            sql.replace(Regex("""\bisLocked\b""", RegexOption.IGNORE_CASE), "0 AS isLocked")
        } else {
            sql
        }
    }

    fun isDiscoverFilterQuery(sql: String): Boolean {
        return sql.contains("discover_filter", ignoreCase = true)
    }

    fun unlockDiscoverFilter(sql: String): String {
        if (sql.contains("'' AS permission", ignoreCase = true)) {
            return sql
        }
        return if (sql.contains("SELECT * FROM discover_filter", ignoreCase = true)) {
            sql.replace(Regex("""SELECT\s+\*\s+FROM\s+discover_filter""", RegexOption.IGNORE_CASE), "SELECT id, filterId, pillType, '' AS permission FROM discover_filter")
        } else if (sql.contains("permission", ignoreCase = true)) {
            sql.replace(Regex("""\bpermission\b""", RegexOption.IGNORE_CASE), "'' AS permission")
        } else {
            sql
        }
    }
}
