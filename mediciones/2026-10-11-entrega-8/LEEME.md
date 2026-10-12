# Entrega 8 en el Pixel: ficha, banco y apertura (2026-10-11)

La primera corrida en el Pixel 8 Pro con la entrega 8 completa ([plan](../../docs/plan.md#entrega-8-medir-la-lectura)):
qué dice la cámara de sí misma, cómo le va a cada motor en el banco de ese celular, y si las partes 6 a 10 (ficha,
registro por intento, resolución, lado a lado y luz en la mira) cambiaron la apertura respecto a lo
[mejor medido](../2026-10-09-visor-primero/LEEME.md).

- **Equipo:** el mismo Pixel 8 Pro, Android 17, `CP3A.260905.009`, por USB, «Permanecer activo», batería 62 %, estado
  térmico 0 al empezar.
- **App:** `benchmarkRelease` del commit `d99c50a` (main con los PR 17 a 22), versión 0.1.0, con el perfil de arranque
  compilado (`cmd package compile -f -m speed-profile`). Se instaló desinstalando la anterior (otra firma); desde hoy
  la app del Pixel va con la llave de depuración de esta PC. El APK medido, las 45 trazas de Perfetto, el
  `dumpsys media.camera` y la captura del banco están fuera de git, en
  `C:\Users\salva\Documents\TPV\mediciones-pixel8pro\2026-10-11-entrega-8\`.

## Ficha de la cámara

[ficha-camara.txt](ficha-camara.txt), tal como la muestra Ajustes (y coincide con `dumpsys media.camera`):

| Lente | Focal | Sensor | Enfoca desde | A esa distancia abarca | 0.33 mm ahí | Zoom con que abre |
|---|---|---|---|---|---|---|
| Lógica 0 (y física 2, la principal) | 6.9 mm | 9.8×7.4 mm | 10.5 cm, aproximada | 8.4 cm | 2.8 px | 1.7× |
| Física 5 (la principal recortada, el «2×») | 6.9 mm | 4.9×3.7 mm | 10.5 cm | 4.2 cm | 5.7 px | 1.0× |
| Física 3 (gran angular) | 2.2 mm | 6.4×4.8 mm | 2.0 cm | 3.2 cm | 7.4 px | 1.0× |
| Física 4 (tele) | 18 mm | 5.6×4.2 mm | 80 cm | 14.1 cm | 1.7 px | 2.0× |

Lo que importa para las etiquetas: la principal a 10.5 cm da 2.8 px por módulo de 0.33 mm a 720p (el mínimo útil es 2),
y la app abre con 1.7× para que un código de 30 mm llene el 60 % del ancho desde ~6 cm sin acercarse más de lo que
enfoca. La gran angular enfoca a 2 cm y ahí da 7.4 px por módulo: es la candidata para los códigos chicos de cerca
(parte 8: hay que medirla en la tienda). El enfoque viene **aproximado** en todos los lentes: la sesión sí llena
`distancia_cm`. Hay una sola región AE: la luz en la mira funciona (parte 10).

## Banco de pruebas en el Pixel

Modo «Catálogo», franja 36 %, las 7 simbologías, 121 escenas (109 con código, 12 sin). Tardó 25 s.

| | Automático | ML Kit | zxing-cpp |
|---|---|---|---|
| Aciertos | 88/109 (80 %) | **95/109 (87 %)** | 62/109 (56 %) |
| Lecturas incorrectas | 0 | 0 | 0 |
| Sin lectura | 21 | 14 | 47 |
| Falsos positivos (12 escenas sin código) | 0 | 0 | 0 |
| ms por escena, caliente, mediana / p95 | 1.2 / 18.2 | 12.9 / 43.4 | **0.8 / 1.6** |
| ms en frío (primera lectura), mediana | 1.3 | 57.8 | 1.2 |
| Aciertos a 1.5 px/módulo | 17/18 | 18/18 | 11/18 |
| Aciertos a 2.0 px/módulo | 21/30 | 24/30 | 14/30 |
| Aciertos a 3.0 px/módulo | 37/48 | 40/48 | 29/48 |
| Aciertos a 5.0 px/módulo | 13/13 | 13/13 | 8/13 |

Lo que dice:

- **Cero lecturas incorrectas y cero falsos positivos** en los tres, también con la escena de barras falsas: la meta
  del plan (0) se cumple en el banco.
- **ML Kit en el Pixel cuesta 13 ms por escena** (mediana), no los 55–100 ms del emulador: cabe en cada cuadro a 30
  cuadros por segundo. Y lee más que el automático (87 contra 80 %), porque en el banco cada escena es un solo cuadro
  y el automático solo deja correr a ML Kit en uno de cada dos. Es el argumento para el experimento 6 del plan (ML Kit
  en más cuadros): con «Lado a lado» en la tienda se ve si en vivo pasa lo mismo.
- **zxing-cpp solo lee el 56 %:** lo que no lee es lo borroso, con ruido y con poca luz (en lo nítido a 5 px lee 8 de
  13, por los girados). Sigue siendo el que cuesta menos (0.8 ms), y por eso va primero.

## Apertura

Un `am start -S -W` a mano, con el perfil compilado: Displayed (TTID) 164 ms, Fully drawn (primer cuadro analizado)
467 ms; la apertura por dentro: camara_abierta=351, primer_cuadro_sensor=375, primer_analisis=386 ms desde el proceso;
la cámara esperó 60 ms a CameraX. En el primer cuadro se pidió la luz en la mira (centro del buffer, lado 0.36).

**Macrobenchmark** (`AperturaBenchmark`, los tres casos con perfil, 15 iteraciones cada uno, 9 minutos en total;
[benchmarkData.json](benchmarkData.json), [hitos.csv](hitos.csv) de las 45 trazas). Medianas (mínimo–máximo), contra
[el visor desde el primer cuadro](../2026-10-09-visor-primero/LEEME.md) (lo que el plan tenía como mejor) y la corrida B
del [A/B de la barra](../2026-10-10-ab-barra/LEEME.md) (la mejor en frío hasta hoy):

| Con perfil, medianas | Visor primero (09-10) | A/B barra, B (10-10) | Entrega 8 (hoy) | Cambio contra lo mejor |
|---|---|---|---|---|
| Frío: ya escaneando (TTFD) | 532 ms (515–576) | 513 ms (454–576) | **504 ms** (485–530) | −8 ms y menos disperso |
| Frío: apertura → primer análisis | 352 ms | 334 ms | 340 ms | igual |
| Frío: primer cuadro en pantalla (TTID) | 239 ms | 230 ms | **198 ms** (176–228) | −32 ms |
| Tibio: ya escaneando | 386 ms | 354 ms | **318 ms** (299–347) | −36 ms |
| Tibio: apertura → primer análisis | 292 ms | 273 ms | 255 ms | −18 ms |
| Caliente: apertura → primer análisis | 279 ms | 267 ms | 271 ms (236–282) | igual |
| Por cuadro (frío / tibio / caliente) | 2.4 / 3.7 / 4.0 ms | 2.2 / 2.7 / 3.1 | **2.1 / 2.5 / 2.4** | igual o mejor |

**Conclusión:** las partes 6 a 10 no le cuestan nada a la apertura: la ficha se lee después del primer análisis y
una sola vez por proceso, la región de luz en la mira va después del enlace, y lo demás está apagado por omisión. En
frío y en tibio salió incluso mejor que lo mejor medido (la mitad de la diferencia puede ser el día: otra temperatura
y otro estado del sistema, no hay cambio que lo explique), así que es la versión que se queda instalada en el Pixel.
Por cuadro, 2.1 ms con el motor automático: igual que la línea base.

**Los hitos en las trazas** (`herramientas/trazas.sh`, medianas en ms desde el lanzamiento; entre paréntesis, el visor
desde el primer cuadro del 2026-10-09):

| Con perfil | Abrir la cámara | Cámara abierta | Sesión | Sesión lista | Primer análisis | Espera de la cámara a la sesión |
|---|---|---|---|---|---|---|
| Frío | 191 (192) | 213 (213) | 236 (262) | 295 (321) | 456 (479) | 20 (42) |
| Tibio | 55 (100) | 81 (142) | 100 (166) | 159 (226) | 305 (372) | 21 (18) |
| Caliente | 36 (40) | 62 (68) | 65 (69) | 124 (133) | 302 (304) | 2 (2) |

En frío la cámara abre igual (a los ~190 ms) y lo que mejoró es la sesión, que ahora espera 20 ms a la superficie del
visor en vez de 42. En tibio la app empieza a abrir la cámara 45 ms antes (55 contra 100 ms) y todo lo demás se corre
con ello. En los tres, de la sesión lista al primer análisis siguen siendo ~150–180 ms: el HAL del Pixel, como ya se
sabía.
