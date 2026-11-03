package io.github.s1ddhants1.unhinge.hook

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.Module
import io.github.s1ddhants1.unhinge.util.attempt
import io.github.s1ddhants1.unhinge.util.loadClassFlexible
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.lang.reflect.Modifier

private val HOOK_ID_SANITIZER = Regex("[^A-Za-z0-9_.#-]")
private const val MAX_HOOK_ID_LEN = 128

val OBJECT_METHODS = setOf("toString", "hashCode", "equals", "wait", "notify", "notifyAll")

fun XposedModule.hookTracked(
    executable: Executable,
    idPrefix: String = "${executable.declaringClass.name}#${executable.name}",
    priority: Int = XposedInterface.PRIORITY_DEFAULT,
    deoptimize: Boolean = false,
    exceptionMode: XposedInterface.ExceptionMode = XposedInterface.ExceptionMode.PROTECTIVE
): XposedInterface.HookBuilder {
    if (deoptimize) {
        attempt("deoptimize ${executable.declaringClass.simpleName}#${executable.name}", silent = true) {
            deoptimize(executable)
        }
    }
    val params = executable.parameterTypes.joinToString(",") { it.name }
    val sig = "${executable.declaringClass.name}#${executable.name}($params)"
    var hookId = "${idPrefix.replace(HOOK_ID_SANITIZER, "_")}:${sig.hashCode().toUInt().toString(16)}"
    if (hookId.length > MAX_HOOK_ID_LEN) hookId = hookId.take(MAX_HOOK_ID_LEN)

    val builder = hook(executable)
        .setPriority(priority)
        .setExceptionMode(exceptionMode)
    if (apiVersion >= XposedInterface.API_102) builder.setId(hookId)
    val mod = this as? Module
    return object : XposedInterface.HookBuilder {
        override fun setPriority(priority: Int) = apply { builder.setPriority(priority) }
        override fun setExceptionMode(mode: XposedInterface.ExceptionMode) = apply { builder.setExceptionMode(mode) }
        override fun setId(id: String?) = apply {
            if (id != null) hookId = id
            if (apiVersion >= XposedInterface.API_102) builder.setId(id)
        }
        override fun intercept(hooker: XposedInterface.Hooker): XposedInterface.HookHandle =
            builder.intercept(hooker).also { mod?.rememberHook(hookId, it) }
    }
}

fun XposedModule.hookFirst(
    cl: ClassLoader,
    className: String,
    methodName: String,
    id: String,
    exceptionMode: XposedInterface.ExceptionMode = XposedInterface.ExceptionMode.PROTECTIVE,
    handler: (chain: XposedInterface.Chain) -> Any?
) {
    try {
        val clazz = loadClassFlexible(cl, className) ?: return
        val targets = clazz.declaredMethods.filter { it.name == methodName }
        if (targets.isEmpty()) return
        for (m in targets) {
            try {
                hookTracked(m, idPrefix = id, deoptimize = true, exceptionMode = exceptionMode)
                    .intercept { chain -> handler(chain) }
            } catch (t: Throwable) {
                Log.w(Consts.TAG, "hook failed $className#$methodName: ${t.message}")
            }
        }
    } catch (t: Throwable) {
        Log.w(Consts.TAG, "hookFirst $className#$methodName: ${t.message}")
    }
}

fun XposedModule.hookAllDeclared(
    cl: ClassLoader,
    className: String,
    idPrefix: String,
    exceptionMode: XposedInterface.ExceptionMode,
    predicate: (Method) -> Boolean,
    handler: (chain: XposedInterface.Chain, m: Method) -> Any?
) {
    try {
        val clazz = loadClassFlexible(cl, className) ?: return
        for (m in clazz.declaredMethods) {
            if (!predicate(m)) continue
            if (Modifier.isAbstract(m.modifiers)) continue
            try {
                hookTracked(m, idPrefix = "$idPrefix#${m.name}", deoptimize = true, exceptionMode = exceptionMode)
                    .intercept { chain -> handler(chain, m) }
            } catch (t: Throwable) {
                Log.w(Consts.TAG, "hook failed $className#${m.name}: ${t.message}")
            }
        }
    } catch (t: Throwable) {
        Log.w(Consts.TAG, "hookAllDeclared $className: ${t.message}")
    }
}

fun XposedModule.hookAllDeclared(
    cl: ClassLoader,
    className: String,
    idPrefix: String,
    predicate: (Method) -> Boolean,
    handler: (chain: XposedInterface.Chain, m: Method) -> Any?
) = hookAllDeclared(cl, className, idPrefix, XposedInterface.ExceptionMode.PROTECTIVE, predicate, handler)

fun defaultFor(returnType: Class<*>): Any? = when (returnType) {
    Void.TYPE -> null
    java.lang.Boolean.TYPE -> false
    java.lang.Integer.TYPE -> 0
    java.lang.Long.TYPE -> 0L
    java.lang.Double.TYPE -> 0.0
    java.lang.Float.TYPE -> 0f
    else -> null
}

fun args(vararg a: Any?): Array<Any?> = arrayOf(*a)

fun Any.getFieldValue(name: String): Any? = attempt("get field $name", silent = true) {
    javaClass.getDeclaredField(name).apply { isAccessible = true }.get(this)
}

inline fun <reified T> Any.getTypedFieldValue(name: String): T? = getFieldValue(name) as? T
