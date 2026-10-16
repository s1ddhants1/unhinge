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
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile

@Composable
fun CandidatePromptCard(
    candidate: CachedCandidateProfile,
    page: Int,
    promptCardsCount: Int,
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
    tiemposRegular: FontFamily,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onOpenSettings: () -> Unit,
    onAskAi: (String) -> Unit,
    onSteer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reply by (promptEntry?.suggestedReplyFlow?.collectAsState() ?: remember { mutableStateOf(null) })
    val promptItem = candidate.prompts.getOrNull(page)

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
            if (candidate.prompts.isEmpty() || promptItem == null) {
                // Fallback Bio Card
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "About ${candidate.firstName.ifBlank { "Candidate" }}",
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
                                onAskAi("Give me a sharp, funny angle about ${candidate.firstName.ifBlank { "this profile" }}.")
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

                    val bioText = listOfNotNull(
                        candidate.jobTitle.takeIf { it.isNotBlank() }?.let { "Works as $it" },
                        candidate.school.takeIf { it.isNotBlank() }?.let { "Studied at $it" },
                        candidate.hometown.takeIf { it.isNotBlank() }?.let { "From $it" },
                        candidate.location.takeIf { it.isNotBlank() }?.let { "Lives in $it" },
                        candidate.datingIntention.takeIf { it.isNotBlank() }?.let { "Looking for $it" },
                    ).joinToString("\n")

                    Text(
                        text = bioText.ifBlank { "Active candidate in Hinge feed" },
                        fontFamily = tiemposRegular,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 32.sp,
                        color = textPrimary,
                    )
                }
            } else {
                // Regular Prompt Card
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Prompt ${page + 1} of $promptCardsCount",
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
                                onAskAi("Focus on her prompt \"${promptItem.question}\": \"${promptItem.answer}\" and give me a sharp, funny angle.")
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
                        text = promptItem.question,
                        fontFamily = modernEraBold,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary,
                    )

                    Text(
                        text = promptItem.answer.replace("\\n", "\n"),
                        fontFamily = tiemposRegular,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 32.sp,
                        color = textPrimary,
                    )
                }
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
                onSteer = onSteer,
                isChatMode = false,
            )
        }
    }
}
