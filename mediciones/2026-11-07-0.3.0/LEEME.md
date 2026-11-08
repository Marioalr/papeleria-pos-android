# La 0.3.0 y su perfil de arranque en el Pixel (2026-11-07)

La primera medición de la fase 2 en el Pixel 8 Pro: la pantalla de los tres modos (0.2.0 y 0.3.0) contra la 0.1.0 que
tenía instalada, y el perfil de arranque viejo (el de la 0.1) contra uno nuevo generado en el mismo Pixel.

- **Equipo:** Pixel 8 Pro, Android 17, `CP3A.261005.005` (otra compilación que la de octubre), cargando por USB,
  «Permanecer activo», estado térmico 0 y piel debajo de 36 °C antes de cada versión.
- **Versiones** (`benchmarkRelease`, misma firma de depuración, una tras otra en la misma sesión):
  - **A:** 0.1.0, `sin-red.apk` del PR #12 (la que se dejaba instalada desde el 2026-10-11).
  - **B:** 0.3.0, `a60c02a` (main con el PR #24), con el perfil de la 0.1.
  - **C:** 0.3.0, el mismo código con el perfil generado hoy en el Pixel (el de este cambio).
- **Corrida:** `AperturaBenchmark#frioConPerfil` y `#calienteConPerfil`, 15 iteraciones por caso. Los APK y las trazas
  están en `~/Documents/TPV/mediciones-pixel8pro/2026-11-07-0.3.0/` (fuera de git).

En frío, Macrobenchmark guardó las métricas de una sola de las 15 iteraciones (las 15 trazas sí están), en las tres
versiones. Por eso los números de frío salen de las trazas con `herramientas/trazas.sh` (`hitos-frio-*.csv`). Algunas
trazas no traen el primer análisis y quedan fuera: n = 15, 12 y 14. En caliente las métricas sí están completas.

| Frío, con perfil, medianas (rango) | A: 0.1.0 | B: 0.3.0, perfil 0.1 | C: 0.3.0, perfil Pixel |
|---|---|---|---|
| Proceso (`bindApplication`) | 89 ms | 88 ms | 88 ms |
| Abrir la cámara | 226 ms | 192 ms | 211 ms |
| Primer cuadro analizado (ya escaneando) | 523 ms (475–604) | 467 ms (431–512) | **453 ms** (430–493) |

| Caliente, con perfil, medianas | A | B | C |
|---|---|---|---|
| Primer cuadro de la interfaz (TTID) | 41 ms | 59 ms | 58 ms |
| Hasta el primer cuadro analizado | 244 ms | 244 ms | 248 ms |
| Por cuadro | 7.2 ms | 2.8 ms | 2.7 ms |

- La pantalla nueva no hace más lento abrir: en frío la 0.3.0 llega a escanear ~60 ms antes que la 0.1.0 en esta sesión.
  En caliente, su primer cuadro de interfaz cuesta ~17 ms más (compone más), pero escanea al mismo tiempo.
- El perfil nuevo trae la pantalla principal, el botón rápido y Ajustes (el de la 0.1 traía la pestaña del escáner, que
  ya no existe). Frente a B gana ~14 ms de mediana y el peor caso baja de 512 a 493 ms; es poco frente al ruido, pero
  ningún hito empeora.
- El por cuadro de A en caliente (7.2 ms, de 3 a 10) no se repitió en B ni en C y en octubre fue 2.4 ms: no se toma como
  diferencia de código.

**Cómo se generó el perfil:** el generador (`GeneradorPerfil`) corrió en el Pixel a mano con `am instrument`, porque
`:app:generateBaselineProfile` desinstala la app al terminar (docs/pruebas.md, «Arranque y perfil»). La tarea de
Gradle que junta los resultados no corre sin la prueba conectada; los dos archivos se juntaron con la misma regla que el
plugin: `baseline-prof.txt` es la unión de los dos recorridos (las banderas H, S y P de una misma regla se suman) y
`startup-prof.txt` es el recorrido de la apertura tal cual. Nadie apuntó la cámara a un código: el perfil no trae la
lectura ni la confirmación (el de la 0.1 se generó en el emulador con un cartel y sí las traía).
