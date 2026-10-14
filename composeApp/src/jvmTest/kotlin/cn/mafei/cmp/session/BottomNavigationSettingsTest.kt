package cn.mafei.cmp.session

import cn.mafei.cmp.common.design_system.BottomNavigationType
import cn.mafei.cmp.common.glass.GlassStyle
import cn.mafei.cmp.core.storage_api.StorageUnavailableException
import cn.mafei.cmp.storage.DocumentSettingsStorage
import cn.mafei.cmp.storage.SettingsDocumentBackend
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BottomNavigationSettingsTest {
    private class Document : SettingsDocumentBackend {
        var bytes: ByteArray? = null
        var fail = false
        override fun read() = bytes?.copyOf()
        override fun replace(document: ByteArray) { check(!fail); bytes = document.copyOf() }
    }

    @Test fun missingOrUnknownPreferenceKeepsTheExistingFloatingNavigation(): Unit = runBlocking {
        val settings = DocumentSettingsStorage(Document())
        val store = GlassStyleStore(settings)
        store.load()
        assertEquals(BottomNavigationType.Floating, store.bottomNavigation.value)
        settings.putString(GlassStyleStore.NAVIGATION_KEY, "unknown")
        store.load()
        assertEquals(BottomNavigationType.Floating, store.bottomNavigation.value)
    }

    @Test fun bothChoicesSurviveReloadWithoutChangingMaterialOrAccounts(): Unit = runBlocking {
        val document = Document()
        val settings = DocumentSettingsStorage(document)
        settings.putString("account.fixture", "unchanged")
        GlassStyleStore(settings).save(GlassStyle.GaussianBlur)
        for (type in BottomNavigationType.entries) {
            GlassStyleStore(settings).saveBottomNavigation(type)
            val reloaded = GlassStyleStore(DocumentSettingsStorage(document))
            assertEquals(GlassStyle.GaussianBlur, reloaded.load())
            assertEquals(type, reloaded.bottomNavigation.value)
        }
        assertEquals("unchanged", settings.getString("account.fixture"))
    }

    @Test fun failedWriteDoesNotPublishAChoiceThatWasNotSaved(): Unit = runBlocking {
        val document = Document()
        val store = GlassStyleStore(DocumentSettingsStorage(document))
        document.fail = true
        assertFailsWith<StorageUnavailableException> { store.saveBottomNavigation(BottomNavigationType.Bar) }
        assertEquals(BottomNavigationType.Floating, store.bottomNavigation.value)
    }
}
