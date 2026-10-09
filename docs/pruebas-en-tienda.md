# Pruebas del escáner en la tienda

> Las pruebas reales en la matriz solo se pueden hacer **sábado y domingo** (la regla del POS, `docs/notas.md`,
> «Forma de trabajar»). Entre semana se prepara todo y se prueba con cualquier código de barras fuera de la tienda
> ([pruebas](pruebas.md)); aquí va lo que necesita la tienda, en orden, para no improvisar ahí. La app no toca eleventa
> ni la red de la tienda: solo usa la cámara de cada celular.

## Entre semana, antes del fin de semana

- **El APK probado:** la versión etiquetada (`vX.Y.Z`) que armó GitHub Actions, instalada antes en un celular propio y
  en el emulador, con la sesión de prueba y el banco de pruebas funcionando y el CSV abriéndose bien en una hoja de
  cálculo.
- **Una lista de productos de prueba** que estén en la tienda, por tipo de código (ver abajo), con su código esperado
  escrito a mano o sacado de la etiqueta. Si se quiere sacar del catálogo, de una copia de solo lectura de la base, nunca
  de eleventa en uso.
- **La forma de pasar el APK a los celulares:** cable USB con `adb install` desde la laptop, o el archivo en una
  memoria o por mensaje. En cada celular hay que permitir «instalar apps desconocidas» para esa vía y quitarlo al
  terminar.
- **La laptop** con el cable, el repositorio y Android Studio por si hay que ver el registro (`adb logcat`) de un
  celular que falle.
- **El APK de release firmado** (el que adjunta la versión de GitHub), no el de depuración: el de depuración es más
  lento y falsea los tiempos. Fuera de Google Play el perfil de arranque se compila hasta la noche con el celular
  cargando; para medir desde el primer día, con el cable: `adb shell cmd package compile -f -m speed-profile
  mx.papeleriapos.android` ([pruebas](pruebas.md#arranque-y-perfil)).
- **Un cronómetro o la cámara lenta de otro celular** (240 cuadros por segundo) para la parte humana de la apertura:
  del bolsillo al bip.

## En la tienda

**Al llegar (15 min por celular):**

1. Anotar de cada celular del personal que participe: marca, modelo, versión de Android, si tiene linterna y cuánta
   batería trae. Pedir permiso a cada quien para instalar la app de prueba en su celular y desinstalarla al terminar.
2. Instalar el APK, abrirlo, dar el permiso de la cámara y correr el **banco de pruebas** (2–3 min): exportar su CSV.
   Mide cada motor en ese celular sin depender de la mano de nadie.
3. Abrir la **sesión de prueba** con el nombre del celular y de quien escanea.

**Los intentos (1 a 2 h en total):**

- Mínimo **200 intentos por celular**, alternando el motor cada 25 en orden ABBA (ML Kit, zxing-cpp, zxing-cpp, ML Kit…)
  y la mitad en modo venta y la mitad en modo conteo. El motor se cambia en Ajustes.
- **Qué códigos:**

  | Tipo | Ejemplos en la tienda |
  |---|---|
  | EAN-13 de fábrica (750…) | Cuadernos, pegamentos, plumones |
  | EAN-13 interno (2…) | Etiquetas que imprime el POS, la hoja de códigos del mostrador |
  | UPC-A | Productos importados |
  | UPC-E | Plumas, gomas y lápices con código chico |
  | EAN-8 | Productos chicos |
  | ITF-14 | Cajas de cartón corrugado de los proveedores |
  | Code 128 | Las claves de eleventa con letras (etiquetas viejas) |
  | QR | Un ticket del POS de prueba, si hay |

- **En qué condiciones:** con la luz normal del mostrador; en el rincón más oscuro (con y sin linterna); empaques
  brillantes con reflejo; superficies curvas (botes, plumas); códigos chicos o desde lejos; varios códigos juntos (una
  hoja de etiquetas); con la mano movida. Cada condición, unos 10 intentos.
- **Marcar cada intento** en la sesión: lo normal es OK y no hay que tocar nada. Si leyó otro código, «lectura
  incorrecta» (es lo más grave: anotar qué producto era y tomar foto del código); si leyó algo donde no había código,
  «falso positivo»; si contó dos veces la misma pieza, «duplicado»; si no leyó en unos 5 segundos, «no leyó».
- **Preguntar a quien escaneó** (y anotarlo en la sesión): si supo de inmediato que ya estaba escaneando, si entendió
  las indicaciones (¿alguna confundió?), si el bip y la vibración se notaban con el ruido de la tienda, y qué cambiaría.

**Apertura (15 min por celular), con el bloqueo con PIN o huella que use cada quien:**

1. Agregar el mosaico «Escanear» a Ajustes rápidos (botón en Ajustes de la app, o a mano en Android 12 o anterior) y
   dejarlo entre los primeros para que se vea con un solo deslizamiento.
2. Si la marca lo permite, configurar el gesto que abre una app: Samsung (doble pulsación del botón lateral → Apps →
   Escáner), Motorola (Quick Launch), Pixel (Toque rápido → Abrir app). Anotar si abre sin desbloquear.
3. Con el celular bloqueado en la bolsa, 10 veces cada camino: (a) mosaico, (b) gesto, (c) desbloquear y tocar el
   ícono, (d) dejar el escáner abierto, apagar la pantalla y volver a encenderla. Cronometrar del bolsillo al bip; la
   sesión guarda además las marcas internas de cada apertura (tipo de arranque y ms de cada paso, en aperturas.csv).
4. Anotar si el mosaico pidió el PIN (en algunas versiones de Android 14 lo pide) y si el escáner apareció sobre el
   bloqueo.

**Batería y calor (20 min por celular), con el monitor de recursos de la sesión:** sin cable, brillo fijo (el mismo en
todos), Wi-Fi y datos como los traiga cada quien, y la batería entre 80 % y 60 %. Primero 3 minutos sin escanear (en la
pestaña Sesión: es la línea base), luego 10 a 15 minutos escaneando de corrido. El costo de escanear es la potencia
escaneando menos la de la línea base. Si da tiempo, repetir con cada motor en orden ABBA. La sesión exporta
recursos.csv (una fila por segundo: CPU, memoria, corriente, potencia, temperatura, estado térmico, cuadros por
segundo) además de los intentos.

**Estrés (si sobra tiempo, en un solo celular):** la pestaña Estrés: 200 ciclos de abrir y cerrar la cámara, la
ráfaga de zoom y linterna, 50 cambios de motor y 15 minutos de escaneo continuo. Cada prueba dice si pasa y exporta su
CSV.

**Al terminar:** exportar los CSV de la sesión y del banco (Compartir → a la laptop o al correo), desinstalar la app de
los celulares del personal y quitar el permiso de apps desconocidas.

## Qué se espera

| Medida | Meta |
|---|---|
| Lecturas incorrectas aceptadas | **0** (si aparece una, no se libera la versión) |
| Intentos leídos en ≤ 2 s | ≥ 98 % |
| Del código a la vista a la lectura | p50 < 300 ms y p90 < 800 ms en el celular más barato |
| Abrir la cámara (en frío) hasta que escanea | < 1.5 s |
| Del bolsillo (bloqueado) al bip, con el mosaico o el gesto | Lo más bajo posible: es lo que se compara entre caminos y celulares |
| CPU de la app escaneando | < 35 % de un núcleo |
| Indicaciones | Que el personal las entienda sin explicación |

## Después, entre semana

- Con los CSV: elegir el motor por defecto (primero cero lecturas incorrectas, luego más lecturas al primer intento,
  luego menor p90; si empatan, el más chico, zxing-cpp), ajustar los umbrales de la guía y el zoom con lo que pasó en
  cada celular, y anotar en [CAMBIOS.md](../CAMBIOS.md) lo que se cambió y por qué.
- Si un celular falló en algo (no enfoca de cerca, la cámara tarda, se calienta), anotarlo con su modelo: va a la lista
  de celulares que sirven o no para escanear.
