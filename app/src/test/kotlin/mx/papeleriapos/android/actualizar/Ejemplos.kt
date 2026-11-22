package mx.papeleriapos.android.actualizar

/** Lo que contesta GitHub, de mentira: una versión 0.5.1 con sus dos APK, como la publica GitHub Actions. */
internal object Ejemplos {
    const val NOMBRE = "escaner-0.5.1-a1b2c3d.apk"

    /** 2026-11-28T18:30:05Z. */
    const val PUBLICADA = 1_795_890_605_000L

    fun json(
        etiqueta: String = "v0.5.1",
        publicada: String = "2026-11-28T18:30:05Z",
        huella: String? = "sha256:" + "ab".repeat(32),
        url: String = "https://github.com/Marioalr/papeleria-pos-android/releases/download/v0.5.1/$NOMBRE",
        tamano: Long = 15_404_054,
        archivos: String = """
            {"name": "escaner-0.5.1-a1b2c3d-depuracion.apk", "size": 31000000, "browser_download_url": "https://github.com/Marioalr/papeleria-pos-android/releases/download/v0.5.1/escaner-0.5.1-a1b2c3d-depuracion.apk", "digest": "sha256:${"cd".repeat(32)}"},
            {"name": "$NOMBRE", "size": $tamano, "browser_download_url": "$url", "digest": ${if (huella == null) "null" else "\"$huella\""}, "download_count": 3}
        """,
    ): String = """
        {
          "url": "https://api.github.com/repos/Marioalr/papeleria-pos-android/releases/1",
          "tag_name": "$etiqueta",
          "name": "$etiqueta",
          "draft": false,
          "prerelease": false,
          "published_at": "$publicada",
          "author": {"login": "github-actions[bot]"},
          "assets": [$archivos],
          "body": "Dos detalles de la venta.\n\n- **El teclado de la cantidad:** acepta medias piezas, como\n  la caja.\n  - Solo en lo que se vende a granel.\n- Buscar recuerda lo último que se eligió ([pruebas](docs/pruebas.md)).\n"
        }
    """.trimIndent()

    fun version(tamano: Long = 12, sha256: String? = null, numero: Version = Version(0, 5, 1)) = VersionPublicada(
        version = numero,
        notas = listOf("Dos detalles de la venta.", "• El teclado de la cantidad acepta medias piezas."),
        publicada = PUBLICADA,
        apk = ApkPublicado("escaner-$numero-a1b2c3d.apk", tamano, "https://github.com/Marioalr/papeleria-pos-android/releases/download/v$numero/escaner-$numero-a1b2c3d.apk", sha256),
    )
}
