package com.example

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class ListingLoaderTest {
    private fun items(n: Int) = (1..n).map { "item$it" }

    @Test
    fun neverMoreThanPermitsRunAtOnce() = runBlocking {
        val loader = ListingLoader<String>(permits = 3)
        val running = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val results = (1..10).map { i ->
            async {
                loader.load("k$i") {
                    val now = running.incrementAndGet()
                    peak.updateAndGet { maxOf(it, now) }
                    delay(40)
                    running.decrementAndGet()
                    items(12)
                }
            }
        }.awaitAll()
        assertEquals(3, peak.get())
        assertTrue(results.all { it.size == 12 })
    }

    @Test
    fun retrySucceedsOnSecondAttempt() = runBlocking {
        val loader = ListingLoader<String>()
        val calls = AtomicInteger(0)
        val r = loader.load("k") { if (calls.incrementAndGet() == 1) throw RuntimeException("timeout") else items(24) }
        assertEquals(24, r.size)
        assertEquals(2, calls.get())
    }

    @Test
    fun retryStopsAfterConfiguredAttemptsAndReturnsEmptyWithoutThrowing() = runBlocking {
        val loader = ListingLoader<String>(attempts = 2)
        val calls = AtomicInteger(0)
        val r = loader.load("k") { calls.incrementAndGet(); throw RuntimeException("boom") }
        assertTrue(r.isEmpty())
        assertEquals(2, calls.get())
    }

    @Test
    fun emptyResponseIsRetriedToo() = runBlocking {
        val loader = ListingLoader<String>()
        val calls = AtomicInteger(0)
        val r = loader.load("k") { if (calls.incrementAndGet() == 1) emptyList() else items(15) }
        assertEquals(15, r.size)
        assertEquals(2, calls.get())
    }

    @Test
    fun cancellationPropagatesAndIsNotSwallowedOrRetried() = runBlocking {
        val loader = ListingLoader<String>()
        val calls = AtomicInteger(0)
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            loader.load("k") { calls.incrementAndGet(); delay(60_000); items(12) }
        }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        assertEquals(1, calls.get())
        // a fetch that throws CancellationException itself is rethrown, not turned into an empty list
        var thrown = false
        try {
            loader.load("k2") { throw CancellationException("cancel") }
        } catch (e: CancellationException) {
            thrown = true
        }
        assertTrue(thrown)
    }

    @Test
    fun cacheHitSkipsFetchAndExpires() = runBlocking {
        var now = 1_000L
        val loader = ListingLoader<String>(cacheMs = 600_000L, clock = { now })
        val calls = AtomicInteger(0)
        val fetch: suspend () -> List<String> = { calls.incrementAndGet(); items(24) }
        loader.load("k", fetch)
        loader.load("k", fetch)
        assertEquals(1, calls.get())
        now += 599_000L
        loader.load("k", fetch)
        assertEquals(1, calls.get())
        now += 2_000L
        loader.load("k", fetch)
        assertEquals(2, calls.get())
    }

    @Test
    fun failedEmptyAndSmallResponsesAreNotCached() = runBlocking {
        val loader = ListingLoader<String>(attempts = 1, minCacheSize = 10)
        val calls = AtomicInteger(0)
        loader.load("fail") { calls.incrementAndGet(); throw RuntimeException("x") }
        loader.load("fail") { calls.incrementAndGet(); throw RuntimeException("x") }
        assertEquals(2, calls.get())
        loader.load("empty") { calls.incrementAndGet(); emptyList() }
        loader.load("empty") { calls.incrementAndGet(); emptyList() }
        assertEquals(4, calls.get())
        loader.load("small") { calls.incrementAndGet(); items(3) }
        loader.load("small") { calls.incrementAndGet(); items(3) }
        assertEquals(6, calls.get())
        // after a failure the next call can succeed and is then cached
        loader.load("fail") { calls.incrementAndGet(); items(24) }
        loader.load("fail") { calls.incrementAndGet(); items(24) }
        assertEquals(7, calls.get())
    }

    @Test
    fun overallBudgetStopsQueuedRowsAndNothingIsCached() = runBlocking {
        val loader = ListingLoader<String>(permits = 1, budgetMs = 150L)
        val started = AtomicInteger(0)
        val t0 = System.currentTimeMillis()
        val results = (1..6).map { i ->
            async { loader.load("k$i") { started.incrementAndGet(); delay(100); items(12) } }
        }.awaitAll()
        val elapsed = System.currentTimeMillis() - t0
        assertTrue("budget must bound the whole call, took $elapsed ms", elapsed < 600)
        assertTrue("some queued rows must have been given up", results.count { it.isEmpty() } >= 3)
        assertTrue("rows that started and finished are returned", results.any { it.size == 12 })
        // a row given up on is not cached: a new loader call fetches again
        val again = AtomicInteger(0)
        val late = results.indexOfFirst { it.isEmpty() } + 1
        loader.load("k$late") { again.incrementAndGet(); items(12) }
        assertEquals(1, again.get())
    }
}
