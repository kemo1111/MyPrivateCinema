package com.phisher98

import com.phisher98.StreamPlayExtractor.invokeVidSrcXyz
import com.phisher98.StreamPlayExtractor.invokeVidlink
import com.phisher98.StreamPlayExtractor.invokeVidzee
import com.phisher98.StreamPlayExtractor.invokeVideasy
import com.phisher98.StreamPlayExtractor.invokeWatchsomuch
import com.phisher98.StreamPlayExtractor.invokeAllMovieland
import com.phisher98.StreamPlayExtractor.invokeMoviesmod
import com.phisher98.StreamPlayExtractor.invokeMoviesdrive
import com.phisher98.StreamPlayExtractor.invokeBollyflix
import com.phisher98.StreamPlayExtractor.invokeVegamovies
import com.phisher98.StreamPlayExtractor.invokeRogmovies
import com.phisher98.StreamPlayExtractor.invokeHdmovie2
import com.phisher98.StreamPlayExtractor.invoke4khdhub
import com.phisher98.StreamPlayExtractor.invokehdhub4u
import com.phisher98.StreamPlayExtractor.invokeDudefilms
import com.phisher98.StreamPlayExtractor.invokeM4uhd
import com.phisher98.StreamPlayExtractor.invokeCineVood
import com.phisher98.StreamPlayExtractor.invokeFilmyfiy
import com.phisher98.StreamPlayExtractor.invokeMovies4u
import com.phisher98.StreamPlayExtractor.invokeHindmoviez
import com.phisher98.StreamPlayExtractor.invokeZinkmovies
import com.phisher98.StreamPlayExtractor.invokeZshow
import com.phisher98.StreamPlayExtractor.invokeHianime
import com.phisher98.StreamPlayExtractor.invokeAnimex
import com.phisher98.StreamPlayExtractor.invokeReAnime
import com.phisher98.StreamPlayExtractor.invokeKisskh
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
                        "moviesmod" -> invokeMoviesmod("tt0133093", null, null, sub, cb)
                        "moviesdrive" -> invokeMoviesdrive("tt0133093", null, null, sub, cb)
                        "bollyflix" -> invokeBollyflix("tt0133093", null, null, sub, cb)
                        "vegamovies" -> invokeVegamovies("The Matrix", "tt0133093", null, null, sub, cb)
                        "rogmovies" -> invokeRogmovies("The Matrix", "tt0133093", null, null, sub, cb)
                        "hdmovie2" -> invokeHdmovie2("The Matrix", 1999, null, sub, cb)
                        "4khdhub" -> invoke4khdhub("The Matrix", 1999, null, null, sub, cb)
                        "hdhub4u" -> invokehdhub4u("tt0133093", "The Matrix", 1999, null, null, sub, cb)
                        "dudefilms" -> invokeDudefilms("tt0133093", null, null, sub, cb)
                        "m4uhd" -> invokeM4uhd("The Matrix", null, null, sub, cb)
                        "cinevood" -> invokeCineVood("tt0133093", sub, cb)
                        "filmyfiy" -> invokeFilmyfiy("The Matrix", sub, cb)
                        "movies4u" -> invokeMovies4u("tt0133093", "The Matrix", 1999, null, null, sub, cb)
                        "hindmoviez" -> invokeHindmoviez("tt0133093", null, null, sub, cb)
                        "zinkmovies" -> invokeZinkmovies("The Matrix", 1999, null, null, sub, cb)
                        "zshow" -> invokeZshow("The Matrix", 1999, null, null, sub, cb)
                        "hianime" -> invokeHianime(16498, 1, sub, cb, "SUB")
                        "animex" -> invokeAnimex(16498, 16498, "Attack on Titan", 1, sub, cb, "SUB")
                        "reanime" -> invokeReAnime(16498, 1, sub, cb, "SUB")
                        "kisskh" -> invokeKisskh("Squid Game", 1, 1, 1, sub, cb)
                        else -> error("unknown")
                    }; true } }
            } catch (e: Throwable) { err = e.javaClass.simpleName + ": " + (e.message ?: "").take(100); false }
            val sb = StringBuilder("$n done=${done == true} timeout=${done == null} links=${links.size} ms=${System.currentTimeMillis() - t0} err=$err\n")
            links.take(2).forEach { l ->
                val body = try { runBlocking { withTimeoutOrNull(15000) { app.get(l.url, referer = l.referer, headers = l.headers, timeout = 10).text } } } catch (e: Throwable) { null }
                val lines = (body ?: "").lines()
                sb.append("  ${l.source} | ${l.name} | q=${l.quality} | ${l.type} | ${l.url.take(60)} FULL=${l.url} REF=${l.referer} HDR=${l.headers} | first=${lines.firstOrNull()?.take(10)} streamInf=${lines.count { it.startsWith("#EXT-X-STREAM-INF") }} extinf=${lines.count { it.startsWith("#EXTINF") }}\n")
            }
            out.appendText(sb.toString())
        }
    }
}
