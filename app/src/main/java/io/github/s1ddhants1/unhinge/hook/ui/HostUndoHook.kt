package io.github.s1ddhants1.unhinge.hook.ui

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.hook.hookTracked
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.attempt

object HostUndoHook : HookHandler {

    private const val UNLIMITED_UNDOS = 999

    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        if (!prefs.enableFeedNavigation) return

        attempt("hook SharedPreferencesImpl for unlimited undos", silent = false) {
            val spClass = Class.forName("android.app.SharedPreferencesImpl")

            val getIntMethod = spClass.getDeclaredMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            module.hookTracked(getIntMethod, idPrefix = "undo-sp-get-int", deoptimize = true)
                .intercept { chain ->
                    val key = chain.args.getOrNull(0) as? String
                    if (isUndoKey(key)) {
                        return@intercept UNLIMITED_UNDOS
                    }
                    chain.proceed()
                }

            val containsMethod = spClass.getDeclaredMethod("contains", String::class.java)
            module.hookTracked(containsMethod, idPrefix = "undo-sp-contains", deoptimize = true)
                .intercept { chain ->
                    val key = chain.args.getOrNull(0) as? String
                    if (isUndoKey(key)) {
                        return@intercept true
                    }
                    chain.proceed()
                }

            attempt("hook SharedPreferencesImpl.getLong", silent = true) {
                val getLongMethod = spClass.getDeclaredMethod("getLong", String::class.java, Long::class.javaPrimitiveType)
                module.hookTracked(getLongMethod, idPrefix = "undo-sp-get-long", deoptimize = true)
                    .intercept { chain ->
                        val key = chain.args.getOrNull(0) as? String
                        if (isUndoKey(key)) {
                            return@intercept UNLIMITED_UNDOS.toLong()
                        }
                        chain.proceed()
                    }
            }

            attempt("hook SharedPreferencesImpl.getAll", silent = true) {
                val getAllMethod = spClass.getDeclaredMethod("getAll")
                module.hookTracked(getAllMethod, idPrefix = "undo-sp-get-all", deoptimize = false)
                    .intercept { chain ->
                        val result = chain.proceed()
                        if (result is Map<*, *>) {
                            val map = HashMap<Any?, Any?>(result)
                            map["localAvailableSkipUndos"] = UNLIMITED_UNDOS
                            map["apiAvailableSkipUndos"] = UNLIMITED_UNDOS
                            return@intercept map
                        }
                        result
                    }
            }

            Log.i(Consts.TAG, "HostUndoHook: successfully installed SharedPreferences hooks for unlimited undos")
        }
    }

    fun isUndoKey(key: String?): Boolean {
        return key == "localAvailableSkipUndos" || key == "apiAvailableSkipUndos"
    }
}
