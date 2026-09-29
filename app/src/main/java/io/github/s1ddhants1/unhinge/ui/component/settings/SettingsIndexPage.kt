package io.github.s1ddhants1.unhinge.ui.component.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.R

@Composable
fun SettingsIndexPage(
    onNavigateToSubpage: (SettingsSubpage) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(4.dp))

        SettingsCategoryCard(
            icon = Icons.Filled.Palette,
            title = "Appearance",
            description = "Theme mode and AMOLED pure black",
            onClick = { onNavigateToSubpage(SettingsSubpage.APPEARANCE) }
        )

        SettingsCategoryCard(
            icon = Icons.Filled.AutoAwesome,
            title = "AI Prompt Wingman",
            description = "OpenRouter / OpenAI API key, model selection, and system prompt",
            onClick = { onNavigateToSubpage(SettingsSubpage.AI_WINGMAN) }
        )

        SettingsCategoryCard(
            icon = Icons.Filled.Shield,
            title = "Privacy & Telemetry",
            description = "13 modular hooks suppressing analytics, ad SDKs & anti-fraud",
            onClick = { onNavigateToSubpage(SettingsSubpage.PRIVACY) }
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = { uriHandler.openUri("https://github.com/s1ddhants1/unhinge") },
                shape = CircleShape,
                modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_github),
                    contentDescription = "GitHub Repository",
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
