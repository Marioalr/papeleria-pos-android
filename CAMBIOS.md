# Cambios

Qué trae cada versión de la app ([versiones](docs/plan.md#versiones)). La más nueva va arriba.

## 0.3.0 (sin publicar)

La tercera entrega de la [propuesta de interfaz](docs/propuesta-interfaz.md#del-bolsillo-al-precio), «Del bolsillo al
precio»: el verificador sobre el bloqueo y los accesos del ícono. No necesita nada del POS.

- **El mosaico de Ajustes rápidos ahora es «Precio»,** con la etiqueta de precio del modo Precio por ícono (antes, un
  código de barras). Quien ya lo tenía lo conserva: también en Android 7 a 9, donde la etiqueta nueva no llegaba sola.
- **El escaneo rápido queda fijo en Precio:** aunque la app esté en Ticket o en Conteo, lo que se lee ahí no entra a
  ninguna lista. Sobre el bloqueo solo se ve lo que el cliente ve en la tienda: el nombre, el precio, el mayoreo
  («Desde 12: $41.00 c/u»), la promoción y, de una presentación, cuánto sale cada pieza («$4.50 c/u (12)»). Nunca la
  existencia, el ticket ni el conteo. Lo que no está dice «No está en el catálogo» con su código (mientras cargan los
  productos, solo el código). TalkBack dice cada lectura, también el mismo producto otra vez. Ya no lleva título, para
  que todo quepa en su 31 % del alto; con la letra grande, lo leído se desplaza y los botones no se encogen.
- **«Abrir la app» abre Precio con ese producto,** también si la app ya estaba abierta (antes solo la traía, como
  estaba), y desde Ajustes o las herramientas regresa a la principal: cierra el menú y las hojas y enseña el producto
  como si se hubiera leído, sin vibrar, sin anotarlo en la sesión de prueba y sin tocar el ticket; TalkBack lo dice,
  también al volver de Ajustes. Pide el PIN una sola vez aunque se toque dos veces; mientras se teclea, el escaneo
  rápido no se cierra solo, y si se cancela sigue ahí. Recientes no guarda su imagen (Android 13+).
- **Un toque al mosaico siempre abre:** si el escaneo rápido se había quedado abierto y ya había caducado, el toque se
  perdía (se cerraba al volver). Y traerlo con el mosaico cuenta como abrirlo otra vez: su plazo empieza de nuevo y ya
  no se cierra a los pocos segundos. Al encender la pantalla con el plazo vencido se sigue cerrando sin abrir la cámara.
- **Los accesos del ícono:** «Revisar precio» sigue fijo; «Armar ticket» y «Contar» son dinámicos, con id nuevos, y la
  app los publica después de abrir la cámara, solo si el menú cambió. Hoy salen los dos (este celular arma tickets y
  cuenta el conteo de prueba); con los perfiles, «Armar ticket» no saldrá en el celular de la tienda ni «Contar» en el
  piloto. **Lo que se ancló de «Ticket» y «Conteo» con la 0.1 o la 0.2 queda apagado:** hay que anclar otra vez
  «Armar ticket» o «Contar». Su mensaje («Este acceso cambió…») quizá no se vea en el lanzador.
- **Buscar le deja su lugar al aviso:** un aviso que seguía a la vista al abrir Buscar (p. ej. «… al ticket» y luego ←)
  tapaba los resultados de arriba; ahora la lista empieza abajo de él, como en el ticket completo.

**Pendiente antes de publicar la 0.3.0:** los perfiles de arranque siguen siendo los de la 0.1 (ver la 0.2.0). Y lo que
solo se prueba en el celular: [pruebas](docs/pruebas.md#en-el-pixel-8-pro-o-el-emulador).

## 0.2.0 (sin publicar)

La primera entrega de la [propuesta de interfaz](docs/propuesta-interfaz.md#entregas), «La base»: la pantalla de la
fase 2 sobre el escáner de hoy, todavía sin conexión al POS.

- Tres modos abajo, sobre el escáner del mismo tamaño: **Precio** (el verificador F9: escanear solo enseña el precio,
  el mayoreo, la existencia y la promoción, con «Al ticket» junto al precio), **Ticket** (cada lectura suma; el último
  renglón va resaltado con − y +, y tocar la cantidad abre el teclado) y **Conteo** (el de prueba de hoy, en el
  celular). Cada modo tiene su color: ámbar, azul petróleo y violeta. La app abre en el último modo usado.
- **El botón rápido**, abajo a la derecha, para una mano: un toque abre tres opciones; pulsar y deslizar hacia una la
  elige al soltar. ← busca, ↑ entrega (en Precio, «Enseñar al cliente»: el precio en grande) y ↖ es lo del modo. Gana
  la opción que queda bajo el dedo; un toque de rebote no confirma nada; vibra distinto al armarse, al cambiar y al
  elegir; en zurdo va en espejo; TalkBack lo ofrece como acciones. «Mandar» e «Impresiones» se ven apagadas en su
  lugar, con su razón: llegan con la conexión al POS.
- **Buscar** con el campo abajo, junto al pulgar, y los resultados encima: pedazos de palabra en cualquier orden y sin
  acentos («lus az»); sin escribir, los rápidos y lo más vendido.
- **El ticket completo** (se jala o se toca el asidero o el encabezado): todos los renglones con − y +, deshacer,
  «Buscar», el total, «Mandar a una caja» e «Impresiones» (apagadas, con su razón) y «Empezar otro ticket». La cantidad y
  Buscar que se abren desde ahí regresan ahí. Se guarda en el celular.
- **Cada «Deshacer» deshace lo que dice su aviso**, aunque mientras tanto se haya escaneado otra cosa: lo quitado regresa a
  su lugar, entre los mismos renglones, «… al ticket» quita esa pieza y «Ticket vacío» regresa lo de antes; lo escaneado
  después queda arriba. Si el deshacer del ticket completo ya lo deshizo, el aviso se va (no lo hace dos veces). Teclear 0
  también avisa «Quitado del ticket». Con el precio en grande, que es para el cliente, los avisos esperan a que se regrese.
- **TalkBack y el teclado:** cada lectura se anuncia (en Precio, el producto y su precio; en Ticket, lo que se sumó y el
  total; en Conteo, el producto y cuántos van); Buscar dice cuántos encontró y lee los resultados del mejor al peor; los
  avisos duran lo que pida «Tiempo para actuar»; con el menú abierto el foco no sale de él, Esc lo cierra y regresa al
  botón, y el botón enfocado se ve. Con la letra al 200 % la tarjeta de Precio se acomoda y, si no cabe, se desplaza:
  nada desaparece.
- Productos de prueba con nombre y precio (los del prototipo, ficticios), con el mayoreo como lo cobra la caja.
- **Ajustes**, desde el botón de arriba a la izquierda: vibración, bip, linterna automática, «Botón rápido a la
  izquierda», «Volver a ver las pistas» y el mosaico. Las herramientas de medición (el escáner, la sesión de prueba,
  el banco y las pruebas de estrés), que eran cuatro de las cinco pestañas de abajo, bajan a Ajustes ›
  «Herramientas de medición», junto con los contadores del botón rápido.
- Los accesos directos son «Precio», «Ticket» y «Conteo», y abren como el ícono: después de usar uno, el ícono regresa a
  la app abierta en lugar de abrir otra encima. El de «Venta» que ya estaba anclado abre el ticket.
- **El escaneo rápido** (el mosaico) sigue el modo de la app, también si se quedó abierto: cada vez que vuelve (el
  mosaico otra vez, o al encender la pantalla) toma el modo de ese momento y lo de esa apertura empieza de cero. En
  Precio solo enseña lo leído con su nombre y su precio, y nada entra al ticket; TalkBack lo dice cada vez, también si
  es el mismo producto. Con la letra grande sus botones no se encogen.
- **Los contadores del botón rápido** salen con la medición de la sesión de prueba (contadores-rapido.csv).

**Pendiente antes de publicar la 0.2.0:** volver a generar los perfiles de arranque
(`app/src/main/generated/baselineProfiles`, con `./gradlew :app:generateBaselineProfile` en el Pixel o en un emulador
con API 33 o más; [pruebas](docs/pruebas.md#arranque-y-perfil)). Los de ahora son de la 0.1: traen la pestaña del escáner,
que ya no existe, y nada de la pantalla principal ni del botón rápido. No se editan a mano.

## 0.1.0 (sin publicar)

Primera versión de prueba: el escáner solo, sin conexión al POS.

- Lee EAN-13 (también los internos que empiezan con 2), UPC-A, UPC-E, EAN-8, ITF-14, Code 128 y QR con la cámara
  trasera. El motor automático lee primero con zxing-cpp (~0.4 ms por cuadro), luego con el umbral que midió el
  localizador (etiquetas blancas sobre fondo gris) y deja ML Kit de respaldo para lo borroso; también se puede fijar un
  solo motor.
- Guía al personal con una sola indicación a la vez: muévete a un lado, acércate, aléjate, mantén firme, enfocando,
  poca luz o reflejo. Si el código ya se leyó y sigue enfrente, lo dice («Ya leído: retíralo para leerlo otra vez»).
- Confirma cada lectura con vibración, retícula verde y el código en grande; el bip viene apagado y se enciende en
  Ajustes. Valida el dígito verificador y, en los códigos de barras, pide dos lecturas iguales en cuadros seguidos
  (nunca de un solo cuadro; el QR entra de una). El mismo código vuelve a contar cuando sale de la mira medio segundo o
  a los 1.5 s aunque siga enfrente (sostenerlo más lo cuenta otra vez).
- Modo venta (lista con +1, −1 y deshacer) y modo conteo (contador grande).
- Abre rápido: la cámara se enlaza antes de que haya interfaz, sin destello blanco y con un perfil de arranque. El
  mosaico «Escanear» de Ajustes rápidos abre el escaneo rápido sobre la pantalla de bloqueo, sin pedir el PIN; también
  hay accesos directos «Escanear venta» y «Escanear conteo». Al volver a la pestaña del escáner la cámara abre una sola
  vez.
- Sesión de prueba con CSV (lecturas, aperturas y consumo de recursos cada segundo) para la prueba en la tienda, banco
  de pruebas que compara los motores en cada celular y pestaña de estrés (abrir y cerrar 200 veces, ráfagas de zoom y
  linterna, cambio de motor y escaneo continuo con el estado térmico).
- El APK de release sale firmado con la llave del proyecto: se instala encima de la versión anterior.
- No usa la red: sin permisos de Internet ni de estado de red y sin las estadísticas de uso que ML Kit manda a Google.
  Cada APK revisa al armarse que solo pide la cámara y la vibración.
- El banco de pruebas, los carteles del emulador y las pruebas usan solo códigos ficticios (EAN-13 750999…, internos
  299999…, UPC-A 099999…, Code 128 ZZ…), ninguno de un producto real.
- Busca cada lectura en un catálogo de prueba con la regla que usará el servicio del POS: el código tal cual, el UPC-A
  sin el 0 inicial, el EAN-13 que empieza con 0 como UPC-A de 12 y de 11, y el UPC-E expandido; nada más. Da lo mismo
  con ML Kit que con zxing-cpp. Trae un catálogo de ejemplo ficticio; en Ajustes se carga el archivo que exporte el
  dueño, que se copia al celular y no sale de él. La sesión de prueba anota si cada lectura está en el catálogo, por qué
  variante y con qué clave, y el panel de lecturas marca lo que no está.
- Hoja de etiquetas de prueba para imprimir a tamaño real (`./gradlew :nucleo:hojaEtiquetas`): EAN-13, Code 128 y
  EAN-8 ficticios con módulos de 0.25 a 0.5 mm y varias alturas, con su regla de 100 mm y su CSV de catálogo.
- Ficha de la cámara: al primer arranque lee de Camera2 cada lente trasera (la lógica y sus físicas): focal, sensor,
  desde dónde enfoca y si viene calibrado, modos de enfoque, rango de zoom, resoluciones y cuadros por segundo. Dice
  cuánto abarca cada lente donde ya enfoca, cuántos píxeles le tocan a un módulo de 0.33 mm ahí y con qué zoom abre la
  app. Se ve en Ajustes (junto al lente) y sale con la medición de la sesión (ficha-camara.csv).
- Registro por intento: cada intento de la sesión de prueba guarda además el lente, la cámara física, la resolución
  de análisis, los ms por cuadro y a qué distancia enfocaba al leer (solo si la cámara da el enfoque calibrado). Y un
  resumen corto por tanda (cada bloque de condición), en la pestaña Sesión y en resumen.csv al exportar los intentos:
  cuántos leyó bien y a qué distancia, la lectura típica y la p90, lecturas por minuto, falsas (deben ser 0), con y
  sin linterna, y por lente.
- Más resolución: en Ajustes, el análisis a 1080p, 1440p o 4K (el visor se queda en 720p); se analiza solo el plano Y,
  recortado a la franja, y siempre el último cuadro, así que lo que cuesta de más es la franja más grande. Y «Leer
  también girados 90°», para que zxing-cpp pruebe las dos orientaciones de la franja. Las dos vienen apagadas hasta
  medirlas en la tienda; la sesión guarda la resolución de cada intento.
- Lado a lado: un motor más en Ajustes que corre zxing-cpp y ML Kit en cada cuadro sobre la misma franja y lee lo que
  lea cualquiera. Cada intento de la sesión dice, para los cuadros de esa lectura, en cuántos leyó cada motor el código,
  en cuántos leyó otra cosa, a los cuántos ms lo leyó por primera vez y lo que costó por cuadro; el resumen de la tanda
  lo junta por motor y el diagnóstico lo muestra en vivo. Es para comparar los motores con productos reales, no para
  la tienda.
- Luz en la mira: la cámara mide la exposición en el centro de la franja (no con todo el cuadro), siempre, y la vuelve
  a poner después de cada disparo de enfoque; así un código oscuro con el fondo claro no sale quemado ni negro. Se
  apaga en Ajustes para comparar; el diagnóstico dice cuál rige. El enfoque sigue continuo con su disparo al abrir, al
  tocar y cuando un código no se lee, y la linterna a mano (o automática en Ajustes), como antes.
