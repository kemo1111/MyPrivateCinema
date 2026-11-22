package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.parsedSafe
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

// Original code written for this repo against the live shahed4u.here theme
// (cards: a.show-card; details under /film/ and /episode/; player page under
// /watch/ whose servers resolve through a POST to /watch/resolve returning a
// one-time /m/ page that embeds the external host).
// Replaces the earlier arprov adaptation, whose selectors matched a different
// Shahid4u theme and could not load anything on this domain.
// Selectors and the full resolve chain were verified in a desktop browser on
// 2026-11-22. Playback inside CloudStream on device is NOT yet confirmed;
// the site sits behind Cloudflare and rotates domains, so report breakage.
class Shahid4uProvider : MainAPI() {
    override var mainUrl = "https://shahed4u.here"
    override var name = "Shahid4u"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "" to "أحدث الإضافات",
        "/category/افلام-اجنبي" to "أفلام أجنبية",
        "/category/مسلسلات-اجنبي" to "مسلسلات أجنبية",
        "/category/افلام-عربي" to "أفلام عربية",
        "/category/مسلسلات-تركية" to "مسلسلات تركية",
        "/category/مسلسلات-اسيوية" to "مسلسلات آسيوية",
        "/category/افلام-انمي" to "أفلام أنمي",
        "/category/مسلسلات-انمي" to "مسلسلات أنمي",
    )

    private fun Element.toSearchResponse(): SearchResponse? {
        val href: String = this.attr("href")
        if (href.isBlank()) return null
        val img: Element? = this.selectFirst("img")
        val title: String = img?.attr("alt")?.trim().orEmpty()
        if (title.isBlank()) return null
        val poster: String? = img?.attr("src")?.ifBlank { null }
        return if (href.contains("/film/")) {
            newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = poster }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = poster }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url: String = if (request.data.isEmpty()) {
            "$mainUrl/?page=$page"
        } else {
            "$mainUrl${request.data}?page=$page"
        }
        val doc = app.get(url).document
        val items: List<SearchResponse> = doc.select("a.show-card").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(mainUrl, params = mapOf("s" to query)).document
        return doc.select("a.show-card").mapNotNull { it.toSearchResponse() }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val title: String = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().substringBefore(" - ").trim()
        val poster: String? = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("img[src*=uploads]")?.attr("src")
        val watchHref: String? = doc.select("a")
            .firstOrNull { it.text().trim() == "مشاهدة الآن" }
            ?.attr("href")

        if (url.contains("/film/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, watchHref ?: url) {
                this.posterUrl = poster
            }
        }

        // Episode pages list every episode of the series inside div.items.
        val episodes: List<Episode> = doc.select("div.items a[href*=/episode/]")
            .mapNotNull { a: Element ->
                val epUrl: String = a.attr("href")
                if (epUrl.isBlank()) return@mapNotNull null
                val epName: String = a.text().trim()
                val epNum: Int? = Regex("(\\d+)").find(epName)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(epUrl) {
                    this.name = epName.ifBlank { epUrl.substringAfterLast('/') }
                    this.episode = epNum
                }
            }
            .distinctBy { it.data }
            .ifEmpty {
                listOf(newEpisode(watchHref ?: url) { this.name = title })
            }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
            this.posterUrl = poster
        }
    }

    data class ResolveResponse(val ok: Boolean? = null, val url: String? = null)

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        // data may be a /film/ or /episode/ page or a direct /watch/ page.
        val pageCookies: Map<String, String>
        val watchUrl: String
        if (data.contains("/watch/")) {
            watchUrl = data
            pageCookies = emptyMap()
        } else {
            val pageResp = app.get(data)
            pageCookies = pageResp.cookies
            val href: String? = pageResp.document.select("a")
                .firstOrNull { it.text().trim() == "مشاهدة الآن" }
                ?.attr("href")
            if (href.isNullOrBlank()) return false
            watchUrl = fixUrl(href)
        }

        val watchResp = app.get(watchUrl, referer = data, cookies = pageCookies)
        val cookies: Map<String, String> = pageCookies + watchResp.cookies
        val watchHtml: String = watchResp.text
        val doc = watchResp.document

        val csrf: String = Regex("csrfToken\\s*=\\s*\"([^\"]+)\"").find(watchHtml)?.groupValues?.get(1)
            ?: return false
        val key: String = Regex("watchResolveKey\\s*=\\s*\"([^\"]+)\"").find(watchHtml)?.groupValues?.get(1)
            ?: return false

        val servers: List<Pair<String, String>> = doc.select("button.btn-server").mapNotNull { b: Element ->
            val token: String = b.attr("data-token")
            if (token.isBlank()) return@mapNotNull null
            val serverName: String = b.selectFirst("img")?.attr("title")?.trim().orEmpty()
            serverName to token
        }
        if (servers.isEmpty()) return false

        var found = false
        servers.forEach { (serverName, token) ->
            runCatching {
                val resp = app.post(
                    "$mainUrl/watch/resolve",
                    headers = mapOf(
                        "X-CSRF-TOKEN" to csrf,
                        "X-Requested-With" to "XMLHttpRequest",
                        "Accept" to "application/json",
                    ),
                    referer = watchUrl,
                    cookies = cookies,
                    json = mapOf("key" to key, "token" to token, "human_signal" to null),
                )
                val resolved: ResolveResponse? = resp.parsedSafe<ResolveResponse>()
                    ?: runCatching { parseJson<ResolveResponse>(resp.text) }.getOrNull()
                val mUrl: String = resolved?.url?.takeIf { it.isNotBlank() } ?: return@runCatching

                val mHtml: String = app.get(fixUrl(mUrl), referer = watchUrl, cookies = cookies).text
                val embed: String = Regex("iframe src=\"(https?://[^\"]+)\"").find(mHtml)?.groupValues?.get(1)
                    ?: return@runCatching

                found = true
                loadExtractor(embed, watchUrl, subtitleCallback, callback)
            }
        }
        return found
    }
}
