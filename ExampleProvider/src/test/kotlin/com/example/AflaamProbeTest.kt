package com.example

import com.lagradost.cloudstream3.*
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

// Opt-in live probe: runs only if /tmp/aflaam_probe.txt exists. Writes /tmp/aflaam_results.txt.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AflaamProbeTest {
    @Test fun probe() {
        assumeTrue(File("/tmp/aflaam_probe.txt").exists())
        val sb = StringBuilder(); val api = AflaamProvider()
        runBlocking {
            val mp = api.getMainPage(1, api.mainPage[0].let { MainPageRequest(it.name, it.data, false) }); sb.append("main ${mp.items.firstOrNull()?.list?.size}\n")
            val sr = api.search("Hereditary"); sb.append("search ${sr.size} ${sr.firstOrNull()?.name} ${sr.firstOrNull()?.url}\n")
            val mv = api.load(sr.first { it.url.contains("/movie/") }.url) as MovieLoadResponse
            sb.append("movie ${mv.name} year=${mv.year} data=${mv.dataUrl}\n")
            val ml = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
            sb.append("movie loadLinks=${api.loadLinks(mv.dataUrl, false, {}, { ml.add(it) })}\n")
            ml.forEach { sb.append("  ${it.name} q=${it.quality} ${it.url} ref=${it.referer}\n") }
            val ss = api.getMainPage(1, api.mainPage[3].let { MainPageRequest(it.name, it.data, false) }).items.first().list.first { it.url.contains("/series/") }
            val tv = api.load(ss.url) as TvSeriesLoadResponse
            sb.append("series ${tv.name} eps=${tv.episodes.size} first=${tv.episodes.first().data}\n")
            val el = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
            sb.append("episode loadLinks=${api.loadLinks(tv.episodes.first().data, false, {}, { el.add(it) })}\n")
            el.forEach { sb.append("  ${it.name} q=${it.quality} ${it.url} ref=${it.referer}\n") }
        }
        File("/tmp/aflaam_results.txt").writeText(sb.toString())
    }
}
