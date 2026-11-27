package com.example

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

/** Embed pages that put an HLS master playlist url in the page (plain or p.a.c.k.e.d). */
object CimamixHls {

    fun unpackFallback(html: String): String? {
        val header = "eval(function(p,a,c,k,e,d){"
        val start = html.indexOf(header)
        if (start < 0) return null
        val end = html.indexOf("'.split('|')", start)
        if (end < 0) return null
        val sepStart = html.lastIndexOf("',", end)
        if (sepStart < start) return null
        val sep = html.substring(sepStart + 2, end)
        val sepMatch = Regex("^(\\d+),(\\d+),'([\\s\\S]*)$").find(sep) ?: return null
        val a = sepMatch.groupValues[1].toInt()
        val c = sepMatch.groupValues[2].toInt()
        val k = sepMatch.groupValues[3].split("|")
        if (c > k.size) return null
        var p = html.substring(start + header.length, sepStart)
        for (i in c - 1 downTo 0) {
            val ki = k.getOrNull(i) ?: continue
            if (ki.isEmpty()) continue
            p = p.replace(Regex("\\b" + i.toString(a) + "\\b"), ki)
        }
        return p
    }

    fun findM3u8(html: String): String? {
        val unpacked = runCatching { getAndUnpack(html) }.getOrNull() ?: unpackFallback(html)
        for (text in listOfNotNull(unpacked, html)) {
            Regex("""file\s*:\s*["']([^"']+\.m3u8[^"']*)["']""").find(text)?.groupValues?.get(1)?.let { return it }
            Regex("""https?://[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*""").find(text)?.value?.let { return it }
        }
        return null
    }
}

open class CimamixHlsExtractor(override val name: String, override val mainUrl: String) : ExtractorApi() {
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val html = runCatching {
            app.get(url, headers = mapOf("Referer" to (referer ?: url)), timeout = 15L).text
        }.getOrNull() ?: return
        val m3u8 = CimamixHls.findM3u8(html) ?: return
        val links = runCatching { M3u8Helper.generateM3u8(name, m3u8, referer = url) }.getOrNull()
        if (!links.isNullOrEmpty()) {
            links.forEach(callback)
        } else {
            callback(newExtractorLink(name, name, m3u8, ExtractorLinkType.M3U8) {
                this.referer = url
                this.quality = Qualities.Unknown.value
            })
        }
    }
}

class CimamixAnafast : CimamixHlsExtractor("Anafast", "https://anafast.org")
class CimamixVidoba : CimamixHlsExtractor("Vidoba", "https://vidoba.org")
class CimamixVidspeed : CimamixHlsExtractor("Vidspeed", "https://vidspeed.org")

/** Uqload pages on this site serve an HLS master with 360/720/1080 variants; fall back to the built-in extractor. */
class CimamixUqload : CimamixHlsExtractor("Uqload", "https://uqload.is") {
    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        var emitted = false
        super.getUrl(url, referer, subtitleCallback) { emitted = true; callback(it) }
        if (!emitted) {
            runCatching { com.lagradost.cloudstream3.extractors.Uqload().getUrl(url, referer, subtitleCallback, callback) }
        }
    }
}
