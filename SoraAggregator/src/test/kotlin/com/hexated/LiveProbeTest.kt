package com.hexated

import com.hexated.SoraExtractor.invokeVidSrc
import com.hexated.SoraExtractor.invokeVidsrcto
import com.hexated.SoraExtractor.invoke2embed
import com.hexated.SoraExtractor.invokeMoflix
import com.hexated.SoraExtractor.invokeAllMovieland
import com.hexated.SoraExtractor.invokeZoechip
import com.hexated.SoraExtractor.invokeNowTv
import com.hexated.SoraExtractor.invokeWatchsomuch
import com.hexated.SoraExtractor.invokeShowflix
import com.hexated.SoraExtractor.invokeUhdmovies
import com.hexated.SoraExtractor.invokeDahmerMovies
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// Optional live probe: runs only when /tmp/live_sources.txt exists. Public fixture: The Matrix (tmdb 603, imdb tt0133093).
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LiveProbeTest {
    @Test fun probe() {
        val f = File("/tmp/live_sources.txt"); assumeTrue(f.exists())
        val names = f.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val out = File("/tmp/live_results.txt")
        for (n in names) {
            val links = mutableListOf<ExtractorLink>()
            val cb: (ExtractorLink) -> Unit = { links.add(it) }
            val sub: (com.lagradost.cloudstream3.SubtitleFile) -> Unit = {}
            var err = ""
            val t0 = System.currentTimeMillis()
            val done = try {
                runBlocking { withTimeoutOrNull(30000) {
                    when (n) {
                        "vidsrc" -> invokeVidSrc(603, null, null, cb)
                        "vidsrcto" -> invokeVidsrcto("tt0133093", null, null, sub, cb)
                        "2embed" -> invoke2embed("tt0133093", null, null, sub, cb)
                        "moflix" -> invokeMoflix(603, null, null, cb)
                        "allmovieland" -> invokeAllMovieland("tt0133093", null, null, cb)
                        "zoechip" -> invokeZoechip("The Matrix", 1999, null, null, cb)
                        "nowtv" -> invokeNowTv(603, "tt0133093", null, null, cb)
                        "watchsomuch" -> invokeWatchsomuch("tt0133093", null, null, sub)
                        "showflix" -> invokeShowflix("The Matrix", 1999, null, null, sub, cb)
                        "uhdmovies" -> invokeUhdmovies("The Matrix", 1999, null, null, cb)
                        "dahmer" -> invokeDahmerMovies("The Matrix", 1999, null, null, cb)
                        else -> error("unknown")
                    }
                    true
                } }
            } catch (e: Throwable) { err = e.javaClass.simpleName + ": " + (e.message ?: "").take(100); false }
            val sb = StringBuilder("$n done=${done == true} timeout=${done == null} links=${links.size} ms=${System.currentTimeMillis() - t0} err=$err\n")
            links.take(3).forEach { l ->
                val code = try { runBlocking { withTimeoutOrNull(15000) { app.get(l.url, referer = l.referer, headers = l.headers, timeout = 10).code } } } catch (e: Throwable) { -1 }
                sb.append("  ${l.source} | ${l.name} | q=${l.quality} | ${l.type} | head=${l.url.take(70)} | http=$code\n")
            }
            out.appendText(sb.toString())
        }
    }
}
