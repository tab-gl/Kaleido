package com.tabgl.kaleido.ui

import android.app.Activity
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
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
import com.tabgl.kaleido.data.AppSettings
import com.tabgl.kaleido.data.MediaRepository
import com.tabgl.kaleido.data.Photo
import com.tabgl.kaleido.data.arrangedBy
import com.tabgl.kaleido.data.hasMediaAccess
import com.tabgl.kaleido.data.mediaPermissions
import com.tabgl.kaleido.data.toAlbums
import kotlinx.coroutines.delay
import kotlin.math.abs

enum class Tab { Photos, Albums, Starred, Settings }

/** The photos the viewer can swipe through, and the one that was tapped. */
private data class ViewerRequest(val photos: List<Photo>, val index: Int)

/** How long a burst of MediaStore changes is coalesced into one reload. */
private const val RELOAD_DEBOUNCE_MS = 300L

/** Opens this app's page in system settings, where the user can grant photo access. */
private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

/** Depth in the navigation: Photos 0, Albums 1, an album's photos 2. Used to pick the slide direction. */
private fun screenRank(state: Pair<Tab, String?>): Int =
    if (state.second != null) 2 else state.first.ordinal

/** Grid density cycles 3 → 4 → 5 → 3. */
private fun nextColumns(current: Int): Int = if (current >= 5) 3 else current + 1

@Composable
fun KaleidoApp(settings: AppSettings) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(context.hasMediaAccess()) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableStateOf(0) }
    var photos by remember { mutableStateOf<List<Photo>>(emptyList()) }
    var tab by rememberSaveable { mutableStateOf(Tab.Photos) }
    var openAlbum by rememberSaveable { mutableStateOf<String?>(null) }
    var viewer by remember { mutableStateOf<ViewerRequest?>(null) }
    var editing by remember { mutableStateOf<Photo?>(null) }
    var navVisible by remember { mutableStateOf(true) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDelete by remember { mutableStateOf(emptySet<Long>()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Check the system state rather than the request result: partial ("selected photos")
        // access is reported differently on Android 14+.
        requested = true
        granted = context.hasMediaAccess()
    }

    // Android 11+ asks the user to confirm deletes through the system dialog.
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            settings.saveStarred(settings.starred - pendingDelete)
            selectedIds = selectedIds - pendingDelete
        }
        pendingDelete = emptySet()
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

    val ordered = remember(photos, settings.sortOrder) { photos.arrangedBy(settings.sortOrder) }
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
    val starredPhotos = remember(ordered, settings.starred) { ordered.filter { it.id in settings.starred } }
    val albums = remember(photos) { photos.toAlbums() }
    val albumPhotos = remember(ordered, openAlbum) {
        openAlbum?.let { name -> ordered.filter { it.album == name } }.orEmpty()
    }
    val selectedPhotos = remember(ordered, selectedIds) { ordered.filter { it.id in selectedIds } }
    val allSelectedStarred = selectedPhotos.isNotEmpty() && selectedPhotos.all { it.id in settings.starred }

    // Hide the floating navbar while scrolling down through content; show it on scroll up.
    val navScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (abs(available.y) > 2f) navVisible = available.y > 0f
                return Offset.Zero
            }
        }
    }

    fun toggleStar(photo: Photo) {
        val current = settings.starred
        settings.saveStarred(if (photo.id in current) current - photo.id else current + photo.id)
    }

    fun starSelected() {
        val ids = selectedPhotos.mapTo(HashSet()) { it.id }
        val current = settings.starred
        settings.saveStarred(if (allSelectedStarred) current - ids else current + ids)
    }

    fun requestDelete(targets: List<Photo>) {
        if (targets.isEmpty()) return
        val resolver = context.contentResolver
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            pendingDelete = targets.mapTo(HashSet()) { it.id }
            val request = MediaStore.createDeleteRequest(resolver, targets.map { it.uri })
            deleteLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
        } else {
            // Before Android 11 an app can delete only the photos it can write. Any others are skipped.
            val gone = targets
                .filter { photo -> runCatching { resolver.delete(photo.uri, null, null) > 0 }.getOrDefault(false) }
                .mapTo(HashSet()) { it.id }
            settings.saveStarred(settings.starred - gone)
            selectedIds = selectedIds - gone
            Toast.makeText(context, "Deleted ${gone.size} of ${targets.size}", Toast.LENGTH_SHORT).show()
        }
    }

    val selection = SelectionControls(
        selected = selectedIds,
        allStarred = allSelectedStarred,
        onToggle = { id -> selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id },
        onStar = { starSelected() },
        onShare = { sharePhotos(context, selectedPhotos) },
        onDelete = { requestDelete(selectedPhotos) },
        onClear = { selectedIds = emptySet() },
    )

    // Back closes things in order: the viewer and editor are their own dialogs, then selection,
    // then search, then an open album.
    BackHandler(enabled = openAlbum != null && viewer == null && editing == null) { openAlbum = null }
    BackHandler(enabled = searchOpen && viewer == null && editing == null) {
        searchOpen = false
        query = ""
    }
    BackHandler(enabled = selectedIds.isNotEmpty() && viewer == null && editing == null) {
        selectedIds = emptySet()
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
                when (currentTab) {
                    Tab.Photos -> GalleryScreen(
                        title = "Photos",
                        photos = visiblePhotos,
                        columns = settings.columns,
                        onCycleColumns = { settings.saveColumns(nextColumns(settings.columns)) },
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
                        sort = SortControls(order = settings.sortOrder, onSelect = { settings.saveSortOrder(it) }),
                        starred = settings.starred,
                    )

                    Tab.Starred -> GalleryScreen(
                        title = "Starred",
                        photos = starredPhotos,
                        columns = settings.columns,
                        onCycleColumns = { settings.saveColumns(nextColumns(settings.columns)) },
                        onOpen = { index -> viewer = ViewerRequest(starredPhotos, index) },
                        selection = selection,
                        starred = settings.starred,
                    )

                    Tab.Settings -> SettingsScreen(settings = settings)

                    Tab.Albums -> if (album == null) {
                        AlbumsScreen(
                            albums = albums,
                            onOpen = { openAlbum = it.name },
                        )
                    } else {
                        GalleryScreen(
                            title = album,
                            photos = albumPhotos,
                            columns = settings.columns,
                            onCycleColumns = { settings.saveColumns(nextColumns(settings.columns)) },
                            onOpen = { index -> viewer = ViewerRequest(albumPhotos, index) },
                            onBack = { openAlbum = null },
                            selection = selection,
                            starred = settings.starred,
                        )
                    }
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
                starred = settings.starred,
                onDismiss = { viewer = null },
                onToggleStar = { toggleStar(it) },
                onEdit = { editing = it },
                onDelete = {
                    viewer = null
                    requestDelete(listOf(it))
                },
            )
        }

        // Stacked after the viewer so it draws on top of it.
        editing?.let { photo ->
            EditorDialog(
                photo = photo,
                onDismiss = { editing = null },
                onSaved = { editing = null },
            )
        }
    }
}
