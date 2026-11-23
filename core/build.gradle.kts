// 순수 Kotlin. Android에 기대지 않는다(design.md 4절).
plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
    // 그림 갤러리(GalleryTest): -Pgallery=docs/checks/m3 을 주면 PNG를 거기에 쓴다
    (project.findProperty("gallery") as String?)?.let { systemProperty("gallery.out", rootProject.file(it).absolutePath) }
    (project.findProperty("preview") as String?)?.let { systemProperty("preview.out", rootProject.file(it).absolutePath) }
}
