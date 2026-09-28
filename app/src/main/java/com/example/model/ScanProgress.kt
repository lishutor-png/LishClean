package com.example.model

sealed interface ScanStatus {
    object Idle : ScanStatus
    data class DiscoveringFiles(val directory: String, val foundFilesCount: Int) : ScanStatus
    data class GroupingBySize(val totalFiles: Int) : ScanStatus
    data class ComputingHashes(
        val currentIndex: Int,
        val totalCandidates: Int,
        val currentFileName: String,
        val currentFileSize: Long,
        val speedFilesPerSec: Double
    ) : ScanStatus
    data class Finalizing(val duplicatesFound: Int) : ScanStatus
    data class Completed(val stats: ScanPerformanceStats) : ScanStatus
    data class Error(val message: String) : ScanStatus
}

data class ScanPerformanceStats(
    val totalFilesDiscovered: Int = 0,
    val candidateFilesTested: Int = 0,
    val duplicateGroupsFound: Int = 0,
    val duplicateFilesCount: Int = 0,
    val totalWastedBytes: Long = 0L,
    val elapsedDurationMs: Long = 0L,
    val filesPerSecond: Double = 0.0,
    val megabytesPerSecond: Double = 0.0,
    val algorithmUsed: String = "SHA-256",
    val timeFormatted: String = ""
) {
    val formattedWastedBytes: String
        get() = DuplicateItem.formatFileSize(totalWastedBytes)

    val formattedDuration: String
        get() = if (elapsedDurationMs < 1000) {
            "${elapsedDurationMs}ms"
        } else {
            String.format("%.2f dtk", elapsedDurationMs / 1000.0)
        }
}
