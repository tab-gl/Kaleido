package com.tabgl.kaleido.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import java.io.File
import java.io.InputStream
import java.util.UUID
import kotlin.math.max

/** One photo in the vault. The file is encrypted; [name] is the original file name. */
data class VaultItem(val file: File, val name: String, val addedAt: Long)

/**
 * Private, encrypted photo storage in app-only files. Each file is named "<uuid>.<base64 name>.enc".
 * Nothing here is visible to the gallery or to other apps.
 */
object Vault {
    private const val SUFFIX = ".enc"

    private fun dir(context: Context): File = File(context.filesDir, "vault").apply { mkdirs() }

    fun list(context: Context): List<VaultItem> =
        (dir(context).listFiles() ?: emptyArray<File>())
            .filter { it.name.endsWith(SUFFIX) }
            .mapNotNull { file ->
                val encoded = file.name.removeSuffix(SUFFIX).substringAfter('.', "")
                val name = runCatching {
                    String(Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP), Charsets.UTF_8)
                }.getOrNull() ?: return@mapNotNull null
                VaultItem(file, name, file.lastModified())
            }
            .sortedByDescending { it.addedAt }

    /** Copies a photo into the vault, encrypted. Returns false if it could not be read or stored. */
    fun add(context: Context, source: Uri, name: String): Boolean {
        val encodedName = Base64.encodeToString(name.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        val target = File(dir(context), "${UUID.randomUUID()}.$encodedName$SUFFIX")
        return runCatching {
            val input = context.contentResolver.openInputStream(source) ?: return@runCatching false
            input.use { src ->
                SecureStore.encryptingStream(target.outputStream()).use { dst -> src.copyTo(dst) }
            }
            true
        }.getOrElse {
            target.delete()
            false
        }
    }

    private fun open(item: VaultItem): InputStream = SecureStore.decryptingStream(item.file.inputStream())

    /** Decodes a picture no larger than about [maxPx] on its longest side. Null if it cannot be shown. */
    fun decode(item: VaultItem, maxPx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open(item).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
        open(item).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    }.getOrNull()

    /** Writes the decrypted picture to Pictures/Kaleido/Restored, then removes it from the vault. */
    fun restore(context: Context, item: VaultItem): Boolean {
        val extension = item.name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "image/jpeg"
        val saved = runCatching {
            writePicture(context, item.name, mime, "Restored") { out ->
                open(item).use { it.copyTo(out) }
                true
            } != null
        }.getOrDefault(false)
        if (saved) item.file.delete()
        return saved
    }

    fun delete(item: VaultItem) {
        item.file.delete()
    }
}
