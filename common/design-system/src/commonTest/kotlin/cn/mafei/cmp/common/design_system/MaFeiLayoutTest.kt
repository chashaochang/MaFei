package cn.mafei.cmp.common.design_system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaFeiLayoutTest {
    @Test fun navigationUsesWindowNotDeviceOrInput() {
        assertEquals(NavigationPlacement.Bottom, mafeiLayout(390f, 844f).navigation)
        assertEquals(NavigationPlacement.Rail, mafeiLayout(800f, 600f).navigation)
        assertEquals(NavigationPlacement.Sidebar, mafeiLayout(1180f, 780f).navigation)
    }

    @Test fun breakpointEdgesAreDeterministic() {
        assertEquals(NavigationPlacement.Bottom, mafeiLayout(599f, 800f).navigation)
        assertEquals(NavigationPlacement.Rail, mafeiLayout(600f, 800f).navigation)
        assertEquals(NavigationPlacement.Rail, mafeiLayout(999f, 800f).navigation)
        assertEquals(NavigationPlacement.Sidebar, mafeiLayout(1000f, 800f).navigation)
    }

    @Test fun landscapeWithLittleHeightDoesNotWasteSpaceOnHero() {
        val portrait = mafeiLayout(390f, 844f)
        val landscape = mafeiLayout(844f, 390f)
        assertTrue(landscape.heroHeightDp < portrait.heroHeightDp)
        assertEquals(NavigationPlacement.Rail, landscape.navigation)
    }

    @Test fun columnsUseRemainingWidthAfterNavigation() {
        val layout = mafeiLayout(1440f, 900f)
        assertEquals(208f, layout.navigationWidthDp)
        assertEquals(1168f, layout.gridWidthDp)
        assertEquals(6, layout.posterColumns)
        assertTrue(layout.horizontalHero)
    }

    @Test fun largeTextGetsFewerColumnsAndMoreRoom() {
        assertTrue(mafeiLayout(390f, 844f, 2f).posterColumns < mafeiLayout(390f, 844f).posterColumns)
        assertTrue(mafeiLayout(1000f, 800f, 2f).posterColumns < mafeiLayout(1000f, 800f).posterColumns)
        assertTrue(!mafeiLayout(1000f, 800f, 2f).horizontalHero)
    }

    @Test fun narrowWindowNeverProducesZeroColumnsOrNegativeSpace() {
        val layout = mafeiLayout(160f, 200f)
        assertEquals(1, layout.posterColumns)
        assertTrue(layout.gridWidthDp > 0)
        assertTrue(layout.heroHeightDp > 0)
    }
}
