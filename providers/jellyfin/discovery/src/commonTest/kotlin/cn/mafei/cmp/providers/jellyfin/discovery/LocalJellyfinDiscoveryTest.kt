package cn.mafei.cmp.providers.jellyfin.discovery

import kotlin.test.Test
import kotlin.test.assertEquals

class LocalJellyfinDiscoveryTest {

    @Test
    fun decodesOfficialReplyShapeAndIgnoresUnknownFields() {
        assertEquals(
            listOf(
                LocalJellyfinServer(
                    address = "http://192.168.31.96:8096",
                    id = "server-1",
                    name = "客厅服务器",
                    endpointAddress = "192.168.31.96:7359",
                ),
            ),
            decodeLocalJellyfinResponses(
                listOf(
                    """{"Address":"http://192.168.31.96:8096","Id":"server-1","Name":"客厅服务器","EndpointAddress":"192.168.31.96:7359","Ignored":true}""",
                ),
            ),
        )
    }

    @Test
    fun malformedRepliesAreIgnoredAndServerIdsAreDeduplicated() {
        assertEquals(
            listOf(
                LocalJellyfinServer(address = "http://first:8096", id = "same", name = "First"),
                LocalJellyfinServer(address = "http://other:8096", id = "other", name = "Other"),
            ),
            decodeLocalJellyfinResponses(
                listOf(
                    "not-json",
                    """{"Address":"http://first:8096","Id":"same","Name":"First"}""",
                    """{"Address":"http://duplicate:8096","Id":"same","Name":"Duplicate"}""",
                    """{"Address":"http://other:8096","Id":"other","Name":"Other"}""",
                ),
            ),
        )
    }
}
