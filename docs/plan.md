# Plan de la app Android

La app Android de [Papelería POS](https://github.com/Marioalr/papeleria-pos): con la cámara del celular, el personal
escanea lo que hoy pasa por el lector de la caja (contar, recibir mercancía, consultar precios y, después, vender).

> **El alcance de este repo: lo máximo en un Pixel 8 Pro.** Todavía no hay app Android del POS: el POS va primero
> (su plan). Mientras llega su fase de Android, aquí se exprime el escáner en un solo celular, el Pixel 8 Pro del dueño
> (Android 17, Tensor G3), y se mide todo. Con eso se decide después cómo se une al POS, con qué motor y umbrales, y qué
> necesitan los celulares del personal (decisión del dueño, 2026-10-09).

## Decisiones

| Tema | Decisión |
|---|---|
| Equipo de referencia | El Pixel 8 Pro del dueño: cada cifra y cada decisión técnica se toma ahí. Se aprovecha todo lo que tenga: sus tres cámaras, Android 17 y su CPU. La app sigue instalando desde Android 7.0 (API 24) mientras no estorbe, pero nada se limita por los celulares viejos o de gama baja; si algo del Pixel lo pide, se sube |
| Criterio | El máximo rendimiento medido: tiempo, CPU, memoria, batería y temperatura. No lo que diga el plan del POS. Un cambio se queda solo si mejora lo medido en el Pixel sin empeorar lo demás |
| Plataforma del escáner | Android nativo: Kotlin, Jetpack Compose y CameraX, en su propio módulo (`escaner`), sin nada del POS; la app de prueba también es nativa |
| Motor de lectura | El automático: zxing-cpp (~0.4 ms por cuadro), luego zxing con el umbral del localizador, y ML Kit de respaldo; ML Kit y zxing-cpp solos quedan para comparar ([diseño](escaner.md#motores)) |
| Guía para moverse | Un localizador propio sobre la imagen en gris dice dónde está el código, su tamaño, si está borroso y la luz, aunque todavía no se pueda leer ([diseño](escaner.md#localizador)) |
| Simbologías | EAN-13 (incluidos los internos que empiezan con 2), UPC-A, UPC-E, EAN-8, ITF-14, Code 128 (las claves de eleventa) y QR (los tickets); las de verificación débil (Code 39, Codabar) no se leen |
| Distribución | APK firmado que se instala con cable o desde la versión de GitHub. La llave (generada el 2026-10-09) vive fuera del repo y en GitHub como secretos; si se pierde, hay que desinstalar la app (y sus datos) para instalar la nueva |
| Idioma | Todo en español: documentos, código, commits y PRs, como en el POS |

**El escáner, nativo; cómo se une, cuando el POS llegue a Android.** El [plan del POS](https://github.com/Marioalr/papeleria-pos/blob/main/docs/plan.md)
dice «App Android: .NET MAUI con la misma interfaz» (la interfaz Blazor compartida con la caja y el panel). El escáner
es la parte que más depende de la cámara: analiza hasta 30 cuadros por segundo, cambia el zoom, el enfoque y la
linterna, y dibuja la guía encima del video. Aun dentro de MAUI eso sería código nativo (las bibliotecas de MAUI para
leer códigos envuelven a ML Kit o a ZXing); escrito directo en Kotlin se usan CameraX y ML Kit sin capa intermedia y se
mide y ajusta cada milisegundo. Por eso el escáner queda en su propio módulo y, cuando toque, se une sin rehacerlo:

- **MAUI con la interfaz Blazor del POS,** como dice su plan, y el escáner como biblioteca de Android (AAR) que abre su
  pantalla con un `Intent` y devuelve lo leído; o
- **la app completa nativa,** que habla con el servicio de la caja principal por la red de la tienda (como hoy el conteo
  desde el navegador del celular).

## Forma de trabajar

- **Todo se mide en el Pixel 8 Pro**, con el APK de release (el de depuración es más lento y falsea los tiempos). El
  emulador sirve para las pruebas automáticas y para no romper nada, no para las cifras. Macrobenchmark se corre a mano
  para no borrar los datos de la app ([pruebas](pruebas.md#arranque-y-perfil)).
- **Con cualquier código de barras, cualquier día:** productos de la casa, etiquetas impresas, la pantalla de la PC.
- **En la tienda, solo sábado y domingo** (regla del POS, decidida por el dueño el 2026-10-08: `docs/notas.md`, «Forma
  de trabajar»): con el Pixel, los productos de verdad (empaques brillantes, cajas de cartón, plumas con UPC-E), la luz
  del mostrador y el ruido ([guía](pruebas-en-tienda.md)).
- **Ramas y PR:** ramas cortas `plan/<tema>` y `fase-N/<tema>`, un PR por parte; si una parte toca lo de la anterior,
  su rama sale de ella y los PR se fusionan en orden. Commits como «Fase 1, entrega 2, parte 1: …». El dueño fusiona.
- **Antes de cada PR:** `./gradlew test assembleDebug` y, si toca la cámara o la pantalla, una corrida en el emulador
  ([pruebas](pruebas.md)); si promete ser más rápido, la medición en el Pixel contra la línea base.

## Versiones

- `MAYOR.MENOR.PARCHE` en `app/build.gradle.kts` (`versionApp`); el código de versión de Android se deriva solo
  (`MAYOR·10000 + MENOR·100 + PARCHE`). Cada APK dice en la sesión de prueba con qué versión y commit se armó.
- **0.x** mientras sea de prueba; **1.0.0** cuando se use en la tienda conectada al POS.
- Al fusionar en `main` una entrega que se va a probar, se etiqueta `vX.Y.Z`: GitHub Actions corre las pruebas, arma el
  APK de release firmado y crea la versión en GitHub con el APK adjunto. [CAMBIOS.md](../CAMBIOS.md) dice qué trae cada
  una.
- **Firma:** el release va firmado con la llave del proyecto (en la PC, `~/.config/papeleria-pos-android/`; en Actions,
  secretos). El de depuración lleva la llave de depuración de cada máquina: para pasar de uno al otro hay que
  desinstalar, y eso borra los datos de la app (exportar antes lo que importe).

## Fase 1: el escáner

Lo que quien escanea debe notar ([diseño](escaner.md)):

1. **Que ya está escaneando,** en cuanto abre la pantalla: la línea que barre la retícula aparece con el primer cuadro
   analizado, no antes.
2. **Qué hacer para que lea:** en cuanto hay un código a la vista, unas esquinas ámbar lo siguen, y si no se puede leer
   aparece una sola indicación clara: muévete a un lado, acércate, aléjate, mantén firme, enciende la linterna o
   inclina por el reflejo.
3. **Que ya leyó:** bip, vibración, retícula verde con palomita y el código en grande, todo en el mismo instante.
4. **Que abre al instante:** sacar el celular del bolsillo y escanear de inmediato, aun bloqueado (mosaico de Ajustes
   rápidos, ícono o gesto del celular), con cada paso de la apertura medido.

La app trae sus propias herramientas de medición: marcas de tiempo de la apertura, monitor de recursos, banco de pruebas
con códigos borrosos, con reflejo y con poca luz en grados crecientes, pruebas de estrés de la cámara y Macrobenchmark.

| # | Entrega | Qué trae |
|---|---|---|
| 1 | **Plan y diseño** | Este plan, el [diseño](escaner.md), la [investigación](investigacion-escaner.md), las [pruebas](pruebas.md) y la [guía del fin de semana](pruebas-en-tienda.md) |
| 2 | **Proyecto** | Gradle, GitHub Actions, y en el núcleo: verificación de GTIN, codificadores, escenas sintéticas y geometría |
| 3 | **Detección** | Localizador, guía, validación y consenso; la cámara (CameraX) y los motores |
| 4 | **Experiencia** | La pantalla del escáner (estados, guía, confirmación con bip y vibración) y la app de prueba con modo venta y modo conteo |
| 5 | **Medición** | La sesión de prueba con su CSV, el banco de pruebas en el celular y las pruebas en el emulador |
| 6 | **Apertura y estrés** | Apertura inmediata (mosaico de Ajustes rápidos con el celular bloqueado, accesos directos, arranque optimizado con Baseline Profile), marcas de tiempo de cada paso, monitor de recursos, banco por grados de dificultad y pruebas de estrés de la cámara |
| 7 | **Lo máximo en el Pixel 8 Pro** | Lo de abajo: cada experimento medido contra la línea base |

### Entrega 7: lo máximo en el Pixel 8 Pro

**Línea base** (2026-10-09, `v0.1.0`, Macrobenchmark con 15 iteraciones, medianas, con el perfil de arranque;
[detalle](pruebas.md#arranque-y-perfil)):

| Medida | Línea base |
|---|---|
| Frío: ya escaneando (primer cuadro analizado) | 629 ms (primer cuadro en pantalla: 225 ms) |
| Tibio: ya escaneando | 386 ms |
| Caliente (vuelve de segundo plano): ya escaneando | 278 ms, de ellos ~200 ms reabrir la cámara |
| Por cuadro: total / localizador / lectura | 2.4 / 0.6 / 1.8 ms |

Lo que falta medir para completar la línea base: del bolsillo al bip por cada camino (mosaico, gesto, ícono, pantalla
apagada), la lectura de lo difícil (borroso, reflejo, poca luz, chico, lejos) con productos reales, y la batería y la
temperatura en 30 minutos de escaneo continuo.

**Experimentos**, en este orden (cada uno se queda solo si mejora lo medido sin empeorar lo demás):

1. **El camino crítico de la apertura en frío.** Ya hay 90 trazas de Perfetto del Pixel: ver qué espera a qué entre el
   proceso, CameraX, el dispositivo de la cámara y el primer cuadro, y adelantar o quitar lo que sobre (por ejemplo,
   empezar a abrir la cámara desde el mosaico o desde `Application`). **Hecho el 2026-10-09** (abajo).
2. **La vuelta en caliente.** Casi todo es reabrir la cámara: medir dejarla abierta unos segundos al salir (y lo que
   cuesta en batería) o reabrirla antes.
3. **Las cámaras del Pixel.** Principal, ultra gran angular (enfoca muy de cerca) y teleobjetivo 5x: ver qué expone
   CameraX (cámara lógica y zoom de 0.5x en adelante, cámaras físicas con Camera2), y usar la de cerca para códigos
   chicos y el zoom para los lejanos.
4. **Resolución y cuadros por segundo.** Con 2.4 ms por cuadro sobra margen: probar más resolución para códigos chicos
   o lejanos y 60 cuadros por segundo (menos tiempo hasta leer y menos movido).
5. **Exposición y enfoque.** Exposición más corta para que no salga movido, medición de la luz y enfoque en la franja,
   linterna automática.
6. **Motores.** Con el margen de CPU, ML Kit en más cuadros o en paralelo con zxing: medir la lectura de lo difícil en
   el banco «Límites» y con productos reales.
7. **Del bolsillo al bip.** Mosaico sobre el bloqueo, Toque rápido del Pixel, ícono y pantalla apagada con el escáner
   abierto, cronometrados de punta a punta.
8. **Batería y temperatura.** Escaneo continuo de 30 minutos con el monitor de recursos, antes y después de cada cambio
   que gaste más (resolución, 60 cuadros por segundo, ML Kit en más cuadros).

**Resultado del experimento 1** (las 90 trazas del Pixel, `herramientas/trazas.sh`; ms desde que el sistema lanza
la Activity, medianas con perfil):

| Arranque | Cámara abierta | Sesión de la cámara | Primer cuadro analizado | Cámara abierta esperando la sesión |
|---|---|---|---|---|
| Frío | 235 | 396 | 575 | **160** |
| Tibio | 134 | 186 | 374 | 61 |
| Caliente | 78 | 80 | 307 | 2 |

- **En frío y tibio, lo que más estorba es el visor.** La cámara ya está abierta y espera 160 ms (61 en tibio) a que
  la app cree la sesión, y la sesión necesita la superficie del visor (SurfaceView). La superficie llega hasta que se
  dibuja la interfaz completa. El primer cuadro es negro a propósito, y el segundo compone todo: ~40 ms de medir texto
  y ~95 ms de la GPU. CameraX no deja crear la sesión antes: CameraPipe sabe usar superficies diferidas, pero CameraX
  marca la de la vista previa como `Preview` y no como `SurfaceHolder`, y no hay API para cambiarlo. **Siguiente
  cambio:** que el visor esté desde el primer cuadro y lo demás de la interfaz llegue después (se calcula ~150 ms menos
  en frío y ~60 en tibio).
- **Los ~95 ms de GPU son de compilar shaders** (`CreateGraphicsPipeline-CompileAfterCacheMiss`): Macrobenchmark borra
  la caché de shaders antes de cada arranque frío. Con el celular de todos los días (sacarlo del bolsillo) esa caché
  está en disco y no se paga. Falta un caso de Macrobenchmark de **frío con caché** para medir eso.
- **En caliente no hay espera, pero el primer cuadro tarda ~170 ms después de la sesión.** El HAL del Pixel arma su
  procesamiento completo para la vista previa: segmentación, rostros, saliencia, reducción de ruido temporal y 76
  buffers. Además, la cámara lógica enciende los tres sensores. Va a los experimentos 3 (una sola cámara física), 4
  (más cuadros por segundo) y 5 (sugerencias de uso del flujo al HAL).
- **Mientras la cámara abre, la interfaz dibuja ~30 cuadros** (~90 ms de GPU en caliente) sin que nada se vea
  cambiar. Hay que encontrar qué los pide.
- **Lo que no vale la pena:** `bindApplication` (~55 ms) es del sistema (cargar el dex y los recursos). CameraX se
  inicializa en paralelo (~35 ms). Los sonidos se decodifican con MediaCodec a los ~200 ms, en otros hilos; se revisa
  después del visor.

**Fuera de esta fase:** buscar el producto en el POS, vender, contar o recibir con el POS, el inicio de sesión y los
permisos por rol, y adaptar el escáner a los celulares del personal.

## Cuando el POS llegue a su fase de Android

Con lo medido en el Pixel se decide:

- **Cómo se une al POS:** app nativa que habla con el servicio de la caja principal, o el escáner como AAR dentro de
  una app MAUI.
- **El motor y los umbrales** de la guía, con los CSV del Pixel en la tienda.
- **Qué celulares necesita el personal:** cuánto de lo logrado depende de un celular como el Pixel (cámaras, CPU,
  Android reciente) se mide entonces en los celulares del personal, con las mismas herramientas.

## Por definir

- **Cómo guarda eleventa los UPC.** El POS ya trata los códigos de 12 dígitos como UPC-A
  (`RegistroCompras.EsCodigoDeBarras`); el escáner entrega las claves de 12 y 13 dígitos (y las de 8 de un UPC-E) para que
  la búsqueda las encuentre de cualquier forma. Se confirma cuando se una al POS, con una copia de la base, sin tocar
  eleventa.
