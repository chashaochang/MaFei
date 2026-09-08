package cn.mafei.cmp.providers.feiniu.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeiniuSessionRedactionTest {
    @Test fun credentialAndSnapshotDiagnosticsNeverContainSecrets() {
        val credentials = FeiniuCredentials("fixture-token-private", "session=fixture-cookie-private")
        val snapshot = FeiniuSessionSnapshot(credentials, 7, 12)
        for (text in listOf(credentials.toString(), snapshot.toString())) {
            assertFalse(text.contains("fixture-token-private"))
            assertFalse(text.contains("fixture-cookie-private"))
        }
        assertTrue(snapshot.toString().contains("identityGeneration=7"))
        assertTrue(snapshot.toString().contains("revision=12"))
    }
}
