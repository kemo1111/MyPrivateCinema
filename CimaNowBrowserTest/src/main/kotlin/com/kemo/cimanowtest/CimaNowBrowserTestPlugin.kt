package com.kemo.cimanowtest

// Original experimental UI/session bridge. No upstream CimaNow implementation copied.
import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.utils.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@CloudstreamPlugin
class CimaNowBrowserTestPlugin : Plugin() {
    private val provider = CimaNowBrowserTestProvider()
    override fun load(context: Context) {
        registerMainAPI(provider)
        openSettings = {
            Handler(Looper.getMainLooper()).post {
                CommonActivity.activity?.let { activity ->
                    AlertDialog.Builder(activity)
                        .setTitle("CimaNow Browser Test • تجريبي")
                        .setMessage("ده اختبار منفصل، مش مصدر مكتمل. اختار CimaNow Browser Test من المصادر، افتح بطاقة الاختبار واضغط تشغيل. اتصفح الموقع واكمل التحقق بنفسك، وشغّل العمل من صفحة الموقع. لو ظهر مرشح فيديو، اختار مرشح العمل فقط للفحص والتسليم للمشغل. ممكن تظهر روابط إعلانات. الجلسة بحد أقصى ٤ دقائق ونص. مفيش خدمة توكن أو بروكسي، ومفيش سجل للروابط أو الكوكيز. التحقق من التشغيل على أندرويد لسه مطلوب.")
                        .setPositiveButton("تمام", null)
                        .show()
                }
            }
        }
    }
    override fun beforeUnload() { provider.cancel() }
}

class CimaNowBrowserTestProvider : MainAPI() {
    override var name = "CimaNow Browser Test"
    override var mainUrl = "https://cimanow.cc"
    override var lang = "ar"
    override val hasMainPage = true
    override val supportedTypes = setOf(TvType.Movie)
    override val loadLinksTimeoutMs: Long = 300_000L
    override val mainPage = mainPageOf("browser" to "اختبار المتصفح، تجريبي")
    @Volatile private var bridge: BrowserBridge? = null

    private fun entry() = newMovieSearchResponse("ابدأ اختبار متصفح سيما ناو", "$mainUrl/", TvType.Movie) { }
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse =
        newHomePageResponse(request.name, listOf(entry()), false)
    override suspend fun search(query: String): List<SearchResponse> = listOf(entry())
    override suspend fun load(url: String): LoadResponse =
        newMovieLoadResponse("اختبار سيما ناو في المتصفح", "$mainUrl/", TvType.Movie, "$mainUrl/") {
            plot = "نسخة تجريبية منفصلة. اضغط تشغيل، اختار العمل من الموقع واكمل التحقق بنفسك. اضغط تشغيل العمل في صفحة الموقع. مرشحات الفيديو قد تشمل إعلانات، اختار مرشح العمل فقط للفحص ثم التسليم لمشغل CloudStream. مفيش ضمان تشغيل قبل التجربة على جهازك. البحث والقوائم داخل الموقع فقط حالياً."
        }

    override suspend fun loadLinks(
        data: String, isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        if (isCasting) return false
        val media = suspendCancellableCoroutine<BrowserBridge.Media?> { continuation ->
            Handler(Looper.getMainLooper()).post {
                if (!continuation.isActive) return@post
                val activity = CommonActivity.activity
                if (activity == null || activity.isFinishing || activity.isDestroyed) {
                    continuation.resume(null)
                    return@post
                }
                cancel()
                lateinit var current: BrowserBridge
                current = BrowserBridge(activity, object : BrowserBridge.Listener {
                    override fun onMedia(media: BrowserBridge.Media) {
                        if (bridge === current) bridge = null
                        if (continuation.isActive) continuation.resume(media)
                    }
                    override fun onClosed(reason: String) {
                        if (bridge === current) bridge = null
                        Toast.makeText(activity.applicationContext, reason, Toast.LENGTH_LONG).show()
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
                bridge = current
                continuation.invokeOnCancellation { current.cancel() }
                if (continuation.isActive) current.open("$mainUrl/") else current.cancel()
            }
        } ?: return false
        callback(newExtractorLink(name, "CimaNow Browser Test • مرشح فحص مبدئي", media.url,
            if (media.hls) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO) {
            headers = media.headers.filterKeys { !it.equals("referer", ignoreCase = true) }
            referer = media.headers.entries.firstOrNull { it.key.equals("referer", ignoreCase = true) }?.value.orEmpty()
            quality = Qualities.Unknown.value
        })
        return true
    }
    fun cancel() { bridge?.cancel(); bridge = null }
}
