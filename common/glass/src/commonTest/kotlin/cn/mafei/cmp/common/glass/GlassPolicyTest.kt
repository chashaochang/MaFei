package cn.mafei.cmp.common.glass

import kotlin.test.Test
import kotlin.test.assertEquals

class GlassPolicyTest {
    private val full = GlassCapability(true, true, true, true, true)

    @Test
    fun missingCaptureOrBlurAlwaysUsesOpaque() {
        assertEquals(GlassLevel.Opaque, resolveGlassLevel(GlassLevel.Full, full, false))
        assertEquals(GlassLevel.Opaque, resolveGlassLevel(GlassLevel.Full, full.copy(blur = false), true))
    }

    @Test
    fun shaderSupportAndUserChoiceControlRefraction() {
        assertEquals(GlassLevel.Full, resolveGlassLevel(GlassLevel.Full, full, true))
        assertEquals(GlassLevel.Reduced, resolveGlassLevel(GlassLevel.Full, full.copy(runtimeShader = false), true))
        assertEquals(GlassLevel.Reduced, resolveGlassLevel(GlassLevel.Reduced, full, true))
        assertEquals(GlassLevel.Opaque, resolveGlassLevel(GlassLevel.Opaque, full, true))
    }
}
