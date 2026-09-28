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

            val defaultTargets = mutableListOf<String>()
            val primary = Environment.getExternalStorageDirectory()
            if (primary != null && primary.exists()) {
                defaultTargets.add(primary.absolutePath)
            } else if (volumes.isNotEmpty()) {
                defaultTargets.add(volumes.first().path)
            }
            _targetFolders.value = defaultTargets
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setResultsCategoryFilter(category: FileCategory) {
        _resultsCategoryFilter.value = category
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

    fun startFolderComparison() {
        val fA = _compareFolderA.value ?: return
        val fB = _compareFolderB.value ?: return

        scanJob?.cancel()
        _isComparing.value = true
        _duplicateGroups.value = emptyList()

        scanJob = viewModelScope.launch {
            DuplicateScannerEngine.compareTwoFolders(
                folderA = fA,
                folderB = fB,
                scanMode = _scanMode.value,
                includeHiddenFiles = _includeHiddenFiles.value
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
                        repository.saveScanHistory(event.stats, "Perbandingan Folder", "$fA vs $fB")
                    }
                }
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

        scanJob = viewModelScope.launch {
            DuplicateScannerEngine.scanFolders(
                targetFolders = targets,
                scanMode = _scanMode.value,
                includeHiddenFiles = _includeHiddenFiles.value,
                categoryFilter = _selectedPreScanCategory.value
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
                            targets.joinToString(", ") { File(it).name }
                        )
                    }
                }
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
            _userMessage.value = "Berhasil membuat file sampel di folder internal!"
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
