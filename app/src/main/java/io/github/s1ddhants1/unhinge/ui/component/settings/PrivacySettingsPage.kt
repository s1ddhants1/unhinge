package io.github.s1ddhants1.unhinge.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
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

        // Master Privacy Shield Card
        SettingsSectionCard(title = "Privacy Shield") {
            SettingsSwitchRow(
                title = "Master Interception Shield",
                description = if (master) "Active • All 13 runtime suppression hooks armed" else "Disabled • Suppression paused",
                icon = Icons.Default.Shield,
                checked = master,
                onCheckedChange = { prefs.masterEnabled = it }
            )
        }

        // Info Card
        Card(
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Unhinge intercepts analytics silently and drops outbound tracker packets. Hinge functions normally without outbound telemetry leaks.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Telemetry & Tracking SDKs Section
        SettingsSectionCard(title = "Telemetry & Analytics SDKs") {
            SettingsSwitchRow(
                title = "Block Firebase Analytics",
                description = "Drop event logging, user properties, and AppMeasurementSdk",
                icon = Icons.Default.Security,
                checked = prefs.blockFirebase,
                onCheckedChange = { prefs.blockFirebase = it },
                enabled = master
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            SettingsSwitchRow(
                title = "Block AppsFlyer Attribution",
                description = "Suppress install attribution, UID tracking, and session pings",
                icon = Icons.Default.NetworkCheck,
                checked = prefs.blockAppsflyer,
                onCheckedChange = { prefs.blockAppsflyer = it },
                enabled = master
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            SettingsSwitchRow(
                title = "Block Split.io Telemetry",
                description = "Suppress feature flag impression tracking and telemetry sync",
                icon = Icons.Default.Security,
                checked = prefs.blockSplitTelemetry,
                onCheckedChange = { prefs.blockSplitTelemetry = it },
                enabled = master
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            SettingsSwitchRow(
                title = "Block Incognia Anti-Fraud",
                description = "Prevent WiFi BSSID/SSID fingerprinting and location telemetry",
                icon = Icons.Default.Fingerprint,
                checked = prefs.blockIncognia,
                onCheckedChange = { prefs.blockIncognia = it },
                enabled = master
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            SettingsSwitchRow(
                title = "Block Metric Worker Jobs",
                description = "Acknowledge WorkManager telemetry jobs without uploading",
                icon = Icons.Default.NetworkCheck,
                checked = prefs.blockMetricWorkers,
                onCheckedChange = { prefs.blockMetricWorkers = it },
                enabled = master
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            SettingsSwitchRow(
                title = "Block OkHttp Telemetry",
                description = "Drop telemetry packets targeted at known tracking hosts",
                icon = Icons.Default.NetworkCheck,
                checked = prefs.blockOkHttpTelemetry,
                onCheckedChange = { prefs.blockOkHttpTelemetry = it },
                enabled = master
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            SettingsSwitchRow(
                title = "Block Crashlytics Upload",
                description = "Suppress crash telemetry and stack trace uploads",
                icon = Icons.Default.Security,
                checked = prefs.blockCrashUpload,
                onCheckedChange = { prefs.blockCrashUpload = it },
                enabled = master
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            SettingsSwitchRow(
                title = "Block Performance SDK",
                description = "Disable Firebase Performance Monitoring trace collection",
                icon = Icons.Default.Security,
                checked = prefs.blockPerf,
                onCheckedChange = { prefs.blockPerf = it },
                enabled = master
            )
        }

        // Permissions & Device Privacy Section
        SettingsSectionCard(title = "Device Permissions & Hardware") {
            SettingsSwitchRow(
                title = "Fuzz Device Location",
                description = "Add subtle jitter to GPS coordinates to prevent precise geo-profiling",
                icon = Icons.Default.LocationOn,
                checked = prefs.fuzzLocation,
                onCheckedChange = { prefs.fuzzLocation = it },
                enabled = master
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            SettingsSwitchRow(
                title = "Block Contacts Synchronization",
                description = "Return empty address book to prevent social graph harvesting",
                icon = Icons.Default.ContactPhone,
                checked = prefs.blockContacts,
                onCheckedChange = { prefs.blockContacts = it },
                enabled = master
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            SettingsSwitchRow(
                title = "Block User Behavior Engine (UBE)",
                description = "Drop interaction cadence and engagement event streams",
                icon = Icons.Default.Lock,
                checked = prefs.blockUbe,
                onCheckedChange = { prefs.blockUbe = it },
                enabled = master
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
