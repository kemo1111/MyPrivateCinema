package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

// Original code written for this repo against the public JSON API used by the
// site's own web app (api/v1/titles, search, watch). Not copied from another project.
class EgyDeadProvider : MainAPI() {
    override var mainUrl = "https://egydead.ca"
    override var name = "EgyDead Kemo"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    private val cf = CloudflareKiller()

    data class TitleItem(
        val id: Int? = null,
        val name: String? = null,
        val poster: String? = null,
        val description: String? = null,
        val year: Int? = null,
        val is_series: Boolean? = null,
        val seasons_count: Int? = null,
        val rating: Double? = null
    )

    data class Pager<T>(val data: List<T>? = null, val next_page: Int? = null)
    data class ListResp(val pagination: Pager<TitleItem>? = null)
    data class SearchResp(val results: List<TitleItem>? = null)
    data class Season(val number: Int? = null)
    data class VideoItem(val id: Int? = null, val name: String? = null, val src: String? = null, val type: String? = null)
    data class TitleFull(val id: Int? = null, val name: String? = null, val poster: String? = null,
                         val backdrop: String? = null, val description: String? = null, val year: Int? = null,
                         val is_series: Boolean? = null, val seasons_count: Int? = null,
                         val rating: Double? = null, val videos: List<VideoItem>? = null,
                         val primary_video: VideoItem? = null)
    data class TitleResp(val title: TitleFull? = null)
    data class Episode0(val id: Int? = null, val name: String? = null, val poster: String? = null,
                        val season_number: Int? = null, val episode_number: Int? = null,
                        val primary_video: VideoItem? = null)
    data class EpResp(val pagination: Pager<Episode0>? = null)
    data class WatchResp(val video: VideoItem? = null, val alternative_videos: List<VideoItem>? = null)

    private val jsonHeaders: Map<String, String> = mapOf(
        "Accept" to "application/json",
        "Referer" to "$mainUrl/"
    )

    private suspend fun api(path: String): String {
        return app.get("$mainUrl/api/v1/$path", headers = jsonHeaders, interceptor = cf).text
    }

    private fun clean(n: String?): String {
        var t: String = n ?: ""
        t = t.replace("HD - ", "").replace("مترجم", "").replace("فيلم", "").replace("مسلسل", "")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun TitleItem.toSearch(): SearchResponse? {
        val i: Int = id ?: return null
        val title = clean(name)
        if (title.isBlank()) return null
        val u = "title:$i:${if (is_series == true) 1 else 0}"
        return if (is_series == true) {
            newTvSeriesSearchResponse(title, u, TvType.TvSeries) { this.posterUrl = poster }
        } else {
            newMovieSearchResponse(title, u, TvType.Movie) { this.posterUrl = poster }
        }
    }

    override val mainPage = mainPageOf(
        "type=movie" to "أفلام",
        "type=series" to "مسلسلات",
        "" to "أحدث الإضافات"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val q = if (request.data.isBlank()) "" else "&${request.data}"
        val txt = api("titles?perPage=30&page=$page&order=created_at:desc$q")
        val items = parseJson<ListResp>(txt).pagination?.data.orEmpty().mapNotNull { it.toSearch() }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val enc = java.net.URLEncoder.encode(query, "UTF-8")
        val txt = api("search/$enc?limit=30")
        return parseJson<SearchResp>(txt).results.orEmpty().mapNotNull { it.toSearch() }
    }

    override suspend fun load(url: String): LoadResponse {
        val parts = url.split(":")
        val id = parts[1]
        val t: TitleFull = parseJson<TitleResp>(api("titles/$id")).title ?: throw ErrorLoadingException("not found")
        val title = clean(t.name)
        if (t.is_series == true) {
            val eps = ArrayList<Episode>()
            val seasons: Int = t.seasons_count ?: 1
            for (s in 1..seasons) {
                var page = 1
                while (page < 30) {
                    val r = parseJson<EpResp>(api("titles/$id/seasons/$s/episodes?perPage=50&page=$page"))
                    val list = r.pagination?.data.orEmpty()
                    for (e in list) {
                        val vid: Int = e.primary_video?.id ?: continue
                        eps.add(newEpisode("video:$vid") {
                            this.name = e.name
                            this.season = e.season_number ?: s
                            this.episode = e.episode_number
                            this.posterUrl = e.poster
                        })
                    }
                    if (r.pagination?.next_page == null || list.isEmpty()) break
                    page++
                }
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, eps) {
                this.posterUrl = t.poster
                this.backgroundPosterUrl = t.backdrop
                this.plot = t.description
                this.year = t.year
            }
        }
        val vid: Int = t.primary_video?.id ?: t.videos?.firstOrNull()?.id ?: 0
        return newMovieLoadResponse(title, url, TvType.Movie, "video:$vid") {
            this.posterUrl = t.poster
            this.backgroundPosterUrl = t.backdrop
            this.plot = t.description
            this.year = t.year
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val vid = data.substringAfter("video:")
        val w = parseJson<WatchResp>(api("watch/$vid"))
        val all = ArrayList<VideoItem>()
        w.video?.let { all.add(it) }
        all.addAll(w.alternative_videos.orEmpty())
        var found = false
        for (v in all.distinctBy { it.src }) {
            val src: String = v.src ?: continue
            if (src.startsWith("http")) {
                loadExtractor(src, "$mainUrl/", subtitleCallback, callback)
                found = true
            }
        }
        return found
    }
}
