package cn.mafei.cmp.feature.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.common.ui.MaFeiRetainedContent
import cn.mafei.cmp.core.domain.ImageKind
import cn.mafei.cmp.core.domain.MediaId
import cn.mafei.cmp.core.domain.MediaImage
import cn.mafei.cmp.core.domain.MediaItem
import cn.mafei.cmp.core.domain.MediaType
import cn.mafei.cmp.core.domain.PersonRef
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.Rule

class HomeBackdropIdentityTest {
    @get:Rule val rule = createComposeRule()
    private val active = mutableStateOf(true)
    private val backdrop = HomeHeroBackdropState().apply { clearCarouselEnabled = false }
    private val creations = mutableMapOf<Pair<ServerId, MediaId>, Int>()
    private val attached = mutableMapOf<Pair<ServerId, MediaId>, Int>()
    private val payloads = mutableMapOf<Pair<ServerId, MediaId>, ObservedPayload>()
    private val probes = mutableListOf<MetadataProbe<*>>()

    @Test
    fun mountingAndWarmActivationNeverHashTheCompleteMediaObject() {
        val first = media("first", "First")
        mount(first)
        rule.runOnIdle {
            assertPayload(first)
            assertNoMetadataHashing()
        }
        repeat(2) {
            rule.runOnIdle { active.value = false }
            advance(48)
            rule.runOnIdle { active.value = true }
            advance(48)
        }
        rule.runOnIdle {
            assertEquals(setOf(identity(first)), visibleSources())
            assertEquals(1, creations[identity(first)])
            assertPayload(first)
            assertNoMetadataHashing()
        }
    }

    @Test
    fun sameIdentityDeliversUpdatedTextAndImageWithoutRemountingArtwork() {
        val first = media("same", "Original title")
        mount(first)
        val updated = updatedMedia(first, "Updated title", "updated-image")
        rule.runOnIdle { backdrop.activeItem = updated }
        advance(48)
        rule.runOnIdle {
            assertEquals(setOf(identity(first)), visibleSources())
            assertEquals(1, creations[identity(first)], "A metadata update must keep the existing artwork composition")
            assertEquals(1, attached[identity(first)])
            assertPayload(updated)
            assertNoMetadataHashing()
        }
        rule.onNodeWithText(updated.title).assertExists()
        rule.onNodeWithText(updated.overview!!).assertExists()
        rule.onNodeWithText("updated-image").assertExists()
        rule.onNodeWithText(first.title).assertDoesNotExist()
        advance(500)
        rule.runOnIdle {
            assertEquals(1, creations[identity(first)])
            assertPayload(updated)
            assertNoMetadataHashing()
        }
    }

    @Test
    fun differentMediaIdsKeepBothSourcesUntilTheFadeFinishes() {
        checkIdentityChange(media("first", "First"), media("second", "Second"))
    }

    @Test
    fun theSameMediaIdOnAnotherServerStillCrossfades() {
        checkIdentityChange(media("same", "Server A", "server-a"), media("same", "Server B", "server-b"))
    }

    @Test
    fun updatingAnIncomingSourcePreservesItsLatestPayloadWhenTheFadeIsInterrupted() {
        val first = media("first", "First")
        val second = media("second", "Second before update")
        val third = media("third", "Third")
        mount(first)
        rule.runOnIdle { backdrop.activeItem = second }
        advance(80)
        rule.runOnIdle { assertEquals(setOf(identity(first), identity(second)), visibleSources()) }

        val updatedSecond = updatedMedia(second, "Second after update", "second-new-image")
        rule.runOnIdle { backdrop.activeItem = updatedSecond }
        advance(48)
        rule.runOnIdle {
            assertPayload(updatedSecond)
            assertEquals(1, creations[identity(second)])
            backdrop.activeItem = third
        }
        advance(80)
        rule.runOnIdle {
            assertTrue(visibleSources().containsAll(setOf(identity(second), identity(third))))
            assertEquals(1, attached[identity(second)])
            assertEquals(1, creations[identity(second)])
            assertPayload(updatedSecond)
            assertPayload(third)
            assertNoMetadataHashing()
        }
        rule.onNodeWithText(updatedSecond.title).assertExists()
        rule.onNodeWithText("second-new-image").assertExists()
        rule.onNodeWithText(second.title).assertDoesNotExist()
        advance(500)
        rule.runOnIdle {
            assertEquals(setOf(identity(third)), visibleSources())
            assertPayload(third)
            assertNoMetadataHashing()
        }
    }

    private fun checkIdentityChange(first: MediaItem, second: MediaItem) {
        mount(first)
        rule.runOnIdle { backdrop.activeItem = second }
        advance(80)
        rule.runOnIdle {
            assertEquals(setOf(identity(first), identity(second)), visibleSources())
            assertPayload(first)
            assertPayload(second)
            assertNoMetadataHashing()
        }
        advance(500)
        rule.runOnIdle {
            assertEquals(setOf(identity(second)), visibleSources())
            assertEquals(1, creations[identity(second)])
            assertPayload(second)
            assertNoMetadataHashing()
        }
    }

    private fun mount(initial: MediaItem) {
        backdrop.activeItem = initial
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaFeiTheme {
                MaFeiRetainedContent(active.value, Modifier.size(390.dp, 844.dp)) {
                    HomeHeroWindowBackdrop(backdrop, artwork = null, ambientArtwork = { media, _, modifier ->
                        // Probe keys intentionally contain no MediaItem or metadata list.
                        val key = identity(media)
                        val imageUrl = media.images.firstOrNull()?.url
                        DisposableEffect(key) {
                            creations[key] = (creations[key] ?: 0) + 1
                            attached[key] = (attached[key] ?: 0) + 1
                            onDispose { attached[key] = checkNotNull(attached[key]) - 1 }
                        }
                        SideEffect { payloads[key] = ObservedPayload(media, imageUrl) }
                        Column(modifier) {
                            BasicText(media.title)
                            BasicText(media.overview.orEmpty())
                            BasicText(imageUrl.orEmpty())
                        }
                    })
                }
            }
        }
        advance(48)
    }

    private fun advance(milliseconds: Long) {
        rule.mainClock.advanceTimeBy(milliseconds)
        rule.waitForIdle()
    }

    private fun identity(item: MediaItem) = item.serverId to item.id

    private fun visibleSources() = attached.filterValues { it > 0 }.keys

    private fun assertPayload(expected: MediaItem) {
        val received = payloads.getValue(identity(expected))
        assertSame(expected, received.item, "The artwork must receive the latest source payload")
        assertEquals(expected.images.firstOrNull()?.url, received.imageUrl)
    }

    private fun assertNoMetadataHashing() {
        probes.forEach { probe ->
            assertEquals(0, probe.hashCalls, "Crossfade must not hash the ${probe.label} list of a MediaItem")
            if (probe.label == "people") {
                assertEquals(0, probe.elementReads, "Backdrop identity must not traverse unrelated person metadata")
            }
        }
    }

    private fun media(id: String, title: String, server: String = "server"): MediaItem {
        val people = MetadataProbe("people", listOf(PersonRef("Actor", image = MediaImage(url = "actor-image"))))
        val images = imageProbe("image-$title")
        probes.add(people)
        return MediaItem(
            MediaId(id), ServerId(server), ProviderKind.Jellyfin, title, MediaType.Movie,
            overview = "Overview for $title", people = people, images = images,
        )
    }

    private fun updatedMedia(original: MediaItem, title: String, imageUrl: String) = original.copy(
        title = title,
        overview = "Updated overview for $title",
        images = imageProbe(imageUrl),
    )

    private fun imageProbe(url: String) = MetadataProbe("images", listOf(MediaImage(ImageKind.Backdrop, url))).also { probes.add(it) }

    private data class ObservedPayload(val item: MediaItem, val imageUrl: String?)

    private class MetadataProbe<T>(val label: String, private val backing: List<T>) : AbstractList<T>() {
        var hashCalls = 0
            private set
        var elementReads = 0
            private set
        override val size: Int get() = backing.size
        override fun get(index: Int): T {
            elementReads++
            return backing[index]
        }
        override fun hashCode(): Int {
            hashCalls++
            return super.hashCode()
        }
    }
}
