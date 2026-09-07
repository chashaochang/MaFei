package cn.mafei.cmp.providers.feiniu.connection

import cn.mafei.cmp.providers.feiniu.core.FeiniuInvalidAddressError
import cn.mafei.cmp.providers.feiniu.core.FeiniuRoute
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FeiniuAddressParserTest {

    @Test
    fun bareFnIdDetected() {
        val parsed = FeiniuAddressParser.parse("testfnid")
        assertEquals(FeiniuAddressParser.Kind.FnId, parsed.kind)
        assertEquals("testfnid", parsed.fnId)
    }

    @Test
    fun relayDomainsMapToFnId() {
        for (input in listOf(
            "https://testfnid.5ddd.com",
            "testfnid.5ddd.com",
            "https://testfnid.fnos.net:5667",
            "testfnid.fnos.net",
            "5ddd.com/testfnid",
        )) {
            val parsed = FeiniuAddressParser.parse(input)
            assertEquals(FeiniuAddressParser.Kind.FnId, parsed.kind, "input $input")
            assertEquals("testfnid", parsed.fnId, "input $input")
        }
    }

    @Test
    fun directAddressGetsDefaultPorts() {
        assertEquals("http://192.168.1.10:5666", FeiniuAddressParser.parse("192.168.1.10").directBaseUrl)
        assertEquals("http://192.168.1.10:5666", FeiniuAddressParser.parse("http://192.168.1.10").directBaseUrl)
        assertEquals("http://nas.example.org:5666", FeiniuAddressParser.parse("nas.example.org").directBaseUrl)
        assertEquals("https://nas.example.org:5667", FeiniuAddressParser.parse("https://nas.example.org").directBaseUrl)
        assertEquals("http://nas.example.org:8080", FeiniuAddressParser.parse("nas.example.org:8080").directBaseUrl)
    }

    @Test
    fun invalidInputsAreRejected() {
        assertFailsWith<FeiniuInvalidAddressError> { FeiniuAddressParser.parse("") }
        assertFailsWith<FeiniuInvalidAddressError> { FeiniuAddressParser.parse("http://host?query=1") }
        assertFailsWith<FeiniuInvalidAddressError> { FeiniuAddressParser.parse("http://user:pass@host") }
    }
}

class FeiniuCandidateBuilderTest {

    @Test
    fun buildsLanPublicRelayCandidatesFromResolveData() {
        val candidates = FeiniuCandidateBuilder.build(
            FeiniuResolveDataForTest,
        )
        assertEquals(
            listOf(
                "http://192.168.1.10:5666",
                "http://203.0.113.10:5666",
                "https://203.0.113.10:5667",
                "http://[2001:db8::1]:5666",
                "https://[2001:db8::1]:5667",
                "https://relay-fixture.fnos.net:5667",
            ),
            candidates.map { it.baseUrl },
        )
        assertEquals(
            listOf(FeiniuRoute.Local, FeiniuRoute.Public, FeiniuRoute.Public, FeiniuRoute.Public, FeiniuRoute.Public, FeiniuRoute.Relay),
            candidates.map { it.route },
        )
    }

    @Test
    fun p2pDisabledDropsPublicCandidatesWhenOthersRemain() {
        val candidates = FeiniuCandidateBuilder.build(FeiniuResolveDataForTest)
        val filtered = FeiniuCandidateBuilder.applyP2pPreference(candidates, p2pEnabled = false)
        assertTrue(filtered.all { it.route != FeiniuRoute.Public })
        assertTrue(filtered.isNotEmpty())
    }

    @Test
    fun relayFallbackDomains() {
        val fallbacks = FeiniuCandidateBuilder.relayFallbackCandidates("testfnid")
        assertEquals("https://testfnid.5ddd.com", fallbacks[0].baseUrl)
        assertEquals("https://testfnid.fnos.net", fallbacks[1].baseUrl)
    }

    private val FeiniuResolveDataForTest: cn.mafei.cmp.providers.feiniu.core.FeiniuResolveData
        get() = cn.mafei.cmp.providers.feiniu.core.FeiniuResolveData(
            lanIpv4 = listOf("192.168.1.10"),
            publicIpv4 = listOf("203.0.113.10"),
            publicIpv6 = listOf("2001:db8::1"),
            relayHosts = listOf("relay-fixture.fnos.net"),
            port = cn.mafei.cmp.providers.feiniu.core.FeiniuResolvePortInfo(httpPort = 5666, httpsPort = 5667),
        )
}
