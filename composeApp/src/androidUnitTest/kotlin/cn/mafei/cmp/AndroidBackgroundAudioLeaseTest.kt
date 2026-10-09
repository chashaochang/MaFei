package cn.mafei.cmp

import cn.mafei.cmp.player.api.*
import kotlin.test.*

class AndroidBackgroundAudioLeaseTest {
    @Test fun videoNeverReceivesAnAudioServiceLease() {
        val lease = AndroidBackgroundAudioLease()
        assertEquals(0L, lease.install(Any(), PlaybackGeneration(), false) { error("video grant") })
        assertFalse(lease.grant(0, true))
    }
    @Test fun onlyStartedServiceGrantsMusicAndDuplicateUpdatesAreIgnored() {
        val events = mutableListOf<RuntimeEvent.BackgroundAudio>()
        val lease = AndroidBackgroundAudioLease(); val generation = PlaybackGeneration()
        val token = lease.install(Any(), generation, true) { events += it as RuntimeEvent.BackgroundAudio }
        assertTrue(events.isEmpty()); assertFalse(lease.granted)
        assertTrue(lease.grant(token, true)); assertTrue(lease.grant(token, true))
        assertEquals(1, events.size); assertSame(generation, events.single().generation)
        assertTrue(events.single().active)
    }
    @Test fun switchingAccountsRejectsOldServiceAndOldRuntimeCleanup() {
        val events = mutableListOf<RuntimeEvent.BackgroundAudio>()
        val lease = AndroidBackgroundAudioLease(); val oldOwner = Any(); val newOwner = Any()
        val first = lease.install(oldOwner, PlaybackGeneration(), true) { events += it as RuntimeEvent.BackgroundAudio }
        lease.grant(first, true)
        val next = lease.install(newOwner, PlaybackGeneration(), true) { events += it as RuntimeEvent.BackgroundAudio }
        assertFalse(lease.grant(first, true)); assertFalse(lease.grant(first, false)); assertFalse(lease.close(oldOwner))
        assertEquals(next, lease.token); assertEquals(listOf(true, false), events.map { it.active })
        assertTrue(lease.grant(next, true)); assertTrue(lease.close(newOwner)); assertFalse(lease.grant(next, true))
        assertNull(lease.generation)
    }
    @Test fun musicToVideoRevokesGrantAndLateCallbacksCannotReactivateMusic() {
        val events = mutableListOf<RuntimeEvent.BackgroundAudio>(); val lease = AndroidBackgroundAudioLease()
        val token = lease.install(Any(), PlaybackGeneration(), true) { events += it as RuntimeEvent.BackgroundAudio }
        lease.grant(token, true)
        lease.install(Any(), PlaybackGeneration(), false) { error("video grant") }
        assertEquals(listOf(true, false), events.map { it.active }); assertFalse(lease.grant(token, true))
    }
    @Test fun playWaitsForServiceFocusAndOnlyFlushesOnce() {
        val lease = AndroidBackgroundAudioLease(); val token = lease.install(Any(), PlaybackGeneration(), true) {}
        var plays = 0
        assertTrue(lease.deferPlay(token) { plays++ }); assertEquals(0, plays)
        lease.grant(token, true); lease.grant(token, true); assertEquals(1, plays)
        assertFalse(lease.deferPlay(token) { plays++ })
    }
    @Test fun pauseOrReloadBeforeFocusPreventsDelayedPlay() {
        val lease = AndroidBackgroundAudioLease(); val owner = Any()
        val token = lease.install(owner, PlaybackGeneration(), true) {}; var plays = 0
        lease.deferPlay(token) { plays++ }; lease.cancelPending(owner); lease.grant(token, true)
        assertEquals(0, plays)
        lease.grant(token, false); lease.deferPlay(token) { plays++ }
        lease.install(Any(), PlaybackGeneration(), true) {}; assertFalse(lease.grant(token, true)); assertEquals(0, plays)
    }
}
