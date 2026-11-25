package com.example

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fixtures are excerpts of live ai.cimamix.tv pages and embed pages saved on 2026-11-25.
class CimamixTest {
    private fun res(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.readBytes().toString(Charsets.UTF_8)
    private val base = "https://ai.cimamix.tv/"

    @Test fun gridCardsParse() {
        val cards = CimaTimeParser.cards(Jsoup.parse(res("cimamix_grid.html"), base))
        assertTrue(cards.size >= 5)
        assertTrue(cards.all { it.url.startsWith("https://ai.cimamix.tv/watch.php?vid=") && it.title.isNotBlank() })
        assertTrue(cards.any { it.title.contains("House of the Dragon") })
    }

    @Test fun seasonEpisodesAscending() {
        val eps = CimaTimeParser.episodes(Jsoup.parse(res("cimamix_season.html"), base))
        assertEquals(listOf(1, 2, 3, 4), eps.map { it.episode })
    }

    @Test fun playPageServers() {
        val e = CimaTimeParser.embeds(Jsoup.parse(res("cimamix_play.html"), base))
        assertEquals(5, e.size)
        assertTrue(e.any { it.first == "Anafast" && it.second.startsWith("https://anafast.org/embed-") })
    }

    @Test fun plainEmbedPageGivesMasterPlaylist() {
        val u = CimamixHls.findM3u8(res("cimamix_anafast_embed.html"))
        assertNotNull(u)
        assertTrue(u!!.contains("master.m3u8"))
    }

    @Test fun packedEmbedPageGivesMasterPlaylist() {
        val u = CimamixHls.findM3u8(res("cimamix_vidoba_embed.html"))
        assertNotNull(u)
        assertTrue(u!!.contains(".m3u8"))
    }

    @Test fun pageWithoutPlaylistGivesNothing() {
        assertEquals(null, CimamixHls.findM3u8("<html><body>file removed</body></html>"))
    }

    @Test fun cimamixMatchesSameFilmOnly() {
        assertFalse(CrossServers.isIsolated("Cimamix Kemo"))
        assertTrue(CrossServers.sameFilm("فيلم Toy Story 5 2026 مترجم", 2026, "Toy Story 5", 2026))
        assertFalse(CrossServers.sameFilm("فيلم Toy Story 5 2026 مترجم", 2026, "Toy Story 4", 2019))
    }
}
