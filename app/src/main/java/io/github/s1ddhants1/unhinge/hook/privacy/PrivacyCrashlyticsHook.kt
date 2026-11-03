package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.*
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import java.lang.reflect.Method

object PrivacyCrashlyticsHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        val cn = "com.google.firebase.crashlytics.FirebaseCrashlytics"
        module.hookAllDeclared(classLoader, cn, "cl",
            { it.name == "setCrashlyticsCollectionEnabled" }) { chain, _ ->
            if (!prefs.isEffective(prefs.blockCrashUpload)) return@hookAllDeclared chain.proceed()
            val p = (chain.executable as Method).parameterTypes
            if (p.size == 1 && p[0] == java.lang.Boolean.TYPE) chain.proceed(args(false))
            else try { chain.proceed(args(java.lang.Boolean.FALSE)) } catch (_: Throwable) { }
            null
        }

        val drop = setOf(
            "log", "recordException", "setCustomKey", "setCustomKeys",
            "setUserId", "sendUnsentReports", "deleteUnsentReports"
        )
        module.hookAllDeclared(classLoader, cn, "cl2", { it.name in drop }) { chain, m ->
            if (!prefs.isEffective(prefs.blockCrashUpload)) return@hookAllDeclared chain.proceed()
            if (m.returnType.name == "com.google.android.gms.tasks.Task") {
                completedTask(classLoader, false) ?: defaultFor(m.returnType)
            } else defaultFor(m.returnType)
        }

        module.hookFirst(classLoader, cn, "checkForUnsentReports", "cl_check") { chain ->
            if (!prefs.isEffective(prefs.blockCrashUpload)) return@hookFirst chain.proceed()
            completedTask(classLoader, false) ?: chain.proceed().also {
                tryDeleteUnsent(classLoader)
            }
        }
    }

    private fun completedTask(cl: ClassLoader, value: Boolean): Any? {
        return try {
            val tasks = Class.forName("com.google.android.gms.tasks.Tasks", false, cl)
            tasks.getMethod("forResult", Any::class.java).invoke(null, value)
        } catch (_: Throwable) { null }
    }

    private fun tryDeleteUnsent(cl: ClassLoader) {
        try {
            val clazz = Class.forName("com.google.firebase.crashlytics.FirebaseCrashlytics", false, cl)
            val inst = clazz.getMethod("getInstance").invoke(null)
            clazz.getMethod("deleteUnsentReports").invoke(inst)
        } catch (_: Throwable) { }
    }
}
