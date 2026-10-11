// Genera el Baseline Profile y el Startup Profile de :app con un recorrido real: abrir el escáner hasta el primer
// cuadro analizado y seguir escaneando unos segundos. Se corre a mano en un celular o emulador con API 33+ (sin root):
//   ./gradlew :app:generateBaselineProfile
// El resultado queda en app/src/main/generated/baselineProfiles/ y va en git.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "mx.papeleriapos.baselineprofile"
    compileSdk { version = release(37) }
    defaultConfig {
        // BaselineProfileRule necesita API 28+ (y 33+ si el equipo no tiene root).
        minSdk = 28
        targetSdk = 36
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    targetProjectPath = ":app"
}

baselineProfile {
    // El celular o emulador conectado; no dispositivos administrados por Gradle.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
