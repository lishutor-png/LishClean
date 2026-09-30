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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
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
        try {
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
            if (totalDiscovered < 2) {
                val stats = ScanPerformanceStats(
                    totalFilesDiscovered = totalDiscovered,
                    candidateFilesTested = 0,
                    duplicateGroupsFound = 0,
                    duplicateFilesCount = 0,
                    totalWastedBytes = 0L,
                    elapsedDurationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1),
                    filesPerSecond = 0.0,
                    megabytesPerSecond = 0.0,
                    algorithmUsed = scanMode.title
                )
                emit(ScanProgressEvent.Completed(emptyList(), stats))
                return@flow
            }

            // 2. Tier 1: Group by file size
            emit(ScanProgressEvent.Status(ScanStatus.GroupingBySize(totalDiscovered)))

            val sizeBuckets = mutableMapOf<Long, MutableList<File>>()
            for (file in allCandidateFiles) {
                if (!coroutineContext.isActive) break
                val size = file.length()
                if (size >= minFileSizeBytes) {
                    sizeBuckets.getOrPut(size) { mutableListOf() }.add(file)
                }
            }

            // Filter out buckets with only 1 file
            val collisionBuckets = sizeBuckets.filterValues { it.size >= 2 }
            val totalCandidates = collisionBuckets.values.sumOf { it.size }

            if (totalCandidates == 0) {
                val stats = ScanPerformanceStats(
                    totalFilesDiscovered = totalDiscovered,
                    candidateFilesTested = 0,
                    duplicateGroupsFound = 0,
                    duplicateFilesCount = 0,
                    totalWastedBytes = 0L,
                    elapsedDurationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1),
                    filesPerSecond = (totalDiscovered * 1000.0) / (System.currentTimeMillis() - startTime).coerceAtLeast(1),
                    megabytesPerSecond = 0.0,
                    algorithmUsed = scanMode.title
                )
                emit(ScanProgressEvent.Completed(emptyList(), stats))
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
                    val sortedFiles = files.sortedBy { it.lastModified() }
                    val items = sortedFiles.mapIndexed { index, file ->
                        val isExternal = isPathExternal(file.absolutePath, externalStoragePaths)
                        DuplicateItem(
                            file = file,
                            path = file.absolutePath,
                            name = file.name,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            hash = hashKey,
                            category = FileCategory.fromExtension(file.extension),
                            isOriginal = index == 0,
                            isSelected = index != 0,
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
        } catch (e: Exception) {
            emit(ScanProgressEvent.Error(e.message ?: "Pemindaian terhenti karena kesalahan sistem."))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Compare two specific folders (Folder A vs Folder B) efficiently and safely.
     * Supports optional category filtering and recursive vs single-folder mode.
     */
    fun compareTwoFolders(
        folderA: String,
        folderB: String,
        scanMode: ScanMode = ScanMode.HASH_SHA256,
        includeHiddenFiles: Boolean = true,
        categoryFilter: FileCategory = FileCategory.ALL,
        recursive: Boolean = true
    ): Flow<ScanProgressEvent> = flow {
        val startTime = System.currentTimeMillis()
        try {
            emit(ScanProgressEvent.Status(ScanStatus.DiscoveringFiles("Menyiapkan perbandingan folder...", 0)))

            val dirA = File(folderA)
            val dirB = File(folderB)

            if (!dirA.exists() || !dirA.isDirectory) {
                emit(ScanProgressEvent.Error("Folder A tidak valid atau tidak dapat diakses: ${dirA.name}"))
                return@flow
            }
            if (!dirB.exists() || !dirB.isDirectory) {
                emit(ScanProgressEvent.Error("Folder B tidak valid atau tidak dapat diakses: ${dirB.name}"))
                return@flow
            }
            if (dirA.canonicalPath == dirB.canonicalPath) {
                emit(ScanProgressEvent.Error("Folder A dan B adalah folder yang sama! Pilih dua folder berbeda."))
                return@flow
            }

            val filesA = mutableListOf<File>()
            val filesB = mutableListOf<File>()

            if (recursive) {
                discoverFilesRecursively(dirA, includeHiddenFiles, 1L, categoryFilter, { d, c ->
                    emit(ScanProgressEvent.Status(ScanStatus.DiscoveringFiles("Folder A: $d", filesA.size + c)))
                }, filesA)
                discoverFilesRecursively(dirB, includeHiddenFiles, 1L, categoryFilter, { d, c ->
                    emit(ScanProgressEvent.Status(ScanStatus.DiscoveringFiles("Folder B: $d", filesB.size + c)))
                }, filesB)
            } else {
                dirA.listFiles()?.filter { it.isFile && it.length() > 0L }?.forEach { f ->
                    if (categoryFilter == FileCategory.ALL || FileCategory.fromExtension(f.extension) == categoryFilter) {
                        filesA.add(f)
                    }
                }
                dirB.listFiles()?.filter { it.isFile && it.length() > 0L }?.forEach { f ->
                    if (categoryFilter == FileCategory.ALL || FileCategory.fromExtension(f.extension) == categoryFilter) {
                        filesB.add(f)
                    }
                }
            }

            if (filesA.isEmpty() || filesB.isEmpty()) {
                val stats = ScanPerformanceStats(
                    totalFilesDiscovered = filesA.size + filesB.size,
                    candidateFilesTested = 0,
                    duplicateGroupsFound = 0,
                    duplicateFilesCount = 0,
                    totalWastedBytes = 0L,
                    elapsedDurationMs = (System.currentTimeMillis() - startTime).coerceAtLeast(1),
                    filesPerSecond = 0.0,
                    algorithmUsed = "Perbandingan Folder Khusus"
                )
                emit(ScanProgressEvent.Completed(emptyList(), stats))
                return@flow
            }

            // Group files in A by size
            val sizeMapA = filesA.groupBy { it.length() }
            val candidateFilesB = filesB.filter { sizeMapA.containsKey(it.length()) }
            val totalCandidates = candidateFilesB.size

            val duplicateGroups = mutableListOf<DuplicateGroup>()
            var testedCount = 0

            for (fileB in candidateFilesB) {
                if (!coroutineContext.isActive) break
                val matchingFilesA = sizeMapA[fileB.length()] ?: continue
                testedCount++

                emit(
                    ScanProgressEvent.Status(
                        ScanStatus.ComputingHashes(
                            currentIndex = testedCount,
                            totalCandidates = totalCandidates,
                            currentFileName = "${fileB.name} (vs Folder A)",
                            currentFileSize = fileB.length(),
                            speedFilesPerSec = 0.0
                        )
                    )
                )

                for (fileA in matchingFilesA) {
                    if (fileA.canonicalPath == fileB.canonicalPath) continue

                    val isMatch = when (scanMode) {
                        ScanMode.NAME_AND_SIZE -> fileA.name.equals(fileB.name, ignoreCase = true)
                        ScanMode.CHUNK_SIZE -> computeChunkHash(fileA, fileA.length()) == computeChunkHash(fileB, fileB.length())
                        else -> {
                            // Fast Tier 2 check first
                            if (computeChunkHash(fileA, fileA.length()) == computeChunkHash(fileB, fileB.length())) {
                                computeFileHash(fileA, "SHA-256") == computeFileHash(fileB, "SHA-256")
                            } else false
                        }
                    }

                    if (isMatch) {
                        val isExtA = isPathExternal(fileA.absolutePath)
                        val isExtB = isPathExternal(fileB.absolutePath)

                        val itemA = DuplicateItem(
                            file = fileA,
                            isOriginal = true,
                            isSelected = false,
                            folderName = fileA.parentFile?.name ?: dirA.name,
                            isExternalStorage = isExtA
                        )
                        val itemB = DuplicateItem(
                            file = fileB,
                            isOriginal = false,
                            isSelected = true,
                            folderName = fileB.parentFile?.name ?: dirB.name,
                            isExternalStorage = isExtB
                        )
                        val group = DuplicateGroup(
                            groupKey = "${fileA.name}_${fileA.length()}",
                            fileSize = fileA.length(),
                            items = listOf(itemA, itemB),
                            category = itemA.category
                        )
                        duplicateGroups.add(group)
                        break
                    }
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
        } catch (e: Exception) {
            emit(ScanProgressEvent.Error(e.message ?: "Perbandingan terhenti karena kesalahan sistem."))
        }
    }.flowOn(Dispatchers.IO)

    private fun isPathExternal(path: String, externalRoots: List<String> = emptyList()): Boolean {
        if (externalRoots.any { path.startsWith(it) }) return true
        if (path.contains("Simulated_SDCard")) return true
        if (path.startsWith("/mnt/media_rw/")) return true
        if (path.contains("/storage/") && !path.startsWith("/storage/emulated/")) return true
        return false
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
                if (folderName == ".git" || folderName == ".gradle" || folderName == ".idea" || folderName == "node_modules") {
                    continue
                }
                onProgress(folderName, accumulator.size)
                discoverFilesRecursively(f, includeHidden, minSizeBytes, categoryFilter, onProgress, accumulator)
            } else if (f.isFile) {
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
                raf.seek(0)
                val readStart = raf.read(buffer, 0, chunkSize)
                if (readStart > 0) digest.update(buffer, 0, readStart)

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
            val buffer = ByteArray(65536)
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
    data class Error(val message: String) : ScanProgressEvent
}
