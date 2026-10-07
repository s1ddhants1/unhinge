package io.github.s1ddhants1.unhinge.hook.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.github.s1ddhants1.unhinge.util.attempt
import java.util.concurrent.CopyOnWriteArrayList

object HostLikesReader {
    private const val HINGE_DEFAULT_PREFS = "default"
    private const val KEY_LOCAL_AVAILABLE_LIKES = "localAvailableLikes"
    private const val KEY_API_AVAILABLE_LIKES = "apiAvailableLikes"
    private const val KEY_LOCAL_AVAILABLE_SUPERLIKES = "localAvailableSuperlikes"
    private const val KEY_API_AVAILABLE_SUPERLIKES = "apiAvailableSuperLikes"

    data class LikesState(
        val availableLikes: Int = -1,
        val availableSuperlikes: Int = -1,
        val lastUpdated: Long = 0L
    ) {
        val hasLikes: Boolean get() = availableLikes > 0

        val displayLikes: String
            get() = if (availableLikes >= 0) availableLikes.toString() else "—"

        val displaySuperlikes: String
            get() = if (availableSuperlikes >= 0) availableSuperlikes.toString() else "0"

        val formattedSummary: String
            get() = when {
                availableLikes >= 0 && availableSuperlikes > 0 ->
                    "$availableLikes like${if (availableLikes == 1) "" else "s"} remaining • $availableSuperlikes rose${if (availableSuperlikes == 1) "" else "s"}"
                availableLikes >= 0 ->
                    "$availableLikes free like${if (availableLikes == 1) "" else "s"} remaining today"
                else ->
                    "Available likes count unavailable"
            }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val observers = CopyOnWriteArrayList<(LikesState) -> Unit>()
    private var registeredPrefs: SharedPreferences? = null

    private val prefChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == null ||
            changedKey == KEY_LOCAL_AVAILABLE_LIKES ||
            changedKey == KEY_API_AVAILABLE_LIKES ||
            changedKey == KEY_LOCAL_AVAILABLE_SUPERLIKES ||
            changedKey == KEY_API_AVAILABLE_SUPERLIKES
        ) {
            val state = readCurrentState(registeredPrefs)
            mainHandler.post {
                observers.forEach { observer ->
                    attempt("dispatch likes update", silent = true) {
                        observer.invoke(state)
                    }
                }
            }
        }
    }

    fun getLikesInfo(context: Context): LikesState {
        val prefs = attempt("obtain Hinge default prefs", silent = true) {
            context.applicationContext.getSharedPreferences(HINGE_DEFAULT_PREFS, Context.MODE_PRIVATE)
        } ?: attempt("obtain Hinge default prefs from context directly", silent = true) {
            context.getSharedPreferences(HINGE_DEFAULT_PREFS, Context.MODE_PRIVATE)
        }
        return readCurrentState(prefs)
    }

    fun parseStateFromPreferences(prefs: SharedPreferences): LikesState {
        return readCurrentState(prefs)
    }

    private fun readCurrentState(prefs: SharedPreferences?): LikesState {
        if (prefs == null) return LikesState()

        return attempt("parse Hinge likes state", silent = true) {
            val localLikes = if (prefs.contains(KEY_LOCAL_AVAILABLE_LIKES)) prefs.getInt(KEY_LOCAL_AVAILABLE_LIKES, -1) else -1
            val apiLikes = if (prefs.contains(KEY_API_AVAILABLE_LIKES)) prefs.getInt(KEY_API_AVAILABLE_LIKES, -1) else -1
            val likes = if (localLikes >= 0) localLikes else apiLikes

            val localSuperlikes = if (prefs.contains(KEY_LOCAL_AVAILABLE_SUPERLIKES)) prefs.getInt(KEY_LOCAL_AVAILABLE_SUPERLIKES, -1) else -1
            val apiSuperlikes = if (prefs.contains(KEY_API_AVAILABLE_SUPERLIKES)) prefs.getInt(KEY_API_AVAILABLE_SUPERLIKES, -1) else -1
            val superlikes = if (localSuperlikes >= 0) localSuperlikes else apiSuperlikes

            LikesState(
                availableLikes = likes,
                availableSuperlikes = superlikes,
                lastUpdated = System.currentTimeMillis()
            )
        } ?: LikesState()
    }

    fun registerObserver(context: Context, observer: (LikesState) -> Unit) {
        observers.add(observer)

        if (registeredPrefs == null) {
            attempt("register Hinge default prefs change listener", silent = true) {
                val prefs = context.applicationContext.getSharedPreferences(HINGE_DEFAULT_PREFS, Context.MODE_PRIVATE)
                    ?: context.getSharedPreferences(HINGE_DEFAULT_PREFS, Context.MODE_PRIVATE)
                registeredPrefs = prefs
                prefs.registerOnSharedPreferenceChangeListener(prefChangeListener)
            }
        }

        val current = getLikesInfo(context)
        observer.invoke(current)
    }

    fun unregisterObserver(observer: (LikesState) -> Unit) {
        observers.remove(observer)
        if (observers.isEmpty()) {
            attempt("unregister Hinge default prefs change listener", silent = true) {
                registeredPrefs?.unregisterOnSharedPreferenceChangeListener(prefChangeListener)
            }
            registeredPrefs = null
        }
    }
}
