# Hasta 60 cuadros por segundo (2026-10-10)

El experimento 4 del [plan](../../docs/plan.md#entrega-7-lo-máximo-en-el-pixel-8-pro): ¿abre antes y escanea mejor a 60
cuadros por segundo? Medido igual que la [línea base](../2026-10-09-linea-base/LEEME.md), contra
[el visor desde el primer cuadro](../2026-10-09-visor-primero/LEEME.md).

- **Cambio:** rango [60, 60] (el Pixel lo acepta a 1280x720 con vista previa y análisis), más la barra de pestañas con
  su alto guardado (que no cambia la medición: en las corridas no hubo reenlaces).
- **Equipo:** el mismo Pixel 8 Pro, Android 17, cargando, estado térmico 0 al empezar.
- **App:** `benchmarkRelease` del commit `9964720`. APK y trazas en
  `~/Documents/TPV/mediciones-pixel8pro/2026-10-10-60fps/`.

| Con perfil, medianas | Visor primero (30) | 60 cuadros | Cambio |
|---|---|---|---|
| Frío: ya escaneando (TTFD) | 532 ms | 562 ms (483–669) | +30 ms, más disperso |
| Frío: primer cuadro analizado (trazas, desde el lanzamiento) | 479 ms | 491 ms | igual (ruido) |
| Tibio: ya escaneando | 386 ms | 383 ms | igual |
| Caliente: primer cuadro analizado | 279 ms | 253 ms | −26 ms |
| De la sesión lista al primer cuadro (trazas) | 146–158 ms | 132–154 ms | ~−15 ms |
| Por cuadro | 2.4 ms (frío), 3.7 (tibio) | 3.9, 7.2 | más caro, y el doble de cuadros |

**Conclusión:** el HAL no tarda en dar el primer cuadro por el ritmo de cuadros sino por armar su proceso, así que 60
cuadros solo quita ~15 ms. En caliente gana 26 ms, en frío no gana, y cada cuadro cuesta más (más del doble de CPU en
total). No se queda como predeterminado. Queda en ajustes («Hasta 60 cuadros por segundo») para ver en la tienda si
lee mejor los códigos en movimiento (exposición más corta).
