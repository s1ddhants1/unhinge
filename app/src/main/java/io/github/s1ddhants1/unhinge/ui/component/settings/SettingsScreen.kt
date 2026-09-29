package io.github.s1ddhants1.unhinge.ui.component.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.s1ddhants1.unhinge.util.PreferencesManager

@Composable
fun SettingsScreen(
    currentSubpage: SettingsSubpage?,
    onNavigateToSubpage: (SettingsSubpage) -> Unit,
    prefs: PreferencesManager,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = currentSubpage,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        modifier = modifier,
        label = "SettingsSubpageTransition"
    ) { subpage ->
        when (subpage) {
            null -> SettingsIndexPage(onNavigateToSubpage = onNavigateToSubpage)
            SettingsSubpage.APPEARANCE -> AppearanceSettingsPage(prefs = prefs)
            SettingsSubpage.AI_WINGMAN -> AiWingmanSettingsPage(prefs = prefs)
            SettingsSubpage.PRIVACY -> PrivacySettingsPage(prefs = prefs)
        }
    }
}
