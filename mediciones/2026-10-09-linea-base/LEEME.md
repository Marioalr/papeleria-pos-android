# Línea base en el Pixel 8 Pro (2026-10-09)

La primera medición en el equipo de referencia: contra esta se comparan los experimentos de la entrega 7
([plan](../../docs/plan.md#entrega-7-lo-máximo-en-el-pixel-8-pro)). No se borra ni se reemplaza.

- **Equipo:** Pixel 8 Pro (husky), Android 17, `CP3A.260905.009`, cargando, «Permanecer activo» encendido, estado
  térmico ligero (1) al empezar.
- **App:** `benchmarkRelease` (R8 y perfil de arranque), versión 0.1.0, armada del commit `fd439ff` (el código de la PR
  #5 antes del arreglo del banco «Límites», que no toca la apertura). Perfil generado en el emulador.
- **Corrida:** `AperturaBenchmark`, 15 iteraciones por caso, instalada y corrida a mano ([pruebas](../../docs/pruebas.md#arranque-y-perfil)).
  Macrobenchmark borra la caché de shaders antes de cada arranque frío.
- **Archivos:** `benchmarkData.json` (lo que escribe Macrobenchmark) y `hitos.csv` (de `herramientas/trazas.sh`). Las
  90 trazas de Perfetto (537 MB) no van en git. Están en `~/Documents/TPV/mediciones-pixel8pro/2026-10-09-linea-base/`
  en la PC del dueño.

| Medianas | Sin perfil | Con perfil |
|---|---|---|
| Frío: ya escaneando (TTFD) | 913 ms | 629 ms |
| Tibio: ya escaneando | 431 ms | 386 ms |
| Caliente: ya escaneando | 407 ms | 278 ms |
| Frío: cámara abierta esperando la sesión | 195 ms | 160 ms |
| Por cuadro: total | 3.3 ms | 2.4 ms |
