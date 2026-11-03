# Propuesta: la app conectada al POS

> **Para aprobar (2026-11-03).** Se prueba en el [prototipo navegable](propuesta-interfaz/prototipo.html): se abre con
> doble clic en la PC o en el celular, sin instalar nada. Arriba se cambian la red, si el POS está en el piloto, el tamaño
> del celular y el tema; a la derecha está «Qué probar». Todo es ficticio.
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
- **El modo dice qué hace cada lectura,** con color, icono y nombre. El precio nunca agrega al ticket: así no se cobra
  por error lo que solo se preguntó.
- **Cambiar de modo no reabre la cámara:** el escáner sigue abierto y solo cambia lo que la app hace con la lectura.
- **Botones de 56 dp** (como hoy), texto que aguanta el tamaño de letra al 200 % y nunca solo el color.
- **Lo que se pueda, sin red.** La red de la tienda se cae (cuando se va el internet, el wifi de los celulares también):
  el precio, la búsqueda y el ticket siguen; el conteo espera para subirse.
- **Lo que la persona no puede hacer, no aparece,** como en la caja nueva.
- **Un solo botón para lo demás,** abajo a la derecha, donde descansa el pulgar: un toque abre tres opciones y pulsar,
  deslizar y soltar elige una sin mirar ([el botón rápido](#el-botón-rápido)).
- Se diseñó y se probó a **360 × 740 dp** (un celular chico) y a **448 × 997 dp** (el Pixel 8 Pro).

## Lo que necesita el piso, de lo más a lo menos seguido

Las frecuencias son estimadas; se confirman en la tienda.

| # | Necesidad | Qué tan seguido | Cómo queda |
|---|---|---|---|
| 1 | Contestar «¿cuánto cuesta?» | Muy seguido | **Precio**: escanear y ver precio, mayoreo, promoción y existencia; ↖ «Enseñar al cliente» lo pone en grande |
| 2 | Pasar lo que lleva el cliente | En cada preventa | **Ticket**: cada código suma uno; lo último arriba, con − y + |
| 3 | Lo que no se escanea | Seguido | ← «Buscar»: dos pedazos de palabra, con el campo junto al pulgar |
| 4 | Las impresiones que trae en la mano | Muchas veces al día | ↖ «Impresiones»: las miniaturas en lotes; se tocan las que trae |
| 5 | Mandarlo a la caja | Al terminar cada ticket | ↑ «Mandar a Caja 2»: la última caja usada ya viene elegida |
| 6 | Sin red | Unas veces al año, por horas | El ticket como QR para el lector de la caja |
| 7 | Contar | El conteo del día; el completo, al arrancar y una vez al año | **Conteo**: a ciegas, cajas y sueltas, se guarda solo |
| 8 | Cambiar de persona | Al cambiar de turno | La ficha y el PIN, como en la caja |
| 9 | Dar de alta el celular | Una vez | Escanear el código que enseña la caja |

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
│ │ (Precio) (Ticket) (Conteo)    [ + ]  │ │  los modos y el botón rápido
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

`[L]` la linterna · `[x]` quitar (el bote) · `[ + ]` el botón rápido.

- **Arriba a la izquierda,** la ficha de quien entró: abre «Este celular» (cambiar de persona, ajustes, la red, salir). A
  su lado, el estado de la red **solo si algo falla** («Red lenta», «Sin red · 3 por subir»), como los chips de la caja.
- **Arriba a la derecha,** la linterna del escáner, como hoy.
- **Abajo, el panel del modo** y la barra de modos: **Precio · Ticket · Conteo**. Se cambia tocando el nombre; deslizar
  sobre la cámara no cambia de modo, para no cambiarlo sin querer al apuntar. Si hay un ticket armado, «Ticket» lleva
  cuántos artículos trae.
- **Al final de esa barra, el botón rápido:** lo demás de cada modo (buscar, impresiones, mandar…), con un toque o con
  el gesto ([abajo](#el-botón-rápido)).
- **El asidero** (o tocar el encabezado) abre el modo completo: el ticket entero o lo contado. Mientras está abierto, las
  lecturas no cuentan.
- **La app abre en el último modo usado.** El mosaico y los accesos directos abren uno en particular
  ([del bolsillo al precio](#del-bolsillo-al-precio)).
- **El panel cabe en el espacio bajo la franja** (unos 280 dp en un celular chico y 360 en el Pixel): el encabezado, lo
  último leído y los modos con el botón rápido. Sin una fila de botones, ese espacio es para la lista. Lo demás se ve
  al jalar.

### El botón rápido

Pedido por el dueño (2026-11-03): lo demás de cada modo en un solo botón, para usarlo con una mano.

```
┌──────────────────────────────────────────┐
│ ╭──────────────────────────────────────╮ │
│ │ Ticket · 4 art. ░░░░░░░░░░░ $129.50  │ │  lo de atrás se atenúa
│ │░░░░░░░░░░░░░ ↑ Mandar a Caja 2  (>)  │ │  ↑ lo principal del modo
│ │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │ │
│ │░░░░░░░ ↖ Impresiones  (▤)⁶░░░░░░░░░  │ │  ↖ lo segundo
│ │░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  │ │
│ │░░░ ← Buscar  (Q)░░░░░░░░░░░░  [ × ]  │ │  ← siempre buscar
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

- **Uno solo, abajo a la derecha,** donde descansa el pulgar, al final de la barra de modos. Reemplaza la fila de botones
  que iba bajo la lista.
- **Un toque abre tres opciones,** cada una con su nombre y su flecha. Se toca una; tocar fuera o el botón otra vez
  cierra.
- **Pulsar y deslizar elige sin mirar:** se pulsa el botón, se desliza hacia una opción y se suelta. Cuenta la
  dirección, no llegar hasta el botoncito: bastan unos 28 dp, y cada dirección acepta un abanico ancho (arriba, la
  diagonal o la izquierda), así que el gesto no tiene que ser exacto. Una vibración corta al pasar por una opción y otra
  al elegirla. Es el gesto que se aprende: pulsar y deslizar hacia arriba es «mandar», sin buscar nada con la vista.
- **Siempre lo mismo en cada dirección:** ← es buscar en los tres modos, ↑ lo principal del modo y ↖ lo segundo.

| Modo | ↑ arriba | ↖ diagonal | ← izquierda |
|---|---|---|---|
| Ticket | Mandar a Caja 2 | Impresiones (con cuántas hay por cobrar) | Buscar |
| Precio | Al ticket (no en el celular de la tienda) | Enseñar al cliente | Buscar |
| Conteo | Guardar | Lo contado | Buscar lo que no tiene código |

- **Cancelar:** soltar hacia abajo o a la derecha, o regresar al botón antes de soltar.
- **Nada se manda solo:** ↑ en Ticket abre «¿A qué caja?», donde se confirma.
- **Mientras está abierto,** lo de atrás se atenúa y las lecturas del escáner no cuentan.
- **Se enseña solo:** la primera vez sale una pista sobre el botón («Pulsa y desliza: ↑ mandar · ↖ impresiones ·
  ← buscar») y las etiquetas llevan su flecha.
- **Con TalkBack o con teclado,** el botón abre las tres como botones normales; atrás o Esc lo cierran.
- **Zurdos:** en Ajustes, «Botón rápido a la izquierda»: el botón pasa a la esquina izquierda y las direcciones van en
  espejo (↑, ↗, →).
- **En Compose:** un `pointerInput` con `awaitEachGesture` sobre el botón: el primer toque abre las opciones, cada
  movimiento calcula el ángulo desde el centro y el sector, y al soltar se elige o se cancela; la vibración con
  `HapticFeedback`. Como el menú ya está dibujado y solo cambia de estado, el gesto no se corta.

### Precio: el verificador (F9) en el celular

```
┌──────────────────────────────────────────┐
│ ╭──────────────────────────────────────╮ │
│ │ [foto]  Cuaderno profesional cuadro  │ │
│ │         chico 100 hojas              │ │
│ │         Escolar · 7509990100019      │ │
│ │         $46.00                       │ │  el precio en grande
│ │ (Desde 12: $41.00 c/u) (Exist.: 8)   │ │  mayoreo y existencia
│ │ (Precio) (Ticket) (Conteo)    [ + ]  │ │  ↑ al ticket · ↖ enseñar al cliente · ← buscar
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

- **Escanear solo enseña.** Nada entra al ticket, como el F9 de la caja; el ámbar es el mismo.
- **Lo que sale:** la foto (la de 240 px que guarda el POS, o las iniciales), el nombre, el departamento y el código, el
  precio en grande, la promoción de hoy, **el mayoreo** («Desde 12: $41.00 c/u») y la existencia («Quedan 1» en ámbar
  cuando queda poco). El precio es el de lo escaneado: el código de la caja de 12 da el de la caja y, al lado, el de
  cada pieza. Sin redondeo: el redondeo al peso solo se aplica al cobrar en efectivo.
- **El mayoreo** no lo enseña todavía el F9 de la caja, aunque su propuesta lo pide; en el piso es lo primero que se
  pregunta después del precio («¿y si llevo 12?»).
- **↖ «Enseñar al cliente»** pone el nombre y el precio a pantalla completa, para voltear el celular. Se toca para
  regresar.
- **↑ «Al ticket»** agrega uno sin salir de Precio; el aviso dice cuántos lleva el ticket y ofrece verlo. En el celular
  de la tienda no está: ahí no hay tickets.
- **Lo que no está en el catálogo** lo dice en rojo, con el código, y ofrece buscar por nombre.
- **Cada consulta cuenta** para «lo más preguntado» de Productos, como en la caja (`POST /api/consultas`).

### Ticket: la preventa

- **Cada lectura suma uno.** El mismo código suma a su renglón y lo sube; el último va arriba, resaltado, con − y +
  (en 1, el − es el bote; quitar se puede deshacer). Tocar la cantidad abre el teclado de números: «20» copias. Es lo
  que ya hace el modo venta de hoy, con nombres y precios.
- **El renglón dice lo que importa:** el precio por pieza, «Mayoreo» cuando llega a la escala (el total ya va con el
  precio de mayoreo, como en la caja) y «Quedan 1».
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
- **Las que dicen «Revisar hojas»** no se pueden elegir: se resuelven en la caja. Tampoco se marcan «No vendida» ni
  «Cobrar lo que salió» desde el celular.
- **Filtrar por PC** con las fichas de arriba, para cuando son muchas.
- **En el ticket** van en un renglón, «Impresiones · 3 trabajos», con sus miniaturas chicas y la suma. Al cobrar, la caja
  arma sus líneas «Impresiones» y «Copias» como siempre, con el precio de cada trabajo.
- **Sin red no se ven:** viven en el servicio (y en Control Print), y la caja tampoco cobra impresiones sin conexión. Se
  agregan en la caja con F2.
- **Que dos no tomen la misma:** hoy nada aparta una impresión elegida. Si el celular la pone en un ticket y otra caja la
  cobra antes, al cobrar el segundo la caja avisa y hay que quitarla. Se propone apartarla mientras está en un ticket
  del celular (F2 diría «en el ticket de Ana») y cerrar un hueco del cobro: el POS revisa que una impresión suya siga
  por cobrar *antes* de tomar el candado de la base, así que dos cobros al mismo tiempo podrían cobrarla dos veces. En
  el piloto no pasa (solo cobra la caja 2), pero con dos cajas sí.

#### Mandar a una caja

- **↑ «Mandar a Caja 2»** (el botón rápido hacia arriba) abre «¿A qué caja?»: cada caja con quién atiende y cuántos
  tickets esperan; la apagada no se puede elegir. La última usada ya viene elegida, así que casi siempre es tocar
  «Mandar»: el gesto nunca manda solo.
- **Llega en espera**, con quien lo mandó en lugar de la hora («Ana · $97.50»), entra con su efecto y no interrumpe el
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
  - **Falta probarlo en la tienda:** que el lector de la caja 2 lea QR y mande cada renglón con su Enter. El decodificador
    del puerto ya separa por renglón y acepta hasta 64 caracteres por código; cada renglón usa unos 16.

### Conteo

Contar tiene su propia sección, más abajo: [contar](#contar). En corto: a ciegas, la pieza suma una y la caja suma la
caja, el − quita la última lectura, el conteo completo guarda solo lo de cada lugar y el del día se aplica con «Es
todo». En el piloto no se cuenta.

## Contar

> **Propuesta completa, sin implementar todavía.** El dueño pidió verla (2026-11-03): «quiero ver la propuesta para
> contar aunque aún no se vaya a implementar». Va en la [entrega 6](#entregas), después del piloto. En el
> [prototipo](propuesta-interfaz/prototipo.html) se ve con «POS: Sin eleventa», o en el piloto con «Ver cómo se
> contará».

Lo contado cambia la existencia y no se deshace con un toque: es lo que menos perdona de la app. Por eso la app no
cambia ninguna regla del POS. Le pone la cámara, las cajas y sueltas, el guardado solo, la bandeja de salida sin red y
una sola mano.

### Lo que ya hace el POS

Leído en su código (`ConteoDepartamento`, `ConteoCiclico`, `RegistroConteoCompleto`, `RegistroConteoCiclico`,
`TrastiendaEnElPiloto`) y en sus pantallas (`ConteoCompleto`, `ContarDepartamento`, `RevisarConteo`, `ConteoDelDia`):

| | Conteo del día | Conteo completo |
|---|---|---|
| **Qué** | 15 productos al día que elige el POS: primero lo que tiene existencia negativa, luego lo que más vale y se vende, por los días que lleva sin contarse | Un departamento entero. Puede haber varios abiertos, uno de cada uno |
| **Quién lo abre** | Nadie: el POS lo arma la primera vez que se pide en el día | Quien tiene «Conteo completo de un departamento» (de inicio, el encargado y el dueño) |
| **Quién cuenta** | Quien tiene «Contar inventario» (de inicio, todos) | Igual |
| **Qué se escribe** | El total de la tienda: anaquel, mostrador y bodega | Lo de un lugar. Cada captura se suma a las de todos |
| **Cuándo entra** | Al guardar: la diferencia entra al kardex en ese momento | Al cerrar: lo contado reemplaza la existencia y lo que nadie contó queda en 0 |
| **Si quedó mal** | No se corrige: una vez por producto al día. Es un ajuste en la caja | Se quita la captura (cada quien las suyas; el encargado, cualquiera) y se cuenta otra vez |
| **A ciegas** | Antes de contar nadie ve el sistema; ya contado, quien ve costos ve la diferencia | Nadie lo ve. El encargado, solo al revisar |
| **Dónde, hoy** | Solo en la caja: F4 y «Conteo del día: faltan N» al abrir el turno | La caja y la página `/conteo` en el navegador del celular |
| **En el piloto** | No hay | No se abre, no se captura ni se cierra. Lo abierto se cancela |

- **El completo ya evita duplicados:** cada captura lleva un número que pone quien captura; si llega dos veces, cuenta
  una. El del día no lo tiene.
- **Se vende mientras se cuenta:** cada producto se compara contra lo que decía el sistema en su primera captura. Lo que
  se vende o llega después sigue moviendo la existencia.
- **`/conteo`, hoy:** usuario y PIN escritos; se busca por nombre o se lee con un lector (no hay cámara para códigos);
  una casilla por presentación; se pone al día cada 10 segundos; «Solo lo que falta» y «Lo que capturaste» con
  «Quitar». No tiene el conteo del día. Lo tecleado se pierde si se escanea otro producto antes de guardar.
- **Las capturas de otros no se quitan en ninguna pantalla:** el POS se lo permite al encargado, pero `/conteo` solo
  enseña las propias.

### Quién cuenta con el celular

No todos tendrán la app (dueño, 2026-11-03). Conteo aparece si la persona tiene «Contar inventario» y el POS deja contar.

| Celular | Quién | Modos | En Conteo |
|---|---|---|---|
| **De alguien de confianza** (la primera versión: el del dueño; en el prototipo, el de Luis) | Su dueño; entra con su PIN, ya viene elegido | Precio, Ticket y Conteo | Lo que dejen sus permisos. El dueño: el del día, contar, abrir departamentos y quitar capturas de otros |
| **De la tienda** (a futuro) | Varios, por turnos, cada quien con su ficha y su PIN | Solo Precio y Conteo, aunque la persona pueda más | Contar el del día y los departamentos abiertos. Abrir uno, solo quien tenga el permiso |
| **Los demás** | Su propio celular, sin la app | — | `/conteo` en el navegador |

- **Qué modos tiene el celular de la tienda lo decide el dueño** al darlo de alta (Configuración › Celulares), no quien
  lo usa.
- **Por turnos:**
  - «Cambiar de persona» guarda lo que se estaba contando a nombre de quien sale. Lo del día sin aplicar, lo aplica o lo
    descarta quien sale.
  - Cada captura sube a nombre de quien la contó, aunque suba después de que salió.
  - Lo contado enseña solo lo de quien está.

### La pantalla

```
┌──────────────────────────────────────────┐
│ (L)                                [L]   │  quién entró · la linterna
│     [ ||| Apunta al código de barras ]   │
│ ┌──────────────────────────────────────┐ │  ← 32 %
│ │  ──────────────────────────────────  │ │  la franja: nada la tapa
│ └──────────────────────────────────────┘ │  ← 68 %
│ ╭───────────────── ══ ─────────────────╮ │
│ │ Escolar · este lugar     37 de 120 › │ │  qué se cuenta y cómo va; se toca: lo contado
│ │ ▬▬▬▬▬▬▬▬▬▬────────────────────────── │ │
│ │ Lápiz HB No. 2         ┌───┐┌──────┐ │ │
│ │ 1 caja + 1 suelta      │ − ││ × 13 │ │ │  − quita la última lectura · el contador: se toca para teclear
│ │ Ya van 24: Ana 24  +12 └───┘└──────┘ │ │  lo que contaron otros · lo que sumó la lectura
│ │ (Precio) (Ticket) (Conteo)    [ + ]  │ │  ↑ guardar · ↖ lo contado · ← buscar
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

- **Arriba, qué se cuenta y cómo va:** «Escolar · este lugar» o «Del día · todo lo que hay», y cuántos van («37 de
  120»). Se toca, o se jala el asidero, para ver lo contado. Cada conteo lleva su icono y su frase: sus reglas no son las
  mismas.
- **El contador grande** (48 sp en el Pixel, 38 en el chico, en violeta): el total del producto, en piezas, metros o
  kilos. Se toca para teclear. Junto a él, lo que sumó la última lectura: «+1» o «+12 caja».
- **El −** quita la última lectura; si fue la caja, la caja («−12»). Así se deshace una lectura de más sin pensar cuál
  fue.
- **Debajo del nombre:** cuántas cajas y sueltas son y, en el completo, lo que ya contaron otros de ese producto («Ya van
  24: Ana 24»). Nunca lo que dice el sistema.
- **Cabe en el chico:** sin fila de botones, bajo la franja quedan unos 180 dp para el encabezado, el producto y el
  contador (unos 260 en el Pixel).
- **Muchas iguales: escanear una y teclear.** Pasar 50 lápices uno por uno tarda; escanear uno y teclear «4 cajas y 2»
  es más rápido y más seguro.

### El botón rápido en Conteo

Las mismas direcciones que en los otros modos ([el botón rápido](#el-botón-rápido)):

| | Conteo del día | Conteo completo |
|---|---|---|
| **↑** | **Es todo · 37:** pide confirmar y lo aplica | **Guardar 13:** guarda este lugar |
| **↖** | **Lo contado:** lo mío y lo que falta | Igual |
| **←** | **Buscar** lo que no tiene código | Igual |

- **↑ en el del día pide confirmar,** como ↑ en Ticket abre «¿A qué caja?»: aplicar no se deshace, y un gesto sin mirar
  no debe hacer lo que no se corrige. En el completo guarda directo: una captura se quita.
- **«Guardar» casi no hace falta:** escanear otro producto guarda el anterior. Es para el último de un lugar.
- **«Contar otra cosa»** (del día a un departamento, o de un departamento a otro) va en lo contado: es de una vez por
  rato.

```
┌──────────────────────────────────────────┐
│ ╭──────────────────────────────────────╮ │
│ │ Lápiz HB No. 2: 37 en total          │ │
│ │ Aquí 13 · antes 24                   │ │
│ │ Se aplica ya. Si quedó mal, es un    │ │
│ │ ajuste en la caja.                   │ │
│ │ [✓ Aplicar 37                      ] │ │  a la altura del pulgar
│ │ [  Seguir contando                 ] │ │
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

### El conteo del día

```
┌──────────────────────────────────────────┐
│ ╭───────────────── ══ ─────────────────╮ │
│ │ Del día · todo lo que hay  3 de 15 › │ │
│ │ Lápiz HB No. 2         ┌───┐┌──────┐ │ │
│ │ Aquí 13 · antes 24     │ − ││ × 37 │ │ │  el total de todos los lugares
│ │ 1 sin aplicar: Goma    └───┘└──────┘ │ │  lo contado que falta aplicar
│ │ (Precio) (Ticket) (Conteo)    [ + ]  │ │  ↑ es todo · ↖ lo contado · ← buscar
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

1. **Sin elegir:** si no hay departamentos abiertos (lo de todos los días, después del arranque), Conteo es el conteo del
   día.
2. **Escanear uno de los 15:** empieza su contador. Lo contado › «Falta» dice cuáles faltan y de qué departamento son.
3. **Todo lo que hay:** se sigue escaneando o tecleando en el mostrador y en la bodega. El contador lleva el total
   («aquí 13 · antes 24»).
4. **Escanear otro de los 15 no aplica el anterior:** queda «sin aplicar» y, al volver a escanearlo, sigue donde iba.
5. **↑ «Es todo · 37»** pide confirmar y lo aplica. Quien ve los costos (de inicio, el dueño y el encargado) ve después lo
   que tenía el sistema y la diferencia, como en la caja.
6. **Lo que no se encuentra:** en lo contado › «Falta» se toca y se aplica 0: «no hay».
7. **Lo que no va:** uno que no está en los 15 de hoy, o uno que ya contó alguien («Ya lo contó Carmen a las 11:02»).
8. **Lo sin aplicar se recuerda:** en el encabezado, al cambiar de persona y con «Terminé mi parte». Lo que quedó de otro
   día se descarta con aviso: el conteo cambia cada día.

- **Por qué no se aplica solo:** el del día es el total de la tienda, de una vez. Si se aplicara al escanear el
  siguiente, lo de la bodega ya no se podría sumar.
- **Hoy se escribe en la caja:** se cuenta en el anaquel y se teclea en F4. Con el celular se cuenta y se aplica ahí
  mismo; el «faltan N» de la caja baja al momento.

### El conteo completo

```
┌──────────────────────────────────────────┐
│ ╭──────────────────────────────────────╮ │
│ │ ¿Qué vas a contar?                   │ │
│ │ [R] Conteo del día         3 de 15 › │ │  el total de cada uno, una vez
│ │ [C] Escolar              37 de 120 › │ │
│ │     ▬▬▬▬▬▬──────────── Luis, Ana     │ │  quién cuenta, en la app o en /conteo
│ │ [C] Oficina              12 de 210 › │ │
│ │     ▬───────────────── Carmen        │ │
│ │ [C] Papel                  0 de 95 › │ │
│ │     ────────────────── nadie         │ │
│ │ [+] Abrir otro departamento        › │ │  solo con «Conteo completo»
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

`[R]` el del día · `[C]` un departamento.

1. **Elegir el departamento:** cada uno dice cuántos van y quién cuenta. Quien tiene «Conteo completo» abre otro.
2. **Contar:** la pieza suma una, la caja suma la caja. Lo que no tiene código: ← Buscar, o la hoja de códigos del
   mostrador (se escanea una vez y se teclea cuántas).
3. **Se guarda solo:** al escanear otro producto, lo del anterior queda como una captura de este lugar («Guardado: 13 de
   Lápiz HB · Quitar», unos segundos). Lo que bajó a 0 con el − no se guarda; un 0 tecleado sí: «no hay».
4. **El mismo producto en otro lugar** es otra captura: se escanea al llegar ahí y se suma.
5. **Lo que ya contaron otros:** «Ya van 24: Ana 24». Se toca: las capturas de ese producto, con quién y a qué hora. Las
   propias se quitan; las de otros, quien tiene «Conteo completo».
6. **Lo de otro departamento:** «Es de Oficina». Si Oficina también se está contando, «Contarlo para Oficina» lo cuenta
   ahí, de paso, hasta escanear otro producto; luego se queda donde está hasta que cierren Oficina (si se lleva a su
   lugar, se contaría dos veces). Si Oficina no está abierto, se lleva a su lugar. En el arranque hay cosas fuera de
   lugar y, si nadie las cuenta, quedan en 0.
7. **Lo que no está en el catálogo** o no lleva inventario: en rojo, con la vibración de error. Se aparta para el
   encargado.
8. **↖ Lo contado:** «Lo mío», con la hora, si ya subió y «Quitar» (se corrige quitando y contando otra vez, como en el
   POS); «Falta», lo que nadie ha contado, por nombre. Tocar uno empieza a contarlo: para lo que no tiene código o para
   decir «no hay».
9. **«Terminé mi parte»** guarda lo que se contaba, dice cuánto contaste y si queda algo por subir, y regresa a «¿Qué vas
   a contar?». Se le dice al encargado.

```
┌──────────────────────────────────────────┐
│ <  Escolar · lo que contaste             │
│    12 capturas · 2 por subir             │
│ Lápiz HB No. 2                      13   │
│ 14:52 · por subir · 1 caja + 1     [x]   │
│ ──────────────────────────────────────── │
│ Cuaderno profesional cuadro…         3   │
│ 14:50 · subida                     [x]   │
│ ──────────────────────────────────────── │
│ Goma blanca chica                    0   │
│ 14:47 · subida · no hay            [x]   │
│                                          │
│ ( Lo mío 12 )  ( Falta 83 )              │  abajo, junto al pulgar
│ [Terminé mi parte] [Contar otra cosa ]   │
└──────────────────────────────────────────┘
```

`[x]` quitar (el bote).

### Cajas y sueltas: el teclado

```
┌──────────────────────────────────────────┐
│ x  Lápiz HB No. 2                        │
│    Lo que hay en este lugar              │
│ ┌──────────────────────────────────────┐ │
│ │▌Cajas de 12                        3 │ │  la casilla que se teclea
│ └──────────────────────────────────────┘ │
│ ┌──────────────────────────────────────┐ │
│ │ Sueltas                            1 │ │
│ └──────────────────────────────────────┘ │
│ Total                         37 piezas  │
│    [  1  ]     [  2  ]     [  3  ]       │
│    [  4  ]     [  5  ]     [  6  ]       │
│    [  7  ]     [  8  ]     [  9  ]       │
│    [  .  ]     [  0  ]     [ <-  ]       │  el punto, solo en metros, kilos o litros
│ [✓ Guardar 37                         ]  │
└──────────────────────────────────────────┘
```

- **Se abre tocando el contador.** Una casilla por presentación, de la más grande a la suelta, como en `/conteo`.
  Arrancan con lo que ya se leyó.
- **El total, al momento.** El teclado abajo, junto al pulgar; las casillas encima.
- **Por metro, kilo o litro,** la suelta acepta el punto («4.5»). Un rollo con su código suma su largo.
- **«Guardar 0» se puede:** dice «no hay».
- **En el del día,** «Otro lugar» guarda lo de aquí y deja las casillas en cero para el siguiente; el total suma todos.
- **Mientras está abierto, las lecturas no cuentan.** Se cierra con el gesto de atrás o deslizándolo hacia abajo: la «x»
  de arriba no hace falta con una mano.
- **Cabe en el chico** sin desplazarse, también con tres presentaciones (caja, paquete y suelta).

### A ciegas

- Ninguna pantalla de la app enseña lo que dice el sistema de lo que se está contando.
- **Tampoco a un toque:** la existencia de lo que se está contando no sale en Precio ni en el «Quedan» del ticket
  («Existencia: al contarlo»): los 15 del día hasta que se aplican y los del departamento elegido mientras se cuenta. En
  la caja el F9 sí la enseña; en el celular, que está en la mano mientras se cuenta, sería la tentación.
- **Lo que contaron otros sí se ve:** lo hace `/conteo` y evita contar dos veces el mismo anaquel.

### Sin red: la bandeja de salida

La red se cae y en la bodega el wifi llega mal. Se sigue contando:

| Estado | Quiere decir |
|---|---|
| Contando | En el celular, sin guardar. No se pierde si se cierra la app o se apaga la pantalla |
| Sin aplicar | Del día: contado, falta «Es todo» |
| Por subir | Guardado en el celular; sube solo al volver la red |
| Subida, aplicada | El POS ya la tiene |
| No entró | El POS no la aceptó (el departamento se cerró o se canceló, ya lo contó otro, cambió de departamento): dice por qué |

- **Cada captura lleva su número:** mandarla dos veces cuenta una. Lo que se quita va en orden, después de su captura;
  quitar una que todavía no sube la borra del celular: nunca llega.
- **Arriba,** «Sin red · 3 por subir»; al volver, «Subiendo 3».
- **«Ya van»** sale de lo último que se supo, con «hace 12 min». Sin red no se abre un departamento.
- **Lo contado sin red sube con el tiempo que hace que se contó** ([lo que pide](#lo-que-pide-al-pos-para-contar)): así
  lo vendido en ese rato no corre la diferencia.
- **Lo que no entró** se queda en lo contado, para decírselo al encargado.

### El encargado y la caja

| En el celular | En la caja (F4 › Conteo completo) |
|---|---|
| Abrir un departamento | También; ahí está el QR de `/conteo` |
| Contar, como todos | — |
| Ver cuántos van y quién cuenta | También |
| Quitar una captura equivocada, también de otros | — |
| — | **Revisar:** lo contado contra el sistema, lo que nadie contó y lo que vale la diferencia |
| — | **Cerrar** el departamento, o cancelarlo |

Revisar y cerrar se quedan en la caja:

- **Cerrar no se deshace:** reemplaza la existencia de todo el departamento y deja en 0 lo que nadie contó. Se decide con
  la tabla entera: cientos de renglones, «Con diferencia» y «Sin contar».
- **En el celular de quien cuenta rompería lo de a ciegas:** la revisión enseña lo que dice el sistema.
- **«Sin contar»** dice lo que nadie contó y el sistema dice que hay: antes de cerrar, el encargado lo manda a buscar.
- **En una urgencia,** `/conteo` deja revisar y cerrar desde el navegador a quien tiene el permiso.

### El conteo físico de arranque

El conteo completo de todos los departamentos da las existencias para dejar eleventa: «las de eleventa solo sirven de
referencia para el conteo; las que valen son las contadas» (plan del POS). Es el uso más grande: miles de productos y
varios contando a la vez.

**Cuándo.** El plan del POS lo pone en las semanas previas al arranque, pero en el piloto el POS no deja contar. Hay que
decidirlo:

| | Cómo | A favor | En contra |
|---|---|---|---|
| **A. Un fin de semana, ya sin eleventa** | Se deja eleventa, se abren todos los departamentos, cuentan todos a la vez y se cierra cada uno al terminar | Lo más exacto. No pide nada al POS | La tienda para uno o dos días; hace falta mucha gente a la vez |
| **B. En el piloto, y se cierra al dejar eleventa** | Un departamento a la vez en las semanas previas. El POS guarda lo contado contra lo que dice la lectura de eleventa y lo aplica al cerrar, ya sin eleventa | Es lo que dice el plan. La tienda sigue abierta | Pide al POS abrir y capturar en el piloto. Lo que vende la caja 1 llega con la lectura cada pocos minutos: lo vendido justo antes de contar se descuenta dos veces. Cada departamento se depura antes de contarlo |
| **C. Arrancar con lo de eleventa y contar después** (lo recomendado) | Se arranca con las existencias de la última lectura. En las semanas siguientes se cuenta un departamento a la vez, con el POS ya sin eleventa | No pide nada al POS. Cuenta contra sus propios movimientos. La tienda sigue abierta. El conteo del día ayuda desde el primer día | Unas semanas con las existencias de eleventa, como hoy. Cambia el orden del plan del POS |

**Cómo se reparte,** con cualquiera de las tres:

1. **El orden:** primero los departamentos que más valen (Inventario › lo que vale el inventario, por departamento).
2. **El encargado abre los del día,** desde la caja o desde la app: uno por persona.
3. **Cada quien toma el suyo** en «¿Qué vas a contar?»; ahí se ve quién está en cada uno.
4. **Cada quien recorre todos los lugares de su departamento:** anaquel, mostrador, exhibidor y bodega. Lo de otro
   departamento abierto lo cuenta para ese; si no está abierto, lo lleva a su lugar.
5. **Al terminar,** lo contado › «Falta»: lo que nadie ha contado. Lo que no aparezca, se deja.
6. **«Terminé mi parte»,** y se le dice al encargado.
7. **El encargado revisa en la caja:** lo que el sistema dice que hay y nadie contó, lo manda a buscar; luego cierra.

- **Un departamento por persona:** nadie cuenta dos veces el mismo anaquel. Si uno es muy grande, dos se lo reparten por
  lugares (uno el piso, otro la bodega); «ya van» avisa si alguien ya contó ese producto.
- **El avance:** «¿Qué vas a contar?» dice de cada departamento cuántos van de cuántos y quién cuenta. La caja, igual.
- **La app y `/conteo` a la vez:** son las mismas capturas; lo de uno se ve en el otro («ya van»). En `/conteo` no hay
  cámara para los códigos: se busca por nombre o se usa un lector.
- **Aquí sirve primero el celular de la tienda:** uno o dos con la app (Precio y Conteo) rinden más que `/conteo` en los
  celulares de cada quien.

### En el piloto

- El POS no deja contar y no hay conteo del día. **En la app, Conteo no aparece,** como la caja, que no ofrece la tarea.
  Tampoco el acceso directo «Contar».
- Con la opción B del arranque, aparece solo el conteo completo, con «Se aplica al dejar eleventa».
- En el prototipo sí está, con el aviso y «Ver cómo se contará», para revisarlo.

### Agregar inventario, recibir mercancía, ajustes y mermas

| Tarea | En el celular | Por qué |
|---|---|---|
| **Agregar inventario** (lo que llegó de un producto, sin proveedor; unas 7 al día) | No, por ahora | Se hace donde llega la mercancía, junto a la caja, de uno en uno. Mueve la existencia y el costo al momento y no tiene deshacer (se corrige con un ajuste). En el celular que cuenta se confundiría con contar, y lo agregado dos veces no se nota |
| **Recibir mercancía a ciegas** (una compra de un proveedor) | Después, como un conteo más | Es contar lo que llegó: escanear, a ciegas, cajas y sueltas. Hoy no se usa: los proveedores están vacíos y lo que llega entra con «Agregar inventario» |
| **Ajustes** | No | Cambian la existencia viéndola, con motivo; al cajero le piden autorización y desde $200 avisan al dueño. Lo que se cree mal se cuenta (si es del día o de un departamento abierto) o se ajusta en la caja |
| **Mermas** | No | Lo dañado se lleva a la caja; ahí se registra con su causa y, arriba de $200 para el cajero, con autorización |
| **Abrir un paquete** | No | Se hace al abrirlo, en la caja |

- El celular de la tienda es solo para contar y ver precios (dueño, 2026-11-03): ahí nada de esto.
- Las fotos de los productos, que hoy se toman en `/conteo` › Fotos, no son de contar: quedan para otra entrega.

### Lo que pide al POS para contar

Todo va sobre el punto 1 de [lo que pide al POS](#lo-que-pide-al-pos) (que el celular hable con la API con su llave) y
detalla sus puntos 8 y 9. El conteo del día (`GET /api/sucursales/{id}/conteo-del-dia`, `POST /api/conteos/{id}/contar`)
y el completo (`/api/conteos-completos/…`) ya están en la API; con la llave, la app los usa como están.

| # | Qué | Por qué | Para |
|---|---|---|---|
| C1 | **Capturar ligero:** que la captura conteste solo esa captura y el producto con sus capturas | Hoy cada captura regresa el departamento entero (sus productos, presentaciones, códigos y todas las capturas). Con cientos de productos y la bandeja subiendo de golpe, pesa | El completo |
| C2 | **Ponerse al día:** las capturas nuevas y las quitadas de un conteo desde la última vez, y si sigue abierto | «Ya van» sin bajar el departamento cada 10 segundos, como `/conteo`; y saber pronto si se cerró | El completo |
| C3 | **El número propio en el del día** (`SolicitudContar` con su `Id`, como `SolicitudCaptura`), y quitar dos veces la misma captura sin error | Si se pierde la respuesta, el celular lo vuelve a mandar. Hoy el segundo dice «Ya se contó hoy» y no se sabe si fue el mismo | Sin red |
| C4 | **Cuándo se contó:** cada captura y cada conteo del día llevan cuánto hace que se contaron, con el reloj interno del celular (no cambia si alguien mueve la hora). El POS compara contra lo que había en ese momento | Lo contado sin red sube después. Hoy se compara contra lo que hay al llegar: lo vendido en ese rato correría la diferencia | Sin red |
| C5 | **Quién cuenta con la app y qué le falta subir:** el latido del celular dice qué conteo tiene elegido y cuántas capturas tiene por subir; la revisión lo enseña antes de «Cerrar» («Ana · 3 por subir · hace 8 min») | Si se cierra con capturas en un celular sin red (en la bodega), no entran | El completo |
| C6 | **Que `/conteo` no pierda lo tecleado** al escanear otro producto: que lo guarde o que avise | La usarán quienes no tengan la app, sobre todo en el arranque | El arranque |
| C7 | **El arranque:** decidir cuándo (A, B o C, arriba). Con B: abrir y capturar en el piloto, y cerrar solo ya sin eleventa | Hoy el piloto no deja contar | El arranque |
| C8 | **Los modos del celular:** que su alta diga si es de la tienda (solo Precio y Conteo) | Lo decide el dueño, no quien lo usa | La tienda |

### Cómo se entregaría

La entrega 6, en partes y después del piloto:

| Parte | Qué trae | Necesita del POS |
|---|---|---|
| 6.1 | **El conteo completo:** elegir y abrir, contar, cajas y sueltas, se guarda solo, lo de otros, lo contado, la bandeja de salida | 1, C1, C2, C4, C5 |
| 6.2 | **El conteo del día:** el total por lugares, «Es todo», lo sin aplicar | C3 |
| 6.3 | **El celular de la tienda:** solo Precio y Conteo, por turnos | C8 |

Con la opción C, la 6.1 conviene tenerla el día del arranque; mientras, `/conteo` (con C6).

### Riesgos

- **Cerrar con capturas por subir:** un celular sin red en la bodega y el encargado cierra; esas no entran. El celular
  avisa «3 por subir» y, con C5, también la caja.
- **Lo contado sin red, sin C4:** si en ese rato se vendió, la diferencia sale corrida.
- **Una lectura de más:** el producto de al lado se lee y, en el completo, se guarda solo como captura de 1. El aviso
  «Guardado · Quitar» y lo contado la dejan quitar.
- **Sostener el producto 1.5 s lo cuenta otra vez** (lo pidió el dueño para el escáner). A ciegas, una pieza de más no se
  nota (pregunta abajo).
- **Dos en el mismo producto del día** (uno en la caja, otro en el celular): vale el primero; al otro se le dice quién y a
  qué hora, y lo suyo no se aplica.
- **El celular de la tienda sin cambiar de persona:** lo contado queda a nombre de otro.
- **«Falta» incluye lo que no hay** (existencia 0): quien cuenta no sabe cuáles; el encargado los separa al revisar.
- **La bodega:** poca luz y wifi débil. La linterna está arriba, lejos del pulgar en el Pixel: «Linterna automática» en
  Ajustes. La bandeja aguanta sin red.
- **Horas de cámara:** la batería. El escáner se pausa a los 20 s sin códigos y, un minuto después, apaga la cámara;
  para el arranque, cargadores a la mano.
- **Con la opción B:** lo vendido en la caja 1 justo antes de contar se descuenta dos veces; y depurar (juntar, cambiar
  la unidad) un producto ya contado deja sus capturas a medias: se depura antes de contar.

### Preguntas de Conteo

- [ ] ¿El conteo de arranque: A, B o C (lo recomendado)?
- [ ] ¿Quién más cuenta con la app en la primera versión, además del dueño: el encargado?
- [ ] ¿El celular de la tienda (uno o dos, con la app) para el conteo de arranque, o cada quien con `/conteo` en el suyo?
- [ ] ¿Un departamento por persona (lo propuesto), o varios en el mismo, repartidos por lugares?
- [ ] En Conteo, ¿sostener el mismo producto 1.5 s lo cuenta otra vez, como hoy en el escáner, o se apaga ahí? Lo
  propuesto: apagarlo en Conteo (el escáner ya tiene el ajuste, `msParaRepetirAVista`); a ciegas, una pieza de más no se
  nota.
- [ ] En el celular de la tienda, ¿pedir el PIN otra vez tras 10 minutos sin usarlo? Así lo contado queda a nombre de
  quien contó.
- [ ] ¿Agregar inventario, ajustes o mermas desde el celular, aunque sea solo el dueño? Lo propuesto: no por ahora.

## Entrar

### Dar de alta el celular, una vez

1. **En la caja:** Configuración › Celulares › «Dar de alta un celular». Lo pide quien ya entró en la caja con el permiso
   «Dar de alta cajas»; la caja enseña un QR con la dirección de la caja principal y un permiso de un solo uso que vence
   en 10 minutos.
2. **En el celular:** «Escanear el código de la caja». Lo lee el mismo escáner (el QR es una de sus simbologías): nadie
   teclea una dirección.
3. **El permiso de Android:** «Dispositivos cercanos». Android 17 lo pide a las apps que apuntan a su versión para hablar
   con otros equipos de la red local
   ([permiso de red local](https://developer.android.com/privacy-and-security/local-network-permission)). La app lo
   explica antes de que salga la ventana del sistema.
4. **El catálogo** baja en segundo plano (unos 4 MB con 6,000 productos, más unas 300 fotos de 15 KB); mientras, ya se
   puede entrar.
5. Antes de mandar su llave, el celular comprueba que habla con la caja principal de verdad (`/api/identidad`, con la
   llave pública del servicio), como ya lo hacen las cajas.

**El celular no es una caja.** Hoy la única forma de dar de alta un equipo es como caja, con su serie de folios, en la
lista de cajas y en el corte del día; además, con «La única caja» el alta desde otro equipo está cerrada. Se propone que
el POS tenga **celulares**: su llave, sin serie ni turno, en Configuración › Celulares (ahí también se les quita el
acceso). Mientras eso llega, el respaldo es lo que existe: la dirección (o «Buscar en la red») y el usuario y PIN de quien
puede dar de alta cajas.

### ¿Quién atiende?

- **Las fichas y el PIN de 4 cifras,** como en la caja: se toca la ficha y entra sola con la cuarta cifra. La API ya
  existe (`GET /api/cajas/{id}/usuarios` y `POST /api/cajas/{id}/sesion`). Cada quien usa su usuario, PIN y permisos.
- **La sesión dura el turno** (8 horas, como `/conteo`). «Cambiar de persona» está en el menú de la ficha.
- **La última persona viene elegida:** si el celular es de alguien, entrar es teclear el PIN.
- **Sin red no se entra:** los PIN no se guardan en el celular. Quien ya entró en el turno sigue trabajando.

## Del bolsillo al precio

- **El mosaico de Ajustes rápidos pasa a ser «Precio»:** abre el escáner sobre la pantalla de bloqueo, sin el PIN del
  celular, en modo Precio. Es el camino de apertura que ya se midió (la cámara enlaza antes que la interfaz).
- **Sobre el bloqueo solo se ve el precio:** nombre, precio, mayoreo y promoción; ni la existencia, ni el ticket, ni el
  conteo. «Abrir la app» pide desbloquear y sigue en Precio con ese producto.
- **Sale de la copia del catálogo,** así que no espera a la red: del bolsillo al precio en lo que tarda la lectura.
- **Los accesos directos del ícono:** «Revisar precio», «Armar ticket» y «Contar».

## Sin red

| | Sin red |
|---|---|
| Precio | Sí, de la copia del catálogo |
| Buscar | Sí, de la copia |
| Armar el ticket | Sí |
| Mandarlo | No: se enseña como QR |
| Impresiones | No: se agregan en la caja con F2 |
| Contar | Sí: lo contado sube solo al volver la red |
| Entrar | Solo quien ya entró en el turno |

- **La red tiene tres estados,** como en la caja: buena, lenta y caída, por lo que tarda el latido. En «lenta», mandar
  espera su turno sin trabar la pantalla.
- **La copia del catálogo** es la de las cajas, sin lo que un celular no debe cargar (ver abajo). Se pone al día sola con
  el aviso de la caja principal (el canal de versiones que ya usan las cajas) y pide solo lo que cambió.

## Ajustes y herramientas de medición

- **Lo del personal,** en Ajustes: vibración y bip (apagado de fábrica, como hoy), linterna automática, la caja principal
  a la que está conectado, el catálogo (cuántos productos y de cuándo) y el nombre del celular.
- **Las herramientas de medición** (sesión de prueba, banco, estrés y los ajustes del escáner: motor, lente, resolución,
  cuadros por segundo) se quedan, porque se sigue midiendo en la tienda, pero bajan a Ajustes › Herramientas de
  medición. Hoy son cuatro de las cinco pestañas de abajo.

## Colores, letras y tamaños

- **El tema de la app,** el de hoy: azul petróleo y grises, claro y oscuro según el celular, sin los colores dinámicos
  del sistema.
- **Un color por modo:** Precio en ámbar (el del verificador de la caja), Ticket en azul petróleo y Conteo en violeta. Va
  en la pestaña del modo, el botón principal y el renglón resaltado; nunca en la retícula, donde el verde de «leído» y
  el ámbar de «viendo» son del escáner.
- **Las letras del sistema** (Roboto) y cifras tabulares: el precio a 40 sp, el total a 24 sp, el contador a 48 sp.
- **56 dp** para lo que se toca, como hoy; el botón rápido mide 60 dp y sus opciones 48 dp, con su nombre al lado.

## Lo que cambia por dentro

- **Lo que no se toca:** `nucleo` y `escaner` (la cámara, los motores, la pantalla del escáner con su retícula, guía,
  confirmación, bip y vibración). La app usa lo que ya expone: `PantallaEscaner` con lo de encima y las lecturas de
  `ControladorEscaner.eventos`. La regla de búsqueda en el catálogo del núcleo (las variantes de UPC y EAN) se usa tal
  cual sobre la copia del catálogo. Si un día hiciera falta cambiar algo del escáner, va en su propio PR y se mide contra
  la línea base, como siempre.
- **Un módulo nuevo, `pos`:** el cliente de la API, la copia del catálogo (Room, que también es SQLite), la búsqueda,
  y una bandeja de salida para los tickets y las capturas, cada una con su número para que mandarla dos veces cuente una.
- **`app`:** las pantallas nuevas sobre la de hoy; las de medición bajan a Ajustes.
- **El contrato:** el POS todavía no publica OpenAPI. Mientras, los DTO se escriben a mano en Kotlin (camelCase, los
  enums como números, ignorando campos nuevos), con ejemplos de JSON sacados de las pruebas del POS. Cuando el POS
  publique OpenAPI con sus respuestas tipadas, el cliente se genera de ahí.
- **La red y los permisos.** Hoy la app no puede usar la red y cada APK lo revisa. Pasa a **solo la red de la tienda**:
  - Pide internet, el estado de la red y «Dispositivos cercanos» (al apuntar a Android 17), y nada más. La revisión de
    cada APK cambia a esa lista, y sigue sin el envío de estadísticas de ML Kit.
  - El cliente solo se conecta a la caja principal dada de alta, y solo a direcciones privadas (10.x, 172.16–31.x,
    192.168.x). No manda nada a internet, ni estadísticas ni fotos de la cámara.
  - El servicio habla HTTP, sin cifrar (la llave y los PIN viajan así, como con las cajas), así que Android debe permitir
    HTTP a esa dirección. El POS ya pide que cajas y celulares no compartan la red con el público.
- **Versiones:** `0.2.0` con la primera entrega de esta propuesta; `1.0.0` cuando se use en la tienda con el POS, como
  dice el plan.

## Lo que pide al POS

Nada de esto existe hoy en `papeleria-pos`; cada punto dice qué entrega de aquí lo necesita.

| # | Qué | Por qué | Para |
|---|---|---|---|
| 1 | **Celulares:** alta con un QR de un solo uso desde la caja, llave propia, sin serie de folios ni turno, y Configuración › Celulares | Un celular dado de alta como caja tiene serie, sale en el corte del día y en la red de cajas; y con «La única caja» no se puede dar de alta | Conectar |
| 2 | **La copia del catálogo para el celular:** la de las cajas (`/api/cajas/{id}/paquete` y `/cambios`) sin usuarios, PIN, clientes ni reglas | La de hoy trae el PIN cifrado de cada usuario y los clientes; en un celular que se puede perder, no | Conectar |
| 3 | **La regla de los códigos** (las variantes de UPC y EAN) en el servicio y en la copia de las cajas | Se decidió el 2026-10-11 y está en el núcleo de esta app, pero el POS todavía busca el código tal cual | Conectar |
| 4 | **Mandar un ticket a una caja:** recibirlo, avisarle a esa caja por su canal de avisos (con acuse) y que entre en espera con quien lo mandó; el permiso «Mandar tickets» | Es la entrega 12 de la propuesta del POS; los tickets en espera hoy viven solo dentro de cada caja | Ticket |
| 5 | **Las cajas para el celular:** quién atiende, cuántos tickets esperan y si está prendida | Hoy la caja no le dice al servicio quién atiende ni cuántos esperan | Ticket |
| 6 | **Impresiones:** apartarlas mientras están en un ticket del celular, y revisar «por cobrar» dentro del candado del cobro | Que dos no tomen la misma; y que dos cobros simultáneos no cobren dos veces una impresión del POS | Impresiones |
| 7 | **El QR del ticket en la caja:** probar en la tienda que el lector lea QR y mande cada renglón con Enter | Si no, la caja tendría que entender el QR | Ticket |
| 8 | **Conteo:** que capturar no regrese el departamento entero; el número propio también en el conteo del día; la lista del día disponible para el celular | Cada captura hoy regresa todos los productos del departamento; el conteo del día no tiene cómo evitar duplicados | Conteo |
| 9 | **El conteo de arranque:** decidir cómo se cuenta en las semanas antes de dejar eleventa | Mientras el POS lee eleventa, no deja contar | Conteo |
| 10 | **El mayoreo en el F9 de la caja** | Su propia propuesta lo pide; que el celular y la caja enseñen lo mismo | — |

## Entregas

Cada entrega se prueba en el emulador y en el Pixel, y la de la cámara no cambia. Las primeras sirven ya en el piloto.

| # | Entrega | Qué trae | Necesita del POS |
|---|---|---|---|
| 1 | **La base** | Los tres modos sobre el escáner de hoy, el panel, la búsqueda y el ticket con el catálogo de prueba (sin red todavía), el tema, Ajustes con las herramientas de medición, el visor del mismo tamaño | Nada |
| 2 | **Conectar y Precio** | El alta (con lo de hoy mientras llega el 1), las fichas y el PIN, el precio y la búsqueda con la API de hoy; la copia del catálogo y sin red cuando el POS la sirva | 1, 2, 3 |
| 3 | **Del bolsillo al precio** | El mosaico «Precio» sobre el bloqueo y los accesos directos | — |
| 4 | **Mandar el ticket** | «¿A qué caja?», mandarlo, la confirmación y el QR sin red | 4, 5, 7 |
| 5 | **Impresiones** | La hoja de impresiones con miniaturas y lotes, el renglón en el ticket | 6 |
| 6 | **Conteo** | El conteo del día y el completo, con la bandeja de salida | 8, 9 (después del piloto) |

## Preguntas para el dueño

- [ ] ¿Quién arma tickets en el piso: cualquiera que atienda o solo el encargado? (también abierta en el POS)
- [ ] ¿Los celulares son de cada quien o de la tienda? Si son de cada quien, la última persona viene elegida y entrar es
  solo el PIN.
- [ ] ¿El precio sobre el bloqueo, sin el PIN del celular? Solo enseña lo que el cliente ve en la tienda.
- [ ] ¿La copia del catálogo en el celular (lo recomendado: precio al instante y sin red) o solo con red?
- [ ] ¿Los celulares como «celulares» en el POS (lo recomendado) o como una caja más?
- [ ] ¿Apartar las impresiones que se agregan en el celular?
- [ ] ¿El conteo físico de arranque se hace con los celulares? ¿Y cómo, si el piloto sigue leyendo eleventa?
- [ ] ¿Las herramientas de medición las ve todo el personal o solo quien tiene Configuración?
- [ ] ¿La app abre en el último modo usado (lo propuesto) o siempre en Precio?
