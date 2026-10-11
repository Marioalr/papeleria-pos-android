#!/usr/bin/env bash
# La app de punta a punta en el emulador, un cartel a la vez (docs/pruebas.md, «En el emulador»).
#
#   herramientas/e2e.sh <carpeta-de-salida> [cartel.png …]
#
# Sin carteles usa todos los de nucleo/build/carteles (./gradlew :nucleo:carteles). Por cada uno arranca el emulador con
# esa imagen como cámara, abre la app, espera y guarda la captura y lo que dijo el escáner (lecturas e indicaciones) en
# <salida>/<cartel>.png y <salida>/<cartel>.txt; al final, un resumen.
set -euo pipefail

salida=$(realpath -m "${1:?Falta la carpeta de salida}")
shift
raiz=$(cd "$(dirname "$0")/.." && pwd)
puerto=${PUERTO:-5570}
espera=${ESPERA:-8}
sdk=${ANDROID_HOME:-$HOME/Android/Sdk}
adb="$sdk/platform-tools/adb -s emulator-$puerto"
mkdir -p "$salida"
carteles=("$@")
[[ ${#carteles[@]} -gt 0 ]] || carteles=("$raiz"/nucleo/build/carteles/*.png)

: >"$salida/resumen.txt"
for cartel in "${carteles[@]}"; do
    nombre=$(basename "$cartel" .png)
    $adb emu kill >/dev/null 2>&1 || true
    while $adb get-state >/dev/null 2>&1; do sleep 1; done
    SIN_ABRIR=1 "$raiz/herramientas/emulador.sh" "$cartel" "$puerto" >/dev/null
    $adb logcat -c
    $adb shell am start -W -n mx.papeleriapos.android/.MainActivity >/dev/null
    sleep "$espera"
    $adb exec-out screencap -p >"$salida/$nombre.png"
    $adb logcat -d -v time -s Escaner >"$salida/$nombre.txt" || true
    leidos=$(grep -o 'Leído [^,]*' "$salida/$nombre.txt" | sort | uniq -c | tr -s ' ' | paste -sd ';' - || true)
    indicaciones=$(grep -o 'Indicación: [A-Z_]*' "$salida/$nombre.txt" | sed 's/Indicación: //' | paste -sd '>' - || true)
    printf '%-22s leídos: %s\n%-22s indicaciones: %s\n' "$nombre" "${leidos:-ninguno}" "" "${indicaciones:-ninguna}" | tee -a "$salida/resumen.txt"
done
$adb emu kill >/dev/null 2>&1 || true
