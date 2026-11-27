package com.example

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Fixtures are pages saved from www.getcima.net and mixdrop.top on 2026-11-27.
class GetCimaTest {
    private fun res(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.readBytes().toString(Charsets.UTF_8)
    private val base = "https://www.getcima.net/"

    @Test fun gridCards() {
        val doc = Jsoup.parse(res("getcima_grid.html"), base)
        val c = GetCimaParser.cards(doc)
        assertEquals(6, c.size)
        assertTrue(c.all { it.url.startsWith("https://www.getcima.net/movie/") && it.title.isNotBlank() })
        assertTrue(c.all { it.poster?.startsWith("https://www.getcima.net/wp-content/") == true })
        assertTrue(GetCimaParser.hasNext(doc))
    }

    @Test fun movieHasPlayLinkAndPoster() {
        val doc = Jsoup.parse(res("getcima_movie.html"), base)
        assertEquals("Victoria & Abdul", GetCimaParser.title(doc))
        assertEquals("https://www.getcima.net/play/victoria-abdul/", GetCimaParser.playLink(doc))
        assertNotNull(GetCimaParser.poster(doc))
    }

    @Test fun seriesEpisodes() {
        val doc = Jsoup.parse(res("getcima_series.html"), base)
        val eps = GetCimaParser.episodes(doc)
        assertTrue(eps.size >= 3)
        assertEquals(1, eps.first().season)
        assertEquals(1, eps.first().number)
        assertTrue(eps.first().url.contains("/season-1/"))
        assertTrue(GetCimaParser.seasonUrls(doc).any { it.endsWith("?season=2") })
    }

    @Test fun playerSourcesAndEmbed() {
        val doc = Jsoup.parse(res("getcima_player.html"), base)
        assertEquals(7, GetCimaParser.playerSources(doc).size)
        assertEquals("https://hgcloud.to/e/agi6eoufnvtk", GetCimaParser.embed(doc))
    }

    @Test fun mixdropEmbedGivesMp4() {
        val u = GetCimaMixdropParse.mp4(res("getcima_mixdrop_embed.html"))
        assertNotNull(u)
        assertTrue(u!!.startsWith("https://") && u.contains(".mp4"))
    }

    @Test fun mixdropWithoutPlayerGivesNothing() {
        assertEquals(null, GetCimaMixdropParse.mp4("<html>removed</html>"))
    }
}
