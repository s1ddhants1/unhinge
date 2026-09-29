package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.hook.hookAllDeclared
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import java.lang.reflect.Modifier

object PrivacyDataTransportHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        // Acknowledge CctTransportBackend with OK while dropping telemetry upload
        module.hookAllDeclared(classLoader, "com.google.android.datatransport.cct.CctTransportBackend", "cct",
            { it.name == "send" }) { chain, _ ->
            if (!prefs.isEffective(prefs.blockDataTransport)) return@hookAllDeclared chain.proceed()
            backendOk(classLoader) ?: chain.proceed()
        }
    }

    private fun backendOk(cl: ClassLoader): Any? {
        return try {
            val br = Class.forName("com.google.android.datatransport.runtime.BackendResponse", false, cl)
            try {
                br.getMethod("ok").invoke(null)
            } catch (_: NoSuchMethodException) {
                br.declaredMethods
                    .firstOrNull {
                        Modifier.isStatic(it.modifiers) && it.parameterTypes.isEmpty() && it.returnType == br
                    }?.also { it.isAccessible = true }?.invoke(null)
            }
        } catch (_: Throwable) { null }
    }
}
