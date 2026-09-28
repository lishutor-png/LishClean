# LishClean 🧹⚡

Aplikasi Android cerdas dan efisien untuk mendeteksi dan membersihkan file duplikat di penyimpanan perangkat dengan antarmuka flat design modern, filter kategori spesifik, perbandingan hash kriptografis akurat, pratinjau aman, dan pembersihan langsung tanpa beban memori.

---

## ✨ Fitur Utama LishClean

1. **Pembersihan Langsung (Zero-Memory Overhead)**:
   - Menghapus salinan duplikat secara langsung tanpa folder karantina cadangan yang membebani memori penyimpanan.
   - Dilengkapi dialog pratinjau (*rich preview*) dan konfirmasi keamanan sebelum penghapusan.

2. **Filter Pencarian Tipe File**:
   - Filter cepat berdasarkan kategori: **Semua**, **Gambar (JPG, PNG, WEBP, dll.)**, **Video (MP4, MKV, AVI, dll.)**, **Audio (MP3, WAV, AAC, dll.)**, **Dokumen (PDF, DOCX, TXT, dll.)**, **Arsip (ZIP, RAR, 7Z, TAR)**, dan **APK**.
   - Kolom pencarian teks instan secara real-time pada layar hasil.

3. **Metode Pemindaian Berkecepatan Tinggi & Akurat (3-Tier Engine)**:
   - **Hash SHA-256**: Akurasi 100% perbandingan kriptografi bit demi bit.
   - **Hash MD5**: Komputasi cepat dan akurat.
   - **Ukuran + Chunk 4KB (Header/Footer)**: Turbo scan untuk file berukuran sangat besar.
   - **Nama & Ukuran**: Pemindaian metadata instan.

4. **Bandingkan Beberapa Folder**:
   - Deteksi kesamaan file antar dua direktori berbeda (misalnya folder `DCIM` vs `Pictures`, `Download` vs `Documents`).

5. **Pratinjau File Aman Sebelum Hapus**:
   - Pratinjau gambar penuh, detail metadata (lokasi folder, ukuran, tanggal modifikasi, hash).
   - Opsi buka file langsung melalui aplikasi default sistem (*open with*).

6. **Pemilihan Pintar (Smart Selection)**:
   - Simpan Tertua (pertahankan file asli pertama).
   - Simpan Terbaru (pertahankan file salinan terakhir).
   - Pilih Semua Salinan / Batalkan Pilihan.

---

## 🚀 Build APK Otomatis di GitHub Actions

Repositori ini telah dilengkapi dengan workflow CI/CD GitHub Actions di `.github/workflows/build-apk.yml`.

### Cara Mendapatkan APK dari GitHub:
1. **Push ke GitHub**: Setiap kali Anda melakukan `git push` ke branch `main` atau `master`, GitHub Actions akan otomatis memicu build APK.
2. **Jalankan Manual (Workflow Dispatch)**:
   - Buka tab **Actions** di repositori GitHub Anda.
   - Pilih workflow **Build LishClean APK** di sisi kiri.
   - Klik tombol **Run workflow** -> pilih branch `main` -> klik **Run workflow**.
3. **Download APK**:
   - Setelah status build hijau (centang hijau), klik judul build yang selesai.
   - Gulir ke bawah ke bagian **Artifacts**.
   - Unduh **`LishClean-debug-apk`**. Ekstrak file zip tersebut dan instal file `.apk` ke ponsel Android Anda!

---

## 💻 Build APK Secara Lokal

Jika Anda mengkloning repositori ini ke komputer lokal:

### Menggunakan Gradle:
```bash
# Pastikan Java 21 terpasang
# Build Debug APK:
./gradlew assembleDebug

# Atau jika menggunakan Gradle global:
gradle assembleDebug
```

Lokasi file APK yang dihasilkan:
`app/build/outputs/apk/debug/app-debug.apk`

### Menjalankan Unit & Robolectric Tests:
```bash
./gradlew testDebugUnitTest
# atau
gradle testDebugUnitTest
```

---

## 🔒 Izin Penyimpanan Android
- Aplikasi menggunakan izin `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE` untuk Android 10 ke bawah.
- Aplikasi menggunakan `MANAGE_EXTERNAL_STORAGE` (All Files Access) untuk Android 11+ agar dapat memindai seluruh folder penyimpanan dan drive eksternal secara menyeluruh.
