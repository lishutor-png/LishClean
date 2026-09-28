package com.example.model

data class DuplicateGroup(
    val groupKey: String,
    val fileSize: Long,
    val items: List<DuplicateItem>,
    val category: FileCategory = items.firstOrNull()?.category ?: FileCategory.OTHERS
) {
    val copiesCount: Int
        get() = (items.size - 1).coerceAtLeast(0)

    val wastedBytes: Long
        get() = copiesCount * fileSize

    val formattedWastedBytes: String
        get() = DuplicateItem.formatFileSize(wastedBytes)

    val selectedCount: Int
        get() = items.count { it.isSelected }

    val selectedBytes: Long
        get() = selectedCount * fileSize
}
