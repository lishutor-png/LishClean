package com.example.model

import java.io.File

data class DuplicateItem(
    val file: File,
    val path: String = file.absolutePath,
    val name: String = file.name,
    val sizeBytes: Long = file.length(),
    val lastModified: Long = file.lastModified(),
    val hash: String = "",
    val category: FileCategory = FileCategory.fromExtension(file.extension),
    val isOriginal: Boolean = false,
    val isSelected: Boolean = false,
    val folderPath: String = file.parent ?: "",
    val folderName: String = file.parentFile?.name ?: "Root",
    val isHidden: Boolean = file.name.startsWith(".") || (file.parentFile?.name?.startsWith(".") == true),
    val isExternalStorage: Boolean = false
) {
    val formattedSize: String
        get() = formatFileSize(sizeBytes)

    companion object {
        fun formatFileSize(bytes: Long): String {
            if (bytes <= 0L) return "0 B"
            if (bytes < 1024L) return "$bytes B"
            val units = arrayOf("KB", "MB", "GB", "TB", "PB")
            val exp = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt() - 1
            val clampedExp = exp.coerceIn(0, units.size - 1)
            val value = bytes / Math.pow(1024.0, (clampedExp + 1).toDouble())
            return String.format(java.util.Locale.US, "%.1f %s", value, units[clampedExp])
        }
    }
}
