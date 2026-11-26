# Propuesta: un celular enlazado a dos sucursales

Para aprobar (2026-11-26). El celular se da de alta en las dos sucursales y el de la tienda dice, con un toque, en cuál
está. No hay comprobaciones periódicas para adivinarlo: al cambiar, se usa la copia de esa sucursal y se habla con su
caja principal.

## Cómo está hoy

- **Un alta, una copia.** `AlmacenAlta` guarda una sola alta en `conexion.xml`: la IP de la caja principal, la caja
  (id, nombre, serie, sucursal), la llave cifrada y la llave pública del servicio. `AlmacenCopia` guarda una sola copia
  en `files/copia/copia.json`. Al leerla, la copia solo se acepta si es de la caja del alta (`Conexion.leerSiFalta`).
- **Dar de alta otra vez borra todo.** `Conexion.darDeAlta` borra la copia de antes, y un alta nueva reemplaza la
  anterior. Ir a la otra sucursal hoy obliga a dar de alta otra vez, con otra caja nueva en el POS cada vez.
- **Cuando la caja principal no contesta**, el latido marca la red como caída con su causa (`Diagnostico`). Después de 3
  latidos sin respuesta busca por UDP (`reencontrar`) y acepta solo la dirección que prueba con `Identidad` que es *la
  misma* caja principal. Si otra sucursal contesta, no la prueba, así que la descarta. Esto está bien: la llave de una
  sucursal nunca sale hacia la otra.
- **Lo que depende de la copia:** los precios y la búsqueda (`ProductosDeLaCopia`), las fotos (en caché por
  `varianteId-huella`), las listas de venta y conteo (`ListasCompartidas`, en disco, renglones por código y resueltos
  contra el catálogo cuando se dibujan) y la entrada con huella (`AlmacenAlta.hayAlta`, que revisa si existe la llave en
  `conexion.xml`).

## El estado del POS (2026-11-26)

- **La segunda sucursal:** una sola caja, instalada como **caja principal** (no como «La única caja»). El ensayo ya salió
  bien. El alta del celular funciona como hoy, y también la búsqueda por UDP.
- **La matriz:** dos cajas. Ya se aprobó instalar la principal y la segunda caja conectada a ella. **Todavía no está
  instalada.**
- **Ya se aprobó vender en las dos sucursales**, aunque todavía se reporta a Eleventa. Cada sucursal tiene su propia
  base, con sus propios ids y nombres: sigue siendo el caso de «dos bases distintas».

No hay nada que bloquee del lado del POS. El orden práctico: el celular se enlaza primero a la segunda sucursal, que ya
está instalada. Cuando se instale la matriz, se agrega el segundo enlace con «Conectar a otra sucursal». Por eso la
migración importa: el primer enlace ya va a existir, con su copia, antes de que llegue el segundo.

En las dos sucursales hay caja principal, así que la búsqueda por UDP funciona en ambas. Conviene igual reservar la IP
de cada caja principal en su router (DHCP fijo).

## La propuesta

### Lo que guarda el celular

**Una lista de enlaces, uno por sucursal, y cuál está activo.** Cada enlace es lo que hoy es el `Alta`, sin cambios,
más su propia copia.

| Qué | Hoy | Con dos sucursales |
|---|---|---|
| Alta | `conexion.xml` | `conexion.xml` sigue siendo el primer enlace (sin migración); el segundo va en `conexion-2.xml` con las mismas claves. Un `enlaces.xml` guarda cuál está activo |
| Llave | Cifrada con `Cifrador` | Igual, una por enlace con la misma llave del Keystore |
| Copia | `files/copia/copia.json` | `files/copia/<cajaId>.json`. La de hoy se renombra la primera vez que se lee (ya se valida contra `caja.id`) |
| Fotos | `files/fotos/<variante>-<huella>.jpg` | Igual, compartidas. Con bases distintas los `varianteId` no chocan. Con el servidor serán los mismos y la caché sirve para las dos |
| Listas de venta y conteo | Una de cada una | Igual, una de cada una (ver «Al cambiar») |

**Cómo se reconoce un enlace: por `base` + `sucursalId`, no por la IP.** `/api/estado` ya trae `base`, y `CajaDto` trae
`sucursalId`. Así:

- Si alguien toca «Conectar a otra sucursal» frente a una que ya está enlazada, la app lo detecta y dice «Este celular
  ya está en Sucursal Centro: usar esa». No crea otra caja en el POS.
- El modelo sirve para los dos mundos. Hoy hay dos instalaciones con dos bases, así que las bases son distintas. Con el
  servidor habrá una base con varias sucursales: la misma base y distinta sucursal. No cambia nada de la estructura.

**Límite: dos enlaces en la interfaz.** El almacén admite una lista, para que no haya que migrar si un día son tres,
pero la pantalla y las pruebas se hacen para dos.

### Dónde se cambia (sin comprobaciones periódicas)

1. **En Ajustes › La conexión**, con alta: una sección «Sucursales de este celular».
   - Cada enlace muestra su nombre, el nombre y la serie de la caja que es en esa sucursal (cada una le pone el suyo), su dirección y de cuándo es su copia. El activo
     lleva una marca; los demás, un botón «Usar esta».
   - Abajo, «Conectar a otra sucursal» abre los mismos tres pasos del alta de hoy (`PasoAlta`): buscar o escribir la IP,
     usuario y PIN, sucursal, nombre y serie. La diferencia es que *agrega* el enlace en vez de reemplazarlo.
   - «Olvidar esta sucursal» borra un enlace en el celular (su llave y su copia). En el POS se sigue quitando con
     «Quitar PC», como hoy.
2. **Donde hoy dice que no encuentra a la caja principal.** Con la red caída, debajo de la causa (`conexion-causa`) y en
   el renglón de la conexión de la pantalla principal:
   > No contesta Sucursal Centro. ¿Estás en otra sucursal?
   > [Usar Sucursal Norte]  [Conectar a otra sucursal]

   Aparece solo cuando ya se sabe que no contesta. No se agrega ninguna búsqueda nueva.
3. **Opcional: una sugerencia sin costo.** `reencontrar` ya busca por UDP después de 3 latidos fallidos, y descarta las
   cajas principales que no prueban ser la propia. En ese mismo recorrido se puede probar `Identidad` también contra los
   *otros* enlaces, con sus llaves. Si uno lo prueba, la sugerencia sube a «Parece que estás en Sucursal Norte: [Cambiar]».
   - **Nunca cambia solo:** el ticket a medias es de una sucursal, y un cambio sin avisar confunde precios y existencias.
   - Funciona en las dos sucursales, porque las dos tienen caja principal con UDP. Recomiendo dejarlo para después del
     primer uso real con la matriz instalada.

### Al cambiar de sucursal

En `Conexion`, «usar este enlace» hace lo mismo que ya hace cuando se olvida o se da un alta:

1. `generacion++`: lo que estaba en vuelo (una copia bajando, una búsqueda por UDP o los cambios) ve que ya no es suyo y
   no publica nada.
2. `alta` pasa al otro enlace con `sinComprobar = true`: antes de mandar la llave, la dirección prueba quién es, como al
   abrir la app.
3. La copia de ese enlace se lee de disco y se publica al instante: precio y búsqueda funcionan desde el primer segundo,
   aunque no haya red. Después el latido pide los cambios (las existencias del día).
4. El renglón de la conexión y Ajustes muestran la sucursal activa. En la pantalla principal conviene un indicador
   discreto con su nombre, para que se note en cuál está.

**El ticket o el conteo a medias.** Si hay algo en la lista, la app pregunta antes de cambiar:

> El ticket tiene 4 productos.
> [Guardar] [Mandar a caja] [Cancelar]

- **Guardar (se lo lleva):** el ticket se queda en la lista y viaja con el celular a la otra sucursal. Los renglones se
  guardan por código y se resuelven contra el catálogo cuando se dibujan, así que ahí toman los precios y las
  existencias de la otra sucursal sin hacer nada más. Si un producto no existe en la otra base, queda como hoy queda un
  código desconocido.
- **Mandar a caja:** abre la hoja de Mandar de hoy (`cajasParaMandar` → `mandar`) contra la sucursal *de la que se va*.
  Cuando se manda, la lista queda vacía y se cambia. Para eso tiene que contestar la caja principal de esa sucursal: si
  se llega desde la vista de «no contesta», el botón sale deshabilitado con «No contesta Sucursal Centro: no se puede
  mandar».
- **El conteo a medias** solo tiene «Guardar»: un conteo no se manda a una caja.

No se puede cambiar mientras se manda o se cobra un ticket: ese botón queda deshabilitado mientras `trabajando`.

**Quitar el acceso.** Si una sucursal le quita el acceso (`sinLlave` → `olvidarAlta`), se olvida *solo ese enlace*. Si
era el activo, el aviso de hoy agrega «[Usar Sucursal Norte]». Hoy `olvidarAlta` borra todo `conexion.xml`. Tiene que
pasar a borrar un enlace.

**La entrada con huella.** `AlmacenAlta.hayAlta` pasa a ser «hay al menos un enlace». La app se bloquea igual tenga uno
o dos.

## Cuando llegue el servidor

Con un solo catálogo y nombres estandarizados, la diferencia entre sucursales queda en las existencias (y en los
precios por sucursal: `PrecioDto.sucursalId` ya existe). La propuesta se prepara para eso sin hacerlo todavía:

- **Hoy:** una copia completa por enlace. Es lo único posible con dos bases distintas, con ids y nombres distintos.
- **Con el servidor:** los dos enlaces tienen la misma `base`. La copia se parte en *catálogo por base* (uno solo) y
  *existencias por sucursal* (las `ExistenciaLocalDto` que ya llegan en `cambios`). Cambiar de sucursal ya no lee otra
  copia: cambia el juego de existencias y el filtro de precios.
- **Probablemente un solo enlace con varias sucursales:** con el servidor, el celular podría ser una caja de la empresa
  y no de una sucursal. Eso lo decide el POS. Mientras, guardar los enlaces por `base` + `sucursalId` deja el camino
  abierto en cualquiera de los dos casos.

## Entregas

| # | Qué | Depende de |
|---|---|---|
| 1 | Enlazar el celular a la segunda sucursal con el alta de hoy (ya se puede) | Nada |
| 2 | Almacén de enlaces (`conexion.xml` + `conexion-2.xml` + `enlaces.xml`) y copia por caja, con la migración de la copia y pruebas de que un celular con una sola alta sigue igual | Nada |
| 3 | `Conexion.usar(enlace)`, `olvidarAlta` por enlace, `hayAlta` con cualquiera; pruebas del cambio con algo en vuelo (`generacion`) | 2 |
| 4 | Ajustes › La conexión: la lista, «Usar esta», «Conectar a otra sucursal» (el alta que agrega) y el aviso de duplicado por `base` + `sucursalId`. Se prueba de verdad cuando se instale la matriz | 3 |
| 5 | La sugerencia en la vista de «no contesta» y el indicador de la sucursal activa; la pregunta del ticket a medias | 4 |
| 6 | (Opcional) La sugerencia por UDP con `Identidad` contra los otros enlaces | 5 y uso real |

## Decidido

- **El ticket a medias al cambiar:** se pregunta si guardarlo (se lo lleva a la otra sucursal) o mandarlo a una caja de
  la sucursal de la que se va.
- **El nombre de la caja:** cada sucursal le pone el suyo. El paso 3 del alta lo pide en cada enlace, como hoy, y la
  serie es la que esté libre en esa base. Que es el mismo celular lo dice `equipo` (el modelo), no el nombre. Cuando las
  bases se unan con el servidor, se decide ahí qué nombre se queda.
