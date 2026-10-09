plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    // Baseline Profile y Startup Profile (docs: generarlos con :baselineprofile; medir con :benchmark).
    alias(libs.plugins.androidx.baselineprofile)
}

// La versión de la app (docs/plan.md, «Versiones»): MAYOR.MENOR.PARCHE; el código de versión se deriva de ella.
val versionApp = "0.1.0"
val (mayor, menor, parche) = versionApp.split(".").map { it.toInt() }

// -PconX86: agrega x86_64 a benchmarkRelease (el release solo trae ARM; el emulador x86_64 corre ARM traducido, más lento).
val conX86 = providers.gradleProperty("conX86").isPresent

android {
    namespace = "mx.papeleriapos.android"
    compileSdk { version = release(37) }
    defaultConfig {
        applicationId = "mx.papeleriapos.android"
        minSdk = 24
        targetSdk = 36
        versionCode = mayor * 10_000 + menor * 100 + parche
        versionName = versionApp
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // El commit con que se armó, para la sesión de prueba (docs/pruebas-en-tienda.md).
        buildConfigField("String", "COMMIT", "\"${commitActual()}\"")
    }
    buildTypes {
        release {
            optimization { enable = true }
            // La fábrica busca el controlador de la cámara por nombre (fabrica/Fabrica.kt).
            proguardFiles("proguard-rules.pro")
            ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
        }
        // Los dos tipos que usa el plugin androidx.baselineprofile, declarados aquí por AGP 9: si el plugin los copia del
        // release (initWith), nonMinifiedRelease hereda optimization { enable = true } y sale ofuscado, y el perfil
        // tendría nombres que no existen en el APK final. Declarados aquí, el plugin solo los hace no depurables,
        // perfilables y firmados con la llave de depuración.
        create("nonMinifiedRelease") {
            // Sin R8: el perfil se genera con los nombres reales; R8 los reescribe al armar el release. Todas las ABI.
            optimization { enable = false }
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        create("benchmarkRelease") {
            // Lo mismo que el release (R8, perfil incluido), instalable para Macrobenchmark.
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            if (conX86) ndk { abiFilters += "x86_64" }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric necesita los recursos para las pruebas de Compose.
        unitTests.isIncludeAndroidResources = true
    }
}

baselineProfile {
    // Un solo perfil en src/main/generated/baselineProfiles (baseline-prof.txt y startup-prof.txt): lo usan release y
    // benchmarkRelease. Se genera a mano con :app:generateBaselineProfile y va en git.
    mergeIntoMain = true
    saveInSrc = true
    automaticGenerationDuringBuild = false
}

fun commitActual(): String =
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "desconocido" }

dependencies {
    constraints {
        // CameraX pide 1.1.0 y androidx.test 1.2.0: con la resolución consistente, las pruebas no compilan si no se sube.
        implementation("androidx.concurrent:concurrent-futures-ktx:1.2.0")
    }
    implementation(project(":escaner"))
    // Para la configuración de CameraX desde la Application y calentar el proveedor.
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.tooling.preview)
    // Instala el Baseline Profile al instalar desde fuera de Play (1.4.1: el mínimo para el plugin 1.5).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.uiautomator)
}
