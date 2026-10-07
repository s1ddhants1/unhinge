package io.github.s1ddhants1.unhinge.ui.component.settings

import io.github.s1ddhants1.unhinge.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.util.PreferencesManager
import io.github.s1ddhants1.unhinge.util.ThemeMode

private data class ThemeOption(
    val mode: ThemeMode,
    val labelRes: Int,
    val icon: ImageVector
)

private val THEME_OPTIONS = listOf(
    ThemeOption(ThemeMode.SYSTEM, R.string.appearance_theme_system, Icons.Filled.Smartphone),
    ThemeOption(ThemeMode.LIGHT, R.string.appearance_theme_light, Icons.Filled.LightMode),
    ThemeOption(ThemeMode.DARK, R.string.appearance_theme_dark, Icons.Filled.DarkMode)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettingsPage(
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

        SettingsSectionCard(title = stringResource(R.string.appearance_theme_mode)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier.fillMaxWidth()
            ) {
                THEME_OPTIONS.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = prefs.themeMode == option.mode,
                        onClick = { prefs.themeMode = option.mode },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = THEME_OPTIONS.size
                        ),
                        icon = {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(option.labelRes),
                                maxLines = 1
                            )
                        }
                    )
                }
            }
        }

        SettingsSectionCard(title = stringResource(R.string.settings_appearance)) {
            SettingsSwitchRow(
                title = stringResource(R.string.appearance_pure_black),
                icon = Icons.Filled.DarkMode,
                checked = prefs.pureBlack,
                onCheckedChange = { prefs.pureBlack = it },
                thumbContent = if (prefs.pureBlack) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize)
                        )
                    }
                } else null
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
