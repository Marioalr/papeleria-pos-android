# Propuesta: la app conectada al POS

> **Para aprobar (2026-11-03).** Se prueba en el [prototipo navegable](propuesta-interfaz/prototipo.html): se abre con
> doble clic en la PC o en el celular, sin instalar nada. Arriba se cambian la red, si el POS está en el piloto, de
> quién es el celular (de Luis o de la tienda), lo que pasa (10 min sin uso, 20 s sin códigos, quitar el acceso), el
> tamaño, el tema y «Ver» (el gesto, letra 200 %); a la derecha está «Qué probar». Todo es ficticio.
>
> **El núcleo de reconocimiento no cambia.** Las pruebas de la cámara salieron bien (dueño, 2026-11-03): `nucleo`, la
> cámara, los motores y la pantalla del escáner (`escaner`) se quedan como se midieron. Esta propuesta pone la app
> alrededor del escáner, sin tocarlo.

El POS se acerca al piloto en la caja 2, así que la app deja de ser solo el escáner de prueba y se vuelve la herramienta
del piso de venta. Hace cuatro cosas con la misma cámara:

| Tarea | Hoy | En la app |
|---|---|---|
| **¿Cuánto cuesta?** | El verificador de la caja (F9) | El modo **Precio**: se escanea y sale el precio, sin agregar nada |
| **Armar el ticket** | El cliente lleva todo a la caja y ahí se escanea | El modo **Ticket**: se escanea en el piso y se manda a una caja, donde llega en espera |
| **Agregar las impresiones** | F2 en la caja | Dentro del ticket: las impresiones por cobrar con su miniatura, como en F2 |
| **Contar** | La página `/conteo` en el navegador del celular: sin cámara, con el usuario escrito | El modo **Conteo**: con la cámara, a ciegas, con cajas y piezas sueltas |

Lo que se averiguó del POS para esta propuesta (la API, el verificador, las impresiones, el conteo y lo que falta) está en
[lo que pide al POS](#lo-que-pide-al-pos) y en cada sección.

## Lo que pide una pantalla chica

- **Una mano y de pie.** La otra mano trae el producto. Lo que se toca va abajo, al alcance del pulgar; arriba solo va lo
  que se lee de un vistazo.
- **La franja es intocable.** Ocupa del 32 al 68 % del visor y nada la tapa. Encima va la indicación del escáner y en
  medio su confirmación verde, como hoy.
- **El visor queda del mismo tamaño que en las mediciones.** Hoy es la pantalla menos la barra de pestañas; esa barra se
  va, pero el visor no crece: el panel de abajo se dibuja encima de ese espacio. Así la franja cae en el mismo lugar de la
  imagen y lo medido sigue valiendo. (El escaneo rápido sobre el bloqueo ya es de pantalla completa y se queda así.)
- **El modo dice qué hace cada lectura,** con color, ícono y nombre. El precio nunca agrega al ticket: así no se cobra
  por error lo que solo se preguntó.
- **Cambiar de modo no reabre la cámara:** el escáner sigue abierto y solo cambia lo que la app hace con la lectura.
- **Botones de 56 dp** (unos 9 mm; el dp es la medida de Android que no cambia con la pantalla: 1 dp ≈ 0.16 mm; el sp es
  lo mismo para la letra y crece con el tamaño de letra del celular), como hoy, texto que aguanta el tamaño de letra al
  200 % y nunca solo el color.
- **Lo que se pueda, sin red.** La red de la tienda se cae (cuando se va el internet, el wifi de los celulares también):
  el precio, la búsqueda y el ticket siguen; el conteo espera para subirse.
- **Lo que la persona no puede hacer, no aparece,** como en la caja nueva.
- **Un solo botón para lo demás,** abajo a la derecha, al alcance del pulgar: un toque abre tres opciones y pulsar,
  deslizar y soltar elige una sin mirar ([el botón rápido](#el-botón-rápido)).
- Se diseñó y se probó a **360 × 740 dp** (un celular chico) y a **448 × 997 dp** (el Pixel 8 Pro).

## Lo que necesita el piso, de lo más a lo menos seguido

Las frecuencias son estimadas; se confirman en la tienda.

| # | Necesidad | Qué tan seguido | Cómo queda |
|---|---|---|---|
| 1 | Contestar «¿cuánto cuesta?» | Muy seguido | **Precio**: escanear y ver precio, mayoreo, promoción y existencia; ↑ «Enseñar al cliente» lo pone en grande; si se lo lleva, «Al ticket» en la tarjeta |
| 2 | Pasar lo que lleva el cliente | En cada preventa | **Ticket**: cada código suma uno; lo último arriba, con − y + |
| 3 | Lo que no se escanea | Seguido | ← «Buscar»: dos pedazos de palabra, con el campo junto al pulgar |
| 4 | Las impresiones que trae en la mano | Muchas veces al día | ↖ «Impresiones»: las miniaturas en lotes; se tocan las que trae |
| 5 | Mandarlo a la caja | Al terminar cada ticket | ↑ «Mandar a Caja 2»: la última caja usada ya viene elegida |
| 6 | Sin red | Unas veces al año, por horas | El ticket como QR para el lector de la caja |
| 7 | Contar | El conteo del día; el completo, al arrancar y una vez al año | **Conteo**: a ciegas y por lugares, cajas y sueltas; lo de cada lugar se guarda solo |
| 8 | Entrar | Una vez por turno; en el de la tienda, cada vez que cambia quien cuenta | En el de Luis, su huella; en el de la tienda, la ficha y el PIN para contar, con «Salir» a la vista |
| 9 | Dar de alta el celular | Una vez | Escanear el código que enseña la caja; la caja confirma «¿Es este?» |

## La pantalla principal: el escáner con tres modos

```
┌──────────────────────────────────────────┐
│ (L) · Sin red                      [L]   │  quién entró (su menú) · la red, solo si falla · la linterna
│                                          │
│     [ ||| Apunta al código de barras ]   │  la indicación del escáner (la de hoy)
│ ┌──────────────────────────────────────┐ │  ← 32 %
│ │                                      │ │
│ │  ──────────────────────────────────  │ │  la franja: nada la tapa
│ │                                      │ │
│ └──────────────────────────────────────┘ │  ← 68 %
│ ╭───────────────── ══ ─────────────────╮ │  el asidero: se jala para ver todo
│ │ Ticket · 4 art.             $129.50  │ │
│ │▌Pegamento blanco 250 g        $32.00 │ │  lo último, resaltado
│ │▌$32.00 c/u               [x  1  +]   │ │
│ │ Cuaderno profesional…  ×2    $92.00  │ │
│ │ Lápiz HB No. 2         ×1     $5.50  │ │
│ │ (Precio) (Ticket) (Conteo)    [···]  │ │  los modos y el botón rápido
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

`[L]` la linterna · `[x]` quitar (el bote) · `[···]` el botón rápido (tres puntos en abanico).

- **Arriba a la izquierda,** la ficha de quien entró, con su menú, «Este celular». En el de Luis: Ajustes, la red, del
  bolsillo al precio y «Cerrar la sesión», sin «Cambiar de persona». En el de la tienda, sin nadie, el ícono de la
  tienda; con alguien, su ficha y, a 8 dp, «Salir» a la vista ([quién tiene la app](#quién-tiene-la-app)). A su lado,
  el estado de la red **solo si algo falla** («Red lenta», «Sin red · 3 por subir»), como los chips de la caja.
- **Arriba a la derecha,** la linterna del escáner, como hoy.
- **Abajo, el panel del modo** y la barra de modos: **Precio · Ticket · Conteo**. Se cambia tocando el nombre; deslizar
  sobre la cámara no cambia de modo, para no cambiarlo sin querer al apuntar. Si hay un ticket armado, «Ticket» lleva
  cuántos artículos trae.
- **Al final de esa barra, el botón rápido:** lo demás de cada modo (buscar, impresiones, mandar…), con un toque o con
  el gesto ([abajo](#el-botón-rápido)).
- **El asidero** (o tocar el encabezado) abre el modo completo: el ticket entero o «Lo contado». Mientras está abierto,
  las lecturas no cuentan.
- **La app abre en el último modo usado** (el de la tienda, en Precio si no hay nadie adentro; si alguien sigue en su
  sesión, donde iba). El mosaico y los accesos directos abren uno en particular ([del bolsillo al
  precio](#del-bolsillo-al-precio)).
- **El panel cabe en el espacio bajo la franja** (unos 280 dp en un celular chico y 360 en el Pixel): el encabezado, lo
  último leído y los modos con el botón rápido. Sin una fila de botones, ese espacio es para la lista. Lo demás se ve
  al jalar.

### El botón rápido

Lo pidió el dueño (2026-11-03): lo demás de cada modo en un solo botón, abajo a la derecha, para usarlo con una mano.
Pulsarlo abre tres botones chicos; sin soltar, se desliza hacia uno y al soltar se abre. Con la práctica es un gesto:
pulsar y deslizar hacia arriba es «mandar», sin buscar con la vista.

```
┌──────────────────────────────────────────┐
│ │                                      │ │  la franja sigue a la vista: el menú no la tapa
│ └──────────────────────────────────────┘ │  ← 68 %
│ ╭───────────────── ══ ─────────────────╮ │  el velo atenúa solo el panel
│ │ Ticket · 4 art.             $129.50  │ │
│ │      [ Pulsa y desliza sin soltar ]  │ │  la pista, mientras se abra con toques
│ │              [ ↑ Mandar a Caja 2 ]   │ │
│ │                                (>)   │ │  ↑ lo entrega: abre «¿A qué caja?»
│ │        [ ↖ Impresiones ] (I)         │ │  ↖ lo propio del modo
│ │                                      │ │
│ │          [ ← Buscar ] (Q)     [ × ]  │ │  ← siempre buscar · el abanico cambia a ×
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

`(>)` mandar · `(I)` impresiones · `(Q)` buscar · `[ × ]` el botón rápido, abierto (cerrado es `[···]`: tres puntos en
abanico). Los modos quedan bajo el velo.

- **Al final de la barra de modos,** a la derecha, al alcance del pulgar. Reemplaza la fila de botones que iba bajo la
  lista: el panel gana 68 dp, un renglón más del ticket en los dos celulares (en el chico, de 116 a 184 dp). Flotando
  sobre el panel taparía los importes y el − y + de los renglones de abajo.
- **Cabe a 360 dp** (medido con Roboto): quedan 260 dp para los modos, 84 cada uno, y el más ancho, «Conteo» con su
  ícono, mide 71. El número del ticket va sobre el ícono, como las burbujas de Material, las guías de diseño de Android:
  junto al nombre, «Ticket 14» pide 101 dp y aprieta a los otros dos.
- **El ícono no es un «+».** El + es el «Uno más» del renglón, en la misma columna (en el chico, 130 dp más arriba), y
  en Precio se leería «agregar». Son tres puntos en abanico, que no salen en otro lado, y cambian a × al abrir.

#### Qué lleva cada modo

| Modo | ↑ Lo entrega | ↖ Lo del modo | ← Buscar |
|---|---|---|---|
| Ticket | **Mandar a Caja 2**: abre «¿A qué caja?» | **Impresiones**, con cuántas hay por cobrar | **Buscar** y agregar |
| Precio | **Enseñar al cliente**: el precio en grande | — | **Buscar** y ver el precio |
| Conteo completo | **Guardar 13**: guarda lo de este lugar; el renglón de lo último ofrece «Quitar» | **Lo contado** | **Buscar** y contar |
| Conteo del día | **Es todo · 37**: abre «Aplicar 37 piezas / Falta un lugar» | **Lo contado** | **Buscar** y contar |

- **Cada dirección quiere decir siempre lo mismo,** en los tres modos y en los dos celulares, para aprender el gesto una
  vez: ← busca; ↑ entrega lo que se tiene (el ticket a la caja, el precio al cliente, lo contado al POS); ↖ es lo propio
  del modo. Si un modo no tiene algo para una dirección, ese lugar queda vacío y se porta como cancelar: las demás no se
  recorren.
- **Igual en los dos celulares:** Precio y Conteo son los mismos en el celular de una persona y en el de la tienda, que
  no tiene Ticket.
- **«Mandar» va en ↑, el botón rápido hacia arriba.** Es lo que se hace al terminar cada ticket, y el gesto lo hace sin
  buscarlo. Un gesto equivocado no manda nada: ↑ abre «¿A qué caja?», donde se toca «Mandar», y esa hoja no acepta el
  toque de rebote ([cómo se usa](#cómo-se-usa)). Lo que se pierde es ver a qué caja va sin abrir nada; lo dice el nombre
  de la opción en cuanto se pulsa. En la primera versión solo arman tickets personas de confianza.
- **En Precio el gesto no cambia nada.** ↑ y ↖ son las direcciones que más se confunden, y en Precio «el precio nunca
  agrega al ticket». Por eso «Al ticket» sale del botón y va a la vista en la tarjeta del precio, como «Agregarlo al
  ticket» en el F9 de la caja ([Precio](#precio-el-verificador-f9-en-el-celular)), y ↖ queda vacío.
- **En Conteo, ↑ depende del conteo** ([el botón rápido en Conteo](#el-botón-rápido-en-conteo)). En el completo guarda
  directo, porque la captura se quita. En el del día abre la confirmación: el POS no deja corregirlo («Ya se contó hoy:
  si hubo un error, se corrige con un ajuste»; la diferencia entra al kardex al aplicarlo) y es el total de la tienda,
  así que aplicar los 13 de un lugar dejaría fuera los 24 de la bodega.
- **El gesto nunca hace lo que no se corrige:** «Mandar» y «Es todo» se confirman en su hoja, «Guardar» guarda una
  captura que se quita y lo demás solo abre o enseña.
- **Lo apagado se ve apagado** y se queda en su lugar, con su razón: «Mandar» con el ticket vacío, «Impresiones» sin
  red, «Enseñar» sin producto, «Guardar» y «Es todo» sin nada contado.
- **Sin opciones, sin botón:** en Conteo, mientras no se cuenta nada (antes de elegir qué contar; en el prototipo,
  también en el piloto), el lugar del botón queda vacío. Los modos no se recorren: no se mueven bajo el pulgar al
  cambiar de modo.

#### Cómo se usa

| Lo que hace la persona | Lo que pasa |
|---|---|
| Toca el botón: se mueve menos de 8 dp y suelta antes de 400 ms | Los tres quedan abiertos para tocar uno |
| Pulsa, desliza hacia una opción (32 dp o más, o hasta quedar encima de ella) y suelta | Se abre esa opción |
| Pulsa y lo sostiene, o se mueve un poco, y suelta sin llegar a una opción | Se cierra sin hacer nada |
| Desliza y suelta en el centro, abajo, a la derecha o en un hueco | Se cierra sin hacer nada |
| Suelta en una opción apagada | Se cierra; vibra de rechazo y un aviso dice por qué (en Conteo, la razón sale en el lugar del producto, como lo que no va, y el lector de pantalla la lee; nunca encima del renglón de lo último) |
| Con el menú abierto, toca una opción | Se abre |
| Con el menú abierto, toca la ×, fuera o «atrás» | Se cierra |
| Con el menú abierto, pulsa la × y desliza | Elige, igual que desde cerrado |

- **La regla corta:** solo un toque deja el menú abierto; soltar de cualquier otra forma elige o cierra. Así el pulgar
  que se apoya al acomodar la mano no deja el menú abierto, y cancelar hacia la orilla funciona aunque de ahí a la
  orilla no haya 32 dp. Al salir de la app o apagarse la pantalla, se cierra.
- **Gana la opción bajo el dedo.** Si el dedo está a 28 dp o menos del centro de una opción, se elige esa. Si no está
  sobre ninguna (el gesto a ciegas), decide el ángulo desde donde se pulsó. Con el puro ángulo, quien apuntaba a lo que
  veía desde una esquina del botón encendía otra opción: 40 de 867 casos, en una rejilla de 4 dp sobre la zona de
  toque. Le pasaba a quien aprende, porque el menú sale al instante.
- **Sale al instante,** como pidió el dueño, sin el retraso de un tercio de segundo de los menús de marcado clásicos.
  Ese retraso es para no estorbar a quien ya se sabe el gesto; aquí no estorba (tres opciones, lejos del pulgar) y
  verlas encenderse confirma lo que se va a elegir. El gesto no espera la animación: un deslizamiento rápido elige
  aunque los botones no hayan terminado de salir.
- **El rebote no cuenta.** La hoja que abre ↑ pone su botón justo bajo el dedo que soltó, y un segundo toque por reflejo
  confirmaría: en el chico, «Mandar a Caja 2» va de 581 a 637 dp y «Aplicar 37 piezas» de 588 a 644; en el Pixel,
  «Mandar» va de 856 a 912, y el dedo suelta ↑ entre 857 y 913. Por eso, por 300 ms desde que el botón elige o se
  cierra, solo él responde: la hoja que abrió (tarda 200 ms en subir), el precio en grande, el panel y el aviso no
  aceptan toques que empiecen en ese tiempo. El botón principal de la hoja se enciende al terminar; la × de arriba de la
  hoja sí responde.
- **Las lecturas siguen contando con el menú abierto,** como al tocar − o +. El escáner no se toca: si lee, pinta su
  verde, y eso la app no lo puede callar; si lo leído no contara, el verde mentiría. Por eso el velo atenúa solo el
  panel y la franja sigue a la vista. Las etiquetas se ponen al día con cada lectura, sin perder el dedo («Guardar 13»
  pasa a «Guardar 14»). «Guardar» y «Es todo» usan lo contado al soltar. «Enseñar» enseña el producto que estaba en la
  tarjeta al bajar el dedo, no uno leído a medio gesto, y la pantalla grande dice su nombre.
- **Con una hoja o el precio en grande, no cuentan.** El visor queda tapado o atenuado. La vibración sí se puede callar:
  la app la apaga mientras tanto y al cerrar devuelve la de Ajustes, para no vibrar «leído» por lo que no cuenta. El
  verde no se puede callar, pero queda bajo la hoja.
- **En Conteo, la repetición a la vista se apaga al entrar al modo,** una sola vez, si se aprueba
  ([preguntas de Conteo](#preguntas-de-conteo)): así sostener el producto con el menú abierto no lo cuenta otra vez.
  Abrir el menú no cambia la configuración del escáner: cambiar la repetición rehace el `Confirmador`, que vuelve a
  leer lo que está a la vista, y con el producto en la mano sumaría una pieza cada vez.
- **Los avisos esperan al menú.** Un aviso que llega con el menú abierto no se ve (taparía ↑ y ↖) y su tiempo no corre;
  sale al cerrarlo, con su tiempo completo. Así «Quitado del ticket · Deshacer» o el «Deshacer» de «Al ticket» no se
  vencen sin que nadie los vea. En Conteo no hay avisos flotantes: lo último va en su renglón fijo y lo que no va, en el
  lugar del producto ([la pantalla de Contar](#la-pantalla)).

#### Medidas

| Qué | Medida | Por qué |
|---|---|---|
| El botón | 56 dp, esquinas de 16 dp, del color del modo; a 16 dp de la orilla y a 4 dp de la barra de gestos, centrado con los modos | Como el botón flotante de Material y el mínimo de la app para lo principal |
| Su zona de toque | 64 dp | Que el pulgar le atine a la esquina sin mirar |
| Los tres chicos | 48 dp; el elegido crece a 56 | Para tocarlos con el menú abierto |
| Dónde salen | A 88 dp del centro del botón (de centro a centro), a 90°, 135° y 180° | Fuera del pulgar, que llega desde abajo a la derecha; 19 dp entre uno y otro |
| La zona de cada opción | 28 dp alrededor de su centro (56 de ancho); entre una y otra quedan 11 dp | Con el dedo encima, gana esa. Desde la esquina de la zona de toque se llega con 15 dp, casi el doble del margen de un toque |
| Las etiquetas | 32 dp de alto, 14 sp, con su flecha. La de ↑, arriba de su botón; las de ↖ y ←, a su izquierda | A 88 dp no caben todas a la izquierda: la de ↑ chocaría con el botón de ↖ |
| Desde dónde se mide el ángulo | Desde donde se pulsó, no desde el centro del botón | Pulsar en la orilla no elige nada |
| El umbral | 32 dp (5 mm en el Pixel); se desarma bajo 24 dp | Un toque se mueve menos de 8 dp (el margen que el sistema le da a un toque): 32 es cuatro veces eso |
| Un toque | Menos de 8 dp y menos de 400 ms | El margen de un toque y la pulsación larga del sistema (`ViewConfiguration`); si la persona alargó la pulsación en Accesibilidad, se respeta |
| ↑ | De 45° a 112.5° (0° es a la derecha y 90° arriba) | Cada frontera va a medio camino entre dos botones; ↑ se abre hacia la derecha, donde no hay nada |
| ↖ | De 112.5° a 157.5° | Entre ↑ y ←, partido a la mitad con cada uno |
| ← | De 157.5° a 225° | Se abre hacia abajo, donde no hay nada |
| Cancelar | Lo demás (la mitad de abajo a la derecha) y el hueco de un modo | Hacia la orilla, lejos de las opciones |
| En las fronteras | 4° de histéresis para la opción de antes, que se revisa primero | Que el resaltado y la vibración no tiemblen, de los dos lados de cada frontera |
| El rebote | 300 ms en que solo responde el botón | Lo que dura un doble toque en Android; la hoja tarda 200 ms en subir |

- **Dónde cae, medido** con Roboto: en el chico (360 × 740) el botón queda en (316, 688); ↑ en (316, 600), ↖ en
  (254, 626) y ← en (228, 688). Lo más alto del menú, la pista, empieza en 490 (al 200 % va arriba de la etiqueta de
  ↑, desde unos 465); el panel empieza en 453 y la franja acaba en 443. En el Pixel (448 × 997), el botón en (404, 945)
  y el menú desde 748; el panel empieza en 630. Nada se encima ni se sale del margen de 16 dp.

#### Lo que se ve y se siente

| Momento | Se ve | Vibra (Android 14 o más) |
|---|---|---|
| Pulsar | El velo sobre el panel; los tres salen del botón (150 ms) con su nombre; el abanico cambia a × | Nada |
| Armarse hacia una opción, o llegar encima de ella | La opción crece y toma el color del modo, con su etiqueta | `GESTURE_THRESHOLD_ACTIVATE` |
| Pasar a otra opción | Se resalta la nueva | `SEGMENT_TICK` |
| Volver al centro o ir a cancelar | Nada resaltado; hacia cancelar se marca la × | `GESTURE_THRESHOLD_DEACTIVATE` |
| Soltar en una opción | Se abre | `GESTURE_END` |
| Soltar en una apagada | El aviso con la razón (en Conteo, en el lugar del producto; nunca encima del renglón de lo último) | `REJECT` |

- **Distinta del escáner:** la lectura confirma con `CONFIRM` (antes de Android 11, con `VIRTUAL_KEY`). El menú nunca
  usa esas dos, para no confundir «leído» con «elegido».
- **En Android viejo:** antes de Android 14 (API 34) no hay `GESTURE_THRESHOLD_*` ni `SEGMENT_TICK`: se usa
  `CLOCK_TICK` al resaltar y nada al desarmar. Antes de Android 11 (API 30), al elegir vibra `CONTEXT_CLICK` (API 23) y
  al rechazar `LONG_PRESS`, como el rechazo de `Retroalimentacion`; si en ese celular `CLOCK_TICK` y `CONTEXT_CLICK` se
  sienten iguales, no se vibra al resaltar. Todas respetan la «Respuesta táctil» del sistema.
- **Sin animaciones** si el sistema las quitó: el menú aparece y se va de golpe.

#### Accesibilidad y los otros gestos

- **TalkBack** (el lector de pantalla de Android): el botón dice «Opciones del ticket, cerradas»; con doble toque se
  abren y el estado dice cuáles hay («abiertas: Mandar a Caja 2, Impresiones, Buscar»), en lugar de mover el foco, que
  TalkBack no siempre sigue. El menú es un grupo con orden fijo: ↑, ↖, ← y la × al final. Las tres también van en las
  acciones del botón (el menú de TalkBack), para elegir sin abrirlo: es el atajo que equivale al gesto. Las apagadas
  dicen por qué. Mientras está abierto, el panel de atrás sale del árbol de accesibilidad.
- **Teclado:** Tab llega al botón; Enter o Espacio abre y lleva el foco a la primera; las flechas y Tab recorren; Enter
  elige; Esc cierra y regresa al botón.
- **Letra al 200 %:** las etiquetas crecen (26 sp, 48 dp de alto) y no se enciman: «↑ Enseñar al cliente» cabe arriba de
  su botón, y la más ancha a la izquierda, «↖ Impresiones», queda dentro del margen de 16 dp. La barra no cabe con
  nombres e íconos (Android lleva 14 sp a 26 sp y «Conteo» con su ícono pide 110 dp de 84): los modos no elegidos se
  quedan con su ícono (48 dp; el nombre, para TalkBack y al dejar el dedo) y el elegido con los dos. 130 + 48 + 48 cabe
  en 260. Sin el botón tampoco cabría: «Conteo» pediría 110 de 107.
- **Zurdos:** Ajustes › «Botón rápido a la izquierda», por persona (sin nadie adentro, el del celular). El botón pasa al
  principio de la barra y todo va en espejo: ↑, ↗ y →, las etiquetas a la derecha y cancelar abajo a la izquierda. ↑
  sigue entregando y → (hacia el centro) busca.
- **El arrastre del panel** vive en el asidero y el encabezado, no en la barra. Además el botón se queda con el gesto
  desde que se pulsa, así que deslizar hacia arriba nunca jala el panel.
- **El gesto de «atrás»** entra desde la orilla derecha (más ancho si se sube su sensibilidad) y el botón está a 16 dp:
  su zona de toque cae adentro, y pulsar ahí y deslizar ← sería «atrás». La zona del botón se excluye del gesto
  (`systemGestureExclusion`, desde Android 10, que es cuando llegó el gesto; Android deja hasta 200 dp por orilla). En
  zurdo, la orilla izquierda.
- **El gesto de inicio** (abajo) no se puede excluir: el botón va arriba de la barra de gestos. Deslizar hacia abajo
  después de pulsar no es inicio, porque empezó en el botón: cancela.
- **Con el menú abierto,** tocar el visor lo cierra sin enfocar (una capa invisible se queda con ese toque) y los modos
  quedan bajo el velo.

#### Cómo se aprende

- **El panel vacío lo nombra por lo que hace:** «Escanea lo que lleva el cliente. Buscar, impresiones y mandar: en el
  botón de abajo a la derecha.» En Precio, «Enseñar y buscar: en el botón de abajo a la derecha» (y, en el de Luis, «Si
  se lo lleva, “Al ticket” en la tarjeta»). En Conteo, igual con lo suyo. Con «Botón rápido a la izquierda», el texto
  dice «abajo a la izquierda».
- **La primera vez en cada modo,** una burbuja sobre el botón dice sus direcciones («Pulsa y desliza: ↑ mandar ·
  ↖ impresiones · ← buscar»; en Precio, «↑ enseñar · ← buscar»; en el conteo del día, «↑ es todo»). Se va al abrir el
  menú en ese modo.
- **Mientras se abra con toques,** arriba del menú sale «Pulsa y desliza sin soltar», hasta que la persona elija tres
  veces deslizando.
- **Las pistas van por persona,** como «Botón rápido a la izquierda»: el celular de la tienda lo usan varios por turnos
  y, por celular, el primero se llevaría las pistas. Ajustes › «Volver a ver las pistas».
- **Las etiquetas llevan su flecha** («↑ Mandar a Caja 2») y los tres salen del botón hacia su lado: la animación enseña
  la dirección.
- **Se mide en la tienda,** en el celular: cuántas veces se abre con toque y cuántas con el gesto, y cuántas veces una
  opción se cierra en menos de 2 s sin hacer nada (se eligió la que no era). Si ↖ junta los errores, se mueven las
  fronteras. Son contadores en el celular, sin el nombre de nadie, en Ajustes › Herramientas de medición, y salen solo
  con el CSV de la sesión de prueba, como las mediciones del escáner: la app no manda estadísticas.

#### Cómo se hace

En Compose, un `pointerInput` sobre el botón. Compose le sigue mandando el dedo al botón aunque salga de él, como hace
`setPointerCapture` en la web:

```kotlin
enum class Lugar { ARRIBA, DIAGONAL, LADO }

/** Las opciones respecto al centro del botón, en dp (y crece hacia abajo); en zurdo, en espejo. */
private val CENTROS = mapOf(
    Lugar.ARRIBA to Offset(0f, -88f),
    Lugar.DIAGONAL to Offset(-62.2f, -62.2f),
    Lugar.LADO to Offset(-88f, 0f),
)

/** Los sectores, desde donde se pulsó: 0° a la derecha y 90° arriba. Lo demás cancela. */
private val SECTORES = listOf(
    Triple(Lugar.ARRIBA, 45.0, 112.5),
    Triple(Lugar.DIAGONAL, 112.5, 157.5),
    Triple(Lugar.LADO, 157.5, 225.0),
)

/** La opción que elige el dedo, o null (cancela). [hay]: las que tiene el modo; el hueco cancela. */
fun lugarDe(desdeBoton: Offset, desdePulso: Offset, armado: Boolean, antes: Lugar?, hay: Set<Lugar>): Lugar? {
    // Sobre una opción (a 28 dp de su centro), esa
    CENTROS.entries.firstOrNull { (l, c) -> l in hay && (desdeBoton - c).getDistance() <= 28f }?.let { return it.key }
    if (!armado) return null
    val g = (Math.toDegrees(atan2(-desdePulso.y, desdePulso.x).toDouble()) + 360) % 360
    // La de antes primero, con 4° de más de cada lado; si no, las demás sin histéresis
    val s = SECTORES.firstOrNull { (l, de, a) -> l == antes && g >= de - 4 && g < a + 4 }
        ?: SECTORES.firstOrNull { (_, de, a) -> g >= de && g < a }
    return s?.first?.takeIf { it in hay }
}

Modifier
    .systemGestureExclusion() // «atrás» no se lleva lo que empieza en el botón
    .pointerInput(zurdo) {
        awaitEachGesture {
            val abajo = awaitFirstDown().also { it.consume() } // el panel no se arrastra
            val yaAbierto = menu.abierto
            menu.abrir() // al instante; guarda lo que hay en la tarjeta, para «Enseñar»
            val sx = if (zurdo) -1f else 1f
            fun enDp(o: Offset) = Offset(o.x * sx, o.y) / density
            val centro = Offset(size.width / 2f, size.height / 2f)
            var lugar: Lugar? = null
            var armado = false
            var lejos = 0f
            var ultimo = abajo
            var cancelado = false
            while (true) {
                val c = awaitPointerEvent().changes.firstOrNull { it.id == abajo.id }
                if (c == null || (!c.pressed && c.isConsumed)) { cancelado = true; break } // el sistema se lo llevó
                val d = enDp(c.position - abajo.position) // desde donde se pulsó
                lejos = max(lejos, d.getDistance())
                armado = d.getDistance() >= (if (armado) 24f else 32f)
                lugar = lugarDe(enDp(c.position - centro), d, armado, lugar, menu.lugares)
                menu.resaltar(lugar, cancelar = armado && lugar == null) // vibra solo si cambió
                c.consume()
                ultimo = c
                if (!c.pressed) break
            }
            val toque = lejos < viewConfiguration.touchSlop / density &&
                ultimo.uptimeMillis - abajo.uptimeMillis < viewConfiguration.longPressTimeoutMillis
            when {
                cancelado -> menu.cerrar()
                lugar != null -> menu.elegir(lugar) // la apagada: rechazo y su razón
                toque && !yaAbierto -> Unit // solo un toque deja abiertas las tres
                else -> menu.cerrar() // sostuvo, deslizó sin elegir o tocó la ×
            }
        }
    }
    .focusable()
    .onKeyEvent {
        val tecla = it.key == Key.Enter || it.key == Key.NumPadEnter || it.key == Key.Spacebar
        if (tecla && it.type == KeyEventType.KeyUp) menu.alternar(enfocarLaPrimera = true)
        tecla // se queda con la tecla al bajar y al subir
    }
    .semantics {
        role = Role.Button
        contentDescription = menu.nombre // «Opciones del ticket», «del precio» o «del conteo»
        stateDescription = if (menu.abierto) "abiertas: " + menu.opciones.joinToString { it.nombre } else "cerradas"
        traversalIndex = 3f // en el grupo del menú: después de ↑, ↖ y ←
        onClick { menu.alternar(); true }
        customActions = menu.opciones.map { o -> CustomAccessibilityAction(o.nombre) { menu.elegir(o.lugar); true } }
    }
```

- **La histéresis** va en `lugarDe`: la opción de antes se revisa primero, con 4° de más de cada lado. Si se revisara en
  orden, solo valdría en un lado de cada frontera.
- **Las etiquetas** («Guardar 14») son texto de Compose: se ponen al día con cada lectura sin tocar el `pointerInput`,
  que sigue con el dedo (su llave es solo `zurdo`).
- **El grupo del menú:** `isTraversalGroup` en el menú y `traversalIndex` en cada opción (↑, ↖, ←) y en el botón, al
  final. Mientras está abierto, el panel lleva `hideFromAccessibility()` (Compose del BOM 2026.02).
- **El rebote:** una capa sobre toda la pantalla, con `awaitFirstDown(pass = PointerEventPass.Initial)`, consume los
  toques que bajan antes de 300 ms desde que el botón eligió o se cerró, salvo los del botón.
- **Con una hoja o el precio en grande:** `controlador.configurar(config.copy(vibracion = false))`; al cerrar, la
  vibración de Ajustes. Cambiar solo eso no rehace el motor ni el `Confirmador` (`Analizador.configurar`). Para esto no
  se toca `msParaRepetirAVista`. En Conteo, si se aprueba, `msParaRepetirAVista = 0` al entrar al modo y lo de antes al
  salir.
- **La vibración,** con `View.performHapticFeedback` y sus revisiones de versión, como `Retroalimentacion`; «atrás» y
  Esc, con un `BackHandler` mientras está abierto.
- **El botón y el menú van en la capa de encima del escáner,** sobre el hueco que les deja la barra: así quedan arriba
  del velo, que cubre el panel.
- **En el prototipo, lo mismo con Pointer Events:** `pointerdown` abre y llama a `setPointerCapture`; `pointermove`
  mide desde donde se pulsó y desde el centro del botón (para la zona), entre la escala del marco del celular (en la PC
  va reducido), para que sean dp; `pointerup` decide y `pointercancel` cierra. El botón lleva `touch-action: none` (si
  no, el navegador se queda con el deslizamiento) y `navigator.vibrate` vibra en Chrome de Android. Mientras se pulsa,
  nada rehace el botón: una lectura solo cambia el texto de las etiquetas.

#### Preguntas del botón rápido

- [ ] ¿«Mandar» va dentro del botón rápido, en ↑ (lo propuesto)? Un error solo abre «¿A qué caja?», que no acepta el
  toque de rebote, y el panel gana un renglón. ¿O siempre a la vista, como antes?
- [ ] En Precio, ¿«Al ticket» como botón a la vista en la tarjeta, como «Agregarlo al ticket» del F9, con ↑ «Enseñar al
  cliente» (lo propuesto)? Así el gesto en Precio no cambia nada. ¿O «Al ticket» en ↑, con «Deshacer»?
- [ ] Con el menú abierto, ¿las lecturas siguen contando (lo propuesto)? El escáner igual pinta su verde.
- [ ] Con el Pixel en una mano, ¿la esquina de abajo se alcanza bien con el pulgar? Si no, el botón sube a flotar sobre
  el panel, unos 10 mm más arriba, a cambio de tapar la esquina de la lista.
- [ ] ¿Se siente bien que el menú salga al instante (lo pedido), o que salga solo si se deja el dedo un momento?
- [ ] Si se pulsa sin moverse y se suelta después de un momento, ¿el menú se cierra (lo propuesto)? Así el pulgar
  apoyado no lo deja abierto.
- [ ] ¿«Botón rápido a la izquierda» y las pistas van por persona (lo propuesto) o por celular?

### Precio: el verificador (F9) en el celular

```
┌──────────────────────────────────────────┐
│ ╭──────────────────────────────────────╮ │
│ │ [foto]  Cuaderno profesional cuadro  │ │
│ │         chico 100 hojas              │ │
│ │         Escolar · 7509990100019      │ │
│ │         $46.00        [+ Al ticket]  │ │  el precio en grande; a su lado, «Al ticket», como en el F9
│ │ (Desde 12: $41.00 c/u) (Exist.: 8)   │ │  mayoreo y existencia
│ │ (Precio) (Ticket) (Conteo)    [···]  │ │  ↑ enseñar al cliente · ← buscar
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

`[···]` el botón rápido (tres puntos en abanico).

- **Escanear solo enseña.** Nada entra al ticket, como el F9 de la caja; el ámbar es el mismo.
- **Lo que sale:** la foto (la de 240 px que guarda el POS, o las iniciales), el nombre, el departamento y el código, el
  precio en grande, la promoción de hoy, **el mayoreo** («Desde 12: $41.00 c/u») y la existencia («Queda 1» en ámbar
  cuando queda poco; en el celular de la tienda no sale, y lo que se está contando dice «Existencia oculta · Ya
  terminé», [a ciegas](#a-ciegas); TalkBack agrega qué se cuenta). El precio es el de lo escaneado: el código de la caja
  de 12 da el de la caja y, al lado, el de cada pieza. Sin redondeo: el redondeo al peso solo se aplica al cobrar en
  efectivo.
- **El mayoreo** no lo enseña todavía el F9 de la caja, aunque su propuesta lo pide; en el piso es lo primero que se
  pregunta después del precio («¿y si llevo 12?»).
- **↑ «Enseñar al cliente»** pone el nombre y el precio a pantalla completa, para voltear el celular: el producto que
  estaba en la tarjeta al bajar el dedo. Se toca para regresar. En Precio, ↖ queda vacío: el gesto no cambia nada.
- **«Al ticket»,** un botón de la tarjeta como «Agregarlo al ticket» del F9, agrega uno sin salir de Precio. Va a la
  derecha del precio (48 dp): lo que se agregue abajo, como «Existencia oculta», no lo baja ni lo saca del panel. Su
  aviso sale arriba, para no tapar el botón (otro toque agrega otro), y nombra el producto: «Cuaderno profesional… al
  ticket · 5 art. · $97.50», con «Deshacer». En el celular de la tienda y sobre el bloqueo no está.
- **Lo que no está en el catálogo** lo dice en rojo, con el código, y ofrece buscar por nombre.
- **Cada consulta cuenta** para «lo más preguntado» de Productos, como en la caja (`POST /api/consultas`).

### Ticket: la preventa

- **Cada lectura suma uno.** El mismo código suma a su renglón y lo sube; el último va arriba, resaltado, con − y +
  (en 1, el − es el bote; quitar se puede deshacer). Tocar la cantidad abre el teclado de números: «20» copias. Es lo
  que ya hace el modo venta de hoy, con nombres y precios.
- **El renglón dice lo que importa:** «Queda 1» primero, para que no se corte en el chico; luego el precio por pieza y
  «Mayoreo» cuando llega a la escala (el total ya va con el precio de mayoreo, como en la caja).
- **Un código que no está en el catálogo** entra en rojo, «Código sin producto»: en la caja se da de alta o se cobra
  como artículo común. No se pierde.
- **Los precios los pone la caja** al recibirlo, con su catálogo; el celular los enseña para que el cliente sepa cuánto
  lleva. Desde el celular no se cambian precios ni se hacen descuentos (la propuesta del POS lo decidió así).
- **El ticket completo** (jalar el asidero): todos los renglones con − y + e importe, el renglón de impresiones,
  «Buscar», «Impresiones», deshacer, el total y «Mandar a Caja 2». «Empezar otro ticket» lo vacía (se puede deshacer).
- **Se guarda en el celular** mientras se arma: cerrar la app o que se apague la pantalla no lo pierde.

#### Lo que no se escanea: buscar

- **El campo va abajo, pegado al teclado,** y los resultados encima: el mejor queda junto al campo y junto al pulgar.
  Con el teclado abierto, la mitad de arriba de la pantalla no se alcanza con una mano.
- **Las reglas de la caja:** pedazos de palabra en cualquier orden («lus az» da el papel lustre azul), los sinónimos de
  la tienda, un error de dedo, lo que se eligió antes con ese texto primero y luego lo más vendido; hasta 30.
- **Sin escribir nada** salen los rápidos (Copia B/N y CURP) y lo más vendido.
- **En los tres modos:** en Ticket agrega, en Precio enseña y en Conteo empieza a contar ese producto.
- **Dónde corre:** la búsqueda del POS está en su núcleo, en C# puro (unas 1,000 líneas, con sus pruebas). Se lleva a
  Kotlin con los mismos casos de prueba para que busque en la copia del celular, también sin red. Mientras, la API de
  hoy (`GET /api/productos/buscar`) sirve con red.

#### Las impresiones

```
┌──────────────────────────────────────────┐
│ <  Impresiones por cobrar                │
│    De todas las PC · lo nuevo arriba     │
│ (Todas) (CAJA2) (PRINCIPAL)              │
│ ┌ Hoy 14:50–14:51 · lote de 2 · $14 ───┐ │
│ │ ┌──────────────┐  ┌──────────────┐   │ │  la miniatura de la primera hoja
│ │ │ ▬▬▬▬▬        │  │ ▒▒▒▒ ●       │   │ │
│ │ │ ▬▬▬▬▬▬▬      │  │ ▬▬▬▬▬        │   │ │
│ │ └──────────────┘  └──────────────┘   │ │
│ │ tarea_historia.pdf #2481 · portada…  │ │  los de Control Print, con su número
│ │ 3 págs · B/N       1 pág · Color     │ │
│ │ 14:51 · CAJA2  $6  14:50 · PRINC. $8 │ │
│ │                     [Elegir los 2]   │ │
│ └──────────────────────────────────────┘ │
│ [+ Agregar 2 · $14.00                 ]  │
└──────────────────────────────────────────┘
```

- **Las mismas que F2:** las que mide el POS (el agente de la caja 2) y las de la cola de Control Print de la caja
  principal (solo se leen), mezcladas, lo nuevo arriba.
- **En lotes,** como F2: las que salieron con 2 minutos o menos entre una y otra van juntas, con «Elegir los 2». Las
  sueltas van de dos en dos.
- **Cada tarjeta:** la miniatura (en gris si es blanco y negro), el documento (con su número si es de Control Print),
  páginas, color y tamaño, la hora, la PC y el precio. Se toca la que coincide con las hojas que trae el cliente.
- **Las que dicen «Revisar hojas»** no se eligen desde el celular: las de Control Print se resuelven en Control Print,
  en la PRINCIPAL (cobrar lo que salió o no vendida), y después salen para cobrarse; las de la caja 2, en su F2. Tampoco
  se marcan «No vendida» ni «Cobrar lo que salió» desde el celular.
- **Filtrar por PC** con las fichas de arriba, para cuando son muchas.
- **En el ticket** van en un renglón, «Impresiones · 3 trabajos», con sus miniaturas chicas y la suma. Al cobrar, la caja
  arma sus líneas «Impresiones» y «Copias» como siempre, con el precio de cada trabajo.
- **Sin red no se ven:** viven en el servicio (y en Control Print), y la caja tampoco cobra impresiones sin conexión. Se
  agregan en la caja con F2.
- **Que dos no tomen la misma:** hoy nada aparta una impresión elegida. Si el celular la pone en un ticket y otra caja
  la cobra antes, al cobrar el segundo la caja avisa y hay que quitarla. Se propone apartarla mientras está en un ticket
  del celular (F2 diría «en el ticket de Luis») y cerrar un hueco del cobro: el POS revisa que una impresión suya siga
  por cobrar *antes* de tomar el candado de la base, así que dos cobros al mismo tiempo podrían cobrarla dos veces. En
  el piloto no pasa (solo cobra la caja 2), pero con dos cajas sí.
- **Con las de Control Print, al revés, nadie avisa:** el POS solo las lee, así que la que se cobra en la caja 2 sigue
  «por cobrar» en Control Print, y en la PRINCIPAL se puede cobrar otra vez (F2 lo advierte arriba de su lista y luego
  enseña «Cobradas dos veces»). Esto ya puede pasar en el piloto, y apartarla tampoco llega a Control Print.

#### Mandar a una caja

- **↑ «Mandar a Caja 2»** (el botón rápido hacia arriba) abre «¿A qué caja?»: cada caja con quién atiende y cuántos
  tickets esperan; la apagada no se puede elegir. La última usada ya viene elegida, así que casi siempre es tocar
  «Mandar»: el gesto nunca manda solo, y la hoja no acepta el toque de rebote (su botón se enciende a los 300 ms).
- **Llega en espera**, con quien lo mandó en lugar de la hora («Luis · $97.50»), entra con su efecto y no interrumpe el
  cobro de la caja (la propuesta del POS, «Desde el celular»).
- **Al mandarlo,** el panel dice «Enviado a Caja 2 · 4 art. · $129.50» en verde y el siguiente escaneo empieza otro
  ticket.
- **En el piloto** solo la caja 2 tiene el POS: la caja 1 sale en gris, «Sigue con eleventa».
- **Sin red: el ticket como QR.** «Enseñar como QR» lo pone en grande (con el brillo al máximo). Lleva un renglón por
  producto, `2*7509990100019`, como si el lector los escaneara uno tras otro: entran al ticket que la caja tenga
  abierto, sin que la caja tenga que entender nada nuevo.
  - Va el código con que el producto está en el catálogo (el que encontró la búsqueda del núcleo), no lo que leyó la
    cámara: así la caja lo encuentra aunque todavía no pruebe las variantes de UPC y EAN.
  - Las impresiones no van en el QR: se agregan en la caja con F2.
  - Después de «Ya lo leyeron», el ticket se vacía y ya no se manda por la red.
  - **Falta probarlo en la tienda:** que el lector de la caja 2 lea QR y mande cada renglón con su Enter. El
    decodificador del puerto acepta hasta 64 caracteres por código (cada renglón usa unos 16) y separa por renglón con
    «Fin» automático (lo recomendado) o CRLF; con «Fin» en CR o en LF, el otro salto se ignora y los renglones se
    pegan. Por eso el QR separa cada renglón con CR+LF, que entra con «Fin» automático, CR, LF o CRLF.

### Conteo

Contar tiene su propia sección, más abajo: [contar](#contar). En corto: a ciegas y por lugares (anaquel, mostrador,
bodega); la pieza suma una y la caja suma la caja; el − quita la última lectura; el completo guarda solo lo de cada lugar
y el renglón de lo último lo dice, con «Quitar»; el del día suma los lugares y se aplica con «Es todo». En el piloto no
se cuenta.

## Contar

> **Propuesta completa, sin implementar todavía.** El dueño pidió verla (2026-11-03): «quiero ver la propuesta para
> contar aunque aún no se vaya a implementar». Va en la [entrega 6](#entregas), después del piloto. En el
> [prototipo](propuesta-interfaz/prototipo.html) se ve con «POS: Sin eleventa», o en el piloto con «Ver cómo se
> contará».

Lo contado cambia la existencia y no se deshace con un toque: es lo que menos perdona de la app. La app no inventa
reglas: usa las del POS y le pone la cámara, las cajas y sueltas, el lugar, el guardado solo, la bandeja de salida sin
red y una sola mano. Al leer el código del POS salieron huecos que la app sola no cierra (un conteo encima de otro, la
hora contra la que se compara, quién dice quién cuenta). Esos se le piden al POS
([lo que pide](#lo-que-pide-al-pos-para-contar)).

### Lo que ya hace el POS

Leído en su código (`ConteoDepartamento`, `ConteoCiclico`, `RegistroConteoCompleto`, `RegistroConteoCiclico`,
`RegistroAjustes`, `SeguridadCajas`, `TrastiendaEnElPiloto`) y en sus pantallas (`ConteoCompleto`, `ContarDepartamento`,
`RevisarConteo`, `ConteoDelDia`):

| | Conteo del día | Conteo completo |
|---|---|---|
| **Qué** | 15 productos al día que elige el POS: primero lo que tiene existencia negativa, luego lo que más vale y se vende, por los días que lleva sin contarse | Un departamento entero. Puede haber varios abiertos, uno de cada uno |
| **Quién lo abre** | Nadie: el POS lo arma la primera vez que se pide en el día | Quien tiene «Conteo completo de un departamento» (de inicio, el encargado y el dueño) |
| **Quién cuenta** | Quien tiene «Contar inventario» (de inicio, todos) | Igual |
| **Qué se escribe** | El total de la tienda: anaquel, mostrador y bodega | Lo de un lugar. Cada captura se suma a las de todos |
| **Contra qué se compara** | Lo que dice el sistema al aplicarlo (`ContarAsync`) | Lo que decía el sistema en la primera captura vigente de cada producto (`EsperadoDe`: la más vieja que no se ha quitado) |
| **Cuándo entra** | Al aplicar: la diferencia entra al kardex en ese momento | Al cerrar: la diferencia se suma a la existencia de ese momento. Lo que nadie contó queda en 0 |
| **Si quedó mal** | No se corrige: una vez por producto al día. Es un ajuste en la caja | Se quita la captura (cada quien las suyas; el encargado, cualquiera) y se cuenta otra vez |
| **A ciegas** | Antes de contar nadie ve el sistema; ya contado, quien ve costos ve la diferencia | Nadie lo ve. El encargado, solo al revisar |
| **Dónde, hoy** | Solo en la caja: F4 › «Conteo del día» (su tarjeta dice «12 por contar») y el chip «Conteo del día 3/15» abajo de la venta | La caja y la página `/conteo` en el navegador del celular |
| **En el piloto** | No hay | No se abre, no se captura ni se cierra. Lo abierto se cancela |

- **El completo ya evita duplicados:** cada captura lleva un número que pone quien captura; si llega dos veces, cuenta
  una. El del día no lo tiene.
- **Se vende mientras se cuenta:** el cierre suma la diferencia contra la primera captura vigente, así que lo que se
  vende o llega después sigue moviendo la existencia. Por eso importa en qué orden se cuentan los lugares ([el lugar y
  el orden](#el-lugar-y-el-orden)).
- **Una corrección en medio se cuenta dos veces.** Si mientras un departamento está abierto el conteo del día o un
  ajuste corrige uno de sus productos, el cierre lo corrige otra vez. Hay 50 y el sistema dice 40: el anaquel da 20
  (contra 40), el del día aplica 50, la bodega da 30 y al cerrar queda 50 + (50 − 40) = 60. Hoy nada lo impide: ni la
  selección del día (`CandidatosAsync`), ni `ContarAsync`, ni los ajustes miran si el departamento está abierto (C9).
- **El del día compara al aplicarlo:** si el anaquel da 13 a las 10:00, se venden 3 y a las 11:30 se suma la bodega
  (24), el sistema queda en 37 y hay 34 (C4).
- **Una captura no dice el lugar** (`CapturaConteo`: producto, cantidad, quién y hora): «Ya van 24» se ve igual si Ana
  contó la bodega o este mismo anaquel (C11).
- **Quién cuenta lo dice quien llama.** La API revisa la llave del equipo y el permiso del `UsuarioId` que viene en lo
  que se manda; la sesión de `SesionesCelular` es solo de la página `/conteo` (el POS supone que los celulares no llaman
  a la API). Con la llave de un celular se podría cerrar un departamento o ver la revisión (`revision=true`) con el id
  del dueño (C10).
- **`/conteo`, hoy:** usuario y PIN escritos; se busca por nombre o se lee con un lector (no hay cámara para códigos);
  una casilla por presentación; se pone al día cada 10 segundos; «Solo lo que falta» y «Lo que capturaste» con
  «Quitar». No tiene el conteo del día. Lo tecleado se pierde si se escanea otro producto antes de guardar.
- **Las capturas de otros no se quitan en ninguna pantalla:** el POS se lo permite al encargado, pero `/conteo` solo
  enseña las propias.

### Quién cuenta con el celular

No todos tendrán la app (dueño, 2026-11-03). Conteo aparece si la persona tiene «Contar inventario» y el POS deja
contar.

| Celular | Quién | Modos | En Conteo |
|---|---|---|---|
| **De una persona** (en la primera versión, solo el de Luis, el dueño) | Esa persona, y nadie más: entra con su huella y el celular sabe de quién es ([entrar](#entrar)) | Precio, Ticket y Conteo | Lo que dejen sus permisos. El dueño: el del día, contar, abrir departamentos y quitar capturas de otros |
| **De la tienda** (a futuro) | Varios, por turnos. Precio no pide entrar; para contar, cada quien entra con su ficha y su PIN | Solo Precio y Conteo, aunque la persona pueda más | Contar el del día y los departamentos abiertos. Abrir uno, solo quien tenga el permiso |
| **Los demás** | Su propio celular, sin la app | — | `/conteo` en el navegador |

- **Qué modos tiene el celular de la tienda lo decide el dueño** al darlo de alta (Configuración › Celulares), no quien
  lo usa. El POS lo hace valer: con esa llave no se mandan tickets ni se revisa, cierra o cancela un conteo (C8).
- **Por turnos, en el de la tienda:**
  - **Entrar pide red:** el PIN lo revisa la caja principal. En la bodega, con el wifi débil, no se cambia de persona:
    el celular se pasa en el mostrador. Sin red, la app lo dice antes de pedir el PIN.
  - **«Salir»** (arriba, junto a su ficha) deja todo a nombre de quien contaba: lo que tenía contando se guarda y lo
    del día sin aplicar se queda para esa persona. No pregunta nada: quien toca «Salir» puede ser quien llega, y no
    decide por otro lo que no se deshace.
  - **«Terminé mi parte y salir»** sí pregunta por lo sin aplicar (aplicar, descartar o seguir contando): lo toca quien
    contó.
  - **La sesión se cierra sola** a los 10 minutos sin usarse (avisa un minuto antes), y solo con red: sin red nadie
    podría volver a entrar. Lo de esa persona queda a su nombre y, al volver, sigue ahí.
  - **Lo sin aplicar de alguien que salió** no lo aplica nadie más. Si no vuelve ese día, se descarta y se le avisa al
    entrar otra vez.
  - **Cada captura sube con la sesión de quien la contó** (C10), aunque suba después de que salió.
  - La hoja «Lo contado» enseña solo lo de quien está.

### La pantalla

```
┌──────────────────────────────────────────┐
│ (L)                                [L]   │  quién entró · la linterna
│     [ ||| Apunta al código de barras ]   │
│ ┌──────────────────────────────────────┐ │  ← 32 %
│ │  ──────────────────────────────────  │ │  la franja: nada la tapa
│ └──────────────────────────────────────┘ │  ← 68 %
│ ╭───────────────── ══ ─────────────────╮ │
│ │ Escolar · [Anaquel]      37 de 120 › │ │  qué se cuenta, dónde y cómo va
│ │ ▬▬▬▬▬▬▬▬▬▬────────────────────────── │ │
│ │ Lápiz HB No. 2     +12 ┌───┐┌──────┐ │ │  lo que sumó la lectura, unos segundos
│ │ 1 caja + 1 suelta      │−12││ × 13 │ │ │  − quita la última lectura · el contador: se toca para teclear
│ │ Ya van 24: Ana, bodega └───┘└──────┘ │ │  el producto se toca: sus capturas
│ │ Antes: Goma blanca 1        [Quitar] │ │  lo último guardado: se queda hasta el siguiente
│ │ (Precio) (Ticket) (Conteo)    [···]  │ │  ↑ guardar · ↖ lo contado · ← buscar
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

- **Arriba, qué se cuenta, dónde y cómo va:** «Escolar · Anaquel» o «Del día · Anaquel», y cuántos van («37 de 120»). El
  lugar se toca para cambiarlo; lo demás, o el asidero, abre «Lo contado». Cada conteo lleva su ícono y su frase: sus
  reglas no son las mismas.
- **El contador grande** (48 sp en el Pixel, 38 en el chico, en violeta): lo de este lugar en el completo y el total de
  la tienda en el del día, en piezas, metros o kilos. Se toca para teclear. Junto al nombre, unos segundos, lo que sumó
  la última lectura: «+1» o «+12 caja».
- **El −** quita la última lectura; si fue la caja, la caja («−12»). Así se deshace una lectura de más sin pensar cuál
  fue.
- **El producto se toca entero** (no un enlace chico): sus capturas, con quién, dónde y a qué hora. Debajo del nombre,
  cuántas cajas y sueltas son y, en el completo, lo que ya contaron otros («Ya van 24: Ana, bodega»). Nunca lo que dice
  el sistema.
- **Cuenta lo de aquí aunque ya contaron:** «Ya van» dice que alguien pasó, no que este lugar ya se contó. La primera
  vez que sale «Ya van», el renglón de lo último lo dice: «Cuenta lo de aquí aunque ya contaron».
- **El renglón de lo último,** fijo, encima de los modos. En el completo, «Antes: Goma blanca 1 · Quitar»; en el del
  día, lo sin aplicar ([abajo](#el-conteo-del-día)). Es de una línea, con el nombre cortado (con la letra grande, de
  dos: la cifra abajo), y se queda hasta el siguiente guardado: no se va por tiempo ni lo tapa otro aviso. «Quitar»
  quita lo que decía al tocarlo, aunque en ese momento llegue otra lectura, y ahí mismo queda «Quitada: Goma blanca 1 ·
  Deshacer». Antes del primer guardado dice la regla: «Escanear otro producto guarda este».
- **Sin avisos flotantes en Conteo:** lo leído, lo rechazado y lo guardado tienen su lugar en el panel; lo de la red va
  arriba, en su chip. Nada sube a la franja.
- **Cabe en el chico:** sin fila de botones, bajo la franja quedan unos 180 dp para el encabezado, el producto, el
  contador y el renglón de lo último (unos 260 en el Pixel). Con la letra grande se esconde lo de menos
  ([una mano](#una-mano-letra-grande-y-talkback)).
- **Muchas iguales: escanear una y teclear.** Pasar 50 lápices uno por uno tarda; escanear uno y teclear «4 cajas y 2»
  es más rápido y más seguro.

### El botón rápido en Conteo

Las mismas direcciones que en los otros modos ([el botón rápido](#el-botón-rápido)):

| | Conteo del día | Conteo completo |
|---|---|---|
| **↑** | **Es todo · 37:** pide confirmar y lo aplica | **Guardar 13:** guarda este lugar |
| **↖** | **Lo contado:** lo mío y lo que falta | Igual, y lo que está fuera de lugar |
| **←** | **Buscar** lo que no tiene código | Igual |

- **↑ en el del día pide confirmar,** como ↑ en Ticket abre «¿A qué caja?»: aplicar no se deshace, y un gesto sin mirar
  no debe hacer lo que no se corrige. En el completo guarda directo: una captura se quita.
- **La etiqueta de ↑ se pone al día** con cada lectura, también con el menú abierto («Guardar 13» pasa a «Guardar 14»), y
  la hoja «Aplicar» tampoco acepta el toque de rebote.
- **«Guardar» casi no hace falta:** escanear otro producto guarda el anterior, y también se guarda solo al cambiar de
  lugar, al pausarse el escáner (20 s sin códigos), al salir de Conteo y al apagarse la pantalla. Es para dejar guardado
  ya lo que se cuenta, sin esperar la pausa de 20 s.
- **«Contar otra cosa»** (del día a un departamento, o de un departamento a otro) va en «Lo contado»: es de una vez por
  rato.
- **El gesto no se confunde con «atrás»:** el botón y el arranque de su gesto se excluyen del gesto de atrás de Android
  (`Modifier.systemGestureExclusion()`, que deja hasta 200 dp por borde); para zurdos, el borde izquierdo.

```
┌──────────────────────────────────────────┐
│ ╭──────────────────────────────────────╮ │
│ │ 37 piezas en total                   │ │  la cifra primero: no se corta
│ │ Lápiz HB No. 2 · 13 aquí + 24 en bo… │ │
│ │ Se aplica ya. Si quedó mal, es un    │ │
│ │ ajuste en la caja.                   │ │
│ │ [✓ Aplicar 37 piezas               ] │ │  a la altura del pulgar
│ │ [  Falta un lugar                  ] │ │  se queda sin aplicar
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

### El lugar y el orden

```
┌──────────────────────────────────────────┐
│ ╭──────────────────────────────────────╮ │
│ │ Escolar: ¿dónde empiezas?            │ │
│ │ Primero lo que está a la venta, de   │ │
│ │ corrido; la bodega al final.         │ │
│ │ [  Anaquel                         ] │ │  una vez por vuelta; se cambia en el encabezado
│ │ [  Mostrador                       ] │ │
│ │ [  Bodega                          ] │ │
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

- **El lugar se elige una vez por vuelta,** al empezar a contar: anaquel, mostrador o bodega. Se queda hasta cambiarlo
  en el encabezado.
- **Viaja con cada captura** (C11): sale en «Ya van» («Ya van 24: Ana, bodega»; la hora, en las capturas del producto),
  en «Lo contado» y en la revisión de la caja. Mientras el POS no lo guarde, se queda en el celular: lo mío dice el
  lugar y lo de otros va sin él.
- **Primero lo que está a la venta, de corrido; la bodega al final.** El POS compara cada producto contra su primera
  captura vigente. Si la bodega va primero, lo que se vende antes de contar el anaquel se descuenta dos veces: el
  sistema dice 50, la bodega da 30, se venden 3, el anaquel da 17 y al cerrar queda 47 + (47 − 50) = 44, cuando hay 47.
  Al revés cuadra: el anaquel da 20, se venden 3, la bodega da 30 y queda 47 + (50 − 50) = 47.
- **Mientras se cuenta un departamento, no se surte ni se acomoda lo que llega** en sus lugares: lo que pasa de la
  bodega a un anaquel ya contado se pierde, y lo que llega a un lugar que falta contar se suma dos veces. Lo que llega
  se deja aparte, o en un lugar ya contado, hasta cerrar.
- **Un departamento se abre, se cuenta y se cierra** de preferencia el mismo día: entre más dura abierto, más fácil se
  cruza un surtido o una compra.
- **Si dos se reparten uno grande,** uno el piso y otro la bodega, la bodega empieza cuando el piso ya terminó.
- **Volver a un producto en el mismo lugar sigue la cuenta:** «14 · 13 guardadas + 1». Lo nuevo sube como otra captura
  y se suma; nadie vuelve a contar desde cero. Al teclear, el número es lo de este lugar y reemplaza lo guardado (se
  quita la captura y sube la nueva, como corrige el POS).
- **Corregir un lugar ya contado corre la comparación:** si se quita la captura más vieja de un producto que ya tiene
  capturas de otro lugar (con «Quitar», o al teclear, que la quita y sube otra), el POS compara contra la siguiente, y
  lo vendido desde esa se descuenta dos veces. El sistema dice 50: el anaquel da 20 a las 10:00 y la bodega 30 a las
  10:30; se venden 2 y a las 10:40 se teclea 18 en el anaquel: al cerrar queda 48 + (48 − 50) = 46, cuando hay 48. En
  ese caso la app lo dice al teclear o al tocar «Quitar» y pide volver a contar los lugares que se contaron después (la
  bodega).
- **Una lectura de más en medio:** si el mismo producto vuelve en menos de 30 s y en medio hubo una sola lectura de
  otro, el renglón de lo último pregunta «¿La goma (1) fue de más? · Quitar».
- **El del día también va por lugares:** «13 aquí + 24 en bodega = 37». El orden es el mismo; con C4, el POS compara
  contra lo que había al contar el primer lugar.

### El conteo del día

```
┌──────────────────────────────────────────┐
│ ╭───────────────── ══ ─────────────────╮ │
│ │ Del día · [Anaquel]        3 de 15 › │ │
│ │ Lápiz HB No. 2     +12 ┌───┐┌──────┐ │ │
│ │ 13 aquí + 24 en bodega │−12││ × 37 │ │ │  el total: lo de aquí más lo de otros lugares
│ │ 1 caja + 1 suelta aquí └───┘└──────┘ │ │
│ │ Goma: 4 · hace 3 min     [¿Es todo?] │ │  lo sin aplicar y cuánto lleva; a los 10 min, en ámbar
│ │ (Precio) (Ticket) (Conteo)    [···]  │ │  ↑ es todo · ↖ lo contado · ← buscar
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

1. **Sin elegir:** si no hay departamentos abiertos (lo de todos los días, después del arranque), Conteo es el conteo
   del día. La primera vez del día pregunta el lugar.
2. **Escanear uno de los 15:** empieza su contador. «Lo contado» › «Falta» dice cuáles faltan y de qué departamento son.
3. **Todo lo que hay:** se sigue en el mostrador y en la bodega, cambiando el lugar. El contador lleva el total («13
   aquí + 24 en bodega»).
4. **Escanear otro de los 15 no aplica el anterior:** el renglón de lo último lo dice al momento, con cuánto lleva
   («Goma: 4 · hace 3 min · ¿Es todo?»). Al volver a escanearlo, sigue donde iba. Antes del primero, el renglón dice la
   regla: «Se aplica con “Es todo”; otro producto no lo aplica».
5. **A los 10 minutos** el renglón se pone en ámbar y vibra una vez. También lo recuerdan el número en la pestaña Conteo
   y un aviso al salir de Conteo, al tocar atrás o «Terminé mi parte».
6. **↑ «Es todo · 37»,** o «¿Es todo?» del renglón, pide confirmar: «Aplicar 37 piezas» o «Falta un lugar» (sigue sin
   aplicar). Quien ve los costos (de inicio, el dueño y el encargado) ve después, en el renglón de lo último, lo que
   tenía el sistema y la diferencia, como en la caja: «Aplicado: Lápiz HB No. 2 37 · sistema 40 · −3».
7. **Lo que no se encuentra:** en «Lo contado» › «Falta» se toca, se teclea 0 y se aplica: «no hay».
8. **Lo que no va:** uno que no está en los 15 de hoy; uno que ya contó alguien («Ya lo contó Carmen a las 11:02»); uno
   de un departamento abierto («Escolar se está contando completo; se cuenta ahí»), que en «Falta» sale en gris.
9. **Lo sin aplicar nunca se aplica solo.** Lo que quedó de otro día se descarta con aviso: el conteo cambia cada día.

- **Por qué no se aplica solo:** el del día es el total de la tienda, de una vez. Si se aplicara al escanear el
  siguiente, lo de la bodega ya no se podría sumar.
- **Por qué el plazo:** el POS compara contra lo que hay al aplicar, así que lo vendido entre el primer lugar y «Es
  todo» queda de más. Con C4, el celular manda la hora del primer lugar y el POS compara contra lo que había entonces;
  mientras, el aviso de los 10 minutos acorta el rato.
- **Hoy se escribe en la caja:** se cuenta en el anaquel y se teclea en F4. Con el celular se cuenta y se aplica ahí
  mismo; la caja lo ve al volver a abrir la venta (hoy su chip «Conteo del día 3/15» solo se pone al día al abrirla o al
  contar en esa caja).

### El conteo completo

```
┌──────────────────────────────────────────┐
│ ══  ¿Qué vas a contar?                   │  una hoja: aquí no se escanea
│ [R] Conteo del día             3 de 15 › │  el total de cada uno, una vez
│ [C] Escolar · ahora Luis, Ana   37/120 › │  quién cuenta ahora, en la app o en /conteo
│ [C] Oficina · ahora Carmen      12/210 › │
│ [C] Papel · nadie                 0/95 › │
│ [C] Arte · nadie                  0/60 › │
│ [C] Regalos · nadie               0/40 › │
│ [+] Abrir otro departamento            › │  solo con «Conteo completo de un departamento»
│ Revisar y cerrar: en la caja (F4)        │
└──────────────────────────────────────────┘
```

`[R]` el del día · `[C]` un departamento.

1. **Elegir el departamento:** «¿Qué vas a contar?» se abre como hoja, con un renglón por conteo, y caben los del
   arranque; ahí no se escanea, así que puede tapar la franja. Cada uno dice cuántos van y quién está contando ahora
   (C5; sin él, quién ya capturó). Quien tiene «Conteo completo de un departamento» abre otro. Luego, el lugar.
2. **Contar:** la pieza suma una, la caja suma la caja. Lo que no tiene código: ← Buscar, o la hoja de códigos del
   mostrador (se escanea una vez y se teclea cuántas).
3. **Se guarda solo:** al escanear otro producto, lo del anterior queda como una captura de este lugar y el renglón de
   lo último dice «Antes: Lápiz HB 13 · Quitar». También al cambiar de lugar, al pausarse el escáner, al salir de Conteo
   y al apagarse la pantalla; si se pausó, el producto se queda a la vista y su contador todavía se toca. Lo que bajó a
   0 con el − no se guarda; un 0 tecleado sí: «no hay».
4. **El mismo producto en otro lugar** es otra captura: se cambia el lugar y se escanea.
5. **Lo que ya contaron otros:** «Ya van 24: Ana, bodega». Se toca el producto: sus capturas, con quién, dónde y a qué
   hora. Las propias se quitan; las de otros, quien tiene «Conteo completo de un departamento».
6. **Lo de otro departamento** se queda en el lugar del producto hasta decidir, con la vibración de aviso:
   - Si Oficina se está contando: «Lo cuento para Oficina» (lo cuenta ahí, de paso, hasta escanear otro, y el producto
     lleva «para Oficina» y «Déjalo aquí»: se deja donde está hasta que cierren Oficina, porque si se lleva a su lugar
     se contaría dos veces) o «Lo llevo a su lugar».
   - Si Oficina no se ha contado: «Llévalo a su lugar».
   - Si Oficina ya se cerró (el POS dice cuándo): «Oficina se contó el 28/10: llévalo y avísale al encargado; es un
     ajuste».
   - Si se escanea otro sin decidir, no se pierde: pasa a «Lo contado» › «Fuera», con su número en ↖.
7. **Lo que no está en el catálogo** o no lleva inventario: en rojo, en el lugar del producto, con la vibración de
   error. Se aparta para el encargado.
8. **↖ Lo contado:** «Lo mío», con el lugar, la hora, el estado y «Quitar» (con «Deshacer» unos segundos, como el bote
   del ticket); «Falta», lo que nadie ha contado, por nombre; «Fuera», lo de otros departamentos (fuera de lugar). Tocar
   uno de «Falta» empieza a contarlo: para lo que no tiene código o para decir «no hay».
9. **«Terminé mi parte»** guarda lo que se contaba, dice cuánto contaste y si queda algo por subir, y regresa a «¿Qué
   vas a contar?». Se le dice al encargado.

```
┌──────────────────────────────────────────┐
│ <  Escolar · Lo contado                  │
│    12 capturas · 2 por subir             │
│ Lápiz HB No. 2                      13   │
│ Anaquel · 14:52 · por subir        [x]   │
│ ──────────────────────────────────────── │
│ Cuaderno profesional cuadro…         3   │
│ Bodega · 14:50 · subida            [x]   │
│ ──────────────────────────────────────── │
│ Goma blanca chica               no hay   │  «no hay», en su color
│ Anaquel · 14:47 · subida           [x]   │
│                                          │
│ (Lo mío 12)  (Falta 83)  (Fuera 1)       │  abajo, junto al pulgar; de 48 dp
│ [Contar otra cosa] [Terminé mi parte]    │
└──────────────────────────────────────────┘
```

`[x]` quitar (el bote).

### Cajas y sueltas: el teclado

```
┌──────────────────────────────────────────┐
│                    ══                    │  se cierra jalando esta barra, o con atrás
│ Lápiz HB No. 2 · lo de Anaquel           │
│ ┌──────────────────┐ ┌─────────────────┐ │
│ │▌Cajas de 12    3 │ │ Sueltas       1 │ │  una casilla por presentación, en dos columnas
│ └──────────────────┘ └─────────────────┘ │
│ Aquí                          37 piezas  │
│    [  1  ]     [  2  ]     [  3  ]       │
│    [  4  ]     [  5  ]     [  6  ]       │
│    [  7  ]     [  8  ]     [  9  ]       │
│    [  .  ]     [  0  ]     [ <-  ]       │  el punto, solo en metros, kilos o litros
│ [✓ Guardar 37                         ]  │  desde «Falta», apagado hasta teclear
└──────────────────────────────────────────┘
```

- **Se abre tocando el contador.** Una casilla por presentación, de la más grande a la suelta, como en `/conteo`, en dos
  columnas. Arrancan con lo de este lugar.
- **El total, al momento.** El teclado abajo, junto al pulgar; las casillas encima. Teclas de 52 dp: con tres
  presentaciones (caja, paquete y suelta) y la letra grande cabe en 360 × 740 sin desplazarse.
- **Por metro, kilo o litro,** la suelta acepta el punto («4.5»). Un rollo con su código suma su largo.
- **Desde «Falta», «Guardar» está apagado hasta teclear una cifra;** teclear el 0 enciende «Guardar 0 · no hay». Así un
  toque de más al desplazar la lista no graba un 0, que en la revisión sacaría el producto de «Sin contar».
- **En el del día,** «Otro lugar» guarda lo de aquí y deja las casillas en cero para el siguiente; el total suma todos.
- **Mientras está abierto, las lecturas no cuentan.**
- **Se cierra con atrás o jalando su barra,** no deslizando cualquier parte: teclear rápido del 2 al 0 es un arrastre
  hacia abajo. Cerrarlo tira lo tecleado; las lecturas se quedan.

### A ciegas

- **Ninguna pantalla de Conteo enseña lo que dice el sistema:** ni el panel, ni «Lo contado», ni ← Buscar, cuyos
  resultados van sin existencia y sin «Quedan».
- **Tampoco a un toque, mientras se cuenta de verdad:** en Precio, lo que se está contando sale «Existencia oculta · Ya
  terminé» (TalkBack: «Existencia oculta mientras cuentas Escolar»); en el ticket no sale «Quedan» y al buscar sale
  «existencia oculta mientras cuentas Escolar». Del día, los que faltan de aplicar; del completo, los del departamento
  elegido.
- **Solo mientras se cuenta:** en Conteo y hasta 10 minutos después de la última lectura; lo del día sin aplicar, hasta
  aplicarlo o descartarlo. Luego la existencia vuelve a salir: el dueño la necesita para «¿te quedan 20?». «Ya terminé»
  hace lo de «Terminé mi parte».
- **En el celular de la tienda, Precio nunca enseña la existencia:** es lo que ve el cliente.
- **Lo que contaron otros sí se ve:** lo hace `/conteo` y dice que alguien ya pasó. No dice si fue este lugar: por eso
  va el lugar con cada captura y el renglón de lo último dice, la primera vez, «Cuenta lo de aquí aunque ya contaron».

### Sin red: la bandeja de salida

La red se cae y en la bodega el wifi llega mal. Se sigue contando:

| Estado | Quiere decir |
|---|---|
| Contando | En el celular, sin guardar. Se guarda solo al escanear otro, al cambiar de lugar, al pausarse el escáner, al salir de Conteo o al apagarse la pantalla; no se pierde si se cierra la app |
| Sin aplicar | Del día: contado, falta «Es todo». Dice cuánto lleva |
| Por subir | Guardado en el celular; sube solo al volver la red (lo del día con «Es todo» se aplica al subir) |
| Subida, aplicada | El POS ya la tiene |
| No entró | El POS no la aceptó (el departamento se cerró o se canceló, ya lo contó otro, cambió de departamento, se está contando completo): dice por qué |

- **Cada captura lleva su número:** mandarla dos veces cuenta una. Quitar una captura también sube, en orden, después de
  la captura que quita; quitar una que todavía no sube la borra del celular: nunca llega.
- **Cada captura lleva cuándo se contó** (C4): cuánto hace, con el reloj del celular que no cambia aunque muevan la hora
  (`SystemClock.elapsedRealtime`). Ese reloj vuelve a cero al reiniciar, así que también se guardan la hora del celular
  y su número de arranque (`Settings.Global.BOOT_COUNT`): si el celular se reinició, sube la hora del celular marcada
  como aproximada.
- **Arriba,** «Sin red · 3 por subir»; al volver, «Subiendo 3» y, si algo no entró, «1 no entró» hasta abrir «Lo
  contado».
- **«Ya van»** sale de lo último que se supo, con «hace 12 min». Sin red no se abre un departamento.
- **Lo que no entró** se queda en «Lo contado», para decírselo al encargado.

### El encargado y la caja

| En el celular | En la caja (F4 › Conteo completo) |
|---|---|
| Abrir un departamento | También; ahí está el QR de `/conteo` |
| Contar, como todos | — |
| Ver cuántos van y quién está contando ahora | También, con lo que cada celular tiene por subir (C5) |
| Quitar una captura equivocada, también de otros | — |
| — | **Revisar:** lo contado contra el sistema, por lugar (C11), lo que nadie contó y lo que vale la diferencia |
| — | **Cerrar** el departamento, o cancelarlo |

Revisar y cerrar se quedan en la caja:

- **Cerrar no se deshace:** corrige todo el departamento de una vez y deja en 0 lo que nadie contó. Se decide con la
  tabla entera: cientos de renglones, «Con diferencia» y «Sin contar».
- **En el celular de quien cuenta rompería lo de a ciegas:** la revisión enseña lo que dice el sistema.
- **«Sin contar»** dice lo que nadie contó y el sistema dice que hay: antes de cerrar, el encargado lo manda a buscar.
- **Antes de cerrar, quién falta de subir:** «Ana · 3 por subir · hace 8 min» (C5), también lo que tiene contando.
- **En una urgencia,** `/conteo` deja revisar y cerrar desde el navegador a quien tiene el permiso.

### El conteo físico de arranque

El conteo completo de todos los departamentos da las existencias para dejar eleventa: «las de eleventa solo sirven de
referencia para el conteo; las que valen son las contadas» (plan del POS). Es el uso más grande: miles de productos y
varios contando a la vez.

**Cuándo.** El plan del POS lo pone «en las semanas previas» al arranque, pero en el piloto el POS no deja contar. Hay
que decidirlo:

| | Cómo | A favor | En contra |
|---|---|---|---|
| **A. Uno o dos días, ya sin eleventa** | Se deja eleventa, se abren todos los departamentos, cuentan todos a la vez y se cierra cada uno al terminar | Lo más exacto: no se vende mientras se cuenta. No pide nada al POS | La tienda para uno o dos días; hace falta mucha gente a la vez |
| **B. En el piloto, y se cierra al dejar eleventa** | Un departamento a la vez en las semanas previas. El POS guarda lo contado contra lo que dice la lectura de eleventa y lo aplica al cerrar, ya sin eleventa | Es lo que dice el plan. La tienda sigue abierta | Pide al POS abrir y capturar en el piloto. Lo que vende la caja 1 llega con la lectura cada pocos minutos: lo vendido justo antes de contar se descuenta dos veces. Los departamentos quedan abiertos semanas: cualquier surtido o compra en medio descuadra. Cada departamento se depura antes de contarlo |
| **C. Arrancar con lo de eleventa y contar después** (lo recomendado) | Se arranca con las existencias de la última lectura. En las semanas siguientes se cuenta un departamento a la vez, con el POS ya sin eleventa: los que más valen, la primera semana | Cuenta contra sus propios movimientos. La tienda sigue abierta. Cada departamento se abre y se cierra el mismo día. Pide poco al POS (C7 y C9) | Va contra el plan del POS, que quiere arrancar con lo contado. Unas semanas con existencias que se saben malas, como hoy: lo que vale el inventario, los bajos y el sugerido de compra salen de ahí. Cada cierre convierte errores viejos de eleventa en «faltó $X». Sin C9, un departamento abierto y el del día se pisan a diario |

**Si se queda C:**

- **Los departamentos que más valen, la primera semana** (Inventario › lo que vale el inventario, por departamento).
- **El del día, solo en lo ya contado** (C7): en lo demás solo convertiría errores de eleventa en alertas («Diferencia
  en el conteo del día», desde $200, le llegaría seguido al dueño), y con un departamento abierto se pisaría (C9).
- **El primer cierre de cada departamento dice «arranque»** en la bitácora, el resumen del día y el aviso al dueño
  (C7): su diferencia es el error que traía eleventa, no una pérdida de esa semana.
- **Hasta contar un departamento,** lo que vale, sus bajos y su sugerido de compra se leen con cuidado: son de
  eleventa. Inventario dice cuáles faltan.

**Cómo se reparte,** con cualquiera de las tres:

1. **El orden:** primero los departamentos que más valen.
2. **El encargado abre los departamentos de hoy,** desde la caja o desde la app: uno por persona.
3. **Cada quien toma el suyo** en «¿Qué vas a contar?»; ahí se ve quién está contando ahora en cada uno.
4. **Cada quien recorre todos los lugares de su departamento,** en orden: primero lo que está a la venta (anaquel,
   mostrador, exhibidor) y la bodega al final. Lo de otro departamento abierto lo cuenta para ese; si no, lo lleva a su
   lugar.
5. **Mientras, nadie surte ni acomoda lo que llega** en ese departamento.
6. **Al terminar,** «Lo contado» › «Falta» (lo que nadie ha contado; lo que no aparezca, se deja) y «Fuera».
7. **«Terminé mi parte»,** y se le dice al encargado.
8. **El encargado revisa en la caja:** quién tiene algo por subir, y lo que el sistema dice que hay y nadie contó, que
   manda a buscar. Luego cierra, de preferencia el mismo día.

- **Un departamento por persona:** nadie cuenta dos veces el mismo anaquel. Si uno es muy grande, dos se lo reparten
  por lugares (uno el piso, otro la bodega) y la bodega empieza cuando el piso ya terminó. «Ya van», con el lugar, dice
  quién contó dónde.
- **El avance:** «¿Qué vas a contar?» dice de cada departamento cuántos van de cuántos y quién está contando. La caja,
  igual.
- **La app y `/conteo` a la vez:** son las mismas capturas; lo de uno se ve en el otro («ya van»). En `/conteo` no hay
  cámara para los códigos: se busca por nombre o se usa un lector.
- **Aquí sirve primero el celular de la tienda:** uno o dos con la app (Precio y Conteo) rinden más que `/conteo` en los
  celulares de cada quien.

### En el piloto

- El POS no deja contar y no hay conteo del día. **En la app, Conteo no aparece,** como la caja, que no ofrece la tarea.
- **«Contar» sale del menú del ícono.** Es un acceso dinámico (desde la 0.3.0): la app lo quita mientras no se pueda
  contar, y el que alguien ancló se queda apagado con su mensaje («En el piloto no se cuenta») hasta que se pueda. El
  «Conteo» que se ancló con la 0.1 o la 0.2 ya está apagado para siempre ([del bolsillo al
  precio](#del-bolsillo-al-precio)). «Revisar precio» sigue, fijo.
- Con la opción B del arranque, aparece solo el conteo completo, con «Se aplica al dejar eleventa».
- En el prototipo sí está, con el aviso y «Ver cómo se contará», para revisarlo.

### Agregar inventario, recibir mercancía, ajustes y mermas

| Tarea | En el celular | Por qué |
|---|---|---|
| **Agregar inventario** (lo que llegó de un producto, sin proveedor; unas 7 al día) | No, por ahora | Se hace donde llega la mercancía, junto a la caja, de uno en uno. Mueve la existencia y el costo al momento y no tiene deshacer (se corrige con un ajuste). En el celular que cuenta se confundiría con contar, y lo agregado dos veces no se nota |
| **Recibir mercancía a ciegas** (una compra de un proveedor) | Después, como un conteo más | Es contar lo que llegó: escanear, a ciegas, cajas y sueltas. Hoy no se usa: los proveedores están vacíos y lo que llega entra con «Agregar inventario» |
| **Ajustes** | No | Cambian la existencia viéndola, con motivo; al cajero le piden autorización y desde $200 avisan al dueño. Lo que se cree mal se cuenta (si es del día) o se ajusta en la caja. Si su departamento se está contando, no se ajusta: se cuenta ahí y el cierre lo corrige; un ajuste en medio se corregiría otra vez (C9) |
| **Mermas** | No | Lo dañado se lleva a la caja; ahí se registra con su causa y, arriba de $200 para el cajero, con autorización |
| **Abrir un paquete** | No | Se hace al abrirlo, en la caja |

- El celular de la tienda es solo para contar y ver precios (dueño, 2026-11-03): ahí nada de esto.
- Lo que llega mientras se cuenta un departamento entra como siempre, en la caja, pero se deja aparte o en un lugar ya
  contado hasta cerrar ([el lugar y el orden](#el-lugar-y-el-orden)).
- Las fotos de los productos, que hoy se toman en `/conteo` › Fotos, no son de contar: quedan para otra entrega.

### Una mano, letra grande y TalkBack

- **Lo que se toca, de 48 dp como mínimo;** lo principal, de 56. El producto se toca entero (en `/conteo`, «Ya van» es
  un texto de 13 px que no se toca); las pestañas de «Lo contado» y el «Quitar» del renglón de lo último, de 48.
- **Con la letra grande** (hasta 200 %, la tabla no lineal de Android 14), el panel esconde en este orden: primero la
  barra de avance y la pista («Cada pieza suma una»); luego el encabezado pasa a corto («Escolar 37/120»); al último, el
  desglose y «Ya van», que salen al tocar el producto. Nunca se esconden el nombre (en una línea), «para Oficina», el −,
  el contador ni el renglón de lo último, que pasa a dos líneas: lo de antes y el nombre arriba, la cifra abajo. El
  panel no se desplaza mientras se escanea y nada sube a la franja.
- **El teclado,** con teclas de 52 dp y las casillas en dos columnas ([arriba](#cajas-y-sueltas-el-teclado)).
- **Zurdos:** con «Botón rápido a la izquierda», el renglón del producto va en espejo: el contador y el − a la
  izquierda, junto al pulgar, y el botón del renglón de lo último también.
- **Los gestos del sistema:** el botón rápido no arranca el gesto de atrás ([arriba](#el-botón-rápido-en-conteo)) y el
  teclado solo se cierra jalando su barra.
- **Atrás en Conteo:** si hay algo contando, lo guarda y sale; si hay algo del día sin aplicar, primero lo recuerda
  («Goma: 4 sin aplicar · ¿Es todo?») y otro atrás sale.
- **TalkBack:** cada lectura se anuncia («Lápiz HB, 14, más 12 caja»), cada rechazo dice por qué («Folder carta: es de
  Oficina») y cada guardado, qué quedó («Guardado: lápiz HB, 13»). El botón rápido abre sus tres como botones normales.

### Lo que pide al POS para contar

Todo va sobre el punto 1 de [lo que pide al POS](#lo-que-pide-al-pos) (que el celular hable con la API con su llave) y
detalla sus puntos 8 y 9. El conteo del día (`GET /api/sucursales/{id}/conteo-del-dia`, `POST /api/conteos/{id}/contar`)
y el completo (`/api/conteos-completos/…`) ya están en la API; con la llave, la app los usa como están. C9 y C10 cierran
huecos que el POS ya tiene hoy, con o sin la app; C10 vale para toda la app y conviene sumarlo al punto 1.

| # | Qué | Por qué | Para |
|---|---|---|---|
| C1 | **Capturar ligero:** que la captura conteste solo esa captura y el producto con sus capturas | Hoy cada captura regresa el departamento entero (sus productos, presentaciones, códigos y todas las capturas). Con cientos de productos y la bandeja subiendo de golpe, pesa | El completo |
| C2 | **Ponerse al día:** las capturas nuevas y las quitadas de un conteo desde la última vez, y si sigue abierto | «Ya van» sin bajar el departamento cada 10 segundos, como `/conteo`; y saber pronto si se cerró | El completo |
| C3 | **El número propio en el del día** (`SolicitudContar` con su `Id`, como `SolicitudCaptura`), y quitar dos veces la misma captura sin error | Si se pierde la respuesta, el celular lo vuelve a mandar. Hoy el segundo dice «Ya se contó hoy» y no se sabe si fue el mismo | Sin red |
| C4 | **Cuándo se contó:** cada captura lleva cuánto hace que se contó (con el reloj que no cambia aunque muevan la hora; tras un reinicio, la hora del celular marcada como aproximada). En el del día, la hora del primer lugar. El POS compara contra lo que había en ese momento | Hoy se compara contra lo que hay al llegar o al aplicar: lo vendido en ese rato (sin red, o entre el anaquel y la bodega) corre la diferencia | Sin red y el del día |
| C5 | **Quién está contando y qué le falta subir:** el latido del celular dice qué conteo tiene elegido, cuántas capturas tiene por subir y si tiene algo contando. «¿Qué vas a contar?» dice «ahora: Ana» y la revisión lo enseña antes de «Cerrar» («Ana · 3 por subir · hace 8 min») | Si se cierra con capturas en un celular sin red (en la bodega), no entran. Y hoy «quién cuenta» es quien ya capturó (`ConteoResumenDto.Personas`), no quien está contando | El completo |
| C6 | **Que `/conteo` no pierda lo tecleado** al escanear otro producto: que lo guarde o que avise | La usarán quienes no tengan la app, sobre todo en el arranque | El arranque |
| C7 | **El arranque:** decidir cuándo (A, B o C, arriba). Con B: abrir y capturar en el piloto, y cerrar solo ya sin eleventa. Con C: el del día solo de los departamentos ya contados desde el arranque, y el primer cierre de cada uno marcado «arranque» en la bitácora, el resumen y el aviso | Hoy el piloto no deja contar. Con C, el del día y los cierres convertirían errores de eleventa en alertas | El arranque |
| C8 | **Los modos del celular:** que su alta diga si es de la tienda (solo Precio y Conteo) y que el POS lo haga valer: esa llave no manda tickets ni revisa, cierra o cancela un conteo. Es el 1a y el 1b de [los celulares](#lo-que-pide-al-pos-para-los-celulares) | Lo decide el dueño, no quien lo usa; y hoy la API no distingue un celular de otro | La tienda |
| C9 | **Que no se pisen:** mientras un departamento está abierto, el del día no elige ni acepta sus productos («se está contando completo»), tampoco en la caja, y un ajuste a uno de ellos avisa y no se hace hasta cerrar o cancelar | El cierre suma la diferencia contra la primera captura: una corrección en medio se suma dos veces. Con C pasaría a diario con lo que más vale, que es justo lo que elige el del día | Los dos |
| C10 | **Quién cuenta, de la sesión** (con el 1b y el 1c de [los celulares](#lo-que-pide-al-pos-para-los-celulares)): el PIN abre una sesión que vale el turno, y el usuario sale de ella (o del celular de una persona, que su alta ata a esa persona), no de lo que manda el celular. Cada captura de la bandeja sube con la sesión de quien la contó | Hoy la API cree el `UsuarioId` del cuerpo: con la llave de un celular se cierra un departamento o se ve la revisión con el id del dueño | Toda la app |
| C11 | **El lugar en la captura,** en la API y en `/conteo` (que lo pregunte una vez por vuelta, como la app): anaquel, mostrador o bodega, para «Ya van» y la revisión | Una captura no dice dónde: «Ya van 24» no distingue la bodega de este mismo anaquel | El completo |

### Cómo se entregaría

La entrega 6, en partes y después del piloto:

| Parte | Qué trae | Necesita del POS |
|---|---|---|
| 6.1 | **El conteo completo:** elegir y abrir, el lugar, contar, cajas y sueltas, se guarda solo, lo de otros, «Lo contado», lo fuera de lugar, la bandeja de salida | 1, C1, C2, C4, C5, C9, C10, C11; con B, C7 |
| 6.2 | **El conteo del día:** el total por lugares, «Es todo», lo sin aplicar con su plazo | C3 (C4, C9 y C10 vienen con la 6.1); con C, C7 |
| 6.3 | **El celular de la tienda:** Precio sin entrar, Conteo por turnos | C8 (1a y 1b), 1c y 2a |

- **El completo primero, aunque el del día sea de cada día y pida menos:** el arranque, con cualquiera de las tres
  opciones, es lo primero que se cuenta y lo más grande; el del día ya se hace en la caja (F4) y, con C, solo entra en
  lo ya contado. La 6.1 trae lo que usa la 6.2: el contador, el teclado, el lugar y la bandeja.
- **Sin C1, C2 y C11 funciona,** más lento y sin el lugar en «Ya van». Sin C9 y C10, no: los conteos se pisan y con la
  llave de un celular se cierra a nombre de otro.
- Con la opción C, la 6.1 conviene tenerla el día del arranque; mientras, `/conteo` (con C6).
- Si el celular de la tienda va al conteo de arranque ([preguntas de Conteo](#preguntas-de-conteo)), la 6.3 va con la
  6.1, antes de la 6.2.

### Riesgos

- **Un conteo encima de otro:** sin C9, el del día o un ajuste de un producto de un departamento abierto se corrige dos
  veces al cerrar. La app no cuenta en el del día lo de un departamento abierto; la caja sí, hasta C9.
- **Lo sin aplicar que se queda:** aplicado tarde, lo vendido en ese rato queda de más (sin C4); olvidado, el producto
  no se cuenta ese día. El renglón dice cuánto lleva y a los 10 minutos avisa.
- **El orden y el surtido:** contar la bodega primero, surtir o acomodar lo que llega mientras se cuenta, o corregir un
  lugar cuando ya se contó otro después, descuadra el cierre. La app lo dice al elegir el lugar y al corregir; nada lo
  impide.
- **Cerrar con capturas por subir o algo contando:** un celular sin red en la bodega y el encargado cierra; eso no
  entra. El celular avisa «3 por subir» y, con C5, también la caja.
- **Lo contado sin red, sin C4:** si en ese rato se vendió, la diferencia sale corrida. Tras un reinicio, la hora es
  aproximada.
- **Una lectura de más:** el de al lado se lee y, en el completo, se guarda solo como captura de 1, y lo que se contaba
  queda partido. «Antes · Quitar», la pregunta «¿fue de más?» y la cuenta que sigue lo arreglan si se ven.
- **Sostener el producto 1.5 s lo cuenta otra vez** (lo pidió el dueño para el escáner). A ciegas, una pieza de más no
  se nota (pregunta abajo).
- **Dos en el mismo producto del día** (uno en la caja, otro en el celular): vale el primero; al otro se le dice quién y
  a qué hora, y lo suyo no se aplica.
- **La API sin C10:** con la llave de un celular se cierra un departamento o se ve la revisión con el id de otro.
- **El celular de la tienda sin red:** no se cambia de persona; lo contado sigue a nombre de quien entró. Sin «Salir»,
  lo contado queda hasta 10 minutos (más, sin red) a nombre de quien entró: la ficha de arriba lo dice.
- **«Falta» incluye lo que no hay** (existencia 0): quien cuenta no sabe cuáles; el encargado los separa al revisar.
- **Lo fuera de lugar:** contado para Oficina, se deja donde está; si alguien lo mueve antes de que cierren Oficina, se
  cuenta dos veces.
- **La bodega:** poca luz y wifi débil. La linterna está arriba, lejos del pulgar en el Pixel: «Linterna automática» en
  Ajustes. La bandeja aguanta sin red.
- **Horas de cámara:** la batería. El escáner se pausa a los 20 s sin códigos y, un minuto después, apaga la cámara;
  para el arranque, cargadores a la mano.
- **Con la opción B:** lo vendido en la caja 1 justo antes de contar se descuenta dos veces; los departamentos quedan
  abiertos semanas; y depurar (juntar, cambiar la unidad) un producto ya contado deja sus capturas a medias: se depura
  antes de contar.
- **Con la opción C:** unas semanas con las existencias de eleventa; sin C7, el del día y los cierres llenan de alertas
  al dueño.
- **La letra grande:** si no se esconde lo de menos, el panel no cabe y habría que desplazarlo mientras se escanea.

### Preguntas de Conteo

- [ ] ¿El conteo de arranque: A, B o C (lo recomendado)? Si la tienda puede cerrar uno o dos días, A es la más exacta.
- [ ] Con C, ¿el conteo del día solo en los departamentos ya contados (lo propuesto)?
- [ ] ¿Quién más cuenta con la app en la primera versión, además del dueño: el encargado?
- [ ] ¿El celular de la tienda (uno o dos, con la app) para el conteo de arranque, o cada quien con `/conteo` en el
  suyo?
- [ ] ¿Un departamento por persona (lo propuesto), o varios en el mismo, repartidos por lugares?
- [ ] ¿Los lugares son anaquel, mostrador y bodega, o hace falta otro (exhibidor, vitrina)?
- [ ] ¿Se puede dejar de surtir un departamento mientras se cuenta, y cerrarlo el mismo día? Es lo que pide el cierre
  para cuadrar.
- [ ] En Conteo, ¿sostener el mismo producto 1.5 s lo cuenta otra vez, como hoy en el escáner, o se apaga ahí? Lo
  propuesto: apagarlo en Conteo (el escáner ya tiene el ajuste, `msParaRepetirAVista`); a ciegas, una pieza de más no
  se nota.
- [ ] En el celular de la tienda, ¿la sesión se cierra a los 10 minutos sin usarse (lo propuesto, solo con red)? Así lo
  contado queda a nombre de quien contó. Es la misma de [quién tiene la app](#preguntas-de-quién-tiene-la-app).
- [ ] ¿Agregar inventario, ajustes o mermas desde el celular, aunque sea solo el dueño? Lo propuesto: no por ahora.

## Quién tiene la app

> **Decisión del dueño (2026-11-03):** «No todos tendrán la app en su celular: en la primera versión solo personas de
> alta confianza, el dueño por ejemplo; y más a futuro quizá el celular de la tienda, pero solo con conteo y
> verificación de precio.»

> **Decisión del dueño (2026-11-08): el celular se da de alta en el POS como una caja más,** con el alta que ya existe
> para la caja de Windows, y **lleva la copia del catálogo de esa caja.** No habrá «celulares» aparte en el POS, ni
> llaves con solo los modos, ni Configuración › Celulares, ni la sesión del celular de la tienda: lo que sigue de esta
> sección que hable de eso (el QR, «¿Es este?», de quién es y sus modos, 1a–1e) queda como lo que se propuso, no como lo
> que se hará. Lo que manda es
> [el documento del POS](https://github.com/Marioalr/papeleria-pos/blob/celular/como-caja/docs/celular-como-caja.md).
> La app descarta de la copia los usuarios con el hash de su PIN, sus permisos y reglas, y los clientes: ni siquiera los
> lee del JSON. Así quedó en la 0.4.0 ([CAMBIOS](../CAMBIOS.md)). El celular de la tienda (6.3) se replantea cuando toque.

Hay dos clases de celular. La clase se elige al darlo de alta en la caja y no cambia con quien lo agarre.

| | **De una persona** (el de Luis, el dueño) | **De la tienda** (a futuro) |
|---|---|---|
| Quién lo usa | Esa persona, de alta confianza. En la primera versión, solo Luis | Varios, por turnos |
| A nombre de quién | De esa persona: lo que se hace ahí es suyo | De la tienda; cada conteo, de quien entró |
| Modos | Lo que la persona puede: Precio siempre; Ticket y Conteo con sus permisos | Precio y Conteo. Sin Ticket ni impresiones |
| Entrar | Su huella, al empezar el turno. El PIN del celular no sirve; el del POS queda de respaldo | Precio, sin entrar. Para contar, la ficha y el PIN del POS |
| La sesión | El turno (8 h) | Se cierra a los 10 minutos sin usarse, solo con red. «Salir» a la vista |
| Existencias | Sí, como el F9 de la caja (menos lo que se está contando, [a ciegas](#a-ciegas)) | Nunca: ni en Precio ni al aplicar el conteo del día. Solo lo que ve el cliente |
| Ajustes | Los suyos | Los de quien entró; sin nadie, los del celular |
| Herramientas de medición | Sí | Piden la ficha y el PIN de alguien con «Configuración de la tienda» |

- **Se ven los modos del celular que la persona puede usar.** En el de Luis, lo que Luis puede. En el de la tienda nunca
  hay Ticket ni existencias, aunque entre Luis.
- **Quien no tiene la app sigue con `/conteo`** en el navegador de su celular. Lo que se cuenta en los dos se suma
  ([contar](#contar)).
- **Una persona, un celular.** Dar de alta otro a su nombre le quita el acceso al anterior. Antes, la caja dice si el
  anterior tiene algo por subir («El Pixel 8 Pro tiene 3 por subir · visto hace 8 min») y ofrece esperar a que suba. De
  la tienda puede haber varios.

### Lo que cambia en cada pantalla

Vale a 360 y a 448 dp de ancho. Nada nuevo toca la franja.

| | Celular de Luis | Celular de la tienda |
|---|---|---|
| Al abrir | En el último modo usado. Con la sesión cerrada, primero su huella | En Precio, sin nadie. Si alguien sigue en su sesión, donde iba |
| Arriba a la izquierda | La ficha «L» (48 dp), con su menú | Sin nadie, el ícono de la tienda (48 dp), con su menú. Con alguien, su ficha y, a 8 dp, «Salir»: dos botones de 48 dp de alto. «Salir» mide 72 dp de ancho y crece con la letra |
| La red, si falla | Junto a la ficha | Junto a «Salir». En el chico baja a un segundo renglón (medido: de 90 a 126 dp desde arriba; la indicación del escáner empieza en 159). Con letra grande no baja: se acorta a su ícono y el número, y el detalle va en el menú |
| Barra de modos | Precio · Ticket · Conteo | Precio · Conteo: dos pestañas de 128 dp en el chico y de 140 en el Pixel |
| Precio | Precio, mayoreo, promoción y existencia | Precio, mayoreo y promoción, como sobre el bloqueo |
| Botón rápido en Precio | ↑ Enseñar al cliente · ← Buscar; «Al ticket» va en la tarjeta | El mismo; la tarjeta no tiene «Al ticket» |
| Buscar | Con la existencia | Sin la existencia |
| Ticket e impresiones | Con «Mandar tickets desde el celular» | No salen |
| Conteo | Lo que Luis puede, también abrir departamentos | Al tocarlo, «¿Quién cuenta?»; luego, lo que puede quien entró |
| «Salir» | — | Arriba, junto a la ficha, y abajo en «¿Qué vas a contar?» |
| «Terminé mi parte» | Regresa a «¿Qué vas a contar?» | «Terminé mi parte y salir» |
| El menú de la ficha | Ajustes, la red, del bolsillo al precio y «Cerrar la sesión». Sin «Cambiar de persona» | «Entrar para contar» o «Salir», Ajustes y la red |
| Ajustes | Los suyos | Los de quien entró: el botón a la izquierda, las pistas, el bip y la vibración. La conexión (la caja principal, el nombre del celular, la sesión) solo se lee: se cambia en la caja |
| La pista del botón rápido | La de cada modo | La misma («↑ enseñar · ← buscar» en Precio); cada quien la ve su primera vez |
| Accesos directos del ícono | Revisar precio · Armar ticket · Contar | Revisar precio · Contar |

```
 Sin nadie, en Precio              Ana, contando
┌────────────────────────────────┐ ┌────────────────────────────────┐
│ (T)                       [L]  │ │ (A) (Salir)               [L]  │  la tienda, sin nadie · Ana y, a 8 dp, «Salir»
│                                │ │ (Sin red · 3 por subir)        │  en el chico, la red baja un renglón
│  [ ||| Apunta al código ]      │ │  [ ||| Apunta al código ]      │
│ ┌────────────────────────────┐ │ │ ┌────────────────────────────┐ │  ← 32 %
│ │ ────────────────────────── │ │ │ │ ────────────────────────── │ │  la franja: nada la tapa
│ └────────────────────────────┘ │ │ └────────────────────────────┘ │  ← 68 %
│ ╭──────────── ══ ────────────╮ │ │ ╭──────────── ══ ────────────╮ │
│ │ [foto] Cuaderno profesio…  │ │ │ │ Escolar · Anaquel     37 › │ │
│ │        $46.00              │ │ │ │ Lápiz HB No. 2   [−] [×13] │ │  el precio, sin existencia
│ │ (Desde 12: $41.00 c/u)     │ │ │ │ 1 caja + 1 suelta          │ │
│ │ (Precio) (Conteo)    [···] │ │ │ │ (Precio) (Conteo)    [···] │ │  dos modos, sin Ticket
│ ╰────────────────────────────╯ │ │ ╰────────────────────────────╯ │
└────────────────────────────────┘ └────────────────────────────────┘
```

`(T)` la tienda: su menú, con «Entrar para contar» · `(A)` la ficha de Ana: su menú · `(Salir)` cierra su sesión, con
«Deshacer» · `[L]` la linterna · `[···]` el botón rápido (tres puntos en abanico).

```
 Precio, tras cerrarse sola        Ana vuelve a entrar
┌────────────────────────────────┐ ┌────────────────────────────────┐
│ (T)                       [L]  │ │ (A) (Salir)               [L]  │
│                                │ │                                │
│  [ ||| Apunta al código ]      │ │  [ ||| Apunta al código ]      │
│ ┌────────────────────────────┐ │ │ ┌────────────────────────────┐ │
│ │ ────────────────────────── │ │ │ │ ────────────────────────── │ │
│ └────────────────────────────┘ │ │ └────────────────────────────┘ │
│ ╭──────────── ══ ────────────╮ │ │ ╭──────────── ══ ────────────╮ │
│ │ Se cerró la sesión de Ana: │ │ │ │ 7 guardadas a las 15:10:   │ │  fija hasta que alguien entre · lo de antes del cierre
│ │ lo contado quedó a su      │ │ │ │ Lápiz HB · Seguir sumando  │ │
│ │ nombre.  [Entrar]     [×]  │ │ │ │ Lápiz HB No. 2   [−] [× 8] │ │  sigue desde 7
│ │ (Precio) (Conteo)    [···] │ │ │ │ (Precio) (Conteo)    [···] │ │
│ ╰────────────────────────────╯ │ │ ╰────────────────────────────╯ │
└────────────────────────────────┘ └────────────────────────────────┘
```

### Dar de alta el celular

**Como quedó en la 0.4.0 (el celular como una caja, 2026-11-08):** en el celular, Ajustes › La conexión. 1. Busca la caja
principal en el Wi-Fi de la tienda (UDP a 5081) o se escribe su IP (solo direcciones privadas); `GET /api/estado` dice si
ahí hay un POS y si da de alta (no «La única caja» ni una tienda nueva). 2. El usuario y el PIN de quien tiene «Dar de
alta y quitar cajas» (`POST /api/alta/opciones`; el PIN no se guarda). 3. La sucursal si hay más de una, el nombre
(«Celular de Luis») y la serie (CEL, o la siguiente libre), que se proponen y se editan como en la PC; se ven las cajas que
ya hay. `POST /api/alta` siempre con `cajaId` vacío: una caja nueva, nunca le quita la llave a una que existe. 4. La llave
queda cifrada con el Android Keystore y la copia baja. Sin bloqueo de pantalla no se da de alta: la app lo dice y abre
la seguridad del celular. Quitarle el acceso: en la caja, Configuración › Cajas › «Quitar PC»; la app, al recibir el 401
«caja-sin-alta», borra la llave y la copia y lo dice. Si la caja principal cambia de IP, tras dos latidos sin respuesta
la app pregunta por UDP y, antes de mandarle la llave, le pide que pruebe quién es (`GET /api/identidad`), como la caja
de Windows.

**Lo que se había propuesto** (celulares aparte, con QR y confirmación en la caja), para que no se pierda:

1. **En la caja:** Configuración › Celulares › «Dar de alta un celular», con el permiso «Dar de alta y quitar cajas y
   celulares». La caja pregunta **¿de quién es?**
   - **De una persona:** se elige quién, de entre quienes tienen «Usar la app en su celular» (hoy, solo Luis). Los modos
     vienen con lo que esa persona puede; se pueden quitar. Si ya tiene un celular, la caja dice cuánto tiene por subir.
   - **De la tienda:** Precio y Conteo, y a los cuántos minutos sin usarse se cierra la sesión (10).
2. **La caja enseña el QR:** la dirección de la caja principal, el número de la caja que lo enseña, la llave pública del
   servicio y un código de un solo uso que vence en 10 minutos. De quién es y los modos no van en el QR: los guarda la
   caja principal con ese código, así que nadie los cambia en el camino.
3. **En el celular, «Escanear el código de la caja»,** con el mismo escáner.
4. **«Dispositivos cercanos»,** el permiso de red local de Android 17, explicado antes. Luego el celular comprueba que
   habla con la caja principal de verdad: pide `/api/identidad` con el número de la caja que enseñó el QR y un reto
   suyo, y revisa la firma del servicio con la llave del QR (la prueba HMAC es de la llave de la caja y no la puede
   revisar). Las cajas no hacen esto al darse de alta: ahí reciben esa llave y la usan después, al volver a encontrar la
   principal. Luego le presenta ese código.
5. **«¿Es este?», en la caja:** «Se conectó un moto g54». El celular dice qué será («Celular de la tienda · Precio y
   Conteo») y espera. Hasta que alguien toca «Sí, es este» en la caja, no queda activo. Si otro celular leyó el código
   primero, «No es este» lo descarta y la caja enseña otro.
6. **Solo en el de una persona:**
   - **«Confirma que es tuyo»:** su PIN del POS, una sola vez. Quien da de alta en la caja puede ser otro con el
     permiso; así nadie da de alta un celular a nombre de Luis sin Luis.
   - **«Entra con tu huella»:** desde ahí, entrar es su huella. Sin bloqueo de pantalla no se da de alta como de una
     persona: la app lo dice y abre los ajustes de seguridad del celular. Sin una huella que Android dé por segura,
     entra con su PIN del POS cada turno.
7. **El catálogo** baja en segundo plano (unos 9 MB con las fotos); mientras, ya se usa con red.

Queda en la bitácora: «Dio de alta el Pixel 8 Pro como celular de Luis (Precio, Ticket y Conteo)».

**El respaldo que se había propuesto mientras el POS no tuviera celulares** (ya no va: ahora la caja con copia es el
camino principal):

- **Solo el de Luis, y lo menos posible.** El de la tienda espera a los celulares.
- **Como una caja nueva, nunca una que ya existe:** «Celular de Luis», con una serie que no use ninguna caja (L). Dar de
  alta el celular en una caja que ya existe le cambia la llave: la PC de esa caja deja de entrar y el celular toma su
  lugar.
- **Sin copia del catálogo y solo con red.** La copia de las cajas lleva el hash del PIN de todos, y un PIN de 4 cifras
  sale de ahí en minutos. El precio y la búsqueda van con la API de hoy (`/api/productos/codigo`,
  `/api/productos/buscar` y `/api/consultas`), y la tarjeta va sin el departamento: la API no lo trae. Sin escribir
  nada, solo salen los rápidos (con red): la búsqueda de la API con el texto vacío no regresa nada. Sin red, Precio lo
  dice; el mosaico del bloqueo también pide red.
- **Entrar, igual:** su huella; su PIN del POS de respaldo, con la entrada de las cajas (`POST /api/cajas/{id}/sesion`).
- **Se quita con «Quitar PC»** en Configuración › Cajas y sus PC. Hasta entonces, su llave abre toda la API de su caja,
  y la API cree el `usuarioId` que le mandan ([1b](#lo-que-pide-al-pos-para-los-celulares)). Al llegar los celulares, se
  le quita la PC a esa caja y el celular se da de alta como de Luis.
- **Con «La única caja»** (la segunda sucursal) no hay respaldo: ahí el alta desde otra PC está cerrada.

### Entrar

**Como quedó en la 0.4.0:** es local, del celular. Con el celular dado de alta, la app se bloquea al abrirse y al volver
después de 10 minutos en el fondo, y se entra con lo que el celular acepte como seguro (la huella o la cara; si no, el
PIN, el patrón o la contraseña del celular). Mientras, la pantalla queda tapada y las lecturas no cuentan ni vibran; la
cámara sí se enlaza, así que al entrar ya escanea. Sin la copia con usuarios, quién usa la app no sale del PIN del POS;
las acciones que en el POS pidan un usuario (mandar un ticket) irán con el del alta o con `POST /api/sesion`, cuando
lleguen. El PIN del celular sí sirve aquí (es el bloqueo del celular, no el del POS): lo que sigue sobre «nunca el PIN
del celular» y la llave del Keystore que pide la huella en cada uso era lo propuesto.

**En el celular de Luis no hay fichas ni «Cambiar de persona»:** es suyo.

- **Con su huella, al empezar el turno.** Al abrir la app con la sesión cerrada sale el aviso del sistema, abajo. «Usar
  mi PIN del POS» queda de respaldo.
- **Solo lo que Android da por seguro, y nunca el PIN del celular.** El PIN del celular lo puede saber alguien de la
  casa o de la tienda: con él sería Luis en el POS. Cuenta la huella y, en los celulares que Android certifica como
  seguros, el rostro.
- **Otra huella no es Luis.** La llave que abre la sesión pide la huella cada vez, y Android la invalida si alguien
  registra otra huella o quita el bloqueo de pantalla. Entonces la app pide el PIN del POS y, con él, activa la huella
  otra vez.
- **La huella no sale del celular:** Android solo le dice a la app que pasó. El PIN del POS ya no pasa por el wifi cada
  mañana.
- **Dura el turno (8 h)** o hasta «Cerrar la sesión». Mientras, lo cuida el bloqueo del celular.
- **Sin red, entra con su huella.** El PIN del POS sí pide red: lo revisa la caja principal.

**En el de la tienda, Precio no pide nada:** enseña lo que ve el cliente. Para contar:

- **Al tocar «Conteo», «¿Quién cuenta?», en dos pasos.** Primero las fichas de quienes tienen «Contar inventario», tres
  por renglón, y ninguna viene elegida. Al tocar una, «PIN de Ana», con el teclado abajo, junto al pulgar, y «Otra
  ficha». Entra sola con la cuarta cifra. «Regresar», o el gesto de atrás, vuelve a Precio.
- **Caben 9 fichas en el chico,** también con letra al 200 % (en el prototipo, a 360 × 740, sus 6 fichas llegan hasta
  358 dp desde arriba, 385 con letra grande; con 9, el tercer renglón llegaría hasta 476 dp, 513 con letra grande). El
  teclado del PIN va en su propia pantalla, entero (de 426 a 706 dp desde arriba). Con más fichas, la lista se desliza
  hacia arriba, nunca de lado: deslizar desde la orilla es el gesto de atrás.
- **Los intentos, de ese celular:** 5 por persona; luego ese celular espera 5 minutos para esa persona. En la caja sigue
  entrando: quien prueba PIN en un celular sin vigilar no deja a nadie fuera de la caja. Cada espera queda en la
  bitácora y avisa por Telegram: «Demasiados intentos de Carmen desde el celular de la tienda».
- **Sin red no se entra:** los PIN no se guardan en el celular. La app lo dice antes de pedir el PIN. Quien ya entró
  sigue.
- **Al volver a entrar, cada quien sigue en lo que contaba.**

### Cuánto dura la sesión

| | Celular de Luis | Celular de la tienda |
|---|---|---|
| Dura | El turno: 8 horas, como `/conteo` | 10 minutos sin usarse, y a lo más el turno |
| Usarlo es | — | Leer un código, tocar la pantalla o moverse con TalkBack o el teclado |
| Sin red | Sigue | No se cierra sin red: si los 10 minutos se cumplen sin red, el aviso del minuto sale al volver la red (y, si sigue sin usarse, se cierra) |
| Se cambia | — | En Configuración › Celulares, de 5 a 30 minutos |
| Se cierra a mano | «Cerrar la sesión», en el menú de la ficha | «Salir» o «Terminé mi parte y salir» |
| Antes de cerrarse sola | — | Un minuto antes vibra y sale un aviso fijo, «Se cierra en 0:59», con «Seguir» de 56 dp; TalkBack lo lee al salir. Con la pantalla apagada, una notificación que vibra |
| Al cerrarse | El ticket armado se queda en el celular | Lo que contaba se guarda a su nombre y vuelve a Precio, sin nadie |

- **La ficha de arriba dice de quién es la sesión.** Si no es la tuya, «Salir».
- **«Salir» no pregunta y se deshace:** «Salió Ana · Deshacer», unos 6 segundos; la sesión se cierra en la caja
  principal al acabarse el aviso. Quien toca «Salir» puede ser quien llega, y no decide por otro. Lo del día sin aplicar
  se queda a nombre de Ana: nadie más lo aplica, se le recuerda al volver y, si no vuelve ese día, se descarta con
  aviso. «Terminé mi parte y salir» sí pregunta: lo toca quien contó.
- **Si se cierra sola a media cuenta,** lo que iba de ese producto se guarda a su nombre (en el completo, sube como una
  captura; en el del día, sigue sin aplicar). Al volver, en el lugar del producto: «7 guardadas a las 15:10: Lápiz HB»,
  con «Seguir sumando» y «Quitar». La siguiente lectura de ese producto sigue en 8 («8 · 7 guardadas + 1»), como al
  volver a cualquier producto en el mismo lugar ([el lugar y el orden](#el-lugar-y-el-orden)); «Quitar» lo deja en 0.
  Así no se cuenta dos veces el mismo anaquel.
- **Mientras, Precio lo dice:** un aviso fijo, «Se cerró la sesión de Ana: lo contado quedó a su nombre · Entrar», hasta
  que alguien entre o lo quite con ×. La primera lectura después avisa «No se contó: estás en Precio».
- **Las sesiones del de la tienda se guardan en la base de la caja principal,** con su inicio y su fin: un reinicio del
  servicio no las borra. Cada captura lleva su sesión y cuánto hace que se contó (C4). Hasta el fin del día, la caja
  principal acepta lo contado mientras esa sesión vivía, aunque ya se haya cerrado: lo que Ana contó en la bodega sin
  red sube a su nombre aunque ya haya salido.

### Sobre el bloqueo

Igual en los dos. El mosaico «Precio» abre el verificador sobre la pantalla de bloqueo, sin desbloquear y sin entrar:
nombre, precio, mayoreo y promoción, de la copia del catálogo. Ni existencia, ni ticket, ni conteo ([del bolsillo al
precio](#del-bolsillo-al-precio)).

- **En el de Luis,** «Abrir la app» pide desbloquear el celular. Si su sesión está abierta, sigue en Precio con ese
  producto; si no, pide su huella.
- **El de la tienda, sin bloqueo de pantalla** (lo propuesto): no guarda nada delicado y para contar pide la ficha y el
  PIN. Con bloqueo, nadie de fuera probaría PIN en él, pero todo el personal sabría el del celular (pregunta abajo).

### La copia del catálogo en cada uno

**Como quedó en la 0.4.0:** la copia de la caja (`GET /api/cajas/{id}/paquete` y después `/cambios` con el latido de
cada 30 s al frente), guardada como JSON propio con los productos, sus presentaciones, precios y existencia, todos los
códigos, las marcas, los sinónimos, lo más vendido, lo elegido, las promociones y el nombre de la tienda. Los usuarios
(con el hash de su PIN), sus permisos y reglas, los clientes y lo del ticket ni se leen. Sin departamento (la copia no lo
trae): la tarjeta enseña solo el código. Los rápidos y las fotos, después. La tabla de abajo es lo propuesto por clase de
celular; el de la tienda se replantea cuando toque.

| | De una persona | De la tienda |
|---|---|---|
| Productos, códigos, precios y mayoreo | Sí | Sí |
| Promociones y fotos | Sí | Sí |
| Lo de la búsqueda: sinónimos, marcas, lo más vendido | Sí | Sí |
| Los rápidos (Copia B/N, CURP) | Sí: se piden en línea a `GET /api/sucursales/{id}/rapidos` (la copia de las cajas no los lleva) y el celular guarda los últimos | No: son del ticket |
| Existencias | Sí | No: así tampoco baja cada venta |
| Quién es | Esa persona: su nombre y lo que puede | Nadie: las fichas se piden con red |
| Usuarios, PIN (ni su hash), clientes y crédito, costos y utilidad, folios, efectivo, el ticket impreso | Nunca | Nunca |

- **Lo que no va en la copia tampoco se lo contesta la API.** Al de la tienda, el precio le llega sin existencia, y el
  conteo del día sin lo que tenía el sistema ni la diferencia, aunque cuente quien ve los costos: eso se ve en la caja.
- **Con el respaldo no hay copia:** todo va con red.

### Si se pierde un celular

- **Desde cualquier caja:** Configuración › Celulares › el celular › «Quitar el acceso». Pregunta ahí mismo y no pide el
  PIN otra vez, como «Quitar PC» en Configuración › Cajas y sus PC. Queda en la bitácora.
- **Al momento,** la caja principal olvida su llave y cierra sus sesiones: nada de lo que mande después entra.
- **Si vuelve a ver la caja principal,** el celular borra su llave, su copia y lo que tenía guardado, y dice «Este
  celular ya no tiene acceso». Lo que no alcanzó a subir se pierde; si aparece el celular, se da de alta otra vez.
- **Lo que se lleva no es delicado:** precios y, en el de una persona, existencias. Nada de PIN, clientes ni costos.
- **Dar de baja a la persona, o quitarle «Usar la app en su celular»,** detiene su celular. Con el permiso de vuelta,
  sigue, sin darlo de alta otra vez.
- **Robado con la sesión abierta,** el de Luis hace lo que Luis hasta que se le quite el acceso o se acabe el turno. Los
  tickets llegan en espera y se ven antes de cobrarlos; las capturas, antes de cerrar el departamento. El conteo del día
  entra al kardex al aplicarse: queda en la bitácora a nombre de Luis y se corrige con un ajuste.
- **Cambiar de celular no es perderlo:** al dar de alta el nuevo, la caja dice cuánto tiene por subir el anterior y
  espera a que suba, si se le pide.

### En el POS: Configuración › Celulares

**No va (dueño, 2026-11-08):** el celular sale en Configuración › Cajas con su serie y su equipo, y ahí se le quita la
llave («Quitar PC»). Lo que sigue era lo propuesto.

```
┌ Configuración › Celulares ────────────────────── [ + Dar de alta un celular ] ┐
│                                                                               │
│  Celular de Luis        De Luis         Precio · Ticket · Conteo              │
│  Pixel 8 Pro            Hoy 14:52 · 0.2.0 · catálogo al día · nada por subir  │
│                                                          [ Quitar el acceso ] │
│  ───────────────────────────────────────────────────────────────────────────  │
│  Celular de la tienda   De la tienda    Precio · Conteo · sesión de 10 min    │
│  moto g54               Ayer 18:10 · 0.2.0 · catálogo al día · 3 por subir    │
│                                                          [ Quitar el acceso ] │
└───────────────────────────────────────────────────────────────────────────────┘
```

- **Un grupo nuevo, junto a «Cajas y sus PC»,** para quien tiene «Dar de alta y quitar cajas». En pantalla pasa a «Dar
  de alta y quitar cajas y celulares»: es el mismo cuidado.
- **De cada celular:** nombre, modelo, de quién es, sus modos, la sesión (el de la tienda), la última vez que se vio, su
  versión, de cuándo es su catálogo y cuánto tiene por subir. La última vez que se vio delata uno perdido.
- **Los modos se cambian ahí** y el celular los toma al momento, por su canal de avisos. Si le quitan el modo en que
  estaba, pasa a Precio y lo dice; el ticket armado se queda guardado.
- **De quién es no se cambia:** para pasar el de Luis a la tienda, se da de alta otra vez.

### Los permisos nuevos

**No van por ahora (dueño, 2026-11-08):** el celular entra con la llave de su caja, como una PC. Lo que sigue era lo
propuesto.

En un grupo «Celular» de Usuarios y permisos. Los dos son solo «Puede» o «No»: el celular no tiene cómo pedir una
autorización.

| Permiso | Qué deja | Por defecto | La primera cuenta |
|---|---|---|---|
| **Usar la app en su celular** | Tener un celular dado de alta a su nombre | No | Puede |
| **Mandar tickets desde el celular** | Ticket e impresiones en su celular | No | Puede |

- **Es la alta confianza en un solo lugar:** al llegar, solo Luis los tiene. Darle el primero a alguien es darle la app.
- **El de la tienda no los pide:** ahí cuenta quien tiene «Contar inventario», como en la caja y en `/conteo`. Precio no
  pide permiso: es lo que ve el cliente.
- **En el código:** van al final de `AccionControlada` y en `SoloPuedeONo`; al llegar, «Puede» para quien tiene todos y
  «No» para los demás, como «Cancelar una entrada o salida».

### Lo que pide al POS para los celulares

Detalla los puntos 1, 2 y 4 de [lo que pide al POS](#lo-que-pide-al-pos). **Lo de 1a a 1e ya no va** (dueño, 2026-11-08:
el celular es una caja más); se deja para cuando se replantee el celular de la tienda (6.3). El 2a sigue.

| # | Qué | Por qué | Para |
|---|---|---|---|
| 1a | **De quién es y sus modos:** el alta dice si es de una persona (cuál) o de la tienda, y con qué modos; la caja principal lo guarda con el código de un solo uso, y el celular queda activo cuando la caja confirma «¿Es este?». El QR lleva la llave pública del servicio, para que el celular compruebe que habla con la caja principal (con «La única caja» no hay búsqueda por UDP para volver a encontrarla si le cambia la IP). El celular pide su perfil (de quién es, sus modos y lo que puede esa persona) al conectarse y con cada aviso | Lo decide quien da de alta, no quien lo usa. Sin la confirmación, el QR le sirve a quien lo lea primero. Es el C8 de Contar | Los dos |
| 1b | **Lo que deja cada llave:** solo las rutas de sus modos. Nada de cobrar, turnos, cortes, efectivo ni configuración, ni revisar, cerrar o cancelar un conteo. Quién hace cada cosa sale de la llave (la de una persona) o de la sesión (la de la tienda, 1c), nunca del `usuarioId` que manda el celular | Hoy la API cree el `usuarioId` que le mandan: con la llave de un celular robado se haría lo que puede cualquiera | Los dos |
| 1c | **La sesión del de la tienda:** sus fichas y entrar con el PIN (`GET /api/celulares/{id}/fichas` y `POST /api/celulares/{id}/sesion`, como los de las cajas). La sesión se guarda en la base, con su inicio y su fin, y se cierra con «Salir», cuando el celular dice que pasaron los 10 minutos o al acabar el turno. Cada captura lleva su sesión y cuánto hace que se contó (C4); hasta el fin del día se acepta lo contado mientras la sesión vivía. Los intentos se cuentan por celular y persona (5, y ese celular espera 5 minutos), con la bitácora y Telegram | Que cada captura sea de quien la contó, también la que sube tarde o después de un reinicio. Y que probar PIN en un celular sin vigilar no deje a nadie fuera de la caja | El de la tienda |
| 1d | **Configuración › Celulares:** la lista, los modos, «Quitar el acceso», la última vez que se vio y cuánto tiene por subir (de su latido, como C5). Dar de alta otro a nombre de alguien le quita el acceso al anterior; antes, la caja dice cuánto tiene por subir y ofrece esperar | Un celular perdido o cambiado | Los dos |
| 1e | **Los permisos «Usar la app en su celular» y «Mandar tickets desde el celular»** | La alta confianza, en Usuarios y permisos | El de Luis, Ticket |
| 2a | **La copia del de la tienda sin existencias ni usuarios,** y la API tampoco se los contesta: ni la existencia en el precio ni lo que tenía el sistema (`Esperado`, `Diferencia`) al aplicar el conteo del día | Solo lo que ve el cliente, y a ciegas | El de la tienda |

### Preguntas de quién tiene la app

- [ ] En tu celular, ¿entrar con tu huella al empezar el turno (lo propuesto) o con tu PIN del POS? ¿Y que el PIN del
  celular no sirva (lo propuesto)? Si sirviera, quien lo sepa sería tú en el POS.
- [ ] En el de la tienda, ¿Precio sin entrar (lo propuesto)?
- [ ] ¿El de la tienda sin existencias (lo propuesto)? Con ellas contestaría «¿hay en bodega?», pero enseñaría más que
  lo que ve el cliente a quien no entró. Lo que se está contando seguiría oculto.
- [ ] ¿La sesión del de la tienda se cierra a los 10 minutos sin usarse, y solo con red (lo propuesto)? En la caja
  dijiste que no a pedir el PIN otra vez tras unos minutos sin usarla, porque estorba (2026-10-17); aquí el celular pasa
  de mano en mano y anda lejos de la caja. Es la misma pregunta de Contar.
- [ ] ¿El de la tienda con bloqueo de pantalla? Sin él (lo propuesto), cualquiera que lo agarre puede probar PIN, y cada
  espera te avisa por Telegram. Con él, todo el personal sabría el PIN del celular.
- [ ] ¿Quién más tendrá la app en su celular, y desde cuándo? La de Contar pregunta por el encargado.
- [ ] ¿Qué celular sería el de la tienda, y cuántos? Se mide antes de comprarlo.
- [ ] ¿Una persona, un celular (lo propuesto), o alguien necesita dos?

## Del bolsillo al precio

- **El mosaico de Ajustes rápidos pasa a ser «Precio»:** abre el escáner sobre la pantalla de bloqueo, sin el PIN del
  celular, siempre en Precio: aunque la app esté en Ticket o en Conteo, lo leído ahí no entra a ninguna lista. Es el
  camino de apertura que ya se midió (la cámara enlaza antes que la interfaz). Por dentro conserva el nombre de cuando
  decía «Escanear»: el sistema guarda el mosaico por él, y con otro se lo quitaría del panel a quien ya lo tiene.
- **Sobre el bloqueo solo se ve el precio:** nombre, precio, mayoreo y promoción y, de una presentación (la caja de 12),
  cuánto sale cada pieza, como en la tarjeta; ni la existencia, ni el ticket, ni el conteo. Lo que no está dice «No está
  en el catálogo» con su código. «Abrir la app» pide desbloquear y sigue en Precio con ese producto (el de la tienda,
  sin bloqueo, abre directo); en el de Luis, si su sesión está cerrada, antes pide su huella ([sobre el
  bloqueo](#sobre-el-bloqueo)). Mientras se teclea el PIN, el escaneo rápido no se cierra solo; si se cancela, sigue
  ahí.
- **Sale de la copia del catálogo,** así que no espera a la red: del bolsillo al precio en lo que tarda la lectura.
- **Los accesos directos del ícono:** «Revisar precio» en los dos celulares, «Armar ticket» en el de Luis y «Contar»
  solo si se puede contar. Los tres abren como el ícono, en su modo.
  - **«Revisar precio» se queda fijo** (`shortcuts.xml`): sirve en los dos celulares y nunca se apaga, sale desde que
    se instala la app (antes de abrirla) y el que alguien ancló con la 0.1 sigue sirviendo. Para eso tiene que seguir
    fijo y con el mismo id.
  - **«Armar ticket» y «Contar» son dinámicos** (`ShortcutManagerCompat`): solo esos se ponen y se quitan mientras la
    app corre, según lo que haga cada celular. La app los revisa cada vez que arranca, después del primer cuadro
    analizado (para no estorbarle a la cámara), y los vuelve a publicar solo si el menú cambió. Llevan id nuevos: los de
    `shortcuts.xml` no se pueden tocar desde el código. Lo que alguien ancló de ellos se apaga con su mensaje cuando ya
    no va («En este celular no se arman tickets», «En el piloto no se cuenta») y vuelve a servir cuando sí.
  - **Lo que se ancló de «Ticket» y «Conteo» con la 0.1 o la 0.2 se apaga para siempre.** Se quedan declarados apagados
    en `shortcuts.xml`, con el mensaje «Este acceso cambió. Mantén presionado el ícono para ver los de ahora.», y ya no
    salen en el menú. Quien los quiera otra vez tiene que anclar «Armar ticket» o «Contar», que son otros. El mensaje
    quizá no se vea: los lanzadores de Android 15 y 16 que se revisaron dicen solo «El acceso directo no está
    disponible» (el del Pixel falta verlo), así que a Luis hay que decírselo por otro lado.

## Sin red

| | Sin red |
|---|---|
| Precio | Sí, de la copia del catálogo |
| Buscar | Sí, de la copia |
| Armar el ticket | Sí |
| Mandarlo | No: se enseña como QR |
| Impresiones | No: se agregan en la caja con F2 |
| Contar | Sí: lo contado sube solo al volver la red |
| Entrar | Con la huella o el bloqueo del celular: es local (0.4.0). Lo propuesto para el de la tienda (solo quien ya entró) se replantea con la 6.3 |

- **La red tiene tres estados,** como en la caja: buena, lenta y caída, por lo que tarda el latido. En «lenta», mandar
  espera su turno sin trabar la pantalla.
- **La copia del catálogo** es la de las cajas, sin lo que un celular no debe cargar (ver abajo). Se pone al día sola con
  el aviso de la caja principal (el canal de versiones que ya usan las cajas) y pide solo lo que cambió.

## Ajustes y herramientas de medición

- **Lo del personal,** en Ajustes: vibración y bip (apagado de fábrica, como hoy), linterna automática, «Botón rápido a
  la izquierda» y «Volver a ver las pistas». Van por persona: en el celular de la tienda, los de quien entró; sin nadie,
  los del celular.
- **La conexión,** solo para leer (0.4.0): la caja principal y cómo está la red, qué caja es este celular (nombre, serie,
  sucursal, equipo), cuántos productos tiene la copia y de cuándo es. «Quitar el alta» se hace desde la caja
  (Configuración › Cajas › «Quitar PC»). Sin alta, ahí mismo está el alta paso a paso. Sin «Buscar cambios ahora»: el
  latido de 30 s basta; si en las pruebas de la tienda hace falta, se agrega.
- **Las herramientas de medición** (sesión de prueba, banco, estrés y los ajustes del escáner: motor, lente, resolución,
  cuadros por segundo) se quedan, porque se sigue midiendo en la tienda, pero bajan a Ajustes › Herramientas de
  medición. Hoy son cuatro de las cinco pestañas de abajo. En el celular de la tienda piden la ficha y el PIN de alguien
  con «Configuración de la tienda».
- **Los contadores del botón rápido** (con toque o con gesto, y las opciones que se cierran en menos de 2 s) van ahí,
  sin el nombre de nadie, y salen solo con el CSV de la sesión de prueba: la app no manda estadísticas.

## Colores, letras y tamaños

- **El tema de la app,** el de hoy: azul petróleo y grises, claro y oscuro según el celular, sin los colores dinámicos
  del sistema.
- **Un color por modo:** Precio en ámbar (el del verificador de la caja), Ticket en azul petróleo y Conteo en violeta. Va
  en la pestaña del modo, el botón principal y el renglón resaltado; nunca en la retícula, donde el verde de «leído» y
  el ámbar de «viendo» son del escáner.
- **Las letras del sistema** (Roboto) y cifras tabulares: el precio a 40 sp (34 en el chico), el total a 24 sp, el
  contador a 48 sp (38 en el chico).
- **56 dp** para lo principal, como hoy, y 48 dp como mínimo para todo lo que se toca. El botón rápido mide 56 dp (su
  zona de toque, 64) y sus opciones 48 dp, con su nombre al lado o arriba.

## Lo que cambia por dentro

- **Lo que no se toca:** `nucleo` y `escaner` (la cámara, los motores, la pantalla del escáner con su retícula, guía,
  confirmación, bip y vibración). La app usa lo que ya expone: `PantallaEscaner` con lo de encima y las lecturas de
  `ControladorEscaner.eventos`. La regla de búsqueda en el catálogo del núcleo (las variantes de UPC y EAN) se usa tal
  cual sobre la copia del catálogo. Si un día hiciera falta cambiar algo del escáner, va en su propio PR y se mide contra
  la línea base, como siempre.
- **Del escáner, la app solo cambia en caliente dos ajustes** (`ControladorEscaner.configurar`, sin tocar `escaner`):
  la vibración, apagada mientras una hoja o el precio en grande tapan el visor, para no vibrar «leído» por lo que no
  cuenta; y, si se aprueba, `msParaRepetirAVista = 0` al entrar a Conteo. Cambiar solo la vibración no rehace el motor
  ni el `Confirmador`.
- **El módulo `pos`** tiene lo del núcleo del POS portado (la búsqueda y el cálculo del precio). **La conexión quedó en
  `app` (0.4.0):** `caja/` (los DTO de la API escritos a mano, el cliente con OkHttp, el descubrimiento por UDP, la prueba
  de identidad, el alta con la llave cifrada con el Keystore y `Conexion`, que late, pide los cambios y vuelve a
  encontrar la caja principal) y `copia/` (la copia como JSON propio, cómo se le aplican los cambios y cómo se vuelve
  productos). Sin Room: la copia entera se lee a memoria al abrir, porque la búsqueda ya vive en memoria; con decenas de
  miles de productos se mide en el Pixel cuánto tarda. La bandeja de salida llega con los tickets. La sesión con la
  llave del Keystore que pide la huella en cada uso era lo propuesto; quedó la huella local al entrar ([entrar](#entrar)).
- **`app`:** las pantallas nuevas sobre la de hoy; las de medición bajan a Ajustes. El botón rápido se excluye del gesto
  de atrás (`Modifier.systemGestureExclusion()`). De los accesos directos del ícono, «Armar ticket» y «Contar» pasan a
  dinámicos, con id nuevos, y «Revisar precio» se queda fijo en `shortcuts.xml`, donde «Ticket» y «Conteo» de antes
  siguen declarados, apagados ([del bolsillo al precio](#del-bolsillo-al-precio)).
- **El contrato:** el POS todavía no publica OpenAPI. Mientras, los DTO se escriben a mano en Kotlin (camelCase, los
  enums como números, ignorando campos nuevos), con ejemplos de JSON sacados de las pruebas del POS. Cuando el POS
  publique OpenAPI con sus respuestas tipadas, el cliente se genera de ahí.
- **La red y los permisos (0.4.0).** La app pasó a **solo la red de la tienda**:
  - Pide Internet y la huella (`USE_BIOMETRIC`, un permiso normal) y nada más (el estado de la red sigue fuera). Con targetSdk 36, Android 17 deja pasar a la red local con
    Internet; al pasar a targetSdk 37 habrá que pedir `ACCESS_LOCAL_NETWORK` en tiempo de ejecución (el permiso de
    Android 17 que reemplazó a «Dispositivos cercanos»). La revisión de cada APK (`verificarPermisos`) acepta cámara,
    vibración, Internet y la huella, y sigue sin el envío de estadísticas de ML Kit.
  - El cliente solo se conecta a la caja principal dada de alta, y solo a direcciones privadas (10.x, 172.16–31.x,
    192.168.x). No manda nada a internet, ni estadísticas ni fotos de la cámara.
  - El servicio habla HTTP, sin cifrar (la llave y los PIN viajan así, como con las cajas), así que Android lo permite
    (`res/xml/red_segura.xml`, que no puede acotar por rango de IP: lo acota la app). El POS ya pide que cajas y
    celulares no compartan la red con el público.
- **Versiones:** `0.2.0` con la primera entrega de esta propuesta, `0.3.0` con «Del bolsillo al precio», `0.4.0` con «El
  celular de Luis»; `1.0.0` cuando se use en la tienda con el POS, como dice el plan.

## Lo que pide al POS

Nada de esto existe hoy en `papeleria-pos`; cada punto dice qué entrega de aquí lo necesita.

| # | Qué | Por qué | Para la entrega |
|---|---|---|---|
| 1 | ~~**Celulares** aparte (1a–1e)~~ **No va (dueño, 2026-11-08): el celular es una caja más**, con el alta de siempre. El POS hizo que una caja que nunca abre turno no estorbe (el corte del día, la bitácora de red, las listas de cajas). Lo que se pedía para el celular de la tienda (1a, 1b, 1c) se replantea con la 6.3 | Era: un celular dado de alta como caja tiene serie, sale en el corte del día y en la red de cajas, y su llave abre toda la API de su caja. Se aceptó: el celular no vende ni abre turno, y si se pierde se le quita la llave como a una PC | — |
| 2 | **La copia del catálogo para el celular:** **se cumple con la copia de la caja (0.4.0):** la app descarta al leer los usuarios, sus permisos y reglas, los clientes y lo del ticket. Queda pendiente el departamento de cada producto (la copia no lo trae: la tarjeta enseña solo el código) y, para el de la tienda, la copia sin existencias (2a) | La de hoy trae el hash del PIN de cada usuario y los clientes; en un celular que se puede perder, no: por eso la app no los guarda. Sin el departamento, «Falta» y el conteo sin red no sabrán de cuál es cada producto | 2 (hecho) y 6 |
| 3 | **La regla de los códigos** (las variantes de UPC y EAN) en el servicio y en la copia de las cajas | Hecho en el POS (rama `celular/como-caja`, 2026-11-08), con la misma regla de `Catalogo.kt`; un código cuyas variantes dan con dos productos no se encuentra, y el celular hace lo mismo | 2 (hecho) |
| 4 | **Mandar un ticket a una caja:** recibirlo, avisarle a esa caja por su canal de avisos (con acuse) y que entre en espera con quien lo mandó; el permiso «Mandar tickets desde el celular» | Es la entrega 12 de la propuesta del POS; los tickets en espera hoy viven solo dentro de cada caja | 4 |
| 5 | **Las cajas para el celular:** quién atiende, cuántos tickets esperan y si está prendida | Hoy la caja no le dice al servicio quién atiende ni cuántos esperan | 4 |
| 6 | **Impresiones:** apartarlas (en el POS: Control Print no se entera) mientras están en un ticket del celular, y revisar «por cobrar» dentro del candado del cobro | Que dos no tomen la misma; y que dos cobros simultáneos no cobren dos veces una impresión del POS | 5 |
| 7 | **El QR del ticket en la caja:** probar en la tienda que el lector lea QR y mande cada renglón con Enter, con el «Fin» que tenga la caja 2 (con «Tab» no sirve) | Si no, la caja tendría que entender el QR | 4 |
| 8 | **Conteo:** capturar ligero, ponerse al día, el número propio en el del día, cuándo se contó, quién está contando y qué le falta subir, que los conteos no se pisen, quién cuenta de la sesión y el lugar en la captura. Detalle: de C1 a C11, [lo que pide al POS para contar](#lo-que-pide-al-pos-para-contar) | Cada captura hoy regresa el departamento entero; el del día no evita duplicados ni mira si un departamento está abierto (una corrección en medio se cuenta dos veces), y se compara contra lo que hay al llegar | 6 |
| 9 | **El conteo de arranque** (C7): decidir cuándo (A, B o C); con C, el del día solo en lo ya contado y el primer cierre de cada departamento marcado «arranque» | Mientras el POS lee eleventa, no deja contar | 6 |
| 10 | **El mayoreo en el F9 de la caja** | Su propia propuesta lo pide; que el celular y la caja enseñen lo mismo | — |

## Entregas

Cada entrega se prueba en el emulador y en el Pixel, y en ninguna cambia la cámara. Las primeras sirven ya en el piloto.
El orden: primero lo que usa el dueño (el precio, el bolsillo, el ticket, las impresiones), luego contar y al final el
celular de la tienda.

| # | Entrega | Qué trae | Necesita del POS |
|---|---|---|---|
| 1 | **La base** | Los tres modos sobre el escáner de hoy (Conteo, todavía el de prueba: el de verdad llega en la 6 y en el piloto no aparece), el panel, el botón rápido con su gesto, la búsqueda y el ticket con el catálogo de prueba (sin red todavía), el tema, Ajustes con las herramientas de medición, el visor del mismo tamaño | Nada |
| 2 | **El celular de Luis** (0.4.0) | El alta como caja (buscar la caja principal o escribir su IP, el PIN del dueño, el nombre y la serie), la copia del catálogo de la caja sin usuarios ni clientes y sus cambios con el latido, el precio y la búsqueda con la copia también sin red, entrar con la huella. Los productos de prueba quedan como herramienta | 3 y la copia de la caja (2); el documento del POS de la rama `celular/como-caja` |
| 3 | **Del bolsillo al precio** | El mosaico «Precio» sobre el bloqueo y los accesos directos: «Revisar precio» fijo, «Armar ticket» y «Contar» dinámicos | — |
| 4 | **Mandar el ticket** | «¿A qué caja?», mandarlo, la confirmación y el QR sin red | 4, 5, 7 |
| 5 | **Impresiones** | La hoja de impresiones con miniaturas y lotes, el renglón en el ticket | 6 |
| 6 | **Conteo**, después del piloto | 6.1 el completo; 6.2 el del día; 6.3 el celular de la tienda, con Precio sin entrar y Conteo por turnos ([cómo se entregaría](#cómo-se-entregaría)) | 6.1: 1, C1, C2, C4, C5, C9, C10, C11 y, con B, C7 · 6.2: C3 y, con C, C7 · 6.3: 1a, 1b, 1c, 2a |

## Preguntas para el dueño

Las del botón rápido, las de Contar y las de quién tiene la app van en su sección:
[el botón rápido](#preguntas-del-botón-rápido), [Contar](#preguntas-de-conteo) y
[quién tiene la app](#preguntas-de-quién-tiene-la-app).

- [x] ¿Quién arma tickets en el piso? Por ahora, solo quien tiene la app en su celular y «Mandar tickets desde el
  celular»: el dueño. El de la tienda no arma tickets (dueño, 2026-11-03).
- [x] ¿Los celulares son de cada quien o de la tienda? Primero, los de personas de confianza; a futuro, quizá uno de la
  tienda con Precio y Conteo (dueño, 2026-11-03). Cómo se entra queda en las preguntas de quién tiene la app.
- [ ] ¿El precio sobre el bloqueo, sin el PIN del celular? Solo enseña lo que el cliente ve en la tienda. Igual en los
  dos celulares.
- [x] ¿La copia del catálogo en el celular o solo con red? **Con copia** (dueño, 2026-11-08): la de la caja, y la app
  descarta los usuarios con el hash de su PIN y los clientes al leerla. Recomendado y por revisar con el dueño: que la
  copia de las cajas deje de traer el hash de los PIN; mientras, la app no los guarda.
- [x] ¿Los celulares como «celulares» en el POS o como una caja más? **Como una caja** (dueño, 2026-11-08), con el alta
  que ya existe. El POS hizo que una caja que nunca abre turno no estorbe.
- [ ] ¿Apartar las impresiones que se agregan en el celular?
- [ ] ¿El conteo físico de arranque: A, B o C (lo recomendado)? Está en las preguntas de Contar.
- [ ] ¿Las herramientas de medición las ve todo el personal o solo quien tiene «Configuración de la tienda»? Lo
  propuesto: en el celular de una persona, esa persona; en el de la tienda, solo con la ficha y el PIN de alguien con
  «Configuración de la tienda».
- [ ] ¿La app abre en el último modo usado (lo propuesto) o siempre en Precio? El de la tienda abre en Precio, salvo que
  alguien siga en su sesión.
