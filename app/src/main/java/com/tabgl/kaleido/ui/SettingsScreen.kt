package com.tabgl.kaleido.ui

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tabgl.kaleido.R
import com.tabgl.kaleido.data.AppSettings
import com.tabgl.kaleido.data.SortOrder
import com.tabgl.kaleido.data.ThemeMode

private const val REPO_URL = "https://github.com/tab-gl/Kaleido"

/** Appearance and behaviour settings, plus an About section. Every change saves immediately. */
@Composable
fun SettingsScreen(settings: AppSettings) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }
    val monetSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 120.dp),
    ) {
        ScreenHeader(title = "Settings", subtitle = "Look and behaviour")

        SectionTitle("Appearance")
        ThemeMode.entries.forEach { mode ->
            OptionRow(
                label = when (mode) {
                    ThemeMode.SYSTEM -> "Follow system"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                selected = settings.themeMode == mode,
                onClick = { settings.setThemeMode(mode) },
            )
        }
        SwitchRow(
            title = "Monet colours",
            subtitle = if (monetSupported) "Match colours to your wallpaper" else "Needs Android 12 or newer",
            checked = monetSupported && settings.dynamicColor,
            enabled = monetSupported,
            onChange = { settings.setDynamicColor(it) },
        )

        SectionTitle("Photos")
        listOf(3, 4, 5).forEach { count ->
            OptionRow(
                label = "$count per row",
                selected = settings.columns == count,
                onClick = { settings.setColumns(count) },
            )
        }
        SortOrder.entries.forEach { order ->
            OptionRow(
                label = sortLabel(order),
                selected = settings.sortOrder == order,
                onClick = { settings.setSortOrder(order) },
            )
        }

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
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column {
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
        Text(
            text = "A fluid, open-source photo gallery built with Jetpack Compose and Material 3.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = { uriHandler.openUri(REPO_URL) }) { Text("Source code") }
            OutlinedButton(onClick = { uriHandler.openUri("$REPO_URL/blob/main/LICENSE") }) { Text("MIT License") }
        }
        Text(
            text = "Made by tab-gl",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
    }
}

private fun sortLabel(order: SortOrder): String = when (order) {
    SortOrder.NEWEST -> "Newest first"
    SortOrder.OLDEST -> "Oldest first"
    SortOrder.NAME -> "Name A to Z"
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

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
