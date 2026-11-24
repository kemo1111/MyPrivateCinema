package com.phisher98

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.SubtitleFile
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

// Opt-in (runs only if /tmp/provider_probe.txt exists): search -> load -> first episode -> loadLinks via the provider.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class ProviderPathProbeTest {
    @Test fun probe() {
        val f = File("/tmp/provider_probe.txt"); assumeTrue(f.exists())
        val q = f.readText().trim()
        val out = File("/tmp/provider_results.txt"); out.writeText("")
        // StreamPlayAnime.<init> pulls app-only classes (coil); bypass the constructor, loadLinks does not use instance state.
        val uc = Class.forName("sun.misc.Unsafe")
        val uf = uc.getDeclaredField("theUnsafe"); uf.isAccessible = true
        val api = uc.getMethod("allocateInstance", Class::class.java).invoke(uf.get(null), StreamPlayAnime::class.java) as StreamPlayAnime
        val sb = StringBuilder()
        runBlocking {
            val links = mutableListOf<ExtractorLink>()
            val done = withTimeoutOrNull(100000) {
                api.loadLinks(q, false, { _: SubtitleFile -> }, { links.add(it) }); true
            }
            sb.append("loadLinks done=${done == true} links=${links.size}\n")
            links.forEach { l -> sb.append("  ${l.source} | ${l.name} | q=${l.quality} | ${l.type} | ${l.url} | ref=${l.referer} | hdr=${l.headers}\n") }
        }
        out.writeText(sb.toString())
    }
}
