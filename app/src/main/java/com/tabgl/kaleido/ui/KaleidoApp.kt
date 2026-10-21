package com.tabgl.kaleido.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tabgl.kaleido.data.Album
import com.tabgl.kaleido.data.MediaRepository
import com.tabgl.kaleido.data.Photo
import com.tabgl.kaleido.data.hasMediaPermission
import com.tabgl.kaleido.data.mediaPermissions
import com.tabgl.kaleido.data.toAlbums

enum class Tab { Photos, Albums }

@Composable
fun KaleidoApp() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasMediaPermission()) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableStateOf(Tab.Photos) }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var viewing by remember { mutableStateOf<Photo?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        granted = result.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(mediaPermissions())
    }

    LaunchedEffect(granted) {
        if (granted) {
            loading = true
            photos = MediaRepository.loadPhotos(context)
            loading = false
        }
    }

    val albums = remember(photos) { photos.toAlbums() }
    val albumPhotos = remember(photos, openAlbum) { photos.filter { it.album == openAlbum } }

    BackHandler(enabled = viewing != null) { viewing = null }
    BackHandler(enabled = viewing == null && openAlbum != null) { openAlbum = null }

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            !granted -> PermissionPrompt(
                onRequest = { permissionLauncher.launch(mediaPermissions()) }
            )

            loading && photos.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }

            else -> AnimatedContent(
                targetState = tab to openAlbum,
                transitionSpec = {
                    (fadeIn(tween(260)) togetherWith fadeOut(tween(160)))
                },
                label = "screen",
            ) { (currentTab, album) ->
                when {
                    currentTab == Tab.Photos -> GalleryScreen(
                        title = "Photos",
                        photos = photos,
                        onOpen = { viewing = it },
                    )

                    album == null -> AlbumsScreen(
                        albums = albums,
                        onOpen = { openAlbum = it.name },
                    )

                    else -> GalleryScreen(
                        title = album,
                        photos = albumPhotos,
                        onOpen = { viewing = it },
                        onBack = { openAlbum = null },
                    )
                }
            }
        }

        if (granted) {
            FloatingNavBar(
                selected = tab,
                onSelect = {
                    tab = it
                    openAlbum = null
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
            )
        }

        viewing?.let { photo ->
            PhotoViewer(photo = photo, onDismiss = { viewing = null })
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Kaleido needs access to your photos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer12()
        Text(
            text = "Grant photo access so Kaleido can show your gallery and albums.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer12()
        Button(onClick = onRequest) { Text("Allow access") }
    }
}

@Composable
private fun Spacer12() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
}

@Composable
private fun GalleryScreen(
    title: String,
    photos: List<Photo>,
    onOpen: (Photo) -> Unit,
    onBack: (() -> Unit)? = null,
) {
    BackHandler(enabled = onBack != null) { onBack?.invoke() }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = title, subtitle = "${photos.size} items", onBack = onBack)
        if (photos.isEmpty()) {
            EmptyState("No photos here yet")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(photos, key = { it.id }) { photo ->
                    PhotoTile(photo = photo, onClick = { onOpen(photo) })
                }
            }
        }
    }
}

@Composable
private fun AlbumsScreen(albums: List<Album>, onOpen: (Album) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(title = "Albums", subtitle = "${albums.size} albums")
        if (albums.isEmpty()) {
            EmptyState("No albums found")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(albums, key = { it.name }) { album ->
                    AlbumTile(album = album, onClick = { onOpen(album) })
                }
            }
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String, onBack: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer12Horizontal()
        }
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Spacer12Horizontal() {
    androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize().wrapContentSize()) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 16.sp,
        )
    }
}
