package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.hook.*
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.attempt
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
            val diagSync = prefs.unlockActiveFilters && isFilterDiagnosticRequest(chain.thisObject)
            if (diagSync) logFilterDiagnosticRequest(chain.thisObject, "sync")
            if (!prefs.isEffective(prefs.blockOkHttpTelemetry)) {
                if (diagSync) return@hookFirst logFilterDiagnosticProceed(chain, "sync")
                return@hookFirst chain.proceed()
            }
            val host = requestHost(chain.thisObject)
            if (host != null && isTelemetryHost(host)) {
                Log.d(Consts.TAG, "Blocked sync telemetry: $host")
                throw IOException("Unhinge: telemetry host dropped ($host)")
            }
            if (diagSync) return@hookFirst logFilterDiagnosticProceed(chain, "sync")
            chain.proceed()
        }

        module.hookFirst(classLoader, cn, "enqueue", "http_async") { chain ->
            val diagAsync = prefs.unlockActiveFilters && isFilterDiagnosticRequest(chain.thisObject)
            if (diagAsync) logFilterDiagnosticRequest(chain.thisObject, "async")
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

    // Opt-in diagnostics for the Active Today / Active Now unlock (gated on
    // unlock_active_filters): logs first-party filter/Discover request lines
    // plus sync response codes so logcat shows whether the server honors the
    // unlocked filter or silently serves unfiltered results. Never logs headers.
    private fun isFilterDiagnosticRequest(realCall: Any?): Boolean {
        return try {
            val req = getRequest(realCall) ?: return false
            val url = req.javaClass.getMethod("url").invoke(req)?.toString() ?: return false
            val u = url.lowercase()
            if (!u.contains("hinge")) return false
            u.contains("discover") || u.contains("filter") || u.contains("prefer") ||
                u.contains("active") || u.contains("choices") || u.contains("feed")
        } catch (_: Throwable) {
            false
        }
    }

    private fun logFilterDiagnosticRequest(realCall: Any?, kind: String) {
        attempt("log filter diagnostic request", silent = true) {
            val req = getRequest(realCall) ?: return@attempt
            val method = attempt("read request method", silent = true) {
                req.javaClass.getMethod("method").invoke(req) as? String
            } ?: "?"
            val url = attempt("read request url", silent = true) {
                req.javaClass.getMethod("url").invoke(req)?.toString()
            } ?: "?"
            val body = requestBodySnippet(req)
            Log.i(Consts.TAG, "ActiveFilterDiag: $kind $method $url${if (body != null) " body=$body" else ""}")
        }
    }

    private fun logFilterDiagnosticProceed(chain: io.github.libxposed.api.XposedInterface.Chain, kind: String): Any? {
        return try {
            val resp = chain.proceed()
            attempt("log filter diagnostic response", silent = true) {
                val code = resp?.javaClass?.getMethod("code")?.invoke(resp)
                Log.i(Consts.TAG, "ActiveFilterDiag: $kind response code=$code")
            }
            resp
        } catch (e: IOException) {
            Log.i(Consts.TAG, "ActiveFilterDiag: $kind request failed: ${e.message}")
            throw e
        } catch (t: Throwable) {
            Log.i(Consts.TAG, "ActiveFilterDiag: $kind request error: ${t.message}")
            throw t
        }
    }

    private fun requestBodySnippet(req: Any?): String? {
        return try {
            val body = req?.javaClass?.getMethod("body")?.invoke(req) ?: return null
            val bufferClass = Class.forName("okio.Buffer")
            val buffer = bufferClass.getDeclaredConstructor().newInstance()
            body.javaClass.getMethod("writeTo", bufferClass).invoke(body, buffer)
            var text = bufferClass.getMethod("readUtf8").invoke(buffer) as? String ?: return null
            text = text.replace(
                Regex("(?i)\"(password|token|auth[A-Za-z]*|secret|session|pushToken)\"\\s*:\\s*\"[^\"]*\""),
                "\"$1\":\"[redacted]\""
            )
            if (text.length > 1500) text.take(1500) + "…[truncated]" else text
        } catch (_: Throwable) {
            null
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
