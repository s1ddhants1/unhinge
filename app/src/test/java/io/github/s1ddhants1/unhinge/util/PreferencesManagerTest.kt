package io.github.s1ddhants1.unhinge.util

import android.content.SharedPreferences
import io.github.s1ddhants1.unhinge.Consts
import org.junit.Assert.*
import org.junit.Test

class PreferencesManagerTest {

    private class FakeEditor(private val storage: MutableMap<String, Any?>) : SharedPreferences.Editor {
        private val staging = mutableMapOf<String, Any?>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor = apply { staging[key] = value }
        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor = apply { staging[key] = values }
        override fun putInt(key: String, value: Int): SharedPreferences.Editor = apply { staging[key] = value }
        override fun putLong(key: String, value: Long): SharedPreferences.Editor = apply { staging[key] = value }
        override fun putFloat(key: String, value: Float): SharedPreferences.Editor = apply { staging[key] = value }
        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor = apply { staging[key] = value }
        override fun remove(key: String): SharedPreferences.Editor = apply { staging.remove(key); storage.remove(key) }
        override fun clear(): SharedPreferences.Editor = apply { staging.clear(); storage.clear() }
        override fun commit(): Boolean {
            storage.putAll(staging)
            return true
        }
        override fun apply() {
            storage.putAll(staging)
        }
    }

    private class FakeSharedPreferences : SharedPreferences {
        val map = mutableMapOf<String, Any?>()

        val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

        override fun getAll(): Map<String, *> = map
        override fun getString(key: String, defValue: String?): String? = (map[key] as? String) ?: defValue
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = (map[key] as? Set<String>) ?: defValues
        override fun getInt(key: String, defValue: Int): Int = (map[key] as? Int) ?: defValue
        override fun getLong(key: String, defValue: Long): Long = (map[key] as? Long) ?: defValue
        override fun getFloat(key: String, defValue: Float): Float = (map[key] as? Float) ?: defValue
        override fun getBoolean(key: String, defValue: Boolean): Boolean = (map[key] as? Boolean) ?: defValue
        override fun contains(key: String): Boolean = map.containsKey(key)
        override fun edit(): SharedPreferences.Editor = FakeEditor(map)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.add(listener)
        }
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
            if (listener != null) listeners.remove(listener)
        }

        fun notifyChange(key: String?) {
            listeners.toList().forEach { it.onSharedPreferenceChanged(this, key) }
        }
    }

    @Test
    fun testDefaultValuesAndEffectiveChecks() {
        val fakePrefs = FakeSharedPreferences()
        val prefs = PreferencesManager(fakePrefs)

        // Master enabled is true by default
        assertTrue(prefs.masterEnabled)
        assertTrue(prefs.blockFirebase)
        assertTrue(prefs.blockCrashUpload)
        assertTrue(prefs.blockPerf)
        assertTrue(prefs.blockAppsflyer)
        assertFalse(prefs.blockIncognia)
        assertFalse(prefs.fuzzLocation)

        // Test isEffective gating
        assertTrue(prefs.isEffective(prefs.blockFirebase))
        assertFalse(prefs.isEffective(prefs.blockIncognia))

        // Disabling master toggle disables all effective protections
        prefs.masterEnabled = false
        assertFalse(prefs.isEffective(prefs.blockFirebase))
        assertFalse(prefs.isEffective(true))
    }

    @Test
    fun testPreferenceMutationAndListenerDispatch() {
        val fakePrefs = FakeSharedPreferences()
        val prefs = PreferencesManager(fakePrefs)

        var changeCount = 0
        prefs.onPreferenceChanged = { changeCount++ }

        prefs.masterEnabled = false
        assertEquals(1, changeCount)
        assertFalse(fakePrefs.getBoolean(Consts.PREF_MASTER_ENABLED, true))

        prefs.openRouterModel = "google/gemini-pro"
        assertEquals(2, changeCount)
        assertEquals("google/gemini-pro", fakePrefs.getString(Consts.PREF_OPENROUTER_MODEL, ""))

        prefs.themeColor = 0xFF123456L
        assertEquals(3, changeCount)
        assertEquals(0xFF123456L, fakePrefs.getLong(Consts.PREF_THEME_COLOR, 0L))
    }

    @Test
    fun testRemotePreferenceChangeListenerSync() {
        val fakePrefs = FakeSharedPreferences()
        val prefs = PreferencesManager(fakePrefs, isDynamic = true)

        assertEquals(1, fakePrefs.listeners.size)
        assertFalse(prefs.blockIncognia)

        // Simulate incoming update via RemotePreferences IPC from companion app
        fakePrefs.map[Consts.PREF_BLOCK_INCOGNIA] = true
        var notified = false
        prefs.onPreferenceChanged = { notified = true }

        fakePrefs.notifyChange(Consts.PREF_BLOCK_INCOGNIA)

        assertTrue(prefs.blockIncognia)
        assertTrue(notified)

        prefs.unregister()
        assertEquals(0, fakePrefs.listeners.size)
    }

    @Test
    fun testThemeModeEnumParsing() {
        val fakePrefs = FakeSharedPreferences()
        val prefs = PreferencesManager(fakePrefs)

        prefs.themeMode = ThemeMode.DARK
        assertEquals(ThemeMode.DARK, prefs.themeMode)
        assertEquals("DARK", fakePrefs.getString(Consts.PREF_THEME_MODE, ""))

        prefs.themeMode = ThemeMode.LIGHT
        assertEquals(ThemeMode.LIGHT, prefs.themeMode)

        // Invalid fallback
        prefs.themeModeString = "NON_EXISTENT_MODE"
        assertEquals(ThemeMode.SYSTEM, prefs.themeMode)
    }
}
