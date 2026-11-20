package io.github.s1ddhants1.unhinge.ui

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.setSingletonImageLoaderFactory
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.ui.component.navigation.UnhingeApp
import io.github.s1ddhants1.unhinge.ui.theme.Theme
import io.github.s1ddhants1.unhinge.util.LSPatchHelper
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.UnhingeImageLoader
import io.github.s1ddhants1.unhinge.util.attempt

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            val localPrefs = remember { getSharedPreferences(Consts.PREFS_SETTINGS, Context.MODE_PRIVATE) }
            val prefsState = remember {
                val mgr = PreferencesManager(localPrefs)
                mgr.loadFromFallbackStorage(this@MainActivity)
                mgr.onPreferenceChanged = { mgr.saveToFallbackStorageAsync(this@MainActivity) }
                mutableStateOf(mgr)
            }
            val prefs = prefsState.value

            LaunchedEffect(Unit) {
                LSPatchHelper.requestServicePush(this@MainActivity)
                viewModel.checkRootStatus()
                viewModel.loadInsights()

                App.serviceState.collect { service ->
                    val evaluation = LSPatchHelper.evaluateFrameworkStatus(this@MainActivity, service)
                    viewModel.updateFrameworkEvaluation(evaluation)
                    if (service != null && !evaluation.isIntegrated) {
                        attempt("retrieve remote preferences from XposedService") {
                            val remotePrefs = service.getRemotePreferences(Consts.PREFS_SETTINGS)
                            syncPreferences(localPrefs, remotePrefs)
                            val remoteMgr = PreferencesManager(remotePrefs, backupPrefs = localPrefs)
                            remoteMgr.onPreferenceChanged = {
                                remoteMgr.saveToFallbackStorageAsync(this@MainActivity)
                            }
                            prefsState.value = remoteMgr
                        }
                    }
                }
            }

            setSingletonImageLoaderFactory { context ->
                UnhingeImageLoader.get(context)
            }

            Theme(
                themeMode = prefs.themeMode,
                pureBlack = prefs.pureBlack,
                themeColor = Color(prefs.themeColor)
            ) {
                UnhingeApp(
                    viewModel = viewModel,
                    state = state,
                    prefs = prefs,
                    intent = intent
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.checkRootStatus()
        viewModel.loadInsights()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }

    private fun syncPreferences(localPrefs: SharedPreferences, remotePrefs: SharedPreferences) {
        val localTime = localPrefs.getLong("updated_at", 0L)
        val remoteTime = remotePrefs.getLong("updated_at", 0L)

        val localMap = localPrefs.all
        val remoteMap = remotePrefs.all

        val remoteEditor = remotePrefs.edit()
        val localEditor = localPrefs.edit()

        val localProvider = localPrefs.getString(Consts.PREF_AI_PROVIDER, "") ?: ""
        val remoteProvider = remotePrefs.getString(Consts.PREF_AI_PROVIDER, "") ?: ""

        if (localTime >= remoteTime || remoteMap.isEmpty() || (remoteProvider.isBlank() && localProvider.isNotBlank())) {
            for ((k, v) in localMap) {
                when (v) {
                    is Boolean -> remoteEditor.putBoolean(k, v)
                    is String -> remoteEditor.putString(k, v)
                    is Int -> remoteEditor.putInt(k, v)
                    is Long -> remoteEditor.putLong(k, v)
                    is Float -> remoteEditor.putFloat(k, v)
                }
            }
        } else {
            for ((k, v) in remoteMap) {
                when (v) {
                    is Boolean -> localEditor.putBoolean(k, v)
                    is String -> localEditor.putString(k, v)
                    is Int -> localEditor.putInt(k, v)
                    is Long -> localEditor.putLong(k, v)
                    is Float -> localEditor.putFloat(k, v)
                }
            }
        }

        for ((k, v) in localMap) {
            if (!remotePrefs.contains(k)) {
                when (v) {
                    is Boolean -> remoteEditor.putBoolean(k, v)
                    is String -> remoteEditor.putString(k, v)
                    is Int -> remoteEditor.putInt(k, v)
                    is Long -> remoteEditor.putLong(k, v)
                    is Float -> remoteEditor.putFloat(k, v)
                }
            }
        }
        for ((k, v) in remoteMap) {
            if (!localPrefs.contains(k)) {
                when (v) {
                    is Boolean -> localEditor.putBoolean(k, v)
                    is String -> localEditor.putString(k, v)
                    is Int -> localEditor.putInt(k, v)
                    is Long -> localEditor.putLong(k, v)
                    is Float -> localEditor.putFloat(k, v)
                }
            }
        }

        remoteEditor.apply()
        localEditor.apply()
        Log.i(Consts.TAG, "syncPreferences: local keys=${localMap.size}, remote keys=${remoteMap.size}")
    }
}
