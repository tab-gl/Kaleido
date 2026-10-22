package com.tabgl.kaleido.ui

import android.content.Context
import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabgl.kaleido.data.Photo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f
private const val ZOOM_ANIMATION_MS = 280

private val dateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())

private fun formatDate(epochSeconds: Long): String =
    Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).format(dateFormatter)

/** Hands the photo to the system share sheet. The URI grant lets the receiving app read it. */
private fun sharePhoto(context: Context, photo: Photo) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/*"
        putExtra(Intent.EXTRA_STREAM, photo.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * Full-screen, swipeable photo viewer.
 * - Opens and closes with a short scale-and-fade.
 * - Tap to show or hide the controls. Double-tap eases in to 2.5x, or back out.
 * - Pinch to zoom, then drag to pan. Swiping between photos is locked while zoomed.
 * - Shows the album and date for the current photo. Share sends it to other apps.
 * Back, the close button and a tap outside the photo all close it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewer(
    photos: List<Photo>,
    startIndex: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = startIndex) { photos.size }
    var controlsVisible by remember { mutableStateOf(true) }
    val zoom = remember { Animatable(1f) }
    val panX = remember { Animatable(0f) }
    val panY = remember { Animatable(0f) }

    // Starts false and is switched to true on first composition, so the viewer animates in.
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }

    fun requestClose() {
        visibleState.targetState = false
    }

    // Once the exit animation has finished, remove the viewer.
    LaunchedEffect(visibleState.currentState, visibleState.targetState) {
        if (!visibleState.targetState && !visibleState.currentState) onDismiss()
    }

    // Each photo starts at fit-to-screen when the user swipes to it.
    LaunchedEffect(pagerState.currentPage) {
        zoom.snapTo(1f)
        panX.snapTo(0f)
        panY.snapTo(0f)
    }

    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newZoom = (zoom.value * zoomChange).coerceIn(1f, MAX_ZOOM)
        zoom.snapTo(newZoom)
        if (newZoom > 1f) {
            panX.snapTo(panX.value + panChange.x)
            panY.snapTo(panY.value + panChange.y)
        } else {
            panX.snapTo(0f)
            panY.snapTo(0f)
        }
    }

    val current = photos[pagerState.currentPage]

    Dialog(
        onDismissRequest = { requestClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(260)),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180)),
        ) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    // While zoomed in, dragging pans the photo instead of changing page.
                    userScrollEnabled = zoom.value <= 1f,
                ) { page ->
                    val isCurrent = page == pagerState.currentPage
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(photos[page].uri).build(),
                        contentDescription = photos[page].name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val scale = if (isCurrent) zoom.value else 1f
                                scaleX = scale
                                scaleY = scale
                                translationX = if (isCurrent) panX.value else 0f
                                translationY = if (isCurrent) panY.value else 0f
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { controlsVisible = !controlsVisible },
                                    onDoubleTap = {
                                        val target = if (zoom.value > 1f) 1f else DOUBLE_TAP_ZOOM
                                        scope.launch { zoom.animateTo(target, tween(ZOOM_ANIMATION_MS)) }
                                        scope.launch { panX.animateTo(0f, tween(ZOOM_ANIMATION_MS)) }
                                        scope.launch { panY.animateTo(0f, tween(ZOOM_ANIMATION_MS)) }
                                    },
                                )
                            }
                            .transformable(state = transformState, canPan = { zoom.value > 1f }),
                    )
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.TopCenter),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = { requestClose() }) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close",
                                tint = Color.White,
                            )
                        }
                        Text(
                            text = "${pagerState.currentPage + 1} / ${photos.size}",
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White,
                        )
                        IconButton(onClick = { sharePhoto(context, current) }) {
                            Icon(
                                imageVector = Icons.Rounded.Share,
                                contentDescription = "Share",
                                tint = Color.White,
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = controlsVisible,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            text = current.name,
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "${current.album} · ${formatDate(current.dateAdded)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
