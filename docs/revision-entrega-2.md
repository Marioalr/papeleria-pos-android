# Revisión de la entrega 2 (0.4.0): el celular como caja

Revisión del diff `a60c02a..0d46e5e -- app` (sin los perfiles de arranque), comparado con el servicio del POS en la rama
`celular/como-caja` de `papeleria-pos-celular` (`docs/celular-como-caja.md` y el C#). Solo defectos confirmados: cada uno
con una prueba JVM que falla con el código de la 0.4.0 (`app/src/test/kotlin/mx/papeleriapos/android/caja/RevisionEntrega2Test.kt`,
marcadas `@Ignore("hallazgo N")`; se quita la marca al arreglarlo) o, donde no se puede probar en la JVM, con las líneas
exactas y el escenario. No se arregló nada.

Las pruebas usan un despachador que contesta **como el servicio de verdad**: `GET /api/estado` está abierto con cualquier
llave o sin ella (`SeguridadCajas.cs:53`, `ApiCajas.cs:24-27`) y solo lo demás contesta 401 «caja-sin-alta». El de
`ConexionTest` contesta 401 también en `/api/estado`, que el servicio no hace (hallazgo 9).

| # | Gravedad | Qué | Cómo se confirmó |
|---|---|---|---|
| 1 | Alta | La llave sale en claro hacia la IP guardada sin que pruebe quién es, también después de una caída | Prueba |
| 2 | Alta | Cualquier equipo en la IP guardada que conteste 401 «caja-sin-alta» borra la llave y la copia | Prueba |
| 3 | Alta | Al abrir la app en frío no está bloqueada hasta que se lee la copia: se ve el ticket y las lecturas cuentan | Líneas |
| 4 | Media | El alta y el latido bajan la copia completa dos veces a la vez; una copia que termina tarde se publica sin alta | 2 pruebas |
| 5 | Media | Las promociones se calculan con el día en que se armó el catálogo: al pasar la medianoche el precio puede ser otro | Líneas |
| 6 | Media | Irse al fondo mientras baja la copia la tira (y «bajando» se queda encendido) | Prueba |
| 7 | Baja | El latido nunca nota que se le quitó la llave (`/api/estado` está abierto) | Prueba |
| 8 | Baja | 100.64/10 (CGNAT de los operadores) cuenta como «red de la tienda» | Prueba |
| 9 | Baja | `ConexionTest` no prueba lo que dice: una aserción vacía y un 401 que el servicio no da | Líneas |

---

## 1. La llave sale hacia la IP guardada sin que pruebe quién es (Alta)

**Dónde:** `caja/Conexion.kt:128-144` (`latir` manda `a.llave` a `a.direccion` siempre), `caja/Conexion.kt:197-218`
(la identidad solo se pide a una dirección **nueva**: `if (d == a.direccion) continue`).

**Escenario:** la PC de la caja principal se reinicia o cambia de IP (DHCP del router). El latido falla (`IOException`,
`Red.CAIDA`). El router le da esa IP a otro equipo de la red (otro celular, una laptop, un cliente en el Wi-Fi). El
siguiente latido, 30 s después, manda `X-Caja-Llave` en HTTP sin cifrar a ese equipo. Con esa llave el equipo es una caja
(«puede pedir lo mismo que una caja», `celular-como-caja.md`, paso 2.3): la copia completa, los precios y las llamadas de turnos y ventas que el celular nunca hace.
Al abrir la app pasa lo mismo: el primer latido manda la llave sin probar nada.

**Lo que hace la caja de Windows** (y lo que el documento dice que es «lo mismo»): al abrir y después de cada caída marca
la dirección como «sin comprobar» (`Contingencia.cs:711-712`, `:822-828`, `:1062-1075`) y **la llave no sale** mientras
no pruebe quién es (`Transporte.cs:125-126`, `ReencuentroDeLaPrincipal.ComprobarGuardadaAsync`).

**Prueba:** `despues de una caida la llave no sale hacia la ip guardada sin que pruebe quien es` — el equipo nuevo recibe
la llave.

**Arreglo propuesto:** un `sinComprobar` en `Conexion`: verdadero al leer el alta del disco y tras cualquier
`IOException`. Mientras lo esté, el latido primero pide `GET /api/identidad` a la dirección guardada (sin llave) y solo si
`Identidad.esLaCajaPrincipal` manda la llave; si no prueba, `Red.CAIDA` y a buscar por UDP (`reencontrar`).

## 2. Un 401 de cualquiera borra la llave y la copia (Alta)

**Dónde:** `caja/Conexion.kt:133-134`, `:158-159`, `:179-181` → `olvidarAlta` (`:263-269`), sin ninguna comprobación de
quién contestó.

**Escenario (el de la pregunta «si la IP cambia a otro equipo»):** la caja principal cambia de IP y la de antes la toma
la laptop del dueño con el POS de prueba (el documento dice que «puede haber una de prueba»), o la PC vieja con otra
base. El latido recibe 200 de `/api/estado` (abierto) con otra `base` → `desactualizada` → `GET /cambios` con la llave →
401 «caja-sin-alta» → se borran la llave y la copia. El celular queda sin precios ni sin red y hay que darlo de alta otra
vez con el PIN del dueño, aunque la caja principal de verdad esté en la red en otra IP y probaría quién es. De paso, la
llave ya viajó a ese otro equipo (hallazgo 1).

**Prueba:** `otro pos en la ip guardada no borra el alta si la caja principal de verdad se encuentra en otra ip`.

**Arreglo propuesto:** ante un 401 «caja-sin-alta», primero `reencontrar` (UDP + identidad); si otra dirección prueba ser
la caja principal, mudarse y repetir. Solo si la dirección guardada **prueba quién es** con `/api/identidad` y aun así
dice que no conoce la caja (404 «caja-sin-alta» de `ApiRed.cs:60-62` llega sin prueba: no se puede firmar), o si nadie lo
prueba, olvidar la llave. Valorar no borrar la copia en ese momento (sin llave no sirve para hablar con nadie, pero sí
para seguir dando precios sin red), como la caja de Windows, que avisa (`SinAlta`) y sigue con su copia.

## 3. Al abrir en frío, la app no está bloqueada hasta leer la copia (Alta)

**Dónde:** `MainActivity.kt:117-123` (`Entrada.alFrente` se llama solo después de `conexion.estado.first { it.leida }`),
`caja/Conexion.kt:96-103` (`leida` se pone después de leer el alta **y de leer y decodificar la copia entera**),
`entrada/Entrada.kt:31` (`_bloqueada` empieza en `false`), `EscanerViewModel.kt:141-146` y `principal/Piso.kt` (`alLeer`
solo descarta con `bloqueada`).

**Escenario:** proceso nuevo (el celular lo mató en segundo plano, o se abre por primera vez en el día) con alta y una
copia de varios MB. `onCreate` dibuja la pantalla principal con el ticket y el conteo guardados (`ListasCompartidas`) y la
cámara se enlaza; mientras el hilo de la conexión decodifica la copia (cientos de ms a segundos con decenas de miles de
productos), `bloqueada` es `false`: se ve el ticket, se puede tocar y **cada lectura cuenta** (suma al ticket, vibra). El
velo sale cuando termina de leer. La documentación de `Entrada` dice lo contrario: «mientras está bloqueada… las
lecturas no cuentan ni vibran» y «la app se bloquea al abrirse».

**Arreglo propuesto:** no esperar la copia para decidir: leer solo si hay alta (un `prefs.contains("llave")`, barato) o,
más simple, empezar bloqueada de forma pesimista (`_bloqueada = true` hasta saber que no hay alta o no hay bloqueo de
pantalla) y separar `leida` del alta de la lectura de la copia.

## 4. Dos copias completas a la vez y una que se publica sin alta (Media)

**Dónde:** `caja/Conexion.kt:255` (`darDeAlta` lanza `bajarCompleta`) y `:148-150` (el latido baja otra si
`_copia.value == null`); `:173-191` no revisa si ya hay una bajando ni si el alta sigue siendo la misma al publicar. El hilo
de un solo trabajador no los ordena: se intercalan en cada suspensión (`withContext(Dispatchers.IO)` de `ClienteCaja`).

**Escenario A:** el dueño da de alta el celular desde Ajustes, con la app al frente (el latido ya corre). La copia tarda
(varios MB, hasta 60 s). En el siguiente latido `_copia` sigue en `null` → segunda descarga simultánea: el doble de red y
de memoria (dos `PaqueteDto` decodificados) en el Pixel, y `bajando` se apaga cuando termina la primera aunque la segunda
siga. Prueba: `al darse de alta con la app al frente la copia completa se baja una sola vez` (bajan 2).

**Escenario B:** si `olvidarAlta` corre mientras una descarga está en vuelo (un 401 en otra llamada), al terminar
`publicar` guarda en `_copia` y en el disco la copia de un alta que ya no existe. Lo mismo hace `reencontrar`
(`:212-215`): si el alta se olvidó mientras buscaba (1.5 s de UDP más la identidad), escribe la IP en las preferencias
vacías y **revive el alta en memoria** con la llave vieja. Probabilidad baja; consecuencia, una copia o un alta fantasma.
Prueba: `una copia que termina de bajar despues de olvidar el alta no se publica`.

**Arreglo propuesto:** un solo `Job` de descarga (si hay uno activo, el latido no lanza otro); y antes de `publicar` /
`guardarDireccion`, ya en el hilo, comprobar que `alta` sigue siendo la misma (`alta?.llave == a.llave && caja.id`) o
llevar un número de generación que `olvidarAlta`/`darDeAlta` incrementan.

## 5. Las promociones se quedan con el día en que se armó el catálogo (Media)

**Dónde:** `catalogo/FuenteDeProductos.kt:69-70` (el catálogo armado se reutiliza mientras la copia sea el mismo objeto),
`copia/ProductosDeLaCopia.kt:300` (`hoy` se fija al armar), `:329`, `:341`, `:351` (el texto de la promoción y el
`Cobro` con ese `hoy`), `catalogo/Productos.kt:110-118` (`Cobro.dia`).

**Escenario:** la app queda viva de un día para otro (en segundo plano el proceso sobrevive horas) y en la mañana no ha
cambiado el catálogo ni las existencias (la tienda abre: sin ventas todavía, el latido no pide cambios y la copia es el
mismo objeto). Una promoción que terminó ayer se sigue enseñando y cobrando en Precio y en el total del ticket, y una que
empieza hoy no sale. La caja cobra distinto de lo que dijo el celular. Las promociones traen sus fechas justamente para
calcularse en el celular (`celular-como-caja.md`, tabla del paso 6).

**Arreglo propuesto:** guardar el día con el catálogo armado (`deLaCopia` → copia + día) y volver a armar si cambió
(al pasar al frente basta con comparar `hoy()`), o que `Cobro` calcule el día al usarse.

## 6. Irse al fondo mientras baja la copia la tira (Media)

**Dónde:** `caja/Conexion.kt:120-125` (`alFondo` cancela el `Job` del latido, que es el que está en `bajarCompleta`;
el comentario dice «lo que esté bajando, termina»), `:173-185` (solo atrapa `ErrorDelPos` e `IOException`: con la
cancelación `bajando` se queda en `true`).

**Escenario:** la primera copia (o una completa por `paqueteCompleto`) está bajando y el dueño contesta un mensaje unos
segundos. La llamada de OkHttp no se interrumpe: los MB bajan y se decodifican, y al volver de `withContext` la
cancelación tira todo. Ajustes dice «bajando» mientras tanto. Al volver al frente se empieza otra vez desde cero; con
idas y vueltas frecuentes (el uso normal del celular en la tienda) la primera copia puede no llegar nunca.

**Prueba:** `irse al fondo mientras baja la primera copia no la tira`.

**Arreglo propuesto:** lanzar la descarga en `alcance` (no dentro del `Job` del latido), con el `Job` único del
hallazgo 4, y apagar `bajando` en un `finally`.

## 7. El latido no nota que se le quitó la llave (Baja)

**Dónde:** `caja/Conexion.kt:131-135` espera un 401 del latido; el servicio nunca lo da en `/api/estado`
(`SeguridadCajas.cs:53`, `ApiCajas.cs:24-27`: abierto, solo usa la llave para «se le vio»).

**Escenario:** el dueño pierde el celular y en la caja hace «Quitar PC». Mientras no cambien el catálogo ni las
existencias, el latido sigue contestando 200: Ajustes dice red «Buena» y «Contestó» a la hora actual. Se nota hasta que
cambia una versión y `/cambios` contesta 401. En horas de venta son minutos; con la tienda cerrada, nunca.

**Prueba:** `al quitarle la llave el latido lo nota aunque no cambie el catalogo`.

**Arreglo propuesto:** cada cierto tiempo (o en cada latido, es barato con las versiones iguales: `CambiosAsync` regresa
casi vacío) llamar a algo con llave que sí valide, como `GET /api/cajas/{id}/cambios`; o pedir en el POS que
`/api/estado?caja=` conteste 401 con una llave que no conoce.

## 8. 100.64/10 cuenta como red de la tienda (Baja)

**Dónde:** `caja/RedLocal.kt:52-53`. El comentario de `:7-11` y `docs/plan.md` solo nombran 10/8, 172.16/12, 192.168/16 y
169.254/16.

**Escenario:** 100.64/10 es el espacio compartido de los operadores (CGNAT, RFC 6598): con datos móviles, una IP así es
de otro cliente del operador, no de la tienda. Si se escribe o se guarda una dirección así, la llave y el PIN del dueño
viajan en claro por la red del operador. (También la usan VPN como Tailscale; si se quiere eso, que sea una decisión
escrita.)

**Prueba:** `solo direcciones privadas de la red de la tienda`.

**Arreglo propuesto:** quitar la rama de CGNAT de `esPrivada`.

## 9. `ConexionTest` no prueba lo que dice (Baja)

**Dónde:** `ConexionTest.kt:199`: `assertTrue(pedidos.none { it.startsWith("/api/estado?caja") && false })` siempre es
verdadera; «a la impostora nunca se le mandó la llave» no se comprueba (además `pedidos` no distingue a qué servidor
llegó). `ConexionTest.kt:56-59` contesta 401 «caja-sin-alta» también a `/api/estado`, que el servicio no hace; por eso
pasa `un 401 sin alta borra la llave y la copia y lo dice` y no se vio el hallazgo 7.

**Arreglo propuesto:** que cada servidor de mentira anote las llaves que recibe y se compruebe que la impostora no recibió
ninguna; que `/api/estado` del despachador esté abierto como en `SeguridadCajas.cs`.

---

## Revisado sin defecto confirmado

- **La copia no lee usuarios ni clientes:** `PaqueteDto` y `CambiosDto` (`caja/Dtos.kt:150-190`) no tienen esos campos y
  `ignoreUnknownKeys` los salta; `Copia` solo guarda lo suyo. El PIN no se guarda (solo en el `PasoAlta` en memoria y el
  cuerpo de las dos llamadas del alta); nada de la conexión escribe en el registro.
- **`Copia.aplicar` contra `CopiaAlDia.Aplicar`:** mismas reglas para productos, quitados, códigos, existencias y lo demás.
  Diferencias sin efecto: no aplica `existencias` a los productos que llegaron (el servicio los manda con la existencia
  leída en el mismo momento, `CopiaDeLaCaja.cs:104-109`) y no quita duplicados de la copia (la copia completa no los trae).
- **La identidad:** `Identidad.kt` coincide con `ApiRed.cs:52-66` y el documento (mensaje, huella, firma P1363, dirección
  en que se conectó). La llave del servicio se guarda en el alta y se exige después.
- **El latido solo al frente:** solo `MainActivity.onStart/onStop` lo encienden y apagan; el escaneo rápido solo lee la
  copia (`FuenteDeProductos`), sin latir.

## Para probar en el Pixel (no confirmado en la JVM)

- Android 11+ con «Usar PIN» en el diálogo de la huella: si el PIN del sistema abre una Activity que detiene a
  `MainActivity`, `onStop` → `Entrada.alFondo` → `pidiendo.cancel()` cancelaría la autenticación en curso y, al volver,
  `alFrente` la pediría otra vez (nunca se entra con el PIN). Revisar si con PIN se entra a la primera.
