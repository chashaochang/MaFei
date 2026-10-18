package cn.mafei.cmp.feature.management

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
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

    @Test fun failedRenamePreservesDraftAndShowsIndependentAcknowledgement() {
        var state by mutableStateOf(ManagementDevicesState(listOf(current), loading = false))
        rule.setContent { MaFeiTheme {
            Box(Modifier.requiredSize(360.dp, 780.dp)) {
                ManagementDeviceDetailScreen(current, state, {}, {}, {}, {})
            }
        } }
        rule.onNode(hasSetTextAction()).performTextReplacement("客厅设备")
        val bounds = rule.onNodeWithText("保存名称").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { state = state.copy(message = "设备操作失败，请重试", actionFailed = true) }
        rule.onNodeWithTag("mafeiGlassConfirmCard").assertExists()
        assertEquals(bounds, rule.onNodeWithText("保存名称").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("知道了").performClick()
        rule.onNode(hasSetTextAction()).assertTextContains("客厅设备")
        rule.onNodeWithTag("mafeiGlassConfirmCard").assertDoesNotExist()
        assertEquals("设备操作失败，请重试", state.message)
    }

    @Test fun refreshFailurePreservesVisibleDevicesAndRetryRemainsAvailable() {
        val remote = current.copy(id = "tv", name = "TV", current = false)
        var state by mutableStateOf(ManagementDevicesState(listOf(current, remote), loading = false))
        var retried = false
        rule.setContent { MaFeiTheme {
            Box(Modifier.requiredSize(900.dp, 650.dp)) {
                ManagementDevicesScreen(state, "device", 0, {}, { retried = true }, {}, {})
            }
        } }
        val bounds = rule.onNodeWithText("TV").fetchSemanticsNode().boundsInRoot
        rule.runOnIdle { state = state.copy(error = "设备刷新请求失败") }
        rule.onNodeWithText("设备刷新请求失败").assertExists()
        assertEquals(bounds, rule.onNodeWithText("TV").fetchSemanticsNode().boundsInRoot)
        rule.onNodeWithText("重试").performClick()
        assertTrue(retried)
    }
}
