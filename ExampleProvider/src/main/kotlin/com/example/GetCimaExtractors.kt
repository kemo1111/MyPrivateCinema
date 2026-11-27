package com.example

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getAndUnpack
import com.lagradost.cloudstream3.utils.newExtractorLink

object GetCimaMixdropParse {
    /** Direct mp4 url from a Mixdrop embed page (the player script is p.a.c.k.e.d). */
    fun mp4(html: String): String? {
        val unpacked = runCatching { getAndUnpack(html) }.getOrNull() ?: CimamixHls.unpackFallback(html)
        for (text in listOfNotNull(unpacked, html)) {
            Regex("""(?:wurl|MDCore\.wurl)\s*=\s*["']([^"']+)["']""").find(text)?.groupValues?.get(1)?.let {
                return if (it.startsWith("//")) "https:$it" else it
            }
        }
        return null
    }
}

/** mixdrop.top embeds (used by getcima.net): one mp4 file, needs the embed page as Referer. */
class GetCimaMixdrop : ExtractorApi() {
    override val name = "Mixdrop"
    override val mainUrl = "https://mixdrop.top"
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
        val mp4 = GetCimaMixdropParse.mp4(html) ?: return
        callback(newExtractorLink(name, name, mp4, ExtractorLinkType.VIDEO) {
            this.referer = "$mainUrl/"
            this.quality = Qualities.Unknown.value
        })
    }
}

/** minochinos.com embeds: HLS master in a packed script. */
class GetCimaMinochinos : CimamixHlsExtractor("Minochinos", "https://minochinos.com")
