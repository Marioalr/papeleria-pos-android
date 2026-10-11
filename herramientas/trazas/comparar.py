"""Compara las medianas de Macrobenchmark de varias corridas: comparar.py <etiqueta>=<carpeta o JSON> ...

Cada carpeta es la que se baja del celular (/sdcard/Android/media/mx.papeleriapos.benchmark/) o el JSON mismo.
"""
import glob
import json
import os
import sys

METRICAS = [
    ("timeToFullDisplayMs", "ya escaneando (TTFD)"),
    ("escaner.hastaPrimerCuadroFirstMs", "apertura → 1er análisis"),
    ("timeToInitialDisplayMs", "primer cuadro (TTID)"),
    ("escaner.cuadroAverageMs", "por cuadro"),
]


def cargar(ruta):
    if os.path.isdir(ruta):
        ruta = glob.glob(os.path.join(ruta, "**", "*benchmarkData.json"), recursive=True)[0]
    return {b["name"]: b["metrics"] for b in json.load(open(ruta))["benchmarks"]}


corridas = [(a.split("=", 1)[0], cargar(a.split("=", 1)[1])) for a in sys.argv[1:]]
casos = sorted({c for _, d in corridas for c in d})
for caso in casos:
    print(caso)
    for clave, nombre in METRICAS:
        celdas = []
        for etiqueta, d in corridas:
            m = d.get(caso, {}).get(clave)
            celdas.append(f"{etiqueta} {m['median']:6.1f} ({m['minimum']:.0f}–{m['maximum']:.0f})" if m else f"{etiqueta}      -")
        if any(not c.endswith("-") for c in celdas):
            print(f"   {nombre:24} " + "   ".join(celdas))
