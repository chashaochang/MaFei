package cn.mafei.cmp.common.glass

import cn.mafei.cmp.common.glass.internal.InteractiveHighlightShaderString
import cn.mafei.cmp.common.glass.internal.VerticalProgressiveBlurShaderString
import com.kyant.backdrop.internal.AmbientHighlightShaderString
import com.kyant.backdrop.internal.DefaultHighlightShaderString
import com.kyant.backdrop.internal.RoundedRectRefractionShaderString
import com.kyant.backdrop.internal.RoundedRectRefractionWithDispersionShaderString
import com.kyant.backdrop.prewarmRuntimeShaderProgram
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.yield

/** CPU RuntimeEffect availability, not GPU driver compilation or frame-time evidence. */
data class GlassShaderProgramWarmupResult(
    val readyPrograms: Int,
    val unsupportedPrograms: Int,
    val failedPrograms: List<String>,
)

internal val glassShaderPrograms = listOf(
    "lens" to RoundedRectRefractionShaderString,
    "dispersion" to RoundedRectRefractionWithDispersionShaderString,
    "default-highlight" to DefaultHighlightShaderString,
    "ambient-highlight" to AmbientHighlightShaderString,
    "interactive-highlight" to InteractiveHighlightShaderString,
    "progressive-blur" to VerticalProgressiveBlurShaderString,
)

private val processShaderWarmup = ShaderProgramWarmup(glassShaderPrograms, ::prewarmRuntimeShaderProgram)

/**
 * Call after the first visible frame, when the startup/entry flow is idle.
 * This bounded CPU job runs once per process on Dispatchers.Default. Re-entering
 * the shell or cancelling one caller does not restart/cancel the shared work.
 *
 * OHOS Skia, iOS and Desktop retain immutable RuntimeEffects; consumers still
 * create separate mutable builders. Android is skipped because its public API
 * cannot expose a reusable immutable program independently of mutable uniforms.
 * No uniforms, backdrop, style, visibility or material parameters are changed.
 * Final GPU pipelines remain lazy; device traces are needed to assess their cost.
 */
suspend fun prewarmGlassShaderPrograms(): GlassShaderProgramWarmupResult = processShaderWarmup.await()

internal class ShaderProgramWarmup(
    private val programs: List<Pair<String, String>>,
    private val prepare: (String) -> Boolean,
) {
    // A process-owned, finite six-program job, not a page/session-owned animation.
    private val request: Deferred<GlassShaderProgramWarmupResult> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("MaFeiShaderProgramWarmup")).async {
            var ready = 0
            var unsupported = 0
            val failed = mutableListOf<String>()
            for ((name, source) in programs) {
                yield()
                try {
                    if (prepare(source)) ready++ else unsupported++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Throwable) {
                    // Warmup is speculative. The normal rendering path retains its
                    // existing failure handling; this does not latch a downgrade.
                    // Skiko Native reports SKSL compilation errors with kotlin.Error.
                    failed += name
                }
            }
            GlassShaderProgramWarmupResult(ready, unsupported, failed.toList())
        }
    }

    suspend fun await(): GlassShaderProgramWarmupResult = request.await()
}
