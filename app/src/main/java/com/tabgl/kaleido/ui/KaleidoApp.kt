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
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
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
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class Tab { Photos, Albums }

/** The photos the viewer can swipe through, and the one that was tapped. */
private data class ViewerRequest(val photos: List<Photo>, val index: Int)

/** Column counts the grid cycles through when the user taps the density button. */
private val gridSizes = listOf(3, 4, 5)

/** How long a burst of MediaStore changes is coalesced into one reload. */
private const val RELOAD_DEBOUNCE_MS = 300L

/** Opens this app's page in system settings, where the user can grant photo access. */
private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Depth in the navigation: Photos 0, Albums 1, an album's photos 2. Used to pick the slide direction. */
private fun screenRank(state: Pair<Tab, String?>): Int =
    if (state.second != null) 2 else state.first.ordinal

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
    var newestFirst by rememberSaveable { mutableStateOf(true) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }

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
            // A bulk import fires many change events. Wait briefly so they collapse into one reload.
            // Each new event restarts this effect, so only the last one in a burst actually loads.
            if (refreshKey > 0) delay(RELOAD_DEBOUNCE_MS)
            // If the query fails, show the empty state rather than spinning forever.
            photos = try {
                MediaRepository.loadPhotos(context)
            } catch (e: Exception) {
                emptyList()
            }
            loaded = true
        }
    }

    // The repository returns newest first. Reversing in memory is cheap and avoids a second query.
    val ordered = remember(photos, newestFirst) { if (newestFirst) photos else photos.asReversed() }
    val visiblePhotos = remember(ordered, query) {
        if (query.isBlank()) {
            ordered
        } else {
            ordered.filter { photo ->
                photo.name.contains(query, ignoreCase = true) ||
                    photo.album.contains(query, ignoreCase = true)
            }
        }
    }
    val albums = remember(photos) { photos.toAlbums() }
    val albumPhotos = remember(ordered, openAlbum) {
        openAlbum?.let { name -> ordered.filter { it.album == name } }.orEmpty()
    }
    val selectedPhotos = remember(ordered, selectedIds) { ordered.filter { it.id in selectedIds } }

    // Hide the floating navbar while scrolling down through content; show it on scroll up.
    val navScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) > 2f) navVisible = available.y > 0f
                return Offset.Zero
            }
        }
    }

    val selection = SelectionControls(
        selected = selectedIds,
        onToggle = { id ->
            selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
        },
        onShare = { sharePhotos(context, selectedPhotos) },
        onClear = { selectedIds = emptySet() },
    )

    // Back closes things in order: the viewer is handled by its own dialog, then selection,
    // then search, then an open album.
    BackHandler(enabled = openAlbum != null && viewer == null) { openAlbum = null }
    BackHandler(enabled = searchOpen && viewer == null) {
        searchOpen = false
        query = ""
    }
    BackHandler(enabled = selectedIds.isNotEmpty() && viewer == null) { selectedIds = emptySet() }

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
                // Forward (Photos → Albums → an album) slides in from the right; back slides from the left.
                transitionSpec = {
                    val towards = if (screenRank(targetState) > screenRank(initialState)) {
                        SlideDirection.Left
                    } else {
                        SlideDirection.Right
                    }
                    (slideIntoContainer(towards, tween(320)) + fadeIn(tween(220))) togetherWith
                        (slideOutOfContainer(towards, tween(320)) + fadeOut(tween(160)))
                },
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
                        selection = selection,
                        sort = SortControls(
                            newestFirst = newestFirst,
                            onToggle = { newestFirst = !newestFirst },
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
                        selection = selection,
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
                        selectedIds = emptySet()
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
