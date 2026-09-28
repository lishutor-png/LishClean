package com.example.model

enum class ScanMode(
    val title: String,
    val description: String,
    val speedTag: String,
    val accuracyTag: String
) {
    HASH_SHA256(
        title = "Hash Lengkap (SHA-256)",
        description = "Akurasi tertinggi (100%), membandingkan bit demi bit konten file menggunakan SHA-256.",
        speedTag = "Optimal",
        accuracyTag = "100% Akurat"
    ),
    HASH_MD5(
        title = "Hash Cepat (MD5)",
        description = "Kombinasi kecepatan tinggi dan akurasi tinggi menggunakan algoritma MD5.",
        speedTag = "Sangat Cepat",
        accuracyTag = "99.9% Akurat"
    ),
    CHUNK_SIZE(
        title = "Ukuran + Header/Footer Hash",
        description = "Super cepat untuk file besar: memeriksa ukuran persis lalu mencocokkan blok awal & akhir file.",
        speedTag = "Ultra Cepat",
        accuracyTag = "Cepat & Aman"
    ),
    NAME_AND_SIZE(
        title = "Nama & Ukuran Persis",
        description = "Mencocokkan kesamaan nama file serta ukuran data tanpa membaca isi file.",
        speedTag = "Instan",
        accuracyTag = "Standar"
    )
}
