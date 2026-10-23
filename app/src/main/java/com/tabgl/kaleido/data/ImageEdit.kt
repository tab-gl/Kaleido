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
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A point in normalized image space: 0..1 on each axis, measured from the top-left of the photo. */
data class Pt(val x: Float, val y: Float)

/** A rectangle in normalized image space. */
data class NRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun normalized() = NRect(min(left, right), min(top, bottom), max(left, right), max(top, bottom))
}

/** A freehand marker stroke. [width] is a fraction of the photo's width. */
data class Stroke(val points: List<Pt>, val color: Int, val width: Float)

/** A text label whose top-left corner sits at ([x], [y]). [size] is a fraction of the photo's width. */
data class Caption(val x: Float, val y: Float, val text: String, val color: Int, val size: Float)

enum class OutputFormat(val label: String, val mime: String, val extension: String, val compress: Bitmap.CompressFormat) {
    JPEG("JPEG", "image/jpeg", "jpg", Bitmap.CompressFormat.JPEG),
    PNG("PNG", "image/png", "png", Bitmap.CompressFormat.PNG),
    WEBP("WebP", "image/webp", "webp", Bitmap.CompressFormat.WEBP),
}

/**
 * Everything the editor changes. Geometry (blur, markup, text, crop) is in normalized photo space,
 * so it stays put whatever the screen size. Rotation and flip are applied last, after the crop.
 */
data class EditState(
    val rotation: Int = 0,
    val flipX: Boolean = false,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val crop: NRect? = null,
    val blurs: List<NRect> = emptyList(),
    val strokes: List<Stroke> = emptyList(),
    val captions: List<Caption> = emptyList(),
    val format: OutputFormat = OutputFormat.JPEG,
    val quality: Int = 92,
) {
    /** Row-major 4x5 colour matrix for brightness, contrast and saturation (offsets in 0..255). */
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

/** Composes two 4x5 colour matrices. The result applies [b] first, then [a]. */
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

/** Width and height of the photo as it is shown (EXIF rotation applied), or null if unreadable. */
fun orientedSize(context: Context, uri: Uri): Pair<Int, Int>? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val rotation = exifRotation(context, uri)
    return if (rotation == 90 || rotation == 270) {
        bounds.outHeight to bounds.outWidth
    } else {
        bounds.outWidth to bounds.outHeight
    }
}

/**
 * Renders the photo with [edit] applied and saves it to Pictures/Kaleido in the chosen format.
 * The original is never changed. Returns the new photo's URI, or null if it failed.
 */
suspend fun saveEditedCopy(context: Context, source: Photo, edit: EditState): Uri? =
    withContext(Dispatchers.IO) {
        val base = decodeOriented(context, source.uri) ?: return@withContext null
        val w = base.width
        val h = base.height

        // 1. Tone adjustments on the full frame.
        val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(frame)
        val tonePaint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(edit.colorMatrix()))
        }
        canvas.drawBitmap(base, 0f, 0f, tonePaint)
        base.recycle()

        // 2. Blur regions: a heavily downscaled copy, scaled back up, drawn inside each region.
        if (edit.blurs.isNotEmpty()) {
            val blurred = fastBlur(frame)
            for (region in edit.blurs) {
                val r = region.normalized()
                canvas.save()
                canvas.clipRect(RectF(r.left * w, r.top * h, r.right * w, r.bottom * h))
                canvas.drawBitmap(blurred, 0f, 0f, null)
                canvas.restore()
            }
        }

        // 3. Markup strokes.
        for (stroke in edit.strokes) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = stroke.color
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                strokeWidth = stroke.width * w
            }
            val pts = stroke.points
            if (pts.size == 1) {
                paint.style = Paint.Style.FILL
                canvas.drawCircle(pts[0].x * w, pts[0].y * h, stroke.width * w / 2f, paint)
            } else if (pts.size > 1) {
                val path = Path()
                pts.forEachIndexed { i, p ->
                    if (i == 0) path.moveTo(p.x * w, p.y * h) else path.lineTo(p.x * w, p.y * h)
                }
                canvas.drawPath(path, paint)
            }
        }

        // 4. Text labels. The preview places the top-left corner; convert it to a baseline here.
        for (caption in edit.captions) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = caption.color
                textSize = caption.size * w
                typeface = Typeface.DEFAULT_BOLD
            }
            canvas.drawText(caption.text, caption.x * w, caption.y * h - paint.ascent(), paint)
        }

        // 5. Crop, then rotate and flip.
        var out = frame
        edit.crop?.normalized()?.let { r ->
            val left = (r.left * w).roundToInt().coerceIn(0, w - 1)
            val top = (r.top * h).roundToInt().coerceIn(0, h - 1)
            val right = (r.right * w).roundToInt().coerceIn(left + 1, w)
            val bottom = (r.bottom * h).roundToInt().coerceIn(top + 1, h)
            val cropped = Bitmap.createBitmap(out, left, top, right - left, bottom - top)
            if (cropped !== out) out.recycle()
            out = cropped
        }
        if (edit.rotation != 0 || edit.flipX) {
            val matrix = Matrix().apply {
                postRotate(edit.rotation.toFloat())
                if (edit.flipX) postScale(-1f, 1f)
            }
            val turned = Bitmap.createBitmap(out, 0, 0, out.width, out.height, matrix, true)
            if (turned !== out) out.recycle()
            out = turned
        }

        // 6. Encode in the chosen format.
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val base = source.name.substringBeforeLast('.', source.name).ifBlank { "Photo" }
        val name = "${base}_edit_$stamp.${edit.format.extension}"
        val uri = writePicture(context, name, edit.format.mime) { stream: OutputStream ->
            out.compress(edit.format.compress, edit.quality, stream)
        }
        out.recycle()
        uri
    }

/**
 * Creates a picture in Pictures/Kaleido (or a subfolder) and fills it with [write].
 * Android 10+ writes through MediaStore with the item pending, so other apps never see a partial file.
 */
fun writePicture(
    context: Context,
    displayName: String,
    mime: String,
    subdir: String = "",
    write: (OutputStream) -> Boolean,
): Uri? {
    val resolver = context.contentResolver
    val folder = if (subdir.isEmpty()) "Kaleido" else "Kaleido/$subdir"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/$folder")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val ok = runCatching { resolver.openOutputStream(uri)?.use(write) ?: false }.getOrDefault(false)
        if (!ok) {
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

    // Android 8 and 9: write the file directly, then register it with MediaStore.
    val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folder)
    dir.mkdirs()
    val file = File(dir, displayName)
    val ok = runCatching { file.outputStream().use(write) }.getOrDefault(false)
    if (!ok) {
        file.delete()
        return null
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
        put(MediaStore.Images.Media.MIME_TYPE, mime)
        put(MediaStore.Images.Media.DATA, file.absolutePath)
    }
    return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
}

private fun exifRotation(context: Context, uri: Uri): Int =
    runCatching {
        context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
    }.getOrDefault(0)

/** Decodes the photo at most [MAX_EDGE_PX] on its longest side, with EXIF rotation applied. */
private fun decodeOriented(context: Context, uri: Uri): Bitmap? {
    val resolver = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_EDGE_PX) sample *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: return null
    val rotation = exifRotation(context, uri)
    if (rotation == 0) return decoded
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    val turned = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
    if (turned !== decoded) decoded.recycle()
    return turned
}

/** A fast blur: shrink the bitmap a lot, then scale it back up with filtering. Same size as [src]. */
private fun fastBlur(src: Bitmap): Bitmap {
    val small = Bitmap.createScaledBitmap(src, max(1, src.width / 24), max(1, src.height / 24), true)
    return Bitmap.createScaledBitmap(small, src.width, src.height, true)
}
