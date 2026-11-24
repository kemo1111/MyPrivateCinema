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
        "$mainUrl/tvshows/" to "مسلسلات"
    )

    private fun String.getImageURL(): String? {
        return this.replace("--im(age|g):url\\(|\\);".toRegex(), "")
            .replace("background-image:url\\('|'\\);".toRegex(), "")
            .ifBlank { null }
    }

    private fun String.getIntFromText(): Int? {
        return Regex("""\d+""").find(this)?.groupValues?.firstOrNull()?.toIntOrNull()
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val aTag = selectFirst("div.Thumb--GridItem a") ?: return null
        val href = aTag.attr("href")
        if (href.isBlank()) return null
        val fullTitle = aTag.attr("title").ifBlank { aTag.selectFirst("strong")?.text() ?: return null }
        val year = fullTitle.getIntFromText()
        val title = fullTitle
            .replace("($year)", "")
            .replace(Regex("مشاهدة|مترجمة|مترجم|اون لاين"), "")
            .trim()

        val posterUrl = selectFirst("span.BG--GridItem")?.attr("data-src")?.ifBlank { null }
            ?: selectFirst("meta[itemprop=thumbnailUrl]")?.attr("content")

        val tvType = when {
            fullTitle.contains("انمي") || fullTitle.contains("أنمي") -> TvType.Anime
            fullTitle.contains("فيلم") -> TvType.Movie
            else -> TvType.TvSeries
        }

        return when (tvType) {
            TvType.Movie -> newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.year = year
            }
            TvType.Anime -> newAnimeSearchResponse(title, href, TvType.Anime) {
                this.posterUrl = posterUrl
            }
            else -> newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val pageUrl = if (page > 1) "${request.data.trimEnd('/')}/page/$page/" else request.data
        val document = app.get(pageUrl).document
        val home = document.select("div.Grid--WecimaPosts div.GridItem")
            .mapNotNull { it.toSearchResult() }
            .distinctBy { it.url }
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
        val document = app.get(url).document

        val rawTitle = document.selectFirst("div.Title--Content--Single-begin h1")?.text()
            ?: document.selectFirst("meta[property=og:title]")?.attr("content")
            ?: "بدون عنوان"
        val year = rawTitle.getIntFromText()
        val title = rawTitle
            .replace("($year)", "")
            .replace(Regex("مشاهدة|مترجمة|مترجم|اون لاين"), "")
            .trim()

        val poster = document.selectFirst("mycima.separated--top")?.attr("style")?.getImageURL()
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("meta[itemprop=thumbnailUrl]")?.attr("content")

        val plot = document.selectFirst("div.StoryMovieContent")?.text()
            ?: document.selectFirst("div.PostItemContent")?.text()
            ?: document.selectFirst("meta[property=og:description]")?.attr("content")

        val recommendations = document.select("div.Grid--WecimaPosts div.GridItem")
            .mapNotNull { it.toSearchResult() }

        val episodeLinks = document.select("div.Seasons--Episodes div.EpisodesList a")
            .distinctBy { it.attr("href") }

        val isAnime = rawTitle.contains("انمي") || rawTitle.contains("أنمي")

        return if (episodeLinks.isNotEmpty()) {
            // صفحة مسلسل: نجمع الحلقات من قائمة الحلقات (والموسم لو موجود)
            val episodes = episodeLinks.mapIndexed { index, el ->
                val epTitle = el.selectFirst("episodetitle")?.text()?.trim()
                    ?.ifBlank { null }
                    ?: el.attr("title").trim()
                newEpisode(el.attr("href")) {
                    this.name = epTitle
                    this.episode = epTitle.getIntFromText() ?: (index + 1)
                }
            }.sortedBy { it.episode }

            newTvSeriesLoadResponse(title, url, if (isAnime) TvType.Anime else TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.recommendations = recommendations
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        val embedUrls = mutableListOf<String>()

        // التصميم الجديد: أزرار السيرفرات button.mc-server-btn فيها data-url مباشر
        document.select("button.mc-server-btn[data-url]").forEach { b ->
            val u = b.attr("data-url")
            if (u.startsWith("http")) embedUrls.add(u)
        }

        // التصميم القديم: روابط مشفرة Base64 داخل ul#watch
        document.select("ul#watch li[data-encoded]").forEach { li ->
            val decoded = try {
                base64Decode(li.attr("data-encoded"))
            } catch (_: Exception) {
                ""
            }
            if (decoded.startsWith("http")) {
                embedUrls.add(decoded)
            }
        }

        // سيرفرات التحميل: نفس الروابط بصيغة /d/ نحولها لصيغة المشاهدة /e/
        document.select("ul.List--Download--Wecima--Single li a[href]").forEach { a ->
            val href = a.attr("href")
            if (href.startsWith("http")) {
                embedUrls.add(href.replace("/d/", "/e/"))
            }
        }

        var found = false
        embedUrls.distinct().amap { embedUrl ->
            try {
                if (loadExtractor(embedUrl, data, subtitleCallback, callback)) {
                    found = true
                }
            } catch (_: Exception) {
            }
        }
        return found
    }
}
