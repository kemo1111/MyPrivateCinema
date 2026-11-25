package com.example

import android.content.Context
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

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
/** Marks calls made on behalf of another site, so those do not start their own search round. */
private object InsideCrossSearch : AbstractCoroutineContextElement(Key) {
    object Key : CoroutineContext.Key<InsideCrossSearch>
}

object CrossServers {
    private const val PER_SITE_TIMEOUT_MS = 25_000L
    private const val SEARCH_CANDIDATES = 5

    /** Providers that stay independent: they neither search other sites nor appear as a sibling. */
    private val isolated = setOf("MyCima Kemo")

    fun isIsolated(name: String) = name in isolated

    /** Sites that may serve as siblings for [selfName]: same plugin, not itself, not isolated. */
    fun <T> pickSiblings(selfName: String, selfPlugin: Any?, all: List<T>, nameOf: (T) -> String, pluginOf: (T) -> Any?): List<T> {
        if (isIsolated(selfName)) return emptyList()
        return all.filter {
            nameOf(it) != selfName && !isIsolated(nameOf(it)) && pluginOf(it) != null && pluginOf(it) == selfPlugin
        }
    }

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

    /** Search text without site wording or year, keeping the original letters. */
    fun searchQuery(title: String): String {
        var t = title.replace(Regex("\\(?\\b(19|20)\\d{2}\\b\\)?"), " ")
        for (n in noise) t = t.replace(Regex(Regex.escape(n), RegexOption.IGNORE_CASE), " ")
        t = t.replace(Regex("\\s+"), " ").trim()
        return if (t.isEmpty()) title else t
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

    /**
     * Runs a provider's own loadLinks, then (movies only, switch on) adds the same film's servers
     * from the other sites. [data] must be the page URL that load() accepts; if load(data) is
     * not a movie (for example an episode, or a site whose data is not a page URL), nothing is added.
     */
    suspend fun withSiblings(
        self: MainAPI,
        data: String,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
        own: suspend ((SubtitleFile) -> Unit, (ExtractorLink) -> Unit) -> Boolean
    ): Boolean {
        if (isIsolated(self.name)) return own(subtitleCallback, callback)
        val ok = own(subtitleCallback, callback)
        runCatching {
            if (CrossServerSettings.isEnabled() && coroutineContext[InsideCrossSearch.Key] == null) {
                val movie = self.load(data) as? MovieLoadResponse
                if (movie != null) addSiblingServers(self, movie.name, movie.year, subtitleCallback, callback)
            }
        }
        return ok
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
        val siblings = pickSiblings(self.name, self.sourcePlugin, APIHolder.allProviders.toList(), { it.name }, { it.sourcePlugin }).filter { it !== self }
        if (siblings.isEmpty()) return
        val seen = HashSet<String>()
        coroutineScope {
            val ch = Channel<Pair<String, List<ExtractorLink>>?>(siblings.size)
            for (api in siblings) {
                launch {
                    val r = withTimeoutOrNull(PER_SITE_TIMEOUT_MS) {
                        runCatching { collectFrom(api, title, year, subtitleCallback) }.getOrNull()
                    }
                    ch.send(r)
                }
            }
            // Emit each site's links as soon as that site finishes, from this coroutine only.
            repeat(siblings.size) {
                val r = ch.receive() ?: return@repeat
                val (site, links) = r
                links.forEach { if (seen.add(it.url)) callback(tag(it, site)) }
            }
        }
    }

    private suspend fun collectFrom(
        api: MainAPI,
        title: String,
        year: Int?,
        subtitleCallback: (SubtitleFile) -> Unit
    ): Pair<String, List<ExtractorLink>>? {
        val hits = api.search(searchQuery(title))?.take(SEARCH_CANDIDATES) ?: return null
        for (hit in hits) {
            if (!sameFilm(title, year, hit.name, yearInText(hit.name))) continue
            val resp = api.load(hit.url) as? MovieLoadResponse ?: continue
            if (!sameFilm(title, year, resp.name, resp.year)) continue
            val links = mutableListOf<ExtractorLink>()
            val ok = runCatching {
                withContext(InsideCrossSearch) { api.loadLinks(resp.dataUrl, false, subtitleCallback) { links.add(it) } }
            }.getOrDefault(false)
            if (links.isNotEmpty()) return api.name.removeSuffix(" Kemo").trim() to links
            if (ok) return null
        }
        return null
    }
}
