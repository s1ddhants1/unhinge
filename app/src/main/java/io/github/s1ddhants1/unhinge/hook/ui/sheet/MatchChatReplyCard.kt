package io.github.s1ddhants1.unhinge.hook.ui.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.s1ddhants1.unhinge.ai.AiWingmanHelper
import io.github.s1ddhants1.unhinge.ai.PromptEntry

data class ChatArchetypeInfo(
    val title: String,
    val subtitle: String,
    val description: String,
)

@Composable
fun MatchChatReplyCard(
    archetype: ChatArchetypeInfo,
    page: Int,
    totalCount: Int,
    isCurrentPage: Boolean,
    promptEntry: PromptEntry?,
    status: AiWingmanHelper.WingmanStatus,
    isCopied: Boolean,
    cardBg: Color,
    cardBorder: Color,
    commentBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    actionBtnBg: Color,
    actionBtnText: Color,
    pillBg: Color,
    pillBorder: Color,
    isDark: Boolean,
    modernEraBold: FontFamily,
    modernEraMedium: FontFamily,
    modernEraRegular: FontFamily,
    matchDisplayName: String,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSettings: () -> Unit,
    onAskAi: (String) -> Unit,
    onSteer: (String) -> Unit,
    onInsert: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reply by (promptEntry?.suggestedReplyFlow?.collectAsState() ?: remember { mutableStateOf(null) })

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (isCurrentPage) 1.5.dp else 1.dp,
            color = if (isCurrentPage) (if (isDark) Color(0xFF4A4A4A) else Color(0xFFC0C0BE)) else cardBorder,
        ),
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${page + 1} of $totalCount · Reply Suggestion",
                        fontFamily = modernEraBold,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = textSecondary,
                    )

                    Surface(
                        shape = RoundedCornerShape(50),
                        color = pillBg,
                        border = BorderStroke(1.dp, pillBorder),
                        modifier = Modifier.clickable {
                            onAskAi("Give me a sharper, ultra-witty reply variation for $matchDisplayName using the \"${archetype.title}\" style.")
                        },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = textSecondary,
                                modifier = Modifier.size(11.dp),
                            )
                            Text(
                                text = "Ask AI",
                                fontFamily = modernEraMedium,
                                fontSize = 11.sp,
                                color = textSecondary,
                            )
                        }
                    }
                }

                Text(
                    text = archetype.title,
                    fontFamily = modernEraBold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )

                Text(
                    text = archetype.description,
                    fontFamily = modernEraRegular,
                    fontSize = 13.sp,
                    color = textSecondary,
                    lineHeight = 18.sp,
                )
            }

            Spacer(Modifier.height(14.dp))

            NativeHingeCommentBubble(
                entry = promptEntry,
                replyText = reply,
                status = status,
                isCopied = isCopied,
                commentBg = commentBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                actionBtnBg = actionBtnBg,
                actionBtnText = actionBtnText,
                onCopy = onCopy,
                onRegenerate = onRegenerate,
                onOpenSettings = onOpenSettings,
                onInsert = onInsert,
                onSteer = onSteer,
                isChatMode = true,
            )
        }
    }
}
