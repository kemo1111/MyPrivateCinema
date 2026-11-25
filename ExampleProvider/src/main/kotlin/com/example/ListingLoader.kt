package com.example

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads listing pages for a home screen that asks for every row at once.
 * - at most [permits] fetches run at the same time (the rest wait their turn),
 * - a failed or empty fetch is retried up to [attempts] times in total,
 * - the whole call (waiting in the queue included) gives up after [budgetMs] and returns an empty list,
 *   so a long queue cannot push the app past its own whole-home deadline,
 * - exceptions never escape, except cancellation, which always propagates,
 * - results are remembered for [cacheMs] only when they hold at least [minCacheSize] items; empty,
 *   failed or small responses are never remembered (a small valid page is just re-fetched next time).
 */
class ListingLoader<T>(
    permits: Int = 3,
    private val attempts: Int = 2,
    private val budgetMs: Long = 90_000L,
    private val cacheMs: Long = 10 * 60 * 1000L,
    private val minCacheSize: Int = 10,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val gate = Semaphore(permits)
    private val cache = ConcurrentHashMap<String, Pair<Long, List<T>>>()

    suspend fun load(key: String, fetch: suspend () -> List<T>): List<T> {
        cache[key]?.let { (at, items) -> if (clock() - at < cacheMs) return items }
        val result = withTimeoutOrNull(budgetMs) {
            gate.withPermit {
                var items: List<T> = emptyList()
                for (attempt in 1..attempts) {
                    items = try {
                        fetch()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        emptyList()
                    }
                    if (items.isNotEmpty()) break
                }
                items
            }
        } ?: emptyList()
        if (result.size >= minCacheSize) cache[key] = clock() to result
        return result
    }
}
