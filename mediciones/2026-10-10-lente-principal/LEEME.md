# Solo la cámara principal (2026-10-10)

El experimento 3 del [plan](../../docs/plan.md#entrega-7-lo-máximo-en-el-pixel-8-pro): con una sola cámara física en
vez de la lógica, ¿el HAL da antes el primer cuadro? En el Pixel 8 Pro la lógica enciende tres sensores y arma un
proceso para cada uno.

- **Cambio:** `Lente.PRINCIPAL` por omisión, para medirlo. `CameraSelector.setPhysicalCameraId("2")`: la principal de
  6.9 mm con el sensor completo, elegida sola por focal y sensor.
- **Corrida:** los tres arranques con perfil, 15 iteraciones, en la misma sesión que el
  [A/B de la barra](../2026-10-10-ab-barra/LEEME.md). Empezó en estado térmico 0 (piel 36 °C) y terminó en estado 3
  (45 °C). APK y trazas en `~/Documents/TPV/mediciones-pixel8pro/2026-10-10-lente/`.

| Con perfil, medianas | Lógica (A2 / B del A/B) | Solo la principal |
|---|---|---|
| Frío: ya escaneando | 498 / 513 ms | 526 ms |
| Frío: apertura → primer análisis | 328 / 334 ms | 354 ms |
| Caliente: apertura → primer análisis | 267 / 267 ms | 268 ms |

**Por qué no gana:** en las trazas el HAL hace lo mismo con la física que con la lógica. Enciende los tres sensores
(`sensor-boitata`, `-kraken`, `-oksoko`) y arma los tres subgrafos (`cam_2`, `cam_3`, `cam_4`). CameraX abre la cámara
lógica y solo toma el flujo de la física, y el Pixel no deja abrir sus físicas como cámaras sueltas: solo anuncia la 0
y la 1. No se queda; por omisión vuelve a la del sistema.

Queda en ajustes («Lente») para lo que no es abrir. La gran angular enfoca a 2 cm (la principal desde 10.5 cm): falta
ver si lee mejor los códigos chicos de cerca.
