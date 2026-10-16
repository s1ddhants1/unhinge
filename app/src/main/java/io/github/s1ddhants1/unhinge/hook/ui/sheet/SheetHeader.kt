package io.github.s1ddhants1.unhinge.hook.ui.sheet

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.ImageLoader
import coil3.compose.SubcomposeAsyncImage
import io.github.s1ddhants1.unhinge.hook.ui.HostLikesReader
import io.github.s1ddhants1.unhinge.hook.ui.HingeIcons
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile
import io.github.s1ddhants1.unhinge.model.ChatMessageRecord
import io.github.s1ddhants1.unhinge.model.MatchRecord

@Composable
fun SheetHeader(
    isChatMode: Boolean,
    candidate: CachedCandidateProfile?,
    chatMatch: MatchRecord?,
    chatMessages: List<ChatMessageRecord>,
    chatLastMessage: ChatMessageRecord?,
    isMyTurn: Boolean,
    matchDisplayName: String,
    matchAge: Int,
    matchPhotoUrl: String,
    showDragHandle: Boolean,
    standalone: Boolean,
    isDark: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    modernEraBold: FontFamily,
    modernEraRegular: FontFamily,
    imageLoader: ImageLoader,
    showAvailableLikes: Boolean,
    dragModifier: Modifier,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    if (showDragHandle && standalone) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .padding(horizontal = 18.dp)
                .then(dragModifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (isDark) Color(0xFF383838) else Color(0xFFD6D6D4)),
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isChatMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .then(dragModifier),
            ) {
                if (matchPhotoUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model = matchPhotoUrl,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = matchDisplayName.take(1).ifBlank { "?" },
                                fontFamily = modernEraBold,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val title = buildString {
                            append(matchDisplayName.ifBlank { "Match" })
                            if (matchAge > 0) append(", $matchAge")
                        }
                        Text(
                            text = title,
                            fontFamily = modernEraBold,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Surface(
                            color = Color(0xFF8E24AA).copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFFAB47BC),
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(
                                    text = "Match Chat",
                                    fontFamily = modernEraBold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFAB47BC),
                                )
                            }
                        }
                    }

                    val subtitle = if (chatLastMessage != null) {
                        if (isMyTurn) "Your turn to reply" else "Waiting on their reply"
                    } else if (chatMessages.isNotEmpty()) {
                        "${chatMessages.size} messages exchanged"
                    } else {
                        "Ready for your first message"
                    }

                    Text(
                        text = subtitle,
                        fontFamily = modernEraRegular,
                        fontSize = 13.sp,
                        color = textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        } else if (candidate != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .then(dragModifier),
            ) {
                val photoUrl = candidate.photos.firstOrNull()
                if (photoUrl != null) {
                    SubcomposeAsyncImage(
                        model = photoUrl,
                        imageLoader = imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(cardBg),
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = cardBg,
                        border = BorderStroke(1.dp, cardBorder),
                        modifier = Modifier.size(48.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = candidate.firstName.take(1).ifBlank { "?" },
                                fontFamily = modernEraBold,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = textPrimary,
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val title = buildString {
                            append(candidate.firstName.ifBlank { "Candidate" })
                            if (candidate.age > 0) {
                                append(", ${candidate.age}")
                            }
                        }
                        Text(
                            text = title,
                            fontFamily = modernEraBold,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val activeStatusId = candidate.lastActiveStatusId
                        if (activeStatusId != null && activeStatusId > 0) {
                            val isActiveNow = activeStatusId == 1
                            Surface(
                                color = if (isActiveNow) Color(0xFF1B5E20).copy(alpha = 0.14f) else cardBg,
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    if (isActiveNow) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .background(Color(0xFF2E7D32), CircleShape),
                                        )
                                    }
                                    Text(
                                        text = if (isActiveNow) "Active now" else "Active today",
                                        fontFamily = modernEraBold,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isActiveNow) Color(0xFF2E7D32) else textSecondary,
                                    )
                                }
                            }
                        }
                    }

                    val subtitle = listOfNotNull(
                        candidate.jobTitle.takeIf { it.isNotBlank() },
                        candidate.location.takeIf { it.isNotBlank() },
                    ).joinToString(" · ")

                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontFamily = modernEraRegular,
                            fontSize = 13.sp,
                            color = textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        } else {
            Text(
                text = "AI Wingman",
                fontFamily = modernEraBold,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                modifier = Modifier
                    .weight(1f)
                    .then(dragModifier),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showAvailableLikes) {
                var likesState by remember { mutableStateOf(HostLikesReader.getLikesInfo(context)) }
                DisposableEffect(context) {
                    val observer: (HostLikesReader.LikesState) -> Unit = { updated ->
                        likesState = updated
                    }
                    HostLikesReader.registerObserver(context, observer)
                    onDispose {
                        HostLikesReader.unregisterObserver(observer)
                    }
                }

                val heartTint = when {
                    likesState.availableLikes == 0 -> Color(0xFFE53935)
                    likesState.availableLikes in 1..2 -> Color(0xFFFFA000)
                    else -> Color(0xFFED5564)
                }

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = cardBg,
                    border = BorderStroke(1.dp, cardBorder),
                    modifier = Modifier
                        .height(36.dp)
                        .clickable {
                            Toast.makeText(context, likesState.formattedSummary, Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.fillMaxHeight(),
                    ) {
                        Icon(
                            imageVector = HingeIcons.HeartVector,
                            contentDescription = "Available Likes",
                            tint = heartTint,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = likesState.displayLikes,
                            fontFamily = modernEraBold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (likesState.availableLikes == 0) Color(0xFFE53935) else textPrimary,
                        )
                        if (likesState.availableSuperlikes > 0) {
                            Text(
                                text = "•",
                                fontSize = 11.sp,
                                color = textSecondary,
                            )
                            Icon(
                                imageVector = HingeIcons.RoseVector,
                                contentDescription = "Available Roses",
                                tint = Color(0xFFF06292),
                                modifier = Modifier.size(13.dp),
                            )
                            Text(
                                text = likesState.displaySuperlikes,
                                fontFamily = modernEraBold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = textPrimary,
                            )
                        }
                    }
                }
            }

            Surface(
                shape = CircleShape,
                color = cardBg,
                border = BorderStroke(1.dp, cardBorder),
                modifier = Modifier.size(36.dp),
            ) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Close",
                        tint = textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
