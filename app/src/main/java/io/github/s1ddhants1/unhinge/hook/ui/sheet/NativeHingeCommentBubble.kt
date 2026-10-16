package io.github.s1ddhants1.unhinge.hook.ui.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.s1ddhants1.unhinge.ai.AiWingmanHelper
import io.github.s1ddhants1.unhinge.ai.PromptEntry
import io.github.s1ddhants1.unhinge.ui.theme.HingeFonts

@Composable
fun NativeHingeCommentBubble(
    entry: PromptEntry?,
    replyText: String?,
    status: AiWingmanHelper.WingmanStatus,
    isCopied: Boolean,
    commentBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    actionBtnBg: Color,
    actionBtnText: Color,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSettings: () -> Unit,
    onInsert: ((String) -> Unit)? = null,
    onSteer: ((String) -> Unit)? = null,
    isChatMode: Boolean = false,
) {

    val context = LocalContext.current
    val modernEraRegular = remember(context) { HingeFonts.modernEraRegular(context) }
    val modernEraMedium = remember(context) { HingeFonts.modernEraMedium(context) }
    val modernEraBold = remember(context) { HingeFonts.modernEraBold(context) }

    val replies by (entry?.repliesFlow?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val activeIndex by (entry?.activeReplyIndexFlow?.collectAsState() ?: remember { mutableIntStateOf(0) })
    val isEntryGenerating by (entry?.isGeneratingFlow?.collectAsState() ?: remember { mutableStateOf(false) })
    var isSteerOpen by remember { mutableStateOf(false) }

    val activeReply = replies.getOrNull(activeIndex) ?: replyText

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = commentBg,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = textSecondary,
                    )
                    Text(
                        text = if (isChatMode) "Wingman reply" else "Wingman opener",
                        fontFamily = modernEraBold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (replies.size > 1) {
                        IconButton(
                            onClick = { entry?.selectPreviousReply() },
                            enabled = activeIndex > 0,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronLeft,
                                contentDescription = if (isChatMode) "Previous reply" else "Previous opener",
                                modifier = Modifier.size(16.dp),
                                tint = if (activeIndex > 0) textPrimary else textSecondary.copy(alpha = 0.3f),
                            )
                        }

                        Text(
                            text = "${activeIndex + 1} of ${replies.size}",
                            fontFamily = modernEraMedium,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textSecondary,
                        )

                        IconButton(
                            onClick = { entry?.selectNextReply() },
                            enabled = activeIndex < replies.size - 1,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = if (isChatMode) "Next reply" else "Next opener",
                                modifier = Modifier.size(16.dp),
                                tint = if (activeIndex < replies.size - 1) textPrimary else textSecondary.copy(alpha = 0.3f),
                            )
                        }

                        Spacer(Modifier.width(2.dp))
                    }

                    if (isEntryGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(4.dp),
                            strokeWidth = 1.8.dp,
                            color = actionBtnBg,
                        )
                    } else if (!activeReply.isNullOrBlank() || replies.isNotEmpty()) {
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = if (isChatMode) "Regenerate reply" else "Regenerate opener",
                                modifier = Modifier.size(15.dp),
                                tint = textSecondary,
                            )
                        }
                    }
                }
            }

            if (!activeReply.isNullOrBlank()) {
                Text(
                    text = activeReply,
                    fontFamily = modernEraMedium,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = textPrimary,
                    modifier = Modifier.clickable { onCopy(activeReply) },
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onSteer != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isSteerOpen) actionBtnBg.copy(alpha = 0.2f) else HingeDesignTokens.LightPillUnselected,
                                border = BorderStroke(1.dp, if (isSteerOpen) actionBtnBg else Color.Transparent),
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable { isSteerOpen = !isSteerOpen },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tune,
                                        contentDescription = null,
                                        tint = if (isSteerOpen) actionBtnBg else textSecondary,
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = "Steer",
                                        fontFamily = modernEraMedium,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSteerOpen) textPrimary else textSecondary,
                                    )
                                }
                            }
                        }

                        val isCopySuccess = isCopied
                        val copyBtnBg = if (isCopySuccess) HingeDesignTokens.SuccessGreen else (if (onInsert != null) HingeDesignTokens.LightPillUnselected else actionBtnBg)
                        val copyBtnText = if (isCopySuccess) Color.White else (if (onInsert != null) textPrimary else actionBtnText)

                        Surface(
                            shape = RoundedCornerShape(50),
                            color = copyBtnBg,
                            modifier = Modifier
                                .height(34.dp)
                                .clickable { onCopy(activeReply) },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Icon(
                                    imageVector = if (isCopySuccess) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    tint = copyBtnText,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = if (isCopySuccess) "Copied!" else "Copy",
                                    fontFamily = modernEraMedium,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = copyBtnText,
                                )
                            }
                        }

                        if (onInsert != null) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = actionBtnBg,
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable { onInsert(activeReply) },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = null,
                                        tint = actionBtnText,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = "Insert",
                                        fontFamily = modernEraMedium,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = actionBtnText,
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isSteerOpen && onSteer != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    OpenerSteeringSection(
                        entry = entry,
                        activeReply = activeReply,
                        commentBg = commentBg,
                        cardBg = commentBg,
                        cardBorder = HingeDesignTokens.LightBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        actionBtnBg = actionBtnBg,
                        actionBtnText = actionBtnText,
                        pillBg = HingeDesignTokens.LightPillUnselected,
                        pillBorder = HingeDesignTokens.LightPillBorder,
                        onSteer = { onSteer?.invoke(it) },
                        onCopy = onCopy,
                    )
                }

            } else {
                when (status) {
                    is AiWingmanHelper.WingmanStatus.Generating -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.8.dp,
                                color = textSecondary,
                            )
                            Text(
                                text = "Crafting reply...",
                                fontFamily = modernEraRegular,
                                fontSize = 13.sp,
                                color = textSecondary,
                            )
                        }
                    }
                    is AiWingmanHelper.WingmanStatus.Error -> {
                        val errorMsg = status.message
                        val isAuthError = errorMsg.contains("auth", ignoreCase = true) ||
                            errorMsg.contains("API key", ignoreCase = true) ||
                            errorMsg.contains("401", ignoreCase = true)

                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isAuthError) Modifier.clickable { onOpenSettings() } else Modifier),
                        ) {
                            Text(
                                text = errorMsg,
                                fontFamily = modernEraRegular,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                            if (isAuthError) {
                                Text(
                                    text = "Tap here to update your token in Settings",
                                    fontFamily = modernEraBold,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                    else -> {
                        Text(
                            text = "Ask AI below or select a tone to craft conversation starters.",
                            fontFamily = modernEraRegular,
                            fontSize = 13.sp,
                            color = textSecondary.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }
}
