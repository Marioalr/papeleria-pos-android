# El visor desde el primer cuadro (2026-10-09)

El primer cambio de la entrega 7 ([plan](../../docs/plan.md#entrega-7-lo-máximo-en-el-pixel-8-pro)), medido igual que
la [línea base](../2026-10-09-linea-base/LEEME.md) para compararlo.

- **Cambio:** el primer cuadro trae solo el visor; lo demás de la interfaz llega cuando el visor ya entregó su
  superficie a CameraX. Antes, la cámara abierta esperaba a que se midiera y dibujara toda la interfaz.
- **Equipo:** el mismo Pixel 8 Pro, Android 17, `CP3A.260905.009`, cargando, «Permanecer activo», estado térmico 0 al
  empezar.
- **App:** `benchmarkRelease` del commit `86d0a93`, versión 0.1.0, el mismo perfil de arranque que la línea base.
- **Corrida:** `AperturaBenchmark`, 15 iteraciones por caso. El APK medido y las 90 trazas están en
  `~/Documents/TPV/mediciones-pixel8pro/2026-10-09-visor-primero/` (fuera de git).

| Con perfil, medianas | Línea base | Visor primero | Cambio |
|---|---|---|---|
| Frío: ya escaneando (TTFD) | 629 ms (583–700) | **532 ms** (515–576) | −97 ms (−15 %) |
| Frío: primer cuadro en pantalla (TTID) | 225 ms | 239 ms | +14 ms: el primer cuadro ya trae el visor |
| Frío: cámara abierta esperando la sesión | 160 ms | 42 ms | −118 ms |
| Tibio: ya escaneando | 386 ms | 386 ms | igual |
| Caliente: ya escaneando | 278 ms | 279 ms | igual |
| Por cuadro | 2.4 ms | 2.4 ms | igual |

Sin perfil: frío 913 → 850 ms (−64). El primer cuadro sin perfil cuesta más (TTID 380 → 467 ms), porque compone más
sin código precompilado.

**Lo que queda en el camino** (`hitos.csv`): en tibio y en caliente la cámara ya no espera al visor. El límite ahora es
el HAL del Pixel, que tarda ~150 ms en dar el primer cuadro después de la sesión. En frío también, más la apertura de la
cámara, que empieza a los ~190 ms del lanzamiento.
