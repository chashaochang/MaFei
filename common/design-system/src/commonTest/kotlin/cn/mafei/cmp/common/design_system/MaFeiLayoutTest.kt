package cn.mafei.cmp.common.design_system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaFeiLayoutTest {
    @Test fun navigationUsesWindowNotDeviceOrInput() {
        val phone = mafeiLayout(390f, 844f)
        val tablet = mafeiLayout(800f, 600f)
        val desktop = mafeiLayout(1600f, 1000f)
        assertEquals(MaFeiLayoutFamily.Phone, phone.family)
        assertEquals(NavigationPlacement.Bottom, phone.navigation)
        assertEquals(MaFeiLayoutFamily.Tablet, tablet.family)
        assertEquals(NavigationPlacement.Drawer, tablet.navigation)
        assertEquals(MaFeiLayoutFamily.Desktop, desktop.family)
        assertEquals(NavigationPlacement.Sidebar, desktop.navigation)
    }

    @Test fun navigationModeOwnsTheFormFactorBoundary() {
        assertEquals(MaFeiNavigationMode.PhoneBottom, mafeiLayout(390f, 844f).navigationMode)
        assertEquals(MaFeiNavigationMode.TabletDrawer, mafeiLayout(800f, 600f).navigationMode)
        assertEquals(MaFeiNavigationMode.TabletSidebar, mafeiLayout(1200f, 800f).navigationMode)
        assertEquals(MaFeiNavigationMode.DesktopSidebar, mafeiLayout(1600f, 1000f).navigationMode)
        assertEquals(MaFeiNavigationMode.CompactRail, mafeiLayout(
            mafeiAdaptiveWindow(
                MaFeiWindowEnvironment(
                    widthDp = 900f,
                    heightDp = 1_000f,
                    foldPosture = MaFeiFoldPosture.Flat,
                    displayRole = MaFeiDisplayRole.Inner,
                ),
            ),
        ).navigationMode)
    }

    @Test fun breakpointEdgesAreDeterministic() {
        assertEquals(NavigationPlacement.Bottom, mafeiLayout(599f, 800f).navigation)
        assertEquals(NavigationPlacement.Drawer, mafeiLayout(600f, 800f).navigation)
        assertEquals(NavigationPlacement.Sidebar, mafeiLayout(999f, 800f).navigation)
        assertEquals(NavigationPlacement.Sidebar, mafeiLayout(1200f, 800f).navigation)
        assertEquals(NavigationPlacement.Drawer, mafeiLayout(1000f, 1400f).navigation)
    }

    @Test fun landscapeWithLittleHeightDoesNotWasteSpaceOnHero() {
        val portrait = mafeiLayout(390f, 844f)
        val landscape = mafeiLayout(844f, 390f)
        assertTrue(landscape.heroHeightDp < portrait.heroHeightDp)
        assertEquals(NavigationPlacement.Sidebar, landscape.navigation)
    }

    @Test fun columnsUseRemainingWidthAfterNavigation() {
        val layout = mafeiLayout(1440f, 900f)
        // 侧栏贴窗口边缘通栏：导航宽度 == 侧栏材质宽度（没有悬浮边距）。
        assertEquals(240f, layout.navigationWidthDp)
        assertEquals(1136f, layout.gridWidthDp)
        assertTrue(layout.horizontalHero)
    }

    @Test fun largePagesUseTheSharedHorizontalInsetToken() {
        assertEquals(MaFeiLargePageHorizontalInsetDp.value, mafeiLayout(800f, 600f).pagePaddingDp)
        assertEquals(MaFeiLargePageHorizontalInsetDp.value, mafeiLayout(1600f, 1000f).pagePaddingDp)
        assertEquals(MaFeiPhonePageHorizontalInsetDp.value, mafeiLayout(390f, 844f).pagePaddingDp)
    }

    @Test fun tabletRotationReevaluatesCurrentWindowAndReflowsDensity() {
        val portrait = mafeiLayout(834f, 1194f)
        val landscape = mafeiLayout(1194f, 834f)

        assertEquals(MaFeiLayoutFamily.Tablet, portrait.family)
        assertEquals(MaFeiLayoutFamily.Tablet, landscape.family)
        assertEquals(NavigationPlacement.Drawer, portrait.navigation)
        assertEquals(NavigationPlacement.Sidebar, landscape.navigation)
        assertTrue(landscape.navigationWidthDp > portrait.navigationWidthDp)
    }

    @Test fun broadFoldingInnerScreenUsesTrailingCompactRail() {
        val broadFold = mafeiLayout(
            mafeiAdaptiveWindow(
                MaFeiWindowEnvironment(
                    widthDp = 900f,
                    heightDp = 1_000f,
                    foldPosture = MaFeiFoldPosture.Flat,
                    displayRole = MaFeiDisplayRole.Inner,
                ),
            ),
        )

        assertEquals(NavigationPlacement.Rail, broadFold.navigation)
        assertEquals(NavigationEdge.Trailing, broadFold.navigationEdge)
        assertEquals(80f, broadFold.navigationWidthDp)
    }

    @Test fun balancedTabletAndSmallFoldDoNotClaimCompactRail() {
        val tablet = mafeiLayout(
            mafeiAdaptiveWindow(
                MaFeiWindowEnvironment(900f, 1_000f),
            ),
        )
        val smallFold = mafeiLayout(
            mafeiAdaptiveWindow(
                MaFeiWindowEnvironment(
                    widthDp = 430f,
                    heightDp = 860f,
                    foldPosture = MaFeiFoldPosture.Flat,
                    displayRole = MaFeiDisplayRole.Inner,
                ),
            ),
        )

        assertEquals(NavigationPlacement.Drawer, tablet.navigation)
        assertEquals(NavigationPlacement.Bottom, smallFold.navigation)
    }

    @Test fun ultraWideDesktopAddsPosterColumnsInsteadOfStretchingCards() {
        val layout = mafeiLayout(2560f, 1440f)

        assertEquals(2320f, layout.contentMaxWidthDp)
        assertEquals(2256f, layout.gridWidthDp)
        assertEquals(12, layout.posterColumns)
        assertTrue(mafeiLayout(1728f, 1000f).posterColumns < layout.posterColumns)
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
