package com.example.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object SampleDataGenerator {

    suspend fun generateSampleDuplicates(context: Context): String = withContext(Dispatchers.IO) {
        val baseDir = File(context.getExternalFilesDir(null), "SampleTestDuplicates")
        if (!baseDir.exists()) {
            baseDir.mkdirs()
        }

        val folderA = File(baseDir, "Folder_A_Kamera").apply { mkdirs() }
        val folderB = File(baseDir, "Folder_B_Download").apply { mkdirs() }
        val folderC = File(baseDir, "Folder_C_Cadangan").apply { mkdirs() }
        val hiddenFolder = File(baseDir, ".hidden_storage").apply { mkdirs() }

        // 1. Generate Bitmap image A and duplicate to folder B and C
        val imgFileA = File(folderA, "foto_pantai_sunset.jpg")
        val imgFileB = File(folderB, "foto_pantai_sunset_salinan.jpg")
        val imgFileC = File(folderC, "IMG_20240928_COPY.jpg")
        createSampleImageFile(imgFileA, "PANTAI SUNSET 2024", Color.rgb(249, 115, 22))
        imgFileA.copyTo(imgFileB, overwrite = true)
        imgFileA.copyTo(imgFileC, overwrite = true)

        // 2. Generate another image with duplicate in hidden folder
        val img2A = File(folderA, "desain_vektor_logo.png")
        val img2Hidden = File(hiddenFolder, ".desain_vektor_logo_backup.png")
        createSampleImageFile(img2A, "DUPLISCAN LOGO", Color.rgb(37, 99, 235))
        img2A.copyTo(img2Hidden, overwrite = true)

        // 3. Generate sample text/documents duplicates
        val doc1A = File(folderA, "laporan_keuangan_q3.pdf")
        val doc1B = File(folderB, "laporan_keuangan_q3_download.pdf")
        val contentDoc = "Laporan Keuangan Kuartal 3\nTotal Revenue: Rp 150.000.000\nPengeluaran Operasional: Rp 45.000.000\nStatus: Terverifikasi oleh tim Audit."
        doc1A.writeText(contentDoc)
        doc1A.copyTo(doc1B, overwrite = true)

        // 4. Generate JSON/Code duplicates
        val json1A = File(folderB, "konfigurasi_server.json")
        val json1C = File(folderC, "backup_config_server.json")
        val jsonContent = """
            {
              "appName": "DupliScan Pro",
              "version": "1.0.0",
              "turboMode": true,
              "hashAlgorithm": "SHA-256",
              "cacheDuration": 3600
            }
        """.trimIndent()
        json1A.writeText(jsonContent)
        json1A.copyTo(json1C, overwrite = true)

        // 5. Generate Large Chunk test file (128 KB with identical content)
        val bigFile1 = File(folderA, "arsip_data_besar.dat")
        val bigFile2 = File(folderB, "arsip_data_besar_duplikat.dat")
        val buffer = ByteArray(128 * 1024) { (it % 255).toByte() }
        bigFile1.writeBytes(buffer)
        bigFile1.copyTo(bigFile2, overwrite = true)

        baseDir.absolutePath
    }

    private fun createSampleImageFile(file: File, text: String, bgColor: Int) {
        val width = 400
        val height = 300
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(bgColor)

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 28f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(text, width / 2f, height / 2f, paint)

        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
    }
}
