package io.github.s1ddhants1.unhinge.hook.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.s1ddhants1.unhinge.data.HostCandidateReader
import io.github.s1ddhants1.unhinge.hook.ui.sheet.InlineAiOpenerCard
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.util.PreferencesManager

/**
 * Backward-compatible coordinator delegating to [InlineAiOpenerCard].
 * Replaces the heavy multi-card bottom sheet with Hinge-styled inline opener analysis.
 */
@Composable
fun HostAppAiSheetContent(
    prefs: PreferencesManager,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    candidateList: List<CachedCandidateProfile>? = null,
    screenClues: HostCandidateReader.ScreenClues? = null,
    showDragHandle: Boolean = false,
    standalone: Boolean = true,
    isChatMode: Boolean = false,
    hostActivity: Activity? = null,
    initialChatContext: HostCandidateReader.MatchChatContext? = null,
) {
    InlineAiOpenerCard(
        prefs = prefs,
        onDismiss = onDismiss,
        modifier = modifier,
        candidate = candidateList?.firstOrNull(),
        screenClues = screenClues,
        isChatMode = isChatMode,
        hostActivity = hostActivity,
        initialChatContext = initialChatContext,
        onInsert = hostActivity?.let { act ->
            { text ->
                HostAppAiFab.insertReplyIntoChatInput(act, text)
            }
        },
    )
}
