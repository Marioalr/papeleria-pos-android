# CameraX, ML Kit y zxing-cpp traen sus reglas en sus AAR; aquí solo lo que les falta con R8 en modo completo.

# ML Kit arranca con los registradores de firebase-components, que crea por reflexión con el constructor vacío. La
# regla de su AAR conserva las clases pero no ese constructor (en modo completo una regla sin miembros no lo guarda):
# sin esto ML Kit no se inicializa en el release y ningún motor lee.
-keep class * implements com.google.firebase.components.ComponentRegistrar {
    <init>();
}
