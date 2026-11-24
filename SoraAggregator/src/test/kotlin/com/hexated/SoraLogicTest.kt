package com.hexated

import android.app.Activity
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class SoraLogicTest {
    private val key = "0123456789abcdef0123456789ABCDEF"

    @Before fun setup() {
        TmdbKey.init(Robolectric.buildActivity(Activity::class.java).setup().get())
        TmdbKey.clear()
    }

    @Test fun wrapperPreservesRefererQualityHeadersType() {
        val link = newExtractorLinkBlocking("Src", "Name", "https://x.test/a.m3u8", type = ExtractorLinkType.M3U8) {
            this.referer = "https://ref.test/"
            this.quality = 1080
            this.headers = mapOf("User-Agent" to "ua")
        }
        assertEquals("Src", link.source)
        assertEquals("Name", link.name)
        assertEquals("https://x.test/a.m3u8", link.url)
        assertEquals(ExtractorLinkType.M3U8, link.type)
        assertEquals("https://ref.test/", link.referer)
        assertEquals(1080, link.quality)
        assertEquals("ua", link.headers["User-Agent"])
    }

    @Test fun wrapperDefaultsWithoutInitializer() {
        val link = newExtractorLinkBlocking("S", "N", "https://x.test/v.mp4")
        assertEquals("https://x.test/v.mp4", link.url)
        assertEquals(ExtractorLinkType.VIDEO, link.type)
    }

    @Test fun mainPageUsesRuntimeKeyAndNeverEmptyKey() {
        assertTrue(TmdbKey.set(key))
        val pages = SoraStream().mainPage
        assertTrue(pages.isNotEmpty())
        pages.forEach {
            assertTrue(it.data, it.data.contains("api_key=$key"))
            assertFalse(it.data, it.data.contains("api_key=&"))
            assertFalse(it.data, it.data.endsWith("api_key="))
        }
        TmdbKey.clear()
        // after clearing, URLs must not keep the old key
        SoraStream().mainPage.forEach { assertFalse(it.data.contains(key)) }
    }

    @Test fun noKeyGivesReadableErrorBeforeAnyNetworkCall() {
        val p = SoraStream()
        val e1 = try { runBlocking { p.getMainPage(1, MainPageRequest("t", "https://api.themoviedb.org/3/movie/popular?api_key=&region=US", false)) }; null } catch (e: ErrorLoadingException) { e }
        assertNotNull(e1)
        assertTrue(e1!!.message!!.contains("TMDB API key"))
        val e2 = try { runBlocking { p.search("matrix") }; null } catch (e: ErrorLoadingException) { e }
        assertNotNull(e2)
        val e3 = try { runBlocking { p.load("https://api.themoviedb.org/3/movie/603") }; null } catch (e: ErrorLoadingException) { e }
        assertNotNull(e3)
    }
}
