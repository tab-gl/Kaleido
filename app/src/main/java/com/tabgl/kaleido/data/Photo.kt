package com.tabgl.kaleido.data

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val album: String,
    val isVideo: Boolean = false,
    val durationMs: Long = 0L,
)

/** "m:ss" for video length badges. */
fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

data class Album(
    val name: String,
    val cover: Photo,
    val count: Int,
)

/** A run of photos taken on the same day, with a human label such as "Today". */
data class PhotoSection(
    val label: String,
    val photos: List<Photo>,
)

/** Orders photos for display. The repository returns them newest first. */
fun List<Photo>.arrangedBy(order: SortOrder): List<Photo> = when (order) {
    SortOrder.NEWEST -> this
    SortOrder.OLDEST -> reversed()
    SortOrder.NAME -> sortedBy { it.name.lowercase() }
}

/** Groups photos by their MediaStore bucket. The cover is the newest photo in each album. */
fun List<Photo>.toAlbums(): List<Album> =
    groupBy { it.album }
        .map { (name, items) -> Album(name = name, cover = items.first(), count = items.size) }
        .sortedBy { it.name.lowercase() }

/**
 * Splits photos (newest first) into day sections. Keeps the input order, so the
 * newest day comes first.
 */
fun List<Photo>.toSections(
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault(),
): List<PhotoSection> {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    return groupBy { Instant.ofEpochSecond(it.dateAdded).atZone(zone).toLocalDate() }
        .map { (date, items) ->
            val label = when (date) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> date.format(formatter)
            }
            PhotoSection(label = label, photos = items)
        }
}
