
package com.hexated

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class SoraStreamPlugin: Plugin() {
    override fun load(context: Context) {
        TmdbKey.init(context)
        openSettings = { ctx ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                val host = com.lagradost.cloudstream3.CommonActivity.activity ?: ctx
                TmdbSettingsDialog.build(host).show()
            }
        }
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(SoraStream())
        registerMainAPI(SoraStreamLite())
        registerExtractorAPI(Animefever())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(MultimoviesSB())
        registerExtractorAPI(Yipsu())
        registerExtractorAPI(Mwish())
        registerExtractorAPI(Playm4u())
        registerExtractorAPI(VCloud())
        registerExtractorAPI(Pixeldra())
        registerExtractorAPI(M4ufree())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(Streamwish())
        registerExtractorAPI(FilelionsTo())
        registerExtractorAPI(Embedwish())
        registerExtractorAPI(UqloadsXyz())
        registerExtractorAPI(Uploadever())
        registerExtractorAPI(Netembed())
        registerExtractorAPI(Flaswish())
        registerExtractorAPI(Comedyshow())
        registerExtractorAPI(Ridoo())
        registerExtractorAPI(Streamvid())
        registerExtractorAPI(Embedrise())
        registerExtractorAPI(Gdmirrorbot())
        registerExtractorAPI(FilemoonNl())
        registerExtractorAPI(Alions())
    }
}