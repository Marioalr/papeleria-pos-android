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
