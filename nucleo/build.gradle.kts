import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}

// Los carteles para la cámara del emulador (docs/pruebas.md).
tasks.register<JavaExec>("carteles") {
    group = "verification"
    description = "Genera los PNG de prueba para la cámara del emulador en build/carteles."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("mx.papeleriapos.escaner.nucleo.Carteles")
    args(layout.buildDirectory.dir("carteles").get().asFile.absolutePath)
}

// La hoja de etiquetas para imprimir y medir desde qué tamaño lee el celular (docs/pruebas.md).
tasks.register<JavaExec>("hojaEtiquetas") {
    group = "verification"
    description = "Genera la hoja de etiquetas de prueba (PDF, SVG y CSV) en build/hoja-etiquetas."
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("mx.papeleriapos.escaner.nucleo.HojaEtiquetas")
    args(layout.buildDirectory.dir("hoja-etiquetas").get().asFile.absolutePath)
}
