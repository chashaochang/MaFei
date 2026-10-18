package cn.mafei.cmp.providers.jellyfin.discovery

import cn.mafei.cmp.core.http_contract.HttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JellyfinAddressCandidatesTest {
    @Test fun explicitHttpWithoutPortRetainsTheBasePathAcrossAutomaticCandidates() {
        val urls = JellyfinAddressCandidates.fromInput("http://media.test/jellyfin")
        assertTrue(urls.all { it.host == "media.test" && it.encodedPath == "/jellyfin" })
        assertTrue(urls.any { it.scheme == HttpUrl.HTTP && it.port == 8096 })
        assertTrue(urls.any { it.scheme == HttpUrl.HTTPS && it.port == 8920 })
        assertTrue(urls.any { it.scheme == HttpUrl.HTTP && it.port == 80 })
    }

    @Test fun explicitHttpsWithoutPortNeverProducesAnInsecureCandidate() {
        val urls = JellyfinAddressCandidates.fromInput("https://[2001:db8::1]/media%20library")
        assertEquals(listOf(443, 8096, 8920), urls.map { it.port })
        assertTrue(urls.all { it.isHttps && it.host == "2001:db8::1" && it.encodedPath == "/media%20library" })
    }

    @Test fun explicitlyTypedDefaultPortsRemainExact() {
        for (address in listOf("http://media.test:80/jellyfin", "https://[2001:db8::1]:443/jellyfin")) {
            assertEquals(listOf(HttpUrl.parse(address)), JellyfinAddressCandidates.fromInput(address))
        }
    }
}
