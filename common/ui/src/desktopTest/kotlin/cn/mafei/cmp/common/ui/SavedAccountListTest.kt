package cn.mafei.cmp.common.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test

class SavedAccountListTest {
    @get:org.junit.Rule
    val composeRule = createComposeRule()

    private val account = SavedAccountListItem(
        key = "account-1",
        provider = ProviderKind.Jellyfin,
        serverId = ServerId("server-1"),
        serverName = "客厅服务器",
        displayName = "xiaobai",
        address = "https://media.example",
        isCurrent = false,
    )

    @Test
    fun deletingLastAccountKeepsRowUntilDissolveCompletes() {
        var items by mutableStateOf(listOf(account))
        var deletingKey by mutableStateOf<String?>(null)
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MaterialTheme {
                MaFeiSavedAccountList(
                    items = items,
                    enabled = deletingKey == null,
                    busyKey = null,
                    deletingKey = deletingKey,
                    deleteAffordance = SavedAccountDeleteAffordance.Button,
                    onAccount = {},
                    onDelete = {},
                    modifier = Modifier.fillMaxSize(),
                    emptyContent = { Text("暂无已保存账号") },
                )
            }
        }
        composeRule.onNodeWithText("xiaobai").assertIsDisplayed()

        composeRule.runOnIdle {
            deletingKey = account.key
            items = emptyList()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("xiaobai").assertIsDisplayed()
        composeRule.onAllNodesWithText("暂无已保存账号").assertCountEquals(0)

        composeRule.mainClock.advanceTimeBy(700L)
        composeRule.waitForIdle()
        composeRule.onAllNodesWithText("xiaobai").assertCountEquals(0)
        composeRule.onNodeWithText("暂无已保存账号").assertIsDisplayed()
    }
}
