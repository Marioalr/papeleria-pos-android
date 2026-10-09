# Cambios

Qué trae cada versión de la app ([versiones](docs/plan.md#versiones)). La más nueva va arriba.

## 0.1.0 (sin publicar)

Primera versión de prueba: el escáner solo, sin conexión al POS.

- Lee EAN-13 (también los internos que empiezan con 2), UPC-A, UPC-E, EAN-8, ITF-14, Code 128 y QR con la cámara
  trasera, con ML Kit o zxing-cpp.
- Guía al personal con una sola indicación a la vez: muévete a un lado, acércate, aléjate, mantén firme, enfocando,
  poca luz o reflejo.
- Confirma cada lectura con bip, vibración, retícula verde y el código en grande. Valida el dígito verificador y pide
  dos lecturas iguales; el mismo código vuelve a contar solo si se retira medio segundo.
- Modo venta (lista con +1, −1 y deshacer) y modo conteo (contador grande).
- Sesión de prueba con CSV para la prueba en la tienda, y banco de pruebas que compara los motores en cada celular.
