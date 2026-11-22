plugins { id("com.android.application") }

android {
    namespace = "app.sn62kr.spike.backup"
    compileSdk { version = release(37) { minorApiLevel = 2 } }
    defaultConfig {
        applicationId = "app.sn62kr.spike.backup"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "s4"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
