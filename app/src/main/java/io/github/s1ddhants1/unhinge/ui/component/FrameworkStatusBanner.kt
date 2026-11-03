package io.github.s1ddhants1.unhinge.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
    title: String,
    desc: String,
    modifier: Modifier = Modifier
) {
    val isActive = isConnected && isInjectable

    val containerBg = if (isActive) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
    }

    val statusAccent = if (isActive) AccentSuccess else MaterialTheme.colorScheme.error

    UnhingeDoubleBezelCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = ShapeTokens.Card,
        containerColor = containerBg,
        borderColor = if (isActive) null else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = statusAccent.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = statusAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    UnhingeBadge(
                        label = if (isActive) stringResource(R.string.banner_status_active) else stringResource(R.string.banner_status_degraded),
                        containerColor = statusAccent.copy(alpha = 0.18f),
                        contentColor = statusAccent,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                if (desc.isNotBlank()) {
                    Text(
                        text = desc,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
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

    val badgeLabel = when (isRootGranted) {
        true -> stringResource(R.string.root_status_granted)
        false -> stringResource(R.string.root_status_unavailable)
        null -> stringResource(R.string.root_status_checking)
    }

    val desc = when (isRootGranted) {
        true -> stringResource(R.string.root_desc_granted)
        false -> stringResource(R.string.root_desc_unavailable)
        null -> stringResource(R.string.root_desc_checking)
    }

    UnhingeDoubleBezelCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = ShapeTokens.Card,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = statusAccent.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = statusAccent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.root_access_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    UnhingeBadge(
                        label = badgeLabel,
                        containerColor = when (isRootGranted) {
                            true -> AccentSuccess.copy(alpha = 0.18f)
                            false -> MaterialTheme.colorScheme.surfaceContainerHighest
                            null -> MaterialTheme.colorScheme.surfaceContainerHighest
                        },
                        contentColor = when (isRootGranted) {
                            true -> AccentSuccess
                            false -> MaterialTheme.colorScheme.onSurfaceVariant
                            null -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        },
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }

                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
