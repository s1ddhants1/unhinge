package io.github.s1ddhants1.unhinge.data

import android.annotation.SuppressLint
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import android.util.Xml
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader

@SuppressLint("SdCardPath")
object SuStorageReader {
    private const val HINGE_PACKAGE = "co.hinge.app"
    private const val HINGE_SHARED_PREFS = "/data/data/$HINGE_PACKAGE/shared_prefs"
    private const val HINGE_DB_PATH = "/data/data/$HINGE_PACKAGE/databases/db"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun readCompleteData(context: Context): CompleteHingeData = withContext(Dispatchers.IO) {
        val isRoot = checkRoot()
        Log.d(Consts.TAG, "SuStorageReader: isRoot = $isRoot")
        if (!isRoot) {
            Log.w(Consts.TAG, "SuStorageReader: Root access not available, returning default state")
            return@withContext CompleteHingeData(isRootGranted = false)
        }

        // 1. Read all shared_prefs files
        val prefFiles = mutableListOf<RawPrefFile>()
        val defaultMap = mutableMapOf<String, Any>()
        val insightsMap = mutableMapOf<String, Any>()

        val fileListOutput = executeSu("ls -1 $HINGE_SHARED_PREFS")
        Log.d(Consts.TAG, "SuStorageReader: fileListOutput = $fileListOutput")
        val fileNames = fileListOutput?.lines()?.map { it.trim() }?.filter { it.endsWith(".xml") } ?: emptyList()
        Log.d(Consts.TAG, "SuStorageReader: fileNames found = $fileNames")

        for (fileName in fileNames) {
            val xmlContent = executeSu("cat $HINGE_SHARED_PREFS/$fileName")
            if (xmlContent == null) {
                Log.w(Consts.TAG, "SuStorageReader: failed to cat $fileName")
                continue
            }
            val (parsedMap, entries) = parseSharedPrefsXmlWithEntries(fileName, xmlContent)
            Log.d(Consts.TAG, "SuStorageReader: parsed $fileName, keys=${parsedMap.size}, entries=${entries.size}")
            prefFiles.add(RawPrefFile(fileName, entries))

            if (fileName == "default.xml") {
                defaultMap.putAll(parsedMap)
            } else if (fileName == "unhinge_insights.xml") {
                insightsMap.putAll(parsedMap)
            }
        }
        Log.d(Consts.TAG, "SuStorageReader: defaultMap has ${defaultMap.size} keys, firstName=${defaultMap["firstName"]}")

        // 2. Build RawHingeTelemetry
        val telemetry = RawHingeTelemetry(
            firstName = defaultMap["firstName"] as? String ?: "",
            identityId = defaultMap["identityId"] as? String ?: (defaultMap["lastIdentityId"] as? String ?: ""),
            metroArea = defaultMap["metroAreaV2"] as? String ?: "",
            billingCountryCode = defaultMap["billingCountryCode"] as? String ?: "",
            apiAvailableLikes = (defaultMap["apiAvailableLikes"] as? Number)?.toInt() ?: 8,
            localAvailableLikes = (defaultMap["localAvailableLikes"] as? Number)?.toInt() ?: 8,
            apiAvailableSuperLikes = (defaultMap["apiAvailableSuperLikes"] as? Number)?.toInt() ?: 0,
            localAvailableSuperlikes = (defaultMap["localAvailableSuperlikes"] as? Number)?.toInt() ?: 0,
            boostsAvailable = (defaultMap["boostsAvailable"] as? Number)?.toInt() ?: 0,
            apiAvailableSnoozes = (defaultMap["apiAvailableSnoozes"] as? Number)?.toInt() ?: 0,
            apiAvailableSkipUndos = (defaultMap["apiAvailableSkipUndos"] as? Number)?.toInt() ?: 0,
            apiAvailableAlcPriorityLikes = (defaultMap["apiAvailableAlcPriorityLikes"] as? Number)?.toInt() ?: 0,
            likesSentForPostLikeEncouragement = (defaultMap["likesSentForPostLikeEncouragement"] as? Number)?.toInt() ?: 0,
            sendLikesOnDiscoverCounter = (defaultMap["sendLikesOnDiscoverCounter"] as? Number)?.toInt() ?: 0,
            timesSeenSendRoseInsteadDialog = (defaultMap["timesSeenSendRoseInsteadDialog"] as? Number)?.toInt() ?: 0,
            totalBoostUpsellPresentations = (defaultMap["totalBoostUpsellPresentations"] as? Number)?.toInt() ?: 0,
            profileCompleteness = (defaultMap["profileCompleteness"] as? Number)?.toFloat() ?: 0f,
            isProfileComplete = defaultMap["isProfileComplete"] as? Boolean ?: false,
            profileRequiredPhotos = (defaultMap["profileRequiredPhotos"] as? Number)?.toInt() ?: 6,
            isAccountPaused = defaultMap["isAccountPaused"] as? Boolean ?: false,
            isSmartPhotoOptedIn = defaultMap["isSmartPhotoOptedIn"] as? Boolean ?: false,
            isCircleOptIn = defaultMap["isCircleOptIn"] as? Boolean ?: false,
            shouldShowVoicePromptContextualNudge = defaultMap["shouldShowVoicePromptContextualNudge"] as? Boolean ?: false,
            hasViewedPromptFeedback = defaultMap["hasViewedPromptFeedback"] as? Boolean ?: false,
            createdTimestamp = (defaultMap["created"] as? Number)?.toLong() ?: 0L,
            firstHingeSync = (defaultMap["firstHingeSync"] as? Number)?.toLong() ?: 0L,
            lastHingeSync = (defaultMap["lastHingeSync"] as? Number)?.toLong() ?: 0L,
            userProfileLastChanged = (defaultMap["userProfileLastChanged"] as? Number)?.toLong() ?: 0L,
            userPreferencesLastChanged = (defaultMap["userPreferencesLastChanged"] as? Number)?.toLong() ?: 0L,
            likesCacheTimestamp = (defaultMap["likesCache"] as? Number)?.toLong() ?: 0L,
            standoutsExpiration = (defaultMap["standouts.expiration"] as? Number)?.toLong() ?: 0L,
            userPermissions = (defaultMap["USER_PERMISSIONS"] as? Set<*>)?.mapNotNull { it?.toString() }?.sorted() ?: emptyList(),
            installId = defaultMap["installId"] as? String ?: "",
            discoverCacheEtag = defaultMap["discoverCache.eTag"] as? String ?: "",
            isRootGranted = true,
            lastReadTimestamp = System.currentTimeMillis()
        )

        // 3. Parse hook insights
        val dailyLikes = (insightsMap["daily_likes_json"] as? String)?.let {
            try { json.decodeFromString<DailyLikeStatus>(it) } catch (e: Exception) { null }
        } ?: DailyLikeStatus(
            likesLeft = telemetry.apiAvailableLikes,
            maxLikes = 8,
            freeRosesLeft = telemetry.apiAvailableSuperLikes
        )

        val incomingLikes = (insightsMap["likes_you_json"] as? String)?.let {
            try { json.decodeFromString<IncomingLikeInsight>(it) } catch (e: Exception) { null }
        } ?: IncomingLikeInsight()

        val profileAudit = (insightsMap["profile_audit_json"] as? String)?.let {
            try { json.decodeFromString<ProfileAuditReport>(it) } catch (e: Exception) { null }
        } ?: ProfileAuditReport(
            percentComplete = (telemetry.profileCompleteness * 100).toInt(),
            photoCount = if (telemetry.isProfileComplete) 6 else 0,
            hasVoicePrompt = !telemetry.shouldShowVoicePromptContextualNudge
        )

        val behavior = (insightsMap["behavior_json"] as? String)?.let {
            try { json.decodeFromString<SessionBehaviorMetrics>(it) } catch (e: Exception) { null }
        } ?: SessionBehaviorMetrics()

        // 4. Query SQLite database for player media, answers, candidate profiles, matches/chats, and table summaries
        val (playerMedia, playerAnswers, candidates, matches, dbTables) = readLocalDatabase(context)

        CompleteHingeData(
            telemetry = telemetry,
            dailyLikes = dailyLikes,
            incomingLikes = incomingLikes,
            profileAudit = profileAudit,
            behaviorMetrics = behavior,
            playerMedia = playerMedia,
            playerAnswers = playerAnswers,
            candidates = candidates,
            matches = matches,
            databaseTables = dbTables,
            allPrefFiles = prefFiles.sortedBy { it.fileName },
            isRootGranted = true,
            lastUpdatedTimestamp = System.currentTimeMillis()
        )
    }

    private fun checkRoot(): Boolean {
        return try {
            val p = ProcessBuilder("su", "-M", "-c", "id").start()
            val text = p.inputStream.bufferedReader().use { it.readText() }
            p.waitFor() == 0 && text.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    fun executeSu(command: String): String? {
        return try {
            val process = ProcessBuilder("su", "-M", "-c", command).start()
            val text = process.inputStream.bufferedReader().use { it.readText() }
            val err = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            Log.d(Consts.TAG, "executeSu: cmd='$command', exitCode=$exitCode, outLen=${text.length}, err='$err'")
            if (exitCode == 0) text.trim() else null
        } catch (e: Exception) {
            Log.e(Consts.TAG, "SuStorageReader: Failed to execute su command: $command", e)
            null
        }
    }

    private data class ParsedXmlResult(val map: Map<String, Any>, val entries: List<StoragePrefEntry>)

    private fun parseSharedPrefsXmlWithEntries(fileName: String, xmlString: String): ParsedXmlResult {
        val map = mutableMapOf<String, Any>()
        val entries = mutableListOf<StoragePrefEntry>()

        try {
            val parser = Xml.newPullParser()
            parser.setInput(StringReader(xmlString))
            var eventType = parser.eventType
            var currentTag: String? = null
            var currentName: String? = null
            var currentSet: MutableSet<String>? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        currentTag = parser.name
                        when (currentTag) {
                            "string" -> {
                                val name = parser.getAttributeValue(null, "name")
                                if (currentSet != null) {
                                    val text = parser.nextText()
                                    currentSet.add(text)
                                } else if (name != null) {
                                    val text = parser.nextText()
                                    map[name] = text
                                    entries.add(StoragePrefEntry(fileName, name, "STRING", text))
                                }
                            }
                            "int" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val value = parser.getAttributeValue(null, "value")?.toIntOrNull()
                                if (name != null && value != null) {
                                    map[name] = value
                                    entries.add(StoragePrefEntry(fileName, name, "INT", value.toString()))
                                }
                            }
                            "long" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val value = parser.getAttributeValue(null, "value")?.toLongOrNull()
                                if (name != null && value != null) {
                                    map[name] = value
                                    entries.add(StoragePrefEntry(fileName, name, "LONG", value.toString()))
                                }
                            }
                            "float" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val value = parser.getAttributeValue(null, "value")?.toFloatOrNull()
                                if (name != null && value != null) {
                                    map[name] = value
                                    entries.add(StoragePrefEntry(fileName, name, "FLOAT", value.toString()))
                                }
                            }
                            "boolean" -> {
                                val name = parser.getAttributeValue(null, "name")
                                val value = parser.getAttributeValue(null, "value")?.toBooleanStrictOrNull()
                                if (name != null && value != null) {
                                    map[name] = value
                                    entries.add(StoragePrefEntry(fileName, name, "BOOLEAN", value.toString()))
                                }
                            }
                            "set" -> {
                                currentName = parser.getAttributeValue(null, "name")
                                currentSet = mutableSetOf()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        val name = currentName
                        val set = currentSet
                        if (parser.name == "set" && name != null && set != null) {
                            map[name] = set
                            entries.add(StoragePrefEntry(fileName, name, "SET", set.joinToString(", ")))
                            currentName = null
                            currentSet = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "SuStorageReader: Failed to parse XML for $fileName", e)
        }
        return ParsedXmlResult(map, entries)
    }

    private data class DbExtractResult(
        val playerMedia: List<PlayerMediaItem>,
        val playerAnswers: List<PlayerAnswerItem>,
        val candidates: List<CachedCandidateProfile>,
        val matches: List<MatchRecord>,
        val dbTables: List<DatabaseTableSummary>
    )

    private fun readLocalDatabase(context: Context): DbExtractResult {
        val playerMedia = mutableListOf<PlayerMediaItem>()
        val playerAnswers = mutableListOf<PlayerAnswerItem>()
        val candidates = mutableListOf<CachedCandidateProfile>()
        val matches = mutableListOf<MatchRecord>()
        val dbTables = mutableListOf<DatabaseTableSummary>()

        try {
            val localDb = File(context.filesDir, "hinge_local.sqlite")
            localDb.parentFile?.mkdirs()
            // Copy Hinge database to app files directory with safe permissions
            val copyCmd = "cp $HINGE_DB_PATH ${localDb.absolutePath} && cp $HINGE_DB_PATH-wal ${localDb.absolutePath}-wal 2>/dev/null; chmod 666 ${localDb.absolutePath}* 2>/dev/null"
            val copyResult = executeSu(copyCmd)
            Log.d(Consts.TAG, "SuStorageReader: db copy result=$copyResult, localDb exists=${localDb.exists()}, size=${localDb.length()}")

            if (!localDb.exists()) {
                val archived = CandidateArchiveDb(context).getAllArchived()
                return DbExtractResult(playerMedia, playerAnswers, archived, matches, dbTables)
            }

            SQLiteDatabase.openDatabase(localDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                // 1. Table Summaries
                try {
                    db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name ASC", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(0) ?: continue
                            try {
                                db.rawQuery("SELECT COUNT(*) FROM \"$name\"", null).use { countCursor ->
                                    if (countCursor.moveToNext()) {
                                        dbTables.add(DatabaseTableSummary(name, countCursor.getInt(0)))
                                    }
                                }
                            } catch (e: Exception) {
                                dbTables.add(DatabaseTableSummary(name, 0))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(Consts.TAG, "SuStorageReader: Error reading table list", e)
                }

                // 2. Player Media (User's photos)
                try {
                    db.rawQuery("SELECT position, photoUrl, description, mediaPromptText FROM player_media ORDER BY position ASC", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            playerMedia.add(
                                PlayerMediaItem(
                                    position = cursor.getInt(0),
                                    photoUrl = cursor.getString(1) ?: "",
                                    description = cursor.getString(2) ?: "",
                                    promptCaption = cursor.getString(3) ?: ""
                                )
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e(Consts.TAG, "SuStorageReader: Error reading player_media", e)
                }

                // 2.5 Prompt Question Titles (id -> prompt question text)
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
                    Log.e(Consts.TAG, "SuStorageReader: Error reading prompts table", e)
                }

                // 3. Player Answers (User's prompts)
                try {
                    db.rawQuery("SELECT questionId, answerData FROM player_answers ORDER BY position ASC", null).use { cursor ->
                        while (cursor.moveToNext()) {
                            val qId = cursor.getString(0) ?: ""
                            val qText = promptTitles[qId] ?: ""
                            val raw = cursor.getString(1) ?: ""
                            val responseText = HostCandidateReader.parsePromptAnswer(raw)
                            val contentId = Regex("\"contentId\"\\s*:\\s*\"([^\"]+)\"").find(raw)?.groupValues?.get(1) ?: ""
                            playerAnswers.add(PlayerAnswerItem(qId, qText, responseText, contentId))
                        }
                    }
                } catch (e: Exception) {
                    Log.e(Consts.TAG, "SuStorageReader: Error reading player_answers", e)
                }

                // 4. Candidate Profiles (Discover & Standouts Queue)
                try {
                    val discoverIds = mutableSetOf<String>()
                    try {
                        db.rawQuery("SELECT userId FROM discover_subject", null).use { c ->
                            while (c.moveToNext()) c.getString(0)?.let { discoverIds.add(it) }
                        }
                    } catch (e: Exception) { }

                    val standoutIds = mutableSetOf<String>()
                    try {
                        db.rawQuery("SELECT subjectId FROM standouts_content", null).use { c ->
                            while (c.moveToNext()) c.getString(0)?.let { standoutIds.add(it) }
                        }
                    } catch (e: Exception) { }

                    // Incoming Likes (Impressions)
                    data class IncomingLikeInfo(
                        val timestamp: Long,
                        val type: String,
                        val showMatchNote: Boolean
                    )
                    val incomingLikesMap = mutableMapOf<String, IncomingLikeInfo>()
                    try {
                        db.rawQuery("SELECT subjectId, initiatedMatch, initiatedWith, showMatchNote FROM impressions", null).use { c ->
                            while (c.moveToNext()) {
                                val sId = c.getString(0) ?: continue
                                incomingLikesMap[sId] = IncomingLikeInfo(
                                    timestamp = c.getLong(1),
                                    type = c.getString(2) ?: "like",
                                    showMatchNote = c.getInt(3) == 1
                                )
                            }
                        }
                    } catch (e: Exception) { }

                    // Algorithmic signals
                    val yourTypeMap = mutableMapOf<String, Boolean>()
                    try {
                        db.rawQuery("SELECT userId, yourTypeLately FROM curated_discover_data", null).use { c ->
                            while (c.moveToNext()) {
                                val uId = c.getString(0) ?: continue
                                yourTypeMap[uId] = c.getInt(1) == 1
                            }
                        }
                    } catch (e: Exception) { }

                    val secondChanceSet = mutableSetOf<String>()
                    try {
                        db.rawQuery("SELECT userId FROM second_chance_data", null).use { c ->
                            while (c.moveToNext()) {
                                c.getString(0)?.let { secondChanceSet.add(it) }
                            }
                        }
                    } catch (e: Exception) { }

                    val ratingsMap = mutableMapOf<String, CandidateRatingRecord>()
                    try {
                        db.rawQuery("SELECT subjectId, rating, content, data FROM pending_ratings", null).use { c ->
                            while (c.moveToNext()) {
                                val sId = c.getString(0) ?: continue
                                val r = c.getString(1) ?: ""
                                val content = c.getString(2) ?: ""
                                val data = c.getString(3) ?: ""
                                val status = if (r.equals("skip", true)) "Passed"
                                else if (r.equals("like", true) || r.equals("note", true)) "Liked"
                                else r

                                var comment = ""
                                if (content.isNotBlank()) {
                                    val match = Regex("\"comment\"\\s*:\\s*\"([^\"]+)\"").find(content)
                                    if (match != null) {
                                        comment = match.groupValues[1]
                                    }
                                }
                                if (comment.isBlank() && data.isNotBlank()) {
                                    val match = Regex("\"comment\"\\s*:\\s*\"([^\"]+)\"").find(data)
                                    if (match != null) {
                                        comment = match.groupValues[1]
                                    }
                                }

                                ratingsMap[sId] = CandidateRatingRecord(status = status, comment = comment)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(Consts.TAG, "SuStorageReader: Error reading pending_ratings", e)
                    }

                    // Also check liked_content table for incoming and outgoing comments
                    val likedContentMap = mutableMapOf<String, String>()
                    try {
                        db.rawQuery("SELECT userId, comment FROM liked_content WHERE comment IS NOT NULL AND comment != ''", null).use { c ->
                            while (c.moveToNext()) {
                                val uId = c.getString(0) ?: continue
                                val comment = c.getString(1) ?: ""
                                if (comment.isNotBlank()) {
                                    likedContentMap[uId] = comment
                                    val existing = ratingsMap[uId]
                                    ratingsMap[uId] = CandidateRatingRecord(
                                        status = existing?.status?.ifBlank { "Liked" } ?: "Liked",
                                        comment = comment
                                    )
                                }
                            }
                        }
                    } catch (e: Exception) { }

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
                    } catch (e: Exception) { }

                    val promptsMap = mutableMapOf<String, MutableList<CandidatePromptItem>>()
                    try {
                        db.rawQuery("SELECT userId, questionId, answerData FROM subject_answers ORDER BY position ASC", null).use { c ->
                            while (c.moveToNext()) {
                                val u = c.getString(0)
                                val qId = c.getString(1) ?: ""
                                val qText = promptTitles[qId] ?: ""
                                val raw = c.getString(2) ?: ""
                                val resp = HostCandidateReader.parsePromptAnswer(raw)
                                if (u != null && resp.isNotBlank()) {
                                    promptsMap.getOrPut(u) { mutableListOf() }.add(CandidatePromptItem(question = qText, answer = resp))
                                }
                            }
                        }
                    } catch (e: Exception) { }

                    db.rawQuery("""
                        SELECT userId, firstName, age, height, hometown, location, 
                               jobTitleText, datingIntentionText, relationshipTypeText, 
                               religionText, ethnicitiesText, selfieVerified, circleMember,
                               educationHistoryText, employmentHistory, politicsText, smoking,
                               drinking, marijuana, drugs, kids, familyPlans, pet, zodiacSign, didJustJoin
                        FROM profiles
                    """.trimIndent(), null).use { c ->
                        while (c.moveToNext()) {
                            val uId = c.getString(0) ?: continue
                            val ratingRec = ratingsMap[uId]
                            val incomingInfo = incomingLikesMap[uId]
                            val isIncoming = incomingInfo != null
                            val incomingComment = if (isIncoming) likedContentMap[uId] ?: "" else ""

                            candidates.add(
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
                                    photos = photosMap[uId] ?: emptyList(),
                                    prompts = promptsMap[uId] ?: emptyList(),
                                    isStandout = standoutIds.contains(uId),
                                    isDiscover = discoverIds.contains(uId),
                                    isLiveInFeed = true,
                                    ratingStatus = ratingRec?.status ?: "",
                                    likeComment = ratingRec?.comment ?: "",
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
                                    isYourTypeLately = yourTypeMap[uId] ?: false,
                                    isSecondChance = secondChanceSet.contains(uId),
                                    isIncomingLike = isIncoming,
                                    incomingComment = incomingComment,
                                    incomingLikeType = incomingInfo?.type ?: "",
                                    incomingTimestamp = incomingInfo?.timestamp ?: 0L
                                )
                            )
                        }
                    }

                    // 5. Ingest into persistent archive and retrieve complete historical records
                    try {
                        val archiveDb = CandidateArchiveDb(context)
                        val merged = archiveDb.upsertAndMerge(candidates, ratingsMap)
                        candidates.clear()
                        candidates.addAll(merged)
                    } catch (e: Exception) {
                        Log.e(Consts.TAG, "SuStorageReader: Error during archive merge", e)
                    }
                } catch (e: Exception) {
                    Log.e(Consts.TAG, "SuStorageReader: Error reading candidate profiles", e)
                }

                // 6. Matches & Chat Messages Pipeline
                try {
                    val transcriptsMap = mutableMapOf<String, String>()
                    try {
                        db.rawQuery("SELECT messageId, transcript FROM chat_voice_note_audio_transcript", null).use { c ->
                            while (c.moveToNext()) {
                                val mId = c.getString(0) ?: continue
                                val t = c.getString(1) ?: ""
                                if (t.isNotBlank()) transcriptsMap[mId] = t
                            }
                        }
                    } catch (e: Exception) { }

                    val messagesMap = mutableMapOf<String, MutableList<ChatMessageRecord>>()
                    try {
                        db.rawQuery("""
                            SELECT localId, subjectId, body, created, sentBySubject, unread, 
                                   subjectReactions, playerReactions, voiceNoteData 
                            FROM chat_messages 
                            ORDER BY created ASC
                        """.trimIndent(), null).use { c ->
                            while (c.moveToNext()) {
                                val mId = c.getString(0) ?: continue
                                val sId = c.getString(1) ?: continue
                                val body = c.getString(2) ?: ""
                                val created = c.getLong(3)
                                val sentBySub = c.getInt(4) == 1
                                val unread = c.getInt(5) == 1
                                val sReactions = c.getString(6) ?: ""
                                val pReactions = c.getString(7) ?: ""
                                val voiceData = c.getString(8) ?: ""
                                val hasVoice = voiceData.isNotBlank()
                                val reactions = when {
                                    sReactions.isNotBlank() && pReactions.isNotBlank() -> "$sReactions | $pReactions"
                                    sReactions.isNotBlank() -> sReactions
                                    else -> pReactions
                                }
                                val transcript = transcriptsMap[mId] ?: ""

                                messagesMap.getOrPut(sId) { mutableListOf() }.add(
                                    ChatMessageRecord(
                                        localId = mId,
                                        subjectId = sId,
                                        body = body,
                                        createdTimestamp = created,
                                        sentBySubject = sentBySub,
                                        unread = unread,
                                        reactions = reactions,
                                        isVoiceNote = hasVoice,
                                        voiceTranscript = transcript
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(Consts.TAG, "SuStorageReader: Error reading chat_messages", e)
                    }

                    val draftMap = mutableMapOf<String, String>()
                    try {
                        db.rawQuery("SELECT subjectId, body FROM draft_messages", null).use { c ->
                            while (c.moveToNext()) {
                                val sId = c.getString(0) ?: continue
                                val body = c.getString(1) ?: ""
                                if (body.isNotBlank()) draftMap[sId] = body
                            }
                        }
                    } catch (e: Exception) { }

                    val subjectPhotosMap = mutableMapOf<String, String>()
                    try {
                        db.rawQuery("SELECT userId, photoUrl FROM subject_media WHERE position = 0", null).use { c ->
                            while (c.moveToNext()) {
                                val u = c.getString(0)
                                val p = c.getString(1)
                                if (u != null && p != null) subjectPhotosMap[u] = p
                            }
                        }
                    } catch (e: Exception) { }

                    try {
                        db.rawQuery("""
                            SELECT m.subjectId, m.playerInitiated, m.reciprocatedMatch, m.initiatedMatch, 
                                   m.initiatedWith, m.originatedFromPriorityLike, m.phoneNumberExchanged, 
                                   m.socialMediaExchanged, m.replyNudgeDaysSinceSubjectMessage, 
                                   m.isHidden, m.chatEnded, m.chatEndedByMe,
                                   p.firstName, p.age, p.jobTitleText
                            FROM matches m
                            LEFT JOIN profiles p ON m.subjectId = p.userId
                            ORDER BY m.reciprocatedMatch DESC
                        """.trimIndent(), null).use { c ->
                            while (c.moveToNext()) {
                                val sId = c.getString(0) ?: continue
                                val pInitiated = c.getInt(1) == 1
                                val reciprocated = c.getLong(2)
                                val initiated = c.getLong(3)
                                val initWith = c.getString(4) ?: ""
                                val priorityLike = c.getInt(5) == 1
                                val phoneEx = c.getInt(6) == 1
                                val socialEx = c.getInt(7) == 1
                                val replyNudge = c.getInt(8)
                                val isHidden = c.getInt(9) == 1
                                val chatEnded = c.getInt(10) == 1
                                val chatEndedByMe = c.getInt(11) == 1
                                val fName = c.getString(12) ?: ""
                                val age = c.getInt(13)
                                val job = c.getString(14) ?: ""

                                val chatList = messagesMap[sId] ?: emptyList()
                                val lastMsg = chatList.lastOrNull()
                                val draft = draftMap[sId] ?: ""
                                val photoUrl = subjectPhotosMap[sId] ?: ""

                                matches.add(
                                    MatchRecord(
                                        subjectId = sId,
                                        firstName = fName,
                                        photoUrl = photoUrl,
                                        age = age,
                                        jobTitle = job,
                                        playerInitiated = pInitiated,
                                        reciprocatedTimestamp = reciprocated,
                                        initiatedTimestamp = initiated,
                                        initiatedWith = initWith,
                                        originatedFromPriorityLike = priorityLike,
                                        phoneNumberExchanged = phoneEx,
                                        socialMediaExchanged = socialEx,
                                        replyNudgeDays = replyNudge,
                                        isHidden = isHidden,
                                        chatEnded = chatEnded,
                                        chatEndedByMe = chatEndedByMe,
                                        draftMessage = draft,
                                        lastMessageText = lastMsg?.body ?: "",
                                        lastMessageTimestamp = lastMsg?.createdTimestamp ?: 0L,
                                        lastMessageSentBySubject = lastMsg?.sentBySubject ?: false,
                                        messageCount = chatList.size,
                                        messages = chatList
                                    )
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(Consts.TAG, "SuStorageReader: Error reading matches", e)
                    }
                } catch (e: Exception) {
                    Log.e(Consts.TAG, "SuStorageReader: Error in matches pipeline", e)
                }
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "SuStorageReader: Error copying or opening SQLite database", e)
            val archived = CandidateArchiveDb(context).getAllArchived()
            return DbExtractResult(playerMedia, playerAnswers, archived, matches, dbTables)
        }

        return DbExtractResult(playerMedia, playerAnswers, candidates, matches, dbTables)
    }
}
