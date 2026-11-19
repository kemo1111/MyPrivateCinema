package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class CimaNowProvider : MainAPI() {
    override var mainUrl = "https://cimanow.cc"
    override var name = "CimaNow Kemo"
    override val hasMainPage = true
    override var lang = "ar"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    override val mainPage = mainPageOf(
        "$mainUrl/category/الافلام/" to "الأفلام",
        "$mainUrl/category/المسلسلات/" to "المسلسلات",
        "$mainUrl/category/افلام-عربية/" to "أفلام عربية",
        "$mainUrl/category/افلام-اجنبية/" to "أفلام أجنبية",
        "$mainUrl/category/مسلسلات-عربية/" to "مسلسلات عربية",
        "$mainUrl/category/مسلسلات-اجنبية/" to "مسلسلات أجنبية"
    )

    private val requestHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36"
    )

    private suspend fun getCleanDoc(url: String, referer: String = "$mainUrl/"): Document {
        val response = app.get(
            url,
            headers = requestHeaders + ("Referer" to referer)
        )
        val html = response.text
        if (html.contains("String.fromCharCode") && html.contains("atob(")) {
            val decoded = decodeCimaNow(html)
            if (decoded.isNotBlank()) {
                return Jsoup.parse(decoded, url)
            }
        }
        return response.document
    }

    private fun decodeCimaNow(html: String): String {
        return try {
            val offsetMatch = Regex("""-\s*(\d+)\s*\)""").find(html) ?: return ""
            val offset = offsetMatch.groupValues[1].toIntOrNull() ?: return ""
            val arrayMatch = Regex("""\[\s*"([^"]+)"(?:\s*,\s*"[^"]+")*\s*]""").find(html) ?: return ""
            val chunks = Regex(""""([^"]+)"""").findAll(arrayMatch.value).map { it.groupValues[1] }.toList()
            val sb = StringBuilder()
            for (chunk in chunks) {
                val decodedStr = base64Decode(chunk)
                val digits = decodedStr.replace(Regex("""\D"""), "")
                val code = digits.toIntOrNull()
                if (code != null) {
                    sb.append((code - offset).toChar())
                }
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page > 1) "${request.data.trimEnd('/')}/page/$page/" else request.data
        val document = getCleanDoc(pageUrl)
        val home = document.select("article[aria-label=post], div.owl-item, section a[href]")
            .mapNotNull { item: Element -> item.toSearchResult() }
            .distinctBy { it.url }
        return newHomePageResponse(request.name, home, home.isNotEmpty())
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag: Element = if (this.tagName() == "a") {
            this
        } else {
            this.selectFirst("a") ?: return null
        }
        val href = aTag.attr("href")
        if (href.isBlank() || href.contains("/category/") || href.contains("/tag/") || !href.startsWith("http")) {
            return null
        }
        val titleEl: Element? = aTag.selectFirst("li[aria-label=title]")
        val imgEl: Element? = aTag.selectFirst("img")
        val title = titleEl?.text()?.trim()
            ?.ifEmpty { aTag.attr("title") }
            ?: imgEl?.attr("alt")
            ?: return null
        if (title.isBlank()) return null

        val posterUrl = imgEl?.attr("data-src")?.ifEmpty { imgEl.attr("src") }
        val isSeries = title.contains("مسلسل") || href.contains("مسلسل") || href.contains("%D9%85%D8%B3%D9%84%D8%B3%D9%84")

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
        val document = getCleanDoc(url)
        return document.select("article[aria-label=post], section a[href]")
            .mapNotNull { item: Element -> item.toSearchResult() }
            .distinctBy { it.url }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = getCleanDoc(url)
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: document.selectFirst("h1")?.text()
            ?: "فيلم"
        val posterImg: Element? = document.selectFirst("figure img, img.lazy")
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.ifEmpty { null }
            ?: posterImg?.attr("data-src")?.ifEmpty { posterImg.attr("src") }
        val plot = document.selectFirst("li[aria-label=story] p")?.text()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val epElements = document.select("ul#eps li a")
        if (epElements.isNotEmpty()) {
            val episodes = epElements.mapIndexed { index, el: Element ->
                val epUrl = el.attr("href")
                val epImg: Element? = el.selectFirst("img")
                val emText: String? = el.selectFirst("em")?.text()?.trim()
                val epName = if (!emText.isNullOrBlank()) {
                    "الحلقة $emText"
                } else {
                    el.text().trim().ifEmpty { "الحلقة ${index + 1}" }
                }
                val num = Regex("""(\d+)""").find(epName)?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                newEpisode(epUrl) {
                    this.name = epName
                    this.episode = num
                    this.posterUrl = epImg?.attr("data-src")?.ifEmpty { epImg.attr("src") }
                }
            }.sortedBy { it.episode }

            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        }

        val watchAnchor: Element? = document.selectFirst("a[href*=/watching/]")
        val watchUrl = watchAnchor?.attr("href")?.ifEmpty { "${url.trimEnd('/')}/watching/" }
            ?: "${url.trimEnd('/')}/watching/"

        return newMovieLoadResponse(title, url, TvType.Movie, watchUrl) {
            this.posterUrl = poster
            this.plot = plot
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val watchUrl = if (data.contains("/watching/")) {
            data
        } else {
            val epDoc = getCleanDoc(data)
            val watchAnchor: Element? = epDoc.selectFirst("a[href*=/watching/]")
            watchAnchor?.attr("href")?.ifEmpty { "${data.trimEnd('/')}/watching/" }
                ?: "${data.trimEnd('/')}/watching/"
        }

        val document = getCleanDoc(watchUrl, referer = data)
        val embedUrls = mutableListOf<String>()

        val activeIframe: Element? = document.selectFirst("li[aria-label=embed] iframe")
        val activeSrc = activeIframe?.attr("src")
        if (!activeSrc.isNullOrBlank()) {
            embedUrls.add(fixUrl(activeSrc))
        }

        val serverItems = document.select("ul#watch li[data-id][data-index]")
        serverItems.amap { item: Element ->
            val dataId = item.attr("data-id")
            val dataIndex = item.attr("data-index")
            if (dataId.isNotBlank() && dataIndex.isNotBlank()) {
                val endpoints = listOf(
                    "$mainUrl/wp-content/themes/Cima%20Now%20New/core/?action=switch&index=$dataIndex&id=$dataId",
                    "$mainUrl/wp-admin/admin-ajax.php?action=switch&index=$dataIndex&id=$dataId"
                )
                for (endpoint in endpoints) {
                    try {
                        val resDoc = app.get(
                            endpoint,
                            headers = requestHeaders + mapOf(
                                "X-Requested-With" to "XMLHttpRequest",
                                "Referer" to watchUrl
                            )
                        ).document
                        val iframeEl: Element? = resDoc.selectFirst("iframe")
                        val src = iframeEl?.attr("src")
                        if (!src.isNullOrBlank()) {
                            synchronized(embedUrls) {
                                embedUrls.add(fixUrl(src))
                            }
                            break
                        }
                    } catch (_: Exception) {
                    }
                }
            }
        }

        val downloadLinks = document.select("ul#download a[href]")
        downloadLinks.amap { el: Element ->
            val href = el.attr("href")
            val label = el.text().trim().ifEmpty { "CimaNow Direct" }
            if (href.startsWith("http")) {
                if (href.contains(".mp4", ignoreCase = true) || href.contains(".m3u8", ignoreCase = true)) {
                    val type = if (href.contains(".m3u8", ignoreCase = true)) {
                        ExtractorLinkType.M3U8
                    } else {
                        ExtractorLinkType.VIDEO
                    }
                    callback.invoke(
                        newExtractorLink(
                            name,
                            "CimaNow $label",
                            href,
                            type
                        ) {
                            this.referer = watchUrl
                            this.quality = getQualityFromName(label)
                        }
                    )
                } else if (!href.contains(mainUrl)) {
                    synchronized(embedUrls) {
                        embedUrls.add(href)
                    }
                }
            }
        }

        embedUrls.distinct().amap { embedUrl ->
            try {
                loadExtractor(embedUrl, watchUrl, subtitleCallback, callback)
            } catch (_: Exception) {
            }
        }

        return true
    }
}
