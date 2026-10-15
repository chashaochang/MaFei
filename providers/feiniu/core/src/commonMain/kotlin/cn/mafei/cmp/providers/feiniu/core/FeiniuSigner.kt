package cn.mafei.cmp.providers.feiniu.core

/**
 * Feiniu request signing formulas, frozen by the legacy protocol:
 *
 * - connect `fn-sign` = SHA256("trim_connect`{fnId}`{timestamp}`anna")
 * - `Authx` = nonce=..&timestamp=..&sign=MD5(prefix_path_nonce_ts_dataMD5_secret)
 *   with dataMD5 = MD5(body) when a body is present, otherwise
 *   MD5(sorted "name=value" joined with '&') for query parameters, otherwise
 *   MD5(""). The connect variant uses [FeiniuIdentity.AUTHX_API_KEY] as the
 *   secret suffix, the media variant [FeiniuIdentity.MEDIA_API_SECRET].
 */
data object FeiniuSigner {

    fun connectFnSign(fnId: String, timestamp: String): String =
        FeiniuDigest.sha256("trim_connect`$fnId`$timestamp`anna")

    fun authx(
        path: String,
        nonce: String,
        timestamp: String,
        bodyText: String? = null,
        queryParameters: List<Pair<String, String>> = emptyList(),
        variant: Variant = Variant.Media,
    ): String {
        val dataMd5 = when {
            !bodyText.isNullOrEmpty() -> FeiniuDigest.md5(bodyText)
            queryParameters.isNotEmpty() -> FeiniuDigest.md5(
                queryParameters
                    .filter { it.first.isNotEmpty() }
                    .sortedWith(compareBy { it.first })
                    .joinToString("&") { "${it.first}=${it.second}" },
            )
            else -> FeiniuDigest.md5("")
        }
        val secret = when (variant) {
            Variant.Connect -> FeiniuIdentity.AUTHX_API_KEY
            Variant.Media -> FeiniuIdentity.MEDIA_API_SECRET
        }
        val signText = listOf(
            FeiniuIdentity.AUTHX_PREFIX,
            path,
            nonce,
            timestamp,
            dataMd5,
            secret,
        ).joinToString("_")
        return "nonce=$nonce&timestamp=$timestamp&sign=${FeiniuDigest.md5(signText)}"
    }

    /**
     * Legacy six-digit nonce formula: floor(random * 900000) + 100000.
     * Frozen to match the reference implementation exactly.
     */
    fun nonceFrom(random: Double): String = ((random * 900000.0).toInt() + 100000).toString()

    enum class Variant { Connect, Media }
}
