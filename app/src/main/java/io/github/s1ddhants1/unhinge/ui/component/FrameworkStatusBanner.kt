package io.github.s1ddhants1.unhinge.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.R
import io.github.s1ddhants1.unhinge.ui.theme.AccentSuccess

@Composable
fun FrameworkStatusBanner(
    isConnected: Boolean,
    isInjectable: Boolean,
    modifier: Modifier = Modifier,
    title: String = "",
    desc: String = ""
) {
    val isActive = isConnected && isInjectable

    val containerBg = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
    }

    val statusAccent = if (isActive) AccentSuccess else MaterialTheme.colorScheme.error

    UnhingeDoubleBezelCard(
        modifier = modifier,
        shape = ShapeTokens.Card,
        containerColor = containerBg,
        borderColor = if (isActive) null else MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = statusAccent.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.Extension else Icons.Default.Warning,
                        contentDescription = "lsposed",
                        tint = statusAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "LSPosed",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun RootStatusBanner(
    isRootGranted: Boolean?,
    modifier: Modifier = Modifier
) {
    val statusAccent = when (isRootGranted) {
        true -> AccentSuccess
        false -> MaterialTheme.colorScheme.onSurfaceVariant
        null -> MaterialTheme.colorScheme.primary
    }

    val icon = when (isRootGranted) {
        true -> Icons.Default.Shield
        false -> Icons.Default.Warning
        null -> Icons.Default.Shield
    }

    val containerBg = when (isRootGranted) {
        false -> MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceContainer
    }

    UnhingeDoubleBezelCard(
        modifier = modifier,
        shape = ShapeTokens.Card,
        containerColor = containerBg,
        borderColor = if (isRootGranted == false) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f) else null,
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = statusAccent.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Root",
                        tint = statusAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Root",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

