# Plan de la app Android

La app Android de [Papelería POS](https://github.com/Marioalr/papeleria-pos): con la cámara del celular, el personal
escanea lo que hoy pasa por el lector de la caja (contar, recibir mercancía, consultar precios y, después, vender).

> **El alcance de este repo: lo máximo en un Pixel 8 Pro.** Todavía no hay app Android del POS: el POS va primero
> (su plan). Mientras llega su fase de Android, aquí se exprime el escáner en un solo celular, el Pixel 8 Pro del dueño
> (Android 17, Tensor G3), y se mide todo. Con eso se decide después cómo se une al POS, con qué motor y umbrales, y qué
> necesitan los celulares del personal (decisión del dueño, 2026-10-09).
>
> **Desde el 2026-10-11, la meta es medir la lectura** ([entrega 8](#entrega-8-medir-la-lectura)): qué lee bien este
> celular, a qué distancia y a qué ritmo, sin una sola lectura falsa. Con eso se decide si el celular basta para contar
> y para la preventa o hace falta un lector, y de qué tamaño imprime el POS sus etiquetas. La interfaz se congela hasta
> tener esas mediciones.

## Decisiones

| Tema | Decisión |
|---|---|
| Equipo de referencia | El Pixel 8 Pro del dueño: cada cifra y cada decisión técnica se toma ahí. Se aprovecha todo lo que tenga: sus tres cámaras, Android 17 y su CPU. La app sigue instalando desde Android 7.0 (API 24) mientras no estorbe, pero nada se limita por los celulares viejos o de gama baja; si algo del Pixel lo pide, se sube |
| Criterio | El máximo rendimiento medido: tiempo, CPU, memoria, batería y temperatura. No lo que diga el plan del POS. Un cambio se queda solo si mejora lo medido en el Pixel sin empeorar lo demás |
| Plataforma del escáner | Android nativo: Kotlin, Jetpack Compose y CameraX, en su propio módulo (`escaner`), sin nada del POS; la app de prueba también es nativa |
| Motor de lectura | El automático: zxing-cpp (~0.4 ms por cuadro), luego zxing con el umbral del localizador, y ML Kit de respaldo; ML Kit y zxing-cpp solos quedan para comparar ([diseño](escaner.md#motores)). Cuál queda se decide con la medición lado a lado, sobre los mismos cuadros ([entrega 8](#entrega-8-medir-la-lectura)) |
| Guía para moverse | Un localizador propio sobre la imagen en gris dice dónde está el código, su tamaño, si está borroso y la luz, aunque todavía no se pueda leer ([diseño](escaner.md#localizador)) |
| Simbologías | Solo EAN-13 (incluidos los internos del POS, que empiezan con 2), UPC-A, UPC-E, EAN-8, ITF (los GTIN-14), Code 128 (las claves con letras, que el POS imprimirá en etiquetas) y QR (el folio del ticket). Menos formatos activos, menos lecturas falsas y más velocidad; las de verificación débil (Code 39, Codabar) no se leen |
| Exactitud | Una lectura falsa es peor que una lenta: la meta es **0 falsas**. En 1D se acepta solo con 2 lecturas iguales seguidas y con su dígito verificador, nunca de un solo cuadro para ganar velocidad; un código del catálogo con el verificador mal no se lee, y no se «corrige» |
| Red y privacidad | La app no usa la red: no manda cuadros, fotos ni códigos a ningún servidor, sin estadísticas, telemetría ni anuncios. Permisos: solo cámara y vibración (cada APK lo revisa al armarse). Lo medido se guarda en el celular y se exporta a mano; fotos, nunca por omisión |
| Datos de la tienda | No entran al repositorio, ni a commits ni a PRs: los ejemplos y las pruebas usan códigos ficticios (EAN-13 válidos que empiezan con 750999). Para probar con los códigos reales, el dueño los exporta a un archivo fuera del repo y la app lo carga en el celular. Nada se conecta al servicio de la tienda ni a eleventa |
| Distribución | APK firmado que se instala a mano, con cable o desde la versión de GitHub; no va a Play Store. La llave (generada el 2026-10-09) vive fuera del repo y en GitHub como secretos; si se pierde, hay que desinstalar la app (y sus datos) para instalar la nueva |
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
3. **Que ya leyó:** vibración, retícula verde con palomita y el código en grande, todo en el mismo instante. El bip
   viene apagado (decisión del dueño, 2026-10-11) y se enciende en Ajustes.
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
| 8 | **Medir la lectura** | Lo necesario para decidir celular o lector y el tamaño de las etiquetas ([abajo](#entrega-8-medir-la-lectura)) |

### Entrega 7: lo máximo en el Pixel 8 Pro

**Línea base** (2026-10-09, `v0.1.0`, Macrobenchmark con 15 iteraciones, medianas, con el perfil de arranque;
[detalle](pruebas.md#arranque-y-perfil)):

| Medida | Línea base |
|---|---|
| Frío: ya escaneando (primer cuadro analizado) | 629 ms (primer cuadro en pantalla: 225 ms) |
| Tibio: ya escaneando | 386 ms |
| Caliente (vuelve de segundo plano): ya escaneando | 278 ms, de ellos ~200 ms reabrir la cámara |
| Por cuadro: total / localizador / lectura | 2.4 / 0.6 / 1.8 ms |

**Lo mejor medido hasta ahora** (se actualiza con cada cambio que se queda; cada medición, con su APK y sus trazas, en
[mediciones](../mediciones/)):

| Medida | Línea base | Mejor | Con qué |
|---|---|---|---|
| Frío: ya escaneando | 629 ms | **532 ms** | El visor desde el primer cuadro (2026-10-09) |
| Tibio: ya escaneando | 386 ms | 386 ms | |
| Caliente: ya escaneando | 278 ms | 278 ms | |
| Por cuadro | 2.4 ms | 2.4 ms | |

En el Pixel queda instalada siempre la versión más rápida medida, con su perfil compilado.

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
   chicos y el zoom para los lejanos. **Para abrir, medido el 2026-10-10:** una sola física no gana. CameraX abre la
   lógica y el HAL enciende los mismos tres sensores ([medición](../mediciones/2026-10-10-lente-principal/LEEME.md)).
   Queda «Lente» en ajustes; falta ver si la gran angular (enfoca a 2 cm) lee mejor los códigos chicos de cerca.
4. **Resolución y cuadros por segundo.** Con 2.4 ms por cuadro sobra margen: probar más resolución para códigos chicos
   o lejanos y 60 cuadros por segundo (menos tiempo hasta leer y menos movido). **60 cuadros, medido el 2026-10-10:** no
   se queda como predeterminado. El HAL solo tarda ~15 ms menos en dar el primer cuadro, en frío no gana, y cada cuadro
   cuesta más ([medición](../mediciones/2026-10-10-60fps/LEEME.md)). Queda en ajustes para ver si lee mejor códigos en
   movimiento. Falta la resolución.
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
  marca la de la vista previa como `Preview` y no como `SurfaceHolder`, y no hay API para cambiarlo. **Hecho:**
  el visor sale desde el primer cuadro y lo demás llega cuando el visor ya entregó su superficie. En frío, la espera
  bajó de 160 a 42 ms y ya escaneando de 629 a 532 ms ([medición](../mediciones/2026-10-09-visor-primero/LEEME.md)).
  En tibio no cambió: la espera bajó de 61 a 18 ms, pero ahí manda el HAL.
- **Los ~95 ms de GPU son de compilar shaders** (`CreateGraphicsPipeline-CompileAfterCacheMiss`): Macrobenchmark borra
  la caché de shaders antes de cada arranque frío. Con el celular de todos los días (sacarlo del bolsillo) esa caché
  está en disco y no se paga. Falta un caso de Macrobenchmark de **frío con caché** para medir eso.
- **Ahora el límite en los tres arranques es el HAL:** ~150 ms desde que la sesión está lista hasta el primer cuadro.
  En caliente nunca hubo espera, y el primer cuadro tarda ~170 ms después de la sesión. El HAL del Pixel arma su
  procesamiento completo para la vista previa: segmentación, rostros, saliencia, reducción de ruido temporal y 76
  buffers. Además, la cámara lógica enciende los tres sensores. Va a los experimentos 3 (una sola cámara física), 4
  (más cuadros por segundo) y 5 (sugerencias de uso del flujo al HAL).
- **Mientras la cámara abre, la interfaz dibuja ~30 cuadros** (~90 ms de GPU en caliente) sin que nada se vea
  cambiar. Hay que encontrar qué los pide.
- **Lo que no vale la pena:** `bindApplication` (~55 ms) es del sistema (cargar el dex y los recursos). CameraX se
  inicializa en paralelo (~35 ms). Los sonidos se decodifican con MediaCodec a los ~200 ms, en otros hilos; se revisa
  después del visor.

### Entrega 8: medir la lectura

Para decidir con datos (dueño, 2026-10-11): si el celular basta para contar y para la preventa o hace falta un lector, y
de qué tamaño se imprimen las etiquetas del POS. **La interfaz se congela:** solo se agrega lo necesario para medir, y
nada de preventa ni conteo de verdad hasta tener las mediciones. Los experimentos 3 a 6 de la entrega 7 (lentes,
resolución, enfoque, motores) siguen aquí, medidos por lo que leen y no por lo que tardan en abrir.

| # | Parte | Qué trae |
|---|---|---|
| 1 | **Sin red** | Fuera los permisos de Internet y de estado de red, y el envío de estadísticas de ML Kit (datatransport), que entraban con la biblioteca; cada APK revisa al armarse que pide solo cámara y vibración. El bip, apagado por omisión |
| 2 | **Códigos ficticios** | Los ejemplos y las pruebas del repo, con EAN-13 válidos que empiezan con 750999 (y UPC, EAN-8 e ITF igual de ficticios) |
| 3 | **Aceptar sin falsas** | En 1D, 2 lecturas iguales seguidas con su dígito verificador; el QR, de un cuadro (trae su propia corrección de errores). El mismo código no vuelve a contar hasta que sale de la mira o pasan 1.5 s |
| 4 | **Búsqueda en el catálogo** | La búsqueda prueba, en este orden y sin otra corrección: el código tal cual; el UPC-A sin el 0 inicial (11 dígitos); el EAN-13 que empieza con 0 como UPC-A de 12 y luego de 11; el UPC-E expandido a UPC-A. Es la misma regla que usará el servicio del POS. Un catálogo de ejemplo ficticio, y cargar en el celular el archivo que exporte el dueño |
| 5 | **Hoja de etiquetas** | Una hoja para imprimir al 100 % con Code 128 y EAN-13 ficticios, con módulos de 0.25, 0.33, 0.4 y 0.5 mm y varias alturas: dice desde qué tamaño lee bien el Pixel |
| 6 | **Ficha de la cámara** | Al primer arranque, por cada lente física: distancia mínima de enfoque, focal, modos de enfoque, rango de zoom, resoluciones y calibración del enfoque. Con eso se elige el lente: para códigos chicos puede servir más la principal con 1.5–2× a 12–15 cm que acercarse |
| 7 | **Registro por intento** | Por intento: celular, lente, zoom, resolución, motor, simbología, ms hasta la primera lectura aceptada, ms por cuadro, linterna, distancia (por el enfoque, si la cámara la da calibrada) y resultado (bien, nada o falsa). Al final de cada tanda, un resumen corto |
| 8 | **Más resolución** | Análisis a 1920×1080 o más: solo el plano Y, recortado a la franja (ancha para 1D) en vez de reducir el cuadro; solo el último cuadro, sin cola; las dos orientaciones |
| 9 | **Lado a lado** | ML Kit (con el modelo incluido en la app) y zxing-cpp sobre los mismos cuadros: velocidad y aciertos de cada uno |
| 10 | **Enfoque y luz en la mira** | Enfoque continuo, más un disparo de enfoque y medición de la luz en la región de la mira; linterna a mano |

**Qué se mide** (en casa con la [hoja de etiquetas](pruebas.md#hoja-de-etiquetas), cualquier día; en la tienda, sábado y
domingo):

- Distancia mínima y máxima por tamaño de código, y ángulo.
- Plástico brillante y empaques curvos (plumas, marcadores), EAN-8 chico, código arrugado o gastado.
- Luz de la tienda contra luz baja, con y sin linterna.
- Ráfaga de conteo: muchos productos seguidos sin tocar la pantalla, cuántos por minuto.

**El resumen de cada tanda:** qué lee bien y a qué distancia, lecturas por minuto en la ráfaga, lecturas falsas (deben
ser 0), si la linterna ayuda y qué lente conviene.

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

- **Cómo guarda eleventa los UPC.** Resuelto el 2026-10-11: la búsqueda prueba las variantes de la parte 4 de la
  [entrega 8](#entrega-8-medir-la-lectura), la misma regla que usará el servicio del POS.
