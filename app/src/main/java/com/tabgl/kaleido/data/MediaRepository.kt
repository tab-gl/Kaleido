package com.tabgl.kaleido.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object MediaRepository {

    /**
     * Loads every photo and video on the device, newest first. Runs off the main thread.
     * Image and video MediaStore ids overlap, so ids are made unique: images are even, videos odd.
     */
    suspend fun loadPhotos(context: Context): List<Photo> = withContext(Dispatchers.IO) {
        val images = query(context, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, isVideo = false)
        val videos = query(context, MediaStore.Video.Media.EXTERNAL_CONTENT_URI, isVideo = true)
        (images + videos).sortedByDescending { it.dateAdded }
    }

    private fun query(context: Context, collection: Uri, isVideo: Boolean): List<Photo> {
        val projection = buildList {
            add(MediaStore.MediaColumns._ID)
            add(MediaStore.MediaColumns.DISPLAY_NAME)
            add(MediaStore.MediaColumns.DATE_ADDED)
            add(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            if (isVideo) add(MediaStore.Video.Media.DURATION)
        }.toTypedArray()
        val photos = mutableListOf<Photo>()

        context.contentResolver.query(collection, projection, null, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
            ?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val durationColumn = if (isVideo) cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION) else -1

                while (cursor.moveToNext()) {
                    val mediaId = cursor.getLong(idColumn)
                    photos += Photo(
                        id = if (isVideo) mediaId * 2 + 1 else mediaId * 2,
                        uri = ContentUris.withAppendedId(collection, mediaId),
                        name = cursor.getString(nameColumn).orEmpty(),
                        dateAdded = cursor.getLong(dateColumn),
                        album = cursor.getString(bucketColumn) ?: "Unknown",
                        isVideo = isVideo,
                        durationMs = if (isVideo) cursor.getLong(durationColumn) else 0L,
                    )
                }
            }
        return photos
    }
}
