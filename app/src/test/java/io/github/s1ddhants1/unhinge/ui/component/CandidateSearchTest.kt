package io.github.s1ddhants1.unhinge.ui.component

import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.model.CandidatePromptItem
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateSearchTest {

    private val sampleCandidate = CachedCandidateProfile(
        userId = "user_12345",
        firstName = "Chloé",
        age = 27,
        height = 172,
        hometown = "Montreal, QC",
        location = "Austin, TX",
        jobTitle = "Software Architect",
        datingIntention = "Long-term relationship",
        relationshipType = "Monogamy",
        religion = "Agnostic",
        ethnicity = "French-Canadian",
        isSelfieVerified = true,
        isCircleMember = true,
        isStandout = true,
        isLiveInFeed = true,
        ratingStatus = "Liked",
        likeComment = "Loved your greenbelt photo!",
        prompts = listOf(
            CandidatePromptItem("Typical Sunday", "Coffee, vinyl records, and hiking in the greenbelt"),
            CandidatePromptItem("I'm looking for", "Someone who appreciates dry humor and spontaneous road trips")
        )
    )

    @Test
    fun testNormalizeSearchText() {

        val normalized = normalizeSearchText("Chloé’s “Special” Café")
        assertTrue(normalized.contains("chloe's"))
        assertTrue(normalized.contains("\"special\""))
        assertTrue(normalized.contains("cafe"))
    }

    @Test
    fun testSingleTermMatching() {
        assertTrue(matchesCandidateSearch(sampleCandidate, "Chloé"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "chloe"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "Architect"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "Austin"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "Monogamy"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "Agnostic"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "French-Canadian"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "vinyl"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "greenbelt"))
    }

    @Test
    fun testMultiTokenConjunction() {

        assertTrue(matchesCandidateSearch(sampleCandidate, "Chloe Architect"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "Austin, TX Software"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "Austin 27yo"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "chloe coffee vinyl"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "Montreal Architect 27"))

        assertFalse(matchesCandidateSearch(sampleCandidate, "Chloe Doctor"))
        assertFalse(matchesCandidateSearch(sampleCandidate, "Austin 30"))
    }

    @Test
    fun testAgeAndHeightMatching() {
        assertTrue(matchesCandidateSearch(sampleCandidate, "27"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "27yo"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "172cm"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "172"))
    }

    @Test
    fun testBadgesAndStatusMatching() {
        assertTrue(matchesCandidateSearch(sampleCandidate, "verified"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "circle"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "standout"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "feed"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "liked"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "like"))
    }

    @Test
    fun testLikeCommentMatching() {
        assertTrue(matchesCandidateSearch(sampleCandidate, "greenbelt photo"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "loved your"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "comment"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "note"))
        assertTrue(matchesCandidateSearch(sampleCandidate, "Chloe photo"))
    }

    @Test
    fun testBlankOrPunctuationOnlyQueries() {
        assertTrue(matchesCandidateSearch(sampleCandidate, ""))
        assertTrue(matchesCandidateSearch(sampleCandidate, "   "))
        assertTrue(matchesCandidateSearch(sampleCandidate, " , ;  "))
    }

    @Test
    fun testRichAttributesSearch() {
        val richCandidate = sampleCandidate.copy(
            school = "McGill University",
            employer = "Google",
            politics = "Liberal",
            drinking = "Socially",
            smoking = "Never",
            kids = "Don't have children",
            pet = "Dog",
            zodiac = "Leo",
            isNewHere = true,
            isYourTypeLately = true,
            isIncomingLike = true,
            incomingComment = "Hey! What's your favorite spot in Austin?",
            incomingLikeType = "rose"
        )

        assertTrue(matchesCandidateSearch(richCandidate, "McGill"))
        assertTrue(matchesCandidateSearch(richCandidate, "Google"))
        assertTrue(matchesCandidateSearch(richCandidate, "Liberal"))
        assertTrue(matchesCandidateSearch(richCandidate, "Socially"))
        assertTrue(matchesCandidateSearch(richCandidate, "Dog"))
        assertTrue(matchesCandidateSearch(richCandidate, "Leo"))
        assertTrue(matchesCandidateSearch(richCandidate, "new"))
        assertTrue(matchesCandidateSearch(richCandidate, "type"))
        assertTrue(matchesCandidateSearch(richCandidate, "liked you"))
        assertTrue(matchesCandidateSearch(richCandidate, "rose"))
        assertTrue(matchesCandidateSearch(richCandidate, "favorite spot"))
        assertTrue(matchesCandidateSearch(richCandidate, "Google McGill Leo"))
    }
}
