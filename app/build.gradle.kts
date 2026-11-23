plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

android {
    // namespace는 코드 패키지라 이름이 정해져도 바꾸지 않는다. applicationId만 바꾼다(Play 첫 업로드 때 영구 고정, D61).
    namespace = "app.sn62kr.grow"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        applicationId = "app.sn62kr.growtimer" // 임시. 공개 이름이 정해지면 app.sn62kr.<이름>
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        // 친구 서버 주소. 디버그는 이 맥의 wrangler dev(폰은 `adb reverse tcp:8787 tcp:8787`). 릴리스는 배포한 뒤 넣는다 — 비어 있으면 친구 탭이 "준비 중".
        debug {
            applicationIdSuffix = ".debug"
            buildConfigField("String", "API_BASE", "\"http://127.0.0.1:8787\"")
        }
        release {
            buildConfigField("String", "API_BASE", "\"\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true // Robolectric이 문구·테마를 읽는다
    }
}

room { schemaDirectory("$projectDir/schemas") }
android.sourceSets.getByName("debug").assets.srcDir("$projectDir/schemas") // 이전 시험(MigrationTestHelper, Robolectric)이 읽는다. 릴리스에는 안 들어간다

// 화면 사진은 app/screenshots/에. 기준 사진을 다시 찍을 때: ./gradlew :app:recordRoborazziDebug
roborazzi { outputDir.set(file("screenshots")) }

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.navigation.compose)

    implementation(libs.glance.appwidget)
    implementation(libs.work.runtime)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.core)

    // 시험은 JVM에서 돈다(Robolectric). 폰 없이 DB·타이머·화면 흐름·화면 사진까지 본다(D62)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.rule)
}
