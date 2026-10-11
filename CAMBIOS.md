# Cambios

Qué trae cada versión de la app ([versiones](docs/plan.md#versiones)). La más nueva va arriba.

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
