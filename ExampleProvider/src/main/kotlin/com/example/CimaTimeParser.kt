package com.example

import org.jsoup.nodes.Document

/** Pure HTML reading for cima-time.com (checked 2026-11-25). No network. */
object CimaTimeParser {
    data class Card(val url: String, val title: String, val poster: String?)
    data class Ep(val url: String, val title: String, val episode: Int?)

    private val watchHref = Regex("watch\\.php\\?vid=\\w+")

    fun cards(doc: Document): List<Card> =
        doc.select("ul#pm-grid li div.caption h3 a[href*=watch.php]").mapNotNull { a ->
            val href = a.attr("abs:href").ifBlank { a.attr("href") }
            val title = a.attr("title").ifBlank { a.text() }.trim()
            if (href.isBlank() || title.isBlank() || !watchHref.containsMatchIn(href)) return@mapNotNull null
            val img = a.parent()?.parent()?.parent()?.selectFirst("img")
            val poster = img?.attr("abs:src")?.takeIf { it.isNotBlank() }
            Card(href, title, poster)
        }.distinctBy { it.url }

    fun isEpisodeTitle(title: String) = title.contains("الحلقة")

    fun episodeNumber(text: String): Int? =
        Regex("الحلقة\\s*(\\d+)").find(text)?.groupValues?.get(1)?.toIntOrNull()

    /** Episode links of a season page, ascending, unknown numbers last. Season number is not guessed. */
    fun episodes(doc: Document): List<Ep> =
        doc.select("div.SeasonsEpisodesMain a[href*=watch.php]").mapNotNull { a ->
            val href = a.attr("abs:href").ifBlank { a.attr("href") }
            if (href.isBlank()) return@mapNotNull null
            val label = a.text().trim()
            Ep(href, a.attr("title").ifBlank { label }, episodeNumber(label) ?: episodeNumber(a.attr("title")))
        }.distinctBy { it.url }
            .sortedWith(compareBy({ it.episode == null }, { it.episode ?: 0 }))

    /** iframe src of every server entry on a play page, in page order. */
    fun embeds(doc: Document): List<Pair<String, String>> =
        doc.select("li[id^=server_][data-embed]").mapNotNull { li ->
            val frame = org.jsoup.Jsoup.parse(li.attr("data-embed")).selectFirst("iframe") ?: return@mapNotNull null
            val src = frame.attr("src").trim()
            if (!src.startsWith("http")) null else li.id().removePrefix("server_") to src
        }.distinctBy { it.second }

    fun year(title: String): Int? =
        Regex("\\b(19|20)\\d{2}\\b").find(title)?.value?.toIntOrNull()

    fun cleanTitle(title: String): String =
        title.replace(Regex("^(فيلم|مسلسل)\\s+"), "")
            .replace(Regex("\\s*(مترجم(ة)?|اون لاين|كامل)\\s*"), " ")
            .replace(Regex("\\s+"), " ").trim()
}
