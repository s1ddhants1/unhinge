package io.github.s1ddhants1.unhinge.data

import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.model.CandidatePromptItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TargetCandidateResolutionTest {

    private val standoutCandidate = CachedCandidateProfile(
        userId = "standout_user_1",
        firstName = "Elena",
        age = 28,
        height = 168,
        hometown = "Chicago, IL",
        location = "Austin, TX",
        jobTitle = "Product Designer",
        datingIntention = "Long-term relationship",
        relationshipType = "Monogamy",
        religion = "Agnostic",
        ethnicity = "Caucasian",
        isSelfieVerified = true,
        isCircleMember = true,
        isStandout = true,
        isDiscover = false,
        isIncomingLike = false,
        isLiveInFeed = true,
        school = "Northwestern",
        employer = "DesignLab",
        prompts = listOf(
            CandidatePromptItem(
                question = "My simple pleasures",
                answer = "Matcha lattes, vintage bookstores, and sunrise pottery sessions"
            ),
            CandidatePromptItem(
                question = "Together, we could",
                answer = "Build a cozy cabin in the mountains and adopt three golden retrievers"
            )
        )
    )

    private val discoverCandidate = CachedCandidateProfile(
        userId = "discover_user_2",
        firstName = "Maya",
        age = 25,
        height = 175,
        hometown = "Seattle, WA",
        location = "Austin, TX",
        jobTitle = "Bioinformatics Scientist",
        datingIntention = "Life partner",
        relationshipType = "Monogamy",
        religion = "Atheist",
        ethnicity = "East Asian",
        isSelfieVerified = true,
        isCircleMember = false,
        isStandout = false,
        isDiscover = true,
        isIncomingLike = false,
        isLiveInFeed = true,
        school = "UW Seattle",
        employer = "Genomics Inc",
        prompts = listOf(
            CandidatePromptItem(
                question = "A shower thought I had recently",
                answer = "Turtles probably find humans terrifyingly fast at walking"
            ),
            CandidatePromptItem(
                question = "I'm looking for",
                answer = "Someone who gets overly passionate about obscure board games"
            )
        )
    )

    private val incomingLikeCandidate = CachedCandidateProfile(
        userId = "incoming_user_3",
        firstName = "Sophia",
        age = 29,
        height = 165,
        hometown = "Denver, CO",
        location = "Austin, TX",
        jobTitle = "Landscape Architect",
        datingIntention = "Long-term relationship",
        relationshipType = "Monogamy",
        religion = "Spiritual",
        ethnicity = "Hispanic",
        isSelfieVerified = true,
        isCircleMember = false,
        isStandout = false,
        isDiscover = false,
        isIncomingLike = true,
        isLiveInFeed = true,
        school = "Colorado Boulder",
        employer = "EcoLandscapes",
        prompts = listOf(
            CandidatePromptItem(
                question = "The secret to good conversation is",
                answer = "Active listening and not waiting for your turn to speak"
            )
        )
    )

    @Test
    fun testStandoutsFullProfileScoring() {
        val screenClues = HostCandidateReader.ScreenClues(
            visibleTexts = setOf(
                "Elena’s photo",
                "Elena",
                "28",
                "Elena, 28",
                "Product Designer",
                "Matcha lattes, vintage bookstores, and sunrise pottery sessions",
                "Prompt: My simple pleasures. Answer: Matcha lattes, vintage bookstores, and sunrise pottery sessions",
                "Northwestern"
            ),
            activeContext = HostCandidateReader.ScreenContext.STANDOUTS
        )

        val elenaScore = HostCandidateReader.scoreCandidate(standoutCandidate, screenClues)
        val mayaScore = HostCandidateReader.scoreCandidate(discoverCandidate, screenClues)

        assertTrue("Elena should have high positive score", elenaScore >= 1500)
        assertTrue("Maya should have low/zero or penalized score", mayaScore < 100)
        assertTrue("Elena should outscore Maya when viewing Standouts profile", elenaScore > mayaScore)
    }

    @Test
    fun testStandoutsCarouselCardScoring() {

        val screenClues = HostCandidateReader.ScreenClues(
            visibleTexts = setOf(
                "Elena",
                "My simple pleasures",
                "Matcha lattes, vintage bookstores, and sunrise pottery sessions"
            ),
            activeContext = HostCandidateReader.ScreenContext.STANDOUTS
        )

        val elenaScore = HostCandidateReader.scoreCandidate(standoutCandidate, screenClues)
        val mayaScore = HostCandidateReader.scoreCandidate(discoverCandidate, screenClues)

        assertTrue("Elena should score strongly from prompt answer and name", elenaScore >= 1200)
        assertTrue("Maya should score <= 0", mayaScore <= 0)
    }

    @Test
    fun testDiscoverProfileScoring() {
        val screenClues = HostCandidateReader.ScreenClues(
            visibleTexts = setOf(
                "Skip Maya",
                "Maya’s photo",
                "Maya, 25",
                "Bioinformatics Scientist",
                "Prompt: A shower thought I had recently. Answer: Turtles probably find humans terrifyingly fast at walking",
                "UW Seattle"
            ),
            activeContext = HostCandidateReader.ScreenContext.DISCOVER
        )

        val mayaScore = HostCandidateReader.scoreCandidate(discoverCandidate, screenClues)
        val elenaScore = HostCandidateReader.scoreCandidate(standoutCandidate, screenClues)

        assertTrue("Maya should score high on Discover page", mayaScore >= 1800)
        assertTrue("Elena should have zero/negative score on Discover page", elenaScore <= 0)
        assertTrue("Maya must outscore Elena on Discover", mayaScore > elenaScore)
    }

    @Test
    fun testIncomingLikeProfileScoring() {
        val screenClues = HostCandidateReader.ScreenClues(
            visibleTexts = setOf(
                "Sophia’s photo",
                "Sophia, 29",
                "Landscape Architect",
                "Active listening and not waiting for your turn to speak"
            ),
            activeContext = HostCandidateReader.ScreenContext.LIKES_YOU
        )

        val sophiaScore = HostCandidateReader.scoreCandidate(incomingLikeCandidate, screenClues)
        val elenaScore = HostCandidateReader.scoreCandidate(standoutCandidate, screenClues)
        val mayaScore = HostCandidateReader.scoreCandidate(discoverCandidate, screenClues)

        assertTrue("Sophia should score highest on Likes You page", sophiaScore >= 1500)
        assertTrue("Elena and Maya should have much lower score", sophiaScore > elenaScore && sophiaScore > mayaScore)
    }

    @Test
    fun testFuzzyPromptAnswerWordMatching() {

        val screenClues = HostCandidateReader.ScreenClues(
            visibleTexts = setOf(
                "pottery sessions and vintage bookstores"
            ),
            activeContext = HostCandidateReader.ScreenContext.UNKNOWN
        )

        val score = HostCandidateReader.scoreCandidate(standoutCandidate, screenClues)
        assertTrue("Should receive partial prompt match points for significant word overlap", score >= 600)
    }

    @Test
    fun testContextAlignmentPenaltyAndBonus() {
        val blankStandoutClues = HostCandidateReader.ScreenClues(
            visibleTexts = emptySet(),
            activeContext = HostCandidateReader.ScreenContext.STANDOUTS
        )

        val elenaScore = HostCandidateReader.scoreCandidate(standoutCandidate, blankStandoutClues)
        val mayaScore = HostCandidateReader.scoreCandidate(discoverCandidate, blankStandoutClues)

        assertEquals("Standout candidate should receive +100 context bonus", 100, elenaScore)
        assertEquals("Discover candidate should receive -100 context penalty on standouts page", -100, mayaScore)
    }
}
