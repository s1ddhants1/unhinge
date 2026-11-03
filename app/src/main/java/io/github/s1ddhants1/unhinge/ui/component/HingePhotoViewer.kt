package io.github.s1ddhants1.unhinge.ui.component

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.SubcomposeAsyncImage
import io.github.s1ddhants1.unhinge.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

const val HINGE_PHOTO_ASPECT_RATIO: Float = 4f / 5f

fun calculateHingeCardDimensions(availableWidth: Float, availableHeight: Float): Pair<Float, Float> {
    val targetRatio = HINGE_PHOTO_ASPECT_RATIO
    return if (availableWidth / targetRatio <= availableHeight) {
        availableWidth to (availableWidth / targetRatio)
    } else {
        (availableHeight * targetRatio) to availableHeight
    }
}

fun calculateMaxPanOffset(size: Float, zoomScale: Float): Float {
    return if (zoomScale > 1f) (size * (zoomScale - 1f)) / 2f else 0f
}

@Composable
fun HingePhotoViewer(
    photos: List<String>,
    initialIndex: Int = 0,
    title: String? = null,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onCopyUrl: ((String) -> Unit)? = null,
    initialCropToStandardRatio: Boolean = true
) {
    val validPhotos = remember(photos) { photos.filter { it.isNotBlank() } }
    if (validPhotos.isEmpty()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }

    val safeInitialIndex = initialIndex.coerceIn(0, validPhotos.size - 1)
    val pagerState = rememberPagerState(initialPage = safeInitialIndex) { validPhotos.size }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var cropToStandardRatio by remember { mutableStateOf(initialCropToStandardRatio) }
    var isCopied by remember { mutableStateOf(false) }

    LaunchedEffect(pagerState.currentPage) {
        zoomScale = 1f
        panOffset = Offset.Zero
        isCopied = false
    }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(1800)
            isCopied = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                ),
            contentAlignment = Alignment.Center
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                contentAlignment = Alignment.Center
            ) {
                val maxCardHeight = (maxHeight - 136.dp).coerceAtLeast(180.dp)
                val maxCardWidth = maxWidth
                val (cardWidth, cardHeight) = remember(maxCardWidth, maxCardHeight) {
                    val (w, h) = calculateHingeCardDimensions(maxCardWidth.value, maxCardHeight.value)
                    w.dp to h.dp
                }

                Column(
                    modifier = Modifier
                        .width(cardWidth)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {}
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (!title.isNullOrBlank()) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val counterText = if (validPhotos.size > 1) {
                                stringResource(
                                    R.string.image_viewer_photo_count_format,
                                    pagerState.currentPage + 1,
                                    validPhotos.size
                                )
                            } else null
                            val headerSubtitle = listOfNotNull(
                                subtitle?.takeIf { it.isNotBlank() },
                                counterText
                            ).joinToString(" • ")

                            if (headerSubtitle.isNotBlank()) {
                                Text(
                                    text = headerSubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.72f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_close),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    HingeSheetDotsIndicator(
                        pageCount = validPhotos.size,
                        currentPage = pagerState.currentPage,
                        onDotClick = { targetIndex ->
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(targetIndex)
                            }
                        },
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(cardWidth, cardHeight)
                            .clip(RoundedCornerShape(22.dp))
                            .background(Color(0xFF161618))
                            .hairlineBorder(RoundedCornerShape(22.dp), Color.White.copy(alpha = 0.14f), 1.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(pagerState.currentPage) {
                                    detectTapGestures(
                                        onDoubleTap = { tapOffset ->
                                            if (zoomScale > 1.05f) {
                                                zoomScale = 1f
                                                panOffset = Offset.Zero
                                            } else {
                                                zoomScale = 2.5f
                                                val maxOffsetX = (size.width * 1.5f) / 2f
                                                val maxOffsetY = (size.height * 1.5f) / 2f
                                                val targetX = (size.width / 2f - tapOffset.x) * 1.5f
                                                val targetY = (size.height / 2f - tapOffset.y) * 1.5f
                                                panOffset = Offset(
                                                    x = targetX.coerceIn(-maxOffsetX, maxOffsetX),
                                                    y = targetY.coerceIn(-maxOffsetY, maxOffsetY)
                                                )
                                            }
                                        },
                                        onTap = { tapOffset ->
                                            if (zoomScale <= 1.05f && validPhotos.size > 1) {
                                                val fraction = tapOffset.x / size.width
                                                if (fraction < 0.25f && pagerState.currentPage > 0) {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                                    }
                                                } else if (fraction > 0.75f && pagerState.currentPage < validPhotos.size - 1) {
                                                    coroutineScope.launch {
                                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                                .pointerInput(pagerState.currentPage) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (zoomScale * zoom).coerceIn(1f, 5f)
                                        zoomScale = newScale
                                        if (newScale > 1.05f) {
                                            val maxOffsetX = (size.width * (newScale - 1f)) / 2f
                                            val maxOffsetY = (size.height * (newScale - 1f)) / 2f
                                            panOffset = Offset(
                                                x = (panOffset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                                                y = (panOffset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                        } else {
                                            panOffset = Offset.Zero
                                        }
                                    }
                                }
                        ) {
                            HorizontalPager(
                                state = pagerState,
                                userScrollEnabled = zoomScale <= 1.05f,
                                modifier = Modifier.fillMaxSize()
                            ) { page ->
                                val isCurrentPage = pagerState.currentPage == page
                                val currentUrl = validPhotos[page]

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            if (isCurrentPage) {
                                                scaleX = zoomScale
                                                scaleY = zoomScale
                                                translationX = panOffset.x
                                                translationY = panOffset.y
                                            } else {
                                                scaleX = 1f
                                                scaleY = 1f
                                                translationX = 0f
                                                translationY = 0f
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    SubcomposeAsyncImage(
                                        model = currentUrl,
                                        contentDescription = title?.let {
                                            stringResource(R.string.photo_of_format, page + 1, it)
                                        } ?: stringResource(R.string.cd_photo_index_format, page + 1),
                                        contentScale = if (cropToStandardRatio) ContentScale.Crop else ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                        loading = {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                CircularProgressIndicator(
                                                    strokeWidth = 2.5.dp,
                                                    modifier = Modifier.size(36.dp),
                                                    color = Color.White.copy(alpha = 0.85f)
                                                )
                                            }
                                        },
                                        error = {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.BrokenImage,
                                                    contentDescription = null,
                                                    tint = Color.White.copy(alpha = 0.5f),
                                                    modifier = Modifier.size(44.dp)
                                                )
                                                Spacer(Modifier.height(8.dp))
                                                Text(
                                                    text = stringResource(R.string.image_viewer_failed_load),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = Color.White.copy(alpha = 0.7f)
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (validPhotos.size > 1 && zoomScale <= 1.05f) {
                            if (pagerState.currentPage > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 10.dp)
                                        .size(34.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = stringResource(R.string.cd_previous_photo),
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                            if (pagerState.currentPage < validPhotos.size - 1) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.45f),
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .padding(end = 10.dp)
                                        .size(34.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                            }
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = stringResource(R.string.cd_next_photo),
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (zoomScale > 1.05f) {
                            Surface(
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.75f),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(12.dp)
                                    .bouncyClickable {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ZoomOutMap,
                                        contentDescription = stringResource(R.string.cd_reset_zoom),
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.1f", zoomScale)}x",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.bouncyClickable {
                                cropToStandardRatio = !cropToStandardRatio
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (cropToStandardRatio) Icons.Default.AspectRatio else Icons.Default.CropFree,
                                    contentDescription = stringResource(R.string.cd_aspect_ratio_mode),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (cropToStandardRatio) {
                                        stringResource(R.string.image_viewer_mode_standard)
                                    } else {
                                        stringResource(R.string.image_viewer_mode_fit)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        val activeUrl = validPhotos[pagerState.currentPage]
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White.copy(alpha = 0.12f),
                            modifier = Modifier.bouncyClickable {
                                onCopyUrl?.invoke(activeUrl) ?: run {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.copy_photo_url), activeUrl))
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.toast_copied_format, context.getString(R.string.copy_photo_url)),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                isCopied = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.action_copy_url),
                                    tint = if (isCopied) MaterialTheme.colorScheme.primary else Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = if (isCopied) {
                                        stringResource(R.string.action_copied)
                                    } else {
                                        stringResource(R.string.action_copy_url)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isCopied) MaterialTheme.colorScheme.primary else Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HingePhotoViewer(
    photoUrl: String,
    title: String? = null,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    onCopyUrl: ((String) -> Unit)? = null,
    initialCropToStandardRatio: Boolean = true
) {
    if (photoUrl.isBlank()) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    HingePhotoViewer(
        photos = listOf(photoUrl),
        initialIndex = 0,
        title = title,
        subtitle = subtitle,
        onDismiss = onDismiss,
        onCopyUrl = onCopyUrl,
        initialCropToStandardRatio = initialCropToStandardRatio
    )
}

@Composable
fun HingeSheetDotsIndicator(
    pageCount: Int,
    currentPage: Int,
    onDotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (pageCount <= 1) return

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            val isActive = index == currentPage
            val dotDiameter by animateDpAsState(
                targetValue = if (isActive) 7.5.dp else 6.dp,
                label = "dotDiameter_$index"
            )
            val dotColor by animateColorAsState(
                targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                label = "dotColor_$index"
            )
            Box(
                modifier = Modifier
                    .size(width = 18.dp, height = 16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        if (index != currentPage) {
                            onDotClick(index)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(dotDiameter)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
        }
    }
}

@Composable
fun HingePhotoCarousel(
    photos: List<String>,
    modifier: Modifier = Modifier,
    candidateName: String = "",
    captions: List<String>? = null,
    onCopyUrl: ((String) -> Unit)? = null
) {
    val validPhotos = remember(photos) { photos.filter { it.isNotBlank() } }
    if (validPhotos.isEmpty()) return

    val pagerState = rememberPagerState(initialPage = 0) { validPhotos.size }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(HINGE_PHOTO_ASPECT_RATIO)
            .clip(ShapeTokens.CardNested)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .hairlineBorder(ShapeTokens.CardNested)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val photoUrl = validPhotos[page]
            SubcomposeAsyncImage(
                model = photoUrl,
                contentDescription = if (candidateName.isNotBlank()) {
                    stringResource(R.string.photo_of_format, page + 1, candidateName)
                } else null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    SkeletonBox(
                        modifier = Modifier.fillMaxSize(),
                        shape = ShapeTokens.CardNested
                    )
                },
                error = {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.BrokenImage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            )
        }

        if (validPhotos.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )

            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(validPhotos.size) { index ->
                    val isActive = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(18.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isActive) Color.White
                                    else Color.White.copy(alpha = 0.4f)
                                )
                        )
                    }
                }
            }
        }

        val currentCaption = captions?.getOrNull(pagerState.currentPage)?.takeIf { it.isNotBlank() }
        val showBottomControls = validPhotos.size > 1 || onCopyUrl != null || currentCaption != null

        if (showBottomControls) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = if (currentCaption != null) 0.85f else 0.45f)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (currentCaption != null) {
                        Text(
                            text = currentCaption,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onCopyUrl != null) {
                            val currentUrl = validPhotos.getOrNull(pagerState.currentPage)
                            if (!currentUrl.isNullOrBlank()) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    onClick = { onCopyUrl(currentUrl) },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = stringResource(R.string.copy_photo_url),
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            } else {
                                Spacer(Modifier.width(1.dp))
                            }
                        } else {
                            Spacer(Modifier.width(1.dp))
                        }

                        if (validPhotos.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${pagerState.currentPage + 1}/${validPhotos.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

