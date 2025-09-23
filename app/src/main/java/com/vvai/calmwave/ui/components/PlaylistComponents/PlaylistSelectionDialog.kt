package com.vvai.calmwave.ui.components.PlaylistComponents

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Column

/**
 * Simple AlertDialog that lists playlists and allows selecting one.
 * - playlists: list of playlist titles
 * - current: title of current playlist (nullable)
 * - onSelect: invoked with new playlist title when user picks one
 * - onDismiss: invoked when dialog is dismissed
 */
@Composable
fun PlaylistSelectionDialog(
    playlists: List<String>,
    current: String?,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Use a key to force recomposition if the target audio changes
    key(current ?: "__no_key__") {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Escolha a playlist") },
            text = {
                Column {
                    // We keep the UI minimal; each playlist is shown as a TextButton
                    playlists.forEach { playlist ->
                        val isCurrent = playlist == current
                        TextButton(
                            onClick = { if (!isCurrent) onSelect(playlist) },
                            enabled = !isCurrent
                        ) {
                            Text(text = playlist)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        )
    }
}
