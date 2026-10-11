# A/B: el visor con la barra fija contra el visor primero (2026-10-10)

¿La versión que arregla el reenlace (`a38573a`: la barra de pestañas guarda su alto, 30 cuadros por omisión) abre igual
de rápido que la del [visor primero](../2026-10-09-visor-primero/LEEME.md) (`86d0a93`)? Medidas una tras otra en la
misma sesión: A = visor primero, B = barra fija, A2 = visor primero otra vez. Los tres arranques con perfil, 15
iteraciones cada uno. Cada corrida empezó con el Pixel en estado térmico 0: A con la piel a 31 °C, B y A2 a 36 °C.
Cada una lo calentó hasta 40–43 °C.

| Medianas | A | B (barra fija) | A2 |
|---|---|---|---|
| Frío: ya escaneando (TTFD) | 487 ms | 513 ms | 498 ms |
| Frío: primer cuadro (TTID) | 207 ms | 230 ms | 205 ms |
| Frío: primer cuadro analizado (trazas) | 447 ms | 454 ms | 444 ms |
| Tibio: ya escaneando | 328 ms | 354 ms | 322 ms |
| Caliente: primer cuadro analizado | 262 ms | 267 ms | 267 ms |
| **El mismo código en las tres:** enlace de CameraX en frío | 6.1 ms | 8.1 ms | 6.6 ms |
| **El mismo código:** `bindApplication` en frío | 41.7 ms | 48.7 ms | 45.4 ms |

**Conclusión:** durante B el teléfono iba ~15 % más lento, como se ve en el código que es idéntico en las tres
versiones. Los 15–25 ms que B queda atrás son de ese orden, y A contra A2 (el mismo APK) ya varía ~10 ms. No hay una
diferencia que se pueda atribuir al cambio. La barra fija se queda porque quita el reenlace que tenía el visor primero
fuera de Macrobenchmark, y es la que queda instalada en el Pixel.

**Aprendido para medir:** una corrida de tres arranques calienta el Pixel de 31 a 40–43 °C en ~10 minutos. Se compara
lo del mismo orden en cada corrida y se revisa el código idéntico (enlace de CameraX, `bindApplication`) para saber si
el teléfono iba igual de rápido. Herramienta: `herramientas/trazas/comparar.py`. Los APK y las trazas están en
`~/Documents/TPV/mediciones-pixel8pro/2026-10-10-ab/`.
