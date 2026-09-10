package cn.mafei.cmp.common.design_system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaFeiLayoutTest {
    @Test fun navigationUsesWindowNotDeviceOrInput() {
        val phone = mafeiLayout(390f, 844f)
        val tablet = mafeiLayout(800f, 600f)
        val desktop = mafeiLayout(1180f, 780f)
        assertEquals(MaFeiLayoutFamily.Phone, phone.family)
        assertEquals(NavigationPlacement.Bottom, phone.navigation)
        assertEquals(MaFeiLayoutFamily.Tablet, tablet.family)
        assertEquals(NavigationPlacement.Rail, tablet.navigation)
        assertEquals(MaFeiLayoutFamily.Desktop, desktop.family)
        assertEquals(NavigationPlacement.Sidebar, desktop.navigation)
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
        assertEquals(1160f, layout.gridWidthDp)
        assertEquals(6, layout.posterColumns)
        assertTrue(layout.horizontalHero)
    }

    @Test fun tabletRotationReevaluatesCurrentWindowAndReflowsDensity() {
        val portrait = mafeiLayout(834f, 1194f)
        val landscape = mafeiLayout(1194f, 834f)

        assertEquals(MaFeiLayoutFamily.Tablet, portrait.family)
        assertEquals(MaFeiLayoutFamily.Desktop, landscape.family)
        assertTrue(landscape.posterColumns > portrait.posterColumns)
    }

    @Test fun ultraWideDesktopCapsContentAndPosterDensity() {
        val layout = mafeiLayout(2560f, 1440f)

        assertEquals(1440f, layout.contentMaxWidthDp)
        assertEquals(1368f, layout.gridWidthDp)
        assertEquals(6, layout.posterColumns)
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

    @Test fun adaptiveWindowCarriesPaneGeometryIntoPagePolicy() {
        val window = mafeiAdaptiveWindow(
            MaFeiWindowEnvironment(
                widthDp = 840f,
                heightDp = 900f,
                occlusions = listOf(MaFeiRectDp(414f, 0f, 426f, 900f)),
                displayRole = MaFeiDisplayRole.Inner,
            ),
        )

        val layout = mafeiLayout(window)

        assertEquals(2, layout.paneCount)
        assertEquals(MaFeiOcclusionAxis.Vertical, layout.occlusionAxis)
        assertEquals(MaFeiDisplayRole.Inner, layout.displayRole)
    }
}
