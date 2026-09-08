package cn.mafei.cmp.data.repositories.session

import cn.mafei.cmp.core.secure_credential_api.CredentialAlias
import cn.mafei.cmp.core.secure_credential_api.SecureSecret
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.*

class SessionCredentialSourceTest {
    @Test fun snapshotsDefensivelyCopyAndRedactSecrets() {
        val alias = CredentialAlias("account/token")
        val material = mutableMapOf<CredentialAlias, SecureSecret>(alias to SecureSecret.AuthToken("Bearer", "sensitive-fixture"))
        val snapshot = SessionCredentialSnapshot(2, 4, material)
        material.clear()
        assertEquals(1, snapshot.secrets.size)
        assertFalse(snapshot.toString().contains("sensitive-fixture"))
        assertFalse(snapshot.toString().contains("account/token"))
    }

    @Test fun currentProjectsActualStateWithoutWaitingForCollection() {
        data class Wire(val identity: Long, val revision: Long, val token: String)
        val authoritative = MutableStateFlow(Wire(1, 1, "first"))
        val source = StateFlowCredentialSource(authoritative) {
            SessionCredentialSnapshot(it.identity, it.revision, mapOf(CredentialAlias("token") to SecureSecret.AuthToken("Bearer", it.token)))
        }
        authoritative.value = Wire(5, 7, "latest")
        val snapshot = source.current()
        assertEquals(5L, snapshot.identityGeneration)
        assertEquals(7L, snapshot.revision)
        assertEquals("latest", (snapshot.secrets[CredentialAlias("token")] as SecureSecret.AuthToken).token)
    }

    @Test fun negativeVersionsAreRejected() {
        assertFailsWith<IllegalArgumentException> { SessionCredentialSnapshot(-1, 0, emptyMap()) }
        assertFailsWith<IllegalArgumentException> { SessionCredentialSnapshot(0, -1, emptyMap()) }
    }
}
