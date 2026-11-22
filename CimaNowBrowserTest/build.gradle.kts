version = 1
cloudstream {
    description = "اختبار متصفح سيما ناو، تحقق يدوي وتشغيل تجريبي. ليس مصدراً مكتمل القوائم. لا يغيّر ExampleProvider."
    authors = listOf("Kemo")
    language = "ar"
    status = 2
    tvTypes = listOf("Movie")
    requiresResources = false
}
android { namespace = "com.kemo.cimanowtest" }
dependencies { testImplementation("junit:junit:4.13.2") }
tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8" }
