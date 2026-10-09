# Pruebas

Cómo se prueba el escáner sin la tienda: en la PC de desarrollo (Linux), en el emulador y con cualquier código de barras
en un celular. Lo que solo se puede probar en la tienda (los celulares del personal, los productos y la luz de ahí) va
en [pruebas en tienda](pruebas-en-tienda.md), sábado y domingo.

Todos los comandos, desde la raíz del repositorio y con el JDK de Android Studio:

```
export JAVA_HOME=/opt/android-studio/jbr
```

## En la JVM (cualquier PC, también GitHub Actions)

```
./gradlew test
```

- **`nucleo`:** verificación de GTIN, codificadores (comprobados contra zbar), geometría y rotaciones, el localizador
  sobre escenas sintéticas (tamaños, giros, desenfoque, código cortado, escenas sin código), la guía (secuencias de
  cuadros: cada indicación, histéresis y que no parpadee) y la validación y el consenso (normalización, ITF de 14
  dígitos, dos lecturas iguales, repetidos con ausencia).
- **`app`:** la sesión de prueba y su CSV, el banco de pruebas y las listas de venta y conteo.

**Escenas sintéticas.** `Lienzo` (en `nucleo`) dibuja un código en un plano de luminancia como lo vería la cámara:
posición, tamaño, giro, perspectiva, desenfoque, movimiento, ruido, poca luz y reflejo. Lo usan las pruebas, el banco
de pruebas del celular y los carteles del emulador.

## En el emulador

El emulador puede usar una imagen como cámara trasera (`-camera-back imagefile:<png>`). Los carteles con códigos en
distintas situaciones se generan con:

```
./gradlew :nucleo:carteles      # PNG en nucleo/build/carteles
```

Para abrir la app con uno de ellos (arranca el emulador sin ventana en su propio puerto, instala el APK, da el permiso
de la cámara y abre el escáner):

```
herramientas/emulador.sh nucleo/build/carteles/ean13-centro.png
```

Lo que pasa se ve en el registro (`adb -s emulator-5570 logcat -s Escaner`) y en capturas
(`adb -s emulator-5570 exec-out screencap -p > captura.png`).

**Pruebas instrumentadas** (en el emulador o en un celular conectado):

```
./gradlew connectedDebugAndroidTest
```

- **Motores:** cada motor lee los códigos dibujados con el texto correcto, sus esquinas caen sobre el código, ML Kit avisa
  de los que ve sin leer y ninguno lee algo donde no hay código; mide la latencia con la franja y con el cuadro completo.
- **Pantalla del escáner:** cada estado y cada indicación muestran lo que deben (con un controlador de muestra, sin
  cámara), y capturas de cada estado para revisarlas a ojo.

Las latencias del emulador sirven para comparar, no como meta: las de verdad se miden en celulares.

## Con cualquier código de barras

Sin ir a la tienda: cualquier producto de la casa, las etiquetas del POS impresas o un código en otra pantalla.

1. Instalar el APK en un celular propio (`adb install -r …` o el APK de la corrida de GitHub Actions).
2. **Escanear** en modo venta y en modo conteo; en Ajustes se cambia el motor y se puede ver el diagnóstico (cuadros por
   segundo, milisegundos, px por módulo, nitidez, brillo).
3. **Banco de pruebas:** compara los dos motores en ese celular con escenas sintéticas, sin depender de la mano.
4. **Sesión de prueba:** registra cada intento con sus tiempos y exporta un CSV, igual que en la tienda.

## Rendimiento y batería

En un celular conectado:

```
adb shell top -b -n 1 -o PID,%CPU,RES,NAME | grep papeleriapos     # CPU y memoria escaneando
adb shell dumpsys batterystats --reset                             # antes de escanear 10–15 min
adb shell dumpsys batterystats --charged mx.papeleriapos.android   # después
```

Las secciones `escaner.cuadro`, `escaner.localizar` y `escaner.leer` aparecen en un rastreo de Perfetto o del perfilador
de Android Studio para ver cuánto tarda cada paso.
