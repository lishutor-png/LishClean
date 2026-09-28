package com.example.model

import java.io.File

enum class StorageType(val label: String) {
    INTERNAL("Memori Internal"),
    EXTERNAL_SD("Memori Eksternal (Kartu SD)"),
    USB_OTG("Memori Eksternal (USB OTG)"),
    CUSTOM("Folder Tambahan")
}

data class StorageVolumeInfo(
    val id: String,
    val name: String,
    val path: String,
    val isRemovable: Boolean,
    val isPrimary: Boolean,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long = (totalBytes - freeBytes).coerceAtLeast(0L),
    val isSelected: Boolean = true,
    val storageType: StorageType = if (isRemovable) StorageType.EXTERNAL_SD else StorageType.INTERNAL
) {
    val usagePercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedTotal: String
        get() = DuplicateItem.formatFileSize(totalBytes)

    val formattedFree: String
        get() = DuplicateItem.formatFileSize(freeBytes)

    val formattedUsed: String
        get() = DuplicateItem.formatFileSize(usedBytes)
}

data class CompareFolderItem(
    val id: String,
    val path: String,
    val displayName: String,
    val fileCount: Int = 0,
    val totalSize: Long = 0L
)
