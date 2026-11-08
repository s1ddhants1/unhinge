package io.github.s1ddhants1.unhinge.hook.ui

import android.content.Context
import android.util.Log
import android.view.WindowManager
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.hook.hookTracked
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.attempt

object HostGatedFeaturesHook : HookHandler {

    private const val UNLIMITED_QUOTA = 999

    val UNLOCKED_PERMISSIONS = setOf(
        "undo_skip_replenish_unlimited",
        "all_incoming_likes_view",
        "sort_incoming_likes",
        "filters_plus",
        "preference_premium_edit",
        "preference_standard_edit",
        "preference_custom_boundaries_edit",
        "relocation_replenish_unlimited",
        "snooze_extra_replenish",
        "prompt_feedback_receive",
        "standouts_extra_show"
    )

    private val GATED_INT_KEYS = setOf(
        "localAvailableSkipUndos",
        "apiAvailableSkipUndos",
        "YOUR_TURN_MATCH_LIMIT"
    )

    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        if (prefs.unlockGatedFeatures) {
            installPreferencesHooks(module)
        }
        if (prefs.allowScreenshots) {
            installScreenshotHooks(module)
        }
    }

    private fun installPreferencesHooks(module: XposedModule) {
        attempt("hook SharedPreferencesImpl for gated features", silent = false) {
            val spClass = Class.forName("android.app.SharedPreferencesImpl")

            val getIntMethod = spClass.getDeclaredMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            module.hookTracked(getIntMethod, idPrefix = "gated-sp-get-int", deoptimize = true)
                .intercept { chain ->
                    val key = chain.args.getOrNull(0) as? String
                    if (isGatedKey(key)) {
                        return@intercept UNLIMITED_QUOTA
                    }
                    chain.proceed()
                }

            val containsMethod = spClass.getDeclaredMethod("contains", String::class.java)
            module.hookTracked(containsMethod, idPrefix = "gated-sp-contains", deoptimize = true)
                .intercept { chain ->
                    val key = chain.args.getOrNull(0) as? String
                    if (isGatedKey(key)) {
                        return@intercept true
                    }
                    chain.proceed()
                }

            attempt("hook SharedPreferencesImpl.getLong", silent = true) {
                val getLongMethod = spClass.getDeclaredMethod("getLong", String::class.java, Long::class.javaPrimitiveType)
                module.hookTracked(getLongMethod, idPrefix = "gated-sp-get-long", deoptimize = true)
                    .intercept { chain ->
                        val key = chain.args.getOrNull(0) as? String
                        if (isGatedKey(key)) {
                            return@intercept UNLIMITED_QUOTA.toLong()
                        }
                        chain.proceed()
                    }
            }

            attempt("hook SharedPreferencesImpl.getAll", silent = true) {
                val getAllMethod = spClass.getDeclaredMethod("getAll")
                module.hookTracked(getAllMethod, idPrefix = "gated-sp-get-all", deoptimize = false)
                    .intercept { chain ->
                        val result = chain.proceed()
                        if (result is Map<*, *>) {
                            val map = HashMap<Any?, Any?>(result)
                            map["localAvailableSkipUndos"] = UNLIMITED_QUOTA
                            map["apiAvailableSkipUndos"] = UNLIMITED_QUOTA
                            map["YOUR_TURN_MATCH_LIMIT"] = UNLIMITED_QUOTA
                            return@intercept map
                        }
                        result
                    }
            }

            attempt("hook SharedPreferencesImpl.getStringSet", silent = true) {
                val getStringSetMethod = spClass.getDeclaredMethod("getStringSet", String::class.java, Set::class.java)
                module.hookTracked(getStringSetMethod, idPrefix = "gated-sp-get-stringset", deoptimize = true)
                    .intercept { chain ->
                        val result = chain.proceed()
                        val key = chain.args.getOrNull(0) as? String
                        if (key == "USER_PERMISSIONS") {
                            val set = (result as? Set<*>)?.filterIsInstance<String>()?.toMutableSet() ?: mutableSetOf()
                            set.addAll(UNLOCKED_PERMISSIONS)
                            return@intercept set
                        }
                        result
                    }
            }

            Log.i(Consts.TAG, "HostGatedFeaturesHook: installed SharedPreferences hooks")
        }
    }

    private fun installScreenshotHooks(module: XposedModule) {
        attempt("hook Window for screenshot protection bypass", silent = true) {
            val windowClass = Class.forName("android.view.Window")
            val flagSecure = WindowManager.LayoutParams.FLAG_SECURE

            val setFlagsMethod = windowClass.getDeclaredMethod("setFlags", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            module.hookTracked(setFlagsMethod, idPrefix = "gated-window-set-flags", deoptimize = true)
                .intercept { chain ->
                    val flags = chain.args.getOrNull(0) as? Int ?: 0
                    val mask = chain.args.getOrNull(1) as? Int ?: 0
                    if ((mask and flagSecure) != 0) {
                        chain.args[0] = flags and flagSecure.inv()
                        chain.args[1] = mask and flagSecure.inv()
                    }
                    chain.proceed()
                }

            val addFlagsMethod = windowClass.getDeclaredMethod("addFlags", Int::class.javaPrimitiveType)
            module.hookTracked(addFlagsMethod, idPrefix = "gated-window-add-flags", deoptimize = true)
                .intercept { chain ->
                    val flags = chain.args.getOrNull(0) as? Int ?: 0
                    if ((flags and flagSecure) != 0) {
                        chain.args[0] = flags and flagSecure.inv()
                    }
                    chain.proceed()
                }

            Log.i(Consts.TAG, "HostGatedFeaturesHook: installed Window screenshot hooks")
        }
    }

    fun isGatedKey(key: String?): Boolean {
        return key in GATED_INT_KEYS
    }
}

