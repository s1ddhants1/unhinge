package io.github.s1ddhants1.unhinge.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import io.github.s1ddhants1.unhinge.Consts

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    suspend fun forceStopTargetApp(
        context: Context,
        packageName: String = Consts.TARGET_PACKAGE,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
        processExecutor: (Array<String>) -> Process = { Runtime.getRuntime().exec(it) }
    ): Boolean {
        val stopped = withContext(ioDispatcher) {
            var process: Process? = null
            try {
                process = processExecutor(arrayOf("su", "-c", "am force-stop $packageName"))
                withTimeoutOrNull(3_000) { process.waitFor() } == 0
            } catch (_: Throwable) {
                false
            } finally {
                process?.destroy()
            }
        }
        if (stopped) return true

        openTargetAppDetails(context)
        return false
    }

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
