@file:Suppress("UnstableApiUsage")


version = 653

android {
    namespace = "com.phisher98"
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "TMDB_API", "\"\"")
        buildConfigField("String", "ZSHOW_API", "\"\"")
        buildConfigField("String", "ANICHI_API", "\"\"")
        buildConfigField("String", "KissKh", "\"\"")
        buildConfigField("String", "KisskhSub", "\"\"")
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"\"")
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"\"")
        buildConfigField("String", "SUPERSTREAM_FIRST_API", "\"\"")
        buildConfigField("String", "PROXYAPI", "\"\"")
        buildConfigField("String", "KAISVA", "\"\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"\"")
        buildConfigField("String", "KAIMEG", "\"\"")
        buildConfigField("String", "KAIDEC", "\"\"")
        buildConfigField("String", "KAIENC", "\"\"")
        buildConfigField("String", "VideasyDEC", "\"\"")
        buildConfigField("String", "YFXENC", "\"\"")
        buildConfigField("String", "YFXDEC", "\"\"")
        buildConfigField("String", "NuvFeb", "\"\"")
        buildConfigField("String", "ANICHI_APP", "\"\"")
    }
}

dependencies {
    implementation("com.google.android.material:material:1.14.0")
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

     description = "#1 best extention based on MultiAPI"
     authors = listOf("Phisher98", "Hexated")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Anime",
        "Movie",
        "Cartoon",
        "AnimeMovie"
    )

    iconUrl = "https://i3.wp.com/yt3.googleusercontent.com/ytc/AIdro_nCBArSmvOc6o-k2hTYpLtQMPrKqGtAw_nC20rxm70akA=s900-c-k-c0x00ffffff-no-rj?ssl=1"

    requiresResources = true
    isCrossPlatform = false

}

dependencies {
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    implementation("com.google.android.material:material:1.14.0")
    implementation("androidx.browser:browser:1.10.0")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.mozilla:rhino:1.7.15")
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.18.1")
    testImplementation(files(System.getProperty("user.home") + "/.gradle/caches/cloudstream/cloudstream/cloudstream.jar"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("dev.whyoleg.cryptography:cryptography-core:0.4.0")
    testImplementation("io.coil-kt.coil3:coil-core:3.1.0")
    testRuntimeOnly("dev.whyoleg.cryptography:cryptography-provider-jdk:0.4.0")
}
tasks.withType<Test>().configureEach { maxHeapSize = "512m" }
