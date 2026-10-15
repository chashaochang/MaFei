package cn.mafei.cmp.platform.secure_storage_macos

import kotlin.test.*

/** Real JNI linkage/typed-result proof; rejected scope never reaches SecItem. */
class MacOSKeychainJniShapeTest {
    @Test fun allFiveEntryPointsRejectForeignServicesBeforeCallingKeychain() {
        val invalidService = "outside-mafei-scope"
        val bytes = ByteArray(9)
        assertEquals(-50, JniKeychainBridge.add(invalidService, "a", bytes))
        assertEquals(-50, JniKeychainBridge.update(invalidService, "a", bytes))
        assertEquals(-50, JniKeychainBridge.delete(invalidService, "a"))
        val read = JniKeychainBridge.read(invalidService, "a")
        assertEquals(-50, read.status); assertNull(read.data)
        val aliases = JniKeychainBridge.aliases(invalidService)
        assertEquals(-50, aliases.status); assertNull(aliases.accounts)
    }
}
