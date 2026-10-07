package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.hook.hookAllDeclared
import io.github.s1ddhants1.unhinge.util.PreferencesManager

object PrivacyMetricWorkersHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        for (cn in arrayOf(
            "co.hinge.metrics.impl.jobs.SendMetricWork",
            "co.hinge.metrics.impl.jobs.SendUnauthenticatedMetricWork"
        )) {
            module.hookAllDeclared(classLoader, cn, "work", { it.name == "d" || it.name == "doWork" }) { chain, m ->
                if (!prefs.isEffective(prefs.blockMetricWorkers)) return@hookAllDeclared chain.proceed()

                if (m.parameterTypes.any { it.name.contains("Continuation") }) {
                    return@hookAllDeclared chain.proceed()
                }
                workerSuccess(classLoader) ?: chain.proceed()
            }
        }

        module.hookAllDeclared(classLoader, "co.hinge.telemetry.token.TelemetryTokenStore", "tstore",
            { it.returnType == String::class.java }) { chain, _ ->
            if (!prefs.isEffective(prefs.blockMetricWorkers)) return@hookAllDeclared chain.proceed()
            ""
        }
    }

    private fun workerSuccess(cl: ClassLoader): Any? {
        return try {
            val res = Class.forName("androidx.work.ListenableWorker\$Result", false, cl)
            res.getMethod("success").invoke(null)
        } catch (_: Throwable) { null }
    }
}
