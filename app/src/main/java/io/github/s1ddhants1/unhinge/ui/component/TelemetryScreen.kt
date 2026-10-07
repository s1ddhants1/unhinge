package io.github.s1ddhants1.unhinge.ui.component

import io.github.s1ddhants1.unhinge.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import io.github.s1ddhants1.unhinge.hook.ui.HingeIcons
import io.github.s1ddhants1.unhinge.model.CompleteHingeData
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private fun getResetCountdown(): String {
    val now = Calendar.getInstance()
    val resetCal = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 4)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (before(now)) {
            add(Calendar.DAY_OF_YEAR, 1)
        }
    }
    val diffMillis = (resetCal.timeInMillis - now.timeInMillis).coerceAtLeast(0L)
    val hours = diffMillis / (1000 * 60 * 60)
    val minutes = (diffMillis % (1000 * 60 * 60)) / (1000 * 60)
    val seconds = (diffMillis % (1000 * 60)) / 1000
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m ${seconds}s"
    }
}

@Composable
fun TelemetryScreen(
    data: CompleteHingeData,
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false
) {
    val t = data.telemetry
    val inLikes = data.incomingLikes
    val behavior = data.behaviorMetrics
    val playerPhoto = data.playerMedia.firstOrNull { it.photoUrl.isNotBlank() }?.photoUrl

    var countdownText by remember { mutableStateOf(getResetCountdown()) }
    LaunchedEffect(Unit) {
        while (true) {
            countdownText = getResetCountdown()
            delay(1000L)
        }
    }

    if (isRefreshing && data.telemetry.identityId.isBlank() && data.candidates.isEmpty()) {
        TelemetrySkeleton(modifier = modifier)
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        UnhingeDoubleBezelCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeTokens.Card,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {

                        val initial = t.firstName.firstOrNull()?.uppercase() ?: "H"
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(46.dp)
                        ) {
                            if (!playerPhoto.isNullOrBlank()) {
                                SubcomposeAsyncImage(
                                    model = playerPhoto,
                                    contentDescription = stringResource(R.string.cd_profile_photo),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                    loading = {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    },
                                    error = {
                                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = initial,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                )
                            } else {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = initial,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (t.firstName.isNotBlank()) t.firstName else stringResource(R.string.telemetry_account_fallback),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (t.metroArea.isNotBlank()) "${t.metroArea} (${t.billingCountryCode})" else stringResource(R.string.telemetry_account_local),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (t.isAccountPaused) {
                        UnhingeBadge(
                            label = stringResource(R.string.telemetry_badge_paused),
                            icon = Icons.Default.PauseCircle,
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.error,
                            showPulseDot = false
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    MetaLabelValue(stringResource(R.string.telemetry_meta_created), formatDate(t.createdTimestamp))
                    MetaLabelValue(stringResource(R.string.telemetry_meta_synced), formatDate(t.lastHingeSync))
                }
            }
        }

        UnhingeDoubleBezelCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeTokens.Card,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            HingeIcons.HeartVector,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.telemetry_section_quotas),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    UnhingeBadge(
                        label = stringResource(R.string.telemetry_reset_in_format, countdownText),
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = ShapeTokens.CardNested,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.telemetry_likes_remaining),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${t.apiAvailableLikes} / 8",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (t.apiAvailableLikes > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                            )
                        }

                        val progress by animateFloatAsState(
                            targetValue = (t.apiAvailableLikes.coerceIn(0, 8)) / 8f,
                            label = "LikesProgress"
                        )
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricBadge(
                        title = stringResource(R.string.metric_free_roses),
                        value = "${t.apiAvailableSuperLikes}",
                        icon = HingeIcons.RoseVector,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricBadge(
                        title = stringResource(R.string.metric_snoozes),
                        value = "${t.apiAvailableSnoozes}",
                        icon = Icons.Default.Snooze,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricBadge(
                        title = stringResource(R.string.metric_undo_skips),
                        value = "${t.apiAvailableSkipUndos}",
                        icon = Icons.AutoMirrored.Filled.Undo,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryMetricBadge(
                        title = stringResource(R.string.metric_priority_likes),
                        value = "${t.apiAvailableAlcPriorityLikes}",
                        icon = Icons.Default.FlashOn,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricBadge(
                        title = stringResource(R.string.metric_boosts_left),
                        value = "${t.boostsAvailable}",
                        icon = Icons.Default.Bolt,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryMetricBadge(
                        title = stringResource(R.string.metric_selectivity),
                        value = "${behavior.selectivityRatio.toInt()}%",
                        icon = Icons.Default.FilterAlt,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        UnhingeDoubleBezelCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeTokens.Card,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.MarkEmailUnread,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = stringResource(R.string.telemetry_section_inbound),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = stringResource(R.string.telemetry_inbound_waiting_format, inLikes.totalLikes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    InboundTypePill(
                        label = stringResource(R.string.telemetry_type_comment),
                        count = inLikes.commentLikes,
                        icon = Icons.Default.ChatBubble,
                        modifier = Modifier.weight(1f)
                    )
                    InboundTypePill(
                        label = stringResource(R.string.telemetry_type_photo),
                        count = inLikes.photoLikes,
                        icon = Icons.Default.Photo,
                        modifier = Modifier.weight(1f)
                    )
                    InboundTypePill(
                        label = stringResource(R.string.telemetry_type_plain),
                        count = inLikes.plainLikes,
                        icon = HingeIcons.HeartVector,
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    text = stringResource(R.string.telemetry_cache_timestamp_format, formatDate(t.likesCacheTimestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        UnhingeDoubleBezelCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ShapeTokens.Card,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Default.Analytics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = stringResource(R.string.telemetry_section_algo),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DataMetricRow(stringResource(R.string.telemetry_metric_likes_sent), "${t.likesSentForPostLikeEncouragement}")
                    DataMetricRow(stringResource(R.string.telemetry_metric_discover_likes), "${t.sendLikesOnDiscoverCounter}")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DataMetricRow(stringResource(R.string.telemetry_metric_rose_dialogs), "${t.timesSeenSendRoseInsteadDialog}")
                    DataMetricRow(stringResource(R.string.telemetry_metric_boost_dialogs), "${t.totalBoostUpsellPresentations}")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DataMetricRow(stringResource(R.string.telemetry_metric_profiles_seen), "${behavior.profilesSeen}")
                    DataMetricRow(stringResource(R.string.telemetry_metric_pass_streak), "${behavior.consecutivePassStreak}")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun MetaLabelValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun DataMetricRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TelemetryMetricBadge(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 74.dp),
        shape = ShapeTokens.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    softWrap = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(15.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun InboundTypePill(
    label: String,
    count: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = ShapeTokens.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$count",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TelemetrySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
            shape = RoundedCornerShape(18.dp)
        )
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            shape = RoundedCornerShape(18.dp)
        )
        SkeletonBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(18.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SkeletonBox(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp),
                shape = RoundedCornerShape(14.dp)
            )
            SkeletonBox(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp),
                shape = RoundedCornerShape(14.dp)
            )
        }
    }
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "N/A"
    return try {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    } catch (e: Exception) {
        "N/A"
    }
}
