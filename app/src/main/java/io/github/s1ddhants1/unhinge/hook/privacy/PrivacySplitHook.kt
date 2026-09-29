package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.*
import io.github.s1ddhants1.unhinge.util.PreferencesManager

object PrivacySplitHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        // 1. Semantic SplitClient interface telemetry sync & flush methods
        module.hookAllDeclared(classLoader, "io.split.android.client.SplitClient", "split_client", {
            it.name == "flush" || it.name == "destroy"
        }) { chain, m ->
            if (!prefs.isEffective(prefs.blockSplitTelemetry)) return@hookAllDeclared chain.proceed()
            defaultFor(m.returnType)
        }

        // 2. Telemetry and impression persistence: ImpressionsObserver, EventsTracker, SplitRoomDatabase
        for (cn in arrayOf(
            "io.split.android.client.service.impressions.ImpressionsObserver",
            "io.split.android.client.service.events.EventsTracker",
            "io.split.android.client.storage.db.SplitRoomDatabase"
        )) {
            module.hookAllDeclared(classLoader, cn, "split_storage", {
                it.name.startsWith("track") || it.name.startsWith("log") || it.name.startsWith("save")
            }) { chain, m ->
                if (!prefs.isEffective(prefs.blockSplitTelemetry)) return@hookAllDeclared chain.proceed()
                defaultFor(m.returnType)
            }
        }
    }
}
