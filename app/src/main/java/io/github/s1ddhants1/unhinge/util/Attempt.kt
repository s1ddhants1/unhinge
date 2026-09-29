package io.github.s1ddhants1.unhinge.util

import android.util.Log
import io.github.s1ddhants1.unhinge.Consts

/** Silent-safe execution helper (SwiftBackupPrem `attempt` pattern, framework-only). */
inline fun <T> attempt(operation: String, silent: Boolean = false, block: () -> T): T? = try {
    block()
} catch (t: Throwable) {
    if (!silent) Log.w(Consts.TAG, "Failed $operation: ${t.message}")
    null
}

inline fun <T> attemptOrDefault(operation: String, default: T, silent: Boolean = false, block: () -> T): T =
    attempt(operation, silent, block) ?: default

/** Load a class, tolerating R8 `defpackage.` renames. */
fun loadClassFlexible(cl: ClassLoader, name: String): Class<*>? {
    val clean = name.removePrefix("defpackage.")
    return attempt("load $clean", silent = true) { cl.loadClass(clean) }
        ?: attempt("load defpackage.$clean", silent = true) { cl.loadClass("defpackage.$clean") }
        ?: attempt("load $name", silent = true) { cl.loadClass(name) }
}
