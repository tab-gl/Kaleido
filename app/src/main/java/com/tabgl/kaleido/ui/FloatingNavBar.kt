package com.tabgl.kaleido.ui

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Photo
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * A floating Material 3 Expressive toolbar that hovers above the content.
 * The selected destination is shown as a filled, labelled toggle; the others stay as icons.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FloatingNavBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        modifier = modifier,
    ) {
        NavItem(
            label = "Photos",
            icon = Icons.Rounded.Photo,
            selected = selected == Tab.Photos,
            onClick = { onSelect(Tab.Photos) },
        )
        Spacer(Modifier.width(4.dp))
        NavItem(
            label = "Albums",
            icon = Icons.Rounded.Collections,
            selected = selected == Tab.Albums,
            onClick = { onSelect(Tab.Albums) },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.NavItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        Icon(imageVector = icon, contentDescription = label)
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
