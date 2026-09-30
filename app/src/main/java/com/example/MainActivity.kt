package com.example

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ScanStatus
import com.example.ui.DupliScanViewModel
import com.example.ui.screens.CompareFoldersScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PerformanceBenchmarkScreen
import com.example.ui.screens.ResultsScreen
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                LishCleanApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LishCleanApp(vm: DupliScanViewModel = viewModel()) {
    val context = LocalContext.current
    var currentNavIndex by rememberSaveable { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // State from ViewModel
    val storageVolumes by vm.storageVolumes.collectAsStateWithLifecycle()
    val targetFolders by vm.targetFolders.collectAsStateWithLifecycle()
    val scanMode by vm.scanMode.collectAsStateWithLifecycle()
    val includeHidden by vm.includeHiddenFiles.collectAsStateWithLifecycle()
    val preScanCategory by vm.selectedPreScanCategory.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val resultsCategoryFilter by vm.resultsCategoryFilter.collectAsStateWithLifecycle()
    val storageLocationFilter by vm.storageLocationFilter.collectAsStateWithLifecycle()
    val sortOption by vm.sortOption.collectAsStateWithLifecycle()
    val scanStatus by vm.scanStatus.collectAsStateWithLifecycle()
    val duplicateGroups by vm.duplicateGroups.collectAsStateWithLifecycle()
    val lastStats by vm.lastStats.collectAsStateWithLifecycle()
    val previewItem by vm.previewItem.collectAsStateWithLifecycle()
    val compareFolderA by vm.compareFolderA.collectAsStateWithLifecycle()
    val compareFolderB by vm.compareFolderB.collectAsStateWithLifecycle()
    val compareCategory by vm.compareCategory.collectAsStateWithLifecycle()
    val compareIncludeSubfolders by vm.compareIncludeSubfolders.collectAsStateWithLifecycle()
    val isComparing by vm.isComparing.collectAsStateWithLifecycle()
    val scanHistory by vm.scanHistory.collectAsStateWithLifecycle()
    val userMessage by vm.userMessage.collectAsStateWithLifecycle()

    // Storage permission state check
    var hasStoragePermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Environment.isExternalStorageManager()
            } else {
                true
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasStoragePermission = Environment.isExternalStorageManager()
        }
        vm.refreshStorageVolumes()
    }

    // Auto navigate to Results screen when scan completes with results
    LaunchedEffect(scanStatus) {
        if (scanStatus is ScanStatus.Completed) {
            currentNavIndex = 1
        }
    }

    // Display user messages in snackbar
    LaunchedEffect(userMessage) {
        userMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearUserMessage()
        }
    }

    // Back handling: Return to Home if on sub-screen
    BackHandler(enabled = currentNavIndex != 0) {
        currentNavIndex = 0
    }

    val totalDuplicateFiles = remember(duplicateGroups) {
        duplicateGroups.sumOf { it.copiesCount }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "LishClean",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "FLAT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontSize = 10.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                NavigationBarItem(
                    selected = currentNavIndex == 0,
                    onClick = { currentNavIndex = 0 },
                    icon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Pindai") },
                    label = { Text("Pindai") },
                    modifier = Modifier.testTag("nav_home")
                )

                NavigationBarItem(
                    selected = currentNavIndex == 1,
                    onClick = { currentNavIndex = 1 },
                    icon = {
                        if (totalDuplicateFiles > 0) {
                            BadgedBox(
                                badge = {
                                    Badge(containerColor = MaterialTheme.colorScheme.error) {
                                        Text("$totalDuplicateFiles")
                                    }
                                }
                            ) {
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Hasil Duplikat")
                            }
                        } else {
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Hasil Duplikat")
                        }
                    },
                    label = { Text("Hasil") },
                    modifier = Modifier.testTag("nav_results")
                )

                NavigationBarItem(
                    selected = currentNavIndex == 2,
                    onClick = { currentNavIndex = 2 },
                    icon = { Icon(imageVector = Icons.Default.CompareArrows, contentDescription = "Bandingkan") },
                    label = { Text("Bandingkan") },
                    modifier = Modifier.testTag("nav_compare")
                )

                NavigationBarItem(
                    selected = currentNavIndex == 3,
                    onClick = { currentNavIndex = 3 },
                    icon = { Icon(imageVector = Icons.Default.Speed, contentDescription = "Performa") },
                    label = { Text("Performa") },
                    modifier = Modifier.testTag("nav_performance")
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Storage Permission Warning Banner (if needed)
            if (!hasStoragePermission && currentNavIndex == 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Akses Penyimpanan Lengkap",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Diperlukan untuk memindai file duplikat di seluruh penyimpanan perangkat.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Button(
                            onClick = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    try {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                        context.startActivity(intent)
                                    }
                                } else {
                                    permissionLauncher.launch(
                                        arrayOf(
                                            android.Manifest.permission.READ_EXTERNAL_STORAGE,
                                            android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                    )
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("grant_permission_button")
                        ) {
                            Text("Izinkan")
                        }
                    }
                }
            }

            // Screen Content Switcher (Clean 4 Tabs)
            when (currentNavIndex) {
                0 -> HomeScreen(
                    storageVolumes = storageVolumes,
                    targetFolders = targetFolders,
                    scanMode = scanMode,
                    includeHiddenFiles = includeHidden,
                    selectedCategory = preScanCategory,
                    scanStatus = scanStatus,
                    onSetScanMode = { vm.setScanMode(it) },
                    onToggleHiddenFiles = { vm.toggleIncludeHiddenFiles() },
                    onSetCategory = { vm.setPreScanCategory(it) },
                    onToggleFolderTarget = { vm.toggleFolderTarget(it) },
                    onSelectAllStorage = { vm.selectAllStorageVolumes() },
                    onSelectInternalOnly = { vm.selectInternalOnly() },
                    onSelectExternalOnly = { vm.selectExternalOnly() },
                    onRefreshStorage = { vm.refreshStorageVolumes() },
                    onStartScan = { vm.startScan() },
                    onCancelScan = { vm.cancelScan() },
                    onGenerateSampleData = { vm.generateSampleDuplicates() },
                    onGenerateSimulatedExternal = { vm.generateSimulatedExternalStorage() },
                    onNavigateToResults = { currentNavIndex = 1 }
                )
                1 -> ResultsScreen(
                    groups = duplicateGroups,
                    searchQuery = searchQuery,
                    selectedCategory = resultsCategoryFilter,
                    storageLocationFilter = storageLocationFilter,
                    sortOption = sortOption,
                    previewItem = previewItem,
                    onSearchQueryChange = { vm.setSearchQuery(it) },
                    onCategoryChange = { vm.setResultsCategoryFilter(it) },
                    onStorageLocationFilterChange = { vm.setStorageLocationFilter(it) },
                    onSortOptionChange = { vm.setSortOption(it) },
                    onToggleSelect = { groupKey, path -> vm.toggleItemSelection(groupKey, path) },
                    onSelectSmartKeepOldest = { vm.selectSmartKeepOldest() },
                    onSelectSmartKeepNewest = { vm.selectSmartKeepNewest() },
                    onSelectKeepInternalDeleteExternal = { vm.selectKeepInternalDeleteExternal() },
                    onSelectKeepExternalDeleteInternal = { vm.selectKeepExternalDeleteInternal() },
                    onSelectAll = { vm.selectAllDuplicates() },
                    onDeselectAll = { vm.deselectAll() },
                    onSetPreview = { group, item -> vm.setPreview(group, item) },
                    onClearPreview = { vm.clearPreview() },
                    onDeleteSelected = {
                        vm.deleteSelectedDuplicates { }
                    },
                    onNavigateBack = { currentNavIndex = 0 }
                )
                2 -> CompareFoldersScreen(
                    folderA = compareFolderA,
                    folderB = compareFolderB,
                    storageVolumes = storageVolumes,
                    isComparing = isComparing,
                    scanStatus = scanStatus,
                    duplicateGroups = duplicateGroups,
                    compareCategory = compareCategory,
                    compareIncludeSubfolders = compareIncludeSubfolders,
                    onSelectFolders = { a, b -> vm.setCompareFolders(a, b) },
                    onSwapFolders = { vm.swapCompareFolders() },
                    onSetCompareCategory = { vm.setCompareCategory(it) },
                    onSetCompareIncludeSubfolders = { vm.setCompareIncludeSubfolders(it) },
                    onStartCompare = { vm.startFolderComparison() },
                    onCancelCompare = { vm.cancelFolderComparison() },
                    onPreview = { group, item -> vm.setPreview(group, item) },
                    onNavigateToResults = { currentNavIndex = 1 }
                )
                3 -> PerformanceBenchmarkScreen(
                    lastStats = lastStats,
                    history = scanHistory,
                    onClearHistory = { vm.clearHistory() }
                )
            }
        }
    }
}
