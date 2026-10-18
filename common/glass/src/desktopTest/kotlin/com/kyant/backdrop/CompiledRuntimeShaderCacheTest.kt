package com.kyant.backdrop

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CompiledRuntimeShaderCacheTest {
    @Test
    fun equalSourceCompilesOnceAndDifferentSourceNeverAliases() {
        var compilations = 0
        val cache = CompiledRuntimeShaderCache<Any> { compilations++; Any() }
        val first = cache.obtain("source-a")
        assertSame(first, cache.obtain("source-a".toCharArray().concatToString()))
        assertNotSame(first, cache.obtain("source-b"))
        assertEquals(2, compilations)
    }

    @Test
    fun boundedCacheEvictsLeastRecentlyUsedProgram() {
        val cache = CompiledRuntimeShaderCache<Any>(capacity = 2) { Any() }
        val first = cache.obtain("first")
        val second = cache.obtain("second")
        assertSame(first, cache.obtain("first"))
        cache.obtain("third")
        assertEquals(2, cache.size)
        assertSame(first, cache.obtain("first"))
        assertNotSame(second, cache.obtain("second"))
        assertEquals(2, cache.size)
    }

    @Test
    fun failedCompilationDoesNotPoisonLaterRenderingAttempt() {
        var attempts = 0
        val cache = CompiledRuntimeShaderCache<Any> {
            if (++attempts == 1) error("speculative warmup failed")
            Any()
        }
        assertFailsWith<IllegalStateException> { cache.obtain("retry") }
        val result = cache.obtain("retry")
        assertSame(result, cache.obtain("retry"))
        assertEquals(2, attempts)
    }

    @Test
    fun racingWarmupAndDrawCallersCompileResidentProgramOnce() {
        val calls = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(8)
        val cache = CompiledRuntimeShaderCache<Any> {
            calls.incrementAndGet()
            started.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            Any()
        }
        try {
            val requests = List(8) { executor.submit<Any> { cache.obtain("shared-source") } }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            release.countDown()
            val results = requests.map { it.get(10, TimeUnit.SECONDS) }
            results.forEach { assertSame(results.first(), it) }
            assertEquals(1, calls.get())
            assertEquals(1, cache.size)
        } finally {
            release.countDown()
            executor.shutdownNow()
        }
    }
}
