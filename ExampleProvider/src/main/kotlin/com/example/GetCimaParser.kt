package com.example

import org.jsoup.nodes.Document

/** Parsing for www.getcima.net (checked 2026-11-27). Pure functions so fixtures can test them. */
object GetCimaParser {
    data class Card(val url: String, val title: String, val poster: String?, val isSeries: Boolean)
    data class Ep(val url: String, val season: Int, val number: Int, val name: String)

    fun cards(doc: Document): List<Card> =
        doc.select("article.c-card a.c-card__link[href]").mapNotNull { a ->
            val url = a.attr("abs:href").ifBlank { return@mapNotNull null }
            if (!url.contains("/movie/") && !url.contains("/series/")) return@mapNotNull null
            val title = a.attr("aria-label").trim().ifBlank { a.selectFirst("img")?.attr("alt")?.trim().orEmpty() }
            if (title.isBlank()) return@mapNotNull null
            val img = a.selectFirst("img")
            val poster = img?.attr("data-src")?.ifBlank { null } ?: img?.attr("src")?.takeIf { it.startsWith("http") }
            Card(url, title, poster, url.contains("/series/"))
        }.distinctBy { it.url }

    fun hasNext(doc: Document): Boolean =
        doc.selectFirst("a[rel=next]") != null ||
            doc.select("a[href]").any { Regex("/search/page/\\d+/").containsMatchIn(it.attr("href")) }

    fun title(doc: Document): String? =
        doc.selectFirst("meta[property=og:title]")?.attr("content")?.trim()?.ifBlank { null }
            ?: doc.selectFirst("h1")?.text()?.trim()?.ifBlank { null }

    fun plot(doc: Document): String? =
        doc.selectFirst("meta[property=og:description]")?.attr("content")?.trim()?.ifBlank { null }

    /** The page's own poster image is the first card-less wp-content image; og:image is a site logo here. */
    fun poster(doc: Document): String? =
        doc.select("img[data-src],img[src]").map { it.attr("data-src").ifBlank { it.attr("src") } }
            .firstOrNull { it.contains("/wp-content/uploads/") && !it.contains("logo") }

    fun seasonUrls(doc: Document): List<String> =
        doc.select("a[href*=?season=]").map { it.attr("abs:href") }.distinct()

    fun episodes(doc: Document): List<Ep> =
        doc.select("article[data-episode-row]").mapNotNull { row ->
            val a = row.selectFirst("a[href]") ?: return@mapNotNull null
            val url = a.attr("abs:href").ifBlank { return@mapNotNull null }
            val season = row.attr("data-season").toIntOrNull() ?: 1
            val label = row.selectFirst("strong")?.text()?.trim().orEmpty()
            val n = Regex("\\d+").find(label)?.value?.toIntOrNull()
                ?: row.selectFirst("span")?.text()?.trim()?.toIntOrNull() ?: 0
            val sub = row.selectFirst("small")?.text()?.trim().orEmpty()
            Ep(url, season, n, label.ifBlank { sub })
        }.distinctBy { it.url }

    /** Player iframe on an episode page, or the "/play/" link on a movie page. */
    fun playerFrame(doc: Document): String? =
        doc.selectFirst("iframe[src*=cinema-player]")?.attr("abs:src")?.ifBlank { null }

    fun playLink(doc: Document): String? =
        doc.select("a[href*=/play/]").map { it.attr("abs:href") }.firstOrNull { !it.contains("type=download") }

    /** Embed urls from a cinema-player page: the active iframe plus every other ?source=N tab. */
    fun playerSources(doc: Document): List<String> =
        doc.select("a[data-cpc-source-tab][href]").map { it.attr("abs:href") }.distinct()

    fun embed(doc: Document): String? =
        doc.selectFirst("iframe.cpc-player__embed")?.attr("src")?.trim()?.takeIf { it.startsWith("http") }
}
