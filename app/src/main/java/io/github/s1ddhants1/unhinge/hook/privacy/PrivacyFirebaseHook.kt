package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.*
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import java.lang.reflect.Method

object PrivacyFirebaseHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        // 1. Force collection flags off on FirebaseAnalytics
        module.hookFirst(classLoader, "com.google.firebase.analytics.FirebaseAnalytics",
            "setAnalyticsCollectionEnabled", "fa_coll") { chain ->
            if (!prefs.isEffective(prefs.blockFirebase)) return@hookFirst chain.proceed()
            val rt = chain.executable as Method
            if (rt.parameterTypes.size == 1 && rt.parameterTypes[0] == java.lang.Boolean.TYPE) {
                chain.proceed(args(false)); null
            } else {
                chain.proceed(args(java.lang.Boolean.FALSE)); null
            }
        }

        // 2. Drop event logging / user props / user id
        val dropNames = setOf(
            "logEvent", "setUserProperty", "setUserProperties", "setUserId",
            "resetAnalyticsData", "setSessionTimeoutDuration", "setMinimumSessionDuration",
            "setConsent", "setDefaultEventParameters", "setCurrentScreen"
        )
        module.hookAllDeclared(classLoader, "com.google.firebase.analytics.FirebaseAnalytics", "fa",
            { it.name in dropNames }) { chain, m ->
            if (!prefs.isEffective(prefs.blockFirebase)) return@hookAllDeclared chain.proceed()
            defaultFor(m.returnType)
        }

        // 3. Scion backend: AppMeasurementSdk.logEvent + measurement/data-collection kill
        module.hookFirst(classLoader, "com.google.android.gms.measurement.api.AppMeasurementSdk",
            "logEvent", "ams_log") { chain ->
            if (!prefs.isEffective(prefs.blockFirebase)) return@hookFirst chain.proceed()
            null
        }

        module.hookAllDeclared(classLoader, "com.google.android.gms.measurement.api.AppMeasurementSdk", "ams",
            { it.name == "setMeasurementEnabled" || it.name == "setDataCollectionEnabled" }) { chain, _ ->
            if (!prefs.isEffective(prefs.blockFirebase)) return@hookAllDeclared chain.proceed()
            val n = chain.executable as Method
            if (n.parameterTypes.isNotEmpty() && n.parameterTypes[0] == java.lang.Boolean.TYPE) {
                chain.proceed(args(false)); null
            } else {
                try { chain.proceed(args(java.lang.Boolean.FALSE)) } catch (_: Throwable) { }
                null
            }
        }

        module.hookFirst(classLoader, "com.google.android.gms.measurement.api.AppMeasurementSdk",
            "getAppInstanceId", "ams_iid") { chain ->
            if (!prefs.isEffective(prefs.blockFirebase)) return@hookFirst chain.proceed()
            "" // empty string: avoids NPE in callers while suppressing identity
        }

        module.hookAllDeclared(classLoader, "com.google.android.gms.measurement.api.AppMeasurementSdk", "ams2",
            { it.name == "beginAdUnitExposure" || it.name == "endAdUnitExposure" || it.name == "setCurrentScreen" }) { chain, m ->
            if (!prefs.isEffective(prefs.blockFirebase)) return@hookAllDeclared chain.proceed()
            defaultFor(m.returnType)
        }
    }
}
