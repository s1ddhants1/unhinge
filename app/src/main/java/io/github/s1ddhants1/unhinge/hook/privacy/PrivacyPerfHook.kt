package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.*
import io.github.s1ddhants1.unhinge.util.PreferencesManager

object PrivacyPerfHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        // 1. Firebase Perf Trace
        val cn = "com.google.firebase.perf.metrics.Trace"
        val drop = setOf(
            "start", "stop", "putAttribute", "putMetric",
            "incrementMetric", "removeAttribute"
        )
        module.hookAllDeclared(classLoader, cn, "perf", { it.name in drop }) { chain, m ->
            if (!prefs.isEffective(prefs.blockPerf)) return@hookAllDeclared chain.proceed()
            defaultFor(m.returnType)
        }

        // 2. AppStartTrace
        module.hookAllDeclared(classLoader, "com.google.firebase.perf.metrics.AppStartTrace", "ast",
            { it.name != "getInstance" && it.name !in OBJECT_METHODS }) { chain, m ->
            if (!prefs.isEffective(prefs.blockPerf)) return@hookAllDeclared chain.proceed()
            val n = m.name
            if (m.returnType == Void.TYPE || n.startsWith("on") || n.startsWith("register") ||
                n.startsWith("unregister") || n.startsWith("log") || n.startsWith("set") ||
                n.startsWith("put") || n.startsWith("increment") || n.startsWith("mark")
            ) defaultFor(m.returnType) else chain.proceed()
        }
    }
}
