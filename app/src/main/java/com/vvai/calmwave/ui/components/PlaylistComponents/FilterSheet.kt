package com.vvai.calmwave.ui.components.PlaylistComponents

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * FilterSheet shows a ModalBottomSheet with options to filter by favorites and by playlist.
 * - playlists: list of playlist titles
 * - initialOnlyFavorites: initial value for onlyFavorites
 * - initialPlaylist: initial playlist filter
 * - onApply: invoked with (onlyFavorites, playlistFilter?) when user applies
 * - onDismiss: invoked to close
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterSheet(
    playlists: List<String>,
    initialOnlyFavorites: Boolean,
    initialPlaylist: String?,
    onApply: (Boolean, String?) -> Unit,
    onDismiss: () -> Unit
) {
    var tempOnlyFavorites by remember { mutableStateOf(initialOnlyFavorites) }
    var tempPlaylistFilter by remember { mutableStateOf(initialPlaylist) }
    var expandedPlaylistDropdown by remember { mutableStateOf(false) }
    val sheetColor = Color(0xFF2DC9C6)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        containerColor = sheetColor,
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "Filtros",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(
                        checked = tempOnlyFavorites,
                        onCheckedChange = { tempOnlyFavorites = it },
                        colors = CheckboxDefaults.colors()
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Apenas favoritos", color = Color.White)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Playlist:", color = Color.White)
                ExposedDropdownMenuBox(
                    expanded = expandedPlaylistDropdown,
                    onExpandedChange = { expandedPlaylistDropdown = !expandedPlaylistDropdown }
                ) {
                    OutlinedTextField(
                        value = tempPlaylistFilter ?: "Todas",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Playlist") },
                        trailingIcon = {},
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedPlaylistDropdown,
                        onDismissRequest = { expandedPlaylistDropdown = false }
                    ) {
                        DropdownMenuItem(text = { Text("Todas") }, onClick = { tempPlaylistFilter = null; expandedPlaylistDropdown = false })
                        playlists.forEach { playlist ->
                            DropdownMenuItem(text = { Text(playlist) }, onClick = { tempPlaylistFilter = playlist; expandedPlaylistDropdown = false })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancelar", color = Color.White) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onApply(tempOnlyFavorites, tempPlaylistFilter) },
                        colors = ButtonDefaults.buttonColors()
                    ) {
                        Text("Aplicar", color = Color.White)
                    }
                }
            }
        }
    )
}
