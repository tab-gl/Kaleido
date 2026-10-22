package com.tabgl.kaleido.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Edits applied to a photo. The defaults leave it unchanged. */
data class EditState(
    val rotation: Int = 0,        // clockwise degrees: 0, 90, 180 or 270
    val flipX: Boolean = false,
    val brightness: Float = 0f,   // -0.5 .. 0.5
    val contrast: Float = 1f,     // 0.5 .. 1.5
    val saturation: Float = 1f,   // 0 .. 2
) {
    /** Row-major 4x5 colour matrix for the tone settings (offsets in 0..255 units). */
    fun colorMatrix(): FloatArray = multiply(
        offsetMatrix(brightness * 255f),
        multiply(contrastMatrix(contrast), saturationMatrix(saturation)),
    )
}

private fun saturationMatrix(s: Float) = floatArrayOf(
    0.213f + 0.787f * s, 0.715f - 0.715f * s, 0.072f - 0.072f * s, 0f, 0f,
    0.213f - 0.213f * s, 0.715f + 0.285f * s, 0.072f - 0.072f * s, 0f, 0f,
    0.213f - 0.213f * s, 0.715f - 0.715f * s, 0.072f + 0.928f * s, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

private fun contrastMatrix(c: Float): FloatArray {
    val offset = (1f - c) * 128f
    return floatArrayOf(
        c, 0f, 0f, 0f, offset,
        0f, c, 0f, 0f, offset,
        0f, 0f, c, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )
}

private fun offsetMatrix(b: Float) = floatArrayOf(
    1f, 0f, 0f, 0f, b,
    0f, 1f, 0f, 0f, b,
    0f, 0f, 1f, 0f, b,
    0f, 0f, 0f, 1f, 0f,
)

/** Composes two 4x5 colour matrices: the result applies [b] first, then [a]. */
private fun multiply(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (r in 0 until 4) {
        for (c in 0 until 4) {
            var sum = 0f
            for (k in 0 until 4) sum += a[r * 5 + k] * b[k * 5 + c]
            out[r * 5 + c] = sum
        }
        var offset = a[r * 5 + 4]
        for (k in 0 until 4) offset += a[r * 5 + k] * b[k * 5 + 4]
        out[r * 5 + 4] = offset
    }
    return out
}

private const val MAX_EDGE_PX = 4096

/**
 * Renders [source] with [edit] applied and saves it as a new JPEG in Pictures/Kaleido.
 * The original is never changed. Returns the new photo's URI, or null if it failed.
 */
suspend fun saveEditedCopy(context: Context, source: Photo, edit: EditState): Uri? =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        // Photos can carry an EXIF rotation that the viewer applies. Include it so the copy looks the same.
        val exifRotation = runCatching {
            resolver.openInputStream(source.uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)

        val decoded = decodeSampled(context, source.uri) ?: return@withContext null
        val matrix = Matrix().apply {
            postRotate((exifRotation + edit.rotation).toFloat())
            if (edit.flipX) postScale(-1f, 1f)
        }
        val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val output = Bitmap.createBitmap(turned.width, turned.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(edit.colorMatrix()))
        }
        Canvas(output).drawBitmap(turned, 0f, 0f, paint)
        decoded.recycle()
        turned.recycle()

        val uri = writeJpeg(context, output, source.name)
        output.recycle()
        uri
    }

/** Decodes the photo, scaled down so its longest edge is at most [MAX_EDGE_PX]. */
private fun decodeSampled(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
}

private fun writeJpeg(context: Context, bitmap: Bitmap, sourceName: String): Uri? {
    val resolver = context.contentResolver
    val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val base = sourceName.substringBeforeLast('.', sourceName).ifBlank { "Photo" }
    val name = "${base}_edit_$stamp.jpg"

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // Write while the item is pending, so other apps never see a half-written file.
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Kaleido")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val written = resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } ?: false
        if (!written) {
            resolver.delete(uri, null, null)
            return null
        }
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
        return uri
    }

    // Android 8 to 9: write the file directly, then register it with MediaStore.
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Kaleido")
    dir.mkdirs()
    val file = File(dir, name)
    val written = file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    if (!written) return null
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, name)
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.DATA, file.absolutePath)
    }
    return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}
