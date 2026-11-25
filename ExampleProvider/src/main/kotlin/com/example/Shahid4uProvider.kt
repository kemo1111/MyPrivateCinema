package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
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
//
// History: v22 added a labelled load-time probe that exposed the real defect
// (OkHttp rejects Arabic characters in header values; every loadLinks call
// crashed on the Arabic Referer and the app swallowed it). v23 fixed the
// encoding and playback was confirmed on device, so v24 removes the probe;
// session handling, timeouts, Origin header and encoding remain unchanged.

// OkHttp rejects non-ASCII characters in header VALUES (e.g. Referer), while
// it percent-encodes request URLs itself. This site's slugs are Arabic, so any
// header derived from a URL must be encoded or the request crashes inside
// header validation - which CloudStream then swallows into "No Links Found".
internal fun encodeForHeader(url: String): String {
    val q = url.indexOf('?')
    val base = if (q >= 0) url.substring(0, q) else url
    val query = if (q >= 0) url.substring(q) else ""
    val encodedBase = base.split("/").joinToString("/") { seg ->
        if (seg.any { it.code > 127 }) {
            java.net.URLEncoder.encode(seg, "UTF-8").replace("+", "%20")
        } else seg
    }
    return encodedBase + query
}

class Shahid4uProvider : MainAPI() {
    override var mainUrl = "https://shahed4u.here"
    override var name = "Shahid4u"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    // NiceHttp's per-request default is unlimited; CloudStream wraps the whole
    // loadLinks call in a 120s timeout, so one hung request would silently eat
    // the entire window. Bound every request instead.
    private val reqTimeout = 20L

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
        val doc = app.get(url, timeout = reqTimeout).document
        val items: List<SearchResponse> = doc.select("a.show-card").mapNotNull { it.toSearchResponse() }
        return newHomePageResponse(request.name, items, hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(mainUrl, params = mapOf("s" to query), timeout = reqTimeout).document
        return doc.select("a.show-card").mapNotNull { it.toSearchResponse() }
    }

    // The site's /watch/resolve is bound to the Laravel session cookie and
    // CloudStream keeps no cookie jar, so every step must carry the cookies
    // collected from the previous one, like a browser session would.
    private suspend fun getWithSession(
        url: String,
        cookies: MutableMap<String, String>,
        referer: String? = null
    ) = app.get(url, referer = referer?.let(::encodeForHeader), cookies = cookies, timeout = reqTimeout).also {
        cookies.putAll(it.cookies)
    }

    private fun findWatchHref(doc: org.jsoup.nodes.Document): String? =
        doc.select("a").firstOrNull { it.text().trim() == "مشاهدة الآن" }?.attr("href")

    data class ServerEntry(val name: String, val token: String)

    private fun parseServers(doc: org.jsoup.nodes.Document): List<ServerEntry> =
        doc.select("button.btn-server").mapNotNull { b: Element ->
            val token: String = b.attr("data-token")
            if (token.isBlank()) return@mapNotNull null
            val serverName: String = b.selectFirst("img")?.attr("title")?.trim().orEmpty()
            ServerEntry(serverName, token)
        }

    private suspend fun postResolve(
        csrf: String,
        key: String,
        token: String,
        watchUrl: String,
        cookies: Map<String, String>
    ) = app.post(
        "$mainUrl/watch/resolve",
        headers = mapOf(
            "X-CSRF-TOKEN" to csrf,
            "X-Requested-With" to "XMLHttpRequest",
            "Accept" to "application/json",
            // Browsers always send Origin on this POST; match them.
            "Origin" to mainUrl,
        ),
        referer = encodeForHeader(watchUrl),
        cookies = cookies,
        json = mapOf("key" to key, "token" to token, "human_signal" to null),
        timeout = reqTimeout,
    )

    data class ResolveResponse(val ok: Boolean? = null, val url: String? = null)

    override suspend fun load(url: String): LoadResponse {
        val cookies = mutableMapOf<String, String>()
        getWithSession(mainUrl, cookies) // establish the session first
        val pageResp = getWithSession(url, cookies, referer = mainUrl)
        val doc = pageResp.document
        val title: String = doc.selectFirst("h1")?.text()?.trim()
            ?: doc.title().substringBefore(" - ").trim()
        val poster: String? = doc.selectFirst("meta[property=og:image]")?.attr("content")
            ?: doc.selectFirst("img[src*=uploads]")?.attr("src")
        val watchHref: String? = findWatchHref(doc)

        if (url.contains("/film/")) {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
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
        // data may be a /film/ or /episode/ page or a direct /watch/ page.
        val sessionCookies = mutableMapOf<String, String>()
        getWithSession(mainUrl, sessionCookies)

        val watchUrl: String
        if (data.contains("/watch/")) {
            watchUrl = data
        } else {
            val pageResp = getWithSession(data, sessionCookies, referer = mainUrl)
            val href: String? = findWatchHref(pageResp.document)
            if (href.isNullOrBlank()) {
                throw ErrorLoadingException("Shahid4u: watch button not found on content page")
            }
            watchUrl = fixUrl(href)
        }

        val watchResp = getWithSession(watchUrl, sessionCookies, referer = data)
        val watchHtml: String = watchResp.text
        val doc = watchResp.document

        val csrf: String = Regex("csrfToken\\s*=\\s*\"([^\"]+)\"").find(watchHtml)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Shahid4u: watch page missing player tokens (protection or layout change)")
        val key: String = Regex("watchResolveKey\\s*=\\s*\"([^\"]+)\"").find(watchHtml)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("Shahid4u: watch page missing resolve key")

        val servers: List<ServerEntry> = parseServers(doc)
        if (servers.isEmpty()) throw ErrorLoadingException("Shahid4u: no servers listed on watch page")

        var found = false
        var rejected = 0
        servers.forEach { server ->
            runCatching {
                val resp = postResolve(csrf, key, server.token, watchUrl, sessionCookies)
                val resolved: ResolveResponse? = runCatching {
                    parseJson<ResolveResponse>(resp.text)
                }.getOrNull()
                val mUrl: String? = resolved?.url?.takeIf { it.isNotBlank() }
                if (mUrl == null) {
                    rejected++
                    return@runCatching
                }

                val mHtml: String = getWithSession(fixUrl(mUrl), sessionCookies, referer = watchUrl).text
                val embed: String = Regex("iframe src=\"(https?://[^\"]+)\"").find(mHtml)?.groupValues?.get(1)
                    ?: return@runCatching

                found = true
                loadExtractor(embed, encodeForHeader(watchUrl), subtitleCallback, callback)
            }
        }
        if (!found) {
            throw ErrorLoadingException("Shahid4u: site rejected all server resolves ($rejected/${servers.size})")
        }
        return found
    }
}
