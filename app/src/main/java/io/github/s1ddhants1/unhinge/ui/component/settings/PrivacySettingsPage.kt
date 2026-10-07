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
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.util.PreferencesManager

@Composable
fun PrivacySettingsPage(
    prefs: PreferencesManager
) {
    val master = prefs.masterEnabled

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SettingsSectionCard(title = stringResource(R.string.priv_section_shield)) {
            SettingsSwitchRow(
                title = stringResource(R.string.priv_master),
                icon = Icons.Default.Shield,
                checked = master,
                onCheckedChange = { prefs.masterEnabled = it }
            )
        }

        SettingsSectionCard(title = stringResource(R.string.priv_section_sdks)) {
            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_firebase),
                icon = Icons.Default.Security,
                checked = prefs.blockFirebase,
                onCheckedChange = { prefs.blockFirebase = it },
                enabled = master
            )
            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_appsflyer),
                icon = Icons.Default.NetworkCheck,
                checked = prefs.blockAppsflyer,
                onCheckedChange = { prefs.blockAppsflyer = it },
                enabled = master
            )
            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_split),
                icon = Icons.Default.Security,
                checked = prefs.blockSplitTelemetry,
                onCheckedChange = { prefs.blockSplitTelemetry = it },
                enabled = master
            )
            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_incognia),
                icon = Icons.Default.Fingerprint,
                checked = prefs.blockIncognia,
                onCheckedChange = { prefs.blockIncognia = it },
                enabled = master
            )
            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_metric_workers),
                icon = Icons.Default.NetworkCheck,
                checked = prefs.blockMetricWorkers,
                onCheckedChange = { prefs.blockMetricWorkers = it },
                enabled = master
            )
            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_okhttp),
                icon = Icons.Default.NetworkCheck,
                checked = prefs.blockOkHttpTelemetry,
                onCheckedChange = { prefs.blockOkHttpTelemetry = it },
                enabled = master
            )
            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_crashlytics),
                icon = Icons.Default.Security,
                checked = prefs.blockCrashUpload,
                onCheckedChange = { prefs.blockCrashUpload = it },
                enabled = master
            )
            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_perf),
                icon = Icons.Default.Security,
                checked = prefs.blockPerf,
                onCheckedChange = { prefs.blockPerf = it },
                enabled = master
            )
        }

        SettingsSectionCard(title = stringResource(R.string.priv_section_device)) {
            SettingsSwitchRow(
                title = stringResource(R.string.priv_fuzz_location),
                icon = Icons.Default.LocationOn,
                checked = prefs.fuzzLocation,
                onCheckedChange = { prefs.fuzzLocation = it },
                enabled = master
            )
            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_contacts),
                icon = Icons.Default.ContactPhone,
                checked = prefs.blockContacts,
                onCheckedChange = { prefs.blockContacts = it },
                enabled = master
            )
            SettingsDivider()

            SettingsSwitchRow(
                title = stringResource(R.string.priv_block_ube),
                icon = Icons.Default.Lock,
                checked = prefs.blockUbe,
                onCheckedChange = { prefs.blockUbe = it },
                enabled = master
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
