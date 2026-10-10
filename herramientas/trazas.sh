#!/usr/bin/env bash
# Los hitos de la apertura en las trazas de Perfetto de Macrobenchmark (docs/pruebas.md, «Arranque y perfil»).
#
#   herramientas/trazas.sh <carpeta-con-las-trazas> [serie-del-equipo]
#
# El trace_processor_shell que trae Macrobenchmark (en el caché de Gradle) es un binario de Android: corre en un equipo
# conectado, de preferencia el emulador (no hace falta que tenga cámara). Sube el binario, las trazas y la consulta a
# /data/local/tmp/trazas-escaner, deja hitos.csv en la carpeta, imprime las medianas por caso y borra lo que subió.
set -euo pipefail

carpeta=$(realpath "${1:?Falta la carpeta con las trazas (*.perfetto-trace)}")
serie=${2:-${ANDROID_SERIAL:-emulator-5570}}
sdk=${ANDROID_HOME:-$HOME/Android/Sdk}
adb="$sdk/platform-tools/adb -s $serie"
raiz=$(cd "$(dirname "$0")/.." && pwd)
remoto=/data/local/tmp/trazas-escaner

case "$($adb shell getprop ro.product.cpu.abi | tr -d '\r')" in
    x86_64) abi=x86_64 ;;
    arm64-v8a) abi=aarch64 ;;
    *) echo "ABI no soportada" >&2; exit 1 ;;
esac
binario=$(find "$HOME/.gradle/caches" -path "*benchmark-common*/assets/trace_processor_shell_$abi" 2>/dev/null | head -1)
[[ -n "$binario" ]] || { echo "No está trace_processor_shell en el caché de Gradle: ./gradlew :benchmark:assembleBenchmarkRelease" >&2; exit 1; }

$adb shell mkdir -p "$remoto"
trap '$adb shell rm -rf "$remoto"' EXIT
$adb push "$binario" "$remoto/tp" >/dev/null 2>&1
$adb shell chmod 755 "$remoto/tp"
$adb push "$raiz/herramientas/trazas/hitos.sql" "$remoto/" >/dev/null 2>&1
$adb push "$carpeta"/*.perfetto-trace "$remoto/" >/dev/null 2>&1
$adb shell "cd $remoto; for f in *.perfetto-trace; do printf '%s,' \"\$f\"; ./tp -q hitos.sql \"\$f\" 2>/dev/null | tail -1; done" \
    | tr -d '\r' >"$carpeta/hitos.csv"
python3 "$raiz/herramientas/trazas/resumen.py" "$carpeta/hitos.csv"
