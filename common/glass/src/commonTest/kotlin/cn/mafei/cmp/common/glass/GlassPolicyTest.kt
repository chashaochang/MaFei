package cn.mafei.cmp.common.glass

import kotlin.test.Test
import kotlin.test.assertEquals

class GlassPolicyTest {
    private val supported = GlassCapability(backdrop = true, blur = true)

    @Test
    fun missingCaptureOrBlurAlwaysUsesOpaque() {
        assertEquals(GlassLevel.Opaque, resolveGlassLevel(GlassLevel.Frosted, supported, false))
        assertEquals(GlassLevel.Opaque, resolveGlassLevel(GlassLevel.Frosted, supported.copy(blur = false), true))
    }

    @Test
    fun supportedBlurHonorsTheRequestedMaterial() {
        assertEquals(GlassLevel.Frosted, resolveGlassLevel(GlassLevel.Frosted, supported, true))
        assertEquals(GlassLevel.Opaque, resolveGlassLevel(GlassLevel.Opaque, supported, true))
    }
}
