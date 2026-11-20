package cn.mafei.cmp.session

import cn.mafei.cmp.common.design_system.BottomNavigationType
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
        val store = BottomNavigationSettingsStore(settings)
        store.load()
        assertEquals(BottomNavigationType.Floating, store.bottomNavigation.value)
        settings.putString(BottomNavigationSettingsStore.NAVIGATION_KEY, "unknown")
        store.load()
        assertEquals(BottomNavigationType.Floating, store.bottomNavigation.value)
    }

    @Test fun bothChoicesSurviveReloadWithoutChangingOldMaterialOrAccounts(): Unit = runBlocking {
        val document = Document()
        val settings = DocumentSettingsStorage(document)
        settings.putString("account.fixture", "unchanged")
        settings.putString("mafei.ui.glass-style.v1", "liquid")
        for (type in BottomNavigationType.entries) {
            BottomNavigationSettingsStore(settings).saveBottomNavigation(type)
            val reloaded = BottomNavigationSettingsStore(DocumentSettingsStorage(document))
            reloaded.load()
            assertEquals(type, reloaded.bottomNavigation.value)
        }
        assertEquals("unchanged", settings.getString("account.fixture"))
        assertEquals("liquid", settings.getString("mafei.ui.glass-style.v1"))
    }

    @Test fun failedWriteDoesNotPublishAChoiceThatWasNotSaved(): Unit = runBlocking {
        val document = Document()
        val store = BottomNavigationSettingsStore(DocumentSettingsStorage(document))
        document.fail = true
        assertFailsWith<StorageUnavailableException> { store.saveBottomNavigation(BottomNavigationType.Bar) }
        assertEquals(BottomNavigationType.Floating, store.bottomNavigation.value)
    }
}
