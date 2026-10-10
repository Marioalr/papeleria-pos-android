#!/usr/bin/env python3
"""Genera los sonidos del escáner (escaner/src/main/res/raw/): el bip de lectura y el doble tono de error.

Solo usa la librería estándar y no tiene nada aleatorio: correrlo dos veces da los mismos bytes, así que los WAV se
cometen y este script es la receta para volver a hacerlos.

    python3 herramientas/sonidos.py

- bip.wav: 100 ms a 2.7 kHz, rampas de 5 ms. En 2.5-3 kHz resuena la bocina de casi cualquier celular, así que se oye
  fuerte sin subir el volumen; las rampas evitan el chasquido al empezar y al terminar.
- error.wav: dos tonos graves de 120 ms (520 y 390 Hz) con un poco de tercer armónico para que la bocina chica no
  los pierda. Se distingue del bip por tono, duración y número de golpes (no solo por el volumen).
"""

import math
import struct
import wave
from pathlib import Path

MUESTREO = 44_100
PICO = 0.80  # Un poco abajo del máximo para que el resampleo de SoundPool no recorte.
RAMPA_S = 0.005

RAIZ = Path(__file__).resolve().parent.parent
SALIDA = RAIZ / "escaner" / "src" / "main" / "res" / "raw"


def tono(frecuencia: float, segundos: float, tercer_armonico: float = 0.0) -> list[float]:
    """Un tono con rampas de subida y bajada en coseno (sin chasquidos)."""
    n = round(MUESTREO * segundos)
    rampa = round(MUESTREO * RAMPA_S)
    norma = 1.0 + tercer_armonico
    muestras = []
    for i in range(n):
        t = i / MUESTREO
        valor = math.sin(2 * math.pi * frecuencia * t)
        if tercer_armonico:
            valor += tercer_armonico * math.sin(2 * math.pi * 3 * frecuencia * t)
        valor /= norma
        if i < rampa:
            valor *= 0.5 - 0.5 * math.cos(math.pi * i / rampa)
        elif i >= n - rampa:
            valor *= 0.5 - 0.5 * math.cos(math.pi * (n - 1 - i) / rampa)
        muestras.append(valor)
    return muestras


def silencio(segundos: float) -> list[float]:
    return [0.0] * round(MUESTREO * segundos)


def escribir(nombre: str, muestras: list[float]) -> None:
    datos = b"".join(struct.pack("<h", round(max(-1.0, min(1.0, m)) * PICO * 32767)) for m in muestras)
    ruta = SALIDA / nombre
    ruta.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(ruta), "wb") as archivo:
        archivo.setnchannels(1)
        archivo.setsampwidth(2)
        archivo.setframerate(MUESTREO)
        archivo.writeframes(datos)
    print(f"{ruta.relative_to(RAIZ)}: {len(muestras) / MUESTREO * 1000:.0f} ms, {ruta.stat().st_size} bytes")


def main() -> None:
    escribir("bip.wav", tono(2700, 0.100))
    escribir("error.wav", tono(520, 0.120, 0.3) + silencio(0.050) + tono(390, 0.120, 0.3))


if __name__ == "__main__":
    main()
