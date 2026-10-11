# Investigación del escáner

> Hecha el 2026-10-09 con seis investigaciones en paralelo (ML Kit, zxing-cpp, CameraX, UX de escáneres, pruebas y
> el localizador para la guía), cada una revisada por un verificador que intentó refutar sus afirmaciones con fuentes
> primarias. Donde una afirmación salió corregida o refutada, aquí va la versión corregida. Es la base de las decisiones
> de [el diseño](escaner.md); los números marcados [E] o [P] se recalibran en el Pixel 8 Pro ([plan](plan.md)).

Etiquetas:
- **[V]**: verificado en fuente primaria o con prueba propia.
- **[E]**: medido solo en emulador x86_64 o en PC. No representa un celular de verdad.
- **[P]**: propuesta de ingeniería sin fuente. Hay que calibrarla.
- **[NV]**: no verificado.

Verificado además:
- La regla de las pruebas en la tienda viene del POS (`docs/notas.md`, «Forma de trabajar»): las pruebas reales en la
  matriz solo sábado y domingo. La decidió el dueño el 2026-10-08.
- El Compose BOM 2026.02.01 fija `androidx.compose.ui:ui` en **1.10.4** (POM de dl.google.com).
- `HapticFeedbackType.Confirm` y `Reject` existen en ui-android 1.10.4 (javap sobre el AAR).

---

## 1) Motor(es) de detección recomendados

### 1.1 Decisión

| Pieza | Decisión | Por qué |
|---|---|---|
| Decodificador por defecto (v0.1) | **ML Kit bundled `com.google.mlkit:barcode-scanning:17.3.0`** | Ya corrió en el emulador API 37 con 7 pruebas instrumentadas [V/E]. Lee todos los formatos del POS. Funciona sin red desde que se instala. `enableAllPotentialBarcodes()` entrega la caja de los códigos que todavía no puede leer, y eso es la base de "ya lo estoy viendo". Rechaza EAN/UPC con dígito verificador incorrecto [V]. |
| Candidato alterno para comparar (A/B) | **zxing-cpp `io.github.zxing-cpp:android:3.1.1`** (2026-07-29, Apache-2.0) detrás de la misma interfaz, en un flavor `bench` o en debug | Su `.so` arm64 pesa 1,746,848 B, contra 4,946,720 B de ML Kit [V]. Respeta `cropRect` y la rotación, tiene `minLineCount=2` y no depende de GMS. Pero **no se ha ejecutado ni en emulador ni en dispositivo**, y `returnErrors` no sirve para guiar con códigos 1D [V]. |
| Localizador propio (para guiar, no para decodificar) | Kotlin puro sobre el plano Y, independiente del motor | Mide el ancho del módulo en px, la nitidez, el reflejo y la luz. Ningún motor da esos datos. Cuesta ~0.25 ms/cuadro en ART x86_64 [E]. Ver §3. |
| Regla de ejecución | **Un solo motor por cuadro** | Correr dos motores duplica CPU y batería. |

Descartados:
- **Google Code Scanner 16.1.0**: la interfaz es cerrada y `startScan()` devuelve un solo `Task<Barcode>`. No sirve para escanear de forma continua.
- **ZXing Java 3.5.4**: está "Maintenance Mode Only".
- **ML Kit unbundled 18.3.1**: hasta que baja el modelo puede no dar resultados o fallar con `MlKitException.UNAVAILABLE` (código 14).
- **SDK comerciales** (Scandit, Dynamsoft): solo si la tienda demuestra que los motores abiertos fallan. No verifiqué precios.

El motor por defecto definitivo sale del A/B de fin de semana (§5.2). La regla de decisión: primero cero lecturas incorrectas aceptadas, luego mayor tasa de lectura al primer intento, luego menor p90. Si hay empate, gana el APK más chico (zxing-cpp).

### 1.2 Versiones fijadas (catálogo `libs.versions.toml`)

| Artefacto | Versión | Nota |
|---|---|---|
| `com.google.mlkit:barcode-scanning` | 17.3.0 (2024-08-07) | Sin versiones nuevas a oct-2026 (maven-metadata, release notes del 2026-10-07) [V]. Solo está en Google Maven. |
| `io.github.zxing-cpp:android` | 3.1.1 | Sube `kotlin-stdlib` de 2.2.10 a 2.3.20 (metadata 2.3.0). Que Kotlin lea metadata de la versión siguiente es "best-effort", no una garantía. Con AGP 9 se puede declarar KGP ≥2.3.20 en el classpath de nivel superior [V]. La 3.2.0 (−43% de `.so`, PR #1151) aún no está publicada. |
| `androidx.camera:camera-core/-camera2/-lifecycle/-compose` | **1.6.2** (2026-08-26) | La página de releases trae un snippet con `1.7.0-alpha03`: **no copiarlo**. `camera-view` y `camera-mlkit-vision` 1.6.2 solo si se usa el camino alterno. |
| Compose BOM | 2026.02.01, que da ui 1.10.4 | [V hoy] |
| `androidx.benchmark` | 1.5.0 (2026-09-09) | |
| AGP / Gradle / Kotlin | 9.4.1 / 9.6.0 / 2.2.10 (integrado) | Usar `JAVA_HOME=/opt/android-studio/jbr`. `/usr/lib/jvm/java-17-openjdk` es solo JRE (no trae javac) y lint falla con él [V]. |
| minSdk / compileSdk | 24 / 37 | Google Play services se actualiza desde API 24. CameraX 1.5+ y ML Kit piden 23 [V]. |

**ABIs:** en release, `ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }`; x86_64 solo en debug. Las `.so` van sin comprimir ("Stored") porque minSdk ≥23. Un APK universal agrega más de 20 MB solo de ML Kit [V].

### 1.3 Configuración de ML Kit

```kotlin
val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder()
    .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
        Barcode.FORMAT_UPC_E, Barcode.FORMAT_ITF, Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE)
    .enableAllPotentialBarcodes()      // potenciales: format=-1, rawValue=="" (no null) -> isNullOrEmpty()
    .build())                          // getClient() crea una instancia nueva cada vez: UNA por pantalla
```

- **Nunca usar `FORMAT_ALL_FORMATS`.** Así se evitan Code 39/93/Codabar, que tienen checksum débil.
- EAN_13 y UPC_A no se pueden separar: pedir uno devuelve también el otro (issues #510 y #645) [V].
- Restringir formatos casi no acelera en el emulador: 61.1 ms contra 66.3 ms p50 en un frame vacío [E]. La ventaja real es reducir lecturas falsas.
- **Calentamiento:** procesar un frame NV21 vacío al entrar a la pantalla. En el emulador, el primer `process()` tardó 95.8 ms, el segundo 77.1 y el régimen estable fue de 61 a 69 ms [E]. Un cliente nuevo después de `close()` vuelve a tardar ~86 ms.
- **Cierre:** `close()` en `onCleared`. Ignorar el `MlKitException "This detector is already closed!"` que llegue durante el cierre. Un segundo `close()` no lanza nada [V].
- **`ZoomSuggestionOptions`:** **apagado en v0.1**. En las pruebas se disparó una sola vez a ~1.52–1.55 s y siempre pidió el máximo configurado (6, 8 o 10x) [E]. Si se activa para el A/B: `setMaxSupportedZoomRatio(min(zoomState.maxZoomRatio, 2.5f))`, acotar el valor dentro del callback (corre en el hilo principal) y devolver `true` solo si se aplicó. Sin ese tope, el zoom "may be unbounded" [V].

### 1.4 Validación, consenso y normalización (antes de declarar "leído")

| Formato | Validación en la app | Consenso por defecto | Normalización / claves de búsqueda |
|---|---|---|---|
| EAN-13 (750…, internos 2…) | GS1 mod-10 (ML Kit ya lo valida; repetirlo cuesta nada) | 2 iguales en los últimos 3 cuadros (A/B contra N=1) | 13 dígitos |
| UPC-A | GS1 | igual que EAN-13 | ML Kit entrega 12 dígitos: un EAN-13 que empieza con 0 llega como UPC_A, aunque solo se pida EAN_13 (#797) [V]. zxing-cpp entrega 13. Buscar `v` y `"0"+v`. |
| UPC-E | longitud 8 + GS1 sobre la expansión | 2 de 3 | ML Kit da 8 dígitos sin expandir ("09999961"). zxing-cpp da 13 expandidos ("0099999000061") [V]. Expandir a UPC-A y luego a GTIN-13; buscar 8, 12 y 13 dígitos. |
| EAN-8 | GS1 | 2 de 3 | 8 dígitos |
| ITF | **exactamente 14 dígitos + GS1 mod-10** | **2 iguales obligatorias** | ML Kit no valida ITF. Reproduje una lectura falsa de 40 dígitos (el principio del ITF de prueba y luego ceros) [V/E]. El filtro ITF_14 de zxing-cpp no limita la longitud [V]. |
| Code 128 (claves de eleventa) | ninguna disponible (no hay GS1) | **2 iguales obligatorias** (3 si aparecen lecturas erróneas) | Quitar `]C1` si aparece (#176; no lo volví a probar en 17.3.0) [NV]. El issue #17, abierto, incluye Code 128 mal leídos [V]. |
| QR (tickets) | Reed-Solomon interno | 1 | En los logs guardar solo hash + últimos 4 caracteres. |

El formato exacto en que eleventa guarda UPC-A y UPC-E (12 o 13 dígitos, expandido o no) **está pendiente de revisar en una exportación de solo lectura** [NV].

---

## 2) Configuración de CameraX recomendada (1.6.2)

**Arquitectura:**
- `ProcessCameraProvider.awaitInstance()` + `SessionConfig(preview, analysis)`, enlazado con un solo `bindToLifecycle`.
- `CameraXViewfinder(surfaceRequest, implementationMode = ImplementationMode.EXTERNAL, coordinateTransformer = MutableCoordinateTransformer())`. EXTERNAL usa SurfaceView, que según la doc tiene "less performance and power overhead".
- **No usar `LifecycleCameraController`.** En 1.6.2 su `setSessionConfig` es `@RestrictTo`, así que no permite fijar FPS, y además enlaza `IMAGE_CAPTURE|IMAGE_ANALYSIS` por defecto [V].
- Camino alterno, solo si se prefiere simplicidad: `LifecycleCameraController` + `PreviewView` + `MlKitAnalyzer(COORDINATE_SYSTEM_VIEW_REFERENCED)`. Procesa el frame completo de 1280x720, sin recorte. VIEW_REFERENCED solo funciona con camera-view.

| Parámetro | Valor | Detalle |
|---|---|---|
| Resolución de análisis | `ResolutionSelector` con `AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY` + `ResolutionStrategy(Size(1280, 720), FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER)` | Sin esto ImageAnalysis usa 640x480 [V]. `setTargetResolution` está deprecada y mezclarla con el selector lanza IAE. El mismo selector va en Preview. En el emulador, 1920x1080 costó 87.7 ms contra ~68 ms [E]. Para Code 128 largos es mejor apoyarse en el zoom. Registrar en runtime el tamaño real del buffer. |
| Formato | `OUTPUT_IMAGE_FORMAT_YUV_420_888` (default) | NV21 y RGBA agregan conversión [V]. El plano Y tiene `pixelStride=1`; `rowStride` puede ser mayor que el ancho. |
| Contrapresión | `STRATEGY_KEEP_ONLY_LATEST` | Cerrar `ImageProxy` en `addOnCompleteListener` y nunca llamar `Image.close()`. Para saltar frames (pausa, cooldown): `close()` y `return`, **no** `clearAnalyzer()` [V]. |
| Rotación | **No** usar `setOutputImageRotationEnabled(true)` (~10–15 ms/frame a 640x480) | Pasar `imageInfo.rotationDegrees` a ML Kit o a zxing-cpp. |
| Hilo | `Executors.newSingleThreadExecutor { Thread(it, "scan-analysis") }` y `setAnalyzer` **antes** del bind | |
| ROI (recorte) | Franja centrada en orientación de pantalla: 100% del ancho **visible** × 40% del alto | ML Kit ignora `cropRect` (#491) [V]. Copiar el plano Y de la franja a NV21 (croma=128, llenado una sola vez) y llamar `InputImage.fromByteArray(buf, w, h, rot, IMAGE_FORMAT_NV21)`. En frame vacío bajó de 61.0 a 32.4 ms p50; con código, de 65.5 a 54.1 ms [E]. En zxing-cpp: `image.setCropRect(...)`. El **localizador corre sobre el frame completo**, porque tiene que poder guiar hacia la franja. Las cajas de ML Kit salen relativas al recorte y ya rotadas. |
| Zona visible | En una pantalla 20:9 con FILL_CENTER, de un upright de 720x1280 solo se ve ~576 px de ancho (~80%) [cálculo propio] | Calcular ventana y umbrales sobre la región **visible** (ViewPort/transformación), no sobre el buffer. Falta confirmar con un log que `ImageProxy.cropRect` refleje el `ViewPort` de `SessionConfig.Builder.setViewPort(...)` [NV]. |
| FPS | `SessionConfig.Builder.setFrameRateRange(r)`, con `r` **sacado de** `cameraInfo.getSupportedFrameRateRanges(sessionConfig)` (si no, IAE al hacer bind) [V] | Por defecto [P]: el rango con `upper == 30` y el `lower` más alto disponible (ideal [30,30]), para limitar la exposición y el desenfoque por movimiento. Variante B para el A/B: [15,30], que da más luz y más blur. Nunca más de 30. El ahorro de batería al bajar FPS **no está cuantificado** [NV]. |
| Enfoque | AF por defecto `CONTROL_AF_MODE_CONTINUOUS_PICTURE` [V] | Al hacer bind, al tocar, o con un candidato sin leer durante ≥700 ms (máximo 1 cada 2 s): `startFocusAndMetering(FocusMeteringAction.Builder(pt, FLAG_AF or FLAG_AE).setAutoCancelDuration(2, TimeUnit.SECONDS).build())` sobre el centro del candidato. `FLAG_AF` bloquea el foco hasta que se cancele, por eso **nunca** usar `disableAutoCancel()`. `setLockingMode(0)` solo existe en 1.7.0-alpha. |
| Zoom inicial | `z0 = suggestedZoom(...)` con `LENS_INFO_MINIMUM_FOCUS_DISTANCE` (dioptrías), `SENSOR_INFO_PHYSICAL_SIZE` y `LENS_INFO_AVAILABLE_FOCAL_LENGTHS` | Si la calibración es `UNCALIBRATED`, null o 0, usar **1.5x**. Tope **2.0x** [P], para no cambiar al teleobjetivo con `CONTROL_ZOOM_RATIO`. Registrar `LOGICAL_MULTI_CAMERA_ACTIVE_PHYSICAL_ID` (API 29+). Aplicar con `setZoomRatio(z.coerceIn(zs.minZoomRatio, zs.maxZoomRatio))`, porque fuera de rango lanza IAE. |
| Zoom progresivo propio | Si el módulo mide < 2 px y el zoom no está en el tope: `z = min(z * 1.4f, tope)` antes de pedir "acércate" [P] | Volver a `z0` después de una lectura o tras 2 s sin candidatos. Mostrar una etiqueta "1.5×/2×". Supongo que el ISP recorta antes de escalar, lo que daría más px/módulo sin costo de CPU [NV]. |
| Linterna | `cameraControl.enableTorch(true)` solo si `cameraInfo.hasFlashUnit()` | Botón pulsante si la luminancia media Y del 50% central es < 45 durante ≥1 s. Encendido automático como ajuste opcional. **No** apagarla por luminancia (la propia linterna la sube). Apagarla al salir, al ir a segundo plano o por inactividad. `setTorchStrengthLevel` necesita API 35+ y `isTorchStrengthSupported`. Low Light Boost no aplica en gama baja (en dic-2025 solo existía en Pixel 10) y puede bloquear la linterna [V]. |
| Arranque | `Application : CameraXConfig.Provider` con `Camera2Config.defaultConfig()`, `setAvailableCamerasLimiter(CameraSelector.DEFAULT_BACK_CAMERA)` y `setMinimumLoggingLevel(Log.ERROR)`. Llamar `ProcessCameraProvider.getInstance(this)` en `onCreate` | Zoom y linterna se cambian sin volver a enlazar. |
| Inactividad | 20 s sin candidatos: bandera `paused` y mensaje "Toca para seguir escaneando". 60 s: `provider.unbind(sessionConfig)` [P] | En modo caja se puede configurar para que la cámara siga abierta. SparkScan usa 10 s. |
| Señales del AF | `Camera2Interop.Extender(builder).setSessionCaptureCallback(...)`: `CONTROL_AF_STATE`, `LENS_FOCUS_DISTANCE`, `LENS_STATE` | `@ExperimentalCamera2Interop`, deprecado en 1.7.0-alpha03. `LENS_FOCUS_DISTANCE` solo está garantizado en `HARDWARE_LEVEL_FULL`; en LIMITED puede venir null [V]. |

**Transformación de coordenadas** (análisis upright a UI, en Compose) [V]:
1. Upright a sensor: inversa de `imageInfo.sensorToBufferTransformMatrix` más la rotación. Es lo mismo que hace MlKitAnalyzer con `COORDINATE_SYSTEM_SENSOR`.
2. Sensor a buffer del Preview: `SurfaceRequest.TransformationInfo.sensorToBufferTransform`. Se obtiene con `setTransformationInfoListener`.
3. Buffer a UI: **inversa** de `coordinateTransformer.transformMatrix`, porque esa matriz va de UI a buffer.

Para el localizador, que trabaja en coordenadas del buffer sin rotar:
- 90°: `(h−y, x)`
- 180°: `(w−x, h−y)`
- 270°: `(y, w−x)`

Usar `androidx.annotation.OptIn(ExperimentalGetImage::class)`, no `kotlin.OptIn`, que no tiene efecto [V].

**Riesgo de CameraPipe:** en 1.6.x no hay forma de volver a la implementación anterior. Si algún equipo falla, la alternativa es fijar **1.5.3** (ahí `SessionConfig` es `@ExperimentalSessionConfig`). Nunca bajar de 1.5.2 por el crash de Android 17 [V].

---

## 3) Guía de movimiento

### 3.1 Lo que da ML Kit

- **Potenciales** (`enableAllPotentialBarcodes`, bundled ≥17.1.0) [V/E]:
  - Vienen con `format = -1`, `valueType = 0`, `rawValue == ""`, `rawBytes` de longitud 0, `boundingBox` y 4 `cornerPoints`. El javadoc dice null, pero en la práctica es cadena vacía: usar `rawValue.isNullOrEmpty()`.
  - Aparecieron con 0.6–1.2 px/módulo, con blur ×4 y ×8, con el código tapado un 30% y desplazado (cx=0.15).
  - No aparecieron a 0.45 px/módulo. Tampoco en un render a 1.5–2.0 px/módulo, así que **el comportamiento no es monotónico**.
  - No suben la latencia en frame vacío: 61.4 contra 61.1 ms.
  - Solo cubren lo que está dentro de la ROI.
- **Sugerencia de zoom:** es tosca. Se dispara una vez (~1.5 s), siempre pide el máximo y no indica dirección. No sirve como guía fina.
- **Lo que ML Kit no da:** px/módulo, nitidez, movimiento, reflejo ni luz. Eso lo calcula el localizador.

### 3.2 Localizador propio sobre el plano Y

El prototipo de la investigación quedó en el módulo `nucleo` (`Localizador.kt` y `Guia.kt`), con pruebas en la JVM. Pasos:

1. **Muestreo:** paso `s = max(1, ladoLargo/320)` (4 px a 1280x720), con fase estratificada. Diferencias a `d = max(1, s/2)` px en resolución completa. Celdas de 8×8 muestras (32×32 px). Un pixel cuenta como borde si `|gx|+|gy| ≥ 20`.
2. **Tensor de estructura por celda:** Sxx, Syy, Sxy. La coherencia es `c² = ((Sxx−Syy)² + 4Sxy²)/(Sxx+Syy)²`; una celda es 1D si `c ≥ 0.70`. Es la misma idea que el `BarcodeDetector` de OpenCV, que usa 0.9 sobre Scharr.
3. **Región:** BFS con 8 vecinos y tolerancia `|cos 2Δθ| ≥ cos 40°`. El ángulo es `θ = ½·atan2(2ΣSxy, ΣSxx−ΣSyy)`.
4. **3 líneas de escaneo** a lo largo de θ (desplazadas ±0.08·largo). Dan:
   - módulo = percentil 25 de las corridas;
   - nitidez = salto máximo medio en cada transición / rango;
   - número de transiciones, continuidad (NCC) y proporción oscuro/claro.
   - `verified = transiciones ≥ 20 && continuidad ≥ 0.55 && oscuro/claro ∈ [0.35, 2.8]`.
   - Un hueco mayor que `max(12, 10·módulo)` marca la zona de silencio y da `largo`, `centro` y `cutOff`.
5. **Métricas globales:** `meanY` (50% central), `satFrac` (fracción con Y ≥ 250), `frameDiff` (diferencia media por celda contra el cuadro anterior, ~880 celdas a 720p).

**Costo:**
- 0.19–0.20 ms en HotSpot (i9) y 0.25 ms en ART del emulador x86_64 a 1280x720. Cero asignaciones por cuadro y ~0.3 MB preasignados [E].
- En ARM, estimo 1–2.5 ms en Cortex-A55 y 2–4 ms en A53 [NV].
- Meta: p95 < 3 ms en el celular más barato. Si no se cumple, en este orden: `targetSamplesLongSide = 256`, correr cada 2 cuadros, o limitarlo a la ventana más un margen.
- `ByteBuffer.get(int, byte[], int, int)` requiere API 35. Con minSdk 24 usar `buffer.duplicate()` o restaurar `position()`.

**Precisión (sintética, con las cifras corregidas)** [E]:
- Con ≥2 px/módulo (2, 3, 5 y 8 px; 0–90°; blur r=0 y r=2), 10/10 encontrados y verificados por tamaño.
- Error mediano del centro: 3.2–13.5 px, hasta 41 px con blur.
- Relación largo estimado/real: 0.56–1.22 con blur (mediana 1.00).
- Con 1.5 px/módulo: 8/10 aciertos reales y 6/10 verificados.
- Falsos positivos ya verificados: 1/50 por cuadro en escenas con trazos, 1/30 en texto y 0/30 en hoja rayada. Sin el paso de verificación eran 30/30. Por eso **se exige que el candidato persista ≥2–3 cuadros** antes de guiar.
- Nitidez: imagen nítida 0.42–0.87; r=1 0.31; r=2 ~0.145 (0.08–0.19); r≥3 ≤0.06.
- Un movimiento de 2/4/8 px da 0.14/0.054/0.042, **igual que un desenfoque óptico**. Para separarlos hacen falta señales en el tiempo: velocidad del centro, `frameDiff` y estado del AF.
- La clase 2D del localizador se dispara con texto. **Para QR solo se usa ML Kit.**

### 3.3 Fusión de fuentes

Orden de prioridad para saber dónde está el código:
1. código decodificado;
2. potencial de ML Kit (`rawValue` vacío);
3. localizador con `verified` en ≥2 cuadros;
4. si no hay ninguno, **no se dan pistas de dirección**, solo "Apunta al código".

Todo se calcula en coordenadas upright, normalizadas contra la **ventana guía visible**:
- `lenRel = largo / anchoVentana`
- `dx, dy = (centroCódigo − centroVentana) / dimensiónVentana`
- `focusRatio = LENS_FOCUS_DISTANCE / LENS_INFO_MINIMUM_FOCUS_DISTANCE`, solo si ambos existen y son > 0.

### 3.4 Umbrales iniciales con histéresis [P]

Todos se calibran con datos reales. El orden de la tabla es la prioridad.

| Pista | Entra si… | Sale si… | Texto |
|---|---|---|---|
| REFLEJO | `satFrac > 0.06` | `< 0.03` | "Hay reflejo: inclina un poco el celular" |
| MÁS LUZ | `meanY < 45` durante ≥500 ms | `> 60` | "Poca luz: enciende la linterna" (botón pulsante) |
| ALÉJATE | `lenRel > 0.90`, o `cutOff`/toca **ambos** bordes, o borroso con (`focusRatio ≥ 0.95` o `lenRel > 0.75` o módulo > 7 px) | `lenRel < 0.80` | "Aléjate un poco" |
| MUEVE ←/→/↑/↓ | `|dx|` o `|dy|` > 0.20, o la caja toca **un solo** borde (≤4 px) | `< 0.12` | "Mueve el celular a la izquierda" (si el código se ve a la izquierda, se mueve a la izquierda) |
| ACÉRCATE | módulo < 2.0 px (si nitidez ≥0.25); si no, `lenRel < 0.30`. **Solo cuando el zoom propio ya llegó al tope.** | módulo > 2.5 px / `lenRel > 0.36` | "Acércate" |
| MANTÉN FIRME | nitidez < 0.15, o hay movimiento, o el tamaño está bien y no lee en ≥400 ms (en ese caso, disparar AF) | nitidez > 0.22 | "Mantén firme…" (si el AF está buscando: "Enfocando…") |
| ESCANEANDO | por defecto | | "Apunta al código de barras" |

**Tamaño mínimo por simbología** a 2 px/módulo [V para EAN-13; cálculo propio para el resto, NV contra la norma GS1/ISO]:

| Simbología | Módulos | Ancho mínimo a 2 px/módulo |
|---|---|---|
| EAN-13 | 95 | 190 px (26% de 720, ~33% de 576 visibles) |
| EAN-8 | 67 | 134 px |
| UPC-E | 51 | 102 px, pero en las pruebas necesitó ≥3–4 px/módulo, o sea 153–204 px |
| Code 128 de 8 caracteres | 11N+35 = 123 | ~246 px, ~286 px con zonas de silencio |
| ITF-14 | 29R+48 | 241–270 px |

Zona objetivo: **2.5–6 px/módulo**.

**Antiparpadeo:**
- EMA con α = 0.4 sobre lenRel, dx, dy, nitidez y módulo.
- Una condición tiene que durar **3 cuadros y ≥300 ms** antes de mostrarse.
- Un mensaje se queda **≥700 ms** en pantalla. La simulación sin parpadeo usó 450 ms; UX propone 900. Una pista de mayor prioridad sí puede reemplazarlo.
- "Leído" es inmediato y borra cualquier pista.
- No dar pistas de dirección en los primeros 500 ms de un candidato nuevo, para darle tiempo al AF y al zoom.

---

## 4) Feedback: escaneando, guía y leído

### 4.1 Estados

`ABRIENDO → ESCANEANDO → VIENDO/GUIANDO → LEÍDO → ESCANEANDO`, más `ERROR`/`NO_ENCONTRADO` y `PAUSADO`. **La cámara nunca se detiene después de leer.** Material pide "Remain in scanner: Yes" para el caso de uso en tienda.

| Estado | Disparador | Visual | Sonido / vibración |
|---|---|---|---|
| ABRIENDO | primer frame de UI (<100 ms) | Capa negra al 60% (`#99000000`) fuera de la retícula. Retícula blanca de 4dp con esquinas de 8dp. Texto "Abriendo cámara…". Spinner si tarda más de 1 s. | — |
| ESCANEANDO | **primer `analyze()` recibido**, no `previewStreamState`, que con SurfaceView puede adelantarse [V] | Línea láser a la mitad de la franja (coincide con la zona que leen los decodificadores 1D) + pulso suave de la retícula (ciclo de 2.5 s). Texto "Apunta al código de barras". | — |
| VIENDO | candidato presente (potencial o localizador verificado) | **Esquinas ámbar (`#FFC107`, 3dp) que siguen la caja del código en vivo.** Es la señal inmediata de "ya lo vi". | — |
| GUIANDO | pista estable (§3.4) | Chip de texto 20sp semibold sobre fondo sólido, con icono de flecha. Entra con escala 0.8→1 en 150 ms. Borde parcial de la retícula. `liveRegion = Polite`. | — |
| LEÍDO | lectura aceptada (§1.4) y aprobada por el filtro anti-repetición | En el **mismo frame de UI** y en ≤50 ms: retícula verde sólida (`#2E7D32`), palomita grande, código en ≥24sp. Se mantiene 400–600 ms y vuelve a ESCANEANDO. Destello verde opcional ≤30% de opacidad, desvanecido en 250–300 ms, nunca más de 3 por segundo (WCAG 2.3.1). | Bip `SoundPool` precargado de 80–120 ms + vibración `HapticFeedbackConstants.CONFIRM` (API 30+). En Compose: `HapticFeedbackType.Confirm` [V hoy]. Por debajo de API 30: `VIRTUAL_KEY` / `EFFECT_CLICK`. |
| ERROR / NO ENCONTRADO | validación o búsqueda en el POS fallida (cuando se conecte) | Trazo punteado, icono de tache. **Banner** (no tooltip) con "Reintentar" y "Capturar a mano". `liveRegion = Assertive`. | Doble tono grave de ~120 ms + `REJECT` |
| PAUSADO | inactividad | Imagen atenuada, "Toca para seguir escaneando" | — |

**Sonido:**
- `SoundPool.Builder().setMaxStreams(2).setAudioAttributes(USAGE_ASSISTANCE_SONIFICATION / CONTENT_TYPE_SONIFICATION)`.
- Avisar en pantalla si el volumen está en 0. El modo de timbre puede silenciar el bip (Scandit lo advierte), así que **la confirmación visual tiene que bastar por sí sola**.
- Frecuencia de 2.5–3 kHz [P].

**Vibración:**
- Desde API 33 una app normal no puede saltarse el ajuste "Respuesta táctil" del usuario [V]. Probar con ese ajuste apagado.
- Nada de `createOneShot` largos.

**Accesibilidad:**
- Éxito y error se distinguen por forma, icono, texto, sonido y vibración, no solo por color.
- Texto con contraste ≥4.5:1. Blanco sobre la capa al 60% da ≥5.7:1 aunque el fondo sea blanco [V].
- Probar con escala de fuente al 200%.
- Botones de ≥56dp (cerrar, linterna, captura manual, ayuda), pegados a los bordes.

### 4.2 Selección, anti-repetición y modo conteo

- **Varios códigos a la vista:** aceptar solo el que contiene el centro de la ventana o, si no hay, el más cercano al centro (como el Picklist de Zebra). **Nunca aceptar uno con `cutOff`.**
- **Anti-repetición en venta (`DuplicateGate`):**
  - Un código distinto se acepta de inmediato.
  - **El mismo código solo vuelve a sumar si estuvo ausente ≥500 ms.** Ausente significa ni decodificado ni con un candidato que se traslape con su última caja; así un parpadeo del decodificador no duplica.
  - Botón "+1" de 56dp junto al último producto.
  - No usar 1.5 s (modo manual de Scandit) ni 3 s (Dynamsoft): frenan a la cajera. Zebra usa 0.5 s [V]. Los 750 ms de Honeywell no están confirmados [NV].
- **Modo conteo:**
  - Misma regla de ausencia, con 400–500 ms.
  - Contador ×N de 32sp que crece un instante (150 ms) con cada suma.
  - −1 y "Deshacer" siempre visibles.
  - Pausa opcional de 300 ms después de cada lectura.

### 4.3 Presupuesto de latencia (medir p50 y p95 por equipo)

| Tramo | Meta | Origen |
|---|---|---|
| resultado → bip, vibración y frame verde | ≤50 ms | RAIL |
| resultado → producto en pantalla | ≤100 ms | Meta del POS original, escrita para el lector USB |
| código bien encuadrado → bip | p50 ≤250 ms, p95 ≤500 ms en gama baja | [P] |
| arranque en frío → primer frame analizado | ≤1.5 s | [P] |

Marcas de tiempo: `SystemClock.elapsedRealtimeNanos()` al entrar a `analyze()`. No usar `imageInfo.timestamp` sin revisar antes `SENSOR_INFO_TIMESTAMP_SOURCE` [NV].

---

## 5) Plan de pruebas

### 5.1 Automatizables en Linux y emulador (cualquier día, con cualquier código)

**N0. JVM/CI, sin emulador.**
- Pruebas unitarias de: GS1, normalización (UPC-A/E a GTIN-13, ITF de 14 dígitos), consenso 2-de-3, `DuplicateGate`, estabilizador de pistas (los escenarios de `GuidanceSim`, incluido ruido alrededor de un umbral que no debe parpadear) y `BarcodeLocator` sobre planos Y sintéticos.
- Generación: `zint` 2.16.0 (EANX, CODE128, ITF14, UPCA, QRCODE) + degradaciones con numpy/Pillow:
  - escala 0.45–8 px/módulo;
  - rotación 0–90°;
  - blur de caja r=0–6 y movimiento de 2–8 px;
  - ruido ±6 / σ=40, gamma oscura, disco saturado de 60/120 px, recorte del 25–30%;
  - negativos: trazos, texto, hoja rayada.
- Criterios: localizador verificado ≥95% con ≥2 px/módulo y **0 falsos positivos después del filtro temporal**.
- **No usar AlbumentationsX** (AGPL).
- Línea base con zxing-cpp en Python (`uv run --with zxing-cpp==3.1.1`). Ojo: el wrapper de Python **no expone `tryHarder`** y corre con `true`, así que no equivale a la configuración de Android [V].

**N1. Instrumentadas en el emulador** (API 37 x86_64):
- `DecodeAccuracyTest` lee `androidTest/assets/dataset/manifest.csv` (`file,format,expected`), lo pasa por ML Kit y zxing-cpp, y escribe un CSV con `ok, got, format, ms, cold`.
- Casos obligatorios:
  - `7509993456786`, interno `2…`, `0099999291452` (debe normalizarse), UPC-E `09999961`, EAN-8 `75099916`, Code 128 `ZZEV-00123a`, QR;
  - ITF válido `17509990000019` e inválido `17509990000018` (este debe rechazarse);
  - EAN con dígito malo `7509990000013` (debe rechazarse);
  - la lectura ITF de 40 dígitos debe caer en el validador;
  - `falsepositives-1/2` de zxing-cpp (Apache-2.0) con 0 falsos positivos.
- Comparar latencia ROI NV21 contra frame completo.
- Ejecutar con `installDebug` + `installDebugAndroidTest` + `adb shell am instrument -w …` + `adb pull`. No confirmé si `connectedAndroidTest` desinstala la app y borra `Android/data` [NV].
- Dataset ArTe-Lab (CC BY 3.0, 430 jpg, zip de 225,594,194 B): solo un script de descarga con sha256, sin subir las imágenes al repo.
- **Excluidos:** Muenster (sciebo da 404 y la licencia no se pudo confirmar) y BarBeR (licencia desconocida).

**N2. De punta a punta con la cámara virtual del emulador:**
```
emulator -avd Pixel_8_Pro -read-only -no-window -no-audio -no-snapshot -no-boot-anim -port <P> \
  -gpu swiftshader_indirect -grpc <G> -grpc-use-token -camera-back virtualscene -virtualscene-poster wall=/abs/poster.png
adb -s emulator-<P> emu automation play $ANDROID_HOME/emulator/resources/macros/Walk_to_image_room
adb -s emulator-<P> emu virtualscene-image wall /abs/siguiente.png      # cambia el código sin reiniciar
```
- **Nunca usar `-grpc` sin `-grpc-use-token`**: abre `[::]` sin autenticación [V]. El token está en `$XDG_RUNTIME_DIR/avd/running/pid_<pid>.ini` (clave `grpc.token`) y se manda como `authorization: Bearer`.
- Poses con `setPhysicalModel`, ROTATION `(-163.03, -32.83, -170.6)`:
  - POSITION A `(-2.0, 0.85, 1.95)`: debe leerse;
  - B `(-1.7, 0.75, 1.85)`: código cortado, se espera ALÉJATE o MUEVE;
  - C `(-2.99, 0.97, 2.24)`: código lejos, se espera ACÉRCATE o subir el zoom.
- Esperar 1–2 s después de cada `setPhysicalModel` (interpola).
- **Las poses solo valen para pósters de 1600x1200 con la misma composición** [V].
- `-camera-back videofile:` con secuencias de ffmpeg `zoompan` para medir el tiempo hasta leer. Antes hay que calibrar: el emulador ancla la imagen a la izquierda, y con una fuente de 1600x1200 solo se ve x∈[0, ≈900]. Volcar un frame de ImageAnalysis en debug para comprobarlo.
- Para verificar la interfaz: testTags de Compose / UiAutomator, o eventos `SCAN_STATE` en logcat con la secuencia de pistas.
- Cada corrida usa su propio `-port` y su propio `adb -s`. `webcam0` sirve para pruebas manuales con productos reales.

**N3. Rendimiento** (los números valen en celulares reales; en el emulador solo como prueba de humo con `androidx.benchmark.suppressErrors=EMULATOR`):
- Macrobenchmark 1.5.0 con `StartupMode.COLD`, `CompilationMode.DEFAULT` y 10 iteraciones.
- `StartupTimingMetric`, con TTFD definido por `ReportDrawnWhen { primerFrameAnalizado }`.
- `TraceSectionMetric("scan.firstFrame" | "scan.analyze" | "scan.locate" | "scan.decode", Mode.Sum)`.
- Microbenchmark del localizador y de los decodificadores (requiere `debuggable=false`).
- Perfetto con `android.power` + `linux.process_stats` + ftrace `sched_switch`/`cpu_frequency`, vía `adb shell perfetto -c - --txt -o /data/misc/perfetto-traces/scan.perfetto-trace`. Además `top -b -n 1 -p <pid> -o PID,%CPU,RES,NAME`, `dumpsys gfxinfo`, `dumpsys meminfo` y `dumpsys batterystats --reset` / `--charged <pkg>`.
- `PowerMetric` solo funciona en Pixel 6 o posterior.

### 5.2 Prueba en tienda (solo sábado y domingo)

Entre semana se prepara todo:
- APK firmado e instalado;
- guía paso a paso;
- lista de productos con su código esperado, sacada de una exportación **de solo lectura**, sin tocar los datos de eleventa;
- inventario de los celulares de los empleados.

**Protocolo:**
- Activar el "modo prueba" en la app.
- Mínimo **200 escaneos por dispositivo**, alternando el motor en bloques de 25 con orden ABBA, en modo venta y en modo conteo.
- Matriz de códigos: EAN-13 750, internos 2…, UPC-A, UPC-E (plumas), EAN-8, ITF-14 en cartón corrugado, Code 128 de eleventa, QR de ticket.
- Condiciones: poca luz, empaque brillante con reflejo, superficie curva, código chico o lejano, varios códigos juntos.
- **Batería:** 10–15 min de escaneo continuo por equipo.

**CSV por intento:**
- `session, n, engine, mode, fps_range`
- `t_to_candidate_ms, t_to_decode_ms, candidate_to_decode_ms, frames, decode_calls, decode_ms, locator_ms`
- `hints` (secuencia, por ejemplo `ACERCA|MUEVE_IZQ|QUIETO`), `format, value` (en QR solo hash + últimos 4), `expected, match`
- `px_per_module, zoom, torch`
- `outcome` (OK / TIMEOUT / CANCELADO / FALSO_POSITIVO / LECTURA_INCORRECTA / DUPLICADO), marcado por el operador
- `battery_pct, cpu_ms_proc` (`Process.getElapsedCpuTime()`), `thermal` (`PowerManager.currentThermalStatus`)
- `device, sdk, hw_level, min_focus, af_reports, physical_cam_id, app_version, git_sha`

**Exportación:**
- `FileProvider` (`${applicationId}.fileprovider`, `<cache-path name="exports" path="exports/"/>`).
- `Intent.ACTION_SEND`, `type = "text/csv"`, `EXTRA_STREAM`, `FLAG_GRANT_READ_URI_PERMISSION`, `ClipData.newRawUri`, `createChooser`.

**Volcados para recalibrar:** en debug, guardar planos Y con su `rowStride` de los últimos 20 intentos fallidos. Se reutilizan como pruebas de regresión en JVM y se borran después de usarlos.

**Metas** [P]:
- **0 lecturas incorrectas aceptadas** (bloquea la liberación);
- ≥98% de lectura en ≤2 s por intento;
- p50 candidato→lectura <300 ms y p90 <800 ms en gama baja;
- TTFD <1.5 s en frío;
- CPU del proceso <35% de un núcleo;
- localizador p95 <3 ms.

---

## 6) Riesgos y puntos sin verificar

| # | Riesgo / duda | Impacto | Mitigación |
|---|---|---|---|
| 1 | **Ninguna cifra viene de un celular real.** Las latencias de ML Kit (61–69 ms) y del localizador (0.25 ms) son de un emulador x86_64 o de un i9. | Alto | N3 y la prueba de fin de semana en el equipo más barato antes de fijar umbrales |
| 2 | zxing-cpp nunca se ejecutó en emulador ni en dispositivo; solo compiló con `assembleRelease`. | Medio | Correr N1 antes del A/B |
| 3 | ML Kit lee códigos de forma incorrecta: ITF de 40 dígitos (reproducido), issue #17 abierto (EAN-13 y Code 128). | Alto (cobrar otro producto) | Validación y consenso de §1.4; marcar LECTURA_INCORRECTA en tienda |
| 4 | No sé si ML Kit bundled funciona sin GMS (Huawei/AOSP). Tampoco si un `Detector` (que es LifecycleObserver) se cierra solo. | Medio | Probarlo en el equipo; cerrarlo a mano |
| 5 | La heurística de `ZoomSuggestionOptions` no está documentada. | Bajo | Apagado en v0.1 |
| 6 | `LENS_FOCUS_DISTANCE` solo está garantizado en FULL; en LIMITED/LEGACY puede faltar, y con UNCALIBRATED no es repetible. El umbral 0.95 es heurística propia. | Medio | Tratarla como señal opcional; respaldo con módulo y `lenRel` |
| 7 | CameraPipe es obligatorio en 1.6.x; `Camera2Interop` está deprecado en 1.7.0-alpha03. | Medio | Rama de respaldo en 1.5.3; aislar el interop |
| 8 | No sé si `ImageProxy.cropRect` refleja el ViewPort. El zoom digital a 720p que da más px/módulo es una inferencia. | Medio | Log y volcado de un frame en debug |
| 9 | Elegir [30,30] o [15,30] (desenfoque contra brillo) y su efecto en batería: no está medido. | Medio | A/B en tienda |
| 10 | No se sabe cómo guarda eleventa UPC-A y UPC-E. `]C1` en GS1-128 no se volvió a probar. | Medio | Buscar por varias claves; revisar la exportación |
| 11 | Todos los umbrales (luz 45/60, reflejo 6%/3%, 0.20/0.12, 0.30/0.90, nitidez 0.15/0.22, tiempos 300/700/500 ms) son [P]. Los conteos de módulos de Code 128 e ITF-14 son cálculo propio. | Medio | Calibrar con volcados reales |
| 12 | El localizador da ~2–3% de falsos positivos por cuadro en texto o escenas con trazos. | Bajo (solo afecta las pistas) | Filtro de 2–3 cuadros; nunca decide una lectura |
| 13 | Vibración: no se puede saltar "Respuesta táctil" (API 33+). Sonido: el volumen o el modo de timbre lo silencian. | Bajo | La confirmación visual debe bastar sola |
| 14 | La cámara `virtualscene` del emulador no está documentada (36.6.11 local contra 37.2.12 publicada). El formato de `environment.ini` y `VirtualSceneService` no se probaron. `setEnvironment` es para lentes XR. | Medio para N2 | Fijar el emulador en 36.6.11; volver a probar antes de actualizar |
| 15 | Licencias: zxing-cpp es Apache-2.0, pero incluye libzueci (BSD-3), cuyo aviso hay que mostrar. **No revisé los términos de uso de ML Kit.** Muenster y BarBeR quedan excluidos. | Bajo | Pantalla de licencias; revisar los términos de ML Kit |
| 16 | La guía de Material para escanear es M2, ya sin mantenimiento; no encontré equivalente en M3. Las cifras de Zebra (0.1 s entre símbolos distintos) y Honeywell (750 ms) no están confirmadas. | Bajo | Solo como referencia |
| 17 | Los agentes compartieron puertos del emulador (5590) durante la investigación. | Bajo (pruebas) | Un `-port` y un `adb -s` por corrida, y `-read-only` |
| 18 | La regla de fin de semana aplica a las pruebas en la matriz (`notas.md` del plan de sucursales). El desarrollo y las pruebas con cualquier código no están restringidos. | — | Preparar entre semana la lista para el sábado y el domingo |

## Fuentes principales

Consultadas el 2026-10-09.

- ML Kit: [escaneo de códigos en Android](https://developers.google.com/ml-kit/vision/barcode-scanning/android),
  [notas de versión](https://developers.google.com/ml-kit/release-notes),
  [`BarcodeScannerOptions.Builder`](https://developers.google.com/android/reference/com/google/mlkit/vision/barcode/BarcodeScannerOptions.Builder),
  [`ZoomSuggestionOptions`](https://developers.google.com/android/reference/com/google/mlkit/vision/barcode/ZoomSuggestionOptions.Builder),
  [rutas de instalación](https://developers.google.com/ml-kit/tips/installation-paths) e issues
  [#17](https://github.com/googlesamples/mlkit/issues/17), [#176](https://github.com/googlesamples/mlkit/issues/176),
  [#491](https://github.com/googlesamples/mlkit/issues/491), [#510](https://github.com/googlesamples/mlkit/issues/510),
  [#797](https://github.com/googlesamples/mlkit/issues/797).
- zxing-cpp: [versión 3.1.1](https://github.com/zxing-cpp/zxing-cpp/releases/tag/v3.1.1),
  [envoltura de Android](https://github.com/zxing-cpp/zxing-cpp/blob/v3.1.1/wrappers/android/zxingcpp/src/main/java/zxingcpp/BarcodeReader.kt),
  [`ReaderOptions.h`](https://github.com/zxing-cpp/zxing-cpp/blob/v3.1.1/core/src/ReaderOptions.h) y
  [Maven Central](https://repo1.maven.org/maven2/io/github/zxing-cpp/android/).
- CameraX: [versiones](https://developer.android.com/jetpack/androidx/releases/camera),
  [análisis de imágenes](https://developer.android.com/media/camera/camerax/analyze),
  [configuración](https://developer.android.com/media/camera/camerax/configuration),
  [`SessionConfig.Builder`](https://developer.android.com/reference/androidx/camera/core/SessionConfig.Builder),
  [`FocusMeteringAction`](https://developer.android.com/reference/androidx/camera/core/FocusMeteringAction),
  [`LENS_INFO_MINIMUM_FOCUS_DISTANCE`](https://developer.android.com/reference/android/hardware/camera2/CameraCharacteristics#LENS_INFO_MINIMUM_FOCUS_DISTANCE)
  y la charla de Apple sobre el zoom para leer de cerca ([WWDC21 10047](https://developer.apple.com/videos/play/wwdc2021/10047/)).
- Localizador: [detector de OpenCV](https://docs.opencv.org/4.x/d6/d25/tutorial_barcode_detect_and_decode.html)
  ([`bardetect.cpp`](https://raw.githubusercontent.com/opencv/opencv/4.x/modules/objdetect/src/barcode_detector/bardetect.cpp)),
  [PyImageSearch](https://pyimagesearch.com/2014/11/24/detecting-barcodes-images-python-opencv/) y
  [Tekin y Coughlan / Manduchi](https://users.soe.ucsc.edu/~manduchi/papers/barcodes.pdf).
- UX: [Material Design, escaneo de códigos (M2)](https://m2.material.io/design/machine-learning/barcode-scanning.html),
  [ML Kit Material Showcase](https://github.com/googlesamples/mlkit/tree/master/android/material-showcase),
  [Scandit SparkScan](https://docs.scandit.com/sdks/android/sparkscan/intro/),
  [Zebra, tiempo entre lecturas](https://docs.zebra.com/us/en/scanners/general/ds4-series/ds4678-prg/user-preferences-and-miscellaneous-options/user-preferences/timeout-between-decodes),
  [hápticos de Android](https://developer.android.com/develop/ui/views/haptics/haptics-principles),
  [RAIL](https://web.dev/articles/rail) y [WCAG 2.2](https://www.w3.org/TR/WCAG22/).
- Pruebas: [emulador por línea de comandos](https://developer.android.com/studio/run/emulator-commandline),
  [consola del emulador](https://developer.android.com/studio/run/emulator-console),
  [Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview),
  [Perfetto](https://github.com/google/perfetto/blob/main/docs/getting-started/system-tracing.md),
  [compartir archivos](https://developer.android.com/training/secure-file-sharing/setup-sharing) y el
  [dataset ArTe-Lab](https://artelab.dista.uninsubria.it/downloads/datasets/barcode/medium_barcode_1d/medium_barcode_1d.html) (CC BY 3.0).
