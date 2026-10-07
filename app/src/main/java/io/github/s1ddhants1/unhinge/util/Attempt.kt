package io.github.s1ddhants1.unhinge.util

import android.util.Log
import io.github.s1ddhants1.unhinge.Consts

inline fun <T> attempt(operation: String, silent: Boolean = false, block: () -> T): T? = try {
    block()
} catch (t: Throwable) {
    if (!silent) Log.w(Consts.TAG, "Failed $operation: ${t.message}")
    null
}

fun loadClassFlexible(cl: ClassLoader, name: String): Class<*>? {
    val clean = name.removePrefix("defpackage.")
    return attempt("load $clean", silent = true) { cl.loadClass(clean) }
        ?: attempt("load defpackage.$clean", silent = true) { cl.loadClass("defpackage.$clean") }
        ?: attempt("load $name", silent = true) { cl.loadClass(name) }
}
