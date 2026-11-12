package cn.mafei.cmp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlayerWindowStateTest {
    @Test fun updatesReuseTheWindowAndOnlyNewMediaRequestsActivation() {
        val state = PlayerWindowState()
        val owner = Any()
        state.enable(true)
        state.present(owner, "first", "First", {}, {})
        val id = state.request!!.id
        val revision = state.revision

        state.present(owner, "first", "Updated title", {}, {})
        assertEquals(id, state.request!!.id)
        assertEquals(revision, state.revision)
        assertEquals("Updated title", state.request!!.title)

        state.present(owner, "next", "Next", {}, {})
        assertEquals(id, state.request!!.id)
        assertEquals(revision + 1, state.revision)
    }

    @Test fun anOldWindowCannotCloseANewPlaybackAndClosingRunsOnce() {
        val state = PlayerWindowState()
        val owner = Any()
        var closed = 0
        state.enable(true)
        state.present(owner, "first", "First", { closed++ }, {})
        val oldId = state.request!!.id
        state.dismiss(owner)
        state.present(owner, "second", "Second", { closed++ }, {})
        val currentId = state.request!!.id
        assertNotEquals(oldId, currentId)

        state.close(oldId)
        state.failed(oldId)
        assertEquals(currentId, state.request!!.id)
        assertTrue(state.accepts(owner))
        assertEquals(0, closed)
        state.close(currentId)
        state.close(currentId)
        assertNull(state.request)
        assertEquals(1, closed)
    }

    @Test fun openingFailureFallsBackWithoutStoppingPlaybackOrAffectingOtherSessions() {
        val state = PlayerWindowState()
        val owner = Any()
        var closed = false
        state.enable(true)
        state.present(owner, "first", "First", { closed = true }, {})
        state.failed(state.request!!.id)
        assertNull(state.request)
        assertFalse(state.accepts(owner))
        assertFalse(closed)
        state.present(owner, "retry", "Retry", {}, {})
        assertNull(state.request)
        assertTrue(state.accepts(Any()))
    }

    @Test fun releasingAnotherOwnerDoesNotDismissTheCurrentPlayer() {
        val state = PlayerWindowState()
        val owner = Any()
        var closed = false
        state.enable(true)
        state.present(owner, "first", "First", { closed = true }, {})
        state.dismiss(Any())
        assertEquals("first", state.request!!.mediaKey)
        state.dismiss(owner)
        assertNull(state.request)
        assertFalse(closed)
    }

    @Test fun disablingTheHostClosesPlaybackAndANewRequestResetsFullscreen() {
        val state = PlayerWindowState()
        val owner = Any()
        var closed = 0
        assertFalse(state.accepts(owner))
        state.enable(true)
        state.present(owner, "first", "First", { closed++ }, {})
        state.requestFullscreen(true)
        val revision = state.fullscreenRevision
        state.dismiss(owner)
        state.present(owner, "second", "Second", { closed++ }, {})
        assertFalse(state.fullscreenRequested)
        assertTrue(state.fullscreenRevision > revision)
        state.enable(false)
        assertEquals(1, closed)
        assertFalse(state.accepts(owner))
        assertNull(state.request)
    }
}
