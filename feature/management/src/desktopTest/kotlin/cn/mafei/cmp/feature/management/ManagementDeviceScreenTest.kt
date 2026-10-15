package cn.mafei.cmp.feature.management

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import cn.mafei.cmp.common.design_system.MaFeiTheme
import cn.mafei.cmp.core.domain.ManagedDevice
import kotlin.test.*

class ManagementDeviceScreenTest {
    @get:org.junit.Rule val rule = createComposeRule()
    private val current = ManagedDevice("device", "Phone", "", "MaFei", "2", "u", "Alice", "2026-10-10T00:00:00Z", true)
    @Test fun phoneDetailProtectsCurrentDeviceAndAllowsNameChanges() {
        var name: String? = null
        rule.setContent { MaFeiTheme {
            Box(Modifier.requiredSize(360.dp, 780.dp)) {
                ManagementDeviceDetailScreen(current, ManagementDevicesState(listOf(current), loading = false), {}, {}, { name = it }, { error("current device") })
            }
        } }
        rule.onNodeWithText("撤销授权").performScrollTo().assertIsNotEnabled()
        rule.onNodeWithText("当前设备不能撤销授权").assertExists()
        rule.onNodeWithText("保存名称").performScrollTo().performClick(); assertEquals("Phone", name)
    }
    @Test fun wideListUsesRealDeviceIdentityAndProtectsMutationsWithConfirmation() {
        val remote = current.copy(id = "tv", name = "TV", current = false)
        var opened: String? = null; var revoked = 0
        rule.setContent { MaFeiTheme {
            Box(Modifier.requiredSize(900.dp, 650.dp)) {
                ManagementDevicesScreen(ManagementDevicesState(listOf(current, remote), loading = false), "device", 0, {}, {}, { opened = it }, { revoked++ })
            }
        } }
        rule.onNodeWithText("TV").performClick(); assertEquals("tv", opened)
        rule.onNodeWithContentDescription("撤销其他设备").performClick()
        assertEquals(0, revoked); rule.onNodeWithText("当前设备将保留授权", substring = true).assertExists()
        rule.onNodeWithText("取消").performClick(); assertEquals(0, revoked)
    }
}
