package io.github.s1ddhants1.unhinge.hook.ui

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.hook.hookTracked
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.attempt

object HostActiveFilterHook : HookHandler {

    // Verified on-device (co.hinge.app v10.4.0, databases/db): the server-synced
    // `discover_filter` table gates premium filters per row via its `permission`
    // column. Free rows carry an empty permission; premium rows
    // (active_today, new_here, filter_circle_members — and active_now when the
    // server sends it) all require "filters_plus". The free account's
    // USER_PERMISSIONS set lacks it, so injecting it unlocks every premium
    // filter at once, including Active Today and Active Now.
    val ACTIVE_FILTER_PERMISSIONS = setOf("filters_plus")

    private val ACTIVE_FILTER_KEY_REGEX = Regex(
        "(?i).*active.*(now|today).*|(?i).*(now|today).*active.*"
    )

    private val ENTITLEMENT_QUALIFIER_REGEX = Regex(
        "(?i).*(filter|permission|entitl|unlock|premium|hinge|available|grant|access|allow|eligib|enabl|plus).*"
    )

    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        // Install unconditionally: at Phase 1 the backup prefs are not wired yet
        // (ensureBackupPrefs runs in Phase 2), so an install-time gate would read
        // a stale false. Each interceptor re-checks the toggle dynamically instead.
        attempt("hook SharedPreferencesImpl for active filters", silent = false) {
            val spClass = Class.forName("android.app.SharedPreferencesImpl")

            val getStringSetMethod = spClass.getDeclaredMethod(
                "getStringSet", String::class.java, Set::class.java
            )
            module.hookTracked(getStringSetMethod, idPrefix = "activefilter-sp-getstringset", deoptimize = true)
                .intercept { chain ->
                    val result = chain.proceed()
                    if (!prefs.unlockActiveFilters) return@intercept result
                    val key = chain.args.getOrNull(0) as? String
                    if (key == "USER_PERMISSIONS") {
                        val set = (result as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: mutableSetOf()
                        if (set.addAll(ACTIVE_FILTER_PERMISSIONS)) {
                            Log.i(Consts.TAG, "HostActiveFilterHook: injected ${ACTIVE_FILTER_PERMISSIONS.size} active filter permissions")
                        }
                        return@intercept set
                    }
                    result
                }

            val getBooleanMethod = spClass.getDeclaredMethod(
                "getBoolean", String::class.java, java.lang.Boolean.TYPE
            )
            module.hookTracked(getBooleanMethod, idPrefix = "activefilter-sp-get-boolean", deoptimize = true)
                .intercept { chain ->
                    val key = chain.args.getOrNull(0) as? String
                    if (prefs.unlockActiveFilters && isActiveFilterKey(key)) {
                        return@intercept true
                    }
                    chain.proceed()
                }

            val containsMethod = spClass.getDeclaredMethod("contains", String::class.java)
            module.hookTracked(containsMethod, idPrefix = "activefilter-sp-contains", deoptimize = true)
                .intercept { chain ->
                    val key = chain.args.getOrNull(0) as? String
                    if (prefs.unlockActiveFilters && isActiveFilterKey(key)) {
                        return@intercept true
                    }
                    chain.proceed()
                }

            attempt("hook SharedPreferencesImpl.getInt for active filters", silent = true) {
                val getIntMethod = spClass.getDeclaredMethod(
                    "getInt", String::class.java, Int::class.javaPrimitiveType
                )
                module.hookTracked(getIntMethod, idPrefix = "activefilter-sp-get-int", deoptimize = true)
                    .intercept { chain ->
                        val key = chain.args.getOrNull(0) as? String
                        if (prefs.unlockActiveFilters && isActiveFilterKey(key)) {
                            return@intercept 1
                        }
                        chain.proceed()
                    }
            }

            attempt("hook SharedPreferencesImpl.getAll for active filters", silent = true) {
                val getAllMethod = spClass.getDeclaredMethod("getAll")
                module.hookTracked(getAllMethod, idPrefix = "activefilter-sp-get-all", deoptimize = false)
                    .intercept { chain ->
                        val result = chain.proceed()
                        if (result is Map<*, *> && prefs.unlockActiveFilters) {
                            val map = HashMap<Any?, Any?>(result)
                            val perms = (map["USER_PERMISSIONS"] as? Set<*>)?.filterIsInstance<String>()?.toMutableSet()
                                ?: mutableSetOf()
                            perms.addAll(ACTIVE_FILTER_PERMISSIONS)
                            map["USER_PERMISSIONS"] = perms
                            for ((k, _) in result) {
                                val key = k as? String ?: continue
                                if (isActiveFilterKey(key) && map[key] is Boolean) {
                                    map[key] = true
                                }
                            }
                            return@intercept map
                        }
                        result
                    }
            }

            Log.i(Consts.TAG, "HostActiveFilterHook: installed active today/now filter unlock hooks")
        }
    }

    fun isActiveFilterKey(key: String?): Boolean {
        if (key.isNullOrBlank()) return false
        if (ACTIVE_FILTER_PERMISSIONS.contains(key)) return true
        if (key == "USER_PERMISSIONS") return false
        return ACTIVE_FILTER_KEY_REGEX.matches(key) && ENTITLEMENT_QUALIFIER_REGEX.matches(key)
    }

    fun ensureActiveFiltersUnlocked(context: Context) {
        attempt("ensure active filters in default prefs", silent = true) {
            val prefs = context.getSharedPreferences("default", Context.MODE_PRIVATE)
            val current = prefs.getStringSet("USER_PERMISSIONS", null) ?: emptySet()
            if (!current.containsAll(ACTIVE_FILTER_PERMISSIONS)) {
                val updated = current.toMutableSet().apply { addAll(ACTIVE_FILTER_PERMISSIONS) }
                prefs.edit().putStringSet("USER_PERMISSIONS", updated).apply()
                Log.i(Consts.TAG, "HostActiveFilterHook: granted active today/now filter permissions in default prefs")
            }
        }
    }
}
