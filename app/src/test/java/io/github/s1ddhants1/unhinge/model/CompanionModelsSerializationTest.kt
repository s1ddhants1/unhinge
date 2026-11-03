package io.github.s1ddhants1.unhinge.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class CompanionModelsSerializationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Test
    fun testDailyLikeStatusSerialization() {
        val original = DailyLikeStatus(
            likesLeft = 5,
            maxLikes = 8,
            freeRosesLeft = 1,
            roseResetTimestamp = 1750000000000L,
            lastUpdated = 1750000001000L
        )
        val str = json.encodeToString(original)
        val decoded = json.decodeFromString<DailyLikeStatus>(str)
        assertEquals(original, decoded)
    }

    @Test
    fun testIncomingLikeInsightSerialization() {
        val original = IncomingLikeInsight(
            totalLikes = 15,
            commentLikes = 8,
            plainLikes = 7,
            photoLikes = 10,
            promptLikes = 5,
            voicePromptLikes = 0,
            recentLikeTimestamps = listOf(1750000000000L, 1750000001000L),
            lastUpdated = 1750000002000L
        )
        val str = json.encodeToString(original)
        val decoded = json.decodeFromString<IncomingLikeInsight>(str)
        assertEquals(original, decoded)
        assertEquals(8f / 15f * 100f, decoded.commentRate, 0.01f)
    }

    @Test
    fun testProfileAuditReportSerialization() {
        val original = ProfileAuditReport(
            percentComplete = 95,
            photoCount = 6,
            promptCount = 3,
            hasVoicePrompt = true,
            hasVideoPrompt = false,
            promptEvaluationStatus = "Good",
            promptFeedbackDetail = "Strong conversation starters",
            coachingTips = listOf("Consider swapping photo 4 with an action shot"),
            lastUpdated = 1750000000000L
        )
        val str = json.encodeToString(original)
        val decoded = json.decodeFromString<ProfileAuditReport>(str)
        assertEquals(original, decoded)
    }

    @Test
    fun testBoostInsightSerialization() {
        val original = BoostInsight(
            availableBoosts = 1,
            lastBoostTimestamp = 1750000000000L,
            optimalDay = "Sunday",
            optimalHourRange = "7:00 PM – 9:30 PM",
            historyCount = 2,
            lastUpdated = 1750000001000L
        )
        val str = json.encodeToString(original)
        val decoded = json.decodeFromString<BoostInsight>(str)
        assertEquals(original, decoded)
    }

    @Test
    fun testFreshStartStatusSerialization() {
        val original = FreshStartStatus(
            isEligible = true,
            lastCheckTimestamp = 1750000000000L,
            resetHistoryCount = 1,
            lastResetTimestamp = 1740000000000L
        )
        val str = json.encodeToString(original)
        val decoded = json.decodeFromString<FreshStartStatus>(str)
        assertEquals(original, decoded)
    }

    @Test
    fun testSessionBehaviorMetricsSerialization() {
        val original = SessionBehaviorMetrics(
            totalSessions = 12,
            averageDwellSeconds = 8L,
            profilesSeen = 40,
            profilesLiked = 8,
            consecutivePassStreak = 5,
            lastSessionTimestamp = 1750000000000L
        )
        val str = json.encodeToString(original)
        val decoded = json.decodeFromString<SessionBehaviorMetrics>(str)
        assertEquals(original, decoded)
        assertEquals(20.0f, decoded.selectivityRatio, 0.01f)
    }

    @Test
    fun testCandidateSerialization() {
        val original = CachedCandidateProfile(
            userId = "test_user_1",
            firstName = "Alice",
            age = 25,
            height = 168,
            location = "New Delhi",
            photos = listOf("https://example.com/photo1.jpg"),
            prompts = listOf(CandidatePromptItem("My prompt", "My answer")),
            isLiveInFeed = true,
            ratingStatus = "Liked"
        )
        val str = json.encodeToString(original)
        val decoded = json.decodeFromString<CachedCandidateProfile>(str)
        assertEquals(original, decoded)
    }
}
