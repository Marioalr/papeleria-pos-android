# Papelería POS para Android

La app Android de [Papelería POS](https://github.com/Marioalr/papeleria-pos): con la cámara del celular se escanea lo
que hoy pasa por el lector de la caja.

> **Alcance: lo máximo en un Pixel 8 Pro.** El escáner lee EAN-13, UPC-A, UPC-E, EAN-8, ITF-14, Code 128 y QR, guía
> para que el código se lea (muévete, acércate, aléjate, mantén firme, luz, reflejo) y confirma cada lectura con bip,
> vibración y verde. Aquí se exprime y se mide en un solo celular, el equipo de referencia; con eso se decide cómo se
> une al POS cuando el POS llegue a su fase de Android. Todavía no se conecta al POS. El [plan](docs/plan.md) tiene las
> decisiones y la línea base.

## Desarrollo

Requiere el SDK de Android (API 37) y JDK 17 o más nuevo; el de Android Studio sirve:

```
export JAVA_HOME=/opt/android-studio/jbr
./gradlew test assembleDebug
```

El APK de prueba queda en `app/build/outputs/apk/debug/app-debug.apk`. Instalarlo en un celular conectado por USB (con
la depuración USB activada):

```
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Para medir rendimiento sirve el de release (el de depuración de Compose es más lento):

```
./gradlew :app:assembleRelease
```

Sale firmado si en la PC está la llave del proyecto (`~/.config/papeleria-pos-android/firma.properties`, fuera del
repo); si no, sin firmar. Cada PR y cada versión los arma GitHub Actions (flujo «Android») con la llave guardada como
secretos: los APK quedan como artefacto de la corrida y, con una etiqueta `vX.Y.Z`, el firmado se adjunta a la versión.

Cómo probar sin celular (emulador con un código como cámara, pruebas instrumentadas, banco de pruebas) y con cualquier
código de barras: [pruebas](docs/pruebas.md).

## Módulos

| Módulo | Qué tiene |
|---|---|
| `nucleo` | Kotlin puro, probado en la JVM: verificación de GTIN, codificadores, escenas sintéticas, geometría, localizador, guía, validación y consenso |
| `escaner` | Android: la cámara (CameraX), los motores (ML Kit y zxing-cpp) y la pantalla del escáner con su guía, bip y vibración |
| `app` | La app de prueba: modo venta y modo conteo, escaneo rápido sobre el bloqueo (mosaico y accesos directos), ajustes, sesión de prueba con CSV, banco de pruebas y pruebas de estrés |
| `baselineprofile` | Genera el perfil de arranque (Baseline Profile y Startup Profile) que precompila la apertura |
| `benchmark` | Macrobenchmark de la apertura: frío, tibio y caliente hasta el primer cuadro analizado |

## Documentos

| Documento | Contenido |
|---|---|
| [Plan](docs/plan.md) | Alcance (el Pixel 8 Pro), decisiones, forma de trabajar (en la tienda solo sábado y domingo), versiones, entregas y la línea base con sus experimentos |
| [El escáner](docs/escaner.md) | Cómo lee, cómo guía y cómo confirma: cámara, motores, localizador, umbrales y estados |
| [Investigación](docs/investigacion-escaner.md) | Motores, APIs, UX y pruebas, con fuentes verificadas el 2026-10-09 |
| [Pruebas](docs/pruebas.md) | Pruebas automáticas, emulador, banco de pruebas y pruebas con cualquier código |
| [Pruebas en tienda](docs/pruebas-en-tienda.md) | La guía del fin de semana: qué se prepara, qué se mide y cómo se exporta |
| [Cambios](CAMBIOS.md) | Qué trae cada versión |
