package cn.mafei.cmp.session

import cn.mafei.cmp.common.design_system.AppearanceMode
import cn.mafei.cmp.core.storage_api.StorageUnavailableException
import cn.mafei.cmp.storage.DocumentSettingsStorage
import cn.mafei.cmp.storage.SettingsDocumentBackend
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AppearanceStoreTest {
    private class Document : SettingsDocumentBackend {
        var bytes: ByteArray? = null
        var fail = false
        override fun read() = bytes?.copyOf()
        override fun replace(document: ByteArray) { check(!fail); bytes = document.copyOf() }
    }
    @Test fun everyModeRoundTripsWithoutTouchingAccounts(): Unit = runBlocking {
        val document = Document(); val settings = DocumentSettingsStorage(document)
        settings.putString("account.fixture", "unchanged")
        for (mode in AppearanceMode.entries) {
            AppearanceStore(settings).save(mode)
            assertEquals(mode, AppearanceStore(DocumentSettingsStorage(document)).load())
        }
        assertEquals("unchanged", settings.getString("account.fixture"))
    }
    @Test fun invalidValueUsesDarkAndFailedWriteDoesNotPublish(): Unit = runBlocking {
        val document = Document(); val settings = DocumentSettingsStorage(document)
        settings.putString(AppearanceStore.KEY, "invalid")
        val store = AppearanceStore(settings); assertEquals(AppearanceMode.Dark, store.load())
        document.fail = true
        assertFailsWith<StorageUnavailableException> { store.save(AppearanceMode.Light) }
        assertEquals(AppearanceMode.Dark, store.state.value)
    }
    @Test fun followSystemOnlyChangesWhenTheSystemChanges() {
        assertFalse(AppearanceMode.System.dark(false)); assertTrue(AppearanceMode.System.dark(true))
        assertFalse(AppearanceMode.Light.dark(true)); assertTrue(AppearanceMode.Dark.dark(false))
    }
}
