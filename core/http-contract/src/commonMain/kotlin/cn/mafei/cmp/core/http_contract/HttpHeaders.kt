package cn.mafei.cmp.core.http_contract

/**
 * Case-insensitive, multi-value HTTP header collection preserving insertion
 * order. Lookups normalize names to lower case; [entries] keeps the original
 * spelling and order, which matters for `Set-Cookie` semantics.
 */
class HttpHeaders private constructor(
    private val list: List<Pair<String, String>>,
) : Iterable<Pair<String, String>> {

    /** All header entries in insertion order, original name spelling preserved. */
    val entries: List<Pair<String, String>> get() = list


    val size: Int get() = entries.size
    val names: Set<String> get() = list.mapTo(mutableSetOf()) { it.first.lowercase() }

    fun first(name: String): String? =
        list.firstOrNull { it.first.equals(name, ignoreCase = true) }?.second

    fun values(name: String): List<String> =
        list.filter { it.first.equals(name, ignoreCase = true) }.map { it.second }

    fun contains(name: String): Boolean =
        list.any { it.first.equals(name, ignoreCase = true) }

    /** Returns a copy without every value of [name]. */
    fun without(name: String): HttpHeaders =
        HttpHeaders(list.filterNot { it.first.equals(name, ignoreCase = true) })

    operator fun plus(entry: Pair<String, String>): HttpHeaders =
        HttpHeaders(list + entry)

    override fun iterator(): Iterator<Pair<String, String>> = list.iterator()

    override fun equals(other: Any?): Boolean = other is HttpHeaders && other.list == list

    override fun hashCode(): Int = list.hashCode()

    override fun toString(): String =
        list.joinToString("\n") { (name, value) -> "$name: $value" }

    class Builder {
        private val entries = mutableListOf<Pair<String, String>>()

        /** Appends a value keeping multi-value semantics (e.g. `Set-Cookie`). */
        fun add(name: String, value: String): Builder = apply {
            requireValidName(name)
            entries += name to value
        }

        /** Removes all values of [name] and appends a single [value]. */
        fun set(name: String, value: String): Builder = apply {
            requireValidName(name)
            removeAll(name)
            entries += name to value
        }

        fun remove(name: String): Builder = apply {
            removeAll(name)
        }

        fun addAll(headers: HttpHeaders): Builder = apply {
            headers.forEach { (name, value) -> add(name, value) }
        }

        fun addIfNotBlank(name: String, value: String?): Builder = apply {
            if (!value.isNullOrBlank()) add(name, value)
        }

        fun build(): HttpHeaders = HttpHeaders(entries.toList())

        private fun removeAll(name: String) {
            entries.removeAll { it.first.equals(name, ignoreCase = true) }
        }

        private fun requireValidName(name: String) {
            require(name.isNotBlank()) { "header name must not be blank" }
            require(!name.any { it == '\n' || it == '\r' || it == ':' }) {
                "header name contains forbidden characters: '$name'"
            }
        }
    }

    companion object {
        val EMPTY: HttpHeaders = HttpHeaders(emptyList())

        fun of(vararg entries: Pair<String, String>): HttpHeaders =
            Builder().apply { entries.forEach { add(it.first, it.second) } }.build()

        operator fun invoke(build: Builder.() -> Unit): HttpHeaders = Builder().apply(build).build()

        fun builder(): Builder = Builder()

        // Frequently used header names.
        const val ACCEPT = "Accept"
        const val AUTHORIZATION = "Authorization"
        const val CONTENT_LENGTH = "Content-Length"
        const val CONTENT_TYPE = "Content-Type"
        const val COOKIE = "Cookie"
        const val SET_COOKIE = "Set-Cookie"
        const val USER_AGENT = "User-Agent"

        const val CONTENT_TYPE_JSON = "application/json"
    }
}
