package io.github.s1ddhants1.unhinge.ui.component

import io.github.s1ddhants1.unhinge.R
import androidx.compose.ui.res.stringResource
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.model.CompleteHingeData
import io.github.s1ddhants1.unhinge.model.PlayerAnswerItem
import io.github.s1ddhants1.unhinge.model.PlayerMediaItem
import io.github.s1ddhants1.unhinge.ui.theme.AccentSuccess

@Composable
fun ProfileRawScreen(
    data: CompleteHingeData,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val t = data.telemetry
    val audit = data.profileAudit
    val photos = data.playerMedia
    val answers = data.playerAnswers
    val ratingFallback = stringResource(R.string.profile_rating_default)

    fun copy(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, context.getString(R.string.toast_copied_format, label), Toast.LENGTH_SHORT).show()
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
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Column {
                            Text(
                                text = if (t.firstName.isNotBlank()) stringResource(R.string.profile_title_format, t.firstName) else stringResource(R.string.nav_tab_title_profile),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.profile_photos_prompts_format, photos.size, answers.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val percent = (t.profileCompleteness * 100).toInt()
                    UnhingeBadge(
                        label = stringResource(R.string.profile_complete_format, percent),
                        containerColor = if (percent >= 100) AccentSuccess.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (percent >= 100) AccentSuccess else MaterialTheme.colorScheme.primary
                    )
                }

                val completenessProgress by animateFloatAsState(
                    targetValue = t.profileCompleteness.coerceIn(0f, 1f),
                    label = "ProfileCompleteness"
                )
                LinearProgressIndicator(
                    progress = { completenessProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnhingeBadge(
                        label = if (t.isSmartPhotoOptedIn) stringResource(R.string.profile_smart_photos_on) else stringResource(R.string.profile_smart_photos_off),
                        icon = Icons.Default.AutoAwesome,
                        containerColor = if (t.isSmartPhotoOptedIn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (t.isSmartPhotoOptedIn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UnhingeBadge(
                        label = if (t.isCircleOptIn) stringResource(R.string.profile_circle_yes) else stringResource(R.string.profile_circle_no),
                        icon = Icons.Default.Groups,
                        containerColor = if (t.isCircleOptIn) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (t.isCircleOptIn) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (audit.promptEvaluationStatus.isNotBlank() || audit.promptFeedbackDetail.isNotBlank() || audit.coachingTips.isNotEmpty()) {
            UnhingeDoubleBezelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeTokens.Card,
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Psychology,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = stringResource(R.string.profile_server_eval),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        UnhingeBadge(
                            label = stringResource(R.string.profile_rating_format, audit.promptEvaluationStatus.ifBlank { ratingFallback }),
                            containerColor = AccentSuccess.copy(alpha = 0.18f),
                            contentColor = AccentSuccess
                        )
                    }

                    if (audit.promptFeedbackDetail.isNotBlank()) {
                        Surface(
                            shape = ShapeTokens.CardNested,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .hairlineBorder(ShapeTokens.CardNested)
                        ) {
                            Text(
                                text = "\"${audit.promptFeedbackDetail}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp)
                            )
                        }
                    }

                    if (audit.coachingTips.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.profile_ai_coaching_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        audit.coachingTips.forEach { tip ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .size(16.dp)
                                        .padding(top = 2.dp)
                                )
                                Text(
                                    text = tip,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        ProfileSectionHeader(title = stringResource(R.string.profile_section_media), badgeText = stringResource(R.string.profile_section_media_count, photos.size))

        val validMedia = remember(photos) { photos.filter { it.photoUrl.isNotBlank() } }
        if (validMedia.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.PhotoLibrary,
                title = stringResource(R.string.profile_empty_photos),
                description = stringResource(R.string.profile_empty_photos_desc),
            )
        } else {
            HingePhotoCarousel(
                photos = validMedia.map { it.photoUrl },
                captions = validMedia.map { it.promptCaption },
                candidateName = if (t.firstName.isNotBlank()) stringResource(R.string.profile_title_format, t.firstName) else "",
                onCopyUrl = { copy(context.getString(R.string.copy_photo_url), it) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        ProfileSectionHeader(title = stringResource(R.string.profile_section_prompts), badgeText = stringResource(R.string.profile_section_prompts_count, answers.size))

        if (answers.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.FormatQuote,
                title = stringResource(R.string.profile_empty_prompts),
                description = stringResource(R.string.profile_empty_prompts_desc),
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                answers.forEachIndexed { idx, answer ->
                    PlayerAnswerCard(
                        answer = answer,
                        index = idx + 1,
                        onCopy = { copy(context.getString(R.string.copy_prompt_answer), "${answer.questionText}: ${answer.responseText}") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun PlayerAnswerCard(
    answer: PlayerAnswerItem,
    index: Int,
    onCopy: () -> Unit
) {
    UnhingeDoubleBezelCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ShapeTokens.Card,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onCopy
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        modifier = Modifier.size(24.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "$index",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = answer.questionText.ifBlank { stringResource(R.string.profile_prompt_question_fallback) },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = stringResource(R.string.cd_copy),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = answer.responseText.ifBlank { stringResource(R.string.profile_empty_response) },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ProfileSectionHeader(title: String, badgeText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.hairlineBorder(CircleShape)
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
