package com.tabgl.kaleido.ui

import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.tabgl.kaleido.R
import com.tabgl.kaleido.data.Vault
import com.tabgl.kaleido.data.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Photos moved to the vault. Opening it needs a fingerprint, face or screen lock. Images only. */
@Composable
fun VaultScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var unlocked by remember { mutableStateOf(false) }
    var unlockMessage by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var reload by remember { mutableIntStateOf(0) }
    var viewing by remember { mutableStateOf<VaultItem?>(null) }
    var pendingDelete by remember { mutableStateOf<VaultItem?>(null) }

    LaunchedEffect(attempt) {
        val activity = context as? FragmentActivity
        if (activity == null) {
            unlockMessage = "The vault can't open here."
            return@LaunchedEffect
        }
        unlockMessage = null
        authenticate(activity) { ok, message ->
            unlocked = ok
            unlockMessage = message
        }
    }

    LaunchedEffect(unlocked, reload) {
        if (unlocked) items = withContext(Dispatchers.IO) { Vault.list(context) }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (!unlocked) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = unlockMessage ?: "Unlocking…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    if (unlockMessage != null) {
                        TextButton(onClick = { attempt++ }) { Text("Try again") }
                    }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onDismiss) {
                            HiIcon(R.drawable.hi_close, "Close vault", MaterialTheme.colorScheme.onBackground)
                        }
                        Text(
                            text = "Vault · ${items.size}",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    if (items.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = "Nothing here yet. Move photos here from the gallery.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.fillMaxSize()) {
                            items(items, key = { it.file.name }) { item ->
                                VaultThumb(item) { viewing = item }
                            }
                        }
                    }
                }
            }
        }
    }

    viewing?.let { item ->
        Dialog(
            onDismissRequest = { viewing = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val bitmap by produceState<Bitmap?>(null, item) {
                value = withContext(Dispatchers.IO) { Vault.decode(item, 2048) }
            }
            Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { Vault.restore(context, item) }
                            Toast.makeText(
                                context,
                                if (ok) "Restored to Pictures/Kaleido/Restored" else "Couldn't restore",
                                Toast.LENGTH_SHORT,
                            ).show()
                            if (ok) {
                                viewing = null
                                reload++
                            }
                        }
                    }) { Text("Restore", color = Color.White) }
                    TextButton(onClick = { pendingDelete = item }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { viewing = null }) { Text("Close", color = Color.White) }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete from vault?") },
            text = { Text("The encrypted copy is removed. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewing = null
                    scope.launch {
                        withContext(Dispatchers.IO) { Vault.delete(item) }
                        reload++
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun VaultThumb(item: VaultItem, onClick: () -> Unit) {
    val thumb by produceState<Bitmap?>(null, item) {
        value = withContext(Dispatchers.IO) { Vault.decode(item, 256) }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        thumb?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Asks for a fingerprint, face or screen lock. Without any of them the vault stays closed,
 * because an open vault with no check would defeat the point of it.
 */
private fun authenticate(activity: FragmentActivity, onResult: (Boolean, String?) -> Unit) {
    val authenticators = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
    } else {
        BIOMETRIC_WEAK
    }
    if (BiometricManager.from(activity).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(false, "Turn on a fingerprint or screen lock to use the vault.")
        return
    }
    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(true, null)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onResult(false, errString.toString())
            }
        },
    )
    val info = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Open vault")
        .setAllowedAuthenticators(authenticators)
        .apply {
            if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText("Cancel")
        }
        .build()
    prompt.authenticate(info)
}
