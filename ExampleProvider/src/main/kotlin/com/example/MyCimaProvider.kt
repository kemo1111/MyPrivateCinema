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

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "أفلام",
        "$mainUrl/tvshows/" to "مسلسلات",
        "$mainUrl/genres/%d8%af%d8%b1%d8%a7%d9%85%d8%a7/" to "دراما",
        "$mainUrl/genres/%d8%a5%d8%ab%d8%a7%d8%b1%d8%a9/" to "إثارة",
        "$mainUrl/genres/%d9%83%d9%88%d9%85%d9%8a%d8%af%d9%8a%d8%a7/" to "كوميديا",
        "$mainUrl/genres/%d8%ac%d8%b1%d9%8a%d9%85%d8%a9/" to "جريمة",
        "$mainUrl/genres/%d8%ad%d8%b1%d9%83%d8%a9/" to "حركة",
        "$mainUrl/genres/%d8%ba%d9%85%d9%88%d8%b6/" to "غموض",
        "$mainUrl/genres/%d8%b1%d8%b3%d9%88%d9%85-%d9%85%d8%aa%d8%ad%d8%b1%d9%83%d8%a9/" to "رسوم متحركة",
        "$mainUrl/genres/%d8%b1%d9%88%d9%85%d9%86%d8%b3%d9%8a%d8%a9/" to "رومنسية",
        "$mainUrl/genres/%d8%b1%d8%b9%d8%a8/" to "رعب",
        "$mainUrl/genres/%d8%ae%d9%8a%d8%a7%d9%84-%d8%b9%d9%84%d9%85%d9%8a-%d9%88%d9%81%d8%a7%d9%86%d8%aa%d8%a7%d8%b2%d9%8a%d8%a7/" to "خيال علمي وفانتازيا",
        "$mainUrl/genres/%d8%b9%d8%a7%d8%a6%d9%84%d9%8a/" to "عائلي",
        "$mainUrl/genres/%d9%85%d8%ba%d8%a7%d9%85%d8%b1%d8%a9/" to "مغامرة",
        "$mainUrl/genres/%d9%81%d8%a7%d9%86%d8%aa%d8%a7%d8%b2%d9%8a%d8%a7/" to "فانتازيا",
        "$mainUrl/genres/%d8%ae%d9%8a%d8%a7%d9%84-%d8%b9%d9%84%d9%85%d9%8a/" to "خيال علمي",
        "$mainUrl/genres/%d9%88%d8%ab%d8%a7%d8%a6%d9%82%d9%8a/" to "وثائقي",
        "$mainUrl/genres/%d8%ad%d8%b1%d9%83%d8%a9-%d9%88%d9%85%d8%ba%d8%a7%d9%85%d8%b1%d8%a9/" to "حركة ومغامرة",
        "$mainUrl/genres/%d8%aa%d8%a7%d8%b1%d9%8a%d8%ae/" to "تاريخ",
        "$mainUrl/genres/%d8%ad%d8%b1%d8%a8/" to "حرب",
        "$mainUrl/genres/%d9%85%d9%88%d8%b3%d9%8a%d9%82%d9%89/" to "موسيقى",
        "$mainUrl/genres/%d8%ba%d8%b1%d8%a8%d9%8a/" to "غربي",
        "$mainUrl/genres/%d9%81%d9%8a%d9%84%d9%85-%d8%aa%d9%84%d9%81%d8%a7%d8%b2%d9%8a/" to "فيلم تلفازي",
        "$mainUrl/genres/%d9%88%d8%a7%d9%82%d8%b9/" to "واقع",
        "$mainUrl/genres/%d8%a3%d9%88%d8%a8%d8%b1%d8%a7-%d8%b5%d8%a7%d8%a8%d9%88%d9%86%d9%8a%d8%a9/" to "أوبرا صابونية"
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

    override suspend fun search(query: String): List<SearchResponse> {
        val q = query.trim().replace(" ", "%20")
        val document = app.get("$mainUrl/search/$q").document
        return document.select("div.GridItem")
            .filterNot { it.text().contains("اعلان") }
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
    }

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
