package io.github.s1ddhants1.unhinge.hook.ui.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SheetLoadingState(
    text: String,
    actionBtnBg: Color,
    textSecondary: Color,
    modernEraRegular: FontFamily,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                strokeWidth = 2.5.dp,
                color = actionBtnBg,
            )
            Text(
                text = text,
                fontFamily = modernEraRegular,
                fontSize = 14.sp,
                color = textSecondary,
            )
        }
    }
}

@Composable
fun SheetNoMatchChatState(
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    modernEraBold: FontFamily,
    modernEraRegular: FontFamily,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ChatBubbleOutline,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = textSecondary,
                )
                Text(
                    text = "No match chat detected",
                    fontFamily = modernEraBold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )
                Text(
                    text = "Open a conversation from your Hinge Matches tab, then tap the AI button to load reply suggestions for that match.",
                    fontFamily = modernEraRegular,
                    fontSize = 13.5.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
fun SheetNoCandidateState(
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    modernEraBold: FontFamily,
    modernEraRegular: FontFamily,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = cardBg,
            border = BorderStroke(1.dp, cardBorder),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.PersonSearch,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = textSecondary,
                )
                Text(
                    text = "No candidate on screen",
                    fontFamily = modernEraBold,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                )
                Text(
                    text = "Navigate to Hinge's Discover or Likes You feed, then tap the AI button to load openers for the active profile.",
                    fontFamily = modernEraRegular,
                    fontSize = 13.5.sp,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}

@Composable
fun SheetSetupRequiredState(
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    actionBtnBg: Color,
    actionBtnText: Color,
    modernEraBold: FontFamily,
    modernEraMedium: FontFamily,
    modernEraRegular: FontFamily,
    provider: String,
    model: String,
    candidateName: String,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = cardBg,
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Setup required",
                fontFamily = modernEraBold,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = textSecondary,
            )
            Text(
                text = "Connect your AI API token to craft tailored openers.",
                fontFamily = modernEraBold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                lineHeight = 24.sp,
            )
            Text(
                text = if (provider.equals("Zen", ignoreCase = true)) {
                    "An API key is required for paid model $model. Switch to a free model (e.g. muse-spark-1.3-contributor-free) or configure your key in Settings."
                } else {
                    "Configure your $provider token in Unhinge Settings to generate conversation starters for $candidateName."
                },
                fontFamily = modernEraRegular,
                fontSize = 13.sp,
                color = textSecondary,
                lineHeight = 18.sp,
            )
            Button(
                onClick = onConfigure,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = actionBtnBg,
                    contentColor = actionBtnText,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(
                    text = "Configure in Settings",
                    fontFamily = modernEraMedium,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
