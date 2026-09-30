package com.example.scanner

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import com.example.model.StorageType
import com.example.model.StorageVolumeInfo
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

object StorageDetector {

    /**
     * Detects both Internal Storage and External Storage (MicroSD Card, USB OTG, secondary partitions).
     */
    fun detectStorageVolumes(context: Context): List<StorageVolumeInfo> {
        val list = mutableListOf<StorageVolumeInfo>()
        val seenPaths = mutableSetOf<String>()

        // 1. Primary Internal Storage (e.g. /storage/emulated/0)
        val primaryDir = Environment.getExternalStorageDirectory()
        val primaryPath = primaryDir?.absolutePath ?: "/storage/emulated/0"
        if (primaryDir != null && primaryDir.exists()) {
            seenPaths.add(primaryPath)
            val stat = getStorageStats(primaryPath)
            list.add(
                StorageVolumeInfo(
                    id = "internal_primary",
                    name = "Memori Internal",
                    path = primaryPath,
                    isRemovable = false,
                    isPrimary = true,
                    totalBytes = stat.first,
                    freeBytes = stat.second,
                    isSelected = true,
                    storageType = StorageType.INTERNAL
                )
            )
        }

        // 2. StorageManager StorageVolumes (API 24+ Android N)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (storageManager != null) {
                try {
                    val volumes = storageManager.storageVolumes
                    for (volume in volumes) {
                        val isRemovable = volume.isRemovable
                        val isPrimary = volume.isPrimary
                        
                        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            try { volume.directory } catch (_: Throwable) { null }
                        } else {
                            resolveVolumeDirectoryFallback(volume)
                        }

                        if (dir != null && dir.exists() && !seenPaths.contains(dir.absolutePath)) {
                            val path = dir.absolutePath
                            seenPaths.add(path)
                            val stat = getStorageStats(path)
                            val desc = volume.getDescription(context) ?: if (isRemovable) "Kartu SD Eksternal" else "Memori Internal"
                            
                            val isUsb = desc.contains("USB", ignoreCase = true) || path.contains("usb", ignoreCase = true)
                            val type = when {
                                !isRemovable && isPrimary -> StorageType.INTERNAL
                                isUsb -> StorageType.USB_OTG
                                else -> StorageType.EXTERNAL_SD
                            }

                            list.add(
                                StorageVolumeInfo(
                                    id = "volume_${volume.hashCode()}",
                                    name = if (isRemovable) {
                                        if (isUsb) "Memori Eksternal (USB OTG)" else "Memori Eksternal ($desc)"
                                    } else desc,
                                    path = path,
                                    isRemovable = isRemovable,
                                    isPrimary = isPrimary,
                                    totalBytes = stat.first,
                                    freeBytes = stat.second,
                                    isSelected = true,
                                    storageType = type
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                    // Handled by subsequent fallbacks
                }
            }
        }

        // 3. Fallback via ContextCompat.getExternalFilesDirs
        val extDirs = ContextCompat.getExternalFilesDirs(context, null)
        for ((idx, f) in extDirs.withIndex()) {
            if (f != null) {
                val rootDir = extractVolumeRootFromAppDir(f)
                if (rootDir != null && rootDir.exists() && !seenPaths.contains(rootDir.absolutePath)) {
                    val path = rootDir.absolutePath
                    seenPaths.add(path)
                    val stat = getStorageStats(path)
                    val isUsb = path.contains("usb", ignoreCase = true)
                    list.add(
                        StorageVolumeInfo(
                            id = "ext_dir_$idx",
                            name = if (isUsb) "Memori Eksternal (USB Drive)" else "Memori Eksternal (Kartu SD)",
                            path = path,
                            isRemovable = true,
                            isPrimary = false,
                            totalBytes = stat.first,
                            freeBytes = stat.second,
                            isSelected = true,
                            storageType = if (isUsb) StorageType.USB_OTG else StorageType.EXTERNAL_SD
                        )
                    )
                }
            }
        }

        // 4. Probing /storage directory for removable mounts (e.g. /storage/XXXX-XXXX)
        val storageParent = File("/storage")
        if (storageParent.exists() && storageParent.isDirectory) {
            val subDirs = storageParent.listFiles()
            if (subDirs != null) {
                for (sub in subDirs) {
                    val name = sub.name
                    if (name != "emulated" && name != "self" && name != "knox" && name != "container") {
                        if (sub.isDirectory && sub.canRead() && !seenPaths.contains(sub.absolutePath)) {
                            val path = sub.absolutePath
                            seenPaths.add(path)
                            val stat = getStorageStats(path)
                            val isUsb = name.contains("usb", ignoreCase = true)
                            list.add(
                                StorageVolumeInfo(
                                    id = "storage_sub_${sub.name}",
                                    name = if (isUsb) "Memori Eksternal (USB $name)" else "Memori Eksternal (Kartu SD $name)",
                                    path = path,
                                    isRemovable = true,
                                    isPrimary = false,
                                    totalBytes = stat.first,
                                    freeBytes = stat.second,
                                    isSelected = true,
                                    storageType = if (isUsb) StorageType.USB_OTG else StorageType.EXTERNAL_SD
                                )
                            )
                        }
                    }
                }
            }
        }

        // 5. Inspect /proc/mounts for mountpoints of vfat/exfat/ntfs/fuse sdcard
        detectFromProcMounts(seenPaths, list)

        // 6. Check for Simulated/Demo External Storage (if created by user for testing)
        val simSd = File(context.getExternalFilesDir(null), "Simulated_SDCard")
        if (simSd.exists() && !seenPaths.contains(simSd.absolutePath)) {
            val stat = getStorageStats(simSd.absolutePath)
            list.add(
                StorageVolumeInfo(
                    id = "simulated_external_sd",
                    name = "Memori Eksternal (Simulasi SD Card)",
                    path = simSd.absolutePath,
                    isRemovable = true,
                    isPrimary = false,
                    totalBytes = if (stat.first > 0) stat.first else 64L * 1024 * 1024 * 1024,
                    freeBytes = if (stat.second > 0) stat.second else 42L * 1024 * 1024 * 1024,
                    isSelected = true,
                    storageType = StorageType.EXTERNAL_SD
                )
            )
        }

        // 7. Fallback if empty: App sandboxed storage
        if (list.isEmpty()) {
            val appSpecificDir = context.getExternalFilesDir(null) ?: context.filesDir
            val stat = getStorageStats(appSpecificDir.absolutePath)
            list.add(
                StorageVolumeInfo(
                    id = "app_internal_fallback",
                    name = "Penyimpanan Internal",
                    path = appSpecificDir.absolutePath,
                    isRemovable = false,
                    isPrimary = true,
                    totalBytes = stat.first,
                    freeBytes = stat.second,
                    isSelected = true,
                    storageType = StorageType.INTERNAL
                )
            )
        }

        return list
    }

    private fun resolveVolumeDirectoryFallback(volume: Any): File? {
        return try {
            val method = volume.javaClass.getMethod("getDirectory")
            method.invoke(volume) as? File
        } catch (_: Exception) {
            try {
                val method = volume.javaClass.getMethod("getPathFile")
                method.invoke(volume) as? File
            } catch (_: Exception) {
                try {
                    val method = volume.javaClass.getMethod("getPath")
                    (method.invoke(volume) as? String)?.let { File(it) }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }

    /**
     * Walks up from app-specific directory to find the storage volume root.
     * e.g., /storage/1234-5678/Android/data/com.example/files -> /storage/1234-5678
     */
    private fun extractVolumeRootFromAppDir(appDir: File): File? {
        var curr: File? = appDir
        while (curr != null && curr.parentFile != null) {
            if (curr.name.equals("Android", ignoreCase = true)) {
                return curr.parentFile
            }
            curr = curr.parentFile
        }
        return null
    }

    private fun detectFromProcMounts(seenPaths: MutableSet<String>, list: MutableList<StorageVolumeInfo>) {
        val mountsFile = File("/proc/mounts")
        if (!mountsFile.exists() || !mountsFile.canRead()) return

        try {
            BufferedReader(FileReader(mountsFile)).use { reader ->
                var line = reader.readLine()
                while (line != null) {
                    val tokens = line.split("\\s+".toRegex())
                    if (tokens.size >= 3) {
                        val mountPoint = tokens[1]
                        val fsType = tokens[2]
                        if ((mountPoint.startsWith("/storage/") || mountPoint.startsWith("/mnt/media_rw/")) &&
                            !mountPoint.contains("emulated") &&
                            !mountPoint.contains("self")
                        ) {
                            val f = File(mountPoint)
                            if (f.exists() && f.isDirectory && f.canRead() && !seenPaths.contains(f.absolutePath)) {
                                seenPaths.add(f.absolutePath)
                                val stat = getStorageStats(f.absolutePath)
                                val isUsb = mountPoint.contains("usb", ignoreCase = true)
                                list.add(
                                    StorageVolumeInfo(
                                        id = "mount_${f.name}",
                                        name = if (isUsb) "Memori Eksternal (USB OTG)" else "Memori Eksternal (Kartu SD ${f.name})",
                                        path = f.absolutePath,
                                        isRemovable = true,
                                        isPrimary = false,
                                        totalBytes = stat.first,
                                        freeBytes = stat.second,
                                        isSelected = true,
                                        storageType = if (isUsb) StorageType.USB_OTG else StorageType.EXTERNAL_SD
                                    )
                                )
                            }
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (_: Exception) {
            // Ignore proc mounts read failure
        }
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

    /**
     * Determines whether a given file path is located on external/removable storage.
     */
    fun isPathOnExternalStorage(path: String, volumes: List<StorageVolumeInfo>): Boolean {
        for (v in volumes) {
            if (v.isRemovable && path.startsWith(v.path)) {
                return true
            }
        }
        // Fallback heuristic: /storage/XXXX-XXXX, /mnt/media_rw
        if (path.contains("/storage/") && !path.startsWith("/storage/emulated/")) {
            return true
        }
        if (path.startsWith("/mnt/media_rw/")) {
            return true
        }
        if (path.contains("Simulated_SDCard")) {
            return true
        }
        return false
    }

    /**
     * Creates a simulated external SD Card directory with duplicate files
     * allowing users on emulators or devices without physical SD cards to test
     * scanning internal and external memory simultaneously.
     */
    fun createSimulatedExternalStorage(context: Context): String {
        val root = File(context.getExternalFilesDir(null), "Simulated_SDCard")
        val dcim = File(root, "DCIM/Camera").apply { mkdirs() }
        val documents = File(root, "Documents").apply { mkdirs() }
        val music = File(root, "Music").apply { mkdirs() }

        // Create duplicate sample files inside simulated external memory
        File(dcim, "IMG_2026_SD_COPY.jpg").writeBytes(createSyntheticJpeg(600, 600, 0x22, 0x88, 0xDD))
        File(dcim, "IMG_2026_SD_BACKUP.jpg").writeBytes(createSyntheticJpeg(600, 600, 0x22, 0x88, 0xDD))

        File(documents, "Laporan_Keuangan_SD.pdf").writeText("%PDF-1.4\nDuplicate PDF on External SD Card Storage LishClean\n%%EOF")
        File(documents, "Laporan_Keuangan_SD_Copy.pdf").writeText("%PDF-1.4\nDuplicate PDF on External SD Card Storage LishClean\n%%EOF")

        File(music, "Track01_SDCard.mp3").writeText("ID3v2.3.0\nDuplicate MP3 audio stored on MicroSD card partition")
        File(music, "Track01_SDCard_Dup.mp3").writeText("ID3v2.3.0\nDuplicate MP3 audio stored on MicroSD card partition")

        return root.absolutePath
    }

    private fun createSyntheticJpeg(width: Int, height: Int, r: Int, g: Int, b: Int): ByteArray {
        val header = byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
            0x00.toByte(), 0x10.toByte(), 0x4A.toByte(), 0x46.toByte(),
            0x49.toByte(), 0x46.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x01.toByte(), 0x01.toByte(), 0x00.toByte(), 0x48.toByte(),
            0x00.toByte(), 0x48.toByte(), 0x00.toByte(), 0x00.toByte()
        )
        val body = ByteArray(4096) { i -> ((i * 31 + r + g + b) % 256).toByte() }
        val footer = byteArrayOf(0xFF.toByte(), 0xD9.toByte())
        return header + body + footer
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

        // Simulated SD Card if exists
        val simSd = File(context.getExternalFilesDir(null), "Simulated_SDCard")
        if (simSd.exists()) {
            list.add(Pair("Folder SD Card (Simulasi)", simSd.absolutePath))
        }

        return list
    }

    data class FolderQuickInfo(
        val totalFiles: Int,
        val totalSize: Long,
        val subfolderCount: Int
    )

    fun getFolderQuickInfo(folder: File): FolderQuickInfo {
        var files = 0
        var size = 0L
        var subdirs = 0
        try {
            val list = folder.listFiles() ?: return FolderQuickInfo(0, 0L, 0)
            for (f in list) {
                if (f.isDirectory) {
                    subdirs++
                } else if (f.isFile) {
                    files++
                    size += f.length()
                }
            }
        } catch (_: Exception) {
        }
        return FolderQuickInfo(files, size, subdirs)
    }

    /**
     * Resolves an Android SAF content URI (from OpenDocumentTree) to a real filesystem path if possible.
     */
    fun uriToPath(context: Context, uri: android.net.Uri): String? {
        return try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri) ?: return null
            val split = docId.split(":")
            if (split.isEmpty()) return null
            val type = split[0]
            val subPath = if (split.size > 1) split[1] else ""
            if ("primary".equals(type, ignoreCase = true)) {
                val base = Environment.getExternalStorageDirectory().absolutePath
                if (subPath.isEmpty()) base else "$base/$subPath"
            } else {
                val extDirs = ContextCompat.getExternalFilesDirs(context, null)
                val extMatch = extDirs.mapNotNull { it?.absolutePath }.firstOrNull { it.contains(type) }
                if (extMatch != null) {
                    val root = extMatch.substringBefore("/Android")
                    if (subPath.isEmpty()) root else "$root/$subPath"
                } else {
                    val candidate = File("/storage/$type/$subPath")
                    if (candidate.exists()) candidate.absolutePath else "/storage/$type"
                }
            }
        } catch (_: Exception) {
            null
        }
    }
}
