package io.github.s1ddhants1.unhinge.hook.ui.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.s1ddhants1.unhinge.ui.theme.HingeFonts
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class CustomAiInteraction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val query: String,
    val reply: String = "",
    val isStreaming: Boolean = true,
    val error: String? = null,
)

@Composable
fun CustomAiResponseCard(
    interaction: CustomAiInteraction,
    index: Int,
    isSelected: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    actionBtnBg: Color,
    actionBtnText: Color,
    commentBg: Color,
    pillBg: Color,
    pillBorder: Color,
    isDark: Boolean = false,
    onCopy: (String) -> Unit,
    onRegenerate: () -> Unit,
    onDismiss: () -> Unit,
    onRefine: (String) -> Unit,
    onInsert: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val modernEraRegular = remember(context) { HingeFonts.modernEraRegular(context) }
    val modernEraMedium = remember(context) { HingeFonts.modernEraMedium(context) }
    val modernEraBold = remember(context) { HingeFonts.modernEraBold(context) }

    var isCopied by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardBg,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) (if (isDark) Color(0xFF4A4A4A) else Color(0xFFC0C0BE)) else cardBorder,
        ),
        modifier = Modifier
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false),
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = actionBtnBg,
                            modifier = Modifier.size(15.dp),
                        )
                        Text(
                            text = "Ask AI #$index",
                            fontFamily = modernEraBold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = actionBtnBg,
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(24.dp),
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = textSecondary.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = commentBg.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "“${interaction.query}”",
                        fontFamily = modernEraMedium,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        fontStyle = FontStyle.Italic,
                        color = textPrimary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                when {
                    interaction.error != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = interaction.error,
                                fontFamily = modernEraRegular,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                            TextButton(
                                onClick = onRegenerate,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = "Retry",
                                    fontFamily = modernEraMedium,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = actionBtnBg,
                                )
                            }
                        }
                    }
                    interaction.isStreaming && interaction.reply.isBlank() -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(vertical = 6.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = actionBtnBg,
                            )
                            Text(
                                text = "Crafting custom response...",
                                fontFamily = modernEraRegular,
                                fontSize = 13.5.sp,
                                color = textSecondary,
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = interaction.reply,
                            fontFamily = modernEraMedium,
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Normal,
                            lineHeight = 22.sp,
                            color = textPrimary,
                        )
                    }
                }
            }

            if (interaction.reply.isNotBlank() && interaction.error == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!interaction.isStreaming) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            RefinePill("Shorter", pillBg, pillBorder, textPrimary, modernEraMedium) {
                                onRefine("Make this shorter and under 12 words: \"${interaction.reply}\"")
                            }
                            RefinePill("Bolder", pillBg, pillBorder, textPrimary, modernEraMedium) {
                                onRefine("Make this bolder, flirtier, and more playful: \"${interaction.reply}\"")
                            }
                            RefinePill("Teasing", pillBg, pillBorder, textPrimary, modernEraMedium) {
                                onRefine("Add playful teasing and dry humor to this: \"${interaction.reply}\"")
                            }
                            RefinePill("Date pitch", pillBg, pillBorder, textPrimary, modernEraMedium) {
                                onRefine("Convert this into a smooth, low-pressure first date invitation: \"${interaction.reply}\"")
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (isCopied) HingeDesignTokens.VerifiedBlue else (if (onInsert != null) pillBg else actionBtnBg),
                                border = if (onInsert != null && !isCopied) BorderStroke(1.dp, pillBorder) else null,
                                modifier = Modifier
                                    .height(34.dp)
                                    .clickable(enabled = interaction.reply.isNotBlank()) {
                                        onCopy(interaction.reply)
                                        isCopied = true
                                        coroutineScope.launch {
                                            delay(2000)
                                            isCopied = false
                                        }
                                    },
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                        contentDescription = null,
                                        tint = if (onInsert != null && !isCopied) textPrimary else actionBtnText,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = if (isCopied) "Copied!" else "Copy",
                                        fontFamily = modernEraMedium,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (onInsert != null && !isCopied) textPrimary else actionBtnText,
                                    )
                                }
                            }

                            if (onInsert != null) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = actionBtnBg,
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clickable(enabled = interaction.reply.isNotBlank()) {
                                            onInsert(interaction.reply)
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                            tint = actionBtnText,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = "Insert",
                                            fontFamily = modernEraMedium,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = actionBtnText,
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate",
                                tint = textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
