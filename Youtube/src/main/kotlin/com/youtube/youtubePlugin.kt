// Vendored from 3rabi / re-3arabi (Arabic CloudStream extensions): https://github.com/Abodabodd/re-3arabi
// Module: Youtube (published plugin version 3). Upstream author: Abodabodd. Retrieved 2026-11-25.
// Use and modification are granted by the upstream README (section "الحقوق": rights open to all users
// with mention of the original repository). Original repository: https://github.com/Abodabodd/re-3arabi
package com.youtube

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class YoutubeTokenPlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("YouTube", Context.MODE_PRIVATE)
        registerMainAPI(com.lagradost.cloudstream3.ar.youtube.YoutubeProvider(sharedPref))
        openSettings = { ctx ->
            val activity = ctx as? AppCompatActivity

            if (activity != null) {
                com.youtube.YoutubeSettingsBottomSheet.show(activity.supportFragmentManager, sharedPref)
            }
        }
    }
}
