# Pruebas

Cómo se prueba el escáner sin la tienda: en la PC de desarrollo (Linux), en el emulador y con cualquier código de barras
en un celular. Las cifras que cuentan se toman en el Pixel 8 Pro ([plan](plan.md)); lo que solo se puede probar en la
tienda (los productos y la luz de ahí) va en [pruebas en tienda](pruebas-en-tienda.md), sábado y domingo.

Todos los comandos, desde la raíz del repositorio y con el JDK de Android Studio:

```
export JAVA_HOME=/opt/android-studio/jbr
```

## En la JVM (cualquier PC, también GitHub Actions)

```
./gradlew test
```

- **`nucleo`:** verificación de GTIN, codificadores (comprobados contra zbar), geometría y rotaciones, el localizador
  sobre escenas sintéticas (tamaños, giros, desenfoque, código cortado, escenas sin código), la guía (secuencias de
  cuadros: cada indicación, histéresis y que no parpadee) y la validación y el consenso (normalización, ITF de 14
  dígitos, dos lecturas iguales, repetidos con ausencia).
- **`app`:** la sesión de prueba y su CSV, el banco de pruebas y las listas de venta y conteo.

**Escenas sintéticas.** `Lienzo` (en `nucleo`) dibuja un código en un plano de luminancia como lo vería la cámara:
posición, tamaño, giro, perspectiva, desenfoque, movimiento, ruido, poca luz y reflejo. Lo usan las pruebas, el banco
de pruebas del celular y los carteles del emulador.

## En el emulador

El emulador puede usar una imagen como cámara trasera (`-camera-back imagefile:<png>`). Los carteles con códigos en
distintas situaciones (centrado, chico, grande, a la izquierda, abajo, girado, borroso, oscuro, con reflejo, y cada
simbología centrada) se generan con:

```
./gradlew :nucleo:carteles      # PNG en nucleo/build/carteles
```

El emulador (36.6) no muestra la imagen completa: la ancla a la izquierda y la escala por alto. Con un cartel de
1280×960 y la app en vertical se ve x de 0 a ~490 y toda la altura (calibrado con una cuadrícula el 2026-10-09), así
que cada escena se dibuja en esa franja izquierda (`Carteles.kt`).

Para abrir la app con uno de ellos (arranca el emulador sin ventana y de solo lectura en su propio puerto, instala el
APK, da el permiso de la cámara y abre el escáner):

```
herramientas/emulador.sh nucleo/build/carteles/ean13-centro.png
```

**De punta a punta con todos los carteles** (unos 40 s por cartel): por cada uno arranca el emulador, abre la app,
espera y guarda la captura y lo que dijo el escáner (lecturas con su vía e indicaciones con los valores que las
provocaron), y al final un resumen:

```
herramientas/e2e.sh salidas/e2e
```

Resultado del 2026-10-09: se leen los 10 carteles legibles una sola vez cada uno (150–600 ms desde que aparece el
código), el chico pide «Acércate», el grande «Aléjate», el de la izquierda «Mueve el celular a la izquierda» y el
borroso «Mantén firme». El reflejo no se puede probar en el emulador: su cámara nunca pasa de 218 de brillo y el reflejo
se detecta por saturación (≥ 250); se prueba con escenas sintéticas en la JVM y en la tienda. Tampoco sirven ahí la
distancia de enfoque (reporta 10–20 veces la mínima) ni el zoom.

Lo que pasa también se ve en el registro (`adb -s emulator-5570 logcat -s Escaner`): una línea por cada indicación
(con dx, dy, largo, módulo, nitidez, brillo, saturados y enfoque), por cada lectura (con su vía y los ms desde que
apareció el código) y por la geometría de la cámara.

**Pruebas instrumentadas** (en el emulador o en un celular conectado):

```
./gradlew connectedDebugAndroidTest
```

- **Motores:** cada motor lee los códigos dibujados con el texto correcto, sus esquinas caen sobre el código, ML Kit avisa
  de los que ve sin leer y ninguno lee algo donde no hay código; mide la latencia con la franja y con el cuadro completo.
- **Pantalla del escáner:** cada estado y cada indicación muestran lo que deben (con un controlador de muestra, sin
  cámara), y capturas de cada estado para revisarlas a ojo.

Las latencias del emulador sirven para comparar, no como meta: las de verdad se miden en celulares.

## Con cualquier código de barras

Sin ir a la tienda: cualquier producto de la casa, las etiquetas del POS impresas o un código en otra pantalla.

1. Instalar el APK en un celular propio (`adb install -r …` o el APK de la corrida de GitHub Actions).
2. **Escanear** en modo venta y en modo conteo; en Ajustes se cambia el motor y se puede ver el diagnóstico (cuadros por
   segundo, milisegundos, px por módulo, nitidez, brillo).
3. **Banco de pruebas:** compara los dos motores en ese celular con escenas sintéticas, sin depender de la mano.
4. **Sesión de prueba:** registra cada intento con sus tiempos y exporta un CSV, igual que en la tienda.

## Arranque y perfil

**Un solo enlace y la línea de la apertura** (con la app instalada en el emulador o en un celular):

```
adb logcat -c
adb shell am start -S -W -n mx.papeleriapos.android/.MainActivity     # TotalTime = hasta el primer cuadro (TTID)
adb logcat -d -s Escaner | grep -E "Cámara enlazada|Apertura:"
adb logcat -d | grep -E "Displayed|Fully drawn"                        # Fully drawn = primer cuadro analizado (TTFD)
```

Debe salir **una** vez «Cámara enlazada». Para el arranque en caliente: `adb shell input keyevent KEYCODE_HOME` y el
mismo `am start` sin `-S`.

**El perfil de arranque** (Baseline Profile y Startup Profile) se genera en un emulador o celular con API 33+, sin root,
y va en git (`app/src/main/generated/baselineProfiles/`):

```
./gradlew :app:generateBaselineProfile
```

Fuera de Google Play el perfil se compila hasta la noche, con el celular cargando. Para medir desde el principio:

```
adb shell cmd package compile -f -m speed-profile mx.papeleriapos.android
```

**Macrobenchmark** (`:benchmark`): arranque frío, tibio y caliente, sin perfil y con perfil, con el TTFD (primer cuadro
analizado) y las secciones de trace. En el Pixel 8 Pro, 15 iteraciones:

```
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest
```

Ojo: la tarea de Gradle **desinstala la app al terminar, con sus datos** (listas, sesiones, ajustes), y corre en todos
los equipos conectados (`ANDROID_SERIAL=<serie>` para elegir uno). En el Pixel (o en cualquier celular con datos que
importen) se instala y se corre a mano. Al terminar, en el Pixel se deja instalada **la versión más rápida medida**, con
su perfil compilado (regla del dueño, 2026-10-09). Se instala encima sin borrar los datos porque usa la misma firma.
El APK de cada versión medida se guarda junto con su medición, para poder volver a ella. Con la pantalla bloqueada no corre: el
celular cargando con «Permanecer activo» (Opciones de desarrollador).

```
./gradlew :app:assembleBenchmarkRelease :benchmark:assembleBenchmarkRelease
adb install -r app/build/outputs/apk/benchmarkRelease/app-benchmarkRelease.apk
adb install -r -t benchmark/build/outputs/apk/benchmarkRelease/benchmark-benchmarkRelease.apk
adb shell am instrument -w -r -e iteraciones 15 mx.papeleriapos.benchmark/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/media/mx.papeleriapos.benchmark/ resultados/    # JSON y trazas de Perfetto
adb uninstall mx.papeleriapos.benchmark
adb shell rm -r /sdcard/Android/media/mx.papeleriapos.benchmark
adb install -r <el benchmarkRelease más rápido>.apk                      # si no es el que quedó
adb shell cmd package compile -f -m speed-profile mx.papeleriapos.android
```

**Las trazas:** `herramientas/trazas.sh <carpeta-con-las-trazas>` saca de cada traza los hitos de la apertura:
cámara abierta, sesión, primer cuadro analizado y cuánto espera la cámara a la sesión. Imprime las medianas por caso.
Usa el trace_processor de Macrobenchmark, que es un binario de Android: corre en el emulador (o en un celular
conectado) y no toca la app.

**El calor arruina la medición.** Después de una hora de Macrobenchmark el Pixel llegó al estado térmico 2 (piel a
43 °C), y una corrida salió con todo más lento, hasta lo del sistema: `bindApplication` 83 ms en vez de 47. Antes de
cada corrida: estado térmico 0 y piel debajo de 36 °C
(`adb shell dumpsys thermalservice | grep -E "Thermal Status|VIRTUAL-SKIN,"`), con el celular en la pantalla de
inicio hasta que baje. Para comparar dos versiones se miden una tras otra en la misma sesión (A/B), no contra una
corrida de otro día. La corrida que se hizo con calor se guarda marcada como inválida y no se usa.

**La línea base en el Pixel 8 Pro** (2026-10-09, Android 17, 15 iteraciones, medianas; el TTFD es el primer cuadro
analizado):

| Arranque | Sin perfil | Con perfil |
|---|---|---|
| Frío: primer cuadro en pantalla (TTID) | 380 ms | 225 ms |
| Frío: ya escaneando (TTFD) | 913 ms | 629 ms |
| Frío: espera a CameraX | 107 ms | 32 ms |
| Tibio: TTID / TTFD | 130 / 431 ms | 137 / 386 ms |
| Caliente: TTID / ya escaneando | 99 / 407 ms | 75 / 278 ms |
| Por cuadro: total / localizador / lectura | 3.3 / 1.0 / 2.1 ms | 2.4 / 0.6 / 1.8 ms |

El perfil quita ~30 % del arranque frío y hace más parejas las mediciones (TTFD en frío de 583 a 700 ms con perfil, de
855 a 1162 sin él). En caliente, ~200 ms van de la interfaz al primer cuadro analizado: la cámara vuelve a abrir.

En el emulador solo como prueba de humo (las cifras no valen): agregar `-PconX86
-Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
-Pandroid.testInstrumentationRunnerArguments.iteraciones=2`. Los resultados (JSON y trazas de Perfetto) quedan en
`benchmark/build/outputs/connected_android_test_additional_output/`.

La prueba de humo también es la que revisa el APK con R8 (`benchmarkRelease` es el release instalable): falla si la app
no llega a escanear. Así se encontró que ML Kit no arrancaba con R8 en modo completo (`app/proguard-rules.pro`). Se
corre antes de cada versión.

**El mosaico sobre el bloqueo** en el emulador:

```
adb shell locksettings set-pin 1234
adb shell cmd statusbar add-tile mx.papeleriapos.android/.rapido.MosaicoEscanerService
adb shell input keyevent KEYCODE_SLEEP; adb shell input keyevent KEYCODE_WAKEUP
adb shell cmd statusbar expand-settings      # y tocar el mosaico
adb shell locksettings clear --old 1234      # al terminar
```

## Estrés

La pestaña **Estrés** de la app corre sobre la cámara real y dice si cada prueba pasa, con su CSV:

- **Ciclos:** abrir → cámara abierta → primer cuadro → cerrar, 200 veces (con un controlador nuevo por ciclo o el mismo
  yendo y volviendo de segundo plano). Pasa si los ciclos con error son a lo más el 1 %, el p95 del final no pasa de
  1.2 veces el del inicio (sin el primero, que es frío) y la memoria, los hilos y los descriptores no crecen.
- **Ráfaga:** zoom alternado cada 30 ms durante 60 s y la linterna 100 veces; termina apagada y la interfaz coincide.
- **Cambio de motor** en caliente 50 veces.
- **Escaneo continuo** de 5, 15 o 30 minutos con el monitor de recursos: detecta el estrangulamiento térmico (estado
  térmico moderado o más, margen ≥ 0.85, cuadros de análisis < 85 % del primer minuto o CPU por cuadro +25 %).

En el emulador también hay pruebas instrumentadas de ciclos (`CiclosCamaraTest`) y de navegación de la app
(`NavegacionTest`: cada pestaña y de vuelta al escáner, que debe volver a escanear; `ControladorCamaraTest` revisa que
al volver la cámara abra una sola vez). Las pruebas con UiAutomator, el perfil y Macrobenchmark esperan el id
`escaneando`, que aparece con el primer cuadro analizado. Monkey para lo inesperado:

```
adb shell monkey -p mx.papeleriapos.android -s 20261009 --throttle 120 --pct-touch 45 --pct-motion 20 --pct-appswitch 10 -v 20000
```

## Rendimiento y batería

En un celular conectado:

```
adb shell top -b -n 1 -o PID,%CPU,RES,NAME | grep papeleriapos     # CPU y memoria escaneando
adb shell dumpsys batterystats --reset                             # antes de escanear 10–15 min
adb shell dumpsys batterystats --charged mx.papeleriapos.android   # después
```

Las secciones `escaner.cuadro`, `escaner.localizar` y `escaner.leer` aparecen en un rastreo de Perfetto o del perfilador
de Android Studio para ver cuánto tarda cada paso. Dentro de la app, la sesión de prueba exporta `recursos.csv` (una
fila por segundo) y el banco de pruebas tiene el modo **Límites**: para cada motor y cada condición (desenfoque,
movido, reflejo, poca luz, tamaño, giro, perspectiva) sube la dificultad hasta el último grado que todavía lee en el
80 % de las variantes.
