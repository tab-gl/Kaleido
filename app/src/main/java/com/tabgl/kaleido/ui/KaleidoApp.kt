package com.tabgl.kaleido.ui

import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
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
import androidx.compose.runtime.DisposableEffect
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

/** Column counts the grid cycles through when the user taps the density button. */
private val gridSizes = listOf(3, 4, 5)

/** Opens this app's page in system settings, where the user can grant photo access. */
private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

@Composable
fun KaleidoApp() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasMediaAccess()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var tab by rememberSaveable { mutableStateOf(Tab.Photos) }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var navVisible by remember { mutableStateOf(true) }
    var columnIndex by rememberSaveable { mutableStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Check the system state rather than the request result: partial ("selected photos")
        // access is reported differently on Android 14+.
        requested = true
        granted = context.hasMediaAccess()
    }

    LaunchedEffect(Unit) {
        if (!granted) permissionLauncher.launch(mediaPermissions())
    }

    // Reload when the MediaStore changes, so new and deleted photos appear without a restart.
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose {}
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                refreshKey++
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    LaunchedEffect(granted, refreshKey) {
        if (granted) {
            // If the query fails, show the empty state rather than spinning forever.
            photos = try {
                MediaRepository.loadPhotos(context)
            } catch (e: Exception) {
                emptyList()
            }
            loaded = true
        }
    }

    val visiblePhotos = remember(photos, query) {
        if (query.isBlank()) {
            photos
        } else {
            photos.filter { photo ->
                photo.name.contains(query, ignoreCase = true) ||
                    photo.album.contains(query, ignoreCase = true)
            }
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
    BackHandler(enabled = searchOpen && viewer == null) {
        searchOpen = false
        query = ""
    }

    Box(modifier = Modifier.fillMaxSize().nestedScroll(navScroll)) {
        when {
            !granted -> PermissionPrompt(
                showSettings = requested,
                onRequest = { permissionLauncher.launch(mediaPermissions()) },
                onOpenSettings = { context.startActivity(appSettingsIntent(context.packageName)) },
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
                        photos = visiblePhotos,
                        columns = gridSizes[columnIndex],
                        onCycleColumns = { columnIndex = (columnIndex + 1) % gridSizes.size },
                        onOpen = { index -> viewer = ViewerRequest(visiblePhotos, index) },
                        search = SearchControls(
                            open = searchOpen,
                            query = query,
                            onToggle = {
                                searchOpen = !searchOpen
                                if (!searchOpen) query = ""
                            },
                            onQueryChange = { query = it },
                        ),
                    )

                    album == null -> AlbumsScreen(
                        albums = albums,
                        onOpen = { openAlbum = it.name },
                    )

                    else -> GalleryScreen(
                        title = album,
                        photos = albumPhotos,
                        columns = gridSizes[columnIndex],
                        onCycleColumns = { columnIndex = (columnIndex + 1) % gridSizes.size },
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
                        searchOpen = false
                        query = ""
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
