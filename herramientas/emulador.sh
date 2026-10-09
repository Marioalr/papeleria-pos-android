#!/usr/bin/env bash
# Abre la app en el emulador con una imagen como cámara trasera (docs/pruebas.md, «En el emulador»).
#
#   herramientas/emulador.sh <cartel.png> [puerto]
#
# Arranca el AVD sin ventana y de solo lectura (no ensucia el AVD ni choca con otro emulador abierto), instala el APK de
# depuración, da el permiso de la cámara y abre la app. Para apagarlo: adb -s emulator-<puerto> emu kill
set -euo pipefail

cartel=$(realpath "${1:?Falta el cartel: herramientas/emulador.sh <cartel.png> [puerto]}")
puerto=${2:-5570}
avd=${AVD:-Pixel_8_Pro}
sdk=${ANDROID_HOME:-$HOME/Android/Sdk}
adb="$sdk/platform-tools/adb -s emulator-$puerto"
raiz=$(cd "$(dirname "$0")/.." && pwd)
apk="$raiz/app/build/outputs/apk/debug/app-debug.apk"
paquete=mx.papeleriapos.android

[[ -f "$apk" ]] || { echo "Falta el APK: ./gradlew :app:assembleDebug" >&2; exit 1; }

if ! $adb get-state >/dev/null 2>&1; then
    echo "Arrancando $avd en el puerto $puerto con $cartel…"
    "$sdk/emulator/emulator" -avd "$avd" -read-only -no-window -no-audio -no-snapshot -no-boot-anim \
        -port "$puerto" -gpu swiftshader_indirect -camera-back "imagefile:$cartel" \
        >"${TMPDIR:-/tmp}/emulador-$puerto.log" 2>&1 &
    $adb wait-for-device
    until [[ "$($adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == 1 ]]; do sleep 2; done
else
    echo "El emulador del puerto $puerto ya está abierto (su cámara es la imagen con que arrancó)."
fi

$adb install -r -g "$apk" >/dev/null
$adb shell pm grant "$paquete" android.permission.CAMERA
# SIN_ABRIR=1: solo instala (herramientas/e2e.sh abre la app después de limpiar el registro).
[[ -n "${SIN_ABRIR:-}" ]] || $adb shell am start -W -n "$paquete/.MainActivity" >/dev/null
echo "Listo. Registro: $adb logcat -s Escaner   Captura: $adb exec-out screencap -p > captura.png"
