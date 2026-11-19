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
        "$mainUrl/" to "الرئيسية"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home = document.select("div.Block--Item").mapNotNull { item: Element ->
            item.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag: Element = this.selectFirst("a") ?: return null
        val title = aTag.attr("title").ifEmpty { aTag.text() }
        val href = aTag.attr("href")
        val img: Element? = this.selectFirst("img")
        val posterUrl = img?.attr("data-src")?.ifEmpty { img.attr("src") }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val document = app.get(url).document
        return document.select("div.Block--Item").mapNotNull { item: Element ->
            item.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.post-title")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "فيلم"
        val posterImg: Element? = document.selectFirst("div.Poster--Block img")
        val poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.ifEmpty { null }
            ?: posterImg?.attr("data-src")?.ifEmpty { posterImg.attr("src") }
        val plot = document.selectFirst("div.story p")?.text()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

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
    ): Boolean {
        val watchUrl = "${data.trimEnd('/')}/watch/"
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
                    val res = app.post(
                        "$mainUrl/wp-content/themes/movies2023/Ajaxat/Single/Server.php",
                        headers = mapOf(
                            "X-Requested-With" to "XMLHttpRequest",
                            "Referer" to watchUrl
                        ),
                        data = mapOf(
                            "id" to dataId,
                            "i" to serverNum
                        )
                    ).document
                    val src: String? = res.selectFirst("iframe")?.attr("src")
                    if (!src.isNullOrBlank()) {
                        synchronized(iframeUrls) {
                            iframeUrls.add(src)
                        }
                    }
                } catch (_: Exception) {
                }
            }
        }

        iframeUrls.distinct().amap { iframeUrl ->
            try {
                loadExtractor(iframeUrl, watchUrl, subtitleCallback, callback)
                if (iframeUrl.contains("vidtube", ignoreCase = true)) {
                    val embedHtml = app.get(iframeUrl, referer = watchUrl).text
                    val unpacked = if (embedHtml.contains("eval(function(p,a,c,k,e,d)")) {
                        getAndUnpack(embedHtml)
                    } else {
                        embedHtml
                    }
                    val fileMatch = Regex("""file\s*:\s*["'](https?://[^"']+\.(?:m3u8|mp4)[^"']*)["']""")
                        .find(unpacked)
                    val streamUrl = fileMatch?.groupValues?.get(1)
                    if (!streamUrl.isNullOrBlank()) {
                        val type = if (streamUrl.contains(".m3u8")) {
                            ExtractorLinkType.M3U8
                        } else {
                            ExtractorLinkType.VIDEO
                        }
                        callback.invoke(
                            newExtractorLink(
                                name,
                                "VideoTube",
                                streamUrl,
                                type
                            ) {
                                this.referer = iframeUrl
                                this.quality = Qualities.Unknown.value
                            }
                        )
                    }
                }
            } catch (_: Exception) {
            }
        }

        return true
    }
}
