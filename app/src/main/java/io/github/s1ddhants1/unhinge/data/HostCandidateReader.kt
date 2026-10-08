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

            val fallback = File("/data/data/co.hinge.app/databases/db")
            if (!fallback.exists()) {
                Log.w(Consts.TAG, "HostCandidateReader: Hinge DB does not exist at ${dbFile.absolutePath} or ${fallback.absolutePath}")
                return emptyList()
            }
        }

        val targetPath = if (dbFile.exists()) dbFile.absolutePath else "/data/data/co.hinge.app/databases/db"

        try {
            SQLiteDatabase.openDatabase(targetPath, null, SQLiteDatabase.OPEN_READONLY).use { db ->

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

                val promptsMap = mutableMapOf<String, MutableList<CandidatePromptItem>>()
                try {
                    db.rawQuery("SELECT userId, questionId, answerData FROM subject_answers ORDER BY position ASC", null).use { c ->
                        while (c.moveToNext()) {
                            val u = c.getString(0) ?: continue
                            val qId = c.getString(1) ?: ""
                            val qText = promptTitles[qId] ?: ""
                            val raw = c.getString(2) ?: ""
                            val resp = parsePromptAnswer(raw)
                            if (resp.isNotBlank()) {
                                promptsMap.getOrPut(u) { mutableListOf() }.add(
                                    CandidatePromptItem(question = qText, answer = resp)
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(Consts.TAG, "HostCandidateReader: Could not read subject_answers: ${e.message}")
                }

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

                val discoverOrder = mutableListOf<String>()
                try {
                    db.rawQuery("SELECT userId FROM discover_subject ORDER BY batchId ASC, positionInBatch ASC", null).use { c ->
                        while (c.moveToNext()) {
                            c.getString(0)?.let { if (!discoverOrder.contains(it)) discoverOrder.add(it) }
                        }
                    }
                } catch (_: Exception) {}

                val standoutOrder = mutableListOf<String>()
                val standoutSet = mutableSetOf<String>()
                try {
                    db.rawQuery("SELECT subjectId FROM standouts_content ORDER BY position ASC", null).use { c ->
                        while (c.moveToNext()) {
                            c.getString(0)?.let { id ->
                                if (standoutSet.add(id)) {
                                    standoutOrder.add(id)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

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

                val query = """
                    SELECT userId, firstName, age, height, hometown, location,
                           jobTitleText, datingIntentionText, relationshipTypeText,
                           religionText, ethnicitiesText, selfieVerified, circleMember,
                           educationHistoryText, employmentHistory, politicsText, smoking,
                           drinking, marijuana, drugs, kids, familyPlans, pet, zodiacSign, didJustJoin,
                           lastActiveStatusId
                    FROM profiles
                """.trimIndent()

                val profilesFound = mutableMapOf<String, CachedCandidateProfile>()
                db.rawQuery(query, null).use { c ->
                    while (c.moveToNext()) {
                        val uId = c.getString(0) ?: continue
                        val isIncoming = incomingLikes.containsKey(uId)
                        val activeStatusId = if (c.isNull(25)) null else c.getInt(25)
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
                            isStandout = standoutSet.contains(uId),
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
                            incomingLikeType = incomingLikes[uId] ?: "",
                            lastActiveStatusId = activeStatusId
                        )
                        profilesFound[uId] = p
                    }
                }

                for (id in discoverOrder) {
                    profilesFound.remove(id)?.let { candidates.add(it) }
                }

                for (id in standoutOrder) {
                    profilesFound.remove(id)?.let { candidates.add(it) }
                }

                for ((id, _) in incomingLikes) {
                    profilesFound.remove(id)?.let { candidates.add(it) }
                }

                val withPrompts = profilesFound.values.filter { it.prompts.isNotEmpty() }
                candidates.addAll(withPrompts)
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "HostCandidateReader: Error reading active candidates: ${e.message}", e)
        }

        return candidates
    }

    fun parsePromptAnswer(raw: String): String {
        if (raw.isBlank()) return ""
        try {
            val json = org.json.JSONObject(raw)
            val resp = json.optString("response")
            if (resp.isNotBlank()) {
                return resp
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
            }
        } catch (_: Throwable) {}

        val resp = Regex("\"response\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: raw
        return resp
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    enum class ScreenContext {
        DISCOVER,
        STANDOUTS,
        LIKES_YOU,
        UNKNOWN
    }

    data class ScreenClues(
        val visibleTexts: Set<String> = emptySet(),
        val activeContext: ScreenContext = ScreenContext.UNKNOWN
    )

    suspend fun readTargetCandidate(
        context: Context,
        screenClues: ScreenClues? = null
    ): CachedCandidateProfile? = withContext(Dispatchers.IO) {
        readTargetCandidateSync(context, screenClues)
    }

    fun readTargetCandidateSync(
        context: Context,
        screenClues: ScreenClues? = null
    ): CachedCandidateProfile? {
        val allCandidates = readActiveCandidatesSync(context)
        if (allCandidates.isEmpty()) return null

        if (screenClues != null && screenClues.visibleTexts.isNotEmpty()) {
            val scored = allCandidates.map { candidate ->
                candidate to scoreCandidate(candidate, screenClues)
            }.sortedByDescending { it.second }

            val top3 = scored.take(3).map { "${it.first.firstName} (id=${it.first.userId}, standout=${it.first.isStandout}, discover=${it.first.isDiscover}, score=${it.second})" }
            Log.i(Consts.TAG, "HostCandidateReader: Scored top candidates: $top3")

            val best = scored.firstOrNull()
            if (best != null && best.second > 0) {
                Log.i(Consts.TAG, "HostCandidateReader: Targeted candidate on screen: ${best.first.firstName} (${best.first.userId}) score=${best.second}")
                return best.first
            }
        }

        if (screenClues?.activeContext == ScreenContext.STANDOUTS) {
            val standout = allCandidates.firstOrNull { it.isStandout }
            if (standout != null) {
                Log.d(Consts.TAG, "HostCandidateReader: Falling back to first standout candidate: ${standout.firstName} (${standout.userId})")
                return standout
            }
        } else if (screenClues?.activeContext == ScreenContext.LIKES_YOU) {
            val incoming = allCandidates.firstOrNull { it.isIncomingLike }
            if (incoming != null) {
                Log.d(Consts.TAG, "HostCandidateReader: Falling back to first incoming like candidate: ${incoming.firstName} (${incoming.userId})")
                return incoming
            }
        }

        return readCurrentDiscoverCandidateSync(context) ?: allCandidates.firstOrNull()
    }

    fun scoreCandidate(
        candidate: CachedCandidateProfile,
        screenClues: ScreenClues
    ): Int {
        var score = 0
        val normalizedTexts = screenClues.visibleTexts
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }
        val fullBlob = normalizedTexts.joinToString(" ")
        val candidateName = candidate.firstName.trim().lowercase()

        for (prompt in candidate.prompts) {
            val ans = prompt.answer.trim().lowercase()
            if (ans.length >= 3) {
                if (fullBlob.contains(ans) || normalizedTexts.any { it.contains(ans) || (ans.contains(it) && it.length >= 10) }) {
                    score += 1000
                } else {
                    val words = ans.split(Regex("[^a-zA-Z0-9]+")).filter { it.length >= 4 }
                    if (words.size >= 2) {
                        val matched = words.count { fullBlob.contains(it) }
                        if (matched >= 2 && matched >= (words.size * 0.5)) {
                            score += 600
                        }
                    }
                }
            }

            val q = prompt.question.trim().lowercase()
            if (q.length >= 4 && fullBlob.contains(q)) {
                score += 200
            }
        }

        if (candidateName.isNotEmpty()) {
            val photoPattern = Regex("(?i)\\b${Regex.escape(candidateName)}['’]s\\s+photo")
            val skipPattern = Regex("(?i)\\bskip\\s+${Regex.escape(candidateName)}\\b")
            for (text in screenClues.visibleTexts) {
                if (photoPattern.containsMatchIn(text) || skipPattern.containsMatchIn(text)) {
                    score += 800
                    break
                }
            }

            if (screenClues.visibleTexts.any { it.trim().equals(candidate.firstName.trim(), ignoreCase = true) }) {
                score += 250
            }

            if (candidate.age > 0) {
                val nameAgePattern = Regex("(?i)\\b${Regex.escape(candidateName)},?\\s+${candidate.age}\\b")
                if (screenClues.visibleTexts.any { nameAgePattern.containsMatchIn(it) }) {
                    score += 400
                }
            }
        }

        if (candidate.jobTitle.isNotBlank() && candidate.jobTitle.length >= 3) {
            if (fullBlob.contains(candidate.jobTitle.trim().lowercase())) {
                score += 150
            }
        }
        if (candidate.school.isNotBlank() && candidate.school.length >= 3) {
            if (fullBlob.contains(candidate.school.trim().lowercase())) {
                score += 150
            }
        }
        if (candidate.location.isNotBlank() && candidate.location.length >= 4) {
            if (fullBlob.contains(candidate.location.trim().lowercase())) {
                score += 100
            }
        }

        when (screenClues.activeContext) {
            ScreenContext.STANDOUTS -> {
                if (candidate.isStandout) score += 100
                if (candidate.isDiscover) score -= 100
            }
            ScreenContext.DISCOVER -> {
                if (candidate.isDiscover) score += 100
                if (candidate.isStandout) score -= 100
            }
            ScreenContext.LIKES_YOU -> {
                if (candidate.isIncomingLike) score += 100
            }
            ScreenContext.UNKNOWN -> {}
        }

        return score
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

                if (currentUserId.isNullOrBlank()) {
                    try {
                        db.rawQuery("SELECT subjectId FROM impressions LIMIT 1", null).use { c ->
                            if (c.moveToFirst()) {
                                currentUserId = c.getString(0)
                            }
                        }
                    } catch (_: Exception) {}
                }

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
                    val resp = parsePromptAnswer(raw)
                    if (resp.isNotBlank()) {
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

        var isStandout = false
        try {
            db.rawQuery("SELECT 1 FROM standouts_content WHERE subjectId = ? LIMIT 1", arrayOf(targetId)).use {
                isStandout = it.moveToFirst()
            }
        } catch (_: Exception) {}

        var isDiscover = false
        try {
            db.rawQuery("SELECT 1 FROM discover_subject WHERE userId = ? LIMIT 1", arrayOf(targetId)).use {
                isDiscover = it.moveToFirst()
            }
        } catch (_: Exception) {}

        val query = """
            SELECT userId, firstName, age, height, hometown, location,
                   jobTitleText, datingIntentionText, relationshipTypeText,
                   religionText, ethnicitiesText, selfieVerified, circleMember,
                   educationHistoryText, employmentHistory, politicsText, smoking,
                   drinking, marijuana, drugs, kids, familyPlans, pet, zodiacSign, didJustJoin,
                   lastActiveStatusId
            FROM profiles
            WHERE userId = ?
            LIMIT 1
        """.trimIndent()

        try {
            db.rawQuery(query, arrayOf(targetId)).use { c ->
                if (c.moveToFirst()) {
                    val activeStatusId = if (c.isNull(25)) null else c.getInt(25)
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
                        isStandout = isStandout,
                        isDiscover = isDiscover,
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
                        incomingLikeType = incomingType,
                        lastActiveStatusId = activeStatusId
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "HostCandidateReader: Error reading profile for $targetId: ${e.message}", e)
        }

        return null
    }
}
