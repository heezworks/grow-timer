// 순수 Kotlin. Android에 기대지 않는다(design.md 4절).
plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(17) }

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
