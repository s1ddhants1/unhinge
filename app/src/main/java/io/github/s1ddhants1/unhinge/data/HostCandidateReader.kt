package io.github.s1ddhants1.unhinge.data

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.model.CandidatePromptItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@SuppressLint("SdCardPath")
object HostCandidateReader {

    suspend fun readActiveCandidates(context: Context): List<CachedCandidateProfile> =
        withContext(Dispatchers.IO) {
            readActiveCandidatesSync(context)
        }

    fun readActiveCandidatesSync(context: Context): List<CachedCandidateProfile> {
        val candidates = mutableListOf<CachedCandidateProfile>()
        val dbFile = context.getDatabasePath("db")
        if (!dbFile.exists()) {
            // Try alternate common Hinge SQLite path
            val fallback = File("/data/data/co.hinge.app/databases/db")
            if (!fallback.exists()) {
                Log.w(Consts.TAG, "HostCandidateReader: Hinge DB does not exist at ${dbFile.absolutePath} or ${fallback.absolutePath}")
                return emptyList()
            }
        }

        val targetPath = if (dbFile.exists()) dbFile.absolutePath else "/data/data/co.hinge.app/databases/db"

        try {
            SQLiteDatabase.openDatabase(targetPath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                // 1. Read prompts question dictionary
                val promptTitles = mutableMapOf<String, String>()
                try {
                    db.rawQuery("SELECT id, prompt FROM prompts", null).use { c ->
                        while (c.moveToNext()) {
                            val id = c.getString(0)
                            val prompt = c.getString(1)
                            if (id != null && prompt != null) {
                                promptTitles[id] = prompt
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(Consts.TAG, "HostCandidateReader: Could not read prompts table: ${e.message}")
                }

                // 2. Read subject answers
                val promptsMap = mutableMapOf<String, MutableList<CandidatePromptItem>>()
                try {
                    db.rawQuery("SELECT userId, questionId, answerData FROM subject_answers ORDER BY position ASC", null).use { c ->
                        while (c.moveToNext()) {
                            val u = c.getString(0) ?: continue
                            val qId = c.getString(1) ?: ""
                            val qText = promptTitles[qId] ?: ""
                            val raw = c.getString(2) ?: ""
                            val resp = Regex("\"response\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                            if (!resp.isNullOrBlank()) {
                                promptsMap.getOrPut(u) { mutableListOf() }.add(
                                    CandidatePromptItem(question = qText, answer = resp)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(Consts.TAG, "HostCandidateReader: Could not read subject_answers: ${e.message}")
                }

                // 3. Read photos
                val photosMap = mutableMapOf<String, MutableList<String>>()
                try {
                    db.rawQuery("SELECT userId, photoUrl FROM subject_media ORDER BY position ASC", null).use { c ->
                        while (c.moveToNext()) {
                            val u = c.getString(0)
                            val p = c.getString(1)
                            if (u != null && p != null) {
                                photosMap.getOrPut(u) { mutableListOf() }.add(p)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(Consts.TAG, "HostCandidateReader: Could not read subject_media: ${e.message}")
                }

                // 4. Discover IDs
                val discoverOrder = mutableListOf<String>()
                try {
                    db.rawQuery("SELECT userId FROM discover_subject ORDER BY batchId ASC, positionInBatch ASC", null).use { c ->
                        while (c.moveToNext()) {
                            c.getString(0)?.let { if (!discoverOrder.contains(it)) discoverOrder.add(it) }
                        }
                    }
                } catch (_: Exception) {}

                // Standouts IDs
                val standoutIds = mutableSetOf<String>()
                try {
                    db.rawQuery("SELECT subjectId FROM standouts_content", null).use { c ->
                        while (c.moveToNext()) {
                            c.getString(0)?.let { standoutIds.add(it) }
                        }
                    }
                } catch (_: Exception) {}

                // 5. Incoming likes (Impressions)
                val incomingLikes = mutableMapOf<String, String>()
                try {
                    db.rawQuery("SELECT subjectId, initiatedWith FROM impressions", null).use { c ->
                        while (c.moveToNext()) {
                            val sId = c.getString(0) ?: continue
                            val with = c.getString(1) ?: "like"
                            incomingLikes[sId] = with
                        }
                    }
                } catch (_: Exception) {}

                // 6. Profiles query
                val query = """
                    SELECT userId, firstName, age, height, hometown, location, 
                           jobTitleText, datingIntentionText, relationshipTypeText, 
                           religionText, ethnicitiesText, selfieVerified, circleMember,
                           educationHistoryText, employmentHistory, politicsText, smoking,
                           drinking, marijuana, drugs, kids, familyPlans, pet, zodiacSign, didJustJoin
                    FROM profiles
                """.trimIndent()

                val profilesFound = mutableMapOf<String, CachedCandidateProfile>()
                db.rawQuery(query, null).use { c ->
                    while (c.moveToNext()) {
                        val uId = c.getString(0) ?: continue
                        val isIncoming = incomingLikes.containsKey(uId)
                        val p = CachedCandidateProfile(
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
                            photos = photosMap[uId] ?: emptyList(),
                            prompts = promptsMap[uId] ?: emptyList(),
                            isStandout = standoutIds.contains(uId),
                            isDiscover = discoverOrder.contains(uId),
                            isLiveInFeed = true,
                            school = c.getString(13) ?: "",
                            employer = c.getString(14) ?: "",
                            politics = c.getString(15) ?: "",
                            smoking = c.getString(16) ?: "",
                            drinking = c.getString(17) ?: "",
                            marijuana = c.getString(18) ?: "",
                            drugs = c.getString(19) ?: "",
                            kids = c.getString(20) ?: "",
                            familyPlans = c.getString(21) ?: "",
                            pet = c.getString(22) ?: "",
                            zodiac = c.getString(23) ?: "",
                            isNewHere = c.getInt(24) == 1,
                            isIncomingLike = isIncoming,
                            incomingLikeType = incomingLikes[uId] ?: ""
                        )
                        profilesFound[uId] = p
                    }
                }

                // Add in discover queue order first
                for (id in discoverOrder) {
                    profilesFound.remove(id)?.let { candidates.add(it) }
                }

                // Then standouts
                for (id in standoutIds) {
                    profilesFound.remove(id)?.let { candidates.add(it) }
                }

                // Then incoming likes
                for ((id, _) in incomingLikes) {
                    profilesFound.remove(id)?.let { candidates.add(it) }
                }

                // Then remaining profiles that have prompts
                val withPrompts = profilesFound.values.filter { it.prompts.isNotEmpty() }
                candidates.addAll(withPrompts)
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "HostCandidateReader: Error reading active candidates: ${e.message}", e)
        }

        return candidates
    }

    suspend fun readCurrentDiscoverCandidate(context: Context): CachedCandidateProfile? =
        withContext(Dispatchers.IO) {
            readCurrentDiscoverCandidateSync(context)
        }

    fun readCurrentDiscoverCandidateSync(context: Context): CachedCandidateProfile? {
        val dbFile = context.getDatabasePath("db")
        val targetPath = if (dbFile.exists()) dbFile.absolutePath else "/data/data/co.hinge.app/databases/db"
        if (!File(targetPath).exists()) {
            Log.w(Consts.TAG, "HostCandidateReader: Hinge DB does not exist at $targetPath")
            return null
        }

        try {
            SQLiteDatabase.openDatabase(targetPath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                var currentUserId: String? = null

                // 1. Check discover_subject not yet in pending_ratings
                try {
                    db.rawQuery(
                        """
                        SELECT userId FROM discover_subject 
                        WHERE userId NOT IN (SELECT subjectId FROM pending_ratings WHERE subjectId IS NOT NULL)
                        ORDER BY batchId ASC, positionInBatch ASC 
                        LIMIT 1
                        """.trimIndent(),
                        null
                    ).use { c ->
                        if (c.moveToFirst()) {
                            currentUserId = c.getString(0)
                        }
                    }
                } catch (e: Exception) {
                    Log.w(Consts.TAG, "HostCandidateReader: Query with pending_ratings failed: ${e.message}")
                }

                // 2. Fallback: first profile in discover_subject
                if (currentUserId.isNullOrBlank()) {
                    try {
                        db.rawQuery(
                            "SELECT userId FROM discover_subject ORDER BY batchId ASC, positionInBatch ASC LIMIT 1",
                            null
                        ).use { c ->
                            if (c.moveToFirst()) {
                                currentUserId = c.getString(0)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 3. Fallback: incoming likes (impressions)
                if (currentUserId.isNullOrBlank()) {
                    try {
                        db.rawQuery("SELECT subjectId FROM impressions LIMIT 1", null).use { c ->
                            if (c.moveToFirst()) {
                                currentUserId = c.getString(0)
                            }
                        }
                    } catch (_: Exception) {}
                }

                // 4. Fallback: standouts_content
                if (currentUserId.isNullOrBlank()) {
                    try {
                        db.rawQuery("SELECT subjectId FROM standouts_content LIMIT 1", null).use { c ->
                            if (c.moveToFirst()) {
                                currentUserId = c.getString(0)
                            }
                        }
                    } catch (_: Exception) {}
                }

                val targetId = currentUserId ?: return null
                return readSingleProfile(db, targetId)
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "HostCandidateReader: Error reading current discover candidate: ${e.message}", e)
            return null
        }
    }

    private fun readSingleProfile(db: SQLiteDatabase, targetId: String): CachedCandidateProfile? {
        val promptTitles = mutableMapOf<String, String>()
        try {
            db.rawQuery("SELECT id, prompt FROM prompts", null).use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val prompt = c.getString(1)
                    if (id != null && prompt != null) {
                        promptTitles[id] = prompt
                    }
                }
            }
        } catch (_: Exception) {}

        val prompts = mutableListOf<CandidatePromptItem>()
        try {
            db.rawQuery(
                "SELECT questionId, answerData FROM subject_answers WHERE userId = ? ORDER BY position ASC",
                arrayOf(targetId)
            ).use { c ->
                while (c.moveToNext()) {
                    val qId = c.getString(0) ?: ""
                    val qText = promptTitles[qId] ?: ""
                    val raw = c.getString(1) ?: ""
                    val resp = Regex("\"response\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1)
                    if (!resp.isNullOrBlank()) {
                        prompts.add(CandidatePromptItem(question = qText, answer = resp))
                    }
                }
            }
        } catch (_: Exception) {}

        val photos = mutableListOf<String>()
        try {
            db.rawQuery(
                "SELECT photoUrl FROM subject_media WHERE userId = ? ORDER BY position ASC",
                arrayOf(targetId)
            ).use { c ->
                while (c.moveToNext()) {
                    val p = c.getString(0)
                    if (!p.isNullOrBlank()) {
                        photos.add(p)
                    }
                }
            }
        } catch (_: Exception) {}

        var isIncoming = false
        var incomingType = ""
        try {
            db.rawQuery("SELECT initiatedWith FROM impressions WHERE subjectId = ?", arrayOf(targetId)).use { c ->
                if (c.moveToFirst()) {
                    isIncoming = true
                    incomingType = c.getString(0) ?: "like"
                }
            }
        } catch (_: Exception) {}

        val query = """
            SELECT userId, firstName, age, height, hometown, location, 
                   jobTitleText, datingIntentionText, relationshipTypeText, 
                   religionText, ethnicitiesText, selfieVerified, circleMember,
                   educationHistoryText, employmentHistory, politicsText, smoking,
                   drinking, marijuana, drugs, kids, familyPlans, pet, zodiacSign, didJustJoin
            FROM profiles
            WHERE userId = ?
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(query, arrayOf(targetId)).use { c ->
                if (c.moveToFirst()) {
                    return CachedCandidateProfile(
                        userId = targetId,
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
                        photos = photos,
                        prompts = prompts,
                        isStandout = false,
                        isDiscover = true,
                        isLiveInFeed = true,
                        school = c.getString(13) ?: "",
                        employer = c.getString(14) ?: "",
                        politics = c.getString(15) ?: "",
                        smoking = c.getString(16) ?: "",
                        drinking = c.getString(17) ?: "",
                        marijuana = c.getString(18) ?: "",
                        drugs = c.getString(19) ?: "",
                        kids = c.getString(20) ?: "",
                        familyPlans = c.getString(21) ?: "",
                        pet = c.getString(22) ?: "",
                        zodiac = c.getString(23) ?: "",
                        isNewHere = c.getInt(24) == 1,
                        isIncomingLike = isIncoming,
                        incomingLikeType = incomingType
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "HostCandidateReader: Error reading profile for $targetId: ${e.message}", e)
        }

        return null
    }
}
