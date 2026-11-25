package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder

// Original code for ai.cimamix.tv (checked 2026-11-25). Same page engine as cima-time.com, different catalog.
// Category pages work with a plain GET. search.php is behind a Cloudflare challenge for plain requests
// (HTTP 403), so search returns nothing unless the app can pass it. Servers mostly need our own HLS
// extractors (see CimamixExtractors.kt). Many files carry another Arabic site's watermark; max about 480p.
class CimamixProvider : MainAPI() {
    override var mainUrl = "https://ai.cimamix.tv"
    override var name = "Cimamix Kemo"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/category.php?cat=film" to "أفلام",
        "$mainUrl/category.php?cat=mosalsalatenglish" to "مسلسلات أجنبية",
        "$mainUrl/newvideos.php" to "الجديد"
    )

    private fun toSearch(c: CimaTimeParser.Card): SearchResponse =
        if (CimaTimeParser.isEpisodeTitle(c.title) || c.title.startsWith("مسلسل"))
            newTvSeriesSearchResponse(c.title, c.url, TvType.TvSeries) { posterUrl = c.poster }
        else newMovieSearchResponse(c.title, c.url, TvType.Movie) { posterUrl = c.poster }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val sep = if (request.data.contains("?")) "&" else "?"
        val url = if (page > 1) "${request.data}${sep}page=$page" else request.data
        val items = app.get(url, timeout = 20L).document.let { CimaTimeParser.cards(it) }.map { toSearch(it) }
        return newHomePageResponse(request.name, items, items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query, "UTF-8")
        val res = runCatching { app.get("$mainUrl/search.php?keywords=$q", timeout = 15L) }.getOrNull() ?: return emptyList()
        if (res.code != 200) return emptyList()
        return CimaTimeParser.cards(res.document).map { toSearch(it) }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, timeout = 20L).document
        val raw = doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.title().takeIf { it.isNotBlank() } ?: return null
        val poster = doc.selectFirst("meta[property=og:image]")?.attr("content")?.takeIf { it.isNotBlank() }
        val plot = doc.selectFirst("meta[property=og:description]")?.attr("content")?.takeIf { it.isNotBlank() }
        val year = CimaTimeParser.year(raw)
        val title = CimaTimeParser.cleanTitle(raw)
        val eps = if (!CimaTimeParser.isEpisodeTitle(raw)) CimaTimeParser.episodes(doc) else emptyList()
        if (eps.isNotEmpty()) {
            val list = eps.map { e -> newEpisode(e.url) { this.name = e.title; this.episode = e.episode } }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, list) {
                this.posterUrl = poster; this.plot = plot; this.year = year
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster; this.plot = plot; this.year = year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean = CrossServers.withSiblings(this, data, subtitleCallback, callback) { sub, cb ->
        loadLinksOwn(data, sub, cb)
    }

    private suspend fun loadLinksOwn(
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val vid = Regex("vid=(\\w+)").find(data)?.groupValues?.get(1) ?: return false
        val play = app.get("$mainUrl/play.php?vid=$vid", referer = data, timeout = 20L).document
        val embeds = CimaTimeParser.embeds(play)
        val found = java.util.concurrent.atomic.AtomicBoolean(false)
        val lock = Any()
        coroutineScope {
            embeds.map { (_, src) ->
                async {
                    withTimeoutOrNull(15_000L) {
                        runCatching {
                            loadExtractor(src, "$mainUrl/", { synchronized(lock) { subtitleCallback(it) } }) { link ->
                                found.set(true); synchronized(lock) { callback(link) }
                            }
                        }
                    }
                }
            }.awaitAll()
        }
        return found.get()
    }
}
