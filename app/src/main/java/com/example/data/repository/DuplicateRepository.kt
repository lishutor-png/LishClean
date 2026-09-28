package com.example.data.repository

import android.content.Context
import com.example.data.db.AppDatabase
import com.example.data.db.ScanHistoryDao
import com.example.data.db.ScanHistoryEntity
import com.example.model.DuplicateItem
import com.example.model.ScanPerformanceStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class DuplicateRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val scanHistoryDao: ScanHistoryDao = db.scanHistoryDao()

    val scanHistory: Flow<List<ScanHistoryEntity>> = scanHistoryDao.getAllHistory()

    suspend fun saveScanHistory(stats: ScanPerformanceStats, modeName: String, targetFolders: String) {
        withContext(Dispatchers.IO) {
            scanHistoryDao.insertHistory(
                ScanHistoryEntity(
                    scanMode = modeName,
                    targetFolders = targetFolders,
                    totalFilesScanned = stats.totalFilesDiscovered,
                    duplicatesFoundCount = stats.duplicateFilesCount,
                    wastedBytes = stats.totalWastedBytes,
                    durationMs = stats.elapsedDurationMs,
                    scanSpeedFilesPerSec = stats.filesPerSecond
                )
            )
        }
    }

    suspend fun clearScanHistory() = withContext(Dispatchers.IO) {
        scanHistoryDao.clearAllHistory()
    }

    /**
     * Permanently delete duplicate files directly from storage (no heavy backup overhead).
     */
    suspend fun deleteFilesDirectly(items: List<DuplicateItem>): DeletionResult = withContext(Dispatchers.IO) {
        var successCount = 0
        var totalBytesFreed = 0L
        val failed = mutableListOf<String>()

        for (item in items) {
            try {
                if (item.file.exists()) {
                    val size = item.sizeBytes
                    if (item.file.delete()) {
                        successCount++
                        totalBytesFreed += size
                    } else {
                        failed.add(item.name)
                    }
                }
            } catch (e: Exception) {
                failed.add("${item.name}: ${e.localizedMessage}")
            }
        }

        DeletionResult(successCount, totalBytesFreed, failed)
    }
}

data class DeletionResult(
    val successCount: Int,
    val totalBytesFreed: Long,
    val failedFiles: List<String>
)
