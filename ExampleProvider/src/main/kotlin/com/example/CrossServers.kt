package com.example

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** User-facing on/off switch, stored in plain SharedPreferences. Default: on. */
object CrossServerSettings {
    private const val PREFS = "kemo_plugin_prefs"
    private const val KEY = "cross_servers_enabled"
    @Volatile var appContext: Context? = null

    fun isEnabled(): Boolean =
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.getBoolean(KEY, true) ?: true

    fun setEnabled(value: Boolean) {
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY, value)?.apply()
    }
}

/**
 * Movies only. When a film is opened from one provider, searches the other providers of
 * this plugin for the same film and adds their servers, tagged with the site name.
 * Any sibling that fails, times out or has no confident match is skipped.
 */
object CrossServers {
    private const val PER_SITE_TIMEOUT_MS = 10_000L
    private const val SEARCH_CANDIDATES = 5

    private val noise = listOf(
        "فيلم", "مترجم", "مترجمة", "اون لاين", "أون لاين", "مشاهدة", "تحميل", "كامل", "مدبلج", "بجودة", "جودة",
        "hd", "web-dl", "webdl", "bluray", "720p", "1080p", "4k"
    )

    private fun fold(x: String) = x.replace(Regex("[أإآ]"), "ا").replace("ى", "ي").replace("ة", "ه")

    fun normalize(title: String): String {
        var t = title.lowercase()
        t = t.replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "") // tashkeel, tatweel
        t = fold(t)
        t = t.replace(Regex("\\(?\\b(19|20)\\d{2}\\b\\)?"), " ")
        for (n in noise) t = t.replace(fold(n), " ")
        t = t.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        return t.replace(Regex("\\s+"), " ")
    }

    /** True only when the normalized titles are equal and the years do not contradict. */
    fun sameFilm(titleA: String, yearA: Int?, titleB: String, yearB: Int?): Boolean {
        val a = normalize(titleA)
        val b = normalize(titleB)
        if (a.isEmpty() || b.isEmpty() || a != b) return false
        if (yearA != null && yearB != null && yearA != yearB) return false
        return true
    }

    private fun yearInText(s: String): Int? =
        Regex("\\b(19|20)\\d{2}\\b").find(s)?.value?.toIntOrNull()

    private fun tag(link: ExtractorLink, site: String): ExtractorLink {
        if (link is ExtractorLinkPlayList) return link
        return runCatching {
            @Suppress("DEPRECATION")
            ExtractorLink(
                "[$site] ${link.source}",
                "[$site] ${link.name}",
                link.url,
                link.referer,
                link.quality,
                link.headers,
                link.extractorData,
                link.type,
                link.audioTracks
            )
        }.getOrDefault(link)
    }

    suspend fun addSiblingServers(
        self: MainAPI,
        title: String,
        year: Int?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        if (!CrossServerSettings.isEnabled()) return
        if (normalize(title).isEmpty()) return
        val siblings = APIHolder.allProviders.toList().filter {
            it !== self && it.name != self.name && it.sourcePlugin != null && it.sourcePlugin == self.sourcePlugin
        }
        val results = coroutineScope {
            siblings.map { api ->
                async {
                    withTimeoutOrNull(PER_SITE_TIMEOUT_MS) {
                        runCatching { collectFrom(api, title, year, subtitleCallback) }.getOrNull()
                    }
                }
            }.awaitAll()
        }
        val seen = HashSet<String>()
        for (r in results) {
            val (site, links) = r ?: continue
            links.forEach { if (seen.add(it.url)) callback(tag(it, site)) }
        }
    }

    private suspend fun collectFrom(
        api: MainAPI,
        title: String,
        year: Int?,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Pair<String, List<ExtractorLink>>? {
        val hits = api.search(title)?.take(SEARCH_CANDIDATES) ?: return null
        for (hit in hits) {
            if (!sameFilm(title, year, hit.name, yearInText(hit.name))) continue
            val resp = api.load(hit.url) as? MovieLoadResponse ?: continue
            if (!sameFilm(title, year, resp.name, resp.year)) continue
            val links = mutableListOf<ExtractorLink>()
            val ok = runCatching {
                api.loadLinks(resp.dataUrl, false, subtitleCallback) { links.add(it) }
            }.getOrDefault(false)
            if (links.isNotEmpty()) return api.name.removeSuffix(" Kemo").trim() to links
            if (ok) return null
        }
        return null
    }
}
