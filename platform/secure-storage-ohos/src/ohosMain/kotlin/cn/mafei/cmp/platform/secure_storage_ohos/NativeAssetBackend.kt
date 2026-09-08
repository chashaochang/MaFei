@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package cn.mafei.cmp.platform.secure_storage_ohos

import platform.AssetStoreKit.AssetApi.*
import platform.AssetStoreKit.AssetType.*
import kotlinx.cinterop.*
import platform.posix.memcpy
import platform.posix.memset

internal actual fun createAssetBackend(namespace: String): AssetBackend = NativeAssetBackend(AssetKeys(namespace).namespaceBytes)

/** Direct API 11+ binding. No ArkTS business logic, N-API callback, or secret-bearing filesystem. */
private class NativeAssetBackend(private val namespace: ByteArray) : AssetBackend {
    override fun add(key: ByteArray, bytes: ByteArray): Int = withAttrs(query(key) + listOf(
        Attr.Bytes(ASSET_TAG_SECRET, bytes),
        Attr.Number(ASSET_TAG_ACCESSIBILITY, ASSET_ACCESSIBILITY_DEVICE_FIRST_UNLOCKED),
        Attr.Number(ASSET_TAG_AUTH_TYPE, ASSET_AUTH_TYPE_NONE),
        Attr.Number(ASSET_TAG_SYNC_TYPE, ASSET_SYNC_TYPE_NEVER),
        Attr.Number(ASSET_TAG_CONFLICT_RESOLUTION, ASSET_CONFLICT_THROW_ERROR),
    )) { pointer, count -> OH_Asset_Add(pointer, count) }

    override fun update(key: ByteArray, bytes: ByteArray): Int = withAttrs(query(key)) { query, queryCount ->
        withAttrs(listOf(Attr.Bytes(ASSET_TAG_SECRET, bytes))) { attributes, count ->
            OH_Asset_Update(query, queryCount, attributes, count)
        }
    }

    override fun read(key: ByteArray): AssetRead = queryResult(query(key) + listOf(
        Attr.Number(ASSET_TAG_RETURN_TYPE, ASSET_RETURN_ALL),
        Attr.Number(ASSET_TAG_RETURN_LIMIT, 1u),
    )) { status, results ->
        if (status != ASSET_SUCCESS.toInt()) return@queryResult AssetRead(status, null)
        if (results.count != 1u) invalidAssetResult()
        val item = results.results ?: invalidAssetResult()
        val returnedKey = bytes(item, ASSET_TAG_ALIAS, 256)
        if (!returnedKey.contentEquals(key)) invalidAssetResult()
        AssetRead(status, bytes(item, ASSET_TAG_SECRET, OhosAssetCredentialStore.MAX_ENCODED_BYTES))
    }

    override fun exists(key: ByteArray): Int = queryResult(query(key) + listOf(
        Attr.Number(ASSET_TAG_RETURN_TYPE, ASSET_RETURN_ATTRIBUTES),
        Attr.Number(ASSET_TAG_RETURN_LIMIT, 1u),
    )) { status, results ->
        if (status == ASSET_SUCCESS.toInt()) {
            if (results.count != 1u) invalidAssetResult()
            val item = results.results ?: invalidAssetResult()
            if (!bytes(item, ASSET_TAG_ALIAS, 256).contentEquals(key)) invalidAssetResult()
        }
        status
    }

    override fun delete(key: ByteArray): Int = withAttrs(query(key)) { pointer, count -> OH_Asset_Remove(pointer, count) }

    override fun aliases(offset: Int, limit: Int): AssetPage = queryResult(query(null) + listOf(
        Attr.Number(ASSET_TAG_RETURN_TYPE, ASSET_RETURN_ATTRIBUTES),
        Attr.Number(ASSET_TAG_RETURN_OFFSET, offset.toUInt()),
        Attr.Number(ASSET_TAG_RETURN_LIMIT, limit.toUInt()),
    )) { status, results ->
        if (status != ASSET_SUCCESS.toInt()) return@queryResult AssetPage(status, emptyList())
        if (results.count > limit.toUInt()) invalidAssetResult()
        val values = results.results
        if (results.count > 0u && values == null) invalidAssetResult()
        AssetPage(status, List(results.count.toInt()) { index -> bytes(values!![index].ptr, ASSET_TAG_ALIAS, 256) })
    }

    private fun query(key: ByteArray?): List<Attr> = buildList {
        add(Attr.Bytes(ASSET_TAG_DATA_LABEL_CRITICAL_1, namespace))
        if (key != null) add(Attr.Bytes(ASSET_TAG_ALIAS, key))
    }

    private fun <T> queryResult(query: List<Attr>, block: (Int, Asset_ResultSet) -> T): T = withAttrs(query) { attrs, count ->
        memScoped {
            val result = alloc<Asset_ResultSet>()
            result.count = 0u
            result.results = null
            val status = OH_Asset_Query(attrs, count, result.ptr)
            try { block(status, result) } finally { OH_Asset_FreeResultSet(result.ptr) }
        }
    }

    private fun bytes(result: CPointer<Asset_Result>?, tag: UInt, max: Int): ByteArray {
        val attr = OH_Asset_ParseAttr(result ?: invalidAssetResult(), tag) ?: invalidAssetResult()
        if (attr.pointed.tag != tag) invalidAssetResult()
        val blob = attr.pointed.value.blob
        if (blob.size == 0u || blob.size > max.toUInt()) invalidAssetResult()
        return (blob.data ?: invalidAssetResult()).reinterpret<ByteVar>().readBytes(blob.size.toInt())
    }
}

private sealed class Attr(val tag: UInt) {
    class Bytes(tag: UInt, val bytes: ByteArray) : Attr(tag)
    class Number(tag: UInt, val number: UInt) : Attr(tag)
}

private fun <T> withAttrs(attributes: List<Attr>, block: (CPointer<Asset_Attr>, UInt) -> T): T = memScoped {
    val array = allocArray<Asset_Attr>(attributes.size)
    val scratch = mutableListOf<Pair<CPointer<UByteVar>, Int>>()
    try {
        attributes.forEachIndexed { index, attr ->
            array[index].tag = attr.tag
            when (attr) {
                is Attr.Number -> array[index].value.u32 = attr.number
                is Attr.Bytes -> {
                    require(attr.bytes.isNotEmpty()) { "Empty AssetStore attribute" }
                    val buffer = allocArray<UByteVar>(attr.bytes.size)
                    scratch.add(buffer to attr.bytes.size)
                    attr.bytes.usePinned { memcpy(buffer, it.addressOf(0), attr.bytes.size.toULong()) }
                    array[index].value.blob.size = attr.bytes.size.toUInt()
                    array[index].value.blob.data = buffer
                }
            }
        }
        block(array, attributes.size.toUInt())
    } finally {
        scratch.forEach { (buffer, size) -> memset(buffer, 0, size.toULong()) }
    }
}
