package io.github.s1ddhants1.unhinge.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import androidx.core.database.sqlite.transaction
import io.github.s1ddhants1.unhinge.Consts
import io.github.s1ddhants1.unhinge.model.ChatMessageRecord
import io.github.s1ddhants1.unhinge.model.MatchRecord

class MatchArchiveDb(context: Context) : SQLiteOpenHelper(context, "match_archive.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS archived_matches (
                subjectId TEXT PRIMARY KEY,
                firstName TEXT,
                photoUrl TEXT,
                age INTEGER,
                jobTitle TEXT,
                playerInitiated INTEGER,
                reciprocatedTimestamp INTEGER,
                initiatedTimestamp INTEGER,
                initiatedWith TEXT,
                originatedFromPriorityLike INTEGER,
                phoneNumberExchanged INTEGER,
                socialMediaExchanged INTEGER,
                replyNudgeDays INTEGER,
                isHidden INTEGER,
                chatEnded INTEGER,
                chatEndedByMe INTEGER,
                draftMessage TEXT,
                lastMessageText TEXT,
                lastMessageTimestamp INTEGER,
                lastMessageSentBySubject INTEGER,
                messageCount INTEGER,
                isLiveMatch INTEGER DEFAULT 1,
                isUnmatched INTEGER DEFAULT 0,
                firstSeenTimestamp INTEGER,
                lastSeenTimestamp INTEGER
            )
        """.trimIndent())

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS archived_messages (
                localId TEXT PRIMARY KEY,
                subjectId TEXT NOT NULL,
                body TEXT,
                createdTimestamp INTEGER,
                sentBySubject INTEGER,
                unread INTEGER,
                reactions TEXT,
                isVoiceNote INTEGER,
                voiceTranscript TEXT
            )
        """.trimIndent())

        db.execSQL("CREATE INDEX IF NOT EXISTS idx_messages_subject ON archived_messages (subjectId, createdTimestamp ASC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_matches_live ON archived_matches (isLiveMatch)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_matches_unmatched ON archived_matches (isUnmatched)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_matches_reciprocated ON archived_matches (reciprocatedTimestamp DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration logic if schema changes in future versions
    }

    @Synchronized
    fun upsertAndMerge(
        liveMatches: List<MatchRecord>,
        candidateArchiveDb: CandidateArchiveDb? = null
    ): List<MatchRecord> {
        val db = writableDatabase
        val now = System.currentTimeMillis()

        try {
            db.transaction {
                // 1. Mark existing matches as not live
                execSQL("UPDATE archived_matches SET isLiveMatch = 0")

                // 2. Upsert incoming live matches (deduplicated by subjectId)
                val distinctLiveMatches = liveMatches
                    .filter { it.subjectId.isNotBlank() }
                    .distinctBy { it.subjectId }

                for (m in distinctLiveMatches) {
                    var firstSeen = now
                    var existingFirstName = ""
                    var existingPhoto = ""
                    var existingAge = 0
                    var existingJob = ""
                    var existingChatEndedByMe = false

                    db.rawQuery(
                        "SELECT firstSeenTimestamp, firstName, photoUrl, age, jobTitle, chatEndedByMe FROM archived_matches WHERE subjectId = ?",
                        arrayOf(m.subjectId)
                    ).use { c ->
                        if (c.moveToNext()) {
                            firstSeen = c.getLong(0)
                            existingFirstName = c.getString(1) ?: ""
                            existingPhoto = c.getString(2) ?: ""
                            existingAge = c.getInt(3)
                            existingJob = c.getString(4) ?: ""
                            existingChatEndedByMe = c.getInt(5) == 1
                        }
                    }

                    val candidate = if (m.firstName.isBlank() || m.photoUrl.isBlank() || m.age == 0) {
                        candidateArchiveDb?.getCandidateByUserId(m.subjectId)
                    } else null

                    val resolvedFirstName = m.firstName.ifBlank { existingFirstName.ifBlank { candidate?.firstName ?: "" } }
                    val resolvedPhoto = m.photoUrl.ifBlank { existingPhoto.ifBlank { candidate?.photos?.firstOrNull() ?: "" } }
                    val resolvedAge = if (m.age > 0) m.age else (if (existingAge > 0) existingAge else (candidate?.age ?: 0))
                    val resolvedJob = m.jobTitle.ifBlank { existingJob.ifBlank { candidate?.jobTitle ?: "" } }
                    val resolvedChatEndedByMe = m.chatEndedByMe || existingChatEndedByMe

                    val values = ContentValues().apply {
                        put("subjectId", m.subjectId)
                        put("firstName", resolvedFirstName)
                        put("photoUrl", resolvedPhoto)
                        put("age", resolvedAge)
                        put("jobTitle", resolvedJob)
                        put("playerInitiated", if (m.playerInitiated) 1 else 0)
                        put("reciprocatedTimestamp", m.reciprocatedTimestamp)
                        put("initiatedTimestamp", m.initiatedTimestamp)
                        put("initiatedWith", m.initiatedWith)
                        put("originatedFromPriorityLike", if (m.originatedFromPriorityLike) 1 else 0)
                        put("phoneNumberExchanged", if (m.phoneNumberExchanged) 1 else 0)
                        put("socialMediaExchanged", if (m.socialMediaExchanged) 1 else 0)
                        put("replyNudgeDays", m.replyNudgeDays)
                        put("isHidden", if (m.isHidden) 1 else 0)
                        put("chatEnded", if (m.chatEnded) 1 else 0)
                        put("chatEndedByMe", if (resolvedChatEndedByMe) 1 else 0)
                        put("draftMessage", m.draftMessage)
                        put("lastMessageText", m.lastMessageText)
                        put("lastMessageTimestamp", m.lastMessageTimestamp)
                        put("lastMessageSentBySubject", if (m.lastMessageSentBySubject) 1 else 0)
                        put("messageCount", m.messageCount)
                        put("isLiveMatch", 1)
                        put("isUnmatched", if (m.chatEnded && !resolvedChatEndedByMe) 1 else 0)
                        put("firstSeenTimestamp", firstSeen)
                        put("lastSeenTimestamp", now)
                    }
                    db.insertWithOnConflict("archived_matches", null, values, SQLiteDatabase.CONFLICT_REPLACE)

                    val distinctMessages = m.messages.distinctBy { msg ->
                        if (msg.localId.isNotBlank()) msg.localId
                        else "${msg.createdTimestamp}_${msg.sentBySubject}_${msg.body}"
                    }
                    for (msg in distinctMessages) {
                        val msgId = if (msg.localId.isNotBlank()) msg.localId else "${m.subjectId}_${msg.createdTimestamp}_${msg.sentBySubject}_${msg.body.hashCode()}"
                        val msgValues = ContentValues().apply {
                            put("localId", msgId)
                            put("subjectId", m.subjectId)
                            put("body", msg.body)
                            put("createdTimestamp", msg.createdTimestamp)
                            put("sentBySubject", if (msg.sentBySubject) 1 else 0)
                            put("unread", if (msg.unread) 1 else 0)
                            put("reactions", msg.reactions)
                            put("isVoiceNote", if (msg.isVoiceNote) 1 else 0)
                            put("voiceTranscript", msg.voiceTranscript)
                        }
                        db.insertWithOnConflict("archived_messages", null, msgValues, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }

                // 3. For any match in archive that is not in liveMatches:
                // Marked as not live, chat ended, and unmatched
                execSQL("UPDATE archived_matches SET chatEnded = 1, isUnmatched = 1 WHERE isLiveMatch = 0")
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "MatchArchiveDb: Error during upsertAndMerge", e)
        }

        return getAllArchived(candidateArchiveDb)
    }

    @Synchronized
    fun getAllArchived(candidateArchiveDb: CandidateArchiveDb? = null): List<MatchRecord> {
        val matches = mutableListOf<MatchRecord>()
        val seenSubjectIds = mutableSetOf<String>()
        val db = readableDatabase

        try {
            db.rawQuery("""
                SELECT subjectId, firstName, photoUrl, age, jobTitle,
                       playerInitiated, reciprocatedTimestamp, initiatedTimestamp,
                       initiatedWith, originatedFromPriorityLike, phoneNumberExchanged,
                       socialMediaExchanged, replyNudgeDays, isHidden, chatEnded,
                       chatEndedByMe, draftMessage, lastMessageText, lastMessageTimestamp,
                       lastMessageSentBySubject, messageCount, isLiveMatch, isUnmatched
                FROM archived_matches
                GROUP BY subjectId
            """.trimIndent(), null).use { c ->
                while (c.moveToNext()) {
                    val sId = c.getString(0) ?: continue
                    if (!seenSubjectIds.add(sId)) continue
                    var fName = c.getString(1) ?: ""
                    var photo = c.getString(2) ?: ""
                    var age = c.getInt(3)
                    var job = c.getString(4) ?: ""
                    val playerInitiated = c.getInt(5) == 1
                    val reciprocated = c.getLong(6)
                    val initiated = c.getLong(7)
                    val initWith = c.getString(8) ?: ""
                    val priorityLike = c.getInt(9) == 1
                    val phoneEx = c.getInt(10) == 1
                    val socialEx = c.getInt(11) == 1
                    val replyNudge = c.getInt(12)
                    val isHidden = c.getInt(13) == 1
                    val chatEndedVal = c.getInt(14) == 1
                    val chatEndedByMe = c.getInt(15) == 1
                    val draft = c.getString(16) ?: ""
                    var lastMsgText = c.getString(17) ?: ""
                    var lastMsgTime = c.getLong(18)
                    var lastMsgSentBySub = c.getInt(19) == 1
                    val isLiveMatch = c.getInt(21) == 1
                    val isUnmatched = c.getInt(22) == 1 || (!isLiveMatch) || (chatEndedVal && !chatEndedByMe)

                    if (fName.isBlank() || photo.isBlank() || age == 0) {
                        val candidate = candidateArchiveDb?.getCandidateByUserId(sId)
                        if (candidate != null) {
                            if (fName.isBlank()) fName = candidate.firstName
                            if (photo.isBlank()) photo = candidate.photos.firstOrNull() ?: ""
                            if (age == 0) age = candidate.age
                            if (job.isBlank()) job = candidate.jobTitle
                        }
                    }

                    val messageList = mutableListOf<ChatMessageRecord>()
                    db.rawQuery("""
                        SELECT localId, subjectId, body, createdTimestamp, sentBySubject,
                               unread, reactions, isVoiceNote, voiceTranscript
                        FROM archived_messages
                        WHERE subjectId = ?
                        ORDER BY createdTimestamp ASC
                    """.trimIndent(), arrayOf(sId)).use { mc ->
                        while (mc.moveToNext()) {
                            messageList.add(
                                ChatMessageRecord(
                                    localId = mc.getString(0) ?: "",
                                    subjectId = mc.getString(1) ?: sId,
                                    body = mc.getString(2) ?: "",
                                    createdTimestamp = mc.getLong(3),
                                    sentBySubject = mc.getInt(4) == 1,
                                    unread = mc.getInt(5) == 1,
                                    reactions = mc.getString(6) ?: "",
                                    isVoiceNote = mc.getInt(7) == 1,
                                    voiceTranscript = mc.getString(8) ?: ""
                                )
                            )
                        }
                    }

                    val distinctMessageList = messageList.distinctBy { msg ->
                        if (msg.localId.isNotBlank()) msg.localId
                        else "${msg.createdTimestamp}_${msg.sentBySubject}_${msg.body}"
                    }

                    if (distinctMessageList.isNotEmpty()) {
                        val last = distinctMessageList.last()
                        lastMsgText = last.body
                        lastMsgTime = last.createdTimestamp
                        lastMsgSentBySub = last.sentBySubject
                    }

                    matches.add(
                        MatchRecord(
                            subjectId = sId,
                            firstName = fName,
                            photoUrl = photo,
                            age = age,
                            jobTitle = job,
                            playerInitiated = playerInitiated,
                            reciprocatedTimestamp = reciprocated,
                            initiatedTimestamp = initiated,
                            initiatedWith = initWith,
                            originatedFromPriorityLike = priorityLike,
                            phoneNumberExchanged = phoneEx,
                            socialMediaExchanged = socialEx,
                            replyNudgeDays = replyNudge,
                            isHidden = isHidden,
                            chatEnded = if (isLiveMatch) chatEndedVal else true,
                            chatEndedByMe = chatEndedByMe,
                            draftMessage = draft,
                            lastMessageText = lastMsgText,
                            lastMessageTimestamp = lastMsgTime,
                            lastMessageSentBySubject = lastMsgSentBySub,
                            messageCount = distinctMessageList.size,
                            messages = distinctMessageList,
                            isLiveMatch = isLiveMatch,
                            isUnmatched = isUnmatched
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "MatchArchiveDb: Error reading archived matches", e)
        }

        return matches
            .distinctBy { it.subjectId }
            .sortedWith(
                compareByDescending { maxOf(it.lastMessageTimestamp, it.reciprocatedTimestamp, it.initiatedTimestamp) }
            )
    }

    @Synchronized
    fun getArchiveCount(): Int {
        val db = readableDatabase
        db.rawQuery("SELECT count(DISTINCT subjectId) FROM archived_matches", null).use {
            if (it.moveToNext()) return it.getInt(0)
        }
        return 0
    }

    @Synchronized
    fun clearArchive() {
        val db = writableDatabase
        db.delete("archived_messages", null, null)
        db.delete("archived_matches", null, null)
    }

    @Synchronized
    fun upsertMatches(matches: List<MatchRecord>): Int {
        val db = writableDatabase
        var count = 0
        val now = System.currentTimeMillis()
        val distinctMatches = matches
            .filter { it.subjectId.isNotBlank() }
            .distinctBy { it.subjectId }

        try {
            db.transaction {
                for (m in distinctMatches) {
                    val values = ContentValues().apply {
                        put("subjectId", m.subjectId)
                        put("firstName", m.firstName)
                        put("photoUrl", m.photoUrl)
                        put("age", m.age)
                        put("jobTitle", m.jobTitle)
                        put("playerInitiated", if (m.playerInitiated) 1 else 0)
                        put("reciprocatedTimestamp", m.reciprocatedTimestamp)
                        put("initiatedTimestamp", m.initiatedTimestamp)
                        put("initiatedWith", m.initiatedWith)
                        put("originatedFromPriorityLike", if (m.originatedFromPriorityLike) 1 else 0)
                        put("phoneNumberExchanged", if (m.phoneNumberExchanged) 1 else 0)
                        put("socialMediaExchanged", if (m.socialMediaExchanged) 1 else 0)
                        put("replyNudgeDays", m.replyNudgeDays)
                        put("isHidden", if (m.isHidden) 1 else 0)
                        put("chatEnded", if (m.chatEnded) 1 else 0)
                        put("chatEndedByMe", if (m.chatEndedByMe) 1 else 0)
                        put("draftMessage", m.draftMessage)
                        put("lastMessageText", m.lastMessageText)
                        put("lastMessageTimestamp", m.lastMessageTimestamp)
                        put("lastMessageSentBySubject", if (m.lastMessageSentBySubject) 1 else 0)
                        put("messageCount", m.messageCount)
                        put("isLiveMatch", if (m.isLiveMatch) 1 else 0)
                        put("isUnmatched", if (m.isUnmatched) 1 else 0)
                        put("firstSeenTimestamp", now)
                        put("lastSeenTimestamp", now)
                    }
                    val rowId = db.insertWithOnConflict("archived_matches", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    if (rowId != -1L) count++

                    val distinctMessages = m.messages.distinctBy { msg ->
                        if (msg.localId.isNotBlank()) msg.localId
                        else "${msg.createdTimestamp}_${msg.sentBySubject}_${msg.body}"
                    }
                    for (msg in distinctMessages) {
                        val msgId = if (msg.localId.isNotBlank()) msg.localId else "${m.subjectId}_${msg.createdTimestamp}_${msg.sentBySubject}_${msg.body.hashCode()}"
                        val msgValues = ContentValues().apply {
                            put("localId", msgId)
                            put("subjectId", m.subjectId)
                            put("body", msg.body)
                            put("createdTimestamp", msg.createdTimestamp)
                            put("sentBySubject", if (msg.sentBySubject) 1 else 0)
                            put("unread", if (msg.unread) 1 else 0)
                            put("reactions", msg.reactions)
                            put("isVoiceNote", if (msg.isVoiceNote) 1 else 0)
                            put("voiceTranscript", msg.voiceTranscript)
                        }
                        db.insertWithOnConflict("archived_messages", null, msgValues, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(Consts.TAG, "MatchArchiveDb: Error during upsertMatches", e)
        }
        return count
    }
}
