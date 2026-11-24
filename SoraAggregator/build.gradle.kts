version = 1

android {
    namespace = "com.hexated"
    buildFeatures { buildConfig = true }
    defaultConfig {
        // No API key is compiled in. The TMDB key is supplied by the user at runtime.
        buildConfigField("String", "TMDB_API", "\"\"")
        buildConfigField("String", "GHOSTX_API", "\"\"")
        buildConfigField("String", "CINEMATV_API", "\"\"")
        buildConfigField("String", "SFMOVIES_API", "\"\"")
        buildConfigField("String", "ZSHOW_API", "\"\"")
        buildConfigField("String", "DUMP_API", "\"\"")
        buildConfigField("String", "DUMP_KEY", "\"\"")
        buildConfigField("String", "CRUNCHYROLL_BASIC_TOKEN", "\"\"")
        buildConfigField("String", "CRUNCHYROLL_REFRESH_TOKEN", "\"\"")
    }
}

cloudstream {
    language = "en"
    description = "Multi-source movies and series (derived from SoraStream, GPLv3)"
    authors = listOf("Kemo", "Hexated", "Sora")
    status = 1
    tvTypes = listOf("AsianDrama", "TvSeries", "Anime", "Movie")
    requiresResources = false
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}
