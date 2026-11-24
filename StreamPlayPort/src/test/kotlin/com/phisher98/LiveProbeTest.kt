package com.phisher98

import com.phisher98.StreamPlayExtractor.invokeVidSrcXyz
import com.phisher98.StreamPlayExtractor.invokeVidlink
import com.phisher98.StreamPlayExtractor.invokeVidzee
import com.phisher98.StreamPlayExtractor.invokeVideasy
import com.phisher98.StreamPlayExtractor.invokeWatchsomuch
import com.phisher98.StreamPlayExtractor.invokeAllMovieland
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// Opt-in live probe (runs only if /tmp/live_sources.txt exists). Public fixture: The Matrix, tmdb 603, tt0133093.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class LiveProbeTest {
    @Test fun probe() {
        val f = File("/tmp/live_sources.txt"); assumeTrue(f.exists())
        val out = File("/tmp/live_results.txt")
        for (n in f.readLines().map { it.trim() }.filter { it.isNotEmpty() }) {
            val links = mutableListOf<ExtractorLink>()
            val cb: (ExtractorLink) -> Unit = { links.add(it) }
            val sub: (SubtitleFile) -> Unit = {}
            var err = ""
            val t0 = System.currentTimeMillis()
            val done = try {
                runBlocking { withTimeoutOrNull(30000) {
                    when (n) {
                        "vidsrcxyz" -> invokeVidSrcXyz("tt0133093", null, null, cb)
                        "vidlink" -> invokeVidlink(603, null, null, cb)
                        "vidzee" -> invokeVidzee(603, null, null, sub, cb)
                        "videasy" -> invokeVideasy("The Matrix", 603, "tt0133093", 1999, null, null, sub, cb)
                        "watchsomuch" -> invokeWatchsomuch("tt0133093", null, null, sub)
                        "allmovieland" -> invokeAllMovieland("tt0133093", null, null, cb)
                        else -> error("unknown")
                    }; true } }
            } catch (e: Throwable) { err = e.javaClass.simpleName + ": " + (e.message ?: "").take(100); false }
            val sb = StringBuilder("$n done=${done == true} timeout=${done == null} links=${links.size} ms=${System.currentTimeMillis() - t0} err=$err\n")
            links.take(2).forEach { l ->
                val body = try { runBlocking { withTimeoutOrNull(15000) { app.get(l.url, referer = l.referer, headers = l.headers, timeout = 10).text } } } catch (e: Throwable) { null }
                val lines = (body ?: "").lines()
                sb.append("  ${l.source} | ${l.name} | q=${l.quality} | ${l.type} | ${l.url.take(60)} | first=${lines.firstOrNull()?.take(10)} streamInf=${lines.count { it.startsWith("#EXT-X-STREAM-INF") }} extinf=${lines.count { it.startsWith("#EXTINF") }}\n")
            }
            out.appendText(sb.toString())
        }
    }
}
