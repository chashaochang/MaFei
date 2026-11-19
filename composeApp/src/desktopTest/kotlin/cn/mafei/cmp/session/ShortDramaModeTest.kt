package cn.mafei.cmp.session

import cn.mafei.cmp.ShortDramaScrub
import cn.mafei.cmp.shortDramaEpisodeGroups
import cn.mafei.cmp.shortDramaMode
import cn.mafei.cmp.shortDramaSeekPosition
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShortDramaModeTest {
    @Test
    fun workOverrideWinsAndRememberedLibrarySurvivesRename() = runBlocking {
        val preferences = ShortDramaPreferences(MemorySettings())
        val scope = "server-a:viewer-a"
        assertTrue(shortDramaMode(null, null, " 短剧 "))
        assertFalse(shortDramaMode(null, null, "精选短剧"))
        preferences.save(scope, library = true, "library-id", true)
        preferences.save(scope, library = false, "work-id", false)
        suspend fun mode() = shortDramaMode(
            preferences.load(scope, library = false, "work-id"),
            preferences.load(scope, library = true, "library-id"),
            "已改名",
        )
        assertFalse(mode())
        preferences.save(scope, library = false, "work-id", null)
        assertTrue(mode())
        assertNull(preferences.load("server-a:viewer-b", library = true, "library-id"))
        assertNull(preferences.load("server-b:viewer-a", library = true, "library-id"))
        assertNull(preferences.load(scope, library = false, "library-id"))
    }

    @Test
    fun lastGroupContainsExactlyEpisodes151Through170() {
        val group = shortDramaEpisodeGroups(170).last()
        assertEquals(151, group.first + 1)
        assertEquals(170, group.last + 1)
        assertEquals(20, group.count())
    }

    @Test
    fun scrubCancelsWithoutSeekingAndCommitsOnlyTheFinalTarget() {
        val scrub = ShortDramaScrub()
        scrub.moveTo(shortDramaSeekPosition(.5f, 100_000))
        assertNull(scrub.finish(commit = false))
        scrub.moveTo(shortDramaSeekPosition(.2f, 100_000))
        scrub.moveTo(shortDramaSeekPosition(.75f, 100_000))
        assertEquals(75_000L, scrub.finish(commit = true))
        assertNull(scrub.finish(commit = true))
    }
}
