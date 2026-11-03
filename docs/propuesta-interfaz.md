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
- Se diseñó y se probó a **360 × 740 dp** (un celular chico) y a **448 × 997 dp** (el Pixel 8 Pro). En los angostos, los
  botones de al lado se quedan con su icono.

## Lo que necesita el piso, de lo más a lo menos seguido

Las frecuencias son estimadas; se confirman en la tienda.

| # | Necesidad | Qué tan seguido | Cómo queda |
|---|---|---|---|
| 1 | Contestar «¿cuánto cuesta?» | Muy seguido | **Precio**: escanear y ver precio, mayoreo, promoción y existencia; «Enseñar al cliente» lo pone en grande |
| 2 | Pasar lo que lleva el cliente | En cada preventa | **Ticket**: cada código suma uno; lo último arriba, con − y + |
| 3 | Lo que no se escanea | Seguido | La lupa: dos pedazos de palabra, con el campo junto al pulgar |
| 4 | Las impresiones que trae en la mano | Muchas veces al día | «Impresiones»: las miniaturas en lotes; se tocan las que trae |
| 5 | Mandarlo a la caja | Al terminar cada ticket | «A Caja 2»: la última caja usada ya viene elegida |
| 6 | Sin red | Unas veces al año, por horas | El ticket como QR para el lector de la caja |
| 7 | Contar | El conteo del día; el completo, al arrancar y una vez al año | **Conteo**: a ciegas, cajas y sueltas, se guarda solo |
| 8 | Cambiar de persona | Al cambiar de turno | La ficha y el PIN, como en la caja |
| 9 | Dar de alta el celular | Una vez | Escanear el código que enseña la caja |

## La pantalla principal: el escáner con tres modos

```
┌──────────────────────────────────────────┐
│ (A) · Sin red                      [L]   │  quién entró (su menú) · la red, solo si falla · la linterna
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
│ │ [Q] [Impresiones 6] [>  A Caja 2   ] │ │
│ │   Precio    ( Ticket )    Conteo     │ │  los modos
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

`[Q]` buscar · `[L]` la linterna · `[x]` quitar (el bote) · `[>]` mandar · `[<>]` enseñar al cliente.

- **Arriba a la izquierda,** la ficha de quien entró: abre «Este celular» (cambiar de persona, ajustes, la red, salir). A
  su lado, el estado de la red **solo si algo falla** («Red lenta», «Sin red · 3 por subir»), como los chips de la caja.
- **Arriba a la derecha,** la linterna del escáner, como hoy.
- **Abajo, el panel del modo** y la barra de modos: **Precio · Ticket · Conteo**. Se cambia tocando el nombre; deslizar
  sobre la cámara no cambia de modo, para no cambiarlo sin querer al apuntar. Si hay un ticket armado, «Ticket» lleva
  cuántos artículos trae.
- **El asidero** (o tocar el encabezado) abre el modo completo: el ticket entero o lo contado. Mientras está abierto, las
  lecturas no cuentan.
- **La app abre en el último modo usado.** El mosaico y los accesos directos abren uno en particular
  ([del bolsillo al precio](#del-bolsillo-al-precio)).
- **El panel cabe en el espacio bajo la franja** (unos 280 dp en un celular chico y 360 en el Pixel): el encabezado, lo
  último leído, los botones y los modos. Lo demás se ve al jalar.

### Precio: el verificador (F9) en el celular

```
┌──────────────────────────────────────────┐
│ ╭──────────────────────────────────────╮ │
│ │ [foto]  Cuaderno profesional cuadro  │ │
│ │         chico 100 hojas              │ │
│ │         Escolar · 7509990100019      │ │
│ │         $46.00                       │ │  el precio en grande
│ │ (Desde 12: $41.00 c/u) (Exist.: 8)   │ │  mayoreo y existencia
│ │ [Q] [<>] [+ Al ticket (3)          ] │ │  buscar · enseñar al cliente · agregar
│ │  ( Precio )    Ticket     Conteo     │ │
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
- **«Enseñar al cliente»** pone el nombre y el precio a pantalla completa, para voltear el celular. Se toca para
  regresar.
- **«Al ticket»** agrega uno sin salir de Precio; el aviso dice cuántos lleva el ticket y ofrece verlo.
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
- **El ticket completo** (jalar el asidero): todos los renglones con − y +, el renglón de impresiones, «Buscar»,
  «Impresiones», deshacer, el total y «Mandar a Caja 2». «Empezar otro ticket» lo vacía (se puede deshacer).
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

- **«A Caja 2»** abre «¿A qué caja?»: cada caja con quién atiende y cuántos tickets esperan; la apagada no se puede
  elegir. La última usada ya viene elegida, así que casi siempre es tocar «Mandar».
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

```
┌──────────────────────────────────────────┐
│ ╭──────────────────────────────────────╮ │
│ │ Escolar · conteo completo  37 de 120 │ │  se toca: lo que contaste
│ │ ▬▬▬▬▬▬▬▬▬▬────────────────────────── │ │
│ │ Lápiz HB No. 2              ┌──────┐ │ │
│ │ 1 caja de 12 + 1 suelta     │ × 13 │ │ │  el contador: se toca para teclear
│ │ ya van 24 en otros lugares  └──────┘ │ │
│ │ [- 1] [Q] [✓ Guardar 13            ] │ │
│ │   Precio     Ticket    ( Conteo )    │ │
│ ╰──────────────────────────────────────╯ │
└──────────────────────────────────────────┘
```

- **En el piloto no se cuenta.** Mientras el POS lee el inventario de eleventa, no deja abrir, capturar ni cerrar un
  conteo (`ApiConteos`): el modo lo dice y no ofrece nada. Al dejar eleventa, el conteo físico de arranque es justo
  para esto.
- **¿Qué vas a contar?** El **conteo del día** (los 15 que elige el POS cada mañana; hoy solo se hace en la caja), los
  **departamentos abiertos** del conteo completo (cuenta quien tiene «Contar inventario») y, para quien tiene «Conteo
  completo», **«Abrir otro departamento»**. Revisar las diferencias y cerrar se queda en la caja (Inventario, F4).
- **A ciegas:** nunca se ve cuántas debería haber.
- **La pieza suma una y la caja suma la caja:** el código de la pieza suma una suelta y el de la caja de 12 suma 12, como
  hoy en `/conteo`. El contador grande dice el total y, abajo, «1 caja de 12 + 1 suelta».
- **Teclear:** tocar el contador abre una casilla por presentación («Cajas de 12», «Sueltas») con el total al momento, y
  «Guardar». Para lo que está en cajas cerradas.
- **Lo de este lugar:** cada captura es lo que hay en un lugar y se suma a lo de los demás. En el conteo completo dice lo
  que ya contaron otros de ese producto («ya van 24 en otros lugares: Ana 24»), sin decir lo que dice el sistema.
- **Se guarda solo:** al escanear otro producto, lo contado del anterior queda guardado («Guardado: 13 de Lápiz HB»);
  «Guardar» lo hace a mano. Así se corrige lo que hoy pasa en `/conteo`, donde lo tecleado se pierde si se escanea otro
  producto antes de guardar.
- **Lo que no va:** un producto de otro departamento («es de Oficina: se cuenta con ese departamento»), uno que no está
  en el conteo del día o que ya se contó hoy. Lo dice en rojo con su vibración de error: la confirmación verde es del
  escáner, que sí lo leyó; lo rechaza la app.
- **Lo contado** (tocar el título): mis capturas, con la hora, si ya subieron y «Quitar» (las mías). En el conteo del
  día, lo que falta; ahí no se quita: se aplica al guardar, una vez al día, y un error se corrige con un ajuste en la caja.
- **Sin red,** las capturas quedan «por subir» y suben solas al volver la red, sin duplicarse (cada una lleva su número,
  que el POS ya respeta en el conteo completo).

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
- **56 dp** para lo que se toca, como hoy; en los celulares de 360 dp los botones de al lado quedan solo con su icono y
  su número.

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
