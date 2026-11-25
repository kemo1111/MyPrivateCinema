package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder

// Original code for cima-time.com (checked 2026-11-25).
// Flow: category.php / search.php cards -> watch.php?vid=ID -> play.php?vid=ID lists one iframe
// per host; each is given to CloudStream's extractors. Many hosts are dead or unsupported; in the
// sample checked, Mp4Upload gave direct mp4 files with Arabic subtitles burned into the picture for
// older films and some episodes, and was missing on newer pages. Some files carry an ad watermark.
// A series page lists its episodes; an episode page is played as a single item.
class CimaTimeProvider : MainAPI() {
    override var mainUrl = "https://cima-time.com"
    override var name = "CimaTime Kemo"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/category.php?cat=english-movies" to "أفلام أجنبية",
        "$mainUrl/category.php?cat=english-series" to "مسلسلات أجنبية",
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
        return CimaTimeParser.cards(app.get("$mainUrl/search.php?keywords=$q", timeout = 20L).document).map { toSearch(it) }
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
