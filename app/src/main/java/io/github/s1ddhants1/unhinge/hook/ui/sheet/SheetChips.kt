package io.github.s1ddhants1.unhinge.hook.ui.sheet

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun QuickActionChip(
    label: String,
    bg: Color,
    border: Color,
    textColor: Color,
    font: FontFamily,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = label,
            fontFamily = font,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 6.dp),
        )
    }
}

@Composable
fun VibeChip(
    label: String,
    isSelected: Boolean,
    selectedBg: Color,
    selectedText: Color,
    unselectedBg: Color,
    unselectedBorder: Color,
    unselectedText: Color,
    font: FontFamily,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (isSelected) selectedBg else unselectedBg,
        border = BorderStroke(1.dp, if (isSelected) selectedBg else unselectedBorder),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = label,
            fontFamily = font,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (isSelected) selectedText else unselectedText,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun RefinePill(
    label: String,
    bg: Color,
    border: Color,
    textColor: Color,
    font: FontFamily,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = bg,
        border = BorderStroke(1.dp, border),
        modifier = Modifier.clickable { onClick() },
    ) {
        Text(
            text = label,
            fontFamily = font,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}
