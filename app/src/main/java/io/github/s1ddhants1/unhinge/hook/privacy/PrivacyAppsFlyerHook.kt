package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.*
import io.github.s1ddhants1.unhinge.util.PreferencesManager

object PrivacyAppsFlyerHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        val cn = "com.appsflyer.AppsFlyerLib"
        val forceTrue = setOf(
            "setDisableAdvertisingIdentifiers", "setDisableNetworkData",
            "anonymizeUser", "setCollectAndroidId", "setCollectImei"
        )
        module.hookAllDeclared(classLoader, cn, "af", { it.name !in OBJECT_METHODS }) { chain, m ->
            if (!prefs.isEffective(prefs.blockAppsflyer)) return@hookAllDeclared chain.proceed()
            val n = m.name
            if (n == "getInstance") return@hookAllDeclared chain.proceed()
            if (n in forceTrue) {
                val p = m.parameterTypes
                return@hookAllDeclared if (p.size == 1 && p[0] == java.lang.Boolean.TYPE) {
                    chain.proceed(args(true)); defaultFor(m.returnType)
                } else chain.proceed()
            }
            if (n == "stop") {
                return@hookAllDeclared try {
                    val a = chain.getArgs()
                    if (a.isNotEmpty() && m.parameterTypes[0] == java.lang.Boolean.TYPE) {
                        val rest = a.drop(1).toTypedArray()
                        chain.proceed(args(true, *rest))
                    } else chain.proceed()
                    defaultFor(m.returnType)
                } catch (_: Throwable) { defaultFor(m.returnType) }
            }
            if (n == "init") return@hookAllDeclared chain.thisObject
            if (n == "getAppsFlyerUID") return@hookAllDeclared null
            defaultFor(m.returnType)
        }

        module.hookAllDeclared(classLoader, "com.appsflyer.internal.AFa1tSDK", "afi", { it.name !in OBJECT_METHODS }) { chain, m ->
            if (!prefs.isEffective(prefs.blockAppsflyer)) return@hookAllDeclared chain.proceed()
            val n = m.name
            if (n == "getMediationNetwork" && m.parameterTypes.isEmpty()) return@hookAllDeclared chain.proceed()
            if (n == "init") {
                val self = chain.thisObject
                return@hookAllDeclared if (self != null && m.returnType.isInstance(self)) self
                else defaultFor(m.returnType)
            }
            if (n in setOf("setDisableAdvertisingIdentifiers", "setDisableNetworkData", "anonymizeUser")) {
                return@hookAllDeclared if (m.parameterTypes.size == 1 && m.parameterTypes[0] == java.lang.Boolean.TYPE) {
                    chain.proceed(args(true)); defaultFor(m.returnType)
                } else chain.proceed()
            }
            if (m.returnType == Void.TYPE) defaultFor(m.returnType) else chain.proceed()
        }

        for (rc in arrayOf(
            "com.appsflyer.SingleInstallBroadcastReceiver",
            "com.appsflyer.MultipleInstallBroadcastReceiver"
        )) {
            module.hookFirst(classLoader, rc, "onReceive", "af_$rc") { chain ->
                if (!prefs.isEffective(prefs.blockAppsflyer)) return@hookFirst chain.proceed()
                null
            }
        }

        module.hookFirst(classLoader, "com.appsflyer.FirebaseMessagingServiceListener", "onNewToken", "af_tok") { chain ->
            if (!prefs.isEffective(prefs.blockAppsflyer)) return@hookFirst chain.proceed()
            null
        }
    }
}
