package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class ExampleProvider : MainAPI() {
    override var mainUrl = "https://topcinemaa.one"
    override var name = "TopCinema Kemo"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "home" to "الرئيسية",
        "افلام اجنبي" to "أفلام أجنبي",
        "افلام انمي" to "أفلام أنمي",
        "افلام اسيوي" to "أفلام آسيوي",
        "مسلسلات اجنبي" to "مسلسلات أجنبي",
        "مسلسلات اسيوي" to "مسلسلات آسيوية",
        "مسلسلات انمي" to "مسلسلات أنمي"
    )

    private var cachedMenu: Map<String, String> = emptyMap()

    private fun normalizeAr(text: String): String {
        return text
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .trim()
    }

    private suspend fun getMenuLinks(): Map<String, String> {
        if (cachedMenu.isNotEmpty()) return cachedMenu
        val doc = app.get("$mainUrl/").document
        val links = mutableMapOf<String, String>()
        doc.select("a[href]").forEach { el: Element ->
            val href = el.attr("href")
            val text = normalizeAr(el.text())
            if (href.contains("/category/") && text.isNotBlank()) {
                links[text] = fixUrl(href)
            }
        }
        if (links.isNotEmpty()) {
            cachedMenu = links
        }
        return links
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val targetUrl = if (request.data == "home") {
            "$mainUrl/"
        } else {
            val menu = getMenuLinks()
            val key = normalizeAr(request.data)
            menu.entries.firstOrNull { it.key == key }?.value
                ?: menu.entries.firstOrNull { it.key.contains(key) }?.value
        }

        if (targetUrl.isNullOrBlank()) {
            return newHomePageResponse(request.name, emptyList(), false)
        }

        val pageUrl = if (page > 1) "${targetUrl.trimEnd('/')}/page/$page/" else targetUrl
        val document = app.get(pageUrl).document
        val home = document.select("div.Small--Box, div.Block--Item")
            .mapNotNull { item: Element -> item.toSearchResult() }
            .distinctBy { it.url }

        return newHomePageResponse(request.name, home, home.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag: Element = this.selectFirst("a") ?: return null
        val h3Title = this.selectFirst("h3.title")?.text()
        val title = aTag.attr("title").ifEmpty { h3Title ?: aTag.text() }
        val href = aTag.attr("href")
        if (href.isBlank() || title.isBlank()) return null
        val img: Element? = this.selectFirst("img")
        val posterUrl = img?.attr("data-src")?.ifEmpty { img.attr("src") }

        val isSeries = title.contains("مسلسل") || title.contains("الموسم")
        return if (isSeries) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.Small--Box, div.Block--Item")
            .mapNotNull { item: Element -> item.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.post-title")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "فيلم"
        val posterImg: Element? = document.selectFirst("div.Poster--Block img, div.Poster img")
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.ifEmpty { null }
            ?: posterImg?.attr("data-src")?.ifEmpty { posterImg.attr("src") }
        val plot = document.selectFirst("div.story p")?.text()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val episodes = mutableListOf<Episode>()
        val epLinks = document.select("section.allepcont a, div.Episodes--List a, div.row a[href]")
            .filter { el: Element ->
                val h = el.attr("href")
                val t = el.attr("title").ifEmpty { el.text() }
                (h.contains("الحلقة") || h.contains("%D8%A7%D9%84%D8%AD%D9%84%D9%82%D8%A9") || t.contains("الحلقة")) &&
                    !h.contains("/watch") && !h.contains("/download")
            }
            .distinctBy { el: Element -> el.attr("href") }

        if (epLinks.isNotEmpty() && !url.contains("الحلقة") && !url.contains("%D8%A7%D9%84%D8%AD%D9%84%D9%82%D8%A9")) {
            epLinks.forEachIndexed { index, el ->
                val epUrl = el.attr("href")
                val epName = el.attr("title").ifEmpty { el.text().trim() }.ifEmpty { "الحلقة ${index + 1}" }
                val numMatch = Regex("""(\d+)""").find(epName)
                val epNum = numMatch?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                episodes.add(
                    newEpisode(epUrl) {
                        this.name = epName
                        this.episode = epNum
                    }
                )
            }
            val sortedEpisodes = episodes.sortedBy { it.episode }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, sortedEpisodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        }

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.plot = plot
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
        val watchUrl = "$baseUrl/watch/"
        val document = app.get(watchUrl).document
        val iframeUrls = mutableListOf<String>()

        val directIframe: String? = document.selectFirst("div.player--iframe iframe")?.attr("src")
        if (!directIframe.isNullOrBlank()) {
            iframeUrls.add(directIframe)
        }

        val serverItems = document.select("li.server--item")
        serverItems.amap { item: Element ->
            val dataId = item.attr("data-id")
            val serverNum = item.attr("data-server")
            if (dataId.isNotBlank() && serverNum.isNotBlank()) {
                try {
                    val response = app.post(
                        "$mainUrl/wp-content/themes/movies2023/Ajaxat/Single/Server.php",
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "Origin" to mainUrl,
                            "Referer" to watchUrl
                        ),
                        data = mapOf(
                            "id" to dataId,
                            "i" to serverNum
                        )
                    )
                    val resDoc = response.document
                    val iframeEl: Element? = resDoc.selectFirst("iframe")
                    val src: String? = iframeEl?.attr("src")?.ifEmpty { iframeEl.attr("data-src") }
                    if (!src.isNullOrBlank()) {
                        synchronized(iframeUrls) {
                            iframeUrls.add(src)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        try {
            val downloadUrl = "$baseUrl/download/"
            val dlDoc = app.get(downloadUrl, referer = data).document
            val dlLinks = dlDoc.select("a.downloadsLink, div.Download--List a, ul.Download--List a")
            dlLinks.forEach { el: Element ->
                val href = el.attr("href")
                if (href.startsWith("http") && !href.contains(mainUrl)) {
                    synchronized(iframeUrls) {
                        iframeUrls.add(href)
                    }
                }
            }
        } catch (_: Exception) {
        }

        iframeUrls.distinct().amap { iframeUrl ->
            try {
                val found = loadExtractor(iframeUrl, watchUrl, subtitleCallback, callback)
                if (!found || iframeUrl.contains("vidtube", ignoreCase = true) || iframeUrl.contains("updown", ignoreCase = true)) {
                    val embedHtml = app.get(iframeUrl, referer = watchUrl).text
                    val unpacked = if (embedHtml.contains("eval(function(p,a,c,k,e,d)")) {
                        getAndUnpack(embedHtml)
                    } else {
                        embedHtml
                    }
                    val matches = Regex("""file\s*:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""")
                        .findAll(unpacked)
                    for (match in matches) {
                        val streamUrl = match.groupValues[1]
                        if (streamUrl.isNotBlank()) {
                            val type = if (streamUrl.contains(".m3u8")) {
                                ExtractorLinkType.M3U8
                            } else {
                                ExtractorLinkType.VIDEO
                            }
                            val hostName = Regex("""https?://(?:www\.)?([^/]+)""")
                                .find(iframeUrl)?.groupValues?.get(1) ?: "Server"
                            callback.invoke(
                                newExtractorLink(
                                    name,
                                    hostName,
                                    streamUrl,
                                    type
                                ) {
                                    this.referer = iframeUrl
                                    this.quality = Qualities.Unknown.value
                                }
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return true
    }
}
