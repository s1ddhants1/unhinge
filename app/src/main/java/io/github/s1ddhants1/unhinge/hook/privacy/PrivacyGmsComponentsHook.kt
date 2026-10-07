package io.github.s1ddhants1.unhinge.hook.privacy

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.hook.HookHandler
import io.github.s1ddhants1.unhinge.util.PreferencesManager

import io.github.s1ddhants1.unhinge.util.attempt

object PrivacyGmsComponentsHook : HookHandler {
    private var gmsComponentsDisabled = false

    private val GMS_MEASUREMENT_COMPONENTS = arrayOf(
        "com.google.android.gms.measurement.AppMeasurementService",
        "com.google.android.gms.measurement.AppMeasurementReceiver",
        "com.google.android.gms.measurement.AppMeasurementJobService",
        "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
        "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
        "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver"
    )

    override fun apply(
        module: XposedModule,
        context: Context?,
        classLoader: ClassLoader,
        prefs: PreferencesManager
    ) {
        if (context == null || !prefs.isEffective(prefs.blockGmsMeasurement) || gmsComponentsDisabled) return
        gmsComponentsDisabled = true

        attempt("disable GMS measurement components via PackageManager", silent = true) {
            val pm = context.packageManager
            val pkg = context.packageName
            for (compName in GMS_MEASUREMENT_COMPONENTS) {
                try {
                    val comp = ComponentName(pkg, compName)
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (t: Throwable) {
                    Log.d(Consts.TAG, "PrivacyGmsComponentsHook: could not disable $compName: ${t.message}")
                }
            }
            Log.i(Consts.TAG, "PrivacyGmsComponentsHook: measurement components disabled via PackageManager")
        }
    }

    fun reset() {
        gmsComponentsDisabled = false
    }
}
