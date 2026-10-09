plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// La versión de la app (docs/plan.md, «Versiones»): MAYOR.MENOR.PARCHE; el código de versión se deriva de ella.
val versionApp = "0.1.0"
val (mayor, menor, parche) = versionApp.split(".").map { it.toInt() }

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

fun commitActual(): String =
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim().ifEmpty { "desconocido" }

dependencies {
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
