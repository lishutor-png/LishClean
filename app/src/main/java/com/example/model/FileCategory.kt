package com.example.model

enum class FileCategory(
    val displayName: String,
    val shortName: String,
    val extensions: List<String>,
    val badgeLabel: String
) {
    ALL(
        displayName = "Semua Tipe",
        shortName = "Semua",
        extensions = emptyList(),
        badgeLabel = "Semua Format"
    ),
    IMAGES(
        displayName = "Foto & Gambar",
        shortName = "Gambar",
        extensions = listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "svg", "raw", "cr2", "nef"),
        badgeLabel = "JPG, PNG, WEBP, HEIC..."
    ),
    VIDEOS(
        displayName = "Video & Rekaman",
        shortName = "Video",
        extensions = listOf("mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv", "m4v", "ts"),
        badgeLabel = "MP4, MKV, MOV, 3GP..."
    ),
    AUDIO(
        displayName = "Musik & Suara",
        shortName = "Audio",
        extensions = listOf("mp3", "aac", "wav", "flac", "ogg", "m4a", "wma", "opus", "amr"),
        badgeLabel = "MP3, AAC, FLAC, M4A..."
    ),
    DOCUMENTS(
        displayName = "Dokumen & Berkas",
        shortName = "Dokumen",
        extensions = listOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "json", "xml", "epub"),
        badgeLabel = "PDF, DOCX, XLSX, TXT..."
    ),
    ARCHIVES(
        displayName = "Arsip & Kompresi",
        shortName = "Arsip",
        extensions = listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz"),
        badgeLabel = "ZIP, RAR, 7Z, TAR..."
    ),
    APKS(
        displayName = "Paket Aplikasi (APK)",
        shortName = "APK",
        extensions = listOf("apk", "xapk", "apks", "aab"),
        badgeLabel = "APK, XAPK, APKS..."
    ),
    OTHERS(
        displayName = "Format Lainnya",
        shortName = "Lainnya",
        extensions = emptyList(),
        badgeLabel = "File Lain"
    );

    companion object {
        fun fromExtension(ext: String): FileCategory {
            val lower = ext.lowercase()
            return entries.firstOrNull { it != ALL && it != OTHERS && it.extensions.contains(lower) } ?: OTHERS
        }
    }
}
