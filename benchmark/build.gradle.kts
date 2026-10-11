// Macrobenchmark de la apertura de :app (arranque frío, tibio y caliente, con y sin Baseline Profile). Se corre a mano
// en celulares físicos (gama baja y media):
//   ./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
// En el emulador solo como prueba de humo (las cifras no sirven):
//   -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
//   -Pandroid.testInstrumentationRunnerArguments.iteraciones=2
plugins {
    alias(libs.plugins.android.test)
    // Solo para tener las variantes benchmarkRelease/nonMinifiedRelease iguales a las de :app.
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "mx.papeleriapos.benchmark"
    compileSdk { version = release(37) }
    defaultConfig {
        // Las secciones de trace propias (TraceSectionMetric) necesitan API 29+; Macrobenchmark, 23+.
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
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.test.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}
