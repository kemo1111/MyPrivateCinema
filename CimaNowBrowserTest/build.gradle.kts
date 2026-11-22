version = 1
cloudstream {
    description = "اختبار متصفح سيما ناو، تحقق يدوي وتشغيل تجريبي. ليس مصدراً مكتمل القوائم. لا يغيّر ExampleProvider."
    authors = listOf("Kemo")
    language = "ar"
    status = 2
    tvTypes = listOf("Movie")
    requiresResources = false
}
android { namespace = "com.kemo.cimanowtest"; buildToolsVersion = "35.0.0" }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
dependencies { compileOnly("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.1") }
// The vendored CloudStream Gradle plugin adds Kotlin output only. Include our Java bridge too.
tasks.withType<com.lagradost.cloudstream3.gradle.tasks.CompileDexTask>().configureEach {
    val javaTask = tasks.named<JavaCompile>("compileDebugJavaWithJavac")
    dependsOn(javaTask)
    input.from(javaTask.flatMap { it.destinationDirectory })
}
