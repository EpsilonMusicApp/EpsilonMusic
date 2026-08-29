plugins {
    id("com.android.library")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.music.echo.core"
    compileSdk = 36
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}
kotlin { jvmToolchain(21) }
dependencies {
    api(libs.datastore)
    api(libs.compose.runtime)
    api(libs.compose.ui)
    api(libs.compose.animation)
    api(libs.room.runtime)
    api(libs.room.ktx)
    api(libs.media3)
    api(libs.media3.session)
    api(project(":innertube"))
    api(libs.ktor.serialization.json)
    api(libs.protobuf.javalite)
}
