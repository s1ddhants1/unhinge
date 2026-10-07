package io.github.s1ddhants1.unhinge.data

import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class BackupRestoreManagerTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testExportToJson_fullBundleStructure() {
        val bundleJson = BackupRestoreManager.exportToJson(
            prefs = null,
            candidateArchiveDb = null,
            includeSettings = false,
            includeCandidates = false
        )

        val parsed = json.decodeFromString<UnhingeBackupBundle>(bundleJson)
        assertEquals(1, parsed.version)
        assertEquals("Unhinge", parsed.app)
        assertTrue(parsed.exportedAt > 0)
        assertNull(parsed.settings)
        assertNull(parsed.candidates)
    }

    @Test
    fun testSerializationRoundTrip_settingsAndCandidates() {
        val testSettings = UnhingeSettingsBackup(
            masterEnabled = true,
            blockFirebase = true,
            blockAppsflyer = true,
            aiProvider = "Zen",
            openRouterModel = "space-bunny-free",
            pureBlack = true,
            enableFeedNavigation = true,
            showAvailableLikes = true
        )

        val testCandidates = listOf(
            CachedCandidateProfile(
                userId = "test_1",
                firstName = "Jordan",
                age = 25,
                photos = listOf("https://example.com/p1.jpg")
            ),
            CachedCandidateProfile(
                userId = "test_2",
                firstName = "Taylor",
                age = 28,
                likeComment = "Great photo!"
            )
        )

        val bundle = UnhingeBackupBundle(
            version = 1,
            app = "Unhinge",
            exportedAt = System.currentTimeMillis(),
            settings = testSettings,
            candidates = testCandidates
        )

        val rawJson = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(bundle)
        val decoded = json.decodeFromString<UnhingeBackupBundle>(rawJson)

        assertEquals(1, decoded.version)
        assertNotNull(decoded.settings)
        assertEquals("Zen", decoded.settings?.aiProvider)
        assertEquals("space-bunny-free", decoded.settings?.openRouterModel)
        assertTrue(decoded.settings?.pureBlack == true)

        assertNotNull(decoded.candidates)
        assertEquals(2, decoded.candidates?.size)
        assertEquals("test_1", decoded.candidates?.get(0)?.userId)
        assertEquals("Jordan", decoded.candidates?.get(0)?.firstName)
        assertEquals("Taylor", decoded.candidates?.get(1)?.firstName)
        assertEquals("Great photo!", decoded.candidates?.get(1)?.likeComment)
    }

    @Test
    fun testImportFromJson_corruptedJsonFailsGracefully() {
        val corruptedJson = "{ this is not valid json : [[{"
        val result = BackupRestoreManager.importFromJson(
            context = null,
            jsonString = corruptedJson,
            prefs = null,
            candidateArchiveDb = null
        )

        assertFalse(result.success)
        assertFalse(result.settingsRestored)
        assertEquals(0, result.candidatesImported)
        assertNotNull(result.errorMessage)
    }

    @Test
    fun testImportFromJson_forwardCompatibilityUnknownKeys() {
        val futureJson = """
            {
                "version": 2,
                "app": "Unhinge",
                "exportedAt": 1700000000000,
                "newUnknownField": "should_be_ignored",
                "settings": {
                    "masterEnabled": false,
                    "unknownSetting": true,
                    "aiProvider": "Custom"
                },
                "candidates": []
            }
        """.trimIndent()

        val decoded = json.decodeFromString<UnhingeBackupBundle>(futureJson)
        assertEquals(2, decoded.version)
        assertNotNull(decoded.settings)
        assertEquals("Custom", decoded.settings?.aiProvider)
        assertFalse(decoded.settings?.masterEnabled == true)
    }
}
