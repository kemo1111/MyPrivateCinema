package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URLEncoder

// Original code for www.getcima.net (checked 2026-11-27). Plain GET works for lists, titles and players.
// Movie page -> "/play/<slug>/" -> cinema-player iframe with ?source=N tabs, each one embed (mixdrop.top,
// minochinos.com, hgcloud, playmogo, forafile, smoothpre). Several of those hosts sit behind Cloudflare
// for plain requests, so only Mixdrop and Minochinos have our own extractors. Files seen carry
// burned-in Arabic subtitles (one title checked), with another site's watermark.
class GetCimaProvider : MainAPI() {
    override var mainUrl = "https://www.getcima.net"
    override var name = "GetCima Kemo"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/search/?content_type=movie" to "أفلام",
        "$mainUrl/search/?content_type=series" to "مسلسلات"
    )

    private fun pageUrl(base: String, page: Int): String {
        if (page <= 1) return base
        val i = base.indexOf("/search/")
        return base.substring(0, i) + "/search/page/$page/" + base.substring(i + "/search/".length)
    }

    private fun GetCimaParser.Card.toResponse(): SearchResponse =
        if (isSeries) newTvSeriesSearchResponse(title, url, TvType.TvSeries) { posterUrl = poster }
        else newMovieSearchResponse(title, url, TvType.Movie) { posterUrl = poster }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(pageUrl(request.data, page), timeout = 20L).document
        val items = GetCimaParser.cards(doc).map { it.toResponse() }
        return newHomePageResponse(request.name, items, items.isNotEmpty() && GetCimaParser.hasNext(doc))
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val doc = app.get("$mainUrl/search/?s=$q", timeout = 20L).document
        return GetCimaParser.cards(doc).map { it.toResponse() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, timeout = 20L).document
        val title = GetCimaParser.title(doc) ?: return null
        val poster = GetCimaParser.poster(doc)
        val plot = GetCimaParser.plot(doc)
        if (url.contains("/series/")) {
            val all = linkedMapOf<String, GetCimaParser.Ep>()
            GetCimaParser.episodes(doc).forEach { all[it.url] = it }
            GetCimaParser.seasonUrls(doc).forEach { su ->
                runCatching { GetCimaParser.episodes(app.get(su, timeout = 20L).document) }
                    .getOrNull()?.forEach { all.putIfAbsent(it.url, it) }
            }
            val eps = all.values.sortedWith(compareBy({ it.season }, { it.number })).map { e ->
                newEpisode(e.url) { this.name = e.name; this.season = e.season; this.episode = e.number }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = poster; this.plot = plot
            }
        }
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster; this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val page = app.get(data, timeout = 20L).document
        val frame = GetCimaParser.playerFrame(page)
            ?: GetCimaParser.playLink(page)?.let { play ->
                GetCimaParser.playerFrame(app.get(play, referer = data, timeout = 20L).document)
            } ?: return false
        val first = app.get(frame, referer = data, timeout = 20L).document
        val sources = GetCimaParser.playerSources(first).ifEmpty { listOf(frame) }
        val found = java.util.concurrent.atomic.AtomicBoolean(false)
        val lock = Any()
        coroutineScope {
            sources.map { src ->
                async {
                    withTimeoutOrNull(20_000L) {
                        runCatching {
                            val doc = if (src == frame) first else app.get(src, referer = frame, timeout = 15L).document
                            val embed = GetCimaParser.embed(doc) ?: return@runCatching
                            loadExtractor(embed, "$mainUrl/", { synchronized(lock) { subtitleCallback(it) } }) { link ->
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
