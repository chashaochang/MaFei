package cn.mafei.cmp.data.migration

import cn.mafei.cmp.core.domain.ProviderKind
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LegacySnapshotPayloadReaderTest {
    @Test
    fun decodesPlatformAccountSnapshotWithoutExposingWireTypes() {
        val sources = LegacySource.entries.joinToString(",") { "\"${it.name}\":\"Read\"" }
        val result = LegacySnapshotPayloadReader.decode(
            """{
                "status":"captured",
                "version":1,
                "installationId":"ohos-cn.xiaobai.mafei",
                "sources":{$sources},
                "servers":[{
                    "key":"jellyfin-server-1","provider":"jellyfin","name":"Home",
                    "baseUrl":"http://media.example:8096","originalAddress":"http://media.example:8096"
                }],
                "accounts":[{
                    "key":"jellyfin-account-1","serverKey":"jellyfin-server-1",
                    "remoteUserId":"remote-user","userName":"Viewer",
                    "token":{"scheme":"MediaBrowser","value":"fixture-token"},
                    "password":"fixture-password","lastActiveEpochMs":42
                }],
                "selectedAccountKey":"jellyfin-account-1"
            }""".trimIndent(),
        )

        val snapshot = assertIs<LegacyReadResult.Captured>(result).snapshot
        assertEquals("ohos-cn.xiaobai.mafei", snapshot.installationId)
        assertEquals(ProviderKind.Jellyfin, snapshot.servers.single().provider)
        assertEquals("remote-user", snapshot.accounts.single().remoteUserId)
        assertEquals(SecureSecret.AuthToken("MediaBrowser", "fixture-token"), snapshot.accounts.single().token)
        assertEquals(SecureSecret.Password("fixture-password"), snapshot.accounts.single().password)
        assertEquals("jellyfin-account-1", snapshot.selectedAccountKey)
    }

    @Test
    fun rejectsIncompleteSourceCapture() {
        val result = LegacySnapshotPayloadReader.decode(
            """{"status":"captured","version":1,"installationId":"ohos","sources":{}}""",
        )
        assertEquals(
            ReadFailure.InconsistentCapture,
            assertIs<LegacyReadResult.Unavailable>(result).reason,
        )
    }

    @Test
    fun malformedPayloadFailsClosedWithoutThrowing() {
        val result = LegacySnapshotPayloadReader.decode("not-json")
        assertEquals(ReadFailure.Corrupted, assertIs<LegacyReadResult.Unavailable>(result).reason)
    }
}
