package io.github.s1ddhants1.unhinge

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.annotation.Keep
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import io.github.s1ddhants1.unhinge.hook.hookFirst
import io.github.s1ddhants1.unhinge.hook.hookTracked
import io.github.s1ddhants1.unhinge.hook.privacy.*
import io.github.s1ddhants1.unhinge.hook.ui.HostFeedNavigationHook
import io.github.s1ddhants1.unhinge.util.LSPatchHelper
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.attempt
import io.github.s1ddhants1.unhinge.util.loadClassFlexible
import java.util.concurrent.ConcurrentHashMap

@Keep
@SuppressLint("PrivateApi", "DiscouragedPrivateApi")
class Module : XposedModule() {
    private val hookHandles = ConcurrentHashMap<String, XposedInterface.HookHandle>()
    private var modulePrefs: PreferencesManager? = null

    fun rememberHook(id: String?, handle: XposedInterface.HookHandle) {
        if (id != null) hookHandles[id] = handle
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        super.onPackageReady(param)
        if (!param.isFirstPackage || param.packageName != Consts.TARGET_PACKAGE) {
            if (apiVersion >= XposedInterface.API_102) {
                attempt("detach non-target package", silent = true) { detach() }
            }
            return
        }

        val cl = param.classLoader
        val prefs = initPreferences(null)

        applyBytecodeHooks(cl, prefs)

        val appClass = loadClassFlexible(cl, "co.hinge.app.App")
        val onCreateMethod = attempt("find App.onCreate", silent = true) {
            appClass?.getDeclaredMethod("onCreate")
        }

        if (appClass == null || onCreateMethod == null) {
            Log.w(Consts.TAG, "App.onCreate not found; attempting early context resolution")
            val app = attempt("get current Application", silent = true) {
                val atClass = Class.forName("android.app.ActivityThread")
                atClass.getDeclaredMethod("currentApplication").invoke(null) as? Context
            }
            if (app != null) {
                applyContextHooks(app, cl, prefs)
            } else {
                hookAppActivityFallback(cl, prefs)
            }
            return
        }

        hookTracked(
            onCreateMethod,
            idPrefix = "hinge-app-on-create",
            priority = XposedInterface.PRIORITY_HIGHEST,
            deoptimize = true
        ).intercept { chain ->
            val ctx = chain.thisObject as? Context
            if (ctx != null) {
                applyContextHooks(ctx, cl, prefs)
            }
            chain.proceed().also {
                if (ctx != null) {
                    Log.d(Consts.TAG, "Unhinge hooks and overlay successfully active in ${ctx.packageName}")
                }
            }
        }
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        Log.i(Consts.TAG, "Preparing old module generation for hot reload...")
        val state = Bundle().apply {
            putLong("hot_reload_timestamp", System.currentTimeMillis())
        }
        param.setSavedInstanceState(state)

        modulePrefs?.unregister()
        modulePrefs = null
        PrivacyGmsComponentsHook.reset()
        hookHandles.clear()
        return true
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        Log.i(Consts.TAG, "Hot reloaded Unhinge module in process ${param.processName}!")

        for (oldHandle in param.oldHookHandles) {
            try {
                oldHandle.unhook()
            } catch (_: Throwable) {}
        }
        hookHandles.clear()

        val app = attempt("get current Application", silent = true) {
            val atClass = Class.forName("android.app.ActivityThread")
            atClass.getDeclaredMethod("currentApplication").invoke(null) as? android.app.Application
        }
        if (app != null && app.packageName == Consts.TARGET_PACKAGE) {
            val cl = app.classLoader
            val prefs = initPreferences(app)
            applyBytecodeHooks(cl, prefs)
            applyContextHooks(app, cl, prefs)
        }
    }

    private fun initPreferences(ctx: Context?): PreferencesManager {
        modulePrefs?.let {
            if (ctx != null) it.ensureBackupPrefs(ctx)
            return it
        }

        val remotePrefs = attempt("get remote preferences", silent = true) {
            getRemotePreferences(Consts.PREFS_SETTINGS)
        }
        val hasRemotePrefs = remotePrefs != null && remotePrefs.all.isNotEmpty()
        val isIntegrated = ctx?.let { LSPatchHelper.isIntegratedMode(it, remotePrefsAvailable = hasRemotePrefs) } ?: false
        Log.i(Consts.TAG, "initPreferences: isIntegrated=$isIntegrated, hasRemotePrefs=$hasRemotePrefs, remoteKeys=${remotePrefs?.all?.keys}")
        Log.i(Consts.TAG, "initPreferences: ai_provider=${remotePrefs?.getString(Consts.PREF_AI_PROVIDER, "<missing>")}, base_url=${remotePrefs?.getString(Consts.PREF_OPENROUTER_BASE_URL, "<missing>")}")

        val localPrefs = ctx?.getSharedPreferences(Consts.PREFS_SETTINGS, Context.MODE_PRIVATE)
        val prefs = PreferencesManager(remotePrefs, isDynamic = true, backupPrefs = localPrefs)
        if (ctx != null) {
            prefs.ensureBackupPrefs(ctx)
        }
        modulePrefs = prefs
        return prefs
    }

    private fun applyBytecodeHooks(cl: ClassLoader, prefs: PreferencesManager) {
        Log.i(Consts.TAG, "Installing Phase 1 early bytecode privacy hooks...")
        PrivacyFirebaseHook.apply(this, null, cl, prefs)
        PrivacyCrashlyticsHook.apply(this, null, cl, prefs)
        PrivacyPerfHook.apply(this, null, cl, prefs)
        PrivacyAppsFlyerHook.apply(this, null, cl, prefs)
        PrivacyIncogniaHook.apply(this, null, cl, prefs)
        PrivacySplitHook.apply(this, null, cl, prefs)
        PrivacyUbeHook.apply(this, null, cl, prefs)
        PrivacyOkHttpHook.apply(this, null, cl, prefs)
        PrivacyLocationHook.apply(this, null, cl, prefs)
        PrivacyContactsHook.apply(this, null, cl, prefs)
        PrivacyMetricWorkersHook.apply(this, null, cl, prefs)
        PrivacyDataTransportHook.apply(this, null, cl, prefs)

        HostFeedNavigationHook.apply(this, null, cl, prefs)
        io.github.s1ddhants1.unhinge.hook.ui.HostUndoHook.apply(this, null, cl, prefs)

        attempt<Unit>("hook AccessibilityManager.isEnabled", silent = true) {
            hookFirst(
                cl = cl,
                className = "android.view.accessibility.AccessibilityManager",
                methodName = "isEnabled",
                id = "a11y_is_enabled"
            ) {
                true
            }
        }
        Log.i(Consts.TAG, "Phase 1 early bytecode privacy hooks installed successfully")
    }

    private fun applyContextHooks(ctx: Context, cl: ClassLoader, prefs: PreferencesManager) {
        Log.i(Consts.TAG, "Installing Phase 2 context hooks and overlay...")
        prefs.ensureBackupPrefs(ctx)
        io.github.s1ddhants1.unhinge.hook.ui.HostAppAiFab.ensureAccessibilityEnabled(ctx)

        PrivacyGmsComponentsHook.apply(this, ctx, cl, prefs)

        val app = ctx as? android.app.Application ?: ctx.applicationContext as? android.app.Application
        if (app != null) {
            app.registerActivityLifecycleCallbacks(object : android.app.Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: Bundle?) {}
                override fun onActivityStarted(activity: android.app.Activity) {}
                override fun onActivityResumed(activity: android.app.Activity) {
                    val name = activity.javaClass.name
                    if (name.contains("AppActivity") || name.contains("hinge")) {
                        io.github.s1ddhants1.unhinge.hook.ui.HostAppAiFab.attach(activity, prefs)
                    }
                }
                override fun onActivityPaused(activity: android.app.Activity) {}
                override fun onActivityStopped(activity: android.app.Activity) {}
                override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: android.app.Activity) {
                    io.github.s1ddhants1.unhinge.hook.ui.HostAppAiFab.remove(activity)
                }
            })
            Log.i(Consts.TAG, "Registered ActivityLifecycleCallbacks for HostAppAiFab")
        } else {
            hookAppActivityFallback(cl, prefs)
        }
        Log.i(Consts.TAG, "All Unhinge privacy hooks and host FAB active!")
    }

    private fun hookAppActivityFallback(cl: ClassLoader, prefs: PreferencesManager) {
        attempt("hook AppActivity.onResume for AI FAB fallback", silent = true) {
            val activityClass = loadClassFlexible(cl, "co.hinge.app.ui.AppActivity")
            val onResumeMethod = attempt("find AppActivity.onResume", silent = true) {
                activityClass?.getDeclaredMethod("onResume")
            }
            if (onResumeMethod != null) {
                hookTracked(onResumeMethod, idPrefix = "hinge-appactivity-resume").intercept { chain ->
                    val act = chain.thisObject as? android.app.Activity
                    if (act != null) {
                        io.github.s1ddhants1.unhinge.hook.ui.HostAppAiFab.attach(act, prefs)
                    }
                    chain.proceed()
                }
            }
        }
    }
}
