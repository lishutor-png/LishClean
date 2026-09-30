package com.example.ui

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.ScanHistoryEntity
import com.example.data.repository.DeletionResult
import com.example.data.repository.DuplicateRepository
import com.example.model.DuplicateGroup
import com.example.model.DuplicateItem
import com.example.model.FileCategory
import com.example.model.ScanMode
import com.example.model.ScanPerformanceStats
import com.example.model.ScanStatus
import com.example.model.StorageVolumeInfo
import com.example.scanner.DuplicateScannerEngine
import com.example.scanner.SampleDataGenerator
import com.example.scanner.ScanProgressEvent
import com.example.scanner.StorageDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class ResultsSortOption(val title: String) {
    SIZE_DESC("Ukuran Terbesar"),
    COUNT_DESC("Salinan Terbanyak"),
    NAME_ASC("Nama File (A-Z)"),
    DATE_DESC("Paling Baru")
}

enum class StorageLocationFilter(val title: String) {
    ALL("Semua Penyimpanan"),
    INTERNAL("Memori Internal"),
    EXTERNAL("Memori Eksternal (SD/USB)")
}

class DupliScanViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DuplicateRepository(application)

    // Storage and Folder setup
    private val _storageVolumes = MutableStateFlow<List<StorageVolumeInfo>>(emptyList())
    val storageVolumes: StateFlow<List<StorageVolumeInfo>> = _storageVolumes.asStateFlow()

    private val _targetFolders = MutableStateFlow<List<String>>(emptyList())
    val targetFolders: StateFlow<List<String>> = _targetFolders.asStateFlow()

    // Pre-Scan settings
    private val _scanMode = MutableStateFlow(ScanMode.HASH_SHA256)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _includeHiddenFiles = MutableStateFlow(true)
    val includeHiddenFiles: StateFlow<Boolean> = _includeHiddenFiles.asStateFlow()

    private val _selectedPreScanCategory = MutableStateFlow(FileCategory.ALL)
    val selectedPreScanCategory: StateFlow<FileCategory> = _selectedPreScanCategory.asStateFlow()

    // Post-Scan Interactive Filter & Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _resultsCategoryFilter = MutableStateFlow(FileCategory.ALL)
    val resultsCategoryFilter: StateFlow<FileCategory> = _resultsCategoryFilter.asStateFlow()

    private val _storageLocationFilter = MutableStateFlow(StorageLocationFilter.ALL)
    val storageLocationFilter: StateFlow<StorageLocationFilter> = _storageLocationFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(ResultsSortOption.SIZE_DESC)
    val sortOption: StateFlow<ResultsSortOption> = _sortOption.asStateFlow()

    // Scanner state
    private val _scanStatus = MutableStateFlow<ScanStatus>(ScanStatus.Idle)
    val scanStatus: StateFlow<ScanStatus> = _scanStatus.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<DuplicateGroup>> = _duplicateGroups.asStateFlow()

    private val _lastStats = MutableStateFlow<ScanPerformanceStats?>(null)
    val lastStats: StateFlow<ScanPerformanceStats?> = _lastStats.asStateFlow()

    // Preview
    private val _previewItem = MutableStateFlow<Pair<DuplicateGroup, DuplicateItem>?>(null)
    val previewItem: StateFlow<Pair<DuplicateGroup, DuplicateItem>?> = _previewItem.asStateFlow()

    // Folder comparison
    private val _compareFolderA = MutableStateFlow<String?>(null)
    val compareFolderA: StateFlow<String?> = _compareFolderA.asStateFlow()

    private val _compareFolderB = MutableStateFlow<String?>(null)
    val compareFolderB: StateFlow<String?> = _compareFolderB.asStateFlow()

    private val _compareCategory = MutableStateFlow(FileCategory.ALL)
    val compareCategory: StateFlow<FileCategory> = _compareCategory.asStateFlow()

    private val _compareIncludeSubfolders = MutableStateFlow(true)
    val compareIncludeSubfolders: StateFlow<Boolean> = _compareIncludeSubfolders.asStateFlow()

    private val _isComparing = MutableStateFlow(false)
    val isComparing: StateFlow<Boolean> = _isComparing.asStateFlow()

    // Notification / Snackbars
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // DB Observers: History
    val scanHistory: StateFlow<List<ScanHistoryEntity>> = repository.scanHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var scanJob: Job? = null

    init {
        refreshStorageVolumes()
    }

    fun refreshStorageVolumes() {
        viewModelScope.launch {
            val volumes = StorageDetector.detectStorageVolumes(getApplication())
            _storageVolumes.value = volumes

            // By default, select all detected storage volumes (both internal and external)
            // so that user can scan everything with one tap!
            val allPaths = volumes.map { it.path }
            if (allPaths.isNotEmpty()) {
                _targetFolders.value = allPaths
            } else {
                val primary = Environment.getExternalStorageDirectory()
                if (primary != null && primary.exists()) {
                    _targetFolders.value = listOf(primary.absolutePath)
                }
            }
        }
    }

    fun selectAllStorageVolumes() {
        val allPaths = _storageVolumes.value.map { it.path }
        _targetFolders.value = allPaths
        _userMessage.value = "Semua lokasi penyimpanan (Internal & Eksternal) dipilih untuk dipindai."
    }

    fun selectInternalOnly() {
        val internalPaths = _storageVolumes.value.filter { !it.isRemovable }.map { it.path }
        if (internalPaths.isNotEmpty()) {
            _targetFolders.value = internalPaths
            _userMessage.value = "Hanya memori internal yang dipilih."
        }
    }

    fun selectExternalOnly() {
        val externalPaths = _storageVolumes.value.filter { it.isRemovable }.map { it.path }
        if (externalPaths.isNotEmpty()) {
            _targetFolders.value = externalPaths
            _userMessage.value = "Hanya memori eksternal (SD/USB) yang dipilih."
        } else {
            _userMessage.value = "Tidak ada memori eksternal terdeteksi. Gunakan tombol 'Uji SD Card' untuk simulasi."
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setResultsCategoryFilter(category: FileCategory) {
        _resultsCategoryFilter.value = category
    }

    fun setStorageLocationFilter(filter: StorageLocationFilter) {
        _storageLocationFilter.value = filter
    }

    fun setSortOption(sort: ResultsSortOption) {
        _sortOption.value = sort
    }

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
    }

    fun toggleIncludeHiddenFiles() {
        _includeHiddenFiles.update { !it }
    }

    fun setPreScanCategory(category: FileCategory) {
        _selectedPreScanCategory.value = category
    }

    fun toggleFolderTarget(folderPath: String) {
        _targetFolders.update { current ->
            if (current.contains(folderPath)) {
                if (current.size > 1) current - folderPath else current
            } else {
                current + folderPath
            }
        }
    }

    fun setCompareFolders(folderA: String?, folderB: String?) {
        _compareFolderA.value = folderA
        _compareFolderB.value = folderB
    }

    fun swapCompareFolders() {
        val temp = _compareFolderA.value
        _compareFolderA.value = _compareFolderB.value
        _compareFolderB.value = temp
    }

    fun setCompareCategory(category: FileCategory) {
        _compareCategory.value = category
    }

    fun setCompareIncludeSubfolders(include: Boolean) {
        _compareIncludeSubfolders.value = include
    }

    fun cancelFolderComparison() {
        scanJob?.cancel()
        _isComparing.value = false
        _scanStatus.value = ScanStatus.Idle
        _userMessage.value = "Perbandingan folder dibatalkan."
    }

    fun startFolderComparison() {
        val fA = _compareFolderA.value
        val fB = _compareFolderB.value

        if (fA == null || fB == null) {
            _userMessage.value = "Silakan tentukan Folder A dan Folder B terlebih dahulu!"
            return
        }

        if (fA == fB) {
            _userMessage.value = "Folder A dan Folder B tidak boleh sama! Pilih dua folder yang berbeda."
            return
        }

        scanJob?.cancel()
        _isComparing.value = true
        _duplicateGroups.value = emptyList()

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                DuplicateScannerEngine.compareTwoFolders(
                    folderA = fA,
                    folderB = fB,
                    scanMode = _scanMode.value,
                    includeHiddenFiles = _includeHiddenFiles.value,
                    categoryFilter = _compareCategory.value,
                    recursive = _compareIncludeSubfolders.value
                ).collect { event ->
                    when (event) {
                        is ScanProgressEvent.Status -> {
                            _scanStatus.value = event.status
                        }
                        is ScanProgressEvent.Completed -> {
                            _duplicateGroups.value = event.groups
                            _lastStats.value = event.stats
                            _scanStatus.value = ScanStatus.Completed(event.stats)
                            _isComparing.value = false
                            repository.saveScanHistory(
                                event.stats,
                                "Banding: ${File(fA).name} vs ${File(fB).name}",
                                "$fA vs $fB"
                            )
                        }
                        is ScanProgressEvent.Error -> {
                            _scanStatus.value = ScanStatus.Error(event.message)
                            _userMessage.value = event.message
                            _isComparing.value = false
                        }
                    }
                }
            } catch (e: Exception) {
                _scanStatus.value = ScanStatus.Error(e.message ?: "Terjadi kesalahan saat membandingkan folder.")
                _userMessage.value = "Gagal membandingkan: ${e.message}"
                _isComparing.value = false
            } finally {
                _isComparing.value = false
            }
        }
    }

    fun startScan() {
        val targets = _targetFolders.value
        if (targets.isEmpty()) {
            _userMessage.value = "Pilih setidaknya satu lokasi penyimpanan!"
            return
        }

        scanJob?.cancel()
        _duplicateGroups.value = emptyList()
        _searchQuery.value = ""
        _resultsCategoryFilter.value = _selectedPreScanCategory.value
        _storageLocationFilter.value = StorageLocationFilter.ALL

        val externalRoots = _storageVolumes.value.filter { it.isRemovable }.map { it.path }

        scanJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                DuplicateScannerEngine.scanFolders(
                    targetFolders = targets,
                    scanMode = _scanMode.value,
                    includeHiddenFiles = _includeHiddenFiles.value,
                    categoryFilter = _selectedPreScanCategory.value,
                    externalStoragePaths = externalRoots
                ).collect { event ->
                    when (event) {
                        is ScanProgressEvent.Status -> {
                            _scanStatus.value = event.status
                        }
                        is ScanProgressEvent.Completed -> {
                            _duplicateGroups.value = event.groups
                            _lastStats.value = event.stats
                            _scanStatus.value = ScanStatus.Completed(event.stats)
                            repository.saveScanHistory(
                                event.stats,
                                _scanMode.value.title,
                                targets.joinToString(", ") { File(it).name.ifEmpty { it } }
                            )
                        }
                        is ScanProgressEvent.Error -> {
                            _scanStatus.value = ScanStatus.Error(event.message)
                            _userMessage.value = "Pemindaian terhenti: ${event.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                _scanStatus.value = ScanStatus.Error(e.message ?: "Terjadi kesalahan sistem saat memindai.")
                _userMessage.value = "Pemindaian terhenti: ${e.message}"
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _scanStatus.value = ScanStatus.Idle
        _userMessage.value = "Pemindaian dibatalkan."
    }

    fun generateSampleDuplicates() {
        viewModelScope.launch {
            _scanStatus.value = ScanStatus.DiscoveringFiles("Membuat file sampel pengujian...", 0)
            val path = SampleDataGenerator.generateSampleDuplicates(getApplication())
            _targetFolders.value = listOf(path)
            _userMessage.value = "Berhasil membuat file sampel di memori internal!"
            startScan()
        }
    }

    /**
     * Generates simulated external SD card storage with duplicate files
     * and sets both internal and external storage to be scanned.
     */
    fun generateSimulatedExternalStorage() {
        viewModelScope.launch {
            _scanStatus.value = ScanStatus.DiscoveringFiles("Membuat simulasi memori eksternal (SD Card)...", 0)
            val internalPath = SampleDataGenerator.generateSampleDuplicates(getApplication())
            val externalPath = StorageDetector.createSimulatedExternalStorage(getApplication())
            
            // Refresh detected storage volumes to pick up simulated SD Card
            val volumes = StorageDetector.detectStorageVolumes(getApplication())
            _storageVolumes.value = volumes
            
            // Scan both internal and external sample directories
            _targetFolders.value = listOf(internalPath, externalPath)
            _userMessage.value = "Simulasi Kartu SD siap! Memindai memori internal & eksternal..."
            startScan()
        }
    }

    fun toggleItemSelection(groupKey: String, itemPath: String) {
        _duplicateGroups.update { groups ->
            groups.map { group ->
                if (group.groupKey == groupKey) {
                    val updatedItems = group.items.map { item ->
                        if (item.path == itemPath) {
                            item.copy(isSelected = !item.isSelected)
                        } else item
                    }
                    group.copy(items = updatedItems)
                } else group
            }
        }
    }

    fun selectSmartKeepOldest() {
        _duplicateGroups.update { groups ->
            groups.map { group ->
                val sorted = group.items.sortedBy { it.lastModified }
                val updated = sorted.mapIndexed { index, item ->
                    item.copy(isSelected = index > 0)
                }
                group.copy(items = updated)
            }
        }
        _userMessage.value = "Dipilih: Semua salinan kecuali file tertua."
    }

    fun selectSmartKeepNewest() {
        _duplicateGroups.update { groups ->
            groups.map { group ->
                val sorted = group.items.sortedByDescending { it.lastModified }
                val updated = sorted.mapIndexed { index, item ->
                    item.copy(isSelected = index > 0)
                }
                group.copy(items = updated)
            }
        }
        _userMessage.value = "Dipilih: Semua salinan kecuali file terbaru."
    }

    /**
     * Keeps original on Internal Storage, marks duplicate copy on External Storage for deletion.
     */
    fun selectKeepInternalDeleteExternal() {
        _duplicateGroups.update { groups ->
            groups.map { group ->
                val hasInternal = group.items.any { !it.isExternalStorage }
                if (hasInternal) {
                    val updated = group.items.map { item ->
                        item.copy(isSelected = item.isExternalStorage)
                    }
                    group.copy(items = updated)
                } else {
                    // Fallback to oldest
                    val sorted = group.items.sortedBy { it.lastModified }
                    val updated = sorted.mapIndexed { idx, item -> item.copy(isSelected = idx > 0) }
                    group.copy(items = updated)
                }
            }
        }
        _userMessage.value = "Dipilih: Pertahankan di Internal, hapus salinan di Eksternal."
    }

    /**
     * Keeps original on External Storage, marks duplicate copy on Internal Storage for deletion.
     */
    fun selectKeepExternalDeleteInternal() {
        _duplicateGroups.update { groups ->
            groups.map { group ->
                val hasExternal = group.items.any { item -> item.isExternalStorage }
                if (hasExternal) {
                    val updated = group.items.map { item ->
                        item.copy(isSelected = !item.isExternalStorage)
                    }
                    group.copy(items = updated)
                } else {
                    val sorted = group.items.sortedBy { it.lastModified }
                    val updated = sorted.mapIndexed { idx, item -> item.copy(isSelected = idx > 0) }
                    group.copy(items = updated)
                }
            }
        }
        _userMessage.value = "Dipilih: Pertahankan di Eksternal (SD), hapus salinan di Internal."
    }

    fun selectAllDuplicates() {
        _duplicateGroups.update { groups ->
            groups.map { group ->
                val updated = group.items.mapIndexed { index, item ->
                    item.copy(isSelected = !item.isOriginal || index > 0)
                }
                group.copy(items = updated)
            }
        }
    }

    fun deselectAll() {
        _duplicateGroups.update { groups ->
            groups.map { group ->
                val updated = group.items.map { it.copy(isSelected = false) }
                group.copy(items = updated)
            }
        }
    }

    fun setPreview(group: DuplicateGroup, item: DuplicateItem) {
        _previewItem.value = Pair(group, item)
    }

    fun clearPreview() {
        _previewItem.value = null
    }

    /**
     * Directly delete selected duplicates (zero memory overhead from backups).
     */
    fun deleteSelectedDuplicates(onComplete: (DeletionResult) -> Unit) {
        viewModelScope.launch {
            val selected = _duplicateGroups.value.flatMap { it.items }.filter { it.isSelected }
            if (selected.isEmpty()) {
                _userMessage.value = "Tidak ada file duplikat yang dipilih!"
                return@launch
            }

            val result = repository.deleteFilesDirectly(selected)
            val deletedPaths = selected.map { it.path }.toSet()

            _duplicateGroups.update { groups ->
                groups.mapNotNull { group ->
                    val remaining = group.items.filterNot { deletedPaths.contains(it.path) }
                    if (remaining.size >= 2) {
                        group.copy(items = remaining)
                    } else null
                }
            }

            _userMessage.value = "Berhasil menghapus ${result.successCount} file duplikat (${DuplicateItem.formatFileSize(result.totalBytesFreed)} ruang dibebaskan)!"
            onComplete(result)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearScanHistory()
            _userMessage.value = "Riwayat pemindaian telah dibersihkan."
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }
}
