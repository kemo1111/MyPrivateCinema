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
}
