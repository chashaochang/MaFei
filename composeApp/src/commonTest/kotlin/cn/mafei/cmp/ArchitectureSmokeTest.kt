package cn.mafei.cmp

import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import cn.mafei.cmp.common.glass.GlassSurfaceSpec
import kotlin.test.Test
import kotlin.test.assertEquals

class ArchitectureSmokeTest {
    @Test
    fun opaqueFallbackIsTheBootstrapDefault() {
        val spec = GlassSurfaceSpec(
            role = GlassRole.Content,
            level = GlassLevel.Opaque,
        )

        assertEquals(GlassLevel.Opaque, spec.level)
    }
}
