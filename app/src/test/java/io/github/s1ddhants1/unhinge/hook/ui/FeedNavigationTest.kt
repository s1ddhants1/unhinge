package io.github.s1ddhants1.unhinge.hook.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeedNavigationTest {

    @Before
    fun setUp() {
        FeedNavigator.reset()
    }

    @Test
    fun testIsTargetQueryIdentifiesDiscoverSubjectSelects() {
        val query = "SELECT userId FROM discover_subject ORDER BY batchId ASC, positionInBatch ASC LIMIT 1"
        assertTrue(HostFeedNavigationHook.isTargetQuery(query))

        val roomQueryWithComment = "/* Room query */ SELECT userId FROM discover_subject LIMIT 1"
        assertTrue(HostFeedNavigationHook.isTargetQuery(roomQueryWithComment))

        val hingeCandidateListQuery = """
            SELECT
                discover_subject.userId AS subjectId,
                profiles.firstName AS firstName,
                subject_media.*
            FROM discover_subject
            INNER JOIN discover_batch ON discover_subject.batchId = discover_batch.id
            INNER JOIN profiles USING(userId)
            LEFT JOIN subject_media USING(userId)
            WHERE
                discover_batch.feedId = ? AND
                discover_batch.expiresAt > ? AND
                discover_batch.filters = ? AND
                profiles.state = 1 AND
                NOT EXISTS (
                    SELECT 1
                    FROM pending_ratings
                    WHERE
                        discover_subject.userId = pending_ratings.subjectId AND
                        (sentTime IS NULL OR sentTime > ?)
                )
            ORDER BY discover_batch.id ASC, discover_subject.positionInBatch ASC
        """.trimIndent()
        assertTrue(HostFeedNavigationHook.isTargetQuery(hingeCandidateListQuery))

        val countQuery = "SELECT COUNT(*) FROM discover_subject"
        assertFalse(HostFeedNavigationHook.isTargetQuery(countQuery))

        val existsQuery = "SELECT EXISTS(SELECT 1 FROM pending_ratings INNER JOIN discover_subject ON pending_ratings.subjectId = discover_subject.userId LIMIT 1)"
        assertFalse(HostFeedNavigationHook.isTargetQuery(existsQuery))

        val distinctQuery = "SELECT DISTINCT userId FROM discover_subject"
        assertFalse(HostFeedNavigationHook.isTargetQuery(distinctQuery))

        val distinctWithOrder = "SELECT DISTINCT discover_subject.userId FROM discover_subject INNER JOIN discover_batch ON discover_subject.batchId = discover_batch.id ORDER BY discover_batch.id ASC, discover_subject.positionInBatch ASC"
        assertTrue(HostFeedNavigationHook.isTargetQuery(distinctWithOrder))

        val pointLookupQuery = "SELECT * FROM discover_subject WHERE userId = ? LIMIT 1"
        assertFalse(HostFeedNavigationHook.isTargetQuery(pointLookupQuery))

        val pointLookupWithTable = "SELECT * FROM discover_subject WHERE discover_subject.userId = ? LIMIT 1"
        assertFalse(HostFeedNavigationHook.isTargetQuery(pointLookupWithTable))

        val pointLookupWithBackticks = "SELECT * FROM `discover_subject` WHERE `discover_subject`.`userId` = ? LIMIT 1"
        assertFalse(HostFeedNavigationHook.isTargetQuery(pointLookupWithBackticks))

        val otherTableQuery = "SELECT * FROM profiles WHERE userId = '123'"
        assertFalse(HostFeedNavigationHook.isTargetQuery(otherTableQuery))

        val updateQuery = "UPDATE discover_subject SET batchId = batchId"
        assertFalse(HostFeedNavigationHook.isTargetQuery(updateQuery))
    }

    @Test
    fun testInjectOffsetStandardLimit() {
        val sql = "SELECT userId FROM discover_subject ORDER BY batchId ASC LIMIT 1"
        val modified = HostFeedNavigationHook.injectOffset(sql, 2)
        assertEquals("SELECT userId FROM discover_subject ORDER BY batchId ASC LIMIT 1 OFFSET 2", modified)
    }

    @Test
    fun testInjectOffsetParameterizedLimit() {
        val sql = "SELECT userId FROM discover_subject ORDER BY batchId ASC LIMIT ?"
        val modified = HostFeedNavigationHook.injectOffset(sql, 2)
        assertEquals("SELECT userId FROM discover_subject ORDER BY batchId ASC LIMIT ? OFFSET 2", modified)
    }

    @Test
    fun testInjectOffsetParameterizedLimitWithTrailingSemicolon() {
        val sql = "SELECT userId FROM discover_subject ORDER BY batchId ASC LIMIT ?;"
        val modified = HostFeedNavigationHook.injectOffset(sql, 3)
        assertEquals("SELECT userId FROM discover_subject ORDER BY batchId ASC LIMIT ? OFFSET 3;", modified)
    }

    @Test
    fun testInjectOffsetWithTrailingSemicolon() {
        val sql = "SELECT userId FROM discover_subject ORDER BY batchId ASC LIMIT 1;"
        val modified = HostFeedNavigationHook.injectOffset(sql, 3)
        assertEquals("SELECT userId FROM discover_subject ORDER BY batchId ASC LIMIT 1 OFFSET 3;", modified)
    }

    @Test
    fun testInjectOffsetWithExistingOffset() {
        val sql = "SELECT userId FROM discover_subject LIMIT 1 OFFSET 0"
        val modified = HostFeedNavigationHook.injectOffset(sql, 5)
        assertEquals("SELECT userId FROM discover_subject LIMIT 1 OFFSET 5", modified)
    }

    @Test
    fun testInjectOffsetWithoutLimit() {
        val sql = "SELECT userId FROM discover_subject ORDER BY batchId ASC, positionInBatch ASC"
        val modified = HostFeedNavigationHook.injectOffset(sql, 1)
        assertEquals("SELECT userId FROM discover_subject ORDER BY batchId ASC, positionInBatch ASC LIMIT -1 OFFSET 1", modified)
    }

    @Test
    fun testInjectOffsetPreservesSubquery() {
        val sql = "SELECT userId FROM discover_subject WHERE userId NOT IN (SELECT subjectId FROM pending_ratings WHERE subjectId IS NOT NULL) ORDER BY batchId ASC LIMIT 1;"
        val modified = HostFeedNavigationHook.injectOffset(sql, 4)
        val expected = "SELECT userId FROM discover_subject WHERE userId NOT IN (SELECT subjectId FROM pending_ratings WHERE subjectId IS NOT NULL) ORDER BY batchId ASC LIMIT 1 OFFSET 4;"
        assertEquals(expected, modified)
    }

    @Test
    fun testInjectOffsetZeroOrNegativeNoOp() {
        val sql = "SELECT userId FROM discover_subject LIMIT 1"
        assertEquals(sql, HostFeedNavigationHook.injectOffset(sql, 0))
        assertEquals(sql, HostFeedNavigationHook.injectOffset(sql, -1))
    }

    @Test
    fun testFeedNavigatorInitialState() {
        assertEquals(0, FeedNavigator.currentOffset)
        assertEquals(1, FeedNavigator.displayPosition)
        assertFalse(FeedNavigator.isNavigated)
        assertFalse(FeedNavigator.navigateBack())
    }

    @Test
    fun testVirtualSkipStackOperations() {
        assertEquals(0, FeedNavigator.currentOffset)
        assertEquals(1, FeedNavigator.displayPosition)
        assertFalse(FeedNavigator.isNavigated)

        FeedNavigator.onVirtualSkipInserted("user_1")
        assertEquals(1, FeedNavigator.currentOffset)
        assertEquals(2, FeedNavigator.displayPosition)
        assertTrue(FeedNavigator.isNavigated)

        FeedNavigator.onVirtualSkipInserted("user_2")
        assertEquals(2, FeedNavigator.currentOffset)
        assertEquals(3, FeedNavigator.displayPosition)

        FeedNavigator.onVirtualSkipInserted("user_2")
        assertEquals(2, FeedNavigator.currentOffset)

        FeedNavigator.reset()
        assertEquals(0, FeedNavigator.currentOffset)
        assertEquals(1, FeedNavigator.displayPosition)
        assertFalse(FeedNavigator.isNavigated)
    }

    @Test
    fun testPendingRatingsColumnParsing() {
        val sql = "INSERT OR REPLACE INTO `pending_ratings` (`id`,`subjectId`,`sessionId`,`rating`,`origin`,`ratingSource`,`token`,`hasPairing`,`created`,`content`,`initiatedWith`,`topPhotoContentId`,`data`,`sentTime`,`ratingId`,`sortType`,`hcmRunId`,`secondChanceEligible`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        val parenStart = sql.indexOf('(')
        val parenEnd = sql.indexOf(')', parenStart)
        val cols = sql.substring(parenStart + 1, parenEnd).split(',').map { it.trim().trim('`', '"', '[', ']', ' ') }

        val ratingSourceIdx = cols.indexOfFirst { it.equals("ratingSource", ignoreCase = true) }
        val sentTimeIdx = cols.indexOfFirst { it.equals("sentTime", ignoreCase = true) }
        val subjectIdIdx = cols.indexOfFirst { it.equals("subjectId", ignoreCase = true) }

        assertEquals(5, ratingSourceIdx)
        assertEquals(13, sentTimeIdx)
        assertEquals(1, subjectIdIdx)
    }

    @Test
    fun testFeedNavigationTargetQueryFilteringWithPendingRatings() {
        val rawDiscoverQuery = "SELECT userId FROM discover_subject ORDER BY batchId ASC, positionInBatch ASC LIMIT 1"
        assertTrue(HostFeedNavigationHook.isTargetQuery(rawDiscoverQuery))
        assertFalse(rawDiscoverQuery.contains("pending_ratings", ignoreCase = true))

        val filteredQuery = "SELECT userId FROM discover_subject WHERE userId NOT IN (SELECT subjectId FROM pending_ratings WHERE subjectId IS NOT NULL) ORDER BY batchId ASC LIMIT 1"
        assertTrue(HostFeedNavigationHook.isTargetQuery(filteredQuery))
        assertTrue(filteredQuery.contains("pending_ratings", ignoreCase = true))
    }

    @Test
    fun testVirtualSkipStackSequentialOrder() {
        FeedNavigator.reset()
        assertEquals(0, FeedNavigator.currentOffset)

        FeedNavigator.onVirtualSkipInserted("user_alpha")
        assertEquals(1, FeedNavigator.currentOffset)
        assertEquals(2, FeedNavigator.displayPosition)

        FeedNavigator.onVirtualSkipInserted("user_beta")
        assertEquals(2, FeedNavigator.currentOffset)
        assertEquals(3, FeedNavigator.displayPosition)

        FeedNavigator.onVirtualSkipInserted("user_gamma")
        assertEquals(3, FeedNavigator.currentOffset)
        assertEquals(4, FeedNavigator.displayPosition)

        FeedNavigator.reset()
        assertEquals(0, FeedNavigator.currentOffset)
        assertEquals(1, FeedNavigator.displayPosition)
        assertFalse(FeedNavigator.isNavigated)
    }

    @Test
    fun testConfirmBackNavigationSuccessPopsTop() {
        FeedNavigator.onVirtualSkipInserted("user_1")
        FeedNavigator.onVirtualSkipInserted("user_2")
        assertEquals(2, FeedNavigator.currentOffset)

        assertTrue(FeedNavigator.confirmBackNavigation("user_2", true))
        assertEquals(1, FeedNavigator.currentOffset)
        assertEquals(2, FeedNavigator.displayPosition)
    }

    @Test
    fun testConfirmBackNavigationFailureKeepsStack() {
        FeedNavigator.onVirtualSkipInserted("user_1")
        FeedNavigator.onVirtualSkipInserted("user_2")

        assertFalse(FeedNavigator.confirmBackNavigation("user_2", false))
        assertEquals(2, FeedNavigator.currentOffset)
        assertEquals(3, FeedNavigator.displayPosition)
    }

    @Test
    fun testConfirmBackNavigationRejectsBlankOrMissing() {
        assertFalse(FeedNavigator.confirmBackNavigation(null, true))
        assertFalse(FeedNavigator.confirmBackNavigation("", true))
        assertFalse(FeedNavigator.confirmBackNavigation("ghost", true))
        assertEquals(0, FeedNavigator.currentOffset)
    }

    @Test
    fun testConfirmBackNavigationDesyncRemovesOccurrence() {
        FeedNavigator.onVirtualSkipInserted("user_1")
        FeedNavigator.onVirtualSkipInserted("user_2")

        assertTrue(FeedNavigator.confirmBackNavigation("user_1", true))
        assertEquals(1, FeedNavigator.currentOffset)
    }

    @Test
    fun testSelectUnknownIdReturnsFirstUnknown() {
        val known = setOf("user_1", "user_2")
        assertEquals("user_3", FeedNavigator.selectUnknownId(known, listOf("user_2", "user_3", "user_4")))
    }

    @Test
    fun testSelectUnknownIdSkipsBlanksAndKnown() {
        val known = setOf("user_1")
        assertEquals("user_2", FeedNavigator.selectUnknownId(known, listOf("", "user_1", "user_2")))
        assertEquals(null, FeedNavigator.selectUnknownId(known, listOf("", "user_1")))
        assertEquals(null, FeedNavigator.selectUnknownId(known, emptyList()))
    }

    @Test
    fun testNavigateWithNullContextTouchesNothing() {
        assertFalse(FeedNavigator.navigateBack(null))
        assertFalse(FeedNavigator.navigateForward(null))
        assertEquals(0, FeedNavigator.currentOffset)
        assertFalse(FeedNavigator.isNavigated)
    }
}
