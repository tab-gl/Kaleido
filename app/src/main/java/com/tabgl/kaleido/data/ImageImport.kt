package com.tabgl.kaleido.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Copies picked files into Pictures/Kaleido/Imported so they show up in the gallery.
 * Returns how many imports succeeded. A file that can't be read or stored is skipped, not fatal.
 */
suspend fun importPictures(context: Context, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
    uris.count { uri ->
        runCatching {
            val name = displayNameOf(context, uri)
            val mime = context.contentResolver.getType(uri) ?: mimeFor(name)
            val source = context.contentResolver.openInputStream(uri) ?: return@runCatching false
            source.use { input ->
                writePicture(context, name, mime, "Imported") { out -> input.copyTo(out); true } != null
            }
        }.getOrDefault(false)
    }
}

private fun displayNameOf(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    } ?: "Imported_${System.currentTimeMillis()}"
