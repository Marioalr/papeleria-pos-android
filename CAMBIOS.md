# Cambios

Qué trae cada versión de la app ([versiones](docs/plan.md#versiones)). La más nueva va arriba.

## 0.4.2 (sin publicar)

Los 9 hallazgos de la [revisión de la entrega 2](docs/revision-entrega-2.md), arreglados; cada uno con su prueba.

- **La llave solo sale hacia la caja principal que probó quién es:** al abrir la app y después de cada caída, antes de
  mandar la llave, la dirección guardada tiene que probar quién es (`/api/identidad`), como en la caja de Windows. Si
  no lo prueba, se busca la caja principal por UDP.
- **Un 401 de otro equipo ya no borra el alta:** solo se olvida la llave si quien dice «caja-sin-alta» prueba ser la
  caja principal o si no se encuentra a nadie que lo pruebe. Si la caja principal está en otra IP, el celular se cambia ahí.
- **Al abrir en frío con alta, la app sale bloqueada desde el primer cuadro,** sin esperar a que se lea la copia.
- **Una sola copia completa a la vez,** y una que termina de bajar después de olvidar el alta ya no se guarda.
  Irse al fondo mientras baja ya no la tira.
- **Las promociones, con el día de hoy:** si la app sigue abierta de un día para otro, los productos se vuelven a armar
  al pasar al frente.
- **El latido nota que se le quitó la llave** aunque no cambie el catálogo: con las mismas versiones, pide los cambios
  (casi vacíos) solo para comprobar la llave.
- **100.64/10 ya no cuenta como red de la tienda** (es la de los operadores, CGNAT).
- `ConexionTest` contesta como el servicio de verdad y sí comprueba que la impostora nunca recibe la llave.

## 0.4.1 (sin publicar)

Tres detalles de Ajustes › La conexión que salieron al probar la 0.4.0 en el Pixel 8 Pro contra el POS de la rama
`celular/como-caja` (2026-11-09).

- **Las fechas, siempre en español de México:** «Dado de alta», «De cuándo» y «Contestó» seguían el idioma del celular
  («Nov 9, 2026 8:47 AM»). Ahora salen como el resto de la app: «9 nov 2026, 8:47», con reloj de 24 horas. Se arman a
  mano (`ui/Fechas.kt`), sin `DateFormat`, para que salgan igual en cualquier versión de Android. Las demás fechas de la
  app ya eran numéricas y sin idioma (la sesión de prueba, los CSV y los nombres de archivo).
- **La red, buena desde el alta:** justo después del alta decía «La red: Sin probar todavía» hasta el primer latido
  (30 s), aunque el alta y la copia ya habían hablado con el servicio. Ahora el alta deja la red como buena (o lenta, si
  tardó más de 1.5 s) con su hora, y la copia al bajar también cuenta como respuesta.
- **El aviso de sin acceso ya no se pierde:** si la caja le quitaba el acceso (401 `caja-sin-alta`) con la entrada con
  la huella tapando todo, o si la app se reiniciaba, La conexión solo decía «Conectar con la caja principal». Ahora el
  aviso se guarda (solo el aviso: la llave y los datos de la caja se borran igual) hasta que el dueño lo ve en La
  conexión o vuelve a dar de alta. Mientras, el renglón de Ajustes lo dice y Precio, al no encontrar un código, lo
  dice abajo y discreto.

## 0.4.0 (sin publicar)

La entrega 2 de la [propuesta de interfaz](docs/propuesta-interfaz.md#entregas), «El celular de Luis», con lo que decidió
el dueño el 2026-11-08: **el celular se da de alta en el POS como una caja más y lleva la copia del catálogo de esa
caja** ([el documento del POS](https://github.com/Marioalr/papeleria-pos/blob/celular/como-caja/docs/celular-como-caja.md),
que manda sobre la propuesta). Ya no hay «celulares» aparte en el POS, ni llaves con solo los modos, ni Configuración ›
Celulares, ni la sesión del celular de la tienda. Necesita el POS de la rama `celular/como-caja`.

- **Ajustes › La conexión: el alta como caja,** igual que la caja de Windows. Busca la caja principal en el Wi-Fi de la
  tienda (UDP a 5081) o se escribe su IP (solo direcciones privadas); el usuario y el PIN de quien tiene «Dar de alta y
  quitar cajas» (el PIN no se guarda); el nombre («Celular de Luis») y la serie (CEL o, si ya existe, CEA, CEB…; el
  servicio solo acepta de 1 a 3 letras), que se proponen y se editan como en la PC. Siempre crea una caja nueva: nunca le quita la llave a una que existe. La llave
  queda cifrada con el Android Keystore. Quitarle el acceso se hace desde la caja: Configuración › Cajas › «Quitar PC»;
  entonces la app borra la llave y la copia y lo dice.
- **La copia del catálogo** (`GET /api/cajas/{id}/paquete`, varios MB) baja al dar de alta y se guarda como JSON propio
  en la memoria de la app. Solo los productos con sus presentaciones, precios y existencia, todos los códigos, las
  marcas, los sinónimos, lo más vendido, lo elegido en la búsqueda, las promociones y el nombre de la tienda. **Los
  usuarios con el hash de su PIN, sus permisos y reglas, y los clientes con su crédito ni siquiera se leen del JSON**:
  nunca tocan la memoria ni el disco (`CopiaTest` lo revisa). Mientras la app está al frente le avisa a la caja principal
  cada 30 segundos (`GET /api/estado?caja=`); si cambió algo, pide solo lo que cambió (`/cambios`) y lo aplica como la
  caja de Windows; si el servicio dice que se baje entera, la baja. Nunca en segundo plano. Sin red, Precio y Buscar
  siguen con la copia.
- **Precio, Buscar y el ticket con la copia:** cada variante es un producto y cada otra presentación (la caja de 12) es
  otro, con su código y su precio. El precio se calcula con el mismo `CalculoPrecio` sobre los precios tal cual: el de la
  sucursal manda sobre el general, las escalas de mayoreo, el precio propio de la presentación nunca más caro que sus
  piezas, y la promoción que corre hoy (la tarjeta la dice: «Regreso a clases: lleva 3 y paga 2»). Un código que es de
  dos productos no se adivina, como en el POS: la tarjeta dice «Ese código es de más de un producto: búscalo por nombre».
  La tarjeta ya no trae el departamento (la copia no lo tiene), solo el código. Buscar es la búsqueda de la caja con las
  marcas y los sinónimos de la tienda, lo que esa caja eligió antes primero y lo más vendido; sin escribir, lo más
  vendido (los rápidos llegan después, con red). El mosaico «Precio» sobre el bloqueo también lee la copia.
- **Entrar con la huella:** con el celular dado de alta, la app se bloquea al abrirse y al volver después de 10 minutos
  en el fondo, y se entra con lo que el celular acepte (la huella o la cara; si no, el PIN, el patrón o la contraseña del
  celular). Mientras, la pantalla queda tapada y las lecturas no cuentan ni vibran; la cámara sí se enlaza, así que al
  entrar ya escanea. Sin bloqueo de pantalla no se da de alta: la app lo dice y abre la seguridad del celular. El mosaico
  sobre el bloqueo no pide nada, como antes. Es local, del celular: la app no pide la sesión del POS (`POST /api/sesion`)
  porque el precio y la búsqueda no la necesitan; llegará con lo que dependa de quién.
- **Si la caja principal cambia de IP:** tras dos latidos sin respuesta la app pregunta por UDP y, antes de mandarle la
  llave a la dirección nueva, le pide que pruebe quién es (`GET /api/identidad`: el HMAC con la huella de la llave y la
  firma ECDSA del servicio), como la caja de Windows.
- **Los productos de prueba** (los ficticios del prototipo) salen de Precio, Buscar y el ticket y quedan como herramienta:
  Ajustes › Herramientas de medición › El escáner › «Productos de prueba», para medir y probar sin la caja principal
  (también en el escaneo rápido). Ajustes dice si está encendida.
- **Permisos:** vuelve Internet (la app solo acepta direcciones privadas de la red local; HTTP sin TLS como la caja de
  Windows, en `res/xml/red_segura.xml`) y entra el de la huella (`USE_BIOMETRIC`, normal, sin diálogo); el estado de la
  red sigue fuera. La revisión al armar cada APK (`verificarPermisos`) acepta cámara, vibración, Internet y la huella, y
  nada más. Con targetSdk 36, Android 17 deja pasar a la red local con Internet;
  al pasar a targetSdk 37 habrá que pedir `ACCESS_LOCAL_NETWORK` en tiempo de ejecución.
- **Bibliotecas nuevas:** OkHttp 4.12 y kotlinx-serialization 1.9 (y MockWebServer en las pruebas). Ninguna manda nada
  fuera de la red de la tienda.

**Lo que no trae y por qué:** «Buscar cambios ahora» en Ajustes (el latido de 30 s basta; si en las pruebas de la tienda
hace falta, se agrega), los rápidos (Copia B/N, CURP) en Buscar sin escribir (van por `GET /api/sucursales/{id}/rapidos`,
con red; después), las fotos (la interfaz no las enseña), la red «lenta» con espera para mandar (no hay nada que mandar
todavía) y `POST /api/sesion`.

**Pendiente antes de publicar la 0.4.0:** probar en la PC contra el servicio del POS de la rama `celular/como-caja` con la
base ficticia, y en la tienda un fin de semana con el dueño: el Pixel en el Wi-Fi, el alta en la caja principal, y el
precio y la búsqueda con los productos reales ([pruebas](docs/pruebas.md#la-conexión-con-el-pos)). Medir en el Pixel
cuánto tarda en armarse la copia de la tienda (el índice de la búsqueda) y que la apertura no empeore.

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

**Los perfiles de arranque, generados en el Pixel** (2026-11-07): ya traen la pantalla principal, el botón rápido y
Ajustes. En frío, la 0.3.0 llega a escanear en 453 ms (mediana), contra 523 ms de la 0.1.0 en la misma sesión
([medición](mediciones/2026-11-07-0.3.0/LEEME.md)). Todavía sin la lectura de un código: nadie apuntó la cámara.

**Pendiente antes de publicar la 0.3.0:** lo que solo se prueba en el celular con el PIN, el mosaico o el emulador
([pruebas](docs/pruebas.md#en-el-pixel-8-pro-o-el-emulador)). En el Pixel ya se revisó que los accesos y el ícono dejan
una sola MainActivity.

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

**Los perfiles de arranque** se volvieron a generar con la 0.3.0 (ver arriba).

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
