package io.github.s1ddhants1.unhinge.hook

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.util.PreferencesManager

interface HookHandler {
    fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    )
}
