# Pruebas del escáner en la tienda

> Las pruebas reales en la matriz solo se pueden hacer **sábado y domingo** (la regla del POS, `docs/notas.md`,
> «Forma de trabajar»). Se hacen con el **Pixel 8 Pro**, el equipo de referencia ([plan](plan.md)): la tienda pone lo
> que no hay en casa, que son sus productos, su luz y su ruido. Entre semana se prepara todo y se prueba con cualquier
> código de barras ([pruebas](pruebas.md)). La app no toca eleventa ni la red de la tienda: solo usa la cámara.

## Entre semana, antes del fin de semana

- **El APK de release firmado** de la versión etiquetada (`vX.Y.Z`, el que adjunta la versión de GitHub), instalado en
  el Pixel, con la sesión de prueba y el banco funcionando y el CSV abriéndose bien en una hoja de cálculo. El de
  depuración es más lento y falsea los tiempos. Si el Pixel tenía el de depuración, hay que desinstalarlo antes (otra
  firma) y eso borra los datos de la app: exportar antes lo que importe.
- **El perfil de arranque compilado:** fuera de Google Play se compila hasta la noche con el celular cargando; para
  medir desde el primer día, con el cable: `adb shell cmd package compile -f -m speed-profile mx.papeleriapos.android`
  ([pruebas](pruebas.md#arranque-y-perfil)).
- **Una lista de productos de prueba** que estén en la tienda, por tipo de código (ver abajo), con su código esperado
  escrito a mano o sacado de la etiqueta.
- **El catálogo de la tienda en el Pixel** ([búsqueda en el catálogo](escaner.md#búsqueda-en-el-catálogo)):
  1. El dueño exporta los códigos de eleventa a un archivo de texto o CSV, con el código en la primera columna, fuera
     del repositorio.
  2. Lo pasa al Pixel con el cable (a Descargas). Ni por correo ni a la nube: son códigos de la tienda.
  3. En Ajustes → «Catálogo de prueba» → «Cargar archivo…» lo elige. Debe decir su nombre y cuántos códigos trae
     (casi tantos como productos tiene eleventa); si avisa algo (vacío, más de 8 MB), revisar la exportación.
  4. La app lo copia a su memoria privada: se puede borrar de Descargas.

  Nada se conecta a eleventa ni al servicio de la tienda, y los códigos reales no van al repositorio.
- **La laptop** con el cable, el repositorio y Android Studio, por si hay que ver el registro (`adb logcat -s Escaner`).
- **Un cronómetro o la cámara lenta de otro celular** (240 cuadros por segundo) para la parte humana de la apertura:
  del bolsillo al bip.

## En la tienda

**Al llegar (10 min):** batería, temperatura y versión del APK anotadas en la sesión; correr el **banco de pruebas**
(2–3 min) y exportar su CSV, que dice cómo le va a cada motor sin depender de la mano; abrir la **sesión de prueba**
con el nombre de quien escanea.

**Los intentos (1 a 2 h en total):**

- Mínimo **200 intentos**, alternando el motor cada 25 en orden ABBA (Automático, ML Kit, ML Kit, Automático…; el
  Automático ya incluye a zxing-cpp) y la mitad en modo venta y la mitad en modo conteo. El motor se cambia en Ajustes.
- **Qué códigos:**

  | Tipo | Ejemplos en la tienda |
  |---|---|
  | EAN-13 de fábrica (750…) | Cuadernos, pegamentos, plumones |
  | EAN-13 interno (2…) | Etiquetas que imprime el POS, la hoja de códigos del mostrador |
  | UPC-A | Productos importados |
  | UPC-E | Plumas, gomas y lápices con código chico |
  | EAN-8 | Productos chicos |
  | ITF-14 | Cajas de cartón corrugado de los proveedores |
  | Code 128 | Las claves con letras: hoy no están impresas; las etiquetas que imprima el POS y la [hoja de etiquetas](pruebas.md#hoja-de-etiquetas) de prueba |
  | QR | Un ticket del POS de prueba, si hay |

- **En qué condiciones:** con la luz normal del mostrador; en el rincón más oscuro (con y sin linterna); empaques
  brillantes con reflejo; superficies curvas (botes, plumas); códigos chicos o desde lejos; varios códigos juntos (una
  hoja de etiquetas); con la mano movida. Cada condición, unos 10 intentos.
- **El catálogo:** la sesión anota sola si cada lectura está en el catálogo (`en_catalogo`), por qué variante y con
  qué clave. Un producto de la tienda que sale «no» es un hallazgo (cómo lo guardó eleventa, o un verificador mal en el
  catálogo): se anota en la nota del intento y no se corrige. El panel de lecturas lo marca: «no está en el catálogo».
- **Marcar cada intento** en la sesión: lo normal es OK y no hay que tocar nada. Si leyó otro código, «lectura
  incorrecta» (es lo más grave: anotar qué producto era y qué código leyó; fotos, solo si el dueño lo decide y sin que salgan del
  celular); si leyó algo donde no había código,
  «falso positivo»; si contó dos veces la misma pieza, «duplicado»; si no leyó en unos 5 segundos, «no leyó».
  Sostener la misma pieza más de 1.5 s la cuenta otra vez (así está pensado): también se marca «duplicado», para medir
  cuántas veces pasa.
- **Al cerrar cada tanda** (al cambiar la condición), leer su resumen en la pestaña Sesión, debajo de los contadores:
  cuántos leyó bien y a qué distancia (si la cámara da el enfoque calibrado), la lectura típica y la p90, lecturas por
  minuto, falsas (deben ser 0), con y sin linterna, y por lente. Si algo sorprende, es el momento de repetir la tanda
  o anotarlo. Lo mismo sale en resumen.csv junto con los intentos al exportar; cada intento lleva además el lente, la
  cámara física, la resolución, los ms por cuadro y la distancia.
- **Que escanee también alguien del personal**, unos 20 intentos con el Pixel, y preguntarle (y anotarlo en la sesión):
  si supo de inmediato que ya estaba escaneando, si entendió las indicaciones (¿alguna confundió?), si la vibración se
  notaba con el ruido de la tienda (el bip viene apagado; si hace falta, se prueba encendiéndolo en Ajustes), y qué
  cambiaría.

**Apertura (15 min), con el PIN o la huella del Pixel:**

1. El mosaico «Escanear» entre los primeros de Ajustes rápidos, para que se vea con un solo deslizamiento.
2. Toque rápido del Pixel (doble toque en la parte de atrás → Abrir app → Escáner). Anotar si abre sin desbloquear.
3. Con el celular bloqueado en la bolsa, 10 veces cada camino: (a) mosaico, (b) Toque rápido, (c) desbloquear y tocar
   el ícono, (d) dejar el escáner abierto, apagar la pantalla y volver a encenderla. Cronometrar del bolsillo al bip;
   la sesión guarda además las marcas internas de cada apertura (tipo de arranque y ms de cada paso, en aperturas.csv).

**Batería y calor (20 min), con el monitor de recursos de la sesión:** sin cable, brillo fijo, y la batería entre 80 %
y 60 %. Primero 3 minutos sin escanear (en la pestaña Sesión: es la línea base), luego 10 a 15 minutos escaneando de
corrido. El costo de escanear es la potencia escaneando menos la de la línea base. Si da tiempo, repetir con cada motor
en orden ABBA. La sesión exporta recursos.csv (una fila por segundo: CPU, memoria, corriente, potencia, temperatura,
estado térmico, cuadros por segundo) y ficha-camara.csv (una fila por lente trasera: focal, sensor, desde dónde enfoca,
zoom, resoluciones; dice con qué cámara se midió) además de los intentos.

**Estrés (si sobra tiempo):** la pestaña Estrés: 200 ciclos de abrir y cerrar la cámara, la ráfaga de zoom y
linterna, 50 cambios de motor y 15 minutos de escaneo continuo. Cada prueba dice si pasa y exporta su CSV.

**Al terminar:** exportar los CSV de la sesión (intentos y resumen por tanda; recursos, aperturas y ficha de la cámara)
y del banco a mano: Compartir → guardar en el celular, y de ahí a la laptop con el cable. Ni por correo ni a la nube: llevan códigos de la tienda.

## Qué se espera

| Medida | Meta |
|---|---|
| Lecturas incorrectas aceptadas | **0** (si aparece una, no se libera la versión) |
| Intentos leídos en ≤ 2 s | ≥ 98 % |
| Del código a la vista a la lectura | p50 < 300 ms y p90 < 800 ms |
| Abrir la cámara (en frío) hasta que escanea | No peor que la línea base: 629 ms ([plan](plan.md#entrega-7-lo-máximo-en-el-pixel-8-pro)) |
| Del bolsillo (bloqueado) al bip, con el mosaico o el gesto | Lo más bajo posible: es lo que se compara entre caminos y entre versiones |
| CPU de la app escaneando | < 35 % de un núcleo |
| Indicaciones | Que quien escanea las entienda sin explicación |

## Después, entre semana

- Con los CSV: completar la línea base del Pixel (lo difícil con productos reales, del bolsillo al bip, batería), ver
  qué falla y en qué condición, y decidir qué experimento del [plan](plan.md#entrega-7-lo-máximo-en-el-pixel-8-pro) va
  primero. Lo que se cambie, con su porqué, en [CAMBIOS.md](../CAMBIOS.md).
- Con `en_catalogo` y `variante`: qué productos no se encontraron y cómo guarda eleventa los que sí (12 u 11 dígitos,
  UPC-E como UPC-A). Si el catálogo de la tienda ya no hace falta en el celular, «Usar el de ejemplo» borra la copia.
- El motor y los umbrales definitivos se deciden cuando el POS llegue a su fase de Android ([plan](plan.md)); aquí se
  junta la evidencia.
