package cn.mafei.cmp.feature.settings

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.domain.ServerId
import kotlin.test.Test
import kotlin.test.assertEquals

class AccountSwitchGroupingTest {

    @Test
    fun sameServerIdentityGroupsAccountsAcrossDifferentAddresses() {
        val items = listOf(
            item("a", "https://media.example", "Living Room"),
            item("b", "http://192.168.1.10:8096", "Living Room"),
        )

        val groups = accountSwitchGroups(items)

        assertEquals(1, groups.size)
        assertEquals(listOf("a", "b"), groups.single().items.map { it.key })
        assertEquals("Living Room", groups.single().title)
    }

    @Test
    fun sameDisplayNameDoesNotMergeDifferentServers() {
        val items = listOf(
            item("a", "https://one.example", "Jellyfin", ServerId("server-one")),
            item("b", "https://two.example", "Jellyfin", ServerId("server-two")),
        )

        assertEquals(2, accountSwitchGroups(items).size)
    }

    @Test
    fun migratedRowsFromTheSameLegacyServerKeepOneGroup() {
        val items = listOf(
            item(
                "a",
                "https://media.example",
                "Living Room",
                ServerId("legacy-v1/install/server/one"),
            ),
            item(
                "b",
                "http://192.168.1.10:8096",
                "Living Room",
                ServerId("legacy-v1/install/server/one"),
            ),
        )

        assertEquals(1, accountSwitchGroups(items).size)
    }

    @Test
    fun unrelatedLegacyServersAreNotMergedByDisplayName() {
        val items = listOf(
            item(
                "a",
                "https://one.example",
                "Jellyfin",
                ServerId("legacy-v1/install/server/one"),
            ),
            item(
                "b",
                "https://two.example",
                "Jellyfin",
                ServerId("legacy-v1/install/server/two"),
            ),
        )

        assertEquals(2, accountSwitchGroups(items).size)
    }

    private fun item(
        key: String,
        address: String,
        serverName: String,
        serverId: ServerId = ServerId("server-one"),
    ) = AccountSwitchItem(
        key = key,
        provider = ProviderKind.Jellyfin,
        serverId = serverId,
        serverName = serverName,
        displayName = key,
        address = address,
        lastUsedEpochMs = null,
        isCurrent = false,
    )
}
