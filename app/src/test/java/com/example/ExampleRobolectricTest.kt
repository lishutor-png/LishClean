package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.DuplicateRepository
import com.example.model.DuplicateItem
import com.example.model.FileCategory
import com.example.model.ScanMode
import com.example.scanner.DuplicateScannerEngine
import com.example.scanner.ScanProgressEvent
import com.example.scanner.SampleDataGenerator
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun sampleDataGenerator_createsDuplicateFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val path = SampleDataGenerator.generateSampleDuplicates(context)
        val dir = File(path)
        assertTrue("Sample directory should exist", dir.exists())

        // Run DuplicateScannerEngine on generated samples
        val completedEvent = DuplicateScannerEngine.scanFolders(
            targetFolders = listOf(path),
            scanMode = ScanMode.HASH_SHA256,
            includeHiddenFiles = true
        ).last()

        assertTrue(completedEvent is ScanProgressEvent.Completed)
        val completed = completedEvent as ScanProgressEvent.Completed
        assertTrue("Should detect duplicate groups in sample data", completed.groups.isNotEmpty())
        assertTrue("Total duplicate files found should be > 0", completed.stats.duplicateFilesCount > 0)
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
