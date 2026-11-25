package com.example

/** The 23 genre pages of wecimas.live. Reached through search because they are not home rows. */
object MyCimaGenres {
    data class Genre(val slug: String, val label: String)

    val all = listOf(
        Genre("%d8%af%d8%b1%d8%a7%d9%85%d8%a7", "دراما"),
        Genre("%d8%a5%d8%ab%d8%a7%d8%b1%d8%a9", "إثارة"),
        Genre("%d9%83%d9%88%d9%85%d9%8a%d8%af%d9%8a%d8%a7", "كوميديا"),
        Genre("%d8%ac%d8%b1%d9%8a%d9%85%d8%a9", "جريمة"),
        Genre("%d8%ad%d8%b1%d9%83%d8%a9", "حركة"),
        Genre("%d8%ba%d9%85%d9%88%d8%b6", "غموض"),
        Genre("%d8%b1%d8%b3%d9%88%d9%85-%d9%85%d8%aa%d8%ad%d8%b1%d9%83%d8%a9", "رسوم متحركة"),
        Genre("%d8%b1%d9%88%d9%85%d9%86%d8%b3%d9%8a%d8%a9", "رومنسية"),
        Genre("%d8%b1%d8%b9%d8%a8", "رعب"),
        Genre("%d8%ae%d9%8a%d8%a7%d9%84-%d8%b9%d9%84%d9%85%d9%8a-%d9%88%d9%81%d8%a7%d9%86%d8%aa%d8%a7%d8%b2%d9%8a%d8%a7", "خيال علمي وفانتازيا"),
        Genre("%d8%b9%d8%a7%d8%a6%d9%84%d9%8a", "عائلي"),
        Genre("%d9%85%d8%ba%d8%a7%d9%85%d8%b1%d8%a9", "مغامرة"),
        Genre("%d9%81%d8%a7%d9%86%d8%aa%d8%a7%d8%b2%d9%8a%d8%a7", "فانتازيا"),
        Genre("%d8%ae%d9%8a%d8%a7%d9%84-%d8%b9%d9%84%d9%85%d9%8a", "خيال علمي"),
        Genre("%d9%88%d8%ab%d8%a7%d8%a6%d9%82%d9%8a", "وثائقي"),
        Genre("%d8%ad%d8%b1%d9%83%d8%a9-%d9%88%d9%85%d8%ba%d8%a7%d9%85%d8%b1%d8%a9", "حركة ومغامرة"),
        Genre("%d8%aa%d8%a7%d8%b1%d9%8a%d8%ae", "تاريخ"),
        Genre("%d8%ad%d8%b1%d8%a8", "حرب"),
        Genre("%d9%85%d9%88%d8%b3%d9%8a%d9%82%d9%89", "موسيقى"),
        Genre("%d8%ba%d8%b1%d8%a8%d9%8a", "غربي"),
        Genre("%d9%81%d9%8a%d9%84%d9%85-%d8%aa%d9%84%d9%81%d8%a7%d8%b2%d9%8a", "فيلم تلفازي"),
        Genre("%d9%88%d8%a7%d9%82%d8%b9", "واقع"),
        Genre("%d8%a3%d9%88%d8%a8%d8%b1%d8%a7-%d8%b5%d8%a7%d8%a8%d9%88%d9%86%d9%8a%d8%a9", "أوبرا صابونية")
    )

    private fun norm(s: String) = s.trim().lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ى', 'ي').replace('ة', 'ه').replace(Regex("\\s+"), " ")

    fun match(query: String): Genre? {
        var q = norm(query)
        for (p in listOf("تصنيف ", "genre ")) if (q.startsWith(p)) q = q.removePrefix(p).trim()
        return all.firstOrNull { norm(it.label) == q }
    }
}
