package cn.mafei.cmp.data.repositories

/** Owned exclusively by MediaRepository's mutex; no network or suspension inside this container. */
internal class MediaCache<K, V>(private val capacity: Int) {
    private val values = linkedMapOf<K, Entry<V>>()
    data class Entry<V>(val value: V, val fetchedAt: Long) {
        fun age(now: Long): Long? {
            if (now < fetchedAt) return null // wall-clock rollback invalidates freshness
            return (now - fetchedAt).takeIf { it >= 0 } // overflow is not fresh either
        }
    }
    fun get(key: K): Entry<V>? = values.remove(key)?.also { values[key] = it }
    fun put(key: K, value: V, now: Long) {
        values.remove(key)
        values[key] = Entry(value, now)
        while (values.size > capacity) values.remove(values.keys.first())
    }
    fun remove(key: K) { values.remove(key) }
    fun clear() { values.clear() }
}
