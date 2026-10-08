package io.github.s1ddhants1.unhinge.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.database.sqlite.transaction
import android.util.Log
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.model.CandidatePromptItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class CandidateRatingRecord(
    val status: String = "",
    val comment: String = ""
)

class CandidateArchiveDb(context: Context) : SQLiteOpenHelper(context, "candidate_archive.db", null, 3) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS archived_candidates (
                userId TEXT PRIMARY KEY,
                firstName TEXT,
                age INTEGER,
                height INTEGER,
                hometown TEXT,
                location TEXT,
                jobTitle TEXT,
                datingIntention TEXT,
                relationshipType TEXT,
                religion TEXT,
                ethnicity TEXT,
                isSelfieVerified INTEGER,
                isCircleMember INTEGER,
                isStandout INTEGER,
                isDiscover INTEGER,
                isLiveInFeed INTEGER,
                ratingStatus TEXT,
                likeComment TEXT,
                school TEXT,
                employer TEXT,
                politics TEXT,
                smoking TEXT,
                drinking TEXT,
                marijuana TEXT,
                drugs TEXT,
                kids TEXT,
                familyPlans TEXT,
                pet TEXT,
                zodiac TEXT,
                isNewHere INTEGER,
                isYourTypeLately INTEGER,
                isSecondChance INTEGER,
                isIncomingLike INTEGER,
                incomingComment TEXT,
                incomingLikeType TEXT,
                incomingTimestamp INTEGER,
                firstSeenTimestamp INTEGER,
                lastSeenTimestamp INTEGER,
                photosJson TEXT,
                promptsJson TEXT,
                lastActiveStatusId INTEGER DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_archived_live ON archived_candidates (isLiveInFeed)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_archived_lastSeen ON archived_candidates (lastSeenTimestamp)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        ensureColumns(db)
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        ensureColumns(db)
    }

    private fun ensureColumns(db: SQLiteDatabase) {
        val columns = listOf(
            "likeComment TEXT DEFAULT ''",
            "school TEXT DEFAULT ''",
            "employer TEXT DEFAULT ''",
            "politics TEXT DEFAULT ''",
            "smoking TEXT DEFAULT ''",
            "drinking TEXT DEFAULT ''",
            "marijuana TEXT DEFAULT ''",
            "drugs TEXT DEFAULT ''",
            "kids TEXT DEFAULT ''",
            "familyPlans TEXT DEFAULT ''",
            "pet TEXT DEFAULT ''",
            "zodiac TEXT DEFAULT ''",
            "isNewHere INTEGER DEFAULT 0",
            "isYourTypeLately INTEGER DEFAULT 0",
            "isSecondChance INTEGER DEFAULT 0",
            "isIncomingLike INTEGER DEFAULT 0",
            "incomingComment TEXT DEFAULT ''",
            "incomingLikeType TEXT DEFAULT ''",
            "incomingTimestamp INTEGER DEFAULT 0",
            "lastActiveStatusId INTEGER DEFAULT 0"
        )
        for (col in columns) {
            try {
                db.execSQL("ALTER TABLE archived_candidates ADD COLUMN $col")
            } catch (e: Exception) {

            }
        }
    }

    @Synchronized
    fun upsertAndMerge(
        liveCandidates: List<CachedCandidateProfile>,
        ratingsMap: Map<String, CandidateRatingRecord>
    ): List<CachedCandidateProfile> {
        val db = writableDatabase
        val now = System.currentTimeMillis()

        try {
            db.transaction {

                execSQL("UPDATE archived_candidates SET isLiveInFeed = 0")

                for (c in liveCandidates) {
                    var firstSeen = now
                    var existingRating = ""
                    var existingComment = ""

                    db.rawQuery("SELECT firstSeenTimestamp, ratingStatus, likeComment FROM archived_candidates WHERE userId = ?", arrayOf(c.userId)).use { cursor ->
                        if (cursor.moveToNext()) {
                            firstSeen = cursor.getLong(0)
                            existingRating = cursor.getString(1) ?: ""
                            existingComment = cursor.getString(2) ?: ""
                        }
                    }

                    val record = ratingsMap[c.userId]
                    val currentRating = record?.status?.ifBlank { c.ratingStatus.ifBlank { existingRating } }
                        ?: c.ratingStatus.ifBlank { existingRating }
                    val currentComment = record?.comment?.ifBlank { c.likeComment.ifBlank { existingComment } }
                        ?: c.likeComment.ifBlank { existingComment }

                    val values = ContentValues().apply {
                        put("userId", c.userId)
                        put("firstName", c.firstName)
                        put("age", c.age)
                        put("height", c.height)
                        put("hometown", c.hometown)
                        put("location", c.location)
                        put("jobTitle", c.jobTitle)
                        put("datingIntention", c.datingIntention)
                        put("relationshipType", c.relationshipType)
                        put("religion", c.religion)
                        put("ethnicity", c.ethnicity)
                        put("isSelfieVerified", if (c.isSelfieVerified) 1 else 0)
                        put("isCircleMember", if (c.isCircleMember) 1 else 0)
                        put("isStandout", if (c.isStandout) 1 else 0)
                        put("isDiscover", if (c.isDiscover) 1 else 0)
                        put("isLiveInFeed", 1)
                        put("ratingStatus", currentRating)
                        put("likeComment", currentComment)
                        put("school", c.school)
                        put("employer", c.employer)
                        put("politics", c.politics)
                        put("smoking", c.smoking)
                        put("drinking", c.drinking)
                        put("marijuana", c.marijuana)
                        put("drugs", c.drugs)
                        put("kids", c.kids)
                        put("familyPlans", c.familyPlans)
                        put("pet", c.pet)
                        put("zodiac", c.zodiac)
                        put("isNewHere", if (c.isNewHere) 1 else 0)
                        put("isYourTypeLately", if (c.isYourTypeLately) 1 else 0)
                        put("isSecondChance", if (c.isSecondChance) 1 else 0)
                        put("isIncomingLike", if (c.isIncomingLike) 1 else 0)
                        put("incomingComment", c.incomingComment)
                        put("incomingLikeType", c.incomingLikeType)
                        put("incomingTimestamp", c.incomingTimestamp)
                        put("firstSeenTimestamp", firstSeen)
                        put("lastSeenTimestamp", now)
                        put("photosJson", json.encodeToString(c.photos))
                        put("promptsJson", json.encodeToString(c.prompts))
                        put("lastActiveStatusId", c.lastActiveStatusId ?: 0)
                    }

                    db.insertWithOnConflict("archived_candidates", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                }

                for ((userId, record) in ratingsMap) {
                    if (record.status.isNotBlank() || record.comment.isNotBlank()) {
                        val cv = ContentValues().apply {
                            if (record.status.isNotBlank()) put("ratingStatus", record.status)
                            if (record.comment.isNotBlank()) put("likeComment", record.comment)
                        }
                        db.update("archived_candidates", cv, "userId = ?", arrayOf(userId))
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "CandidateArchiveDb: Error during upsertAndMerge", e)
        }

        return getAllArchived()
    }

    @Synchronized
    fun getAllArchived(): List<CachedCandidateProfile> {
        val result = mutableListOf<CachedCandidateProfile>()
        val db = readableDatabase

        try {
            db.rawQuery("""
                SELECT userId, firstName, age, height, hometown, location,
                       jobTitle, datingIntention, relationshipType, religion,
                       ethnicity, isSelfieVerified, isCircleMember, isStandout,
                       isDiscover, isLiveInFeed, ratingStatus, likeComment, firstSeenTimestamp,
                       lastSeenTimestamp, photosJson, promptsJson,
                       school, employer, politics, smoking, drinking, marijuana, drugs,
                       kids, familyPlans, pet, zodiac, isNewHere, isYourTypeLately,
                       isSecondChance, isIncomingLike, incomingComment, incomingLikeType, incomingTimestamp,
                       lastActiveStatusId
                FROM archived_candidates
                ORDER BY isLiveInFeed DESC, lastSeenTimestamp DESC
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val uId = c.getString(0) ?: continue
                    val photosRaw = c.getString(20) ?: "[]"
                    val promptsRaw = c.getString(21) ?: "[]"

                    val photos: List<String> = try {
                        json.decodeFromString(photosRaw)
                    } catch (e: Exception) { emptyList() }

                    val prompts: List<CandidatePromptItem> = try {
                        json.decodeFromString(promptsRaw)
                    } catch (e: Exception) { emptyList() }

                    result.add(
                        CachedCandidateProfile(
                            userId = uId,
                            firstName = c.getString(1) ?: "",
                            age = c.getInt(2),
                            height = c.getInt(3),
                            hometown = c.getString(4) ?: "",
                            location = c.getString(5) ?: "",
                            jobTitle = c.getString(6) ?: "",
                            datingIntention = c.getString(7) ?: "",
                            relationshipType = c.getString(8) ?: "",
                            religion = c.getString(9) ?: "",
                            ethnicity = c.getString(10) ?: "",
                            isSelfieVerified = c.getInt(11) == 1,
                            isCircleMember = c.getInt(12) == 1,
                            isStandout = c.getInt(13) == 1,
                            isDiscover = c.getInt(14) == 1,
                            isLiveInFeed = c.getInt(15) == 1,
                            ratingStatus = c.getString(16) ?: "",
                            likeComment = c.getString(17) ?: "",
                            firstSeenTimestamp = c.getLong(18),
                            lastSeenTimestamp = c.getLong(19),
                            photos = photos,
                            prompts = prompts,
                            school = c.getString(22) ?: "",
                            employer = c.getString(23) ?: "",
                            politics = c.getString(24) ?: "",
                            smoking = c.getString(25) ?: "",
                            drinking = c.getString(26) ?: "",
                            marijuana = c.getString(27) ?: "",
                            drugs = c.getString(28) ?: "",
                            kids = c.getString(29) ?: "",
                            familyPlans = c.getString(30) ?: "",
                            pet = c.getString(31) ?: "",
                            zodiac = c.getString(32) ?: "",
                            isNewHere = c.getInt(33) == 1,
                            isYourTypeLately = c.getInt(34) == 1,
                            isSecondChance = c.getInt(35) == 1,
                            isIncomingLike = c.getInt(36) == 1,
                            incomingComment = c.getString(37) ?: "",
                            incomingLikeType = c.getString(38) ?: "",
                            incomingTimestamp = c.getLong(39),
                            lastActiveStatusId = if (c.isNull(40) || c.getInt(40) == 0) null else c.getInt(40)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "CandidateArchiveDb: Error reading archived candidates", e)
        }

        return result
    }

    @Synchronized
    fun clearArchive() {
        val db = writableDatabase
        db.delete("archived_candidates", null, null)
    }

    @Synchronized
    fun getArchiveCount(): Int {
        val db = readableDatabase
        db.rawQuery("SELECT count(*) FROM archived_candidates", null).use {
            if (it.moveToNext()) return it.getInt(0)
        }
        return 0
    }

    @Synchronized
    fun upsertCandidates(candidates: List<CachedCandidateProfile>): Int {
        val db = writableDatabase
        var count = 0
        val now = System.currentTimeMillis()

        try {
            db.transaction {
                for (c in candidates) {
                    if (c.userId.isBlank()) continue
                    val values = ContentValues().apply {
                        put("userId", c.userId)
                        put("firstName", c.firstName)
                        put("age", c.age)
                        put("height", c.height)
                        put("hometown", c.hometown)
                        put("location", c.location)
                        put("jobTitle", c.jobTitle)
                        put("datingIntention", c.datingIntention)
                        put("relationshipType", c.relationshipType)
                        put("religion", c.religion)
                        put("ethnicity", c.ethnicity)
                        put("isSelfieVerified", if (c.isSelfieVerified) 1 else 0)
                        put("isCircleMember", if (c.isCircleMember) 1 else 0)
                        put("isStandout", if (c.isStandout) 1 else 0)
                        put("isDiscover", if (c.isDiscover) 1 else 0)
                        put("isLiveInFeed", if (c.isLiveInFeed) 1 else 0)
                        put("ratingStatus", c.ratingStatus)
                        put("likeComment", c.likeComment)
                        put("school", c.school)
                        put("employer", c.employer)
                        put("politics", c.politics)
                        put("smoking", c.smoking)
                        put("drinking", c.drinking)
                        put("marijuana", c.marijuana)
                        put("drugs", c.drugs)
                        put("kids", c.kids)
                        put("familyPlans", c.familyPlans)
                        put("pet", c.pet)
                        put("zodiac", c.zodiac)
                        put("isNewHere", if (c.isNewHere) 1 else 0)
                        put("isYourTypeLately", if (c.isYourTypeLately) 1 else 0)
                        put("isSecondChance", if (c.isSecondChance) 1 else 0)
                        put("isIncomingLike", if (c.isIncomingLike) 1 else 0)
                        put("incomingComment", c.incomingComment)
                        put("incomingLikeType", c.incomingLikeType)
                        put("incomingTimestamp", c.incomingTimestamp)
                        put("firstSeenTimestamp", if (c.firstSeenTimestamp > 0) c.firstSeenTimestamp else now)
                        put("lastSeenTimestamp", if (c.lastSeenTimestamp > 0) c.lastSeenTimestamp else now)
                        put("photosJson", json.encodeToString(c.photos))
                        put("promptsJson", json.encodeToString(c.prompts))
                    }
                    val rowId = db.insertWithOnConflict("archived_candidates", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    if (rowId != -1L) count++
                }
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "CandidateArchiveDb: Error during upsertCandidates", e)
        }
        return count
    }
}
