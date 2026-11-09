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
  cuadros: cada indicación, histéresis y que no parpadee), la validación y el consenso (normalización, ITF de 14
  dígitos, verificador mal que nunca entra, dos lecturas iguales en cuadros seguidos, QR de un cuadro, repetidos al
  salir de la mira o a los 1.5 s, dos códigos juntos que no se suman sin fin) y la búsqueda en el catálogo (cada
  variante, el orden, que no corrige nada más, un catálogo con el verificador mal y que da lo mismo con ML Kit que con
  zxing-cpp).
- **`escaner`:** las marcas de la apertura, la máquina de estados y las políticas de la cámara, y el turno del respaldo
  de ML Kit (si lee en su turno, corre también en el cuadro siguiente para la segunda lectura).
- **`app`:** la sesión de prueba y su CSV, el banco de pruebas, las listas del ticket y del conteo (con la clave del
  catálogo y los cambios por renglón) y el catálogo de prueba (el de ejemplo trae un caso por variante; copiar el
  archivo, reabrir y volver al de ejemplo). De la pantalla principal: el gesto del botón rápido con los casos del
  prototipo (`GestoTest`), lo que lleva cada modo (`OpcionesTest`), el estado de los modos, el ticket, las hojas, los
  avisos y las pistas (`PisoTest`) y los productos de prueba con su mayoreo y su búsqueda (`ProductosTest`).
- **`app`, del bolsillo al precio** (la entrega 3):
  - El escaneo rápido fijo en Precio: el nombre, el precio, el mayoreo, la promoción y el precio por pieza de una
    presentación, nunca la existencia; nada entra a las listas aunque la app esté en Ticket o en Conteo; «No está en el
    catálogo» con su código, y mientras cargan los productos solo el código; TalkBack dice cada lectura
    (`EscaneoRapidoTest`), y con la letra grande lo leído cede y los botones no (`PantallaRapidaLetraGrandeTest`).
  - La Activity de verdad, con el controlador de muestra (`EscaneoRapidoActividadTest`): el mosaico la abre aunque ya
    hubiera caducado y es una apertura nueva, con el intent antes y después de `onRestart` (Android 7–9 y 10+), también
    dejada en Inicio y traída ya con el bloqueo, como en el punto 8 de la revisión en el celular; al encender la
    pantalla caducada se cierra sin que la cámara pase de CREATED; «Abrir la app» con ShadowKeyguardManager: el intent
    (MAIN y LAUNCHER, `NEW_TASK | CLEAR_TOP | SINGLE_TOP`, el modo y el producto), un solo pedido aunque se toque dos
    veces, no se cierra sola mientras espera el PIN, cancelado sigue ahí, y en API 25 abre directo.
  - La app abierta con ese intent (`AbrirConUnProductoTest`, con la MainActivity de verdad): en `onCreate` y en
    `onNewIntent`, nada si viene de Recientes o con estado guardado, y los accesos se publican después del primer cuadro
    analizado; desde Ajustes o una herramienta regresa a la principal (`AppEscanerDeFueraTest`); cierra el menú y las
    hojas y no toca el ticket (`PantallaPrincipalRegresionTest`), y TalkBack lo dice (`PantallaPrincipalAccesibleTest`).
  - Los accesos del ícono: la regla de qué se publica, en JUnit (`PlanDeAccesosTest`); lo publicado, con el
    ShadowShortcutManager de serie (`AccesosTest`); lo anclado, con `ShadowAtajosComoAosp`, que le suma las reglas de
    AOSP que le faltan (`AccesosAncladosTest`); y shortcuts.xml leído como lo lee el sistema (`AtajosDelIconoTest`).
  - El mosaico: «Precio» en el manifiesto y en `onStartListening`, su ícono (la etiqueta de precio) y sus textos
    (`MosaicoTest`); el plazo y la cámara apagada mientras se pide el PIN (`CaducidadTest`).
- **`app`, el celular de Luis** (la entrega 2, la conexión con el POS):
  - La copia del catálogo (`CopiaTest`): el paquete se lee con sus versiones; los usuarios con el hash de su PIN, sus
    permisos y reglas, los clientes y lo del ticket no quedan ni en memoria ni en lo que se guarda (el JSON de prueba
    los trae a propósito, `src/test/resources/paquete-prueba.json`, con datos ficticios); los cambios reemplazan,
    agregan, quitan y mueven códigos como `CopiaAlDia.Aplicar` de la caja de Windows; cuándo el latido obliga a pedirlos.
  - Los productos de la copia (`ProductosDeLaCopiaTest`): cada variante y cada otra presentación, el precio de la
    sucursal sobre el general, el mayoreo, el precio propio de la caja de 12, lo que no lleva inventario, las unidades,
    la regla de los códigos y un código de dos productos que no se adivina, la promoción que corre hoy (y la que no), la
    búsqueda con lo elegido y lo más vendido, y el día de hoy en la zona horaria del celular.
  - La red (`RedLocalTest`): solo direcciones privadas, con o sin puerto; lo que contesta el descubrimiento; la serie y
    el nombre que se proponen.
  - La identidad de la caja principal (`IdentidadTest`, con un servicio de mentira que firma con ECDSA, `LlavesDePrueba`):
    la huella y la prueba como en el POS; se acepta la respuesta buena y se rechazan otro reto, otra dirección, otra
    llave, la firma de otro servicio y la basura.
  - El cliente (`ClienteCajaTest`, con MockWebServer): la llave en `X-Caja-Llave`, el alta con `cajaId` vacío, los
    cambios con todas las versiones, los mensajes del servicio tal cual y el 401 «caja-sin-alta».
  - La conexión (`ConexionTest`, con una caja principal de mentira): sin alta no se llama a nadie; el alta guarda la
    llave y baja la copia; el latido pide los cambios solo cuando cambian las versiones y los aplica, y baja la copia
    entera si el servicio lo dice; el 401 borra la llave y la copia; si la caja principal cambia de IP se vuelve a
    encontrar solo cuando prueba quién es (una impostora con la huella pero sin la llave del servicio no entra); y al
    abrir con alta la copia del disco se lee sin red.
  - Cuándo se pide la huella (`EntradaTest`): al abrir y después de 10 minutos en el fondo.
- **La pantalla dibujada, sin celular:** `PantallaPrincipalCapturasTest` dibuja la pantalla principal con Robolectric
  (gráficos nativos) con la geometría del chico (360 × 740 dp con barras de 24 + 24: como Robolectric no da la barra de
  gestos, la ventana mide 716 y abajo va solo el hueco de los modos, así el panel mide lo mismo que en el celular), con el
  controlador de muestra en lugar de la cámara: lee códigos, toca el botón rápido y desliza, abre las hojas y revisa lo
  principal de cada modo, también con la letra al 200 %. Deja una captura de cada estado en `app/build/capturas` para
  verla sin emulador (las de la letra al 200 % llevan «-200»). `PantallaPrincipalRegresionTest`,
  `PantallaPrincipalAccesibleTest` (TalkBack, mirado en el proveedor de accesibilidad de Compose, y el teclado) y
  `PantallaPrincipalLetraGrandeTest` guardan lo que encontró la revisión de la entrega 1, cada caso como prueba.

**Escenas sintéticas.** `Lienzo` (en `nucleo`) dibuja un código en un plano de luminancia como lo vería la cámara:
posición, tamaño, giro, perspectiva, desenfoque, movimiento, ruido, poca luz y reflejo. Lo usan las pruebas, el banco
de pruebas del celular y los carteles del emulador.

**Códigos de ejemplo: siempre ficticios**, nunca de un producto de la tienda ni uno conocido, y el verificador (C)
calculado con `Gtin`, no a ojo: EAN-13 `750999XXXXXXC`, internos del POS `299999XXXXXXC`, UPC-A `099999XXXXXC`, UPC-E
con fabricante 99… (`09999961` se expande a `099999000061`), EAN-8 `750999XC`, GTIN-14 para ITF `1750999XXXXXXC`, Code
128 con letras `ZZ…` (`ZZ0001`, `ZZ-PRUEBA-0001`) y QR del ticket `A-000123` o `ZZ-000001`. El EAN-13 de ejemplo,
`7509993456786`, se eligió porque pasa la prueba del localizador con 5 px por módulo y desenfoque 3, que depende del
dibujo de las barras (uno de cada cuatro EAN-13 al azar no la pasa).

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
2. **Escanear** en Precio, Ticket y Conteo; en Ajustes › Herramientas de medición › El escáner se cambia el motor y se
   puede ver el diagnóstico (cuadros por segundo, milisegundos, px por módulo, nitidez, brillo).
3. **Banco de pruebas:** compara los dos motores en ese celular con escenas sintéticas, sin depender de la mano.
4. **Sesión de prueba:** registra cada intento con sus tiempos y si está en el catálogo de prueba, y exporta un CSV,
   igual que en la tienda. Con el catálogo de ejemplo, los códigos de los carteles del emulador salen «si».

## Hoja de etiquetas

Para decidir de qué tamaño imprime el POS sus etiquetas ([plan](plan.md#entrega-8-medir-la-lectura), parte 5): EAN-13
y Code 128 ficticios con módulos de 0.25, 0.33, 0.40 y 0.50 mm y barras de 15, 10 y 6 mm de alto, más un renglón de
EAN-8 chicos (10 mm de alto). Son 40 etiquetas en 2 páginas tamaño Carta.

```
./gradlew :nucleo:hojaEtiquetas      # en nucleo/build/hoja-etiquetas
```

- `hoja-etiquetas.pdf` para imprimir, `hoja-etiquetas-1.svg` y `-2.svg` para verla en pantalla y
  `hoja-etiquetas.csv` (`codigo,simbologia,modulo_mm,alto_mm,pagina`).
- Cada código dice qué etiqueta es. EAN-13: 750999, el módulo en centésimas, el alto, el consecutivo y el verificador
  (`7509993310132` es la etiqueta 13: 0.33 mm, 10 mm). Code 128: la clave corta `ZZ3310` y la larga
  `ZZ-M033-A10-015`. EAN-8: 750999, la primera cifra del módulo y el verificador (`75099930`).
- Las zonas en blanco son las de la norma: EAN-13, 11 módulos a la izquierda y 7 a la derecha; Code 128, 10 y 10;
  EAN-8, 7 y 7. Debajo de cada código va el texto y «módulo 0.33 mm · alto 10 mm».
- 0.33 mm es el EAN-13 a su tamaño nominal (100 %). 0.25 mm queda debajo del mínimo de GS1 (0.264 mm): sirve para ver
  el límite.

**Imprimir:** en láser, a **tamaño real** («100 %» o «Tamaño real»), nunca «Ajustar a la página» ni «Reducir», y sin
ahorro de tóner. Luego medir la regla de arriba con una regla de verdad: de punta a punta, **100 mm**. Si mide otra
cosa, la impresión salió escalada y los módulos no son los que dicen: volver a imprimir. Cada borde cae en el punto más
cercano de la impresora (0.04 mm a 600 ppp): en el módulo de 0.25 mm, si falla, ver con lupa que las barras salieron
parejas.

**Qué medir,** con la sesión de prueba y cualquier día ([plan](plan.md#entrega-8-medir-la-lectura)):

- Por cada tamaño (módulo y alto), la distancia mínima y la máxima a la que lee: de frente, acercando y alejando
  despacio. Anotarlas en la nota del intento.
- El ángulo, a la distancia cómoda: inclinado hacia atrás 15°, 30° y 45°, y girado sobre la hoja.
- El EAN-8 chico, igual.
- Para que no lea el de al lado: tapar los vecinos con una hoja en blanco o recortar la etiqueta con sus zonas en
  blanco.

El CSV sirve de **catálogo de prueba:** la primera columna es el código y las demás dicen qué etiqueta es, para cruzar
cada lectura de la sesión con su tamaño.

**Comprobada:** `:nucleo:test` vuelve a leer el PDF (por su tabla xref) y los SVG y revisa cada barra contra el
codificador (± 0.001 mm), las zonas en blanco, los márgenes de 10 mm y la regla. El 2026-10-11, zbarimg leyó las 40
etiquetas del PDF pasado a imagen a 300 y a 600 ppp, y a 2540 ppp la regla midió 100.00 mm.

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

Esa tarea también **desinstala la app al terminar, con sus datos**. En el Pixel (o en un celular con datos) se corre a
mano y luego se juntan los dos recorridos como lo hace el plugin (ver
[la medición del 2026-11-07](../mediciones/2026-11-07-0.3.0/LEEME.md)). Para que el perfil traiga la lectura, la
cámara debe estar viendo un código mientras corre:

```
./gradlew :app:assembleNonMinifiedRelease :baselineprofile:assembleNonMinifiedRelease
adb install -r app/build/outputs/apk/nonMinifiedRelease/app-nonMinifiedRelease.apk
adb install -r -t baselineprofile/build/outputs/apk/nonMinifiedRelease/baselineprofile-nonMinifiedRelease.apk
adb shell am instrument -w -r -e androidx.benchmark.enabledRules BaselineProfile mx.papeleriapos.baselineprofile/androidx.test.runner.AndroidJUnitRunner
adb pull /sdcard/Android/media/mx.papeleriapos.baselineprofile/ perfil/   # *-baseline-prof.txt y *-startup-prof.txt
adb uninstall mx.papeleriapos.baselineprofile
adb shell rm -r /sdcard/Android/media/mx.papeleriapos.baselineprofile
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

## En el Pixel 8 Pro o el emulador

Del bolsillo al precio (la entrega 3) tiene lo que Robolectric no tiene: tareas, el lanzador, el bloqueo con PIN y
SystemUI. Se revisa así antes de publicar la 0.3.0, en el Pixel (Android 17) y en el emulador con API 24, 25, 28 y 35.
El mosaico sobre el bloqueo, en el emulador:

```
adb shell locksettings set-pin 1234
adb shell cmd statusbar add-tile mx.papeleriapos.android/.rapido.MosaicoEscanerService
adb shell input keyevent KEYCODE_SLEEP; adb shell input keyevent KEYCODE_WAKEUP
adb shell cmd statusbar expand-settings      # y tocar el mosaico
adb shell locksettings clear --old 1234      # al terminar
```

Cuántas MainActivity tiene la tarea (debe haber una):

```
adb shell dumpsys activity activities | grep Hist | grep -c mx.papeleriapos.android/.MainActivity
```

Si el escaneo rápido sigue vivo aunque no se vea: el primero da 1 (0, ya se cerró) y el segundo, el número del proceso,
el mismo que cuando se abrió (si el proceso murió, el primero sigue dando 1, pero el número cambia o no sale nada):

```
adb shell dumpsys activity activities | grep Hist | grep -c mx.papeleriapos.android/.rapido.EscaneoRapidoActivity
adb shell pidof mx.papeleriapos.android
```

1. **Los accesos anclados de antes.** Con el APK de la 0.2, anclar «Precio», «Ticket» y «Conteo»; `adb shell dumpsys
   shortcut` los da con `Im`, `Man` y `Pin`. Instalar la 0.3 encima: `escanear_venta` y `escanear_conteo` con `Dis`, `Im`
   y `Pin`, `disabledReason=1` y su mensaje; `escanear_precio` con `Man`; y, ya abierta la app y escaneando, `ticket` y
   `contar` con `Dyn`. Tocar los apagados: ¿sale el mensaje de la app o «El acceso directo no está disponible»? Anotar
   la versión del lanzador (`adb shell cmd package resolve-activity -a android.intent.action.MAIN -c
   android.intent.category.HOME`).
2. **Que no se apile otra.** Abrir con «Revisar precio», ir a Inicio y tocar el ícono: una sola MainActivity (según el
   código de AOSP, con la acción de la 0.1 se apilaba otra). Repetir con «Armar ticket» con la app abierta en Precio:
   una sola, en Ticket.
3. **«Contar» se apaga y vuelve.** Anclar «Contar»; instalar un APK de depuración con `EsteCelular.SE_PUEDE_CONTAR =
   false` (rapido/Accesos.kt), abrir la app y tocar el anclado (apagado, «En el piloto no se cuenta»); volver al APK
   normal: se enciende y regresa al menú. El límite de llamadas, con `adb shell cmd shortcut reset-throttling` y
   `adb shell cmd shortcut override-config max_updates_per_interval=1` (`reset-config` lo regresa).
4. **«Abrir la app» con PIN (API 35 a 37).** Bloquear, tocar el mosaico, leer un código, tocar «Abrir la app», teclear el
   PIN: Precio con ese producto. Repetir con la app abierta desde el ícono, desde un acceso anclado, en Ajustes, con la
   hoja de compartir abierta, sin la app abierta y después de `adb shell am kill mx.papeleriapos.android` con la app en
   segundo plano. En cada caso, una sola MainActivity.
5. **El PIN.** Cancelarlo con atrás: sigue el escaneo rápido. Quedarse más de un minuto en el PIN: no se cierra y al
   desbloquear abre. Tocar «Abrir la app» dos veces seguidas: abre una sola vez.
6. **API 24 y 25, en el emulador.** «Abrir la app» enseña el bloqueo normal y, al desbloquear, la app con el producto.
7. **La etiqueta del mosaico.** Con la 0.2 instalada y el mosaico agregado, en API 28 y en 29 o 35, instalar la 0.3 sin
   reiniciar y abrir el panel: «Precio», con la etiqueta de precio. `adb shell settings get secure sysui_qs_tiles` sigue
   trayendo `custom(mx.papeleriapos.android/.rapido.MosaicoEscanerService)`.
8. **Un toque al mosaico siempre abre,** también con el escaneo rápido vivo detrás y ya caducado: en el Pixel (el intent
   llega después de `onRestart`) y en el emulador con API 24 o 25 (llega antes). Primero, `adb shell settings get global
   always_finish_activities` debe dar 0 (con 1, al ir a Inicio la Activity se destruye aunque los comandos de arriba
   digan que sigue) y la pantalla debe tardar 2 minutos en apagarse sola (anotar lo que da `adb shell settings get
   system screen_off_timeout`, poner `adb shell settings put system screen_off_timeout 120000` y al terminar
   regresarlo): en pausa el escaneo rápido deja que la pantalla se apague, y apagada no se cierra sola. Desbloqueado,
   abrirlo con el mosaico, ir a Inicio enseguida y anotar el número del proceso. Apagar la pantalla más de un minuto (se
   bloquea) y encenderla: sale el bloqueo, no el escaneo rápido, que sigue vivo detrás (los comandos de arriba: 1 y el
   mismo número; si no, el toque sería una apertura nueva, que también sale bien con la 0.2: repetir). Bajar el panel y
   tocar el mosaico: abre con la cámara y, sin leer nada, se cierra sola un minuto después del toque, no antes (con la
   0.2 el toque se perdía). Repetir con la pantalla apagada unos 30 s: otra vez un minuto desde el toque (con la 0.2 se
   cerraba al minuto de haberlo abierto). Aparte, dejarlo arriba y apagar la pantalla más de un minuto: al encenderla se
   cierra sin abrir la cámara (no sale el punto verde).
9. **Recientes.** Abrirlo desde el escaneo rápido con el celular bloqueado, con tres botones y con gestos: pide el PIN, y
   en API 33+ la miniatura del escaneo rápido sale negra. Reiniciar con la tarea que creó «Abrir la app» y abrirla
   desde Recientes: no vuelve el producto de entonces.
10. **Tiempos,** si las banderas se deciden por milisegundos: Macrobenchmark de un acceso con `CLEAR_TASK` (los de hoy)
    contra `CLEAR_TOP | SINGLE_TOP`.

## La conexión con el POS

Entre semana, contra un servicio del POS en la PC de desarrollo con la base ficticia, nunca contra los equipos de la
tienda. El POS de la rama `celular/como-caja` (en `~/Documents/TPV/papeleria-pos-celular`), con su README:

```
cd ~/Documents/TPV/papeleria-pos-celular
dotnet run --project src/PapeleriaPos.Herramientas -- generar-ficticios --salida /tmp/ficticios.db
dotnet run --project src/PapeleriaPos.Herramientas -- preparar-caja --base /tmp/ficticios.db
dotnet run --project src/PapeleriaPos.Servicio -- --environment Development --ConnectionStrings:Pos="Data Source=/tmp/ficticios.db"
```

`preparar-caja` deja tres usuarios de prueba (ver su README) con «Dar de alta y quitar cajas». En Development el
servicio escucha solo en `localhost:5080`: desde el emulador la caja principal es `10.0.2.2` (el anfitrión), que se
escribe a mano porque el UDP del emulador no llega a la PC. Para que el Pixel lo vea por Wi-Fi en la red de la casa,
`--Urls=http://*:5080`.

1. **El alta.** Ajustes › La conexión › escribir `10.0.2.2` › «Usar esta dirección» › el usuario y el PIN › el nombre y
   la serie propuestos (CEL) › «Dar de alta». La pantalla pasa a la conexión: la caja principal con la red «Buena» (no
   «Sin probar todavía»), «Celular de …» (serie CEL), «Bajando la copia completa…» y luego cuántos productos y de cuándo,
   en español aunque el celular esté en inglés («9 nov 2026, 8:47»). En el POS, Configuración › Cajas enseña el
   celular con su serie y su equipo.
2. **Precio y Buscar con la copia.** Con el emulador y un cartel (`-camera-back imagefile:`) de un código de la base
   ficticia, o con «Productos de prueba» apagado y un código escrito en Buscar. Un código que no está: «No está en el
   catálogo». Sin red (apagar el servicio): sigue igual.
3. **Los cambios.** Cambiar un precio en el POS (la caja de Windows o `api/productos`); al siguiente latido (30 s con la
   app al frente) la tarjeta ya lo trae y «De cuándo» cambia. `adb logcat` no debe traer nada de la copia ni de la llave.
4. **Quitar PC.** En el POS, Configuración › Cajas › «Quitar PC» del celular; al siguiente latido Ajustes dice «Este
   celular ya no tiene acceso…», sin copia, y Precio ya no encuentra nada hasta el próximo alta: un código que no está
   lo dice abajo, discreto («…Ajustes › La conexión»). El aviso sigue aunque llegue con la huella tapando todo o se
   cierre la app (`adb shell am force-stop`): al volver a abrir, La conexión lo dice; ya visto ahí, Precio deja de decirlo.
5. **La huella.** Con el emulador con PIN (`adb shell locksettings set-pin 1234`) y una huella
   (`adb -e emu finger touch 1` cuando el diálogo la pide): al abrir la app con alta sale «Entra con tu huella»; sin
   entrar, un código leído no vibra ni cambia nada; al entrar ya escanea. Volver a los 10 minutos del fondo pide otra
   vez; antes, no. Sin PIN en el celular, La conexión no deja dar de alta y abre la seguridad.
6. **Otra IP.** Arrancar el servicio en otro puerto (`--Urls=http://localhost:5180`) con la misma base; en el emulador
   no se encuentra solo (sin UDP): se prueba en la PC con `ConexionTest`, o en el Pixel con la PC cambiando de IP.
7. **En el Pixel, la medición:** cuánto tarda en armarse la copia de la tienda (los productos y el índice de la
   búsqueda) al abrir la app, con la sesión de prueba; y que la apertura hasta el primer cuadro analizado no empeore
   contra la [medición del 2026-11-07](../mediciones/2026-11-07-0.3.0/LEEME.md) (Macrobenchmark con el alta hecha).

Y en la tienda, un fin de semana con el dueño: el Pixel en el Wi-Fi, el alta en la caja principal (la de verdad; la serie
CEL no se usa para folios), y el precio y la búsqueda con los productos reales. Los datos de la tienda siguen fuera del
repo.

## Estrés

Ajustes › Herramientas de medición › **Pruebas de estrés** corre sobre la cámara real y dice si cada prueba pasa, con
su CSV:

- **Ciclos:** abrir → cámara abierta → primer cuadro → cerrar, 200 veces (con un controlador nuevo por ciclo o el mismo
  yendo y volviendo de segundo plano). Pasa si los ciclos con error son a lo más el 1 %, el p95 del final no pasa de
  1.2 veces el del inicio (sin el primero, que es frío) y la memoria, los hilos y los descriptores no crecen.
- **Ráfaga:** zoom alternado cada 30 ms durante 60 s y la linterna 100 veces; termina apagada y la interfaz coincide.
- **Cambio de motor** en caliente 50 veces.
- **Escaneo continuo** de 5, 15 o 30 minutos con el monitor de recursos: detecta el estrangulamiento térmico (estado
  térmico moderado o más, margen ≥ 0.85, cuadros de análisis < 85 % del primer minuto o CPU por cuadro +25 %).

En el emulador también hay pruebas instrumentadas de ciclos (`CiclosCamaraTest`) y de navegación de la app
(`NavegacionTest`: los tres modos, Ajustes y cada herramienta de medición, y de vuelta al escáner, que debe volver a
escanear; `ControladorCamaraTest` revisa que
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
