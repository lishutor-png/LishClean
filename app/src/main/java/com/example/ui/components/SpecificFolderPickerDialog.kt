package com.example.ui.components

import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.model.StorageVolumeInfo
import com.example.scanner.StorageDetector
import java.io.File

@Composable
fun SpecificFolderPickerDialog(
    title: String,
    initialPath: String?,
    storageVolumes: List<StorageVolumeInfo>,
    onFolderSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // Default to internal storage root if initialPath is null or invalid
    val defaultRoot = remember(storageVolumes) {
        val internal = storageVolumes.find { !it.isRemovable }?.path
        internal ?: Environment.getExternalStorageDirectory().absolutePath
    }

    var currentPath by remember {
        val start = initialPath?.takeIf { File(it).exists() && File(it).isDirectory } ?: defaultRoot
        mutableStateOf(start)
    }

    var manualInputMode by remember { mutableStateOf(false) }
    var manualPathText by remember { mutableStateOf(currentPath) }

    // SAF System Folder Launcher
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            val resolved = StorageDetector.uriToPath(context, uri)
            if (resolved != null && File(resolved).exists()) {
                currentPath = resolved
                manualPathText = resolved
            }
        }
    }

    val currentDir = remember(currentPath) { File(currentPath) }
    val isValidDir = remember(currentDir) { currentDir.exists() && currentDir.isDirectory }

    // List of subdirectories in current directory
    val subfolders = remember(currentPath) {
        if (!isValidDir) emptyList()
        else {
            try {
                currentDir.listFiles { file -> file.isDirectory && !file.name.startsWith(".git") }
                    ?.sortedBy { it.name.lowercase() }
                    ?.toList() ?: emptyList()
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // Common shortcuts
    val commonShortcuts = remember(context) { StorageDetector.getCommonDirectories(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .heightIn(max = 680.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Tutup")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Storage Root Switcher (Internal vs SD Card)
                if (storageVolumes.size > 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(storageVolumes) { vol ->
                            val isCurrentVol = currentPath.startsWith(vol.path)
                            FilterChip(
                                selected = isCurrentVol,
                                onClick = {
                                    currentPath = vol.path
                                    manualPathText = vol.path
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (vol.isRemovable) Icons.Default.SdCard else Icons.Default.Smartphone,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = { Text(if (vol.isRemovable) "Kartu SD" else "Internal", fontSize = 12.sp) }
                            )
                        }
                    }
                }

                // Quick Shortcuts Row
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(commonShortcuts) { (name, path) ->
                        val isSelected = currentPath == path
                        AssistChip(
                            onClick = {
                                if (File(path).exists()) {
                                    currentPath = path
                                    manualPathText = path
                                }
                            },
                            label = { Text(name, fontSize = 11.sp) },
                            leadingIcon = {
                                Icon(imageVector = Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }

                // Breadcrumb / Current Path Display with UP button
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Up Button
                        val parentFile = currentDir.parentFile
                        IconButton(
                            onClick = {
                                if (parentFile != null && parentFile.exists()) {
                                    currentPath = parentFile.absolutePath
                                    manualPathText = parentFile.absolutePath
                                }
                            },
                            enabled = parentFile != null && parentFile.exists() && parentFile.absolutePath != "/"
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Naik Satu Tingkat",
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Path text
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentDir.name.ifEmpty { "Root" },
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }

                        // Toggle manual edit mode
                        IconButton(onClick = { manualInputMode = !manualInputMode }) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit Path Manual",
                                tint = if (manualInputMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Manual Path Input Field (Expandable)
                if (manualInputMode) {
                    OutlinedTextField(
                        value = manualPathText,
                        onValueChange = {
                            manualPathText = it
                            val testF = File(it.trim())
                            if (testF.exists() && testF.isDirectory) {
                                currentPath = testF.absolutePath
                            }
                        },
                        label = { Text("Ketik atau tempel path folder") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_folder_path_input"),
                        singleLine = true,
                        supportingText = {
                            val f = File(manualPathText.trim())
                            if (f.exists() && f.isDirectory) {
                                Text("✓ Folder valid ditemukan", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp)
                            } else {
                                Text("✗ Folder tidak ditemukan", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                            }
                        }
                    )
                }

                // Subdirectories List
                Text(
                    text = "Subfolder di dalam folder ini (${subfolders.size}):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    if (subfolders.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(180.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Tidak ada subfolder lagi di sini.\nAnda dapat langsung memilih folder ini.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.padding(4.dp)) {
                            items(subfolders) { folder ->
                                val childCount = remember(folder) {
                                    try { folder.listFiles()?.size ?: 0 } catch (_: Exception) { 0 }
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            currentPath = folder.absolutePath
                                            manualPathText = folder.absolutePath
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folder.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "$childCount item",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }

                // SAF File Picker Button
                OutlinedButton(
                    onClick = { safLauncher.launch(null) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pilih Lewat File Manager Sistem (SAF)", fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isValidDir) {
                        onFolderSelected(currentPath)
                        onDismiss()
                    }
                },
                enabled = isValidDir,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_choose_folder_button")
            ) {
                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Gunakan Folder Ini")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Batal")
            }
        }
    )
}
