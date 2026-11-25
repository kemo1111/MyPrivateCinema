package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.net.URLEncoder

// Original code written for this repo against aflaam.com (checked 2026-11-25).
// Flow: list/search cards (div.item a.box) -> /movie/{id}/{slug} or /series/{id}/{slug}
// -> title page lists /watch/... links (div.qualities a.link-show) -> each watch page
// has <video id=player><source src size> pointing at a direct mp4. Plain HTTP, no tokens
// to decrypt. Direct links carry a timestamped path, so they are resolved at play time.
class AflaamProvider : MainAPI() {
    override var mainUrl = "https://aflaam.com"
    override var name = "Aflaam Kemo"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/search?section=2" to "أفلام أجنبية",
        "$mainUrl/search?section=1" to "أفلام عربية",
        "$mainUrl/search?section=3" to "أفلام هندية",
        "$mainUrl/series" to "مسلسلات"
    )

    private fun card(el: Element): SearchResponse? {
        val a = el.selectFirst("a.box") ?: return null
        val href = fixUrl(a.attr("href"))
        val title = el.selectFirst("h3.entry-title")?.text()?.trim()?.ifBlank { null } ?: return null
        val poster = el.selectFirst("picture > img, img")?.attr("src")?.takeIf { it.isNotBlank() }?.let { fixUrl(it) }
        return when {
            href.contains("/movie/") -> newMovieSearchResponse(title, href, TvType.Movie) { posterUrl = poster }
            href.contains("/series/") -> newTvSeriesSearchResponse(title, href, TvType.TvSeries) { posterUrl = poster }
            else -> null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sep = if (request.data.contains("?")) "&" else "?"
        val url = if (page > 1) "${request.data}${sep}page=$page" else request.data
        val items = app.get(url).document.select("div.item").mapNotNull { card(it) }
        return newHomePageResponse(request.name, items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        return app.get("$mainUrl/search?q=$q").document.select("div.item").mapNotNull { card(it) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text()?.trim()?.ifBlank { null } ?: return null
        val poster = doc.selectFirst("a.movie-poster > img")?.attr("src")?.takeIf { it.isNotBlank() }?.let { fixUrl(it) }
        val plot = doc.selectFirst("div#movie-tab-2 div.widget-body > p")?.text()?.trim()
        val year = doc.select("div.movie-container > div.d-flex")
            .firstOrNull { it.text().contains("سنة الإنتاج") }?.text()
            ?.substringAfter(":")?.trim()?.toIntOrNull()
        val tags = doc.select("a.movie-category").map { it.text() }

        if (url.contains("/series/")) {
            val eps = doc.select("div#movie-tab-1 div.entry-box-3").mapNotNull {
                val href = it.selectFirst("a")?.attr("href")?.takeIf { h -> h.isNotBlank() }?.let { h -> fixUrl(h) }
                    ?: return@mapNotNull null
                val n = it.selectFirst("span.font-size-50")?.text()?.trim()?.toIntOrNull()
                newEpisode(href) {
                    this.name = it.selectFirst("h3.entry-title")?.text()?.replaceFirst(Regex("^\\d+"), "")?.trim()
                    this.episode = n
                }
            }.distinctBy { it.data }.sortedWith(compareBy({ it.episode == null }, { it.episode ?: 0 }))
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = poster; this.year = year; this.plot = plot; this.tags = tags
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster; this.year = year; this.plot = plot; this.tags = tags
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = CrossServers.withSiblings(this, data, subtitleCallback, callback) { sub, cb ->
        loadLinksOwn(data, isCasting, sub, cb)
    }

    private suspend fun loadLinksOwn(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val page = app.get(data, timeout = 20L).document
        val watch = if (data.contains("/watch/")) listOf(data)
        else page.select("a.link-show").map { fixUrl(it.attr("href")) }.distinct()
        var found = false
        val seen = HashSet<String>()
        for (w in watch) {
            val d = runCatching { app.get(w, referer = encodeForHeader(data), timeout = 20L).document }.getOrNull() ?: continue
            for (s in d.select("video#player source, video source")) {
                val src = s.attr("src").trim()
                if (src.isBlank() || !seen.add(src)) continue
                val q = s.attr("size").toIntOrNull()
                callback(
                    newExtractorLink(name, if (q != null) "$name ${q}p" else name, src) {
                        this.referer = "$mainUrl/"
                        this.quality = q ?: Qualities.Unknown.value
                    }
                )
                found = true
            }
        }
        return found
    }
}
