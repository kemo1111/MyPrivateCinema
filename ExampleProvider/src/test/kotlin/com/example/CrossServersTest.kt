package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossServersTest {
    @Test fun sameTitleDifferentSiteDecorations() {
        assertTrue(CrossServers.sameFilm("فيلم الفيل الأزرق مترجم اون لاين", 2014, "الفيل الازرق", 2014))
        assertTrue(CrossServers.sameFilm("فيلم Inception 2010 مترجم", 2010, "Inception", null))
        assertTrue(CrossServers.sameFilm("مشاهدة فيلم الجزيرة (2007) اون لاين", 2007, "الجزيره", 2007))
    }
    @Test fun differentYearOrTitleRejected() {
        assertFalse(CrossServers.sameFilm("Joker", 2019, "Joker", 1999))
        assertFalse(CrossServers.sameFilm("Joker", 2019, "Joker Folie a Deux", 2024))
        assertFalse(CrossServers.sameFilm("", null, "x", null))
    }
    @Test fun normalizeStripsNoise() {
        assertEquals("inception", CrossServers.normalize("فيلم Inception (2010) مترجم HD"))
    }
    private data class Site(val name: String, val plugin: String?)
    private val sites = listOf(
        Site("ArabSeed Kemo", "p"), Site("MyCima Kemo", "p"), Site("Akwam Kemo", "p"),
        Site("Aflaam Kemo", "p"), Site("StreamPlay", "other")
    )
    private fun pick(self: String) =
        CrossServers.pickSiblings(self, "p", sites, { it.name }, { it.plugin }).map { it.name }

    @Test fun isolatedSiteIsNeverASibling() {
        val r = pick("ArabSeed Kemo")
        assertFalse(r.contains("MyCima Kemo"))
        assertEquals(listOf("Akwam Kemo", "Aflaam Kemo"), r)
    }
    @Test fun isolatedSiteSearchesNoSiblings() {
        assertTrue(CrossServers.isIsolated("MyCima Kemo"))
        assertEquals(emptyList<String>(), pick("MyCima Kemo"))
    }
    @Test fun otherSitesUnaffectedAndPluginScopeKept() {
        assertFalse(CrossServers.isIsolated("Akwam Kemo"))
        assertEquals(listOf("ArabSeed Kemo", "Aflaam Kemo"), pick("Akwam Kemo"))
        assertFalse(pick("Akwam Kemo").contains("StreamPlay"))
    }
    @Test fun cimaTimeTitlesMatchOnlyTheSameFilm() {
        assertTrue(CrossServers.sameFilm("فيلم Batman The Dark Knight 2008 مترجم اون لاين", 2008, "Batman The Dark Knight", 2008))
        assertFalse(CrossServers.sameFilm("فيلم The Dark Knight Rises 2012 مترجم اون لاين", 2012, "Batman The Dark Knight", 2008))
        assertFalse(CrossServers.sameFilm("Dark Matter", null, "مسلسل Dark Matter الموسم الثاني الحلقة 1 الاولي مترجمة", null))
    }
}
