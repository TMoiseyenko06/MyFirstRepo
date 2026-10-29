package com.beatshow.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    photos: List<File>,
    importProgress: Pair<Int, Int>?,
    message: String?,
    onMessageShown: () -> Unit,
    onImport: (List<android.net.Uri>) -> Unit,
    onImportFolder: (android.net.Uri?) -> Unit,
    onDelete: (Collection<File>) -> Unit,
    onClearAll: () -> Unit,
    onPlay: () -> Unit,
    onSettings: () -> Unit,
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { onImport(it) }
    // The system file browser offers "Select all" in its menu, which the photo picker lacks.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { onImport(it) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { onImportFolder(it) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        if (message != null) {
            snackbar.showSnackbar(message)
            onMessageShown()
        }
    }

    fun exitSelect() {
        selecting = false
        selected = emptySet()
    }

    BackHandler(enabled = selecting) { exitSelect() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(if (selecting) "${selected.size} selected" else "BeatShow") },
                navigationIcon = {
                    if (selecting) {
                        IconButton(onClick = ::exitSelect) { Icon(AppIcons.Close, "Cancel selection") }
                    }
                },
                actions = {
                    if (selecting) {
                        IconButton(
                            enabled = selected.isNotEmpty(),
                            onClick = {
                                onDelete(photos.filter { it.name in selected })
                                exitSelect()
                            },
                        ) { Icon(AppIcons.Delete, "Delete selected") }
                    } else {
                        if (photos.isNotEmpty()) {
                            IconButton(onClick = { selecting = true }) { Icon(AppIcons.Select, "Select") }
                        }
                        IconButton(onClick = onSettings) { Icon(AppIcons.Settings, "Settings") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(AppIcons.More, "More") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Clear all") },
                                    enabled = photos.isNotEmpty(),
                                    onClick = {
                                        menuOpen = false
                                        confirmClear = true
                                    },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { addMenuOpen = true },
                        enabled = importProgress == null,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) {
                        Icon(AppIcons.Add, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Add photos")
                    }
                    DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                        DropdownMenuItem(
                            text = { MenuText("From gallery", "Photo picker") },
                            onClick = {
                                addMenuOpen = false
                                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                        )
                        DropdownMenuItem(
                            text = { MenuText("From files", "Use ⋮ → Select all to pick everything") },
                            onClick = {
                                addMenuOpen = false
                                filePicker.launch(arrayOf("image/*"))
                            },
                        )
                        DropdownMenuItem(
                            text = { MenuText("Whole folder", "Imports every photo in a folder, e.g. DCIM/Camera") },
                            onClick = {
                                addMenuOpen = false
                                folderPicker.launch(null)
                            },
                        )
                    }
                }
                Button(
                    onClick = onPlay,
                    enabled = photos.isNotEmpty() && importProgress == null && !selecting,
                    modifier = Modifier.weight(1f).height(56.dp),
                ) {
                    Icon(AppIcons.Play, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Play")
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (importProgress != null) {
                val (done, total) = importProgress
                Text(
                    if (total == 0) "Finding photos…" else "Importing $done of $total…",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                if (total == 0) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                } else {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / total },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    )
                }
            }
            if (photos.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "No photos yet.\nTap \"Add photos\" to pick some from your gallery.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(photos, key = { it.name }) { file ->
                        val isSelected = file.name in selected
                        Box(
                            Modifier
                                .aspectRatio(1f)
                                .combinedClickable(
                                    onClick = {
                                        if (selecting) selected = if (isSelected) selected - file.name else selected + file.name
                                    },
                                    onLongClick = {
                                        selecting = true
                                        selected = selected + file.name
                                    },
                                ),
                        ) {
                            AsyncImage(
                                model = file,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surfaceContainer)
                                    .alpha(if (selecting && !isSelected) 0.6f else 1f),
                            )
                            if (selecting) {
                                Icon(
                                    AppIcons.CheckCircle,
                                    contentDescription = if (isSelected) "Selected" else null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear all photos?") },
            text = { Text("This removes all ${photos.size} photos from BeatShow. The originals in your gallery are not touched.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearAll()
                }) { Text("Clear all") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MenuText(title: String, subtitle: String) {
    Column {
        Text(title)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
