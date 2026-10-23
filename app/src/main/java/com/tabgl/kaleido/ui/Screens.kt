package com.tabgl.kaleido.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tabgl.kaleido.R
import com.tabgl.kaleido.data.Album
import com.tabgl.kaleido.data.Photo
import com.tabgl.kaleido.data.PhotoSection
import com.tabgl.kaleido.data.SortOrder
import com.tabgl.kaleido.data.toSections

/** State and actions for the search field. Null on screens without search. */
data class SearchControls(
    val open: Boolean,
    val query: String,
    val onToggle: () -> Unit,
    val onQueryChange: (String) -> Unit,
)

/** Multi-select state: picked photo ids and the bulk actions for them. */
data class SelectionControls(
    val selected: Set<Long>,
    val allStarred: Boolean,
    val onToggle: (Long) -> Unit,
    val onStar: () -> Unit,
    val onShare: () -> Unit,
    val onDelete: () -> Unit,
    val onClear: () -> Unit,
)

/** Sort order for a gallery, and the action that picks a new one. */
data class SortControls(
    val order: SortOrder,
    val onSelect: (SortOrder) -> Unit,
)

fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.NEWEST -> "Newest first"
    SortOrder.OLDEST -> "Oldest first"
    SortOrder.NAME -> "Name A to Z"
}

/** Bottom sheet that picks the sort order. Used by galleries and by Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(current: SortOrder, onSelect: (SortOrder) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "Sort by",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        SortOrder.entries.forEach { order ->
            ListItem(
                headlineContent = { Text(sortLabel(order)) },
                trailingContent = { RadioButton(selected = order == current, onClick = null) },
                modifier = Modifier.clickable { onSelect(order) },
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun PermissionPrompt(
    showSettings: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HiIcon(
            icon = R.drawable.hi_photos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Kaleido needs access to your photos",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "Allow access so Kaleido can show your photos and albums. " +
                "On Android 14 and later you can choose which photos to share.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        androidx.compose.material3.Button(onClick = onRequest) { Text("Allow access") }
        if (showSettings) {
            // Shown after a request that didn't grant access. Once it has been denied twice,
            // the system stops showing the dialog, so the only way forward is app settings.
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Access is still off. You can turn it on in app settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenSettings) { Text("Open settings") }
        }
    }
}

/** A pulsing grid of placeholder tiles shown while photos load. It matches the real grid's shape. */
@Composable
fun LoadingState() {
    val transition = rememberInfiniteTransition(label = "loading")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "loadingAlpha",
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(5) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(18.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}

/**
 * Photos in a connected grid. Sections are grouped by day, or shown as one list when sorted by name.
 * [onOpen] receives the tapped photo's index in [photos]. Long-press starts multi-select.
 */
@Composable
fun GalleryScreen(
    title: String,
    photos: List<Photo>,
    columns: Int,
    onCycleColumns: () -> Unit,
    onOpen: (Int) -> Unit,
    onBack: (() -> Unit)? = null,
    search: SearchControls? = null,
    selection: SelectionControls? = null,
    sort: SortControls? = null,
    starred: Set<Long> = emptySet(),
    starredOnly: Boolean = false,
    onToggleStarredOnly: (() -> Unit)? = null,
    headline: String? = null,
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val byName = sort?.order == SortOrder.NAME
    val sections = remember(photos, byName) {
        if (byName) listOf(PhotoSection(label = "All photos", photos = photos)) else photos.toSections()
    }
    // Index lookup per tap, instead of scanning the whole list for every tile.
    val indexById = remember(photos) { photos.withIndex().associate { (index, photo) -> photo.id to index } }
    val queryText = search?.query.orEmpty()
    // Selection mode is on while at least one photo is picked.
    val active = selection?.takeIf { it.selected.isNotEmpty() }
    val tint = MaterialTheme.colorScheme.onBackground
    var sortSheetOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        ScreenHeader(
            title = if (active != null) "${active.selected.size} selected" else headline ?: title,
            subtitle = if (active != null) "Tap photos to add or remove" else itemCountLabel(photos.size),
            onBack = if (active != null) null else onBack,
            actions = {
                if (active != null) {
                    IconButton(onClick = active.onStar) {
                        HiIcon(
                            icon = R.drawable.hi_star,
                            contentDescription = if (active.allStarred) "Unstar selected" else "Star selected",
                            tint = if (active.allStarred) Color(0xFFFFC857) else tint,
                        )
                    }
                    IconButton(onClick = active.onShare) {
                        HiIcon(R.drawable.hi_share, "Share selected", tint)
                    }
                    IconButton(onClick = active.onDelete) {
                        HiIcon(R.drawable.hi_delete, "Delete selected", tint)
                    }
                    IconButton(onClick = active.onClear) {
                        HiIcon(R.drawable.hi_close, "Clear selection", tint)
                    }
                } else {
                    if (search != null) {
                        IconButton(onClick = search.onToggle) {
                            HiIcon(R.drawable.hi_search, if (search.open) "Close search" else "Search", tint)
                        }
                    }
                    if (sort != null) {
                        IconButton(onClick = { sortSheetOpen = true }) {
                            HiIcon(R.drawable.hi_sort, "Sort: ${sortLabel(sort.order)}", tint)
                        }
                    }
                    IconButton(onClick = onCycleColumns) {
                        HiIcon(R.drawable.hi_grid, "Grid size: $columns per row. Tap to change.", tint)
                    }
                }
            },
        )

        if (onToggleStarredOnly != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = starredOnly,
                    onClick = onToggleStarredOnly,
                    label = { Text("Starred") },
                    leadingIcon = { HiIcon(R.drawable.hi_star, null, Color(0xFFFFC857), Modifier.size(18.dp)) },
                )
            }
        }

        if (search != null) {
            AnimatedVisibility(
                visible = search.open,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                OutlinedTextField(
                    value = search.query,
                    onValueChange = search.onQueryChange,
                    singleLine = true,
                    placeholder = { Text("Search by name or album") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                )
            }
        }

        if (photos.isEmpty()) {
            EmptyState(
                when {
                    queryText.isNotBlank() -> "No photos match \"$queryText\""
                    starredOnly -> "No starred photos yet. Long-press a photo to star it."
                    else -> "No photos here yet"
                },
            )
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                // Zero spacing: tiles touch, so the grid reads as one connected surface.
                contentPadding = PaddingValues(top = 4.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                sections.forEach { section ->
                    if (!byName) {
                        item(
                            key = "header-${section.label}",
                            span = { GridItemSpan(maxLineSpan) },
                        ) {
                            SectionHeader(label = section.label, count = section.photos.size)
                        }
                    }
                    items(section.photos, key = { it.id }) { photo ->
                        PhotoTile(
                            photo = photo,
                            selected = active != null && photo.id in active.selected,
                            starred = photo.id in starred,
                            onClick = {
                                if (selection != null && active != null) {
                                    selection.onToggle(photo.id)
                                } else {
                                    onOpen(indexById.getValue(photo.id))
                                }
                            },
                            onLongClick = { selection?.onToggle?.invoke(photo.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (sortSheetOpen && sort != null) {
        SortSheet(
            current = sort.order,
            onSelect = {
                sort.onSelect(it)
                sortSheetOpen = false
            },
            onDismiss = { sortSheetOpen = false },
        )
    }
}

@Composable
fun AlbumsScreen(albums: List<Album>, onOpen: (Album) -> Unit) {
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
fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                HiIcon(R.drawable.hi_back, "Back", MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
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
        actions()
    }
}

@Composable
private fun SectionHeader(label: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HiIcon(
            icon = R.drawable.hi_photos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
