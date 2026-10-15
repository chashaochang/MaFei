package cn.mafei.cmp.common.design_system

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MaFeiAdaptiveEnvironmentTest {
    @Test fun straightAndWideSlabPhonesRemainSinglePane() {
        val normal = mafeiAdaptiveWindow(MaFeiWindowEnvironment(390f, 844f))
        val wideSlab = mafeiAdaptiveWindow(MaFeiWindowEnvironment(430f, 724f))

        assertEquals(MaFeiWidthClass.Compact, normal.widthClass)
        assertEquals(MaFeiWindowAspect.Tall, normal.aspect)
        assertEquals(1, wideSlab.paneCount)
        assertFalse(wideSlab.hasPhysicalSeparation)
    }

    @Test fun smallFoldOuterAndInnerScreensAreDifferentWindowsNotDeviceFlags() {
        val outer = mafeiAdaptiveWindow(
            MaFeiWindowEnvironment(320f, 340f, displayRole = MaFeiDisplayRole.Outer),
        )
        val inner = mafeiAdaptiveWindow(
            MaFeiWindowEnvironment(430f, 860f, displayRole = MaFeiDisplayRole.Inner),
        )

        assertEquals(MaFeiWindowAspect.Balanced, outer.aspect)
        assertEquals(MaFeiWindowAspect.Tall, inner.aspect)
        assertEquals(MaFeiDisplayRole.Outer, outer.displayRole)
        assertEquals(MaFeiDisplayRole.Inner, inner.displayRole)
    }

    @Test fun bookAndDualScreenFoldablesProduceTwoSafePanes() {
        val window = mafeiAdaptiveWindow(
            MaFeiWindowEnvironment(
                widthDp = 840f,
                heightDp = 900f,
                occlusions = listOf(MaFeiRectDp(414f, 0f, 426f, 900f)),
                foldPosture = MaFeiFoldPosture.Flat,
                displayRole = MaFeiDisplayRole.Inner,
            ),
        )

        assertEquals(MaFeiOcclusionAxis.Vertical, window.occlusionAxis)
        assertEquals(2, window.paneCount)
        assertEquals(414f, window.panes.first().width)
        assertEquals(414f, window.panes.last().width)
    }

    @Test fun triFoldWithTwoHingesProducesThreeSafePanes() {
        val window = mafeiAdaptiveWindow(
            MaFeiWindowEnvironment(
                widthDp = 1_220f,
                heightDp = 900f,
                occlusions = listOf(
                    MaFeiRectDp(398f, 0f, 410f, 900f),
                    MaFeiRectDp(810f, 0f, 822f, 900f),
                ),
                displayRole = MaFeiDisplayRole.Inner,
            ),
        )

        assertEquals(MaFeiWidthClass.Large, window.widthClass)
        assertEquals(3, window.paneCount)
        assertTrue(window.panes.all { it.width > 380f })
    }

    @Test fun tabletopPostureProducesTopAndBottomPanes() {
        val window = mafeiAdaptiveWindow(
            MaFeiWindowEnvironment(
                widthDp = 700f,
                heightDp = 820f,
                occlusions = listOf(MaFeiRectDp(0f, 402f, 700f, 418f)),
                foldPosture = MaFeiFoldPosture.HalfOpened,
            ),
        )

        assertEquals(MaFeiOcclusionAxis.Horizontal, window.occlusionAxis)
        assertEquals(2, window.paneCount)
        assertEquals(402f, window.panes.first().height)
        assertEquals(402f, window.panes.last().height)
    }

    @Test fun tabletRotationAndDesktopResizeReclassifyWithoutLosingInputs() {
        val tabletPortrait = mafeiAdaptiveWindow(MaFeiWindowEnvironment(834f, 1_194f, fontScale = 1.2f))
        val tabletLandscape = mafeiAdaptiveWindow(MaFeiWindowEnvironment(1_194f, 834f, fontScale = 1.2f))
        val desktopNarrow = mafeiAdaptiveWindow(MaFeiWindowEnvironment(520f, 760f))
        val desktopWide = mafeiAdaptiveWindow(MaFeiWindowEnvironment(1_440f, 900f))

        assertEquals(MaFeiWidthClass.Medium, tabletPortrait.widthClass)
        assertEquals(MaFeiWidthClass.Expanded, tabletLandscape.widthClass)
        assertEquals(1.2f, tabletLandscape.fontScale)
        assertEquals(MaFeiWidthClass.Compact, desktopNarrow.widthClass)
        assertEquals(MaFeiWidthClass.Large, desktopWide.widthClass)
    }

    @Test fun safeInsetsAndInvalidOcclusionsCannotCreateNegativePanes() {
        val window = mafeiAdaptiveWindow(
            MaFeiWindowEnvironment(
                widthDp = 390f,
                heightDp = 844f,
                safeInsets = MaFeiInsetsDp(left = 12f, top = 24f, right = 12f, bottom = 34f),
                occlusions = listOf(
                    MaFeiRectDp(-40f, -20f, -5f, 900f),
                    MaFeiRectDp(100f, 100f, 100f, 200f),
                ),
            ),
        )

        assertEquals(MaFeiRectDp(12f, 24f, 378f, 810f), window.contentBounds)
        assertEquals(1, window.paneCount)
        assertTrue(window.panes.single().width > 0f)
        assertTrue(window.panes.single().height > 0f)
    }
}
