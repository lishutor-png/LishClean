package com.example.scanner

import com.example.model.DuplicateGroup
import com.example.model.DuplicateItem
import com.example.model.FileCategory
import com.example.model.ScanMode
import com.example.model.ScanPerformanceStats
import com.example.model.ScanStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

object DuplicateScannerEngine {

    fun scanFolders(
        targetFolders: List<String>,
        scanMode: ScanMode = ScanMode.HASH_SHA256,
        includeHiddenFiles: Boolean = true,
        categoryFilter: FileCategory = FileCategory.ALL,
        minFileSizeBytes: Long = 1L,
        externalStoragePaths: List<String> = emptyList()
    ): Flow<ScanProgressEvent> = flow {
        val startTime = System.currentTimeMillis()
        emit(ScanProgressEvent.Status(ScanStatus.DiscoveringFiles("Menyiapkan pemindaian...", 0)))

        val allCandidateFiles = mutableListOf<File>()
        var scannedDirsCount = 0

        // 1. Recursive file discovery
        for (folderPath in targetFolders) {
            val root = File(folderPath)
            if (!root.exists()) continue

            discoverFilesRecursively(
                dir = root,
                includeHidden = includeHiddenFiles,
                minSizeBytes = minFileSizeBytes,
                categoryFilter = categoryFilter,
                onProgress = { currentDir, count ->
                    scannedDirsCount++
                    if (scannedDirsCount % 10 == 0) {
                        emit(ScanProgressEvent.Status(ScanStatus.DiscoveringFiles(currentDir, allCandidateFiles.size + count)))
                    }
                },
                accumulator = allCandidateFiles
            )
        }

        val totalDiscovered = allCandidateFiles.size
        emit(ScanProgressEvent.Status(ScanStatus.GroupingBySize(totalDiscovered)))

        // 2. Tier 1: Group by exact file size
        // Files with unique byte sizes CANNOT be duplicates, so we skip them entirely!
        val sizeBuckets = mutableMapOf<Long, MutableList<File>>()
        for (file in allCandidateFiles) {
            val len = file.length()
            if (len >= minFileSizeBytes) {
                sizeBuckets.getOrPut(len) { mutableListOf() }.add(file)
            }
        }

        // Keep only size buckets with 2 or more files
        val collisionBuckets = sizeBuckets.filterValues { it.size >= 2 }
        val candidateFilesToHash = collisionBuckets.values.flatten()
        val totalCandidates = candidateFilesToHash.size

        if (totalCandidates == 0) {
            val duration = System.currentTimeMillis() - startTime
            val emptyStats = ScanPerformanceStats(
                totalFilesDiscovered = totalDiscovered,
                candidateFilesTested = 0,
                duplicateGroupsFound = 0,
                duplicateFilesCount = 0,
                totalWastedBytes = 0L,
                elapsedDurationMs = duration,
                filesPerSecond = if (duration > 0) (totalDiscovered * 1000.0 / duration) else 0.0,
                algorithmUsed = scanMode.title
            )
            emit(ScanProgressEvent.Completed(emptyList(), emptyStats))
            return@flow
        }

        // 3. Tier 2 & Tier 3: Hashing / Content Comparison
        var processedCount = 0
        var totalBytesHashed = 0L
        val hashGroups = mutableMapOf<String, MutableList<File>>()

        for ((size, filesInBucket) in collisionBuckets) {
            if (!coroutineContext.isActive) break

            // If mode is NAME_AND_SIZE, group directly by name + size
            if (scanMode == ScanMode.NAME_AND_SIZE) {
                for (file in filesInBucket) {
                    val key = "${file.name.lowercase()}_$size"
                    hashGroups.getOrPut(key) { mutableListOf() }.add(file)
                    processedCount++
                    totalBytesHashed += size
                }
                continue
            }

            // Quick chunk optimization for files > 64KB
            val subBuckets = if (filesInBucket.size > 2 && size > 64 * 1024L) {
                // Tier 2: Group by 4KB head + tail chunk
                val chunkMap = mutableMapOf<String, MutableList<File>>()
                for (file in filesInBucket) {
                    val chunkKey = computeChunkHash(file, size)
                    chunkMap.getOrPut(chunkKey) { mutableListOf() }.add(file)
                }
                chunkMap.filterValues { it.size >= 2 }
            } else {
                mapOf("all" to filesInBucket)
            }

            for ((_, candidateSubset) in subBuckets) {
                for (file in candidateSubset) {
                    if (!coroutineContext.isActive) break

                    val hashKey = when (scanMode) {
                        ScanMode.HASH_SHA256 -> computeFileHash(file, "SHA-256")
                        ScanMode.HASH_MD5 -> computeFileHash(file, "MD5")
                        ScanMode.CHUNK_SIZE -> computeChunkHash(file, size) + "_$size"
                        ScanMode.NAME_AND_SIZE -> "${file.name.lowercase()}_$size"
                    }

                    if (hashKey.isNotEmpty()) {
                        hashGroups.getOrPut(hashKey) { mutableListOf() }.add(file)
                    }

                    processedCount++
                    totalBytesHashed += size

                    val elapsedNow = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
                    val speed = (processedCount * 1000.0) / elapsedNow

                    emit(
                        ScanProgressEvent.Status(
                            ScanStatus.ComputingHashes(
                                currentIndex = processedCount,
                                totalCandidates = totalCandidates,
                                currentFileName = file.name,
                                currentFileSize = size,
                                speedFilesPerSec = speed
                            )
                        )
                    )
                }
            }
        }

        // 4. Finalize duplicate groups
        val duplicateGroups = mutableListOf<DuplicateGroup>()
        var totalDuplicateFilesCount = 0
        var totalWastedBytes = 0L

        for ((hashKey, files) in hashGroups) {
            if (files.size >= 2) {
                // Sort by lastModified: oldest is considered original, or newest depending on user preference
                val sortedFiles = files.sortedBy { it.lastModified() }
                val items = sortedFiles.mapIndexed { index, file ->
                    val isExternal = externalStoragePaths.any { file.absolutePath.startsWith(it) } ||
                        (file.absolutePath.contains("/storage/") && !file.absolutePath.startsWith("/storage/emulated/")) ||
                        file.absolutePath.contains("Simulated_SDCard") ||
                        file.absolutePath.startsWith("/mnt/media_rw/")
                    DuplicateItem(
                        file = file,
                        path = file.absolutePath,
                        name = file.name,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        hash = hashKey,
                        category = FileCategory.fromExtension(file.extension),
                        isOriginal = index == 0, // First (oldest) is original by default
                        isSelected = index != 0, // Copies selected for cleanup by default
                        isExternalStorage = isExternal
                    )
                }

                val groupSize = files.first().length()
                val group = DuplicateGroup(
                    groupKey = hashKey,
                    fileSize = groupSize,
                    items = items,
                    category = items.first().category
                )
                duplicateGroups.add(group)
                totalDuplicateFilesCount += (files.size - 1)
                totalWastedBytes += (files.size - 1) * groupSize
            }
        }

        // Sort groups by wasted bytes descending (highest savings first)
        duplicateGroups.sortByDescending { it.wastedBytes }

        val totalDurationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
        val filesPerSec = (totalDiscovered * 1000.0) / totalDurationMs
        val mbPerSec = (totalBytesHashed / (1024.0 * 1024.0)) / (totalDurationMs / 1000.0)

        val stats = ScanPerformanceStats(
            totalFilesDiscovered = totalDiscovered,
            candidateFilesTested = totalCandidates,
            duplicateGroupsFound = duplicateGroups.size,
            duplicateFilesCount = totalDuplicateFilesCount,
            totalWastedBytes = totalWastedBytes,
            elapsedDurationMs = totalDurationMs,
            filesPerSecond = filesPerSec,
            megabytesPerSecond = mbPerSec,
            algorithmUsed = scanMode.title
        )

        emit(ScanProgressEvent.Completed(duplicateGroups, stats))
    }

    /**
     * Compare two specific folders (Folder A vs Folder B) to detect identical files across them.
     */
    fun compareTwoFolders(
        folderA: String,
        folderB: String,
        scanMode: ScanMode = ScanMode.HASH_SHA256,
        includeHiddenFiles: Boolean = true
    ): Flow<ScanProgressEvent> = flow {
        val startTime = System.currentTimeMillis()
        emit(ScanProgressEvent.Status(ScanStatus.DiscoveringFiles("Menyiapkan perbandingan folder...", 0)))

        val filesA = mutableListOf<File>()
        val filesB = mutableListOf<File>()

        discoverFilesRecursively(File(folderA), includeHiddenFiles, 1L, FileCategory.ALL, { _, _ -> }, filesA)
        discoverFilesRecursively(File(folderB), includeHiddenFiles, 1L, FileCategory.ALL, { _, _ -> }, filesB)

        // Build size map for folder A
        val sizeMapA = filesA.groupBy { it.length() }
        val candidatePairs = mutableListOf<Pair<File, File>>()

        for (fileB in filesB) {
            val matchingA = sizeMapA[fileB.length()] ?: continue
            for (fileA in matchingA) {
                candidatePairs.add(Pair(fileA, fileB))
            }
        }

        val totalCandidates = candidatePairs.size
        val duplicateGroups = mutableListOf<DuplicateGroup>()
        var testedCount = 0

        for (pair in candidatePairs) {
            if (!coroutineContext.isActive) break
            val fileA = pair.first
            val fileB = pair.second

            testedCount++
            emit(
                ScanProgressEvent.Status(
                    ScanStatus.ComputingHashes(
                        currentIndex = testedCount,
                        totalCandidates = totalCandidates,
                        currentFileName = "${fileA.name} <-> ${fileB.name}",
                        currentFileSize = fileA.length(),
                        speedFilesPerSec = 0.0
                    )
                )
            )

            val isMatch = when (scanMode) {
                ScanMode.NAME_AND_SIZE -> fileA.name.equals(fileB.name, ignoreCase = true)
                ScanMode.CHUNK_SIZE -> computeChunkHash(fileA, fileA.length()) == computeChunkHash(fileB, fileB.length())
                else -> {
                    // Quick chunk check first
                    if (computeChunkHash(fileA, fileA.length()) == computeChunkHash(fileB, fileB.length())) {
                        computeFileHash(fileA, "SHA-256") == computeFileHash(fileB, "SHA-256")
                    } else false
                }
            }

            if (isMatch) {
                val isExtA = (fileA.absolutePath.contains("/storage/") && !fileA.absolutePath.startsWith("/storage/emulated/")) ||
                    fileA.absolutePath.contains("Simulated_SDCard") || fileA.absolutePath.startsWith("/mnt/media_rw/")
                val isExtB = (fileB.absolutePath.contains("/storage/") && !fileB.absolutePath.startsWith("/storage/emulated/")) ||
                    fileB.absolutePath.contains("Simulated_SDCard") || fileB.absolutePath.startsWith("/mnt/media_rw/")

                val itemA = DuplicateItem(
                    file = fileA,
                    isOriginal = true,
                    isSelected = false,
                    folderName = fileA.parentFile?.name ?: "Folder A",
                    isExternalStorage = isExtA
                )
                val itemB = DuplicateItem(
                    file = fileB,
                    isOriginal = false,
                    isSelected = true,
                    folderName = fileB.parentFile?.name ?: "Folder B",
                    isExternalStorage = isExtB
                )
                val group = DuplicateGroup(
                    groupKey = "${fileA.name}_${fileA.length()}",
                    fileSize = fileA.length(),
                    items = listOf(itemA, itemB),
                    category = itemA.category
                )
                duplicateGroups.add(group)
            }
        }

        val totalDurationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1)
        val stats = ScanPerformanceStats(
            totalFilesDiscovered = filesA.size + filesB.size,
            candidateFilesTested = totalCandidates,
            duplicateGroupsFound = duplicateGroups.size,
            duplicateFilesCount = duplicateGroups.size,
            totalWastedBytes = duplicateGroups.sumOf { it.fileSize },
            elapsedDurationMs = totalDurationMs,
            filesPerSecond = ((filesA.size + filesB.size) * 1000.0) / totalDurationMs,
            algorithmUsed = "Perbandingan Folder (${scanMode.title})"
        )

        emit(ScanProgressEvent.Completed(duplicateGroups, stats))
    }

    private suspend fun discoverFilesRecursively(
        dir: File,
        includeHidden: Boolean,
        minSizeBytes: Long,
        categoryFilter: FileCategory,
        onProgress: suspend (String, Int) -> Unit,
        accumulator: MutableList<File>
    ) {
        if (!coroutineContext.isActive || !dir.exists()) return

        val files = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        } ?: return

        for (f in files) {
            if (!coroutineContext.isActive) return

            if (f.isDirectory) {
                val folderName = f.name
                // Skip developer/build artifacts that aren't user storage
                if (folderName == ".git" || folderName == ".gradle" || folderName == ".idea" || folderName == "node_modules") {
                    continue
                }
                // ALWAYS scan folders starting with '.', such as '.namaFolder', '.secret', '.backup', etc.
                onProgress(folderName, accumulator.size)
                discoverFilesRecursively(f, includeHidden, minSizeBytes, categoryFilter, onProgress, accumulator)
            } else if (f.isFile) {
                // If hidden files are excluded, only skip metadata files like .nomedia / .DS_Store
                if (!includeHidden && f.name.startsWith(".") && (f.name.equals(".nomedia", true) || f.name.equals(".DS_Store", true))) {
                    continue
                }
                if (f.length() >= minSizeBytes) {
                    if (categoryFilter == FileCategory.ALL || FileCategory.fromExtension(f.extension) == categoryFilter) {
                        accumulator.add(f)
                    }
                }
            }
        }
    }

    private fun computeChunkHash(file: File, fileSize: Long): String {
        return try {
            val digest = MessageDigest.getInstance("MD5")
            val chunkSize = 4096.coerceAtMost(fileSize.toInt())
            val buffer = ByteArray(chunkSize)

            RandomAccessFile(file, "r").use { raf ->
                // First chunk
                raf.seek(0)
                val readStart = raf.read(buffer, 0, chunkSize)
                if (readStart > 0) digest.update(buffer, 0, readStart)

                // Last chunk
                if (fileSize > chunkSize) {
                    val tailPos = (fileSize - chunkSize).coerceAtLeast(0L)
                    raf.seek(tailPos)
                    val readEnd = raf.read(buffer, 0, chunkSize)
                    if (readEnd > 0) digest.update(buffer, 0, readEnd)
                }
            }

            bytesToHex(digest.digest())
        } catch (_: Exception) {
            "${file.name}_${file.length()}"
        }
    }

    private fun computeFileHash(file: File, algorithm: String): String {
        return try {
            val digest = MessageDigest.getInstance(algorithm)
            val buffer = ByteArray(65536) // 64KB buffer
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            bytesToHex(digest.digest())
        } catch (_: Exception) {
            ""
        }
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = "0123456789abcdef"
        val result = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val i = b.toInt() and 0xFF
            result.append(hexChars[i shr 4])
            result.append(hexChars[i and 0x0F])
        }
        return result.toString()
    }
}

sealed interface ScanProgressEvent {
    data class Status(val status: ScanStatus) : ScanProgressEvent
    data class Completed(val groups: List<DuplicateGroup>, val stats: ScanPerformanceStats) : ScanProgressEvent
}
