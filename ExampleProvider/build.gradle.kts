dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    testImplementation("junit:junit:4.13.2")
}

// Use an integer for version numbers - bump this by 1 on EVERY update
version = 29

cloudstream {
    description = "إضافات كيمو: TopCinema و MyCima و ArabSeed و Cima4U و FaselHD و EgyDead و Shahid4U و Akwam"
    authors = listOf("Kemo")

    /**
    * Status int as one of the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta-only
    **/
    status = 1 // Will be 3 if unspecified

    tvTypes = listOf("Movie", "TvSeries", "Anime")

    requiresResources = true
    language = "ar"

    iconUrl = "https://upload.wikimedia.org/wikipedia/commons/2/2f/Korduene_Logo.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
