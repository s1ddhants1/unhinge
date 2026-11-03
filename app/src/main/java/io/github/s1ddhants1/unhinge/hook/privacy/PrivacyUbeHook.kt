package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.hook.*
import io.github.s1ddhants1.unhinge.util.PreferencesManager

object PrivacyUbeHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        val grpcClass = "com.squareup.wire.GrpcClient"
        module.hookAllDeclared(classLoader, grpcClass, "ube",
            { it.name == "newCall" || it.name == "newCall\$wire_grpc_client" }) { chain, _ ->
            if (!prefs.isEffective(prefs.blockUbe)) return@hookAllDeclared chain.proceed()
            val call = chain.proceed()
            try {
                val path = grpcPath(chain.getArgs().firstOrNull())
                if (path != null && (path.contains("/ube.") || path.contains("Analytics/PostUbeEvent"))) {
                    call?.javaClass?.getMethod("cancel")?.invoke(call)
                    Log.d(Consts.TAG, "UBE gRPC call cancelled: $path")
                }
            } catch (t: Throwable) {
                Log.w(Consts.TAG, "UBE cancel failed: ${t.message}")
            }
            call
        }
    }

    private fun grpcPath(methodArg: Any?): String? {
        if (methodArg == null) return null
        return try {
            methodArg.javaClass.getMethod("getPath").invoke(methodArg) as? String
        } catch (_: Throwable) {
            try {
                val f = methodArg.javaClass.getDeclaredField("path")
                f.isAccessible = true
                f.get(methodArg) as? String
            } catch (_: Throwable) { null }
        }
    }
}
