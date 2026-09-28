package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.DuplicateRepository
import com.example.model.DuplicateItem
import com.example.model.FileCategory
import com.example.model.ScanMode
import com.example.model.StorageType
import com.example.scanner.DuplicateScannerEngine
import com.example.scanner.SampleDataGenerator
import com.example.scanner.ScanProgressEvent
import com.example.scanner.StorageDetector
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun readStringFromContext_shouldReturnLishClean() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("LishClean", appName)
    }

    @Test
    fun fileCategory_detectsExtensionsAccurately() {
        assertEquals(FileCategory.IMAGES, FileCategory.fromExtension("jpg"))
        assertEquals(FileCategory.IMAGES, FileCategory.fromExtension("png"))
        assertEquals(FileCategory.VIDEOS, FileCategory.fromExtension("mp4"))
        assertEquals(FileCategory.AUDIO, FileCategory.fromExtension("mp3"))
        assertEquals(FileCategory.DOCUMENTS, FileCategory.fromExtension("pdf"))
        assertEquals(FileCategory.ARCHIVES, FileCategory.fromExtension("zip"))
        assertEquals(FileCategory.APKS, FileCategory.fromExtension("apk"))
    }

    @Test
    fun storageDetector_detectsInternalVolumes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val volumes = StorageDetector.detectStorageVolumes(context)
        assertTrue("Storage volumes should not be empty", volumes.isNotEmpty())
        val internalVol = volumes.find { !it.isRemovable }
        assertNotNull("Primary internal storage should be detected", internalVol)
    }

    @Test
    fun dotFolderScan_scansFilesInFoldersStartingWithDot() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val baseDir = File(context.filesDir, "test_dot_folders")
        baseDir.mkdirs()

        // Create folder starting with dot
        val dotFolder = File(baseDir, ".namaFolder")
        dotFolder.mkdirs()

        val normalFolder = File(baseDir, "FolderBiasa")
        normalFolder.mkdirs()

        // Write identical file in .namaFolder and normalFolder
        val fileInDot = File(dotFolder, "dokumen_penting.pdf")
        val fileInNormal = File(normalFolder, "dokumen_penting_copy.pdf")
        val content = "%PDF-1.4 Identical duplicate test inside .namaFolder LishClean %%EOF"
        fileInDot.writeText(content)
        fileInNormal.writeText(content)

        // Scan base directory
        val completedEvent = DuplicateScannerEngine.scanFolders(
            targetFolders = listOf(baseDir.absolutePath),
            scanMode = ScanMode.HASH_SHA256,
            includeHiddenFiles = true
        ).last()

        assertTrue(completedEvent is ScanProgressEvent.Completed)
        val completed = completedEvent as ScanProgressEvent.Completed
        assertTrue("Should detect duplicate group containing file from .namaFolder", completed.groups.isNotEmpty())

        val foundPaths = completed.groups.flatMap { it.items }.map { it.path }
        assertTrue("Should scan and find file inside .namaFolder", foundPaths.contains(fileInDot.absolutePath))
        assertTrue("Should scan and find copy in normalFolder", foundPaths.contains(fileInNormal.absolutePath))
    }

    @Test
    fun dualStorageScan_scansInternalAndExternalSimultaneously() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // 1. Generate internal sample duplicates
        val internalPath = SampleDataGenerator.generateSampleDuplicates(context)

        // 2. Generate external (SD Card) sample duplicates
        val externalPath = StorageDetector.createSimulatedExternalStorage(context)

        val internalDir = File(internalPath)
        val externalDir = File(externalPath)
        assertTrue("Internal test directory should exist", internalDir.exists())
        assertTrue("External test directory should exist", externalDir.exists())

        // 3. Scan BOTH internal and external simultaneously
        val completedEvent = DuplicateScannerEngine.scanFolders(
            targetFolders = listOf(internalPath, externalPath),
            scanMode = ScanMode.HASH_SHA256,
            includeHiddenFiles = true,
            externalStoragePaths = listOf(externalPath)
        ).last()

        assertTrue(completedEvent is ScanProgressEvent.Completed)
        val completed = completedEvent as ScanProgressEvent.Completed
        assertTrue("Should detect duplicate groups across storages", completed.groups.isNotEmpty())

        val allItems = completed.groups.flatMap { it.items }
        val hasExternalItem = allItems.any { it.isExternalStorage }
        val hasInternalItem = allItems.any { !it.isExternalStorage }

        assertTrue("Should contain items flagged as internal memory", hasInternalItem)
        assertTrue("Should contain items flagged as external memory (SD Card)", hasExternalItem)
    }

    @Test
    fun directDeletionWorkflow_shouldFreeSpaceDirectly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = DuplicateRepository(context)

        // Create test duplicate file
        val testDir = File(context.filesDir, "test_duplicates")
        testDir.mkdirs()
        val duplicateFile = File(testDir, "test_duplicate.txt")
        duplicateFile.writeText("Duplicate content in LishClean to be directly deleted")

        val duplicateItem = DuplicateItem(
            file = duplicateFile,
            isSelected = true
        )

        // Directly delete duplicate
        val deletionResult = repo.deleteFilesDirectly(listOf(duplicateItem))
        assertEquals(1, deletionResult.successCount)
        assertFalse("Duplicate file should have been deleted directly", duplicateFile.exists())
    }
}
