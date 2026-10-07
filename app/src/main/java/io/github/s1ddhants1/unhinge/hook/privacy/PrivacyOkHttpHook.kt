package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.hook.*
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import java.io.IOException

object PrivacyOkHttpHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        val cn = "okhttp3.internal.connection.RealCall"

        module.hookFirst(
            classLoader, cn, "execute", "http_sync",
            exceptionMode = XposedInterface.ExceptionMode.PASSTHROUGH
        ) { chain ->
            if (isVirtualNavRequest(chain.thisObject)) {
                Log.w(Consts.TAG, "Blocked sync virtual navigation request")
                throw IOException("Unhinge: virtual navigation request dropped")
            }
            if (!prefs.isEffective(prefs.blockOkHttpTelemetry)) return@hookFirst chain.proceed()
            val host = requestHost(chain.thisObject)
            if (host != null && isTelemetryHost(host)) {
                Log.d(Consts.TAG, "Blocked sync telemetry: $host")
                throw IOException("Unhinge: telemetry host dropped ($host)")
            }
            chain.proceed()
        }

        module.hookFirst(classLoader, cn, "enqueue", "http_async") { chain ->
            if (isVirtualNavRequest(chain.thisObject)) {
                Log.w(Consts.TAG, "Blocked async virtual navigation request")
                try {
                    val cb = chain.getArg(0)
                    val callIface = Class.forName("okhttp3.Call", false, chain.thisObject.javaClass.classLoader)
                    cb?.javaClass?.getMethod("onFailure", callIface, IOException::class.java)
                        ?.invoke(cb, chain.thisObject, IOException("Unhinge: virtual navigation request dropped"))
                } catch (t: Throwable) {
                    Log.w(Consts.TAG, "OkHttp enqueue callback fail: ${t.message}")
                }
                return@hookFirst null
            }
            if (!prefs.isEffective(prefs.blockOkHttpTelemetry)) return@hookFirst chain.proceed()
            val host = requestHost(chain.thisObject)
            if (host != null && isTelemetryHost(host)) {
                Log.d(Consts.TAG, "Blocked async telemetry: $host")
                try {
                    val cb = chain.getArg(0)
                    val callIface = Class.forName("okhttp3.Call", false, chain.thisObject.javaClass.classLoader)
                    cb?.javaClass?.getMethod("onFailure", callIface, IOException::class.java)
                        ?.invoke(cb, chain.thisObject, IOException("Unhinge: telemetry host dropped ($host)"))
                } catch (t: Throwable) {
                    Log.w(Consts.TAG, "OkHttp enqueue callback fail: ${t.message}")
                }
                return@hookFirst null
            }
            chain.proceed()
        }
    }

    private fun isTelemetryHost(host: String): Boolean {
        val h = host.lowercase()
        return Consts.TELEMETRY_HOST_SUBSTRINGS.any { h.contains(it) }
    }

    private fun isVirtualNavRequest(realCall: Any?): Boolean {
        if (!io.github.s1ddhants1.unhinge.hook.ui.FeedNavigator.isVirtualBrowsing &&
            !io.github.s1ddhants1.unhinge.hook.ui.FeedNavigator.isNavigated) return false
        if (realCall == null) return false
        return try {
            val req = getRequest(realCall) ?: return false
            val url = req.javaClass.getMethod("url").invoke(req)?.toString() ?: ""
            url.contains("/ratings", ignoreCase = true) ||
            url.contains("/rating", ignoreCase = true) ||
            url.contains("/undo", ignoreCase = true)
        } catch (_: Throwable) {
            false
        }
    }

    private fun getRequest(realCall: Any?): Any? {
        if (realCall == null) return null
        return try {
            realCall.javaClass.getMethod("request").invoke(realCall)
        } catch (_: Throwable) {
            try {
                val f = findField(realCall.javaClass, "originalRequest")
                f.isAccessible = true
                f.get(realCall)
            } catch (_: Throwable) { null }
        }
    }

    private fun requestHost(realCall: Any?): String? {
        if (realCall == null) return null
        return try {
            val req = realCall.javaClass.getMethod("request").invoke(realCall)
            val url = req.javaClass.getMethod("url").invoke(req)
            url.javaClass.getMethod("host").invoke(url) as? String
        } catch (_: Throwable) {
            try {
                val f = findField(realCall.javaClass, "originalRequest")
                f.isAccessible = true
                val req = f.get(realCall) ?: return null
                val url = req.javaClass.getMethod("url").invoke(req)
                url.javaClass.getMethod("host").invoke(url) as? String
            } catch (_: Throwable) { null }
        }
    }

    private fun findField(c: Class<*>, name: String): java.lang.reflect.Field {
        var cur: Class<*>? = c
        while (cur != null) {
            try { return cur.getDeclaredField(name) } catch (_: NoSuchFieldException) { cur = cur.superclass }
        }
        throw NoSuchFieldException(name)
    }
}
