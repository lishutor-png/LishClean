package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.model.DuplicateGroup
import com.example.model.DuplicateItem
import com.example.model.FileCategory
import com.example.ui.ResultsSortOption
import com.example.ui.StorageLocationFilter
import com.example.ui.components.FilePreviewDialog
import com.example.ui.components.SafeDeleteConfirmDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ResultsScreen(
    groups: List<DuplicateGroup>,
    searchQuery: String,
    selectedCategory: FileCategory,
    storageLocationFilter: StorageLocationFilter,
    sortOption: ResultsSortOption,
    previewItem: Pair<DuplicateGroup, DuplicateItem>?,
    onSearchQueryChange: (String) -> Unit,
    onCategoryChange: (FileCategory) -> Unit,
    onStorageLocationFilterChange: (StorageLocationFilter) -> Unit,
    onSortOptionChange: (ResultsSortOption) -> Unit,
    onToggleSelect: (groupKey: String, itemPath: String) -> Unit,
    onSelectSmartKeepOldest: () -> Unit,
    onSelectSmartKeepNewest: () -> Unit,
    onSelectKeepInternalDeleteExternal: () -> Unit,
    onSelectKeepExternalDeleteInternal: () -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onSetPreview: (DuplicateGroup, DuplicateItem) -> Unit,
    onClearPreview: () -> Unit,
    onDeleteSelected: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    // Category count and wasted bytes map
    val categoryStats = remember(groups) {
        val map = mutableMapOf<FileCategory, Pair<Int, Long>>()
        FileCategory.entries.forEach { cat ->
            val matchingGroups = if (cat == FileCategory.ALL) groups else groups.filter { it.category == cat }
            val count = matchingGroups.sumOf { it.copiesCount }
            val bytes = matchingGroups.sumOf { it.wastedBytes }
            map[cat] = Pair(count, bytes)
        }
        map
    }

    // Filter by Category, Storage Location, and Search Keyword
    val filteredAndSortedGroups = remember(groups, selectedCategory, storageLocationFilter, searchQuery, sortOption) {
        var result = groups

        // 1. Filter by category
        if (selectedCategory != FileCategory.ALL) {
            result = result.filter { it.category == selectedCategory }
        }

        // 2. Filter by storage location (Internal vs External)
        when (storageLocationFilter) {
            StorageLocationFilter.ALL -> {}
            StorageLocationFilter.INTERNAL -> {
                result = result.filter { group -> group.items.any { !it.isExternalStorage } }
            }
            StorageLocationFilter.EXTERNAL -> {
                result = result.filter { group -> group.items.any { it.isExternalStorage } }
            }
        }

        // 3. Filter by search query (file name, path, extension, or folder)
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase()
            result = result.filter { group ->
                group.items.any { item ->
                    item.name.lowercase().contains(q) ||
                    item.path.lowercase().contains(q) ||
                    item.file.extension.lowercase().contains(q) ||
                    item.folderName.lowercase().contains(q)
                }
            }
        }

        // 4. Sort
        when (sortOption) {
            ResultsSortOption.SIZE_DESC -> result.sortedByDescending { it.fileSize }
            ResultsSortOption.COUNT_DESC -> result.sortedByDescending { it.items.size }
            ResultsSortOption.NAME_ASC -> result.sortedBy { it.items.firstOrNull()?.name?.lowercase() ?: "" }
            ResultsSortOption.DATE_DESC -> result.sortedByDescending { it.items.maxOfOrNull { item -> item.lastModified } ?: 0L }
        }
    }

    val totalSelectedItems = remember(groups) {
        groups.sumOf { it.selectedCount }
    }

    val totalSelectedBytes = remember(groups) {
        groups.sumOf { it.selectedBytes }
    }

    val totalWastedBytes = remember(groups) {
        groups.sumOf { it.wastedBytes }
    }

    val totalDuplicateFiles = remember(groups) {
        groups.sumOf { it.copiesCount }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (groups.isEmpty()) {
            // Empty State
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(44.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Penyimpanan Bersih!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Tidak ditemukan file duplikat pada lokasi yang dipindai, atau pemindaian belum dijalankan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("back_to_scan_button")
                ) {
                    Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Jalankan Pemindaian Sekarang")
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Search Bar with Real-time Filtering
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_duplicates_input"),
                        placeholder = {
                            Text(
                                text = "Cari nama file, ekstensi, atau folder...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Cari",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(imageVector = Icons.Default.Close, contentDescription = "Hapus Pencarian")
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Sort Button & Dropdown
                    Box {
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .clickable { showSortMenu = true }
                                .padding(12.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Icon(
                                imageVector = Icons.Default.Sort,
                                contentDescription = "Urutkan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            ResultsSortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = option.title,
                                            fontWeight = if (sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                            color = if (sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    },
                                    onClick = {
                                        onSortOptionChange(option)
                                        showSortMenu = false
                                    }
                                )
                            }
                        }
                    }
                }

                // File Type Category Filter Chips with counts & size
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(FileCategory.entries) { category ->
                        val isSelected = selectedCategory == category
                        val stats = categoryStats[category] ?: Pair(0, 0L)
                        val copiesCount = stats.first
                        val wasted = stats.second

                        // Hide categories with 0 duplicates if not ALL
                        if (copiesCount > 0 || category == FileCategory.ALL) {
                            FilterChip(
                                selected = isSelected,
                                onClick = { onCategoryChange(category) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = getCategoryIcon(category),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(category.shortName)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "($copiesCount)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Storage Location Filter Row (All vs Internal vs External)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(StorageLocationFilter.entries) { loc ->
                        val isSelected = storageLocationFilter == loc
                        FilterChip(
                            selected = isSelected,
                            onClick = { onStorageLocationFilterChange(loc) },
                            leadingIcon = {
                                Icon(
                                    imageVector = when (loc) {
                                        StorageLocationFilter.ALL -> Icons.Default.Storage
                                        StorageLocationFilter.INTERNAL -> Icons.Default.Smartphone
                                        StorageLocationFilter.EXTERNAL -> Icons.Default.SdCard
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            label = { Text(loc.title, fontSize = 11.sp) }
                        )
                    }
                }

                // Header Summary Card & Smart Selection Toolbar
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("results_summary_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Menampilkan ${filteredAndSortedGroups.size} Grup Duplikat",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Potensi hemat total: ${DuplicateItem.formatFileSize(totalWastedBytes)} ruang",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = sortOption.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Smart Selection Toolbar
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                AssistChip(
                                    onClick = onSelectSmartKeepOldest,
                                    label = { Text("Simpan Tertua (Master)") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("smart_keep_oldest_chip")
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onSelectSmartKeepNewest,
                                    label = { Text("Simpan Terbaru") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("smart_keep_newest_chip")
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onSelectKeepInternalDeleteExternal,
                                    label = { Text("Simpan di Internal") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Smartphone,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("smart_keep_internal_chip")
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onSelectKeepExternalDeleteInternal,
                                    label = { Text("Simpan di Eksternal") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SdCard,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    modifier = Modifier.testTag("smart_keep_external_chip")
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onSelectAll,
                                    label = { Text("Pilih Semua") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.SelectAll,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                            item {
                                AssistChip(
                                    onClick = onDeselectAll,
                                    label = { Text("Batal") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // If search query produces no results
                if (filteredAndSortedGroups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Tidak ada file duplikat yang cocok",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Coba ubah kata kunci pencarian atau ganti filter kategori.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Groups List
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredAndSortedGroups, key = { it.groupKey }) { group ->
                            DuplicateGroupCard(
                                group = group,
                                onToggleSelect = { path -> onToggleSelect(group.groupKey, path) },
                                onPreview = { item -> onSetPreview(group, item) }
                            )
                        }
                    }
                }
            }

            // Bottom Sticky Action Bar: Direct Safe Delete (No backup overhead)
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .testTag("sticky_bottom_action_bar"),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$totalSelectedItems file terpilih",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Akan menghemat ${DuplicateItem.formatFileSize(totalSelectedBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (totalSelectedItems > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (totalSelectedItems > 0) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }

                    Button(
                        onClick = { showDeleteConfirmDialog = true },
                        enabled = totalSelectedItems > 0,
                        modifier = Modifier.testTag("clean_selected_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(imageVector = Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Hapus Terpilih")
                    }
                }
            }
        }

        // Preview Dialog Modal
        previewItem?.let { (group, item) ->
            FilePreviewDialog(
                group = group,
                item = item,
                onDismiss = onClearPreview,
                onToggleSelect = { onToggleSelect(group.groupKey, item.path) }
            )
        }

        // Safe Direct Delete Confirmation Dialog (Zero memory backup overhead)
        if (showDeleteConfirmDialog) {
            SafeDeleteConfirmDialog(
                selectedCount = totalSelectedItems,
                selectedBytes = totalSelectedBytes,
                onDismiss = { showDeleteConfirmDialog = false },
                onConfirmDelete = {
                    onDeleteSelected()
                }
            )
        }
    }
}

@Composable
fun DuplicateGroupCard(
    group: DuplicateGroup,
    onToggleSelect: (String) -> Unit,
    onPreview: (DuplicateItem) -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .testTag("group_card_${group.groupKey}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Group header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = getCategoryIcon(group.category),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "${group.items.size} Salinan File (${group.items.firstOrNull()?.formattedSize ?: ""})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Terbuang: ${group.formattedWastedBytes}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = group.category.shortName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(modifier = Modifier.height(6.dp))

            // File items
            group.items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (item.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            else Color.Transparent
                        )
                        .clickable { onToggleSelect(item.path) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Thumbnail or Icon
                    if (item.category == FileCategory.IMAGES && item.file.exists()) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(item.file)
                                .crossfade(true)
                                .build(),
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = getCategoryIcon(item.category),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (item.isOriginal) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Master",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            // Storage location badge
                            if (item.isExternalStorage) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFCCFBF1))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "SD Card",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF0F766E),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "Internal",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                        }

                        val storageTypeLabel = if (item.isExternalStorage) "Eksternal" else "Internal"
                        Text(
                            text = "$storageTypeLabel • ${item.folderName} • ${dateFormat.format(Date(item.lastModified))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Preview Button (Eye icon with 48dp minimum interactive component)
                    IconButton(
                        onClick = { onPreview(item) },
                        modifier = Modifier.testTag("preview_button_${item.name}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = "Pratinjau File",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Checkbox for selection
                    Checkbox(
                        checked = item.isSelected,
                        onCheckedChange = { onToggleSelect(item.path) },
                        modifier = Modifier.testTag("checkbox_${item.name}")
                    )
                }
            }
        }
    }
}
