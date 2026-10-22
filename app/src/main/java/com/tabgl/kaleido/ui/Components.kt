package com.tabgl.kaleido.ui

import android.net.Uri
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.tabgl.kaleido.data.Album
import com.tabgl.kaleido.data.Photo

private const val THUMBNAIL_PX = 320

/** "1 item" or "N items". */
fun itemCountLabel(count: Int): String = if (count == 1) "1 item" else "$count items"

/** Coil request for a thumbnail, with a crossfade so images fade in as they load. */
@Composable
private fun rememberThumbnailRequest(uri: Uri): ImageRequest {
    val context = LocalContext.current
    return remember(uri) {
        ImageRequest.Builder(context)
            .data(uri)
            // Decode thumbnails at grid size, not full resolution: smoother scrolling, less memory.
            .size(THUMBNAIL_PX, THUMBNAIL_PX)
            .crossfade(true)
            .build()
    }
}

/**
 * A square thumbnail. Tiles sit edge to edge, so the grid reads as one connected mosaic.
 * It squishes when pressed and fires [onLongClick] with a haptic tick.
 * When [selected], the photo insets, gets a tint and a check badge. A starred photo shows a star.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoTile(
    photo: Photo,
    selected: Boolean,
    starred: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "tileScale",
    )
    val inset by animateDpAsState(
        targetValue = if (selected) 8.dp else 0.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "tileInset",
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
    ) {
        AsyncImage(
            model = rememberThumbnailRequest(photo.uri),
            contentDescription = photo.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .padding(inset)
                .clip(RoundedCornerShape(if (selected) 14.dp else 0.dp)),
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else if (starred) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = "Starred",
                    tint = Color(0xFFFFC857),
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** An album card: a large rounded cover with the name and item count beneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumTile(album: Album, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
        label = "albumScale",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .combinedClickable(interactionSource = interaction, indication = null, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = rememberThumbnailRequest(album.cover.uri),
            contentDescription = album.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Text(
            text = album.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = itemCountLabel(album.count),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}
