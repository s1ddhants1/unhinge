package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.hook.hookFirst
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import kotlin.math.round

object PrivacyLocationHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        for (m in arrayOf("getLatitude", "getLongitude")) {
            module.hookFirst(classLoader, "android.location.Location", m, "loc_$m") { chain ->
                if (!prefs.isEffective(prefs.fuzzLocation)) return@hookFirst chain.proceed()
                val raw = chain.proceed()
                val v = raw as? Double ?: return@hookFirst raw

                round(v * 100.0) / 100.0
            }
        }
    }
}
