# La 0.4.0 y la 0.4.2 en el Pixel (2026-11-09)

Si la conexión con el POS (entrega 2) y los arreglos de su revisión hacen más lenta la apertura. Las dos versiones van
**sin el alta** (como queda el celular entre pruebas). Medido igual que [la 0.3.0](../2026-11-07-0.3.0/LEEME.md).

- **Equipo:** Pixel 8 Pro, Android 17, `CP3A.261005.005`, cargando por USB, batería 50–58 %, estado térmico 0 y piel
  debajo de 36 °C antes de cada corrida.
- **Versiones** (`benchmarkRelease`, el perfil de arranque generado en el Pixel el 2026-11-07):
  - **A:** 0.4.0, `0d46e5e`.
  - **B y B2:** 0.4.2, `7d70990` (los 9 hallazgos de [la revisión](../../docs/revision-entrega-2.md)), dos corridas
    seguidas para ver el ruido.
- **Corrida:** `AperturaBenchmark#frioConPerfil` y `#calienteConPerfil`, 15 iteraciones. Como el 2026-11-07, en frío
  Macrobenchmark guarda una sola iteración: los números de frío salen de las trazas (`hitos-frio-*.csv`, sin las que no
  traen el primer análisis). APK y trazas en `~/Documents/TPV/mediciones-pixel8pro/2026-11-09-0.4.0/` (fuera de git).

| Con perfil, medianas (rango) | A: 0.4.0 | B: 0.4.2 | B2: 0.4.2 otra vez |
|---|---|---|---|
| Frío: primer cuadro analizado | 465 ms (435–578), n 13 | 489 ms (424–564), n 15 | 455 ms (430–476), n 13 |
| Caliente: TTID | 60 ms | 62 ms | — |
| Caliente: hasta el primer cuadro analizado | 254 ms | 260 ms | — |

- **La apertura no empeoró.** La diferencia de B contra A (+24 ms) no se repitió en B2 (−10 ms): es ruido. La 0.3.0
  midió 453 ms el 2026-11-07.
- El costo por cuadro (3.8 y 5.6 ms en caliente) depende de lo que ve la cámara en el escritorio, no del código.
- Falta medir la apertura **con el alta** (la app abre bloqueada con la huella) y cuánto tarda en armarse la copia de la
  tienda real. Con la base ficticia (1458 productos), el alta y la copia tardaron unos 3 s por USB.
