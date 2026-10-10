# El escáner

Cómo lee la app los códigos de barras con la cámara y qué ve, oye y siente el personal mientras tanto. Las razones y
las fuentes de cada número están en la [investigación](investigacion-escaner.md); los umbrales son puntos de partida
que se calibran con los celulares de la tienda ([pruebas en tienda](pruebas-en-tienda.md)).

## Qué tiene que lograr

| Meta | Cómo se mide |
|---|---|
| **Cero lecturas incorrectas aceptadas** (cobrar otro producto es peor que no leer) | Sesión de prueba: resultado «lectura incorrecta» |
| Que el personal sepa al instante que ya escanea | La línea de la retícula aparece con el primer cuadro analizado; arranque en frío a ese cuadro < 1.5 s |
| Leer rápido | Del código bien colocado al bip: p50 ≤ 250 ms y p95 ≤ 500 ms en gama baja; del resultado al bip, la vibración y el verde ≤ 50 ms |
| Leer casi todo al primer intento | ≥ 98 % de los intentos leídos en ≤ 2 s |
| Gastar poco | CPU de la app < 35 % de un núcleo escaneando; el localizador < 3 ms por cuadro (p95); la cámara se apaga sola si nadie escanea |

## Módulos

```
app ──► escaner ──► nucleo
 │        │           └─ Kotlin puro (se prueba en la JVM): GTIN, codificadores, escenas sintéticas,
 │        │              geometría, localizador, guía, validación y consenso
 │        └─ Android: cámara (CameraX), motores (ML Kit, zxing-cpp), pantalla del escáner, bip y vibración
 └─ la app de prueba: modo venta y modo conteo, ajustes, sesión de prueba (CSV) y banco de pruebas
```

El módulo `escaner` no sabe nada del POS: recibe una configuración y entrega lecturas (`ControladorEscaner`). Así se
puede usar desde la app nativa o como biblioteca desde otra app ([plan](plan.md#decisiones)).

## Cada cuadro

La cámara entrega hasta 30 cuadros por segundo; el análisis toma siempre el más reciente y descarta los que llegan
mientras trabaja (`STRATEGY_KEEP_ONLY_LATEST`), así nunca se atrasa.

1. **Localizar** en el cuadro completo (~0.2 ms): dónde hay barras, su tamaño en px por módulo, nitidez, luz, reflejo y
   el nivel que separa barras de espacios.
2. **Leer** la franja de la retícula con el motor automático: zxing-cpp tal cual, zxing-cpp con el umbral del
   localizador y, solo si el localizador ve un código que no sale, ML Kit ([motores](#motores)).
3. **Validar y confirmar:** dígito verificador, consenso entre cuadros y el filtro de repetidos.
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
| Resolución | 1280×720 (16:9) para la vista y el análisis | Bastan ≥ 2 px por módulo; 1080p cuesta ~30 % más y no ayuda en vivo |
| Formato | YUV_420_888, sin rotar la salida | Se usa solo el plano Y; rotar cuesta 10–15 ms por cuadro |
| Cuadros por segundo | 30 fijos si se puede (menos movido); 15–30 como alternativa en ajustes | Se compara en la tienda |
| Enfoque | Continuo; al abrir, al tocar y con un código que no se lee en 700 ms, enfoque al punto (se suelta solo a los 2 s) | Bloquear el enfoque falla al cambiar de distancia |
| Zoom inicial | Calculado con la distancia mínima de enfoque del lente (1.5× si no la reporta), tope 2× | Muchos celulares no enfocan a menos de 10–15 cm: con zoom se lee desde más lejos |
| Zoom propio | Si el código se ve chico (< 2 px por módulo), ×1.4 hasta el tope; regresa al leer | Antes de pedir «acércate» |
| Linterna | Botón que pulsa si hay poca luz (≥ 1 s); automática como ajuste | Nunca se apaga por la luz, porque la propia linterna la sube |
| Arranque | CameraX se prepara al abrir la app y solo con la cámara trasera | Abre más rápido |

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
2. Si no leyó y el localizador midió el nivel entre barras y espacios: **zxing-cpp sobre la franja binarizada con ese
   umbral** (~1 ms). Rescata la etiqueta blanca sobre cartón o empaque de color, el caso común en la tienda; con un cuadro
   real del emulador, sin esta pasada no se leía y con ella sí.
3. Si tampoco y hay un código probable (el localizador lo ve, o hubo uno hace menos de 1 s): **ML Kit** (incluido en la
   app, 17.3.0) sobre **todo lo visible**, a lo más uno de cada dos cuadros para que la guía siga fluida. Con el celular
   vertical a ML Kit le cuesta menos lo visible que la franja, y le da contexto a un código difícil (borroso, girado).

En la prueba de punta a punta del emulador, 8 de 10 lecturas salieron por la segunda pasada (150–500 ms desde que
apareció el código) y 2 por ML Kit (el código girado y el pegado abajo, 270–600 ms). En ajustes se puede fijar un
solo motor (ML Kit o zxing-cpp) para compararlos; el de la tienda se decide con las mediciones ([pruebas en
tienda](pruebas-en-tienda.md)). Solo se piden las simbologías que se usan, nunca «todas». Cada lectura dice por cuál
vía salió (directa, umbral o respaldo) en el registro y en la sesión de prueba.

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
| Ya leído | El código a la vista ya se contó y no se ha retirado | «Ya leído: retíralo para leerlo otra vez» |
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
toma como desconocida. El ancho del módulo no decide «aléjate» con el código borroso: el desenfoque lo infla.

## Validación y consenso

| Simbología | Se acepta si… | Lecturas iguales | Se entrega como |
|---|---|---|---|
| EAN-13 (750…, internos 2…) | Dígito verificador | 2 de los últimos 3 cuadros | 13 dígitos |
| UPC-A | Dígito verificador | 2 de 3 | GTIN-13 con 0 adelante; se busca también con 12 |
| UPC-E | Su expansión a UPC-A es válida | 2 de 3 | GTIN-13; se busca también con 8 y 12 |
| EAN-8 | Dígito verificador | 2 de 3 | 8 dígitos |
| ITF | **Exactamente 14 dígitos** con verificador (ML Kit llega a leer basura de 40 dígitos) | 2 de 3 | 14 dígitos |
| Code 128 | Sin `]C1` ni caracteres de control | 2 de 3 | Igual |
| QR | Su corrección de errores | 1 | Igual (en la sesión de prueba solo un resumen) |

Con varios códigos a la vista se toma el que tiene el centro de la franja, o el más cercano a él.

**Repetidos.** Un código distinto entra de inmediato. El mismo código vuelve a contar solo si **desapareció al menos
500 ms** (ni leído ni visto en el mismo lugar): un parpadeo del motor no duplica, y para sumar otra pieza basta con
retirarla y volver a pasarla. Mientras siga a la vista, la indicación dice «Ya leído», y el respaldo de ML Kit sigue
corriendo hasta 1 s después del último indicio: si dejara de verlo, el filtro lo daría por retirado y contaría doble.

- **Modo venta:** lista de lo leído; el mismo código suma uno a su renglón. Botones +1, −1 y deshacer.
- **Modo conteo:** contador grande del código actual que crece con cada suma; −1 y deshacer siempre a la vista.

## Experiencia

| Estado | Qué se ve | Qué se oye y se siente |
|---|---|---|
| Abriendo | Fuera de la retícula oscuro al 60 %, «Abriendo cámara…» (rueda si tarda > 1 s) | — |
| Escaneando | Una línea a la mitad de la franja y la retícula que pulsa suave (cada 2.5 s): empieza con el **primer cuadro analizado** | — |
| Viendo | Esquinas ámbar que siguen al código en vivo; la indicación en un recuadro de texto grande con su flecha o icono | — |
| Leído | En el mismo instante: retícula verde, palomita y el código en grande; un destello verde suave | Bip corto y vibración de confirmación |
| En pausa | Imagen atenuada y «Toca para seguir escaneando» | — |
| Error | Tache, el mensaje y «Reintentar» | Dos tonos graves |

- **Nunca depende solo del color:** cada estado tiene forma, icono y texto; el éxito, además, sonido y vibración. El
  bip puede estar silenciado (volumen o modo del teléfono) y la vibración apagada en los ajustes del celular: lo visual
  basta solo, y si el volumen está en cero se avisa.
- Botones de 56 dp o más (linterna, pausa, +1, −1); texto blanco sobre la capa oscura (contraste ≥ 5.7:1); probado con
  letra al 200 %. Las indicaciones se anuncian al lector de pantalla sin interrumpir.
- El zoom actual se muestra («1.5×») y un panel de diagnóstico opcional da cuadros por segundo, milisegundos, px por
  módulo, nitidez y brillo.

## Consumo

- 20 s sin códigos a la vista: el análisis se pausa (los cuadros se descartan sin procesar). 60 s en pausa: se apaga
  la cámara. Al salir de la pantalla o ir a segundo plano, se libera todo.
- Un solo hilo de análisis, sin asignaciones de memoria por cuadro en el camino del localizador, la guía y el recorte.
- La vista usa `SurfaceView` (menos energía que `TextureView`).
