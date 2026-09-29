package io.github.s1ddhants1.unhinge.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import io.github.libxposed.service.XposedService
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.ui.App
import org.json.JSONObject
import org.lsposed.lspatch.IXposedServicePull
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile

object LSPatchHelper {
    const val ACTION_REQUEST_PUSH = "org.lsposed.lspatch.action.REQUEST_PUSH"

    @Volatile
    private var lastRequestTime = 0L

    fun requestServicePush(context: Context) {
        val targetStatus = inspectTargetApp(context)
        if (!targetStatus.isPatched) {
            Log.d(Consts.TAG, "Target app ${Consts.TARGET_PACKAGE} is not patched by LSPatch; skipping service push request")
            return
        }
        val currentFramework = App.service?.let {
            attempt("get frameworkName", silent = true) { it.frameworkName }.orEmpty()
        }.orEmpty()
        if (currentFramework.isNotEmpty() && !currentFramework.contains("LSPatch", ignoreCase = true)) {
            Log.d(Consts.TAG, "Active non-LSPatch framework detected ($currentFramework); skipping LSPatch push request")
            return
        }
        attempt("request LSPatch service push", silent = true) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRequestTime < 3000L) return@attempt
            lastRequestTime = now

            val appContext = context.applicationContext ?: context
            val intent = Intent(ACTION_REQUEST_PUSH)
            val resolveInfos = appContext.packageManager.queryIntentServices(intent, 0)
            if (resolveInfos.isEmpty()) {
                Log.d(Consts.TAG, "No LSPatch manager service found for $ACTION_REQUEST_PUSH")
                return@attempt
            }
            for (info in resolveInfos) {
                val serviceIntent = Intent(intent).apply {
                    component = ComponentName(info.serviceInfo.packageName, info.serviceInfo.name)
                }
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        try {
                            val pullService = IXposedServicePull.Stub.asInterface(service)
                            val accepted = pullService?.requestPush() == true
                            Log.i(Consts.TAG, "LSPatch service push requested from $name: accepted=$accepted")
                        } catch (t: Throwable) {
                            Log.w(Consts.TAG, "Failed to call requestPush on $name: ${t.message}")
                        } finally {
                            try {
                                appContext.unbindService(this)
                            } catch (_: Throwable) { }
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {}
                }
                try {
                    appContext.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
                } catch (t: Throwable) {
                    Log.w(Consts.TAG, "Failed to bind to LSPatch pull service: ${info.serviceInfo.packageName}", t)
                }
            }
        }
    }

    data class TargetStatus(
        val isInstalled: Boolean,
        val isPatched: Boolean,
        val isModuleEmbedded: Boolean,
        val useManager: Boolean? = null
    )

    data class BannerEvaluation(
        val isConnected: Boolean,
        val isInjectable: Boolean,
        val frameworkName: String,
        val frameworkVersion: String,
        val title: String,
        val desc: String,
        val isIntegrated: Boolean = false
    )

    fun inspectTargetApp(context: Context): TargetStatus {
        return attempt("inspect target app", silent = true) {
            val pm = context.packageManager
            val appInfo = attempt("get target app info", silent = true) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getApplicationInfo(
                        Consts.TARGET_PACKAGE,
                        PackageManager.ApplicationInfoFlags.of(PackageManager.GET_META_DATA.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getApplicationInfo(Consts.TARGET_PACKAGE, PackageManager.GET_META_DATA)
                }
            } ?: return@attempt TargetStatus(isInstalled = false, isPatched = false, isModuleEmbedded = false)

            val hasMeta = appInfo.metaData?.containsKey("lspatch") == true
            val hasFactory = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                appInfo.appComponentFactory?.contains("lspatch", ignoreCase = true) == true
            } else false

            val sourceDir = appInfo.sourceDir
            var hasLspAsset = false
            var isEmbedded = false
            var useManager: Boolean? = null

            try {
                ZipFile(sourceDir).use { zip ->
                    hasLspAsset = zip.getEntry("assets/lspatch/config.json") != null ||
                            zip.getEntry("assets/lspatch/metainf") != null
                    val configEntry = zip.getEntry("assets/lspatch/config.json")
                    if (configEntry != null) {
                        val text = zip.getInputStream(configEntry).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                        val json = JSONObject(text)
                        if (json.has("useManager")) useManager = json.optBoolean("useManager")
                        val modules = json.optJSONArray("modules")
                        if (modules != null) {
                            for (i in 0 until modules.length()) {
                                val m = modules.optJSONObject(i) ?: continue
                                val apkPath = m.optString("apkPath", "")
                                if (apkPath.contains(Consts.MODULE_PACKAGE) || apkPath.contains("unhinge")) {
                                    isEmbedded = true
                                    break
                                }
                            }
                        }
                    }
                }
            } catch (_: Throwable) { }

            val isPatched = hasMeta || hasFactory || hasLspAsset
            TargetStatus(isInstalled = true, isPatched = isPatched, isModuleEmbedded = isEmbedded, useManager = useManager)
        } ?: TargetStatus(isInstalled = false, isPatched = false, isModuleEmbedded = false)
    }

    fun isIntegratedMode(context: Context, remotePrefsAvailable: Boolean = false): Boolean {
        if (remotePrefsAvailable) return false
        val status = inspectTargetApp(context)
        return status.isPatched && status.isModuleEmbedded && status.useManager == false
    }

    fun isLSPatched(context: Context): Boolean {
        return inspectTargetApp(context).isPatched
    }

    fun evaluateFrameworkStatus(context: Context, service: XposedService?): BannerEvaluation {
        val target = inspectTargetApp(context)
        if (service == null) {
            return if (target.isPatched && target.isModuleEmbedded && target.useManager == false) {
                BannerEvaluation(
                    isConnected = true,
                    isInjectable = true,
                    frameworkName = "LSPatch",
                    frameworkVersion = "Integrated",
                    title = "LSPatch Integrated Mode Active",
                    desc = "Hooks integrated directly into target application",
                    isIntegrated = true
                )
            } else if (!target.isInstalled) {
                BannerEvaluation(
                    isConnected = false,
                    isInjectable = false,
                    frameworkName = "",
                    frameworkVersion = "",
                    title = "Hinge Not Installed",
                    desc = "Install Hinge to activate companion intelligence features"
                )
            } else {
                BannerEvaluation(
                    isConnected = false,
                    isInjectable = false,
                    frameworkName = "",
                    frameworkVersion = "",
                    title = "Module Inactive",
                    desc = "Enable Unhinge in LSPosed Manager or patch Hinge with LSPatch"
                )
            }
        }

        val fwName = attempt("get frameworkName", silent = true) { service.frameworkName }.orEmpty().ifEmpty { "LSPosed" }
        val fwVersion = attempt("get frameworkVersion", silent = true) { service.frameworkVersion }.orEmpty()
        val scope = attempt("get scope", silent = true) { service.scope } ?: emptyList()
        val inScope = scope.contains(Consts.TARGET_PACKAGE)

        return if (!inScope) {
            BannerEvaluation(
                isConnected = true,
                isInjectable = false,
                frameworkName = fwName,
                frameworkVersion = fwVersion,
                title = "Hinge Not in Scope",
                desc = "Add Hinge (${Consts.TARGET_PACKAGE}) to module scope in LSPosed Manager"
            )
        } else {
            BannerEvaluation(
                isConnected = true,
                isInjectable = true,
                frameworkName = fwName,
                frameworkVersion = fwVersion,
                title = "$fwName Active",
                desc = "Connected to $fwName $fwVersion (API ${service.apiVersion})"
            )
        }
    }
}
