/*
 * AkwamProvider is adapted from AkwamProvider.kt in
 * https://github.com/ramailo1/arprov
 *
 * MIT License
 *
 * Copyright (c) 2025 ramailo1
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 * Changes: package com.example, name "Akwam Kemo", mainUrl https://akwams.org.
 * Adapted to the live akwams.org markup after hands-on verification
 * (2026-11-22):
 * - search: the site has no /search?q= endpoint (404); real search is
 *   /?s=query and returns .movie__block items, not entry-box.
 * - mainPage: /shows is 404; replaced with /recent (verified 200).
 * - load: poster images lazy-load via data-src (src is a load.jpg
 *   placeholder); title cleaned of "مشاهدة"/episode suffix noise.
 * - loadLinks: mirror regex extended with stmruby/rubystream (observed
 *   serving episode streams); unsupported hosts ignored gracefully.
 * Verified live: /movies and /series listings (entry-box + a.box markup),
 * movie/episode /watch pages (iframe), /download pages (mirror anchors),
 * #series-episodes container with a.text-white episode links.
 */
package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class AkwamProvider : MainAPI() {
    override var lang = "ar"
    override var mainUrl = "https://akwams.org"
    override var name = "Akwam Kemo"
    override val usesWebView = false
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.TvSeries, TvType.Movie, TvType.Anime, TvType.Cartoon)

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun isSkippedPath(url: String): Boolean {
        return url.contains("/games/") || url.contains("/programs/") || url.contains("/category/")
    }

    // Explicit types: jsoup's jspecify-annotated returns break Kotlin 2.x type
    // inference in chained lambdas (compile error on the CI toolchain).
    private fun pickPoster(img: Element?): String? {
        if (img == null) return null
        val dataSrc: String = img.attr("data-src")
        if (dataSrc.isNotBlank() && !dataSrc.contains("load.jpg")) return dataSrc
        val src: String = img.attr("src")
        if (src.isNotBlank() && !src.contains("load.jpg")) return src
        return null
    }

    // Listing pages (/movies, /series, /recent): div.entry-box with a.box
    private fun Element.toSearchResponse(): SearchResponse? {
        val a = selectFirst("a.box, a") ?: return null
        val url = a.attr("href") ?: return null
        if (isSkippedPath(url)) return null
        val poster = selectFirst("picture > img, img")
        val title = poster?.attr("alt")?.takeIf { it.isNotBlank() }
            ?: poster?.attr("title")?.takeIf { it.isNotBlank() }
            ?: selectFirst("h3, .entry-title")?.text()
            ?: return null
        val posterUrl = pickPoster(poster)
        val year = selectFirst(".badge-secondary, .badge")?.text()?.toIntOrNull()

        return newMovieSearchResponse(
            cleanTitle(title),
            fixUrl(url),
            if (url.contains("/series/")) TvType.TvSeries else TvType.Movie,
        ) {
            this.posterUrl = posterUrl?.let { fixUrlNull(it) }
            this.year = year
        }
    }

    // Search results (/?s=query): a.movie__block items, episode-level entries
    private fun Element.toSearchResultResponse(): SearchResponse? {
        val a = selectFirst("a.movie__block") ?: return null
        val url = a.attr("href").takeIf { it.isNotBlank() } ?: return null
        if (isSkippedPath(url)) return null
        val isEpisode = a.hasClass("is__episode")
            || selectFirst("div.item__contents")?.hasClass("is__episode") == true
        val poster = selectFirst("img")
        val title = a.attr("title").takeIf { it.isNotBlank() }
            ?: poster?.attr("alt")?.takeIf { it.isNotBlank() }
            ?: return null
        val posterUrl = pickPoster(poster)

        return newMovieSearchResponse(
            cleanTitle(title),
            fixUrl(url),
            if (isEpisode) TvType.TvSeries else TvType.Movie,
        ) {
            this.posterUrl = posterUrl?.let { fixUrlNull(it) }
        }
    }

    private fun cleanTitle(raw: String): String {
        var t = raw.trim()
        t = t.removePrefix("مشاهدة").trim()
        // Drop trailing episode markers like "الحلقة 12 مترجمة"
        t = t.replace(Regex("""\s*الحلقة\s*\d+.*$"""), "").trim()
        return t.ifBlank { raw.trim() }
    }

    override val mainPage = mainPageOf(
        "$mainUrl/movies?page=" to "أفلام",
        "$mainUrl/series?page=" to "مسلسلات",
        "$mainUrl/recent?page=" to "أضيف حديثًا"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(request.data + page).document
        val list = doc.select("div.entry-box").mapNotNull { element ->
            element.toSearchResponse()
        }
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/", params = mapOf("s" to query)).document
        return doc.select("li:has(a.movie__block), div.item__contents").mapNotNull {
            it.toSearchResultResponse()
        }.distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val cleanUrl = fixUrl(url)
        val doc = app.get(cleanUrl).document
        val title = cleanTitle(
            doc.selectFirst("h1.entry-title, h1")?.text()?.trim() ?: "Unknown"
        )
        val posterUrl = pickPoster(
            doc.selectFirst("picture > img, .entry-image img, img.img-fluid, img")
        )?.let { fixUrlNull(it) }

        val year = doc.select("div.font-size-16.text-white.mt-2, .widget-body div").firstOrNull {
            it.text().contains("السنة")
        }?.text()?.getIntFromText()

        val duration = doc.select("div.font-size-16.text-white.mt-2, .widget-body div").firstOrNull {
            it.text().contains("مدة الفيلم") || it.text().contains("المدة")
        }?.text()?.getIntFromText()

        val synopsis = doc.selectFirst("div.widget-body p, .entry-content p")?.text()?.trim()

        val tags = doc.select("div.font-size-16.d-flex.align-items-center.mt-3 > a, .badge-primary").map {
            it.text().trim()
        }.filter { it.isNotBlank() }

        val actors = doc.select("div.widget-body > div > div.entry-box > a").mapNotNull {
            val name = it?.selectFirst("div > .entry-title")?.text() ?: return@mapNotNull null
            val image = it.selectFirst("div > img")?.attr("src") ?: return@mapNotNull null
            ActorData(Actor(name, fixUrl(image)))
        }

        val epElements = doc.select("#series-episodes a.text-white, #series-episodes a[href]")
            .toList()
            .filter { it.attr("href").isNotBlank() && it.text().isNotBlank() }
            .distinctBy { it.attr("href") }

        val isSeries = epElements.isNotEmpty() || cleanUrl.contains("/series/")

        return if (isSeries && epElements.isNotEmpty()) {
            val episodes = epElements.mapNotNull { a ->
                val epHref = fixUrl(a.attr("href"))
                val epText = a.text().trim()
                val epNum = epText.getIntFromText()
                    ?: Regex("""(?i)الحلقة[^\d]*(\d+)""").find(epHref)?.groupValues?.get(1)?.toIntOrNull()
                newEpisode(epHref) {
                    name = epText
                    episode = epNum
                    this.posterUrl = posterUrl
                }
            }.let { eps ->
                val isReversed = (eps.lastOrNull()?.episode ?: 1) < (eps.firstOrNull()?.episode ?: 0)
                if (isReversed) eps.reversed() else eps
            }

            newTvSeriesLoadResponse(title, cleanUrl, TvType.TvSeries, episodes) {
                this.duration = duration
                this.posterUrl = posterUrl
                this.tags = tags
                this.year = year
                this.plot = synopsis
                this.actors = actors
            }
        } else {
            newMovieLoadResponse(title, cleanUrl, TvType.Movie, cleanUrl) {
                this.posterUrl = posterUrl
                this.year = year
                this.plot = synopsis
                this.tags = tags
                this.duration = duration
                this.actors = actors
            }
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
        val baseUrl = data.trimEnd('/')
        var found = false

        // 1. Watch page for the embedded player iframe
        val watchUrl = if (baseUrl.endsWith("/watch")) baseUrl else "$baseUrl/watch"
        try {
            val watchDoc = app.get(watchUrl, timeout = 15).document
            watchDoc.select("iframe[src]").forEach { ifr ->
                var src = ifr.attr("src").trim()
                if (src.startsWith("//")) src = "https:$src"
                if (src.startsWith("http") && !src.contains("ads") && !src.contains("facebook")) {
                    found = true
                    loadExtractor(src, baseUrl, subtitleCallback, callback)
                }
            }
        } catch (_: Exception) {}

        // 2. Download page for video mirror hosts
        val downloadUrl = if (baseUrl.endsWith("/download")) baseUrl else "$baseUrl/download"
        try {
            val downloadDoc = app.get(downloadUrl, timeout = 15).document
            val mirrorRegex = Regex("""(?i)(?:mixdrop|dood|streamruby|stmruby|rubystream|send\.cm|uqload|streamwish|byseraguci|vidhide|voe|hgcloud|mp4upload|vidmoly)""")
            downloadDoc.select("a[href]").forEach { a ->
                var href = a.attr("href").trim()
                if (href.startsWith("//")) href = "https:$href"
                if (href.startsWith("http") && mirrorRegex.containsMatchIn(href)) {
                    found = true
                    loadExtractor(href, baseUrl, subtitleCallback, callback)
                }
            }
        } catch (_: Exception) {}

        return found
    }
}
