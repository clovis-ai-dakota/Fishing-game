package com.deepline.fishing.server

/**
 * Counts actions per key in a sliding window ("at most [limit] in [windowMs]"). In memory: a
 * restart forgets the counts, which is fine for abuse prevention. Thread-safe.
 */
class RateLimiter(private val clock: () -> Long = System::currentTimeMillis) {
    private val hits = HashMap<String, ArrayDeque<Long>>()

    /** Records one action for [key] and returns true, or returns false if it's over the limit. */
    @Synchronized
    fun allow(key: String, limit: Int, windowMs: Long): Boolean {
        val now = clock()
        val q = hits.getOrPut(key) { ArrayDeque() }
        while (q.isNotEmpty() && q.first() <= now - windowMs) q.removeFirst()
        if (q.size >= limit) return false
        q.addLast(now)
        // Keep the map small: drop keys nobody used for a while.
        if (hits.size > MAX_KEYS) hits.entries.removeAll { it.value.isEmpty() || it.value.last() <= now - DAY_MS }
        return true
    }

    private companion object {
        const val MAX_KEYS = 100_000
        const val DAY_MS = 86_400_000L
    }
}
