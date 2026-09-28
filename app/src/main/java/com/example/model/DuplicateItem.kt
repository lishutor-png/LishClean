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
            if (bytes < 1024) return "$bytes B"
            val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
            val pre = "KMGTPE"[exp - 1]
            return String.format("%.1f %sB", bytes / Math.pow(1024.0, exp.toDouble()), pre)
        }
    }
}
