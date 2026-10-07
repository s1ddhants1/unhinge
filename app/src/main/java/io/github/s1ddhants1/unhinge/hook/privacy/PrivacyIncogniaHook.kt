package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.*
import io.github.s1ddhants1.unhinge.util.PreferencesManager

object PrivacyIncogniaHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        val cn = "com.incognia.Incognia"
        module.hookAllDeclared(classLoader, cn, "inc", { it.name !in OBJECT_METHODS }) { chain, m ->
            if (!prefs.isEffective(prefs.blockIncognia)) return@hookAllDeclared chain.proceed()
            when (m.name) {
                "init", "disable", "notifyAppInForeground", "sendCustomEvent",
                "sendLoginEvent", "sendOnboardingEvent", "sendPaymentEvent",
                "clearAccountId", "setAccountId" -> defaultFor(m.returnType)
                "setLocationEnabled" -> {
                    val p = m.parameterTypes
                    return@hookAllDeclared if (p.size == 1 && p[0] == java.lang.Boolean.TYPE) {
                        chain.proceed(args(false))
                    } else {
                        chain.proceed()
                    }
                }
                "generateRequestTokenSync" -> ""
                "generateRequestToken", "generateRequestTokenWithStatus",
                "generateRequestTokenSyncWithStatus", "requestToken" -> defaultFor(m.returnType)
                else -> {
                    if (m.returnType == Void.TYPE) defaultFor(m.returnType) else chain.proceed()
                }
            }
        }
    }
}
