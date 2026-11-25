// Vendored from 3rabi / re-3arabi (Arabic CloudStream extensions): https://github.com/Abodabodd/re-3arabi
// Module: Youtube (published plugin version 3). Upstream author: Abodabodd. Retrieved 2026-11-25.
// Use and modification are granted by the upstream README (section "الحقوق") with mention of the original repository.
version = 6

cloudstream {
    // Vendored: attribution required by the 3rabi README grant ("with mention of the original repository").
    description = "By Abodabodd - source: github.com/Abodabodd/re-3arabi - enhanced (home rows, filters, subs order, channel pages, history/subs rows, diagnostics)"
    authors = listOf("Abodabodd")
    language = "ar"

    status = 1

    tvTypes = listOf(
        "Movie",
        "TvSeries",
        "Live",
        "Anime",
        "Music",
        "Documentary"
    )

    iconUrl = "https://raw.githubusercontent.com/Abodabodd/Oldarabrepo/refs/heads/main/img/IMG_%D9%A2%D9%A0%D9%A2%D9%A5%D9%A1%D9%A2%D9%A0%D9%A6_%D9%A1%D9%A7%D9%A2%D9%A6%D9%A1%D9%A6.jpg"
}

dependencies {
    // Module-local so ExampleProvider's build inputs (and its v37 pin) stay untouched.
    // Versions match the 3rabi root build.gradle.kts, EXCEPT NewPipeExtractor pinned to
    // v0.26.3 to match the CloudStream app's bundled runtime (upstream pins v0.25.2).
    val implementation by configurations
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.26.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.annotation:annotation:1.10.0")
}
