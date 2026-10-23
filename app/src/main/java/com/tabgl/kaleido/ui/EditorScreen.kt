package com.tabgl.kaleido.ui

import androidx.compose.ui.text.font.FontFamily
import com.tabgl.kaleido.data.TextFont
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tabgl.kaleido.R
import com.tabgl.kaleido.data.Caption
import com.tabgl.kaleido.data.EditState
import com.tabgl.kaleido.data.NRect
import com.tabgl.kaleido.data.OutputFormat
import com.tabgl.kaleido.data.Photo
import com.tabgl.kaleido.data.Pt
import com.tabgl.kaleido.data.Stroke as MarkStroke
import com.tabgl.kaleido.data.orientedSize
import com.tabgl.kaleido.data.saveEditedCopy
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Tool { NONE, CROP, BLUR, MARKUP, TEXT }

private enum class Panel { ADJUST, CONVERT, TEXT }

/** Marker colours. The first one is the default. */
private val markColors = listOf(
    Color.White,
    Color.Black,
    Color(0xFFFF8A3D),
    Color(0xFFFF4D4D),
    Color(0xFFFFD23F),
    Color(0xFF4CD964),
    Color(0xFF3DA9FC),
)

/** Marker and text stroke width, as a fraction of the photo's width. */
private const val MARK_WIDTH = 0.012f

/**
 * Full-screen editor. Drag to crop, blur or mark; tap to place text. Rotate, flip, adjust and convert
 * use chips and sheets. Save writes a new copy and leaves the original untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(photo: Photo, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val ink = MaterialTheme.colorScheme.onBackground

    var edit by remember { mutableStateOf(EditState()) }
    val history = remember { mutableStateListOf<EditState>() }
    val redoStack = remember { mutableStateListOf<EditState>() }
    // True while the before-view button is held: shows the untouched photo with no overlays.
    var comparing by remember { mutableStateOf(false) }
    var tool by remember { mutableStateOf(Tool.NONE) }
    var panel by remember { mutableStateOf<Panel?>(null) }
    var pendingText by remember { mutableStateOf<Pt?>(null) }
    var markColor by remember { mutableStateOf(markColors[3]) }
    var dims by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var boxSize by remember { mutableStateOf(IntSize.Zero) }
    var saving by remember { mutableStateOf(false) }
    // Gesture drafts, in normalized photo space. They become history entries when the gesture ends.
    var draftRect by remember { mutableStateOf<NRect?>(null) }
    var draftStroke by remember { mutableStateOf<List<Pt>>(emptyList()) }

    LaunchedEffect(photo.uri) {
        dims = withContext(Dispatchers.IO) { orientedSize(context, photo.uri) }
    }

    fun commit(next: EditState) {
        history.add(edit)
        redoStack.clear()
        edit = next
    }

    fun undo() {
        if (history.isNotEmpty()) {
            redoStack.add(edit)
            edit = history.removeAt(history.lastIndex)
        }
    }

    fun redoAction() {
        if (redoStack.isNotEmpty()) {
            history.add(edit)
            edit = redoStack.removeAt(redoStack.lastIndex)
        }
    }

    Dialog(
        onDismissRequest = { if (!saving) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss, enabled = !saving) {
                    HiIcon(R.drawable.hi_close, "Cancel", ink)
                }
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.titleLarge,
                    color = ink,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconButton(
                    onClick = {},
                    enabled = !saving,
                    modifier = Modifier.pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            comparing = true
                            tryAwaitRelease()
                            comparing = false
                        })
                    },
                ) {
                    HiIcon(R.drawable.hi_eye, "Hold to see original", ink)
                }
                IconButton(onClick = { undo() }, enabled = history.isNotEmpty() && !saving) {
                    HiIcon(R.drawable.hi_undo, "Undo", ink)
                }
                IconButton(onClick = { redoAction() }, enabled = redoStack.isNotEmpty() && !saving) {
                    HiIcon(R.drawable.hi_undo, "Redo", ink, Modifier.graphicsLayer { scaleX = -1f })
                }
                Button(
                    enabled = !saving && dims != null,
                    onClick = {
                        scope.launch {
                            saving = true
                            val uri = runCatching { saveEditedCopy(context, photo, edit) }.getOrNull()
                            saving = false
                            Toast.makeText(
                                context,
                                if (uri != null) "Saved a copy to Pictures/Kaleido" else "Couldn't save this photo",
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (uri != null) onSaved()
                        }
                    },
                ) { Text("Save") }
            }

            // The stage: the photo, sized to its own aspect ratio so normalized coordinates line up.
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(12.dp),
                contentAlignment = Alignment.Center,
            ) {
                val d = dims
                if (d == null) {
                    CircularProgressIndicator()
                } else {
                    Box(
                        modifier = Modifier
                            .aspectRatio(d.first.toFloat() / d.second)
                            .onSizeChanged { boxSize = it }
                            .graphicsLayer {
                                rotationZ = if (comparing) 0f else edit.rotation + edit.straighten
                                scaleX = if (!comparing && edit.flipX) -1f else 1f
                            }
                            .pointerInput(tool) {
                                when (tool) {
                                    Tool.CROP, Tool.BLUR -> detectDragGestures(
                                        onDragStart = { p ->
                                            val n = normalize(p, boxSize)
                                            draftRect = NRect(n.x, n.y, n.x, n.y)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            val n = normalize(change.position, boxSize)
                                            draftRect = draftRect?.copy(right = n.x, bottom = n.y)
                                        },
                                        onDragEnd = {
                                            val r = draftRect?.normalized()
                                            draftRect = null
                                            if (r != null && r.isBig()) {
                                                if (tool == Tool.CROP) {
                                                    commit(edit.copy(crop = r))
                                                } else {
                                                    commit(edit.copy(blurs = edit.blurs + r))
                                                }
                                            }
                                        },
                                        onDragCancel = { draftRect = null },
                                    )

                                    Tool.MARKUP -> detectDragGestures(
                                        onDragStart = { p -> draftStroke = listOf(normalize(p, boxSize)) },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            draftStroke = draftStroke + normalize(change.position, boxSize)
                                        },
                                        onDragEnd = {
                                            val pts = draftStroke
                                            draftStroke = emptyList()
                                            if (pts.isNotEmpty()) {
                                                commit(
                                                    edit.copy(
                                                        strokes = edit.strokes +
                                                            MarkStroke(pts, markColor.toArgb(), MARK_WIDTH),
                                                    ),
                                                )
                                            }
                                        },
                                        onDragCancel = { draftStroke = emptyList() },
                                    )

                                    Tool.TEXT -> detectTapGestures(
                                        onTap = { p ->
                                            pendingText = normalize(p, boxSize)
                                            panel = Panel.TEXT
                                        },
                                    )

                                    else -> Unit
                                }
                            },
                    ) {
                        AsyncImage(
                            model = photo.uri,
                            contentDescription = photo.name,
                            contentScale = ContentScale.FillBounds,
                            colorFilter = if (comparing) null else ColorFilter.colorMatrix(ColorMatrix(edit.colorMatrix())),
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (!comparing) Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height

                            fun drawMark(points: List<Pt>, color: Color) {
                                if (points.size < 2) return
                                val path = Path()
                                points.forEachIndexed { i, p ->
                                    if (i == 0) path.moveTo(p.x * w, p.y * h) else path.lineTo(p.x * w, p.y * h)
                                }
                                drawPath(
                                    path = path,
                                    color = color,
                                    style = Stroke(
                                        width = MARK_WIDTH * w,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round,
                                    ),
                                )
                            }

                            // Blur regions. The preview shows a frosted patch; the saved copy gets a real blur.
                            val blurRects = edit.blurs + listOfNotNull(draftRect?.takeIf { tool == Tool.BLUR })
                            blurRects.forEach { r ->
                                val n = r.normalized()
                                val topLeft = Offset(n.left * w, n.top * h)
                                val rectSize = Size((n.right - n.left) * w, (n.bottom - n.top) * h)
                                drawRect(color = Color.White.copy(alpha = 0.35f), topLeft = topLeft, size = rectSize)
                                drawRect(
                                    color = Color.White,
                                    topLeft = topLeft,
                                    size = rectSize,
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                                    ),
                                )
                            }

                            edit.strokes.forEach { s ->
                                drawMark(s.points, Color(s.color))
                            }
                            if (draftStroke.size > 1) drawMark(draftStroke, markColor)

                            edit.captions.forEach { c ->
                                drawText(
                                    textMeasurer = measurer,
                                    text = c.text,
                                    topLeft = Offset(c.x * w, c.y * h),
                                    style = TextStyle(
                                        color = Color(c.color),
                                        fontSize = with(density) { (c.size * w).toSp() },
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = c.font.family(),
                                    ),
                                )
                            }

                            val crop = (draftRect?.takeIf { tool == Tool.CROP } ?: edit.crop)?.normalized()
                            if (crop != null) {
                                val dim = Color.Black.copy(alpha = 0.55f)
                                val cropTop = crop.top * h
                                val cropBottom = crop.bottom * h
                                val cropLeft = crop.left * w
                                val cropRight = crop.right * w
                                drawRect(dim, Offset.Zero, Size(w, cropTop))
                                drawRect(dim, Offset(0f, cropBottom), Size(w, h - cropBottom))
                                drawRect(dim, Offset(0f, cropTop), Size(cropLeft, cropBottom - cropTop))
                                drawRect(dim, Offset(cropRight, cropTop), Size(w - cropRight, cropBottom - cropTop))
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset(cropLeft, cropTop),
                                    size = Size(cropRight - cropLeft, cropBottom - cropTop),
                                    style = Stroke(width = 1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }

            // Tool chips: what a drag or tap does on the photo.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolChip("Crop", R.drawable.hi_crop, tool == Tool.CROP) {
                    tool = if (tool == Tool.CROP) Tool.NONE else Tool.CROP
                }
                ToolChip("Blur", R.drawable.hi_blur, tool == Tool.BLUR) {
                    tool = if (tool == Tool.BLUR) Tool.NONE else Tool.BLUR
                }
                ToolChip("Markup", R.drawable.hi_markup, tool == Tool.MARKUP) {
                    tool = if (tool == Tool.MARKUP) Tool.NONE else Tool.MARKUP
                }
                ToolChip("Text", R.drawable.hi_text, tool == Tool.TEXT) {
                    tool = if (tool == Tool.TEXT) Tool.NONE else Tool.TEXT
                }
            }

            if (tool == Tool.CROP) {
                val d = dims
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("Free" to null, "1:1" to 1f, "4:3" to 4f / 3f, "3:2" to 1.5f, "16:9" to 16f / 9f)
                        .forEach { (label, aspect) ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    if (d != null) {
                                        commit(edit.copy(crop = if (aspect == null) null else cropRect(d.first, d.second, aspect)))
                                    }
                                },
                                label = { Text(label) },
                            )
                        }
                }
            }
            if (tool == Tool.MARKUP) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    markColors.forEach { c ->
                        ColorSwatch(color = c, selected = c == markColor) { markColor = c }
                    }
                }
            }

            // Action chips: geometry, adjustments and output.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistChip(
                    onClick = { commit(edit.copy(rotation = (edit.rotation + 90) % 360)) },
                    label = { Text("Rotate") },
                    leadingIcon = { HiIcon(R.drawable.hi_rotate, null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { commit(edit.copy(flipX = !edit.flipX)) },
                    label = { Text("Flip") },
                    leadingIcon = { HiIcon(R.drawable.hi_flip, null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { panel = Panel.ADJUST },
                    label = { Text("Adjust") },
                    leadingIcon = { HiIcon(R.drawable.hi_adjust, null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { panel = Panel.CONVERT },
                    label = { Text("Convert: ${edit.format.label}") },
                    leadingIcon = { HiIcon(R.drawable.hi_convert, null, modifier = Modifier.size(18.dp)) },
                )
                AssistChip(
                    onClick = { commit(EditState()) },
                    label = { Text("Reset") },
                )
            }
        }
    }

    when (panel) {
        Panel.ADJUST -> AdjustSheet(
            edit = edit,
            onChange = { edit = it },
            onDismiss = { panel = null },
        )

        Panel.CONVERT -> ConvertSheet(
            edit = edit,
            onChange = { edit = it },
            onDismiss = { panel = null },
        )

        Panel.TEXT -> TextSheet(
            color = markColor,
            onColor = { markColor = it },
            onAdd = { text, font ->
                pendingText?.let { p ->
                    commit(edit.copy(captions = edit.captions + Caption(p.x, p.y, text, markColor.toArgb(), 0.05f, font)))
                }
                pendingText = null
                panel = null
            },
            onDismiss = {
                pendingText = null
                panel = null
            },
        )

        null -> Unit
    }
}

private fun normalize(p: Offset, box: IntSize): Pt = Pt(
    x = (p.x / box.width.coerceAtLeast(1)).coerceIn(0f, 1f),
    y = (p.y / box.height.coerceAtLeast(1)).coerceIn(0f, 1f),
)

private fun NRect.isBig(): Boolean = abs(right - left) > 0.01f && abs(bottom - top) > 0.01f

@Composable
private fun ToolChip(label: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { HiIcon(icon, null, modifier = Modifier.size(18.dp)) },
    )
}

@Composable
private fun ColorSwatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color)
            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun SliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdjustSheet(edit: EditState, onChange: (EditState) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Adjust", style = MaterialTheme.typography.titleLarge)
            SliderRow("Brightness", "%.2f".format(edit.brightness), edit.brightness, -0.5f..0.5f) {
                onChange(edit.copy(brightness = it))
            }
            SliderRow("Contrast", "%.2f".format(edit.contrast), edit.contrast, 0.5f..1.5f) {
                onChange(edit.copy(contrast = it))
            }
            SliderRow("Saturation", "%.2f".format(edit.saturation), edit.saturation, 0f..2f) {
                onChange(edit.copy(saturation = it))
            }
            SliderRow("Warmth", "%.2f".format(edit.warmth), edit.warmth, -1f..1f) {
                onChange(edit.copy(warmth = it))
            }
            SliderRow("Straighten", "%.1f°".format(edit.straighten), edit.straighten, -15f..15f) {
                onChange(edit.copy(straighten = it))
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Black and white", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                Switch(checked = edit.grayscale, onCheckedChange = { onChange(edit.copy(grayscale = it)) })
            }
            TextButton(onClick = { onChange(edit.copy(brightness = 0f, contrast = 1f, saturation = 1f, warmth = 0f, grayscale = false, straighten = 0f)) }) {
                Text("Reset adjustments")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConvertSheet(edit: EditState, onChange: (EditState) -> Unit, onDismiss: () -> Unit) {
    val formats = OutputFormat.entries
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Convert", style = MaterialTheme.typography.titleLarge)
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                formats.forEachIndexed { index, format ->
                    SegmentedButton(
                        selected = edit.format == format,
                        onClick = { onChange(edit.copy(format = format)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = formats.size),
                        label = { Text(format.label) },
                    )
                }
            }
            if (edit.format != OutputFormat.PNG) {
                SliderRow("Quality", edit.quality.toString(), edit.quality.toFloat(), 50f..100f) {
                    onChange(edit.copy(quality = it.roundToInt()))
                }
            }
            Text(
                text = "Saved as .${edit.format.extension} in Pictures/Kaleido. PNG is lossless but larger.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TextSheet(
    color: Color,
    onColor: (Color) -> Unit,
    onAdd: (String, TextFont) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    var font by remember { mutableStateOf(TextFont.SANS) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add text", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                markColors.forEach { c -> ColorSwatch(color = c, selected = c == color) { onColor(c) } }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextFont.entries.forEach { f ->
                    FilterChip(
                        selected = f == font,
                        onClick = { font = f },
                        label = { Text(f.label, fontFamily = f.family()) },
                    )
                }
            }
            Button(enabled = text.isNotBlank(), onClick = { onAdd(text.trim(), font) }) {
                Text("Place on photo")
            }
        }
    }
}

/** A centred crop with the given width/height ratio, as large as the photo allows. */
private fun cropRect(imgW: Int, imgH: Int, aspect: Float): NRect {
    val imgAspect = imgW.toFloat() / imgH
    val (w, h) = if (imgAspect > aspect) aspect / imgAspect to 1f else 1f to imgAspect / aspect
    return NRect((1f - w) / 2f, (1f - h) / 2f, (1f + w) / 2f, (1f + h) / 2f)
}

private fun TextFont.family(): FontFamily = when (this) {
    TextFont.SANS -> FontFamily.SansSerif
    TextFont.SERIF -> FontFamily.Serif
    TextFont.MONO -> FontFamily.Monospace
    TextFont.CURSIVE -> FontFamily.Cursive
}
