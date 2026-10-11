# Plan de la app Android

La app Android de [Papelería POS](https://github.com/Marioalr/papeleria-pos) para los celulares de la tienda: el
personal escanea con la cámara del celular lo que hoy pasa por el lector de la caja (contar, recibir mercancía,
consultar precios y, después, vender). Se instala a mano, sin publicarla en Google Play.

> **Estado: fase 1, el escáner.** Solo la lectura de códigos con la cámara: que lea bien, rápido y sin gastar
> batería, y que el personal sepa en todo momento qué está pasando. Todavía no se conecta al POS.

## Decisiones

| Tema | Decisión |
|---|---|
| Plataforma del escáner | Android nativo: Kotlin, Jetpack Compose y CameraX, en su propio módulo; la app de prueba de esta fase también es nativa |
| Celulares | Android 7.0 (API 24) o más nuevo; los del personal, de gama media o baja |
| Motor de lectura | ML Kit (incluido en la app, sin descargas) por defecto y zxing-cpp para comparar; se elige con la prueba en la tienda ([por qué](investigacion-escaner.md#1-motores-de-detección-recomendados)) |
| Guía para moverse | Un localizador propio sobre la imagen en gris dice dónde está el código, su tamaño, si está borroso y la luz, aunque todavía no se pueda leer ([diseño](escaner.md#localizador)) |
| Simbologías | EAN-13 (incluidos los internos que empiezan con 2), UPC-A, UPC-E, EAN-8, ITF-14, Code 128 (las claves de eleventa) y QR (los tickets); las de verificación débil (Code 39, Codabar) no se leen |
| Distribución | APK firmado que se instala en cada celular; GitHub Actions lo arma con cada versión |
| Idioma | Todo en español: documentos, código, commits y PRs, como en el POS |

**El escáner, nativo; el resto, como diga la fase 2.** El [plan del POS](https://github.com/Marioalr/papeleria-pos/blob/main/docs/plan.md)
dice «App Android: .NET MAUI con la misma interfaz» (la interfaz Blazor compartida con la caja y el panel). El escáner
es la parte que más depende de la cámara: analiza hasta 30 cuadros por segundo, cambia el zoom, el enfoque y la
linterna, y dibuja la guía encima del video. Aun dentro de MAUI eso sería código nativo (las bibliotecas de MAUI para
leer códigos envuelven a ML Kit o a ZXing); escrito directo en Kotlin se usan CameraX y ML Kit sin capa intermedia y se
mide y ajusta cada milisegundo. Por eso el escáner queda en su propio módulo (`escaner`), sin nada del POS, y en la
fase 2 se decide cómo se une sin rehacerlo:

- **MAUI con la interfaz Blazor del POS,** como dice su plan, y el escáner como biblioteca de Android (AAR) que abre su
  pantalla con un `Intent` y devuelve lo leído; o
- **la app completa nativa,** que habla con el servicio de la caja principal por la red de la tienda (como hoy el conteo
  desde el navegador del celular).

## Forma de trabajar

- **Las pruebas reales en la tienda, solo sábado y domingo** (regla del POS, decidida por el dueño el 2026-10-08:
  `docs/notas.md`, «Forma de trabajar»). Ahí va lo que necesita a la tienda: los celulares del personal, los productos
  de verdad (empaques brillantes, cajas de cartón, plumas con UPC-E), la luz del mostrador y el ruido.
- **El resto de la semana:** desarrollo, pruebas automáticas, el emulador y pruebas con cualquier código de barras
  (cualquier producto de la casa sirve; no hace falta la tienda). Se deja listo lo del fin de semana: el APK probado y
  la [guía](pruebas-en-tienda.md) con lo que hay que medir.
- **Ramas y PR:** ramas cortas `plan/<tema>` y `fase-N/<tema>`, un PR por parte; si una parte toca lo de la anterior,
  su rama sale de ella y los PR se fusionan en orden. Commits como «Fase 1, entrega 2, parte 1: …». El dueño fusiona.
- **Antes de cada PR:** `./gradlew test assembleDebug` y, si toca la cámara o la pantalla, una corrida en el emulador
  ([pruebas](pruebas.md)).

## Versiones

- `MAYOR.MENOR.PARCHE` en `app/build.gradle.kts` (`versionApp`); el código de versión de Android se deriva solo
  (`MAYOR·10000 + MENOR·100 + PARCHE`). Cada APK dice en la sesión de prueba con qué versión y commit se armó.
- **0.x** mientras sea de prueba; **1.0.0** cuando se use en la tienda conectada al POS.
- Al fusionar en `main` una entrega que se va a probar, se etiqueta `vX.Y.Z`: GitHub Actions corre las pruebas, arma el
  APK y crea la versión en GitHub con el APK adjunto. [CAMBIOS.md](../CAMBIOS.md) dice qué trae cada una.
- **Firma:** por ahora APK de depuración. La llave de depuración es distinta en cada máquina (y en Actions), así que
  para instalar un APK de otro origen hay que desinstalar antes el anterior. Antes de usarla en la tienda de verdad:
  una llave propia, fuera del repositorio, guardada como secreto de Actions.

## Fase 1: el escáner

Lo que el personal debe notar ([diseño](escaner.md)):

1. **Que ya está escaneando,** en cuanto abre la pantalla: la línea que barre la retícula aparece con el primer cuadro
   analizado, no antes.
2. **Qué hacer para que lea:** en cuanto hay un código a la vista, unas esquinas ámbar lo siguen, y si no se puede leer
   aparece una sola indicación clara: muévete a un lado, acércate, aléjate, mantén firme, enciende la linterna o
   inclina por el reflejo.
3. **Que ya leyó:** bip, vibración, retícula verde con palomita y el código en grande, todo en el mismo instante.
4. **Que abre al instante:** sacar el celular del bolsillo y escanear de inmediato, aun bloqueado (mosaico de Ajustes
   rápidos, ícono o gesto del celular), con cada paso de la apertura medido.

El criterio para cada decisión técnica es el máximo rendimiento medido (tiempo, CPU, memoria, batería y temperatura),
no lo que diga el plan del POS (lo decidió el dueño el 2026-10-09). Por eso la app trae sus propias herramientas de
medición: marcas de tiempo de la apertura, monitor de recursos, banco de pruebas con códigos borrosos, con reflejo y con
poca luz en grados crecientes, y pruebas de estrés de la cámara.

| # | Entrega | Qué trae |
|---|---|---|
| 1 | **Plan y diseño** | Este plan, el [diseño](escaner.md), la [investigación](investigacion-escaner.md), las [pruebas](pruebas.md) y la [guía del fin de semana](pruebas-en-tienda.md) |
| 2 | **Proyecto** | Gradle con los módulos `nucleo`, `escaner` y `app`, GitHub Actions, y en el núcleo: verificación de GTIN, codificadores, escenas sintéticas y geometría |
| 3 | **Detección** | Localizador, guía, validación y consenso; la cámara (CameraX) y los dos motores |
| 4 | **Experiencia** | La pantalla del escáner (estados, guía, confirmación con bip y vibración) y la app de prueba con modo venta y modo conteo |
| 5 | **Medición** | La sesión de prueba con su CSV, el banco de pruebas en el celular y las pruebas en el emulador |
| 6 | **Apertura y estrés** | Apertura inmediata (mosaico de Ajustes rápidos con el celular bloqueado, accesos directos, arranque optimizado con Baseline Profile), marcas de tiempo de cada paso, monitor de recursos, banco por grados de dificultad y pruebas de estrés de la cámara |

Después: la prueba en la tienda ([guía](pruebas-en-tienda.md)), calibrar los umbrales con lo medido, elegir el motor
y sacar la 0.2.

**Fuera de esta fase:** buscar el producto en el POS, vender, contar o recibir con el POS, el inicio de sesión y los
permisos por rol.

## Por definir

- **Cómo se une al POS** (fase 2): app nativa que habla con el servicio de la caja principal, o el escáner dentro de
  una app MAUI.
- **La llave de firma** para los APK de la tienda.
- **Cómo guarda eleventa los UPC.** El POS ya trata los códigos de 12 dígitos como UPC-A
  (`RegistroCompras.EsCodigoDeBarras`); el escáner entrega las claves de 12 y 13 dígitos (y las de 8 de un UPC-E) para que
  la búsqueda las encuentre de cualquier forma. Se confirma en la fase 2 con una copia de la base, sin tocar eleventa.
