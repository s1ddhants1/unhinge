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
    fun testFeedNavigatorNonDestructiveOffsetTransitions() {
        assertEquals(0, FeedNavigator.currentOffset)
        assertEquals(1, FeedNavigator.displayPosition)
        assertFalse(FeedNavigator.isNavigated)

        // Navigate forward
        assertTrue(FeedNavigator.navigateForward())
        assertEquals(1, FeedNavigator.currentOffset)
        assertEquals(2, FeedNavigator.displayPosition)
        assertTrue(FeedNavigator.isNavigated)

        // Navigate forward again
        assertTrue(FeedNavigator.navigateForward())
        assertEquals(2, FeedNavigator.currentOffset)
        assertEquals(3, FeedNavigator.displayPosition)
        assertTrue(FeedNavigator.isNavigated)

        // Navigate back
        assertTrue(FeedNavigator.navigateBack())
        assertEquals(1, FeedNavigator.currentOffset)
        assertEquals(2, FeedNavigator.displayPosition)
        assertTrue(FeedNavigator.isNavigated)

        // Navigate back to origin
        assertTrue(FeedNavigator.navigateBack())
        assertEquals(0, FeedNavigator.currentOffset)
        assertEquals(1, FeedNavigator.displayPosition)
        assertFalse(FeedNavigator.isNavigated)

        // Underflow prevented
        assertFalse(FeedNavigator.navigateBack())
        assertEquals(0, FeedNavigator.currentOffset)
    }

    @Test
    fun testFeedNavigatorReset() {
        FeedNavigator.navigateForward()
        FeedNavigator.navigateForward()
        FeedNavigator.navigateForward()
        assertEquals(3, FeedNavigator.currentOffset)
        assertTrue(FeedNavigator.isNavigated)

        FeedNavigator.reset()
        assertEquals(0, FeedNavigator.currentOffset)
        assertEquals(1, FeedNavigator.displayPosition)
        assertFalse(FeedNavigator.isNavigated)
    }

    @Test
    fun testFeedNavigationTargetQueryAllowsPendingRatingsFilter() {
        val filteredQuery = "SELECT userId FROM discover_subject WHERE userId NOT IN (SELECT subjectId FROM pending_ratings WHERE subjectId IS NOT NULL) ORDER BY batchId ASC LIMIT 1"
        assertTrue(HostFeedNavigationHook.isTargetQuery(filteredQuery))

        val modified = HostFeedNavigationHook.injectOffset(filteredQuery, 2)
        assertEquals("SELECT userId FROM discover_subject WHERE userId NOT IN (SELECT subjectId FROM pending_ratings WHERE subjectId IS NOT NULL) ORDER BY batchId ASC LIMIT 1 OFFSET 2", modified)
    }
}
