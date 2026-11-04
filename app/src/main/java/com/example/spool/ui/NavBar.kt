package com.example.spool.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.spool.ui.theme.Ember

enum class Destination(val label: String, val icon: ImageVector) {
    AUTO("Auto", Icons.Filled.AutoAwesome),
    SONGS("Songs", Icons.Filled.MusicNote),
    VIDEOS("Videos", Icons.Filled.VideoLibrary)
}

@Composable
fun SpoolNavBar(selected: Destination, onSelect: (Destination) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        Destination.entries.forEach { d ->
            NavigationBarItem(
                selected = selected == d,
                onClick = { onSelect(d) },
                icon = { Icon(d.icon, contentDescription = d.label) },
                label = { Text(d.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Ember,
                    selectedTextColor = Ember,
                    indicatorColor = Ember.copy(alpha = 0.18f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}