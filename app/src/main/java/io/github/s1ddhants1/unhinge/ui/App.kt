package io.github.s1ddhants1.unhinge.ui

import android.app.Application
import android.util.Log
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.util.LSPatchHelper
import io.github.s1ddhants1.unhinge.util.UnhingeImageLoader
import io.github.s1ddhants1.unhinge.util.attempt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class App : Application(), SingletonImageLoader.Factory, XposedServiceHelper.OnServiceListener {

    companion object {
        const val TAG = Consts.APP_TAG

        private val _serviceState = MutableStateFlow<XposedService?>(null)
        val serviceState = _serviceState.asStateFlow()

        val service: XposedService?
            get() = _serviceState.value

        val isBound: Boolean
            get() = service != null
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return UnhingeImageLoader.get(context)
    }

    override fun onCreate() {
        super.onCreate()
        attempt("initialize SingletonImageLoader") {
            SingletonImageLoader.setSafe { UnhingeImageLoader.get(it) }
        }
        attempt("register XposedServiceHelper listener") {
            XposedServiceHelper.registerListener(this)
            Log.i(TAG, "service listener registered, bound=$isBound")
        }
        LSPatchHelper.requestServicePush(this)
    }

    override fun onServiceBind(service: XposedService) {
        Log.i(TAG, "connected: ${service.frameworkName} v${service.frameworkVersion} (API ${service.apiVersion})")
        val existing = _serviceState.value
        if (existing != null && existing.frameworkName.contains("LSPosed", ignoreCase = true) &&
            service.frameworkName.contains("LSPatch", ignoreCase = true)) {
            Log.w(TAG, "Ignoring LSPatch service push because LSPosed is already bound")
            return
        }
        _serviceState.value = service
    }

    override fun onServiceDied(service: XposedService) {
        Log.w(TAG, "framework service died")
        _serviceState.value = null
    }
}
