"""Medianas de los hitos de la apertura por caso de Macrobenchmark (herramientas/trazas.sh)."""
import csv
import statistics
import sys

COLUMNAS = ["proceso", "actividad", "enlace", "abrir_camara", "camara_abierta", "cuadro_gpu_max", "sesion",
            "sesion_lista", "primer_analisis", "cuadros_gpu_antes"]


def numero(x):
    try:
        return float(x)
    except ValueError:
        return None


casos = {}
for fila in csv.reader(open(sys.argv[1])):
    # AperturaBenchmark_<caso>_iterNNN_<fecha>.perfetto-trace
    caso = fila[0].split("_")[1]
    casos.setdefault(caso, []).append(dict(zip(COLUMNAS, map(numero, fila[1:]))))


def mediana(filas, f):
    valores = [v for v in (f(x) for x in filas) if v is not None]
    return statistics.median(valores) if valores else float("nan")


def resta(a, b):
    return lambda x: None if x[a] is None or x[b] is None else x[a] - x[b]


print("ms desde el lanzamiento, medianas"
      " | espera: la cámara abierta esperando la sesión (la superficie del visor)")
print(f"{'caso':20} {'n':>3} {'proceso':>8} {'abrir':>6} {'abierta':>8} {'sesión':>7} {'lista':>6} "
      f"{'1er análisis':>12} {'espera':>7} {'gpu máx':>8}")
for caso, filas in sorted(casos.items()):
    print(f"{caso:20} {len(filas):3d} {mediana(filas, lambda x: x['proceso']):8.0f} "
          f"{mediana(filas, lambda x: x['abrir_camara']):6.0f} {mediana(filas, lambda x: x['camara_abierta']):8.0f} "
          f"{mediana(filas, lambda x: x['sesion']):7.0f} {mediana(filas, lambda x: x['sesion_lista']):6.0f} "
          f"{mediana(filas, lambda x: x['primer_analisis']):12.0f} {mediana(filas, resta('sesion', 'camara_abierta')):7.0f} "
          f"{mediana(filas, lambda x: x['cuadro_gpu_max']):8.0f}")
