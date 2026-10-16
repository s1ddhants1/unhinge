package io.github.s1ddhants1.unhinge.hook.ui.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.s1ddhants1.unhinge.model.CachedCandidateProfile

@Composable
fun SheetBottomBar(
    isChatMode: Boolean,
    candidate: CachedCandidateProfile?,
    matchDisplayName: String,
    askAiQuery: String,
    isAskAiGenerating: Boolean,
    pillBg: Color,
    pillBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    actionBtnBg: Color,
    actionBtnText: Color,
    isDark: Boolean,
    modernEraMedium: FontFamily,
    modernEraRegular: FontFamily,
    onQueryChange: (String) -> Unit,
    onSubmitAskAi: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Quick Action Chips Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isChatMode) {
                QuickActionChip(
                    label = "Ask them out",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    onSubmitAskAi("Help me craft a smooth, natural transition to ask $matchDisplayName out for coffee, drinks, or tacos based on our chat.")
                }

                QuickActionChip(
                    label = "Keep it playful",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    onSubmitAskAi("Give me 2 playful, teasing banter replies to keep the momentum going with $matchDisplayName.")
                }

                QuickActionChip(
                    label = "Revive dry chat",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    onSubmitAskAi("Our chat is slowing down. Suggest an unexpected, highly engaging question or callback for $matchDisplayName.")
                }

                QuickActionChip(
                    label = "Analyze vibe",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    onSubmitAskAi("Analyze the vibe, tone, and interest level of this chat so far. Give me tactical advice.")
                }

                QuickActionChip(
                    label = "Translate subtext",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    onSubmitAskAi("What is the underlying tone or subtext of what $matchDisplayName sent, and how should I match that energy?")
                }
            } else {
                QuickActionChip(
                    label = "Vibe check",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    val candidateName = candidate?.firstName?.ifBlank { "her" } ?: "her"
                    onSubmitAskAi("Give me a sharp, 2-sentence vibe check and best conversational angle for $candidateName.")
                }

                QuickActionChip(
                    label = "Playful roast",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    onSubmitAskAi("Give me a light, teasing roast about one of her prompt answers that invites a laugh.")
                }

                QuickActionChip(
                    label = "First date pitch",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    val candidateName = candidate?.firstName?.ifBlank { "her" } ?: "her"
                    onSubmitAskAi("Suggest a low-pressure, tailored first date idea based on $candidateName's bio and interests.")
                }

                QuickActionChip(
                    label = "Hidden hook",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    val candidateName = candidate?.firstName?.ifBlank { "her" } ?: "her"
                    onSubmitAskAi("Spot an understated, witty detail in $candidateName's answers that most matches miss.")
                }

                QuickActionChip(
                    label = "Green & red flags",
                    bg = pillBg,
                    border = pillBorder,
                    textColor = textPrimary,
                    font = modernEraMedium,
                ) {
                    val candidateName = candidate?.firstName?.ifBlank { "her" } ?: "her"
                    onSubmitAskAi("Highlight 2 major green flags and any subtle friction points in $candidateName's profile.")
                }
            }
        }

        // Ask AI Input Field
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (isDark) Color(0xFF242424) else Color(0xFFEFEFEF))
                    .border(
                        BorderStroke(1.dp, if (isDark) Color(0xFF383838) else Color(0xFFE0E0DE)),
                        RoundedCornerShape(26.dp),
                    )
                    .padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Ask AI",
                    tint = if (askAiQuery.isNotBlank() || isAskAiGenerating) actionBtnBg else textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp),
                )

                Spacer(Modifier.width(10.dp))

                BasicTextField(
                    value = askAiQuery,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 10.dp),
                    textStyle = TextStyle(
                        fontFamily = modernEraRegular,
                        fontSize = 14.5.sp,
                        color = textPrimary,
                    ),
                    singleLine = false,
                    maxLines = 3,
                    cursorBrush = SolidColor(actionBtnBg),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send,
                        keyboardType = KeyboardType.Text,
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { onSubmitAskAi(askAiQuery) },
                    ),
                    decorationBox = { innerTextField ->
                        if (askAiQuery.isEmpty()) {
                            Text(
                                text = if (isChatMode) "Ask AI about this chat..." else "Ask AI",
                                fontFamily = modernEraRegular,
                                fontSize = 14.5.sp,
                                color = textSecondary.copy(alpha = 0.7f),
                            )
                        }
                        innerTextField()
                    },
                )

                Spacer(Modifier.width(6.dp))

                Surface(
                    shape = CircleShape,
                    color = if (askAiQuery.isNotBlank() && !isAskAiGenerating) actionBtnBg else Color.Transparent,
                    modifier = Modifier.size(34.dp),
                ) {
                    IconButton(
                        onClick = { onSubmitAskAi(askAiQuery) },
                        enabled = askAiQuery.isNotBlank() && !isAskAiGenerating,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (isAskAiGenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = actionBtnBg,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = "Send",
                                tint = if (askAiQuery.isNotBlank()) actionBtnText else textSecondary.copy(alpha = 0.4f),
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
