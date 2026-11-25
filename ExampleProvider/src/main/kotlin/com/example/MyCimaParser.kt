package com.example

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Pure HTML parsing for wecimas.live, kept free of network calls so it can be unit tested. */
object MyCimaParser {
    const val CARD = "div.Grid--WecimaPosts div.GridItem"

    enum class Kind { MOVIE, SERIES, ANIME }

    data class Card(val title: String, val url: String, val poster: String?, val year: Int?, val type: Kind)
    data class Ep(val url: String, val name: String, val number: Int)
    data class Detail(
        val title: String, val year: Int?, val poster: String?, val plot: String?,
        val episodes: List<Ep>, val isAnime: Boolean
    )

    private val noise = Regex("مشاهدة|مترجمة|مترجم|اون لاين")

    fun firstInt(s: String): Int? = Regex("""\d+""").find(s)?.value?.toIntOrNull()

    private fun clean(full: String, year: Int?): String =
        full.replace(if (year != null) "($year)" else "", "").replace(noise, "").trim()

    fun card(el: Element): Card? {
        val a = el.selectFirst("div.Thumb--GridItem a") ?: return null
        val href = a.attr("href")
        if (href.isBlank()) return null
        val full = a.attr("title").ifBlank { a.selectFirst("strong")?.text().orEmpty() }
        if (full.isBlank()) return null
        val year = firstInt(full)
        val poster = el.selectFirst("span.BG--GridItem")?.attr("data-src")?.ifBlank { null }
            ?: el.selectFirst("meta[itemprop=thumbnailUrl]")?.attr("content")?.ifBlank { null }
        val kind = el.selectFirst("span.mc-card-type")?.text()
            ?: el.selectFirst("strong small")?.text().orEmpty()
        val type = when {
            kind.contains("مسلسل") -> Kind.SERIES
            kind.contains("فيلم") -> Kind.MOVIE
            href.contains("/movies/") -> Kind.MOVIE
            href.contains("/tvshows/") || href.contains("/episodes/") -> Kind.SERIES
            full.contains("انمي") || full.contains("أنمي") -> Kind.ANIME
            full.contains("فيلم") -> Kind.MOVIE
            else -> Kind.SERIES
        }
        return Card(clean(full, year), href, poster, year, type)
    }

    fun detail(doc: Document): Detail {
        val raw = doc.selectFirst("div.Title--Content--Single-begin h1")?.text()
            ?: doc.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "بدون عنوان"
        val year = firstInt(raw)
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")?.ifBlank { null }
            ?: doc.selectFirst("meta[itemprop=thumbnailUrl]")?.attr("content")?.ifBlank { null }
        val plot = doc.selectFirst("div.StoryMovieContent")?.text()
            ?: doc.selectFirst("div.PostItemContent")?.text()
            ?: doc.selectFirst("meta[property=og:description]")?.attr("content")
        val links = doc.select("div.Seasons--Episodes div.EpisodesList a").distinctBy { it.attr("href") }
        val eps = links.mapIndexed { i, el ->
            val name = el.selectFirst("episodetitle")?.text()?.trim()?.ifBlank { null } ?: el.attr("title").trim()
            Ep(el.attr("href"), name, firstInt(name) ?: (i + 1))
        }.sortedBy { it.number }
        return Detail(clean(raw, year), year, poster, plot, eps, raw.contains("انمي") || raw.contains("أنمي"))
    }

    /** This site's own server embeds, in page order, http(s) only, de-duplicated. */
    fun servers(doc: Document): List<String> =
        doc.select("button.mc-server-btn[data-url]").map { it.attr("data-url").trim() }
            .filter { it.startsWith("http") }.distinct()

    fun hasNext(doc: Document): Boolean = doc.selectFirst("link[rel=next]") != null
}
