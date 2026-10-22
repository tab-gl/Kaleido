package com.tabgl.kaleido.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.tabgl.kaleido.data.EditState
import com.tabgl.kaleido.data.Photo
import com.tabgl.kaleido.data.saveEditedCopy
import kotlinx.coroutines.launch

/**
 * Full-screen editor. Rotate, flip and tone changes show live on the preview. Save writes a new
 * copy to Pictures/Kaleido and leaves the original untouched.
 */
@Composable
fun EditorDialog(photo: Photo, onDismiss: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var edit by remember { mutableStateOf(EditState()) }
    var saving by remember { mutableStateOf(false) }
    val ink = MaterialTheme.colorScheme.onBackground

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
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Cancel", tint = ink)
                }
                Text(
                    text = "Edit",
                    style = MaterialTheme.typography.titleLarge,
                    color = ink,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                TextButton(
                    enabled = !saving,
                    onClick = {
                        scope.launch {
                            saving = true
                            val uri = saveEditedCopy(context, photo, edit)
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

            Box(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = photo.uri,
                    contentDescription = photo.name,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.colorMatrix(ColorMatrix(edit.colorMatrix())),
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        rotationZ = edit.rotation.toFloat()
                        scaleX = if (edit.flipX) -1f else 1f
                    },
                )
                if (saving) CircularProgressIndicator()
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { edit = edit.copy(rotation = (edit.rotation + 90) % 360) }) {
                    Icon(imageVector = Icons.Rounded.Refresh, contentDescription = "Rotate", tint = ink)
                }
                IconButton(onClick = { edit = edit.copy(flipX = !edit.flipX) }) {
                    Icon(imageVector = Icons.Rounded.SwapHoriz, contentDescription = "Flip", tint = ink)
                }
                TextButton(onClick = { edit = EditState() }) { Text("Reset") }
            }

            ToneSlider("Brightness", edit.brightness, -0.5f..0.5f) { edit = edit.copy(brightness = it) }
            ToneSlider("Contrast", edit.contrast, 0.5f..1.5f) { edit = edit.copy(contrast = it) }
            ToneSlider("Saturation", edit.saturation, 0f..2f) { edit = edit.copy(saturation = it) }
        }
    }
}

@Composable
private fun ToneSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
