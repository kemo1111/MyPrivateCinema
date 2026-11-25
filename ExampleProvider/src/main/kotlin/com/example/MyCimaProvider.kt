package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class MyCimaProvider : MainAPI() {
    override var mainUrl = "https://wecimas.live"
    override var name = "MyCima Kemo"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)

    // Startup is only these rows: the home waits for every row, so each row adds fresh-open time.
    // Every genre stays reachable by typing its exact name in search (see MyCimaGenres).
    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "أفلام",
        "$mainUrl/tvshows/" to "مسلسلات"
    )

    private fun Element.toSearchResult(): SearchResponse? {
        val c = MyCimaParser.card(this) ?: return null
        return when (c.type) {
            MyCimaParser.Kind.MOVIE -> newMovieSearchResponse(c.title, c.url, TvType.Movie) {
                this.posterUrl = c.poster
                this.year = c.year
            }
            MyCimaParser.Kind.ANIME -> newAnimeSearchResponse(c.title, c.url, TvType.Anime) {
                this.posterUrl = c.poster
            }
            else -> newTvSeriesSearchResponse(c.title, c.url, TvType.TvSeries) {
                this.posterUrl = c.poster
            }
        }
    }

    // wecimas.live builds each listing page in 1.5-3s and gets much slower when many are requested
    // at once, while the home screen asks for every row together. ListingLoader limits that to 3 at a
    // time, retries once, never throws, bounds the whole call and caches only larger good pages.
    private companion object {
        val listings = ListingLoader<SearchResponse>()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page > 1) "${request.data.trimEnd('/')}/page/$page/" else request.data
        val home = listings.load(pageUrl) {
            app.get(pageUrl, timeout = 20L).document
                .select(MyCimaParser.CARD)
                .mapNotNull { it.toSearchResult() }
                .distinctBy { it.url }
        }
        return newHomePageResponse(request.name, home, home.isNotEmpty())
    }

    private suspend fun fetchCards(url: String): Pair<List<SearchResponse>, Boolean> {
        val doc = app.get(url, timeout = 20L).document
        val items = doc.select(MyCimaParser.CARD).mapNotNull { it.toSearchResult() }.distinctBy { it.url }
        return items to MyCimaParser.hasNext(doc)
    }

    // Exact genre name (or "تصنيف <name>") lists that genre page by page; anything else is normal site search.
    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val genre = MyCimaGenres.match(query)
        val url = if (genre != null) {
            if (page > 1) "$mainUrl/genres/${genre.slug}/page/$page/" else "$mainUrl/genres/${genre.slug}/"
        } else {
            val q = query.trim().replace(" ", "%20")
            if (page > 1) "$mainUrl/search/$q/page/$page/" else "$mainUrl/search/$q"
        }
        val (items, hasNext) = fetchCards(url)
        return newSearchResponseList(items, hasNext)
    }

    override suspend fun search(query: String): List<SearchResponse> =
        search(query, 1)?.items.orEmpty()

    override suspend fun load(url: String): LoadResponse {
        val d = MyCimaParser.detail(app.get(url).document)
        return if (d.episodes.isNotEmpty()) {
            val episodes = d.episodes.map { e ->
                newEpisode(e.url) {
                    this.name = e.name
                    this.episode = e.number
                }
            }
            newTvSeriesLoadResponse(d.title, url, if (d.isAnime) TvType.Anime else TvType.TvSeries, episodes) {
                this.posterUrl = d.poster
                this.plot = d.plot
                this.year = d.year
            }
        } else {
            newMovieLoadResponse(d.title, url, TvType.Movie, url) {
                this.posterUrl = d.poster
                this.plot = d.plot
                this.year = d.year
            }
        }
    }

    // Independent stage: only this site's own servers (button.mc-server-btn). No cross-site siblings.
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val servers = MyCimaParser.servers(app.get(data).document)
        var found = false
        servers.amap { embedUrl ->
            try {
                if (loadExtractor(embedUrl, data, subtitleCallback, callback)) found = true
            } catch (_: Exception) {
            }
        }
        return found
    }
}
