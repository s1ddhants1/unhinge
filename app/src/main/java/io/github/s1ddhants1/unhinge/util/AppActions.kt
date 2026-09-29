package io.github.s1ddhants1.unhinge.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.s1ddhants1.unhinge.Consts

object AppActions {
    fun launchTargetApp(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(Consts.TARGET_PACKAGE)
            ?: Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                setPackage(Consts.TARGET_PACKAGE)
            }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        attempt("launch ${Consts.TARGET_PACKAGE}") { context.startActivity(launch) }
    }

    fun openTarget(context: Context) = launchTargetApp(context)

    fun forceStopTargetApp(context: Context): Boolean {
        var process: Process? = null
        try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "am force-stop ${Consts.TARGET_PACKAGE}"))
            val exited = try {
                val deadline = System.currentTimeMillis() + 3000
                while (process.isAlive && System.currentTimeMillis() < deadline) Thread.sleep(50)
                if (!process.isAlive) process.exitValue() == 0 else false
            } catch (_: Throwable) { false } finally { process?.destroy() }
            if (exited) return true
        } catch (_: Throwable) { }
        openTargetAppDetails(context)
        return false
    }

    fun forceStopTarget(context: Context) = forceStopTargetApp(context)

    fun openTargetAppDetails(context: Context) {
        attempt("open ${Consts.TARGET_PACKAGE} app settings") {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", Consts.TARGET_PACKAGE, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }
}
