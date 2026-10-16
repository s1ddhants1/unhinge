package io.github.s1ddhants1.unhinge.hook.ui.sheet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.s1ddhants1.unhinge.ai.OpenerChatMessage
import io.github.s1ddhants1.unhinge.ai.PromptEntry
import io.github.s1ddhants1.unhinge.ui.theme.HingeFonts

@Composable
fun OpenerSteeringSection(
    entry: PromptEntry?,
    activeReply: String,
    commentBg: Color,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    actionBtnBg: Color,
    actionBtnText: Color,
    pillBg: Color,
    pillBorder: Color,
    onSteer: (String) -> Unit,
    onCopy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val modernEraRegular = remember(context) { HingeFonts.modernEraRegular(context) }
    val modernEraMedium = remember(context) { HingeFonts.modernEraMedium(context) }
    val modernEraBold = remember(context) { HingeFonts.modernEraBold(context) }

    val chatMessages by (entry?.followUpMessagesFlow?.collectAsState() ?: remember { mutableStateOf(emptyList()) })
    val isSteering by (entry?.isSteeringFlow?.collectAsState() ?: remember { mutableStateOf(false) })

    var customInput by remember { mutableStateOf("") }

    val quickSteerPills = remember {
        listOf(
            "Funnier & playful",
            "Shorter & punchier",
            "Ask an engaging question",
            "More casual",
            "Witty mock-challenge",
            "More flirty",
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cardBg.copy(alpha = 0.65f))
            .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
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
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = actionBtnBg,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "Steer & refine this opener",
                    fontFamily = modernEraBold,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )
            }

            if (isSteering) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.8.dp,
                        color = actionBtnBg,
                    )
                    Text(
                        text = "Refining...",
                        fontFamily = modernEraRegular,
                        fontSize = 11.sp,
                        color = textSecondary,
                    )
                }
            }
        }

        // Quick Steer Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            quickSteerPills.forEach { pill ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = pillBg,
                    border = BorderStroke(1.dp, pillBorder),
                    modifier = Modifier.clickable(enabled = !isSteering) {
                        onSteer(pill)
                    },
                ) {
                    Text(
                        text = pill,
                        fontFamily = modernEraMedium,
                        fontSize = 11.sp,
                        color = if (isSteering) textSecondary.copy(alpha = 0.5f) else textPrimary,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Chat History for this Opener
        if (chatMessages.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chatMessages.forEach { msg ->
                    if (msg.isUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = actionBtnBg.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, actionBtnBg.copy(alpha = 0.3f)),
                            ) {
                                Text(
                                    text = msg.text,
                                    fontFamily = modernEraMedium,
                                    fontSize = 12.sp,
                                    color = textPrimary,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                    } else if (msg.text.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = commentBg,
                            border = BorderStroke(1.dp, cardBorder),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = msg.text,
                                    fontFamily = modernEraMedium,
                                    fontSize = 13.5.sp,
                                    lineHeight = 19.sp,
                                    color = textPrimary,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(50),
                                        color = pillBg,
                                        border = BorderStroke(1.dp, pillBorder),
                                        modifier = Modifier.clickable {
                                            entry?.addReply(msg.text, selectNew = true)
                                            onCopy(msg.text)
                                        },
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = actionBtnBg,
                                                modifier = Modifier.size(11.dp),
                                            )
                                            Text(
                                                text = "Use & Copy",
                                                fontFamily = modernEraMedium,
                                                fontSize = 10.5.sp,
                                                color = textPrimary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Steer Input Field
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(commentBg)
                .border(BorderStroke(1.dp, cardBorder), RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = customInput,
                onValueChange = { customInput = it },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 6.dp),
                textStyle = TextStyle(
                    fontFamily = modernEraRegular,
                    fontSize = 13.sp,
                    color = textPrimary,
                ),
                singleLine = true,
                cursorBrush = SolidColor(actionBtnBg),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Send,
                    keyboardType = KeyboardType.Text,
                ),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val text = customInput.trim()
                        if (text.isNotBlank() && !isSteering) {
                            customInput = ""
                            onSteer(text)
                        }
                    },
                ),
                decorationBox = { innerTextField ->
                    if (customInput.isEmpty()) {
                        Text(
                            text = "Steer this opener (e.g., mention skiing)...",
                            fontFamily = modernEraRegular,
                            fontSize = 13.sp,
                            color = textSecondary.copy(alpha = 0.65f),
                        )
                    }
                    innerTextField()
                },
            )

            Surface(
                shape = CircleShape,
                color = if (customInput.isNotBlank() && !isSteering) actionBtnBg else Color.Transparent,
                modifier = Modifier.size(28.dp),
            ) {
                IconButton(
                    onClick = {
                        val text = customInput.trim()
                        if (text.isNotBlank() && !isSteering) {
                            customInput = ""
                            onSteer(text)
                        }
                    },
                    enabled = customInput.isNotBlank() && !isSteering,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Steer",
                        tint = if (customInput.isNotBlank() && !isSteering) actionBtnText else textSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
    }
}
