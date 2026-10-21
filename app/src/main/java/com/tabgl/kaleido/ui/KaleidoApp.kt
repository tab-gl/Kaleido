package com.tabgl.kaleido.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tabgl.kaleido.data.MediaRepository
import com.tabgl.kaleido.data.Photo
import com.tabgl.kaleido.data.hasMediaAccess
import com.tabgl.kaleido.data.mediaPermissions
import com.tabgl.kaleido.data.toAlbums
import kotlin.math.abs

enum class Tab { Photos, Albums }

/** The photos the viewer can swipe through, and the one that was tapped. */
private data class ViewerRequest(val photos: List<Photo>, val index: Int)

@Composable
fun KaleidoApp() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasMediaAccess()) }
    var loaded by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var tab by rememberSaveable { mutableStateOf(Tab.Photos) }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var navVisible by remember { mutableStateOf(true) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Check the system state rather than the request result: partial ("selected photos")
        // access is reported differently on Android 14+.
        granted = context.hasMediaAccess()
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(mediaPermissions())
    }

    LaunchedEffect(granted) {
        if (granted) {
            photos = MediaRepository.loadPhotos(context)
            loaded = true
        }
    }

    val albums = remember(photos) { photos.toAlbums() }
    val albumPhotos = remember(photos, openAlbum) {
        openAlbum?.let { name -> photos.filter { it.album == name } }.orEmpty()
    }

    // Hide the floating navbar while scrolling down through content; show it on scroll up.
    val navScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) > 2f) navVisible = available.y > 0f
                return Offset.Zero
            }
        }
    }

    BackHandler(enabled = openAlbum != null && viewer == null) { openAlbum = null }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(navScroll)) {
        when {
            !granted -> PermissionPrompt(
                onRequest = { permissionLauncher.launch(mediaPermissions()) },
            )

            !loaded -> LoadingState()

            else -> AnimatedContent(
                targetState = tab to openAlbum,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(160)) },
                label = "screen",
            ) { (currentTab, album) ->
                when {
                    currentTab == Tab.Photos -> GalleryScreen(
                        title = "Photos",
                        photos = photos,
                        onOpen = { index -> viewer = ViewerRequest(photos, index) },
                    )

                    album == null -> AlbumsScreen(
                        albums = albums,
                        onOpen = { openAlbum = it.name },
                    )

                    else -> GalleryScreen(
                        title = album,
                        photos = albumPhotos,
                        onOpen = { index -> viewer = ViewerRequest(albumPhotos, index) },
                        onBack = { openAlbum = null },
                    )
                }
            }
        }

        if (granted && loaded) {
            AnimatedVisibility(
                visible = navVisible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
                enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
            ) {
                FloatingNavBar(
                    selected = tab,
                    onSelect = { selected ->
                        tab = selected
                        openAlbum = null
                        navVisible = true
                    },
                )
            }
        }

        viewer?.let { request ->
            PhotoViewer(
                photos = request.photos,
                startIndex = request.index,
                onDismiss = { viewer = null },
            )
        }
    }
}
