package cn.mafei.cmp.storage

import cn.mafei.cmp.core.storage_api.*
import kotlinx.coroutines.*
import kotlin.test.*

class DocumentSettingsStorageTest {
    @Test fun editPublishesOneWholeDocumentAndReopens() = runBlocking {
        val backend = MemoryDocument(); val store = DocumentSettingsStorage(backend)
        store.edit { putString("server/id", "synthetic"); putBytes("snapshot", byteArrayOf(1, 2)); putString("server/id", "last") }
        assertEquals(1, backend.writes)
        val reopened = DocumentSettingsStorage(backend)
        assertEquals("last", reopened.getString("server/id"))
        assertContentEquals(byteArrayOf(1, 2), reopened.getBytes("snapshot"))
        assertEquals(listOf("server/id", "snapshot"), reopened.listKeys())
    }

    @Test fun throwingEditorDoesNotPartiallyApplyOrRemainUsable() = runBlocking {
        val backend = MemoryDocument(); val store = DocumentSettingsStorage(backend)
        store.putString("a", "old")
        var escaped: StorageEditor? = null
        val failure = IllegalStateException("caller failure")
        val caught = assertFailsWith<IllegalStateException> {
            store.edit { escaped = this; putString("a", "new"); putString("b", "new"); throw failure }
        }
        // JVM coroutine stacktrace recovery may copy exceptions across dispatchers.
        assertTrue(generateSequence<Throwable>(caught) { it.cause }.any { it === failure })
        assertFailsWith<IllegalStateException> { escaped!!.putString("outside", "bad") }
        assertEquals("old", store.getString("a")); assertFalse(store.contains("b")); assertEquals(1, backend.writes)
        assertEquals("old", DocumentSettingsStorage(backend).getString("a"))
    }

    @Test fun failedWritePreservesMirrorAndReopenedDocumentAndRedactsCause() = runBlocking {
        val backend = MemoryDocument(); val store = DocumentSettingsStorage(backend)
        store.putString("old", "kept")
        backend.failWrite = true
        val failure = assertFailsWith<StorageUnavailableException> { store.edit { remove("old"); putString("new", "proposed") } }
        assertSafeBackendFailure(failure)
        assertEquals("kept", store.getString("old")); assertNull(store.getString("new"))
        assertEquals("kept", DocumentSettingsStorage(backend).getString("old"))
    }

    @Test fun corruptDocumentFailsClosedAndNeverGetsOverwrittenByAnEmptyFallback() = runBlocking {
        for (bytes in listOf(ByteArray(0), byteArrayOf(-1), "{".encodeToByteArray(),
            "{\"version\":2,\"entries\":[]}".encodeToByteArray(),
            "{\"version\":1,\"entries\":[],\"unknown\":1}".encodeToByteArray(),
            "{\"version\":1,\"entries\":[{\"key\":\"a\",\"kind\":\"bytes\",\"value\":\"!\"}]}".encodeToByteArray(),
            "{\"version\":1,\"entries\":[{\"key\":\"a\",\"kind\":\"string\",\"value\":\"1\"},{\"key\":\"a\",\"kind\":\"string\",\"value\":\"2\"}]}".encodeToByteArray())) {
            val backend = MemoryDocument().apply { document = bytes }
            val store = DocumentSettingsStorage(backend)
            assertFailsWith<StorageCorruptedException> { store.listKeys() }
            assertFailsWith<StorageCorruptedException> { store.putString("must-not-repair", "value") }
            assertEquals(0, backend.writes)
        }
    }

    @Test fun byteArraysAreCopiedAtWriteEditReadAndBackendBoundaries() = runBlocking {
        val backend = MemoryDocument(); val store = DocumentSettingsStorage(backend)
        val bytes = byteArrayOf(1, 2, 3)
        store.putBytes("bytes", bytes); bytes.fill(0)
        assertContentEquals(byteArrayOf(1, 2, 3), store.getBytes("bytes"))
        store.getBytes("bytes")!!.fill(0)
        assertContentEquals(byteArrayOf(1, 2, 3), store.getBytes("bytes"))
        val edited = byteArrayOf(4, 5)
        store.edit { putBytes("edited", edited); edited.fill(0) }
        assertContentEquals(byteArrayOf(4, 5), store.getBytes("edited"))
        backend.lastArgument!!.fill(0)
        assertContentEquals(byteArrayOf(4, 5), DocumentSettingsStorage(backend).getBytes("edited"))
    }

    @Test fun deletionEnumerationAndTypeReplacementFollowContract() = runBlocking {
        val backend = MemoryDocument(); val store = DocumentSettingsStorage(backend)
        assertFalse(store.remove("absent")); assertEquals(0, backend.writes)
        store.edit { putString("ab/key", "string"); putBytes("a/key", byteArrayOf(1)); putString("z", "last") }
        assertEquals(listOf("a/key"), store.listKeys("a/"))
        assertNull(store.getString("a/key")); assertNull(store.getBytes("ab/key"))
        store.putString("a/key", "retyped"); assertNull(store.getBytes("a/key"))
        assertTrue(store.remove("a/key")); assertFalse(store.remove("a/key"))
        assertEquals(listOf("ab/key", "z"), DocumentSettingsStorage(backend).listKeys())
    }

    @Test fun cancellationBeforeAdmissionAndBeforeCommitDoNotWrite() = runBlocking {
        val backend = MemoryDocument(); val store = DocumentSettingsStorage(backend)
        val cancelled = Job().apply { cancel() }
        assertFailsWith<CancellationException> { withContext(cancelled) { store.putString("a", "no") } }
        assertEquals(0, backend.reads); assertEquals(0, backend.writes)
        store.putString("a", "old")
        val job = Job()
        assertFailsWith<CancellationException> { withContext(job) { store.edit { putString("a", "new"); job.cancel() } } }
        assertEquals("old", store.getString("a")); assertEquals(1, backend.writes)
    }

    @Test fun concurrentEditsSerializeWithoutLosingDistinctKeys() = runBlocking {
        val backend = MemoryDocument(); val store = DocumentSettingsStorage(backend)
        coroutineScope { repeat(40) { launch(Dispatchers.Default) { store.putString("key-$it", "$it") } } }
        assertEquals(40, store.listKeys().size); assertEquals(40, backend.writes)
    }

    @Test fun malformedInputAndQuotaFailuresNeverCommit() = runBlocking {
        val backend = MemoryDocument(); val store = DocumentSettingsStorage(backend)
        assertFailsWith<IllegalArgumentException> { store.putString("", "value") }
        assertFailsWith<IllegalArgumentException> { store.putString("nul\u0000key", "value") }
        assertFailsWith<IllegalArgumentException> { store.putString("a", "\uD800") }
        assertFailsWith<StorageQuotaExceededException> { store.putBytes("big", ByteArray(2 * 1024 * 1024 + 1)) }
        assertFailsWith<StorageQuotaExceededException> { store.edit { repeat(4097) { putString("key-$it", "value") } } }
        assertEquals(0, backend.writes); assertEquals(emptyList(), store.listKeys())
    }

    @Test fun unavailableReadIsNotAnAbsentDocument() = runBlocking {
        val backend = MemoryDocument().apply { failRead = true }
        val failure = assertFailsWith<StorageUnavailableException> { DocumentSettingsStorage(backend).contains("a") }
        assertSafeBackendFailure(failure); assertEquals(0, backend.writes)
    }

    private fun assertSafeBackendFailure(failure: Throwable) {
        // Recovery may attach another sanitized StorageUnavailableException,
        // but must never resurrect the raw platform exception or its detail.
        generateSequence(failure) { it.cause }.forEach {
            assertTrue(it is StorageUnavailableException)
            assertFalse(it.message.orEmpty().contains("sensitive"))
        }
    }
}

private class MemoryDocument : SettingsDocumentBackend {
    var document: ByteArray? = null
    var lastArgument: ByteArray? = null
    var failWrite = false
    var failRead = false
    var writes = 0
    var reads = 0
    override fun read(): ByteArray? {
        reads++
        if (failRead) throw IllegalStateException("sensitive backend detail")
        return document
    }
    override fun replace(document: ByteArray) {
        if (failWrite) throw IllegalStateException("sensitive backend detail")
        writes++; lastArgument = document
        this.document = document.copyOf()
    }
}
