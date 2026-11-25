package com.example

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MyCimaParserTest {
    private fun doc(n: String) = Jsoup.parse(javaClass.getResourceAsStream("/mycima/$n")!!.readBytes().toString(Charsets.UTF_8))

    @Test fun movieCards() {
        val cards = doc("list.html").select(MyCimaParser.CARD).mapNotNull { MyCimaParser.card(it) }
        assertEquals(3, cards.size)
        cards.forEach {
            assertEquals(MyCimaParser.Kind.MOVIE, it.type)
            assertTrue(it.url.startsWith("https://wecimas.live/movies/"))
            assertNotNull(it.poster)
            assertTrue(it.title.isNotBlank())
        }
    }

    @Test fun seriesCards() {
        val cards = doc("list_tv.html").select(MyCimaParser.CARD).mapNotNull { MyCimaParser.card(it) }
        assertEquals(2, cards.size)
        cards.forEach { assertEquals(MyCimaParser.Kind.SERIES, it.type) }
    }

    @Test fun ownServersOnly() {
        val s = MyCimaParser.servers(doc("movie.html"))
        assertTrue(s.isNotEmpty())
        assertTrue(s.all { it.startsWith("http") })
        assertEquals(s.size, s.distinct().size)
    }

    @Test fun movieDetailHasNoEpisodes() {
        val d = MyCimaParser.detail(doc("movie.html"))
        assertTrue(d.episodes.isEmpty())
    }

    @Test fun seriesEpisodesSortedAndNumbered() {
        val d = MyCimaParser.detail(doc("series.html"))
        assertEquals(5, d.episodes.size)
        assertEquals(listOf(1, 2, 3, 4, 5), d.episodes.map { it.number })
        assertTrue(d.episodes.all { it.url.contains("/episodes/") })
    }

    @Test fun emptyPageGivesNothing() {
        val e = Jsoup.parse("<html></html>")
        assertTrue(e.select(MyCimaParser.CARD).mapNotNull { MyCimaParser.card(it) }.isEmpty())
        assertTrue(MyCimaParser.servers(e).isEmpty())
    }
}
