package cn.mafei.cmp.providers.feiniu.core

import kotlin.test.Test
import kotlin.test.assertEquals

class FeiniuDigestTest {

    @Test
    fun md5StandardVectors() {
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", FeiniuDigest.md5(""))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", FeiniuDigest.md5("abc"))
        assertEquals("f96b697d7cb7938d525a2f31aaf161d0", FeiniuDigest.md5("message digest"))
        assertEquals(
            "57edf4a22be3c955ac49da2e2107b67a",
            FeiniuDigest.md5("12345678901234567890123456789012345678901234567890123456789012345678901234567890"),
        )
        assertEquals("cabe45dcc9ae5b66ba86600cca6b8ba8", FeiniuDigest.md5("a".repeat(1000)))
    }

    @Test
    fun sha256StandardVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", FeiniuDigest.sha256(""))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FeiniuDigest.sha256("abc"))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            FeiniuDigest.sha256("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq"),
        )
    }

    @Test
    fun sha256MultiBlockVector() {
        // crosses the 64-byte chunk boundary; verified against the platform digest
        val message = "abcdefghbcdefghicdefghijefghijkfghijklghijklmhijklmnonopq".repeat(2)
        assertEquals(114, message.length)
        assertEquals("77fa1663872cde595e2f19bbd21eee671fa0ebc07e73168aeb7a2bd8ec67c19e", FeiniuDigest.sha256(message))
        assertEquals("41edece42d63e8d9bf515a9ba6932e1c20cbc9f5a5d134645adb5db1b9737ea3", FeiniuDigest.sha256("a".repeat(1000)))
    }
}
