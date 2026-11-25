package com.example

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Fixtures are excerpts of live cima-time.com HTML saved on 2026-11-25.
class CimaTimeParserTest {
    private fun res(n: String) = javaClass.classLoader!!.getResourceAsStream(n)!!.readBytes().toString(Charsets.UTF_8)

    @Test fun gridCards() {
        val cards = CimaTimeParser.cards(Jsoup.parse(res("cimatime_grid.html"), "https://cima-time.com/"))
        assertEquals(2, cards.size)
        assertTrue(cards[0].url.startsWith("https://cima-time.com/watch.php?vid="))
        assertTrue(cards[0].title.contains("The Dark Knight Rises"))
        assertTrue(cards[0].poster!!.contains("/uploads/thumbs/"))
        assertEquals(2012, CimaTimeParser.year(cards[0].title))
        assertEquals("The Dark Knight Rises 2012", CimaTimeParser.cleanTitle(cards[0].title))
    }

    @Test fun episodesAscending() {
        val eps = CimaTimeParser.episodes(Jsoup.parse(res("cimatime_season.html"), "https://cima-time.com/"))
        assertEquals((1..10).toList(), eps.map { it.episode })
        assertTrue(eps.all { it.url.contains("watch.php?vid=") })
    }

    @Test fun serverEmbedsInPageOrder() {
        val e = CimaTimeParser.embeds(Jsoup.parse(res("cimatime_play.html"), "https://cima-time.com/"))
        assertEquals(listOf("Vk", "Voe", "Mp4upload"), e.map { it.first })
        assertTrue(e.last().second.startsWith("https://www.mp4upload.com/embed-"))
    }

    @Test fun episodeTitleAndNumber() {
        assertTrue(CimaTimeParser.isEpisodeTitle("مسلسل Dark Matter الموسم الثاني الحلقة 7 السابعة مترجمة"))
        assertFalse(CimaTimeParser.isEpisodeTitle("فيلم Batman The Dark Knight 2008 مترجم اون لاين"))
        assertEquals(7, CimaTimeParser.episodeNumber("الحلقة 7 السابعة"))
        assertEquals(null, CimaTimeParser.episodeNumber("الموسم الثاني"))
    }

    @Test fun emptyPage() {
        val d = Jsoup.parse("<html><body></body></html>", "https://cima-time.com/")
        assertTrue(CimaTimeParser.cards(d).isEmpty())
        assertTrue(CimaTimeParser.episodes(d).isEmpty())
        assertTrue(CimaTimeParser.embeds(d).isEmpty())
    }
}
