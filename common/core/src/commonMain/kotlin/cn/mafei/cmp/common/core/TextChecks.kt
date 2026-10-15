package cn.mafei.cmp.common.core

/**
 * Text normalization shared by adapters that map loose wire strings onto
 * optional domain fields. Only helpers with at least two consumers were added.
 */

/** Trimmed value, or `null` when the string is null or blank. */
fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/** Throws [IllegalArgumentException] when [value] is blank; returns it trimmed otherwise. */
fun requireNonBlank(value: String?, name: String): String {
    val trimmed = value?.trim()
    require(!trimmed.isNullOrEmpty()) { "$name must not be blank" }
    return trimmed
}
