package com.example.scanner

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import com.example.model.StorageVolumeInfo
import java.io.File

object StorageDetector {

    fun detectStorageVolumes(context: Context): List<StorageVolumeInfo> {
        val list = mutableListOf<StorageVolumeInfo>()
        val seenPaths = mutableSetOf<String>()

        // 1. Primary Internal Storage
        val primaryDir = Environment.getExternalStorageDirectory()
        if (primaryDir != null && primaryDir.exists()) {
            val primaryPath = primaryDir.absolutePath
            seenPaths.add(primaryPath)
            val stat = getStorageStats(primaryPath)
            list.add(
                StorageVolumeInfo(
                    id = "internal_primary",
                    name = "Penyimpanan Internal",
                    path = primaryPath,
                    isRemovable = false,
                    isPrimary = true,
                    totalBytes = stat.first,
                    freeBytes = stat.second,
                    isSelected = true
                )
            )
        }

        // 2. Secondary volumes / SD Cards / USB OTG
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (storageManager != null) {
                try {
                    val volumes = storageManager.storageVolumes
                    for (volume in volumes) {
                        val isRemovable = volume.isRemovable
                        val isPrimary = volume.isPrimary
                        val description = volume.getDescription(context) ?: if (isRemovable) "Kartu SD / USB" else "Penyimpanan"
                        
                        // Reflection or directory path
                        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            volume.directory
                        } else {
                            try {
                                val method = volume.javaClass.getMethod("getPathFile")
                                method.invoke(volume) as? File
                            } catch (_: Exception) {
                                null
                            }
                        }

                        if (dir != null && dir.exists() && !seenPaths.contains(dir.absolutePath)) {
                            seenPaths.add(dir.absolutePath)
                            val stat = getStorageStats(dir.absolutePath)
                            list.add(
                                StorageVolumeInfo(
                                    id = "volume_${volume.hashCode()}",
                                    name = description,
                                    path = dir.absolutePath,
                                    isRemovable = isRemovable,
                                    isPrimary = isPrimary,
                                    totalBytes = stat.first,
                                    freeBytes = stat.second,
                                    isSelected = isRemovable
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Fallback to getExternalFilesDirs
                }
            }
        }

        // 3. Fallback check with getExternalFilesDirs
        val extDirs = ContextCompat.getExternalFilesDirs(context, null)
        for ((idx, f) in extDirs.withIndex()) {
            if (f != null) {
                // Find root of volume (e.g., /storage/XXXX-XXXX)
                val path = f.absolutePath
                val storageRoot = extractStorageRoot(path)
                if (storageRoot != null && !seenPaths.contains(storageRoot)) {
                    seenPaths.add(storageRoot)
                    val rootFile = File(storageRoot)
                    val stat = getStorageStats(storageRoot)
                    list.add(
                        StorageVolumeInfo(
                            id = "ext_dir_$idx",
                            name = "Drive Eksternal ($idx)",
                            path = storageRoot,
                            isRemovable = true,
                            isPrimary = false,
                            totalBytes = stat.first,
                            freeBytes = stat.second,
                            isSelected = true
                        )
                    )
                }
            }
        }

        // 4. Also add App Sandboxed Media & Test Directory for safe testing
        val appSpecificDir = context.getExternalFilesDir(null)
        if (appSpecificDir != null && list.isEmpty()) {
            val stat = getStorageStats(appSpecificDir.absolutePath)
            list.add(
                StorageVolumeInfo(
                    id = "app_internal",
                    name = "Penyimpanan Aplikasi",
                    path = appSpecificDir.absolutePath,
                    isRemovable = false,
                    isPrimary = true,
                    totalBytes = stat.first,
                    freeBytes = stat.second,
                    isSelected = true
                )
            )
        }

        return list
    }

    private fun extractStorageRoot(fullPath: String): String? {
        val parts = fullPath.split("/")
        val storageIdx = parts.indexOf("storage")
        if (storageIdx != -1 && storageIdx + 1 < parts.size) {
            val volumeId = parts[storageIdx + 1]
            if (volumeId != "emulated" && volumeId != "self") {
                return "/storage/$volumeId"
            }
        }
        return null
    }

    private fun getStorageStats(path: String): Pair<Long, Long> {
        return try {
            val stat = StatFs(path)
            val total = stat.totalBytes
            val available = stat.availableBytes
            Pair(total, available)
        } catch (_: Exception) {
            Pair(0L, 0L)
        }
    }

    fun getCommonDirectories(context: Context): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        val primary = Environment.getExternalStorageDirectory()
        if (primary != null && primary.exists()) {
            val folders = listOf(
                Pair("Unduhan (Downloads)", File(primary, Environment.DIRECTORY_DOWNLOADS)),
                Pair("Kamera & Foto (DCIM)", File(primary, Environment.DIRECTORY_DCIM)),
                Pair("Gambar (Pictures)", File(primary, Environment.DIRECTORY_PICTURES)),
                Pair("Dokumen (Documents)", File(primary, Environment.DIRECTORY_DOCUMENTS)),
                Pair("Musik (Music)", File(primary, Environment.DIRECTORY_MUSIC)),
                Pair("WhatsApp Media", File(primary, "Android/media/com.whatsapp/WhatsApp/Media"))
            )
            for (f in folders) {
                if (f.second.exists()) {
                    list.add(Pair(f.first, f.second.absolutePath))
                }
            }
        }

        // App Test/Sample Directory
        val appTestDir = File(context.getExternalFilesDir(null), "SampleTestDuplicates")
        list.add(Pair("Folder Sampel Pengujian", appTestDir.absolutePath))

        return list
    }
}
