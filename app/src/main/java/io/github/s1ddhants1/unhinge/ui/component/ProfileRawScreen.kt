package io.github.s1ddhants1.unhinge.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import io.github.s1ddhants1.unhinge.model.CompleteHingeData
import io.github.s1ddhants1.unhinge.model.PlayerAnswerItem
import io.github.s1ddhants1.unhinge.model.PlayerMediaItem
import io.github.s1ddhants1.unhinge.ui.theme.AccentGold
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

    var previewPhotoUrl by rememberSaveable { mutableStateOf<String?>(null) }

    fun copy(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Profile Configuration & Completeness Card
        UnhingeDoubleBezelCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
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
                                text = if (t.firstName.isNotBlank()) "${t.firstName}'s Profile" else "My Profile Data",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${photos.size} Photos • ${answers.size} Prompts",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    val percent = (t.profileCompleteness * 100).toInt()
                    UnhingeBadge(
                        label = "$percent% COMPLETE",
                        containerColor = if (percent >= 100) AccentSuccess.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (percent >= 100) AccentSuccess else MaterialTheme.colorScheme.primary
                    )
                }

                // Animated Completeness Bar
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

                // Feature Chips Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    UnhingeBadge(
                        label = if (t.isSmartPhotoOptedIn) "Smart Photos: ON" else "Smart Photos: OFF",
                        icon = Icons.Default.AutoAwesome,
                        containerColor = if (t.isSmartPhotoOptedIn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (t.isSmartPhotoOptedIn) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    UnhingeBadge(
                        label = if (t.isCircleOptIn) "Circle Member: YES" else "Circle Member: NO",
                        icon = Icons.Default.Groups,
                        containerColor = if (t.isCircleOptIn) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = if (t.isCircleOptIn) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 2. Server Review & Audits
        if (audit.promptEvaluationStatus.isNotBlank() || audit.promptFeedbackDetail.isNotBlank() || audit.coachingTips.isNotEmpty()) {
            UnhingeDoubleBezelCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
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
                                text = "Server Prompt Evaluation",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        UnhingeBadge(
                            label = "RATING: ${audit.promptEvaluationStatus.ifBlank { "HIGH" }}",
                            containerColor = AccentSuccess.copy(alpha = 0.18f),
                            contentColor = AccentSuccess
                        )
                    }

                    if (audit.promptFeedbackDetail.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier
                                .fillMaxWidth()
                                .hairlineBorder(RoundedCornerShape(14.dp))
                        ) {
                            Text(
                                text = "\"${audit.promptFeedbackDetail}\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(14.dp),
                                lineHeight = 20.sp
                            )
                        }
                    }

                    if (audit.coachingTips.isNotEmpty()) {
                        Text(
                            text = "AI COACHING RECOMMENDATIONS",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
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
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Player Photos Gallery
        ProfileSectionHeader(title = "PROFILE MEDIA ASSETS", badgeText = "${photos.size} PHOTOS")

        if (photos.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.PhotoLibrary,
                title = "No Profile Photos",
                description = "Media records have not been synced from local storage yet."
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(
                    items = photos,
                    key = { index, item -> item.photoUrl.ifBlank { "photo_$index" } }
                ) { index, media ->
                    PlayerMediaCard(
                        media = media,
                        index = index + 1,
                        onClick = {
                            if (media.photoUrl.isNotBlank()) {
                                previewPhotoUrl = media.photoUrl
                            }
                        },
                        onCopyUrl = { copy("Photo URL", media.photoUrl) }
                    )
                }
            }
        }

        // 4. Prompts & Responses
        ProfileSectionHeader(title = "PROFILE PROMPTS & ANSWERS", badgeText = "${answers.size} PROMPTS")

        if (answers.isEmpty()) {
            EmptyStateView(
                icon = Icons.Default.FormatQuote,
                title = "No Prompts Available",
                description = "Answer records will appear here after syncing with local storage."
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                answers.forEachIndexed { idx, answer ->
                    PlayerAnswerCard(
                        answer = answer,
                        index = idx + 1,
                        onCopy = { copy("Prompt Answer", "${answer.questionText}: ${answer.responseText}") }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // Full-Screen Image Dialog
    if (previewPhotoUrl != null) {
        Dialog(
            onDismissRequest = { previewPhotoUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { previewPhotoUrl = null }
            ) {
                SubcomposeAsyncImage(
                    model = previewPhotoUrl,
                    contentDescription = "Full photo preview",
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                IconButton(
                    onClick = { previewPhotoUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PlayerMediaCard(
    media: PlayerMediaItem,
    index: Int,
    onClick: () -> Unit,
    onCopyUrl: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(180.dp)
            .height(250.dp)
            .bouncyClickable(onClick = onClick)
            .hairlineBorder(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (media.photoUrl.isNotBlank()) {
                SubcomposeAsyncImage(
                    model = media.photoUrl,
                    contentDescription = "Photo $index",
                    contentScale = ContentScale.Crop,
                    loading = {
                        SkeletonBox(
                            modifier = Modifier.fillMaxSize(),
                            shape = RoundedCornerShape(18.dp)
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Photo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Top Position Badge
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = "#$index",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // Bottom Gradient Scrim & Caption
            if (media.promptCaption.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                            )
                        )
                        .padding(10.dp)
                ) {
                    Text(
                        text = media.promptCaption,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        maxLines = 2
                    )
                }
            }
        }
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
        shape = RoundedCornerShape(18.dp),
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
                        text = answer.questionText.ifBlank { "Prompt Question" },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = answer.responseText.ifBlank { "(Empty response)" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
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
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.2.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
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
