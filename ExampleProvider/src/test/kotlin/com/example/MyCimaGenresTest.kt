package com.example

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class MyCimaGenresTest {
    @Test fun twentyThreeUniqueGenres() {
        assertEquals(23, MyCimaGenres.all.size)
        assertEquals(23, MyCimaGenres.all.map { it.slug }.distinct().size)
    }

    @Test fun everyLabelMatchesItself() {
        MyCimaGenres.all.forEach { assertEquals(it, MyCimaGenres.match(it.label)) }
    }

    @Test fun prefixAndSpacingAndHamza() {
        assertEquals("دراما", MyCimaGenres.match("  تصنيف دراما ")?.label)
        assertEquals("إثارة", MyCimaGenres.match("اثارة")?.label)
        assertEquals("حركة", MyCimaGenres.match("حركة")?.label)
        assertEquals("حركة ومغامرة", MyCimaGenres.match("حركة ومغامرة")?.label)
    }

    @Test fun ordinaryQueriesAreNotGenres() {
        assertNull(MyCimaGenres.match("batman"))
        assertNull(MyCimaGenres.match("دراما كورية"))
        assertNull(MyCimaGenres.match(""))
    }

    @Test fun hasNextFollowsRelNext() {
        assertTrue(MyCimaParser.hasNext(Jsoup.parse("<html><head><link rel=\"next\" href=\"/p/2/\"></head></html>")))
        assertFalse(MyCimaParser.hasNext(Jsoup.parse("<html><head><link rel=\"prev\" href=\"/p/1/\"></head></html>")))
    }
}
