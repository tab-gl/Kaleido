package com.tabgl.kaleido.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tabgl.kaleido.R
import com.tabgl.kaleido.data.AppSettings
import com.tabgl.kaleido.data.ThemeMode

private const val REPO_URL = "https://github.com/tab-gl/Kaleido"
private const val HUGEICONS_URL = "https://hugeicons.com"

/** Look, sync and about. Every change saves immediately. */
@Composable
fun SettingsScreen(
    settings: AppSettings,
    syncing: Boolean,
    syncMessage: String,
    onSync: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val monetSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var sortOpen by remember { mutableStateOf(false) }
    var webdavOpen by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
    ) {
        ScreenHeader(title = "Settings", subtitle = "Look, sync and about")

        SectionTitle("Appearance")
        val themes = ThemeMode.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            themes.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = settings.themeMode == mode,
                    onClick = { settings.saveThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
                    label = {
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "System"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                        )
                    },
                )
            }
        }
        ListItem(
            headlineContent = { Text("Monet colours") },
            supportingContent = {
                Text(if (monetSupported) "Match colours to your wallpaper" else "Needs Android 12 or newer")
            },
            trailingContent = {
                Switch(
                    checked = monetSupported && settings.dynamicColor,
                    onCheckedChange = null,
                    enabled = monetSupported,
                )
            },
            modifier = Modifier.clickable(enabled = monetSupported) {
                settings.saveDynamicColor(!settings.dynamicColor)
            },
        )

        SectionTitle("Photos")
        val counts = listOf(3, 4, 5)
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            counts.forEachIndexed { index, count ->
                SegmentedButton(
                    selected = settings.columns == count,
                    onClick = { settings.saveColumns(count) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = counts.size),
                    label = { Text("$count per row") },
                )
            }
        }
        ListItem(
            headlineContent = { Text("Default sort") },
            supportingContent = { Text(sortLabel(settings.sortOrder)) },
            modifier = Modifier.clickable { sortOpen = true },
        )

        SectionTitle("Cloud")
        ListItem(
            headlineContent = { Text("WebDAV sync") },
            supportingContent = {
                Text(
                    when {
                        syncing -> "Syncing…"
                        syncMessage.isNotEmpty() -> syncMessage
                        settings.webdavUrl.isBlank() -> "Not connected"
                        else -> settings.webdavUrl
                    },
                )
            },
            modifier = Modifier.clickable { webdavOpen = true },
        )

        SectionTitle("About")
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF191418)),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    text = "Kaleido",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Version $version",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ListItem(
            headlineContent = { Text("Source code") },
            supportingContent = { Text("github.com/tab-gl/Kaleido") },
            modifier = Modifier.clickable { uriHandler.openUri(REPO_URL) },
        )
        ListItem(
            headlineContent = { Text("License") },
            supportingContent = { Text("MIT") },
            modifier = Modifier.clickable { uriHandler.openUri("$REPO_URL/blob/main/LICENSE") },
        )
        ListItem(
            headlineContent = { Text("Icons") },
            supportingContent = { Text("Hugeicons") },
            modifier = Modifier.clickable { uriHandler.openUri(HUGEICONS_URL) },
        )
    }

    if (sortOpen) {
        SortSheet(
            current = settings.sortOrder,
            onSelect = {
                settings.saveSortOrder(it)
                sortOpen = false
            },
            onDismiss = { sortOpen = false },
        )
    }
    if (webdavOpen) {
        WebDavSheet(
            settings = settings,
            syncing = syncing,
            message = syncMessage,
            onSync = onSync,
            onDismiss = { webdavOpen = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebDavSheet(
    settings: AppSettings,
    syncing: Boolean,
    message: String,
    onSync: () -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(settings.webdavUrl) }
    var user by remember { mutableStateOf(settings.webdavUser) }
    var password by remember { mutableStateOf(settings.webdavPassword) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("WebDAV sync", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Uploads photos the server doesn't have yet, and downloads new ones into " +
                    "Pictures/Kaleido/WebDAV. Use an https address. Your password is encrypted with the Android Keystore.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("Server URL") },
                placeholder = { Text("https://host/remote.php/dav/files/you") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = !syncing,
                    onClick = { settings.saveWebDav(url.trim(), user.trim(), password) },
                ) { Text("Save") }
                Button(
                    enabled = !syncing && url.isNotBlank(),
                    onClick = {
                        settings.saveWebDav(url.trim(), user.trim(), password)
                        onSync()
                    },
                ) { Text("Sync now") }
            }
            if (syncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (settings.lastSync.isNotEmpty()) {
                Text(
                    text = "Last sync: ${settings.lastSync}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp),
    )
}
