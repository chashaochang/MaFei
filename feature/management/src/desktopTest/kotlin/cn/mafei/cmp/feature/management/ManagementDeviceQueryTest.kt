package cn.mafei.cmp.feature.management

import cn.mafei.cmp.core.domain.ManagedDevice
import cn.mafei.cmp.core.serialization.parseIsoInstantToEpochMs
import kotlin.test.*

class ManagementDeviceQueryTest {
    private fun device(id: String, date: String = "2026-10-09T12:00:00Z", user: String = "Alice", name: String = id) =
        ManagedDevice(id, name, "", "Client", "2", "u", user, date, false)
    @Test fun searchIncludesNameClientAndUserAndUserFilterIsCaseInsensitive() {
        val devices = listOf(device("a"), device("b", user = "Bob"))
        assertEquals(listOf("a"), queryDevices(devices, DeviceQuery(text = "  ALICE "), 0).items.map { it.id })
        assertEquals(2, queryDevices(devices, DeviceQuery(text = "client"), 0).total)
        assertEquals(listOf("b"), queryDevices(devices, DeviceQuery(users = setOf("bob")), 0).items.map { it.id })
    }
    @Test fun recentFilterDropsMissingOrOldDatesAndSortDirectionUsesRequestedField() {
        val now = parseIsoInstantToEpochMs("2026-10-10T00:00:00Z")!!
        val devices = listOf(device("b"), device("a", "2026-10-08T12:00:00Z"), device("old", "2026-09-01T00:00:00Z"), device("unknown", ""))
        assertEquals(listOf("b", "a"), queryDevices(devices, DeviceQuery(recentDays = 7), now).items.map { it.id })
        assertEquals(listOf("a", "b"), queryDevices(devices, DeviceQuery(recentDays = 7, sort = DeviceSort.Name, ascending = true), now).items.map { it.id })
    }
    @Test fun pagesRetainLegacyTwentyFiveRowsAndClampAfterDeletion() {
        val devices = (0..25).map { device(it.toString()) }
        val last = queryDevices(devices, DeviceQuery(page = 100), 0)
        assertEquals(1, last.page); assertEquals(2, last.pages); assertEquals(1, last.items.size)
        val shrunk = queryDevices(devices.take(3), DeviceQuery(page = last.page), 0)
        assertEquals(0, shrunk.page); assertEquals(3, shrunk.total)
        assertEquals(DevicePage(emptyList(), 0, 0, 0), queryDevices(emptyList(), DeviceQuery(page = 10), 0))
    }
}
