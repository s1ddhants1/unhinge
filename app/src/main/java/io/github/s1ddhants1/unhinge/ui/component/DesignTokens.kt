package io.github.s1ddhants1.unhinge.ui.component

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.s1ddhants1.unhinge.R
import kotlinx.coroutines.launch

fun Modifier.bouncyClickable(
    enabled: Boolean = true,
    scaleDown: Float = 0.98f,
    onClick: () -> Unit
): Modifier = composed {
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }

    this
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                coroutineScope.launch {
                    scale.animateTo(
                        scaleDown,
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                val upOrCancel = waitForUpOrCancellation()
                coroutineScope.launch {
                    scale.animateTo(
                        1f,
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    )
                }
                if (upOrCancel != null) {
                    onClick()
                }
            }
        }
}

fun Modifier.shimmerEffect(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    val base = MaterialTheme.colorScheme.surfaceContainer
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    val shimmerColors = listOf(base, highlight, base)

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset.Zero,
        end = Offset(x = translateAnim, y = translateAnim)
    )

    background(brush)
}

fun Modifier.hairlineBorder(
    shape: Shape,
    color: Color? = null,
    width: Dp = 0.5.dp
): Modifier = composed {
    val borderColor = color ?: MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
    border(width = width, color = borderColor, shape = shape)
}

object ShapeTokens {
    val Card = RoundedCornerShape(18.dp)
    val CardNested = RoundedCornerShape(14.dp)
    val Pill = RoundedCornerShape(12.dp)
    val Badge = RoundedCornerShape(8.dp)
    val Search = RoundedCornerShape(16.dp)
}

@Composable
fun UnhingeDoubleBezelCard(
    modifier: Modifier = Modifier,
    shape: Shape = ShapeTokens.Card,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainer,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier
            .bouncyClickable(onClick = onClick)
            .clip(shape)
            .background(containerColor)
            .then(if (borderColor != null) Modifier.hairlineBorder(shape = shape, color = borderColor) else Modifier)
    } else {
        modifier
            .clip(shape)
            .background(containerColor)
            .then(if (borderColor != null) Modifier.hairlineBorder(shape = shape, color = borderColor) else Modifier)
    }

    Column(
        modifier = cardModifier.padding(contentPadding),
        content = content
    )
}

@Composable
fun UnhingeBadge(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    showPulseDot: Boolean = false,
    pulseColor: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null
) {
    val badgeShape = ShapeTokens.Badge
    val badgeModifier = if (onClick != null) {
        modifier
            .bouncyClickable(onClick = onClick)
            .clip(badgeShape)
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    } else {
        modifier
            .clip(badgeShape)
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    }

    Row(
        modifier = badgeModifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (showPulseDot) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(pulseColor)
            )
        }
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = contentColor
        )
    }
}

@Composable
fun EmptyStateView(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (actionLabel != null && onAction != null) {
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun SkeletonBox(
    modifier: Modifier = Modifier,
    shape: Shape = ShapeTokens.Badge
) {
    Box(
        modifier = modifier
            .clip(shape)
            .shimmerEffect()
    )
}

@Composable
fun UnhingeSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector = Icons.Default.Search
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = {
                    onQueryChange("")
                    focusManager.clearFocus()
                }) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.action_clear_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        shape = ShapeTokens.Search,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    )
}
