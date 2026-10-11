# El escáner

Cómo lee la app los códigos de barras con la cámara y qué ve, oye y siente quien escanea mientras tanto. Las razones y
las fuentes de cada número están en la [investigación](investigacion-escaner.md); los umbrales son puntos de partida
que se calibran en el Pixel 8 Pro, el equipo de referencia ([plan](plan.md#entrega-7-lo-máximo-en-el-pixel-8-pro)), en
casa y en la tienda ([pruebas en tienda](pruebas-en-tienda.md)).

## Qué tiene que lograr

| Meta | Cómo se mide |
|---|---|
| **Cero lecturas incorrectas aceptadas** (cobrar otro producto es peor que no leer) | Sesión de prueba: resultado «lectura incorrecta» |
| Que el personal sepa al instante que ya escanea | La línea de la retícula aparece con el primer cuadro analizado; arranque en frío a ese cuadro: 629 ms en el Pixel (línea base) y bajando |
| Abrir al instante, aun con el celular bloqueado | Marcas de cada paso de la apertura ([apertura](#apertura)); del bolsillo al bip con cronómetro en la tienda |
| Leer rápido | Del código bien colocado al bip: p50 ≤ 250 ms y p95 ≤ 500 ms en el Pixel; del resultado al bip, la vibración y el verde ≤ 50 ms |
| Leer casi todo al primer intento | ≥ 98 % de los intentos leídos en ≤ 2 s |
| Gastar poco | CPU de la app < 35 % de un núcleo escaneando; el localizador < 3 ms por cuadro (p95); la cámara se apaga sola si nadie escanea |

## Módulos

```
app ──► escaner ──► nucleo
 │        │           └─ Kotlin puro (se prueba en la JVM): GTIN, codificadores, escenas sintéticas,
 │        │              geometría, localizador, guía, validación y consenso, búsqueda en el catálogo
 │        └─ Android: cámara (CameraX), motores (ML Kit, zxing-cpp), pantalla del escáner, bip y vibración
 └─ la app de prueba: modo venta y modo conteo, ajustes, catálogo de prueba, sesión de prueba (CSV) y
    banco de pruebas
```

El módulo `escaner` no sabe nada del POS: recibe una configuración y entrega lecturas (`ControladorEscaner`). Así se
puede usar desde la app nativa o como biblioteca desde otra app ([plan](plan.md#decisiones)).

## Cada cuadro

La cámara entrega hasta 30 cuadros por segundo (60 en ajustes); el análisis toma siempre el más reciente y descarta los que llegan
mientras trabaja (`STRATEGY_KEEP_ONLY_LATEST`), así nunca se atrasa.

1. **Localizar** en el cuadro completo (~0.2 ms): dónde hay barras, su tamaño en px por módulo, nitidez, luz, reflejo y
   el nivel que separa barras de espacios.
2. **Leer** la franja de la retícula con el motor automático: zxing-cpp tal cual, zxing-cpp con el umbral del
   localizador y, solo si el localizador ve un código que no sale, ML Kit ([motores](#motores)).
3. **Validar y confirmar:** dígito verificador, dos lecturas iguales en cuadros seguidos (en 1D) y el filtro de
   repetidos.
4. **Guiar:** de lo localizado, lo que vio el motor y el enfoque sale una sola indicación, sin parpadeos.
5. **Avisar:** el estado nuevo va a la pantalla; una lectura aceptada dispara el bip, la vibración y el verde.

## Coordenadas

Todo lo que cruza entre módulos va en la **región visible normalizada**: de 0 a 1, derecha, tal como se ve en
pantalla (`Caja` en `nucleo/Geometria.kt`). La sesión de la cámara lleva un `ViewPort` del tamaño del visor, así que el
`cropRect` de cada cuadro es justo la parte visible; `Transformacion` pasa entre los píxeles del buffer (sin rotar) y
esa región. Los motores y el localizador trabajan en el buffer; la franja, la guía y la pantalla, en la región visible.
Dibujar es multiplicar por el tamaño del visor.

## Cámara

| Qué | Cómo | Por qué |
|---|---|---|
| Resolución | 1280×720 (16:9) para el visor y, por omisión, el análisis. En ajustes, el análisis a 1080p, 1440p o 4K (la más cercana que dé la cámara); el visor se queda en 720p | Bastan ≥ 2 px por módulo y 1080p cuesta ~30 % más por cuadro. Más resolución da más píxeles a los códigos chicos o lejanos: parte 8 del [plan](plan.md#entrega-8-medir-la-lectura), se mide en la tienda (la sesión guarda la de cada intento). Como solo se analiza el plano Y recortado a la franja, y siempre el último cuadro, el costo crece con la franja |
| Formato | YUV_420_888, sin rotar la salida | Se usa solo el plano Y; rotar cuesta 10–15 ms por cuadro |
| Cuadros por segundo | 30 fijos (exposición corta, menos movido). En ajustes: variable desde 15 (más luz) o hasta 60 donde la cámara los da | 60 en el Pixel 8 Pro: abre casi igual y cada cuadro cuesta más (experimento 4 del [plan](plan.md#entrega-7-lo-máximo-en-el-pixel-8-pro)); falta ver si lee mejor códigos en movimiento |
| Enfoque | Continuo; al abrir, al tocar y con un código que no se lee en 700 ms, enfoque al punto (se suelta solo a los 2 s) | Bloquear el enfoque falla al cambiar de distancia |
| Zoom inicial | Calculado con la distancia mínima de enfoque del lente (1.5× si no la reporta), tope 2× | Muchos celulares no enfocan a menos de 10–15 cm: con zoom se lee desde más lejos |
| Zoom propio | Si el código se ve chico (< 2 px por módulo), ×1.4 hasta el tope; regresa al leer | Antes de pedir «acércate» |
| Linterna | Botón que pulsa si hay poca luz (≥ 1 s); automática como ajuste | Nunca se apaga por la luz, porque la propia linterna la sube |
| Arranque | CameraX se prepara al abrir la app y solo con la cámara trasera | Abre más rápido |

## Ficha de la cámara

Para elegir con qué lente leer ([plan](plan.md#entrega-8-medir-la-lectura), parte 6), la app lee de Camera2 cada lente
trasera: la cámara lógica y, desde API 28, cada una de sus físicas (en el Pixel 8 Pro, la 2, 3 y 4 de la 0). De cada una,
`app/camara/FichaCamara.kt`: nivel de hardware, focal, apertura, sensor (mm y píxeles), `LENS_INFO_MINIMUM_FOCUS_DISTANCE`
y si viene calibrada, hiperfocal, modos de enfoque, rango de zoom, resoluciones para el análisis (YUV) y para el visor,
rangos de cuadros por segundo, linterna y capacidades.

Con eso, por lente, tres cuentas con el modelo de agujero y el recorte 16:9 (a lo ancho del celular en vertical va 9/16
del lado largo del sensor; el análisis pone ahí 720 px):

- **Cuánto abarca donde ya enfoca:** campo = (lado largo del sensor × 9/16) × distancia ÷ focal. La principal del Pixel
  (6.9 mm, 9.8 mm de sensor, enfoca desde 10.5 cm) abarca 8.4 cm ahí; la gran angular (2 mm, enfoca desde 2 cm), 3.2 cm.
- **Píxeles por módulo ahí:** 720 × módulo ÷ campo. Un módulo de 0.33 mm da 2.8 px en la principal a 10.5 cm y 7.5 px en
  la gran angular a 2 cm: para los códigos chicos, la gran angular de cerca o la principal con zoom.
- **Con qué zoom abre la app:** la misma cuenta del [zoom inicial](#cámara) (`zoomSugerido`): un código de 30 mm llena
  el 60 % del ancho a 6.3 cm con la principal, más cerca de lo que enfoca, así que abre con 1.7×.

Se lee una vez por proceso, fuera del hilo principal y nunca en la apertura (con el primer cuadro analizado, o al abrir
Ajustes); no abre la cámara ni necesita su permiso. Queda en `files/camara/ficha-camara.csv` (una fila por lente, con la
huella del sistema; se rehace si cambia), se ve en Ajustes junto al lente y sale con la medición de la sesión.

## Motores

Medido en el emulador con códigos dibujados (prueba `MotoresTest`), por cuadro:

| | zxing-cpp | ML Kit |
|---|---|---|
| Franja (vertical, 462×530) | 0.35 ms | 101 ms |
| Lo visible (576×1280) | 0.86 ms | 55 ms |
| ITF-14 | Lo lee | No lo lee: da ceros o 40 dígitos de basura (los rechaza la validación) |
| Etiqueta blanca sobre fondo gris | Su binarizador local no la lee | La lee |

Por eso el motor por defecto es el **automático**, con hasta tres pasadas por cuadro, de la más barata a la más cara:

1. **zxing-cpp sobre la franja**, tal cual (`minLineCount = 2`, sin `tryHarder`: el siguiente cuadro es el reintento).
   En ajustes, «Leer también girados 90°» (`tryRotate`) prueba las dos orientaciones de la franja, para los códigos de
   pie (una pluma) sin esperar el turno de ML Kit; apagado hasta medir lo que cuesta por cuadro.
2. Si no leyó y el localizador midió el nivel entre barras y espacios: **zxing-cpp sobre la franja binarizada con ese
   umbral** (~1 ms). Rescata la etiqueta blanca sobre cartón o empaque de color, el caso común en la tienda; con un cuadro
   real del emulador, sin esta pasada no se leía y con ella sí.
3. Si tampoco y hay un código probable (el localizador lo ve, o hubo uno hace menos de 1 s): **ML Kit** (incluido en la
   app, 17.3.0) sobre **todo lo visible**, a lo más uno de cada dos cuadros para que la guía siga fluida. Si lee algo en
   su turno, corre también en el cuadro siguiente, porque la segunda lectura tiene que ser de un cuadro seguido
   ([consenso](#validación-y-consenso)); esa corrida toma el turno que seguía, así que en promedio sigue en uno de cada
   dos. Con el celular vertical a ML Kit le cuesta menos lo visible que la franja, y le da contexto a un código difícil
   (borroso, girado).

En la prueba de punta a punta del emulador, 8 de 10 lecturas salieron por la segunda pasada (150–500 ms desde que
apareció el código) y 2 por ML Kit (el código girado y el pegado abajo, 270–600 ms). En ajustes se puede fijar un
solo motor (ML Kit o zxing-cpp) para compararlos; el de la tienda se decide con las mediciones ([pruebas en
tienda](pruebas-en-tienda.md)). Solo se piden las simbologías que se usan, nunca «todas». Cada lectura dice por cuál
vía salió (directa, umbral o respaldo) en el registro y en la sesión de prueba.

**Lado a lado** (parte 9 del [plan](plan.md#entrega-8-medir-la-lectura)): el motor «Lado a lado» de ajustes corre
zxing-cpp (directo y con umbral) y ML Kit en cada cuadro sobre la misma región, y lee lo que lea cualquiera (zxing-cpp
primero). Anota por cuadro qué leyó cada uno y cuánto tardó; cada lectura aceptada lleva a la sesión de prueba, para los
cuadros de su candidato, en cuántos leyó cada motor el código, en cuántos leyó otra cosa, a los cuántos ms lo leyó por
primera vez y su mediana por cuadro (columnas `zxing_*` y `mlkit_*` de intentos.csv), y el resumen de la tanda lo junta
por motor. El diagnóstico muestra lo mismo de los últimos 30 cuadros. Es más lento que el automático (ML Kit en todos
los cuadros): para medir, no para la tienda.

## Localizador

`nucleo/Localizador.kt`. Sobre el plano Y, en celdas de 32×32 px muestreadas cada 4 px: el tensor de estructura de cada
celda dice si sus bordes van todos en la misma dirección (barras). Las celdas vecinas con la misma dirección forman la
región; tres líneas de escaneo a través de las barras dan el ancho del módulo, la nitidez de los bordes, cuántas
transiciones hay y si las barras siguen de una línea a otra. Solo cuenta como código si pasa esas pruebas
(«verificado») y se repite en dos cuadros seguidos: el texto y las hojas rayadas no engañan a la guía.

Con desenfoque o reflejo se pierden transiciones y la verificación falla, pero las barras siguen siendo coherentes,
continuas de una línea a otra y con la proporción de oscuro y claro de un código. Ese **indicio fuerte** (coherencia ≥
0.95, continuidad ≥ 0.9 y proporción entre 0.35 y 2.8, o un reflejo) también cuenta como código si dura tres cuadros: así
un código borroso pide «Mantén firme» en vez de «Apunta». La hoja rayada (proporción ~0.17) y el texto (continuidad <
0.3) no lo cumplen. Lo que ML Kit marca como «potencial» solo cuenta si el localizador lo respalda: ML Kit también ve
potenciales en hojas rayadas y en texto.

## Guía

Una sola indicación a la vez, en este orden de prioridad:

| Indicación | Cuándo | Texto |
|---|---|---|
| Reflejo | > 6 % de la región saturada (sale < 3 %) | «Hay reflejo: inclina un poco el celular» |
| Más luz | Brillo medio < 45 durante ≥ 500 ms (sale > 60) | «Poca luz: enciende la linterna» |
| Aléjate | El código ocupa > 90 % de la franja (sale < 80 %), está cortado por los dos lados, o está borroso y el lente ya está en su enfoque más cercano o el código ocupa > 75 % | «Aléjate un poco» |
| Muévete | El centro del código se desvía > 20 % de la franja (sale < 12 %), o toca un solo borde | «Mueve el celular a la izquierda» (hacia donde se ve el código) |
| Acércate | < 2 px por módulo (sale > 2.5), o < 30 % de la franja si no se puede medir; solo si el zoom propio ya está al tope | «Acércate» |
| Mantén firme | Borroso (nitidez < 0.15, sale > 0.22) o movido; si el lente está enfocando, «Enfocando…» | «Mantén firme» |
| Ya leído | El código a la vista ya se contó: no ha salido de la mira ni han pasado 1.5 s | «Ya leído: retíralo para leerlo otra vez» |
| Leyendo | Bien colocado | «Leyendo…» |
| Apunta | Nada a la vista | «Apunta al código de barras» |

**Sin parpadeos:** los valores se suavizan entre cuadros; cada condición tiene un umbral para entrar y otro para salir;
una indicación nueva tiene que sostenerse 3 cuadros y 300 ms, y la que está se queda al menos 700 ms salvo que entre
una de mayor prioridad. Al aparecer un código nuevo hay 500 ms sin pistas de dirección para que enfoque. «Leído» es
inmediato.

Dónde está el código, en este orden: lo leído, lo que ML Kit vio sin leer (si el localizador lo respalda), y lo que el
localizador verificó o ve con indicio fuerte. Si no hay nada de eso, no se dan direcciones.

Las condiciones de un solo cuadro (reflejo, movimiento, tocar un borde, cortado) cuentan solo si duran tres cuadros
seguidos: si una va y viene, no debe impedir que se confirme lo demás. La proporción de enfoque (`LENS_FOCUS_DISTANCE` /
`LENS_INFO_MINIMUM_FOCUS_DISTANCE`) no puede pasar de 1 en un equipo calibrado; si pasa de 1.2 (el emulador da 10–20) se
toma como desconocida. El ancho del módulo no decide «aléjate» con el código borroso: el desenfoque lo infla. Esa misma
distancia de enfoque, en cm (100 / dioptrías), va en cada lectura para la sesión de prueba, solo si el lente en uso da
el enfoque calibrado (`LENS_INFO_FOCUS_DISTANCE_CALIBRATION` aproximado o calibrado) y el dato es creíble; con la mira
sobre el código es la distancia al código.

## Validación y consenso

Una lectura falsa es peor que una lenta (decisión del dueño, 2026-10-11): la meta es 0 falsas.

| Simbología | Se acepta si… | Lecturas iguales | Se entrega como |
|---|---|---|---|
| EAN-13 (750…, internos 2…) | Dígito verificador | 2 en cuadros seguidos | 13 dígitos |
| UPC-A | Dígito verificador | 2 seguidas | GTIN-13 con 0 adelante; en el catálogo se busca con 12 ([abajo](#búsqueda-en-el-catálogo)) |
| UPC-E | Su expansión a UPC-A es válida (con su verificador) | 2 seguidas | GTIN-13; en el catálogo se busca con 8 y con su UPC-A de 12 |
| EAN-8 | Dígito verificador | 2 seguidas | 8 dígitos |
| ITF | **Exactamente 14 dígitos** con verificador (ML Kit llega a leer basura de 40 dígitos) | 2 seguidas | 14 dígitos |
| Code 128 | Su símbolo de verificación (lo valida el decodificador); sin `]C1` ni caracteres de control | 2 seguidas | Igual |
| QR | Su corrección de errores | 1 | Igual (en la sesión de prueba solo un resumen) |

**Dos seguidas** quiere decir en el cuadro analizado actual y en el anterior: un cuadro analizado sin esa lectura
(vacío, con otro código o con una lectura inválida) rompe la racha, y un cuadro que falló también. Nunca se acepta un
1D de un solo cuadro para ganar velocidad. Un verificador mal no se lee ni se «corrige», por más cuadros que llegue.
El confirmador corre en cada cuadro analizado, con cualquier motor, aunque no haya lecturas. El respaldo de ML Kit
corre uno de cada dos cuadros, salvo la segunda lectura ([motores](#motores)).

Con varios códigos a la vista se toma el que tiene el centro de la franja, o el más cercano a él.

**Repetidos.** Un código distinto entra de inmediato. El mismo código vuelve a contar cuando:

- **Sale de la mira:** desapareció al menos **500 ms** (ni leído ni visto en el mismo lugar). Es el antirrebote: un
  parpadeo del motor no duplica, y para sumar otra pieza basta con retirarla y volver a pasarla.
- **Pasan 1.5 s desde que se aceptó**, aunque siga a la vista (`msParaRepetirAVista`; 0 = nunca). Así lo pidió el
  dueño: **sostener el mismo producto más de 1.5 s lo cuenta otra vez**. En la sesión de prueba se marca «duplicado»
  para medir cuántas veces pasa.

Mientras no se cumpla ninguna, la indicación dice «Ya leído». Se recuerdan varios aceptados: con dos códigos juntos a
la vista, el centro de la franja puede caer en uno y luego en el otro, y sin esa memoria se sumarían los dos en cada
vuelta; así cuenta cada uno a lo más una vez cada 1.5 s. El respaldo de ML Kit sigue corriendo hasta 1 s después del
último indicio: si dejara de verlo, el filtro lo daría por retirado y contaría doble.

- **Modo venta:** lista de lo leído; el mismo código suma uno a su renglón. Botones +1, −1 y deshacer.
- **Modo conteo:** contador grande del código actual que crece con cada suma; −1 y deshacer siempre a la vista.

## Búsqueda en el catálogo

Cada lectura aceptada se busca en un catálogo de prueba: el de ejemplo (ficticio) o el archivo que exporte el dueño.
Es la misma regla que usará el servicio del POS ([plan](plan.md#entrega-8-medir-la-lectura), parte 4), en
`nucleo/Catalogo.kt`. Se prueba, en este orden, y nada más:

1. El código tal cual (`TAL_CUAL`).
2. Un UPC-A de 12 que empieza con 0: sin ese 0, de 11 dígitos (`UPC_A_11`).
3. Un EAN-13 que empieza con 0: como UPC-A de 12 (`EAN13_A_UPC_A`) y luego, si ese empieza con 0, de 11
   (`EAN13_A_UPC_A_11`).
4. Un UPC-E: expandido a su UPC-A de 12 (`UPC_E_A_UPC_A`).

Gana la primera que esté en el catálogo. Ninguna otra corrección: no se adivinan ni se completan dígitos, no se quitan
ceros a otros códigos (EAN-8, ITF, Code 128) y una lectura sin verificar no se busca. Las variantes de una lectura
válida también tienen su verificador bien: un código del catálogo con el verificador mal nunca coincide. Es un hallazgo
del catálogo y no se arregla.

**Sobre qué texto.** Sobre lo que entregó el decodificador, con su simbología, en la forma que dan las barras y ML Kit.
zxing-cpp alarga el UPC-A y el UPC-E a 13 dígitos; la forma se arma desde el normalizado, que es igual con los dos
motores, así que el resultado no depende del motor:

| Lectura | ML Kit | zxing-cpp | Se busca como |
|---|---|---|---|
| UPC-A | 12 dígitos | 13 («0…») | UPC-A de 12: pasos 1 y 2 |
| EAN-13 que empieza con 0 | UPC-A de 12 (las mismas barras) | 13 | UPC-A de 12: pasos 1 y 2 |
| UPC-E | 8 dígitos | Expandido a 13 | Sus 8 dígitos: pasos 1 y 4 |
| EAN-13, EAN-8, ITF-14, Code 128, QR | | | El normalizado: paso 1 |

Un EAN-13 que empieza con 0 tiene las mismas barras que un UPC-A: la cámara no los distingue. El paso 3 es para el
texto de 13 dígitos que manda un lector configurado así; con la cámara, la misma clave sale por el paso 1 (la de 12) o
el 2 (la de 11). Un UPC-A guardado con 13 dígitos («0…») no coincide con la lectura: la regla no completa dígitos.

**Dónde se aplica.** Cada intento de la sesión de prueba lleva `en_catalogo` (si o no; vacío si no se buscó),
`variante` y `clave_catalogo` (oculta en el QR, como su valor). En el panel de lecturas, el renglón que no está en el
catálogo dice «no está en el catálogo».

**El archivo.** Texto o CSV con el código en la primera columna (hasta la primera coma, punto y coma o tabulador), sin
comillas ni espacios alrededor. El encabezado es opcional: el primer renglón lo es si su primera columna empieza con
«codigo», «código», «clave», «code» o «barcode». Las líneas vacías se saltan y un repetido cuenta una vez. UTF-8 o
Windows-1252 (el «CSV» de Excel), hasta 8 MB. Las claves van en un HashSet: buscar cuesta microsegundos.

**Cuándo se carga.** Perezoso, en su propio hilo: con la primera lectura o al abrir Ajustes, nunca en la apertura de
la cámara. Con sesión de prueba, la primera lectura espera la carga (solo esa vez); la vibración y el verde no.

**En Ajustes, «Catálogo de prueba»:** cuál está cargado y cuántos códigos trae, «Cargar archivo…» (el selector del
sistema: sin permisos ni red) y «Usar el de ejemplo». El archivo se copia a la memoria privada de la app
(`files/catalogo`, sin respaldo en la nube) y sigue ahí al reabrirla; «Usar el de ejemplo» borra la copia.

**Privacidad.** El archivo del dueño puede traer códigos reales: no va al registro, no se exporta y no entra a las
pruebas ni al repositorio. Los CSV de la sesión llevan lo de antes (los códigos leídos) y la clave con que coincidió
cada uno.

**El de ejemplo** (`app/src/main/assets/catalogo-ejemplo.csv`): un caso por variante, todos ficticios. Trae también los
códigos de los carteles del emulador.

| Código | Qué es | Variante con la cámara |
|---|---|---|
| 7509993456786 | EAN-13 750999 | `TAL_CUAL` |
| 099999291452 | UPC-A guardado de 12 | `TAL_CUAL` (con 13 dígitos, `EAN13_A_UPC_A`) |
| 99999400021 | UPC-A 099999400021 guardado de 11 | `UPC_A_11` |
| 99999400038 | Guardado de 11; llega como EAN-13 0099999400038 | `UPC_A_11` (con 13 dígitos, `EAN13_A_UPC_A_11`) |
| 099994000059 | El UPC-A del UPC-E 09999459 | `UPC_E_A_UPC_A` |
| 75099916, 17509995000014, 2999990000016, 2999990000023 | EAN-8, GTIN-14 del ITF e internos del POS | `TAL_CUAL` |
| ZZ0001, ZZ-PRUEBA-0001, ZZP0042x | Claves en Code 128 | `TAL_CUAL` |
| 7509994000996 | EAN-13 con el verificador mal (el bueno termina en 5) | Ninguna: no coincide |

## Experiencia

| Estado | Qué se ve | Qué se oye y se siente |
|---|---|---|
| Abriendo | Fuera de la retícula oscuro al 60 %, «Abriendo cámara…» (rueda si tarda > 1 s) | — |
| Escaneando | Una línea a la mitad de la franja y la retícula que pulsa suave (cada 2.5 s): empieza con el **primer cuadro analizado** | — |
| Viendo | Esquinas ámbar que siguen al código en vivo; la indicación en un recuadro de texto grande con su flecha o icono | — |
| Leído | En el mismo instante: retícula verde, palomita y el código en grande; un destello verde suave | Vibración de confirmación; el bip corto, si se enciende en Ajustes |
| En pausa | Imagen atenuada y «Toca para seguir escaneando» | — |
| Error | Tache, el mensaje y «Reintentar» | Vibración de rechazo; dos tonos graves, si el sonido está encendido |

- **Nunca depende solo del color:** cada estado tiene forma, icono y texto; el éxito, además, vibración. El bip viene
  apagado (decisión del dueño, 2026-10-11): con el sonido apagado no se cargan los sonidos al abrir. Encendido, puede
  estar silenciado (volumen o modo del teléfono) y entonces se avisa; la vibración puede estar apagada en los ajustes
  del celular. Lo visual basta solo.
- Botones de 56 dp o más (linterna, pausa, +1, −1); texto blanco sobre la capa oscura (contraste ≥ 5.7:1); probado con
  letra al 200 %. Las indicaciones se anuncian al lector de pantalla sin interrumpir.
- El zoom actual se muestra («1.5×») y un panel de diagnóstico opcional da cuadros por segundo, milisegundos, px por
  módulo, nitidez y brillo.

## Apertura

Sacar el celular del bolsillo y escanear de inmediato, aun bloqueado. Los caminos, del más rápido al más lento:

1. **El escáner se quedó abierto:** al encender la pantalla aparece escaneando (el escáner rápido se muestra sobre el
   bloqueo).
2. **Un gesto del celular** configurado para abrir la app: Samsung (doble pulsación del botón lateral), Motorola (Quick
   Launch), Pixel (Toque rápido). Lo configura cada quien; la app no puede.
3. **El mosaico «Escanear» de Ajustes rápidos:** abre el **escaneo rápido** sobre el bloqueo, sin pedir el PIN (la
   Activity declara `showWhenLocked` en el manifiesto: en tiempo de ejecución no basta). Solo deja escanear y ver lo
   leído; «Abrir la app» pide desbloquear. Se cierra sola tras 5 minutos sin escanear o cuando la cámara se apaga por
   inactividad, y la pantalla solo se mantiene encendida mientras escanea. El mosaico es «no activo»: el sistema arranca
   el proceso al abrir el panel, antes del toque. En la primera versión de Android 14 el sistema todavía pedía el PIN.
4. **El ícono** o sus accesos directos «Escanear venta» y «Escanear conteo».

**Cómo abre rápido:**

- La cámara se enlaza en el `onCreate` de la Activity, antes de que haya interfaz, con la proporción del visor que se
  guardó la vez anterior: abre mientras Compose dibuja. Antes se enlazaba al medir el visor, y **dos veces** (Compose lo
  mide primero sin las barras del sistema y luego con ellas); ahora abrir es idempotente y un cambio real de tamaño
  espera 300 ms y enlaza una sola vez.
- El rango de cuadros por segundo se guarda por modelo de celular (`Build.FINGERPRINT`): no se consulta en el camino de
  la apertura. Se valida una vez por proceso en segundo plano; si la cámara lo rechazó, queda «automático» para siempre
  en ese celular.
- El motor se crea y se calienta en paralelo con la cámara; ML Kit empieza a cargar su modelo al crearse.
- Al cambiar de pestaña la cámara se apaga y al volver abre **una sola vez**. El visor que sale avisa al controlador
  (`visorRetirado`) y, ya con la cámara detenida, se pide otra solicitud de superficie para la vista previa. Antes la
  cámara abría con la solicitud del visor que ya no existía y CameraX rearmaba la sesión dos veces al llegar la del
  visor nuevo: abría, cerraba y volvía a abrir en ~350 ms, y lo que se apuntaba en ese momento se perdía.
- Ventana y pantalla de inicio negras (sin destello blanco) y un primer cuadro de interfaz trivial.
- Un perfil de arranque (Baseline Profile y Startup Profile, generados con `:baselineprofile`) precompila el camino
  de la apertura ([pruebas](pruebas.md#arranque-y-perfil)).

**Lo que se mide** (`MarcasApertura`, en `elapsedRealtimeNanos`): proceso, `Application`, Activity, primer cuadro de la
interfaz, inicio y fin del enlace, cámara abierta, primer cuadro del sensor, primer cuadro analizado y primera lectura.
El tipo de arranque es frío (el proceso nació para la Activity), tibio (el proceso vivía: también el que arrancó el
mosaico) o caliente (volvió de segundo plano o de la pantalla apagada); en Android 15+ lo dice el sistema
(`ApplicationStartInfo`). Cada apertura queda en el registro («Apertura: frío: proceso=0 … primer_analisis=…»), en
`aperturas.csv` de la sesión de prueba (también las del escaneo rápido) y, para Macrobenchmark, como TTFD (el primer
cuadro analizado) y en secciones de trace (`escaner.hastaPrimerCuadro`, `escaner.esperaProveedor`,
`escaner.rangosFps`, `escaner.enlazar`).

**Si la cámara falla:**

| Estado de CameraX | Qué hace |
|---|---|
| Otra app la usa (o hay demasiadas abiertas) | «Otra app está usando la cámara»; CameraX reintenta solo y vuelve a escanear al liberarse |
| Desactivada (privacidad o política) | Lo avisa; se reintenta con el botón |
| No molestar | Pide desactivarlo |
| Rechazó la configuración con el rango fijo (60 o 30) | Vuelve a enlazar sin rango fijo y lo recuerda |
| Error grave | Dos reintentos (a 0.5 y 1 s) y luego el error con «Reintentar» |

Además, si con la cámara abierta y la pantalla al frente pasan 2 s sin cuadros, se vuelve a enlazar una vez. Los estados
que llegan de un enlace anterior se descartan (cada enlace tiene su número).

## Consumo

- 20 s sin códigos a la vista: el análisis se pausa (los cuadros se descartan sin procesar). 60 s en pausa: se apaga
  la cámara. Al salir de la pantalla o ir a segundo plano, se libera todo. La pantalla solo se mantiene encendida
  mientras escanea.
- Un solo hilo de análisis, sin asignaciones de memoria por cuadro en el camino del localizador, la guía y el recorte.
- La vista usa `SurfaceView` (menos energía que `TextureView`).
- El motor automático corre ML Kit solo cuando hace falta (ver [motores](#motores)).
- **Monitor de recursos** (en la sesión de prueba, a 1 Hz en un hilo de baja prioridad): CPU del proceso (ms y % de un
  núcleo), memoria (heap de Java y nativo, RSS, hilos, descriptores), batería (corriente autocalibrada: signo con el
  equipo desconectado y unidad µA/mA por magnitud, cruzada con el contador de carga; voltaje, potencia, temperatura),
  estado térmico y margen térmico, y cuadros por segundo producidos y analizados. La CPU de la app no incluye el
  servicio de la cámara ni su HAL (otros procesos): para eso, Perfetto ([pruebas](pruebas.md#rendimiento-y-batería)).
