package io.github.s1ddhants1.unhinge.ui.component.settings

import io.github.s1ddhants1.unhinge.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterAlt
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.hook.ui.HingeIcons
import io.github.s1ddhants1.unhinge.util.PreferencesManager

@Composable
fun FeedNavigationSettingsPage(
    prefs: PreferencesManager
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SettingsSectionCard(title = stringResource(R.string.feednav_section)) {
            SettingsSwitchRow(
                title = stringResource(R.string.feednav_likes_counter),
                icon = HingeIcons.HeartVector,
                checked = prefs.showAvailableLikes,
                onCheckedChange = { prefs.showAvailableLikes = it }
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.feednav_controls),
                icon = Icons.Outlined.SwapHoriz,
                checked = prefs.enableFeedNavigation,
                onCheckedChange = { prefs.enableFeedNavigation = it }
            )

            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.feednav_active_filters),
                description = stringResource(R.string.feednav_active_filters_desc),
                icon = Icons.Outlined.FilterAlt,
                checked = prefs.unlockActiveFilters,
                onCheckedChange = { prefs.unlockActiveFilters = it }
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
