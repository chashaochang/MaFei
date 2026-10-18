package cn.mafei.cmp.common.glass

import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.SkikoRuntimeShader
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GlassShaderProgramWarmupTest {
    @Test
    fun callerCancellationAndRepeatedEntryDoNotRestartProcessWork() = runBlocking {
        val attempts = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val warmup = ShaderProgramWarmup(listOf("one" to "source-one", "two" to "source-two")) {
            attempts.incrementAndGet()
            started.countDown()
            check(release.await(10, TimeUnit.SECONDS))
            true
        }
        try {
            val firstCaller = async(start = CoroutineStart.UNDISPATCHED) { warmup.await() }
            assertTrue(started.await(10, TimeUnit.SECONDS))
            firstCaller.cancelAndJoin()
            val nextCaller = async(start = CoroutineStart.UNDISPATCHED) { warmup.await() }
            release.countDown()
            val result = nextCaller.await()
            assertEquals(GlassShaderProgramWarmupResult(2, 0, emptyList()), result)
            assertSame(result, warmup.await())
            assertEquals(2, attempts.get())
        } finally {
            release.countDown()
        }
    }

    @Test
    fun unsupportedPlatformsAndIndividualFailuresAreReportedWithoutDowngrade() = runBlocking {
        val unsupported = ShaderProgramWarmup(listOf("android" to "source")) { false }
        assertEquals(GlassShaderProgramWarmupResult(0, 1, emptyList()), unsupported.await())
        val partial = ShaderProgramWarmup(listOf(
            "exception" to "bad-source",
            "native-error" to "native-bad-source",
            "good" to "good-source",
        )) {
            if (it == "bad-source") error("compile failed")
            if (it == "native-bad-source") throw Error("Native SKSL compilation failed")
            true
        }
        assertEquals(GlassShaderProgramWarmupResult(1, 0, listOf("exception", "native-error")), partial.await())
    }

    @Test
    fun realGlassProgramsCompileAndSubsequentInstancesKeepIndependentBuilders() = runBlocking {
        val result = prewarmGlassShaderPrograms()
        assertEquals(6, result.readyPrograms)
        assertEquals(0, result.unsupportedPrograms)
        assertTrue(result.failedPrograms.isEmpty(), result.failedPrograms.toString())
        assertSame(result, prewarmGlassShaderPrograms())
        for ((_, source) in glassShaderPrograms) {
            val first = RuntimeShader(source) as SkikoRuntimeShader
            val second = RuntimeShader(source) as SkikoRuntimeShader
            try {
                assertSame(first.compiledProgram, second.compiledProgram)
                assertNotSame(first.shader, second.shader)
            } finally {
                first.shader.close()
                second.shader.close()
            }
        }
    }
}
