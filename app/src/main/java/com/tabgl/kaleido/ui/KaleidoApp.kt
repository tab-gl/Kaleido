package com.tabgl.kaleido.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import java.time.LocalTime
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tabgl.kaleido.data.AppSettings
import com.tabgl.kaleido.data.MediaRepository
import com.tabgl.kaleido.data.Photo
import com.tabgl.kaleido.data.SortOrder
import com.tabgl.kaleido.data.WebDavSync
import com.tabgl.kaleido.data.arrangedBy
import com.tabgl.kaleido.data.hasMediaAccess
import com.tabgl.kaleido.data.mediaPermissions
import com.tabgl.kaleido.data.toAlbums
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Top-level screen: permission gate, the three tabs, galleries, viewer, editor, delete and sync.
 * Photos come from MediaStore and reload when the library changes (debounced).
 */
@Composable
fun KaleidoApp(settings: AppSettings) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current

    var granted by remember { mutableStateOf(context.hasMediaAccess()) }
    var permanentlyDenied by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf<List<Photo>?>(null) }
    var reloadTick by remember { mutableIntStateOf(0) }

    var tab by remember { mutableStateOf(Tab.PHOTOS) }
    var openAlbum by remember { mutableStateOf<String?>(null) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var starredOnly by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var viewing by remember { mutableStateOf<Pair<List<Photo>, Int>?>(null) }
    var editing by remember { mutableStateOf<Photo?>(null) }
    var pendingDelete by remember { mutableStateOf<List<Photo>?>(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }

    val all = photos.orEmpty().filter { it.album !in settings.hiddenAlbums }
    val albumNames = remember(photos) { photos.orEmpty().map { it.album }.distinct().sorted() }
    // Grid positions survive opening the viewer and switching tabs. Each album starts fresh.
    val photosGrid = rememberLazyGridState()
    val albumGrid = remember(openAlbum) { LazyGridState() }
    val photoList = remember(all, settings.sortOrder, starredOnly, settings.starred, query) {
        all.visible(starredOnly, settings.starred, query, settings.sortOrder)
    }
    val albums = remember(all) { all.toAlbums() }
    val albumPhotos = remember(all, openAlbum, settings.sortOrder, settings.starred, starredOnly, query) {
        all.filter { it.album == openAlbum }.visible(starredOnly, settings.starred, query, settings.sortOrder)
    }

    // Changing tab or album clears the selection and search, so nothing stale carries over.
    LaunchedEffect(tab, openAlbum) {
        selected = emptySet()
        query = ""
        searchOpen = false
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        granted = context.hasMediaAccess()
        // After a denial with no rationale left, Android will not show the dialog again.
        permanentlyDenied = !granted && activity != null &&
            mediaPermissions().none { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) {
        selected = emptySet()
        reloadTick++
    }

    // Returning from system Settings may have granted access.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = context.hasMediaAccess()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Any change to the photo library triggers a reload.
    DisposableEffect(granted) {
        if (!granted) return@DisposableEffect onDispose { }
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reloadTick++
            }
        }
        context.contentResolver.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }

    LaunchedEffect(granted, reloadTick) {
        if (granted) {
            // Debounce: a burst of changes (a camera burst, a sync) loads once.
            delay(if (photos == null) 0L else 350L)
            photos = MediaRepository.loadPhotos(context)
        }
    }

    fun toggleStarred(ids: Set<Long>) {
        val everyStarred = ids.all { it in settings.starred }
        settings.saveStarred(if (everyStarred) settings.starred - ids else settings.starred + ids)
    }

    fun performDelete(targets: List<Photo>) {
        val uris = targets.map { it.uri }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ asks the user once through the system dialog.
            deleteLauncher.launch(IntentSenderRequest.Builder(deleteRequest(context, uris)).build())
        } else {
            scope.launch {
                withContext(Dispatchers.IO) {
                    uris.forEach { runCatching { context.contentResolver.delete(it, null, null) } }
                }
                selected = emptySet()
                reloadTick++
            }
        }
    }

    fun startSync() {
        scope.launch {
            syncing = true
            syncMessage = ""
            val result = WebDavSync.run(context, settings) { progress -> scope.launch { syncMessage = progress } }
            syncing = false
            syncMessage = result
            if (!result.startsWith("Sync failed")) {
                settings.saveLastSync(timestampNow())
                reloadTick++
            }
        }
    }

    val cycleColumns: () -> Unit = {
        settings.saveColumns(
            when (settings.columns) {
                3 -> 4
                4 -> 5
                else -> 3
            },
        )
    }

    BackHandler(enabled = tab != Tab.PHOTOS && openAlbum == null) { tab = Tab.PHOTOS }
    BackHandler(enabled = openAlbum != null) { openAlbum = null }

    // One gallery for both the Photos tab and an open album. Selection, sort and star work the same in each.
    @Composable
    fun Gallery(
        title: String,
        list: List<Photo>,
        onBack: (() -> Unit)?,
        withSearch: Boolean,
        headline: String? = null,
        grid: LazyGridState,
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(bottom = 88.dp)) {
            GalleryScreen(
                title = title,
                headline = headline,
                photos = list,
                columns = settings.columns,
                onCycleColumns = cycleColumns,
                onOpen = { index -> viewing = list to index },
                onBack = onBack,
                search = if (withSearch) {
                    SearchControls(
                        open = searchOpen,
                        query = query,
                        onToggle = {
                            searchOpen = !searchOpen
                            if (!searchOpen) query = ""
                        },
                        onQueryChange = { query = it },
                    )
                } else {
                    null
                },
                selection = SelectionControls(
                    selected = selected,
                    allStarred = selected.isNotEmpty() && selected.all { it in settings.starred },
                    onToggle = { id -> selected = if (id in selected) selected - id else selected + id },
                    onStar = { toggleStarred(selected) },
                    onShare = { sharePhotos(context, list.filter { it.id in selected }) },
                    onDelete = { pendingDelete = list.filter { it.id in selected } },
                    onClear = { selected = emptySet() },
                ),
                sort = SortControls(order = settings.sortOrder, onSelect = settings::saveSortOrder),
                starred = settings.starred,
                starredOnly = starredOnly,
                onToggleStarredOnly = { starredOnly = !starredOnly },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (!granted && tab != Tab.SETTINGS) {
            PermissionPrompt(
                showSettings = permanentlyDenied,
                onRequest = { permissionLauncher.launch(mediaPermissions()) },
                onOpenSettings = { context.startActivity(appSettingsIntent(context)) },
            )
        } else {
            AnimatedContent(
                targetState = tab,
                label = "tabs",
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
            ) { currentTab ->
            when (currentTab) {
                Tab.PHOTOS -> if (photos == null) {
                    LoadingState()
                } else {
                    Gallery(
                        title = "Photos",
                        list = photoList,
                        onBack = null,
                        withSearch = true,
                        headline = greetingFor(LocalTime.now().hour, settings.displayName),
                        grid = photosGrid,
                    )
                }

                Tab.ALBUMS -> when {
                    openAlbum != null -> Gallery(
                        title = openAlbum.orEmpty(),
                        list = albumPhotos,
                        onBack = { openAlbum = null },
                        grid = albumGrid,
                        withSearch = false,
                    )

                    photos == null -> LoadingState()
                    else -> Box(modifier = Modifier.fillMaxSize().padding(bottom = 88.dp)) {
                        AlbumsScreen(albums = albums, onOpen = { openAlbum = it.name })
                    }
                }

                Tab.SETTINGS -> SettingsScreen(
                    settings = settings,
                    syncing = syncing,
                    syncMessage = syncMessage,
                    onSync = { startSync() },
                    albumNames = albumNames,
                )
            }
            }
        }

        FloatingNavBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        )
    }

    viewing?.let { (list, index) ->
        PhotoViewer(
            photos = list,
            startIndex = index,
            starred = settings.starred,
            onDismiss = { viewing = null },
            onToggleStar = { toggleStarred(setOf(it.id)) },
            onEdit = {
                viewing = null
                editing = it
            },
            onDelete = {
                viewing = null
                pendingDelete = listOf(it)
            },
        )
    }

    editing?.let { photo ->
        EditorScreen(
            photo = photo,
            onDismiss = { editing = null },
            onSaved = {
                editing = null
                reloadTick++
            },
        )
    }

    pendingDelete?.let { targets ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(if (targets.size == 1) "Delete this photo?" else "Delete ${targets.size} photos?") },
            text = { Text("They are removed from this phone. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDelete = null
                        performDelete(targets)
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

/** Filters and sorts a list the way the gallery shows it. */
private fun List<Photo>.visible(
    starredOnly: Boolean,
    starred: Set<Long>,
    query: String,
    order: SortOrder,
): List<Photo> = filter { !starredOnly || it.id in starred }
    .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    .arrangedBy(order)

@RequiresApi(Build.VERSION_CODES.R)
private fun deleteRequest(context: Context, uris: List<Uri>): IntentSender =
    MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender

private fun appSettingsIntent(context: Context): Intent =
    Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/** Opens the system share sheet for one or more photos. */
internal fun sharePhotos(context: Context, targets: List<Photo>) {
    if (targets.isEmpty()) return
    val intent = if (targets.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_STREAM, targets.first().uri)
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(targets.map { it.uri }))
        }
    }
    intent.type = "image/*"
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}

private fun timestampNow(): String =
    DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.now())
