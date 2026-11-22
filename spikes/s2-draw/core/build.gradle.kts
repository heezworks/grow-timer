plugins { id("org.jetbrains.kotlin.jvm") }

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // 갤러리 PNG를 쓸 곳
    systemProperty("gallery.out", rootProject.file("../../docs/checks/s2-draw").absolutePath)
    testLogging { events("passed", "failed"); showStandardStreams = true }
}
