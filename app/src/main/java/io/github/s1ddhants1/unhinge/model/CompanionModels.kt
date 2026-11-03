package io.github.s1ddhants1.unhinge.model

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class DailyLikeStatus(
    val likesLeft: Int = 8,
    val maxLikes: Int = 8,
    val freeRosesLeft: Int = 1,
    val roseResetTimestamp: Long = 0L,
    val lastUpdated: Long = 0L
)

@Keep
@Serializable
data class IncomingLikeInsight(
    val totalLikes: Int = 0,
    val commentLikes: Int = 0,
    val plainLikes: Int = 0,
    val photoLikes: Int = 0,
    val promptLikes: Int = 0,
    val voicePromptLikes: Int = 0,
    val recentLikeTimestamps: List<Long> = emptyList(),
    val lastUpdated: Long = 0L
) {
    val commentRate: Float
        get() = if (totalLikes > 0) (commentLikes.toFloat() / totalLikes.toFloat()) * 100f else 0f
}

@Keep
@Serializable
data class ProfileAuditReport(
    val percentComplete: Int = 0,
    val photoCount: Int = 0,
    val promptCount: Int = 0,
    val hasVoicePrompt: Boolean = false,
    val hasVideoPrompt: Boolean = false,
    val promptEvaluationStatus: String = "Pending",
    val promptFeedbackDetail: String = "",
    val coachingTips: List<String> = emptyList(),
    val lastUpdated: Long = 0L
)

@Keep
@Serializable
data class BoostInsight(
    val availableBoosts: Int = 0,
    val lastBoostTimestamp: Long = 0L,
    val optimalDay: String = "Sunday",
    val optimalHourRange: String = "7:00 PM – 9:30 PM",
    val historyCount: Int = 0,
    val lastUpdated: Long = 0L
)

@Keep
@Serializable
data class FreshStartStatus(
    val isEligible: Boolean = false,
    val lastCheckTimestamp: Long = 0L,
    val resetHistoryCount: Int = 0,
    val lastResetTimestamp: Long = 0L
)

@Keep
@Serializable
data class SessionBehaviorMetrics(
    val totalSessions: Int = 0,
    val averageDwellSeconds: Long = 0L,
    val profilesSeen: Int = 0,
    val profilesLiked: Int = 0,
    val consecutivePassStreak: Int = 0,
    val lastSessionTimestamp: Long = 0L
) {
    val selectivityRatio: Float
        get() = if (profilesSeen > 0) (profilesLiked.toFloat() / profilesSeen.toFloat()) * 100f else 0f
}

@Keep
@Serializable
data class RawHingeTelemetry(
    val firstName: String = "",
    val identityId: String = "",
    val metroArea: String = "",
    val billingCountryCode: String = "",
    val apiAvailableLikes: Int = 8,
    val localAvailableLikes: Int = 8,
    val apiAvailableSuperLikes: Int = 0,
    val localAvailableSuperlikes: Int = 0,
    val boostsAvailable: Int = 0,
    val apiAvailableSnoozes: Int = 0,
    val apiAvailableSkipUndos: Int = 0,
    val apiAvailableAlcPriorityLikes: Int = 0,
    val likesSentForPostLikeEncouragement: Int = 0,
    val sendLikesOnDiscoverCounter: Int = 0,
    val timesSeenSendRoseInsteadDialog: Int = 0,
    val totalBoostUpsellPresentations: Int = 0,
    val profileCompleteness: Float = 0f,
    val isProfileComplete: Boolean = false,
    val profileRequiredPhotos: Int = 6,
    val isAccountPaused: Boolean = false,
    val isSmartPhotoOptedIn: Boolean = false,
    val isCircleOptIn: Boolean = false,
    val shouldShowVoicePromptContextualNudge: Boolean = false,
    val hasViewedPromptFeedback: Boolean = false,
    val createdTimestamp: Long = 0L,
    val firstHingeSync: Long = 0L,
    val lastHingeSync: Long = 0L,
    val userProfileLastChanged: Long = 0L,
    val userPreferencesLastChanged: Long = 0L,
    val likesCacheTimestamp: Long = 0L,
    val standoutsExpiration: Long = 0L,
    val userPermissions: List<String> = emptyList(),
    val installId: String = "",
    val discoverCacheEtag: String = "",
    val isRootGranted: Boolean = false,
    val lastReadTimestamp: Long = 0L
)

@Keep
@Serializable
data class PlayerMediaItem(
    val position: Int = 0,
    val photoUrl: String = "",
    val description: String = "",
    val promptCaption: String = ""
)

@Keep
@Serializable
data class PlayerAnswerItem(
    val questionId: String = "",
    val questionText: String = "",
    val responseText: String = "",
    val contentId: String = ""
)

@Keep
@Serializable
data class CandidatePromptItem(
    val question: String = "",
    val answer: String = ""
)

@Keep
@Serializable
data class CachedCandidateProfile(
    val userId: String = "",
    val firstName: String = "",
    val age: Int = 0,
    val height: Int = 0,
    val hometown: String = "",
    val location: String = "",
    val jobTitle: String = "",
    val datingIntention: String = "",
    val relationshipType: String = "",
    val religion: String = "",
    val ethnicity: String = "",
    val isSelfieVerified: Boolean = false,
    val isCircleMember: Boolean = false,
    val photos: List<String> = emptyList(),
    val prompts: List<CandidatePromptItem> = emptyList(),
    val isStandout: Boolean = false,
    val isDiscover: Boolean = false,
    val isLiveInFeed: Boolean = true,
    val ratingStatus: String = "",
    val likeComment: String = "",
    val school: String = "",
    val employer: String = "",
    val politics: String = "",
    val smoking: String = "",
    val drinking: String = "",
    val marijuana: String = "",
    val drugs: String = "",
    val kids: String = "",
    val familyPlans: String = "",
    val pet: String = "",
    val zodiac: String = "",
    val isNewHere: Boolean = false,
    val isYourTypeLately: Boolean = false,
    val isSecondChance: Boolean = false,
    val isIncomingLike: Boolean = false,
    val incomingComment: String = "",
    val incomingLikeType: String = "",
    val incomingTimestamp: Long = 0L,
    val firstSeenTimestamp: Long = 0L,
    val lastSeenTimestamp: Long = 0L,
    val lastActiveStatusId: Int? = null
) {
    val activeStatusText: String
        get() = when (lastActiveStatusId) {
            1 -> "Active now"
            2 -> "Active today"
            else -> ""
        }
}

@Keep
@Serializable
data class ChatMessageRecord(
    val localId: String = "",
    val subjectId: String = "",
    val body: String = "",
    val createdTimestamp: Long = 0L,
    val sentBySubject: Boolean = false,
    val unread: Boolean = false,
    val reactions: String = "",
    val isVoiceNote: Boolean = false,
    val voiceTranscript: String = ""
)

@Keep
@Serializable
data class MatchRecord(
    val subjectId: String = "",
    val firstName: String = "",
    val photoUrl: String = "",
    val age: Int = 0,
    val jobTitle: String = "",
    val playerInitiated: Boolean = false,
    val reciprocatedTimestamp: Long = 0L,
    val initiatedTimestamp: Long = 0L,
    val initiatedWith: String = "",
    val originatedFromPriorityLike: Boolean = false,
    val phoneNumberExchanged: Boolean = false,
    val socialMediaExchanged: Boolean = false,
    val replyNudgeDays: Int = 0,
    val isHidden: Boolean = false,
    val chatEnded: Boolean = false,
    val chatEndedByMe: Boolean = false,
    val draftMessage: String = "",
    val lastMessageText: String = "",
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSentBySubject: Boolean = false,
    val messageCount: Int = 0,
    val messages: List<ChatMessageRecord> = emptyList()
)

@Keep
@Serializable
data class DatabaseTableSummary(
    val tableName: String = "",
    val rowCount: Int = 0
)

@Keep
@Serializable
data class StoragePrefEntry(
    val file: String = "",
    val key: String = "",
    val type: String = "",
    val value: String = ""
)

@Keep
@Serializable
data class RawPrefFile(
    val fileName: String = "",
    val entries: List<StoragePrefEntry> = emptyList()
)

@Keep
@Serializable
data class CompleteHingeData(
    val telemetry: RawHingeTelemetry = RawHingeTelemetry(),
    val dailyLikes: DailyLikeStatus = DailyLikeStatus(),
    val incomingLikes: IncomingLikeInsight = IncomingLikeInsight(),
    val profileAudit: ProfileAuditReport = ProfileAuditReport(),
    val behaviorMetrics: SessionBehaviorMetrics = SessionBehaviorMetrics(),
    val playerMedia: List<PlayerMediaItem> = emptyList(),
    val playerAnswers: List<PlayerAnswerItem> = emptyList(),
    val candidates: List<CachedCandidateProfile> = emptyList(),
    val matches: List<MatchRecord> = emptyList(),
    val databaseTables: List<DatabaseTableSummary> = emptyList(),
    val allPrefFiles: List<RawPrefFile> = emptyList(),
    val isRootGranted: Boolean = false,
    val lastUpdatedTimestamp: Long = 0L
)
