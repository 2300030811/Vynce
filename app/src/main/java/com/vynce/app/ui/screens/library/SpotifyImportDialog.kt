package com.vynce.app.ui.screens.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vynce.app.db.MusicDatabase
import com.vynce.app.ui.theme.VyncePurple
import com.vynce.app.utils.SpotifyImporter
import kotlinx.coroutines.launch

@Composable
fun SpotifyImportDialog(
    database: MusicDatabase,
    onDismiss: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    var url by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var total by remember { mutableIntStateOf(0) }
    var resultMessage by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = {
            if (!isImporting) onDismiss()
        },
        icon = {
            Surface(
                color = Color(0xFF1DB954).copy(alpha = 0.15f),
                shape = androidx.compose.foundation.shape.CircleShape,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.FileDownload,
                        contentDescription = null,
                        tint = Color(0xFF1DB954),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = if (isImporting) "Importing Playlist..." else "Import Playlist Link",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (resultMessage != null) {
                    Text(resultMessage!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                } else if (errorMessage != null) {
                    Text(errorMessage!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } else if (isImporting) {
                    val progressFraction = if (total > 0) progress.toFloat() / total.toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        color = Color(0xFF1DB954)
                    )
                    Text(
                        text = "Matching track $progress of $total to 320kbps streams...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Paste any public playlist link to import and match all songs to high-fidelity 320kbps lossless audio.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("Playlist URL or ID") },
                        placeholder = { Text("https://...") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            if (resultMessage != null || errorMessage != null) {
                TextButton(onClick = onDismiss) {
                    Text("Done")
                }
            } else if (!isImporting) {
                Button(
                    onClick = {
                        val inputUrl = url.trim()
                        if (inputUrl.isNotBlank()) {
                            isImporting = true
                            errorMessage = null
                            coroutineScope.launch {
                                val result = SpotifyImporter.importPlaylist(
                                    urlOrId = inputUrl,
                                    database = database,
                                    onProgress = { p, t ->
                                        progress = p
                                        total = t
                                    }
                                )
                                isImporting = false
                                if (result.isSuccess) {
                                    resultMessage = "Successfully imported: ${result.getOrNull()}"
                                } else {
                                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to import playlist"
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1DB954))
                ) {
                    Text("Import")
                }
            }
        },
        dismissButton = {
            if (!isImporting && resultMessage == null && errorMessage == null) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
