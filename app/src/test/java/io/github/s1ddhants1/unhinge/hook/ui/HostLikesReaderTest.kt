package io.github.s1ddhants1.unhinge.hook.ui

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class HostLikesReaderTest {

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
    }

    @Test
    fun testParseStateWithLocalAvailableLikesAndSuperlikes() {
        val fakePrefs = FakeSharedPreferences().apply {
            map["localAvailableLikes"] = 7
            map["apiAvailableLikes"] = 7
            map["localAvailableSuperlikes"] = 1
            map["apiAvailableSuperLikes"] = 1
        }

        val state = HostLikesReader.parseStateFromPreferences(fakePrefs)

        assertEquals(7, state.availableLikes)
        assertEquals(1, state.availableSuperlikes)
        assertTrue(state.hasLikes)
        assertEquals("7", state.displayLikes)
        assertEquals("1", state.displaySuperlikes)
        assertEquals("7 likes remaining • 1 rose", state.formattedSummary)
    }

    @Test
    fun testParseStateFallbackToApiAvailableLikes() {
        val fakePrefs = FakeSharedPreferences().apply {
            map["apiAvailableLikes"] = 5
            map["apiAvailableSuperLikes"] = 0
        }

        val state = HostLikesReader.parseStateFromPreferences(fakePrefs)

        assertEquals(5, state.availableLikes)
        assertEquals(0, state.availableSuperlikes)
        assertTrue(state.hasLikes)
        assertEquals("5", state.displayLikes)
        assertEquals("0", state.displaySuperlikes)
        assertEquals("5 free likes remaining today", state.formattedSummary)
    }

    @Test
    fun testParseStateZeroLikesRemaining() {
        val fakePrefs = FakeSharedPreferences().apply {
            map["localAvailableLikes"] = 0
            map["localAvailableSuperlikes"] = 0
        }

        val state = HostLikesReader.parseStateFromPreferences(fakePrefs)

        assertEquals(0, state.availableLikes)
        assertEquals(0, state.availableSuperlikes)
        assertFalse(state.hasLikes)
        assertEquals("0", state.displayLikes)
        assertEquals("0 free likes remaining today", state.formattedSummary)
    }

    @Test
    fun testParseStateEmptyPreferences() {
        val fakePrefs = FakeSharedPreferences()

        val state = HostLikesReader.parseStateFromPreferences(fakePrefs)

        assertEquals(-1, state.availableLikes)
        assertEquals(-1, state.availableSuperlikes)
        assertFalse(state.hasLikes)
        assertEquals("—", state.displayLikes)
        assertEquals("Available likes count unavailable", state.formattedSummary)
    }
}
