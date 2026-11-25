package com.example

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class ExamplePlugin: Plugin() {
    private var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as? AppCompatActivity
        CrossServerSettings.appContext = context.applicationContext

        // All providers should be added in this manner
        registerMainAPI(ExampleProvider())
        registerMainAPI(MyCimaProvider())
        registerMainAPI(ArabSeedProvider())
        registerMainAPI(Cima4UProvider())
        registerMainAPI(FaselHDProvider())
        registerMainAPI(EgyDeadProvider())
        registerMainAPI(Shahid4uProvider())
        registerMainAPI(AkwamProvider())
        registerMainAPI(AflaamProvider())

        // Extractors needed by Akwam watch pages (stmruby/streamruby players)
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(StmRuby())
        registerExtractorAPI(RubyStream())


        openSettings = {
            val frag = BlankFragment(this)
            activity?.let {
                frag.show(it.supportFragmentManager, "Frag")
            }
        }
    }
}
