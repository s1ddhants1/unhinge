package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.Context
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.hook.hookAllDeclared
import io.github.s1ddhants1.unhinge.util.PreferencesManager

object PrivacyContactsHook : HookHandler {
    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        module.hookAllDeclared(classLoader, "android.content.ContentResolver", "contacts",
            { it.name == "query" }) { chain, _ ->
            if (!prefs.isEffective(prefs.blockContacts)) return@hookAllDeclared chain.proceed()
            val args = chain.getArgs()
            val uri = args.getOrNull(0)?.toString() ?: ""
            if (uri.contains("contacts") || uri.contains("com.android.contacts")) {
                emptyCursor(classLoader) ?: chain.proceed()
            } else chain.proceed()
        }
    }

    private fun emptyCursor(cl: ClassLoader): Any? {
        return try {
            val mc = Class.forName("android.database.MatrixCursor", false, cl)
            mc.getConstructor(Array<String>::class.java)
                .newInstance(arrayOf("_id", "display_name"))
        } catch (_: Throwable) { null }
    }
}
