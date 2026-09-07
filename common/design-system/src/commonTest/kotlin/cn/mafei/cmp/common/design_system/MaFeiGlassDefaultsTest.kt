package cn.mafei.cmp.common.design_system

import cn.mafei.cmp.common.glass.GlassLevel
import cn.mafei.cmp.common.glass.GlassRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaFeiGlassDefaultsTest {
    @Test fun transparencyPreferenceOverridesEveryRole() {
        for (role in GlassRole.entries) {
            for (level in GlassLevel.entries) {
                assertEquals(GlassLevel.Opaque, MaFeiGlassDefaults.spec(role, level, true).level)
            }
        }
    }

    @Test fun materialChoiceDoesNotChangeGeometry() {
        for (role in GlassRole.entries) {
            val specs = GlassLevel.entries.map { MaFeiGlassDefaults.spec(role, it) }
            assertEquals(1, specs.map { it.cornerRadiusDp }.distinct().size)
            assertTrue(specs.all { it.cornerRadiusDp > 0f && it.tintAlpha in 0f..1f })
        }
    }

    @Test fun productRolesPreserveExplicitMaterialChoice() {
        for (role in GlassRole.entries) {
            for (level in GlassLevel.entries) {
                assertEquals(role, MaFeiGlassDefaults.spec(role, level).role)
                assertEquals(level, MaFeiGlassDefaults.spec(role, level).level)
            }
        }
    }
}
