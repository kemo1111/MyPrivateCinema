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
