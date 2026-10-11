# Papelería POS para Android

La app Android de [Papelería POS](https://github.com/Marioalr/papeleria-pos): los celulares de la tienda escanean con
la cámara lo que hoy pasa por el lector de la caja.

> **Estado: fase 1, el escáner (en construcción).** Leer EAN-13, UPC-A, UPC-E, EAN-8, ITF-14, Code 128 y QR con la
> cámara, guiar al personal para que el código se lea (muévete, acércate, aléjate, mantén firme, luz, reflejo) y
> confirmar cada lectura con bip, vibración y verde, con el máximo rendimiento medido. Todavía sin conexión al POS. Las
> decisiones están en el [plan](docs/plan.md).

## Desarrollo

Requiere el SDK de Android (API 37) y JDK 17 o más nuevo; el de Android Studio sirve:

```
export JAVA_HOME=/opt/android-studio/jbr
./gradlew test
```

## Módulos

| Módulo | Qué tiene |
|---|---|
| `nucleo` | Kotlin puro, probado en la JVM: verificación de GTIN, codificadores, escenas sintéticas, geometría, localizador, guía, validación y consenso |
| `escaner` | Android: la cámara (CameraX) y los motores (ML Kit y zxing-cpp) |

## Documentos

| Documento | Contenido |
|---|---|
| [Plan](docs/plan.md) | Decisiones, forma de trabajar (pruebas en la tienda solo sábado y domingo), versiones y entregas |
| [El escáner](docs/escaner.md) | Cómo lee, cómo guía y cómo confirma: cámara, motores, localizador, umbrales y estados |
| [Investigación](docs/investigacion-escaner.md) | Motores, APIs, UX y pruebas, con fuentes verificadas el 2026-10-09 |
| [Pruebas](docs/pruebas.md) | Pruebas automáticas, emulador, banco de pruebas y pruebas con cualquier código |
| [Pruebas en tienda](docs/pruebas-en-tienda.md) | La guía del fin de semana: qué se prepara, qué se mide y cómo se exporta |
