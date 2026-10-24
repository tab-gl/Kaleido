package com.tabgl.kaleido.ui

import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tabgl.kaleido.R
import com.tabgl.kaleido.data.Photo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Full-screen viewer. Swipe between photos, pinch or double-tap to zoom, tap to hide the chrome.
 * The bottom pill holds the actions for the current photo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewer(
    photos: List<Photo>,
    startIndex: Int,
    starred: Set<Long>,
    onDismiss: () -> Unit,
    onToggleStar: (Photo) -> Unit,
    onEdit: (Photo) -> Unit,
    onDelete: (Photo) -> Unit,
    onVault: (Photo) -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(
        initialPage = startIndex.coerceIn(0, (photos.size - 1).coerceAtLeast(0)),
    ) { photos.size }
    // A zoomed photo takes over the horizontal drags, so the pager must stop scrolling.
    var zoomed by remember { mutableStateOf(false) }
    var chrome by remember { mutableStateOf(true) }
    var infoOpen by remember { mutableStateOf(false) }
    val current = photos.getOrNull(pagerState.currentPage)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !zoomed,
                key = { photos[it].id },
                beyondViewportPageCount = 1,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                if (photos[page].isVideo) {
                    VideoPage(
                        photo = photos[page],
                        active = page == pagerState.currentPage,
                    )
                } else {
                    ZoomablePage(
                        photo = photos[page],
                        active = page == pagerState.currentPage,
                        onZoomChange = { zoomed = it },
                        onTap = { chrome = !chrome },
                    )
                }
            }

            AnimatedVisibility(
                visible = chrome,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onDismiss) {
                        HiIcon(R.drawable.hi_close, "Close", Color.White)
                    }
                    Text(
                        text = "${pagerState.currentPage + 1} / ${photos.size}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                }
            }

            if (current != null) {
                AnimatedVisibility(
                    visible = chrome,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    val isStarred = current.id in starred
                    Surface(
                        modifier = Modifier.navigationBarsPadding().padding(bottom = 20.dp),
                        shape = RoundedCornerShape(32.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shadowElevation = 6.dp,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            PillAction(
                                icon = R.drawable.hi_star,
                                label = if (isStarred) "Unstar" else "Star",
                                tint = if (isStarred) Color(0xFFFFC857) else MaterialTheme.colorScheme.onSurface,
                            ) { onToggleStar(current) }
                            PillAction(R.drawable.hi_info, "Details") { infoOpen = true }
                            if (!current.isVideo) PillAction(R.drawable.hi_lock, "Vault") { onVault(current) }
                            PillAction(R.drawable.hi_share, "Share") { sharePhotos(context, listOf(current)) }
                            if (!current.isVideo) PillAction(R.drawable.hi_edit, "Edit") { onEdit(current) }
                            PillAction(
                                icon = R.drawable.hi_delete,
                                label = "Delete",
                                tint = MaterialTheme.colorScheme.error,
                            ) { onDelete(current) }
                        }
                    }
                }
            }

            if (infoOpen && current != null) {
                ModalBottomSheet(onDismissRequest = { infoOpen = false }) {
                    Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
                        Text("Details", style = MaterialTheme.typography.titleLarge)
                        ListItem(
                            headlineContent = { Text("Name") },
                            supportingContent = { Text(current.name) },
                        )
                        ListItem(
                            headlineContent = { Text("Album") },
                            supportingContent = { Text(current.album) },
                        )
                        ListItem(
                            headlineContent = { Text("Added") },
                            supportingContent = { Text(formatAdded(current.dateAdded)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PillAction(
    icon: Int,
    label: String,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick) {
        HiIcon(icon, label, tint)
    }
}

/** One video page. Plays while it is the visible page and pauses when swiped away. Tap shows the controls. */
@Composable
private fun VideoPage(photo: Photo, active: Boolean) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            VideoView(context).apply {
                setMediaController(MediaController(context).also { it.setAnchorView(this) })
                setVideoURI(photo.uri)
                setOnPreparedListener { player -> player.isLooping = false }
            }
        },
        update = { view ->
            if (active) {
                if (!view.isPlaying) view.start()
            } else if (view.isPlaying) {
                view.pause()
            }
        },
        onRelease = { it.stopPlayback() },
    )
}

/** One page: the photo with its own pinch and pan state. Resets when the page is left. */
@Composable
private fun ZoomablePage(
    photo: Photo,
    active: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember(photo.id) { mutableFloatStateOf(1f) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    LaunchedEffect(active) {
        if (!active) {
            scale = 1f
            offset = Offset.Zero
        }
    }
    AsyncImage(
        model = photo.uri,
        contentDescription = photo.name,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            .pointerInput(photo.id) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        val target = if (scale > 1f) 1f else 2.5f
                        scale = target
                        if (target == 1f) offset = Offset.Zero
                        onZoomChange(target > 1f)
                    },
                )
            }
            .pointerInput(photo.id) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 5f)
                    scale = next
                    offset = if (next > 1f) offset + pan else Offset.Zero
                    onZoomChange(next > 1f)
                }
            },
    )
}

private fun formatAdded(dateAdded: Long): String {
    // MediaStore reports seconds. Anything this large is already milliseconds.
    val millis = if (dateAdded < 100_000_000_000L) dateAdded * 1000 else dateAdded
    return DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(millis))
}
