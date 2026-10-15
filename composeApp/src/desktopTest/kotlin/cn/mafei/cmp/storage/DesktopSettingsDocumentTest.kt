package cn.mafei.cmp.storage

import cn.mafei.cmp.core.storage_api.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.*

class DesktopSettingsDocumentTest {
    @Test fun atomicFileReopensWholeEditsAndRejectsCorruptData() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("mafei-settings-test-").toFile().canonicalFile
        try {
            val store = DocumentSettingsStorage(DesktopSettingsDocument(directory))
            store.edit { putString("a", "one"); putBytes("b", byteArrayOf(2, 3)) }
            val reopened = DocumentSettingsStorage(DesktopSettingsDocument(directory))
            assertEquals("one", reopened.getString("a")); assertContentEquals(byteArrayOf(2, 3), reopened.getBytes("b"))
            assertEquals(listOf("settings.json"), directory.list()!!.toList())
            File(directory, "settings.json").writeText("invalid")
            assertFailsWith<StorageCorruptedException> { DocumentSettingsStorage(DesktopSettingsDocument(directory)).listKeys() }
        } finally { directory.deleteRecursively() }
    }

    @Test fun symlinkTargetIsRejectedWithoutTouchingItsDestination() = runBlocking<Unit> {
        val directory = Files.createTempDirectory("mafei-settings-link-test-").toFile().canonicalFile
        try {
            val other = File(directory, "not-settings.txt").apply { writeText("keep") }
            Files.createSymbolicLink(File(directory, "settings.json").toPath(), other.toPath())
            val store = DocumentSettingsStorage(DesktopSettingsDocument(directory))
            assertFailsWith<StorageUnavailableException> { store.putString("a", "new") }
            assertEquals("keep", other.readText())
        } finally { directory.deleteRecursively() }
    }
}
