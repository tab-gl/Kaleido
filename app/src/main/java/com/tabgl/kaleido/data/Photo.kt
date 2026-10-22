package com.tabgl.kaleido.data

import android.net.Uri

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val dateAdded: Long,
    val album: String,
)

data class Album(
    val name: String,
    val cover: Photo,
    val count: Int,
)

/** Groups photos by their MediaStore bucket. The cover is the newest photo in each album. */
fun List<Photo>.toAlbums(): List<Album> =
    groupBy { it.album }
        .map { (name, items) -> Album(name = name, cover = items.first(), count = items.size) }
        .sortedBy { it.name.lowercase() }
