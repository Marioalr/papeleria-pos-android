package mx.papeleriapos.escaner.nucleo

/**
 * Codificadores de códigos 1D a módulos (verdadero = barra), sin zonas en blanco. Sirven para dibujar códigos de prueba
 * (el banco de pruebas y las pruebas del localizador); son los mismos que imprime el POS en sus etiquetas
 * (`CodigosDeBarras.cs`), más EAN-8, UPC-A e ITF.
 */
object Barras {
    private val L = arrayOf("0001101", "0011001", "0010011", "0111101", "0100011", "0110001", "0101111", "0111011", "0110111", "0001011")
    private val G = arrayOf("0100111", "0110011", "0011011", "0100001", "0011101", "0111001", "0000101", "0010001", "0001001", "0010111")
    private val R = arrayOf("1110010", "1100110", "1101100", "1000010", "1011100", "1001110", "1010000", "1000100", "1001000", "1110100")
    private val PARIDAD = arrayOf("LLLLLL", "LLGLGG", "LLGGLG", "LLGGGL", "LGLLGG", "LGGLLG", "LGGGLL", "LGLGLG", "LGLGGL", "LGGLGL")

    /** Módulos en blanco que hay que dejar a cada lado para que un lector encuentre el código. */
    fun zonaEnBlanco(simbologia: Simbologia): Int = when (simbologia) {
        Simbologia.EAN_13, Simbologia.UPC_A -> 9
        Simbologia.EAN_8 -> 7
        else -> 10
    }

    fun ean13(codigo: String): BooleanArray {
        require(codigo.length == 13 && Gtin.esValido(codigo)) { "«$codigo» no es un EAN-13 válido." }
        val franjas = StringBuilder("101")
        val paridad = PARIDAD[codigo[0] - '0']
        for (i in 1..6) franjas.append((if (paridad[i - 1] == 'L') L else G)[codigo[i] - '0'])
        franjas.append("01010")
        for (i in 7..12) franjas.append(R[codigo[i] - '0'])
        franjas.append("101")
        return aModulos(franjas)
    }

    /** Un UPC-A es un EAN-13 que empieza con 0: las mismas barras. */
    fun upcA(codigo: String): BooleanArray {
        require(codigo.length == 12 && Gtin.esValido(codigo)) { "«$codigo» no es un UPC-A válido." }
        return ean13("0$codigo")
    }

    fun ean8(codigo: String): BooleanArray {
        require(codigo.length == 8 && Gtin.esValido(codigo)) { "«$codigo» no es un EAN-8 válido." }
        val franjas = StringBuilder("101")
        for (i in 0..3) franjas.append(L[codigo[i] - '0'])
        franjas.append("01010")
        for (i in 4..7) franjas.append(R[codigo[i] - '0'])
        franjas.append("101")
        return aModulos(franjas)
    }

    /** ITF (intercalado 2 de 5) con barras anchas de [ancho] módulos; el ITF-14 de las cajas lleva 14 dígitos. */
    fun itf(codigo: String, ancho: Int = 3): BooleanArray {
        require(codigo.isNotEmpty() && codigo.length % 2 == 0 && codigo.all { it in '0'..'9' }) {
            "«$codigo» no se puede poner en ITF: solo dígitos, en número par."
        }
        val patrones = arrayOf("nnwwn", "wnnnw", "nwnnw", "wwnnn", "nnwnw", "wnwnn", "nwwnn", "nnnww", "wnnwn", "nwnwn")
        val modulos = ArrayList<Boolean>()
        fun agrega(barra: Boolean, n: Int) = repeat(n) { modulos.add(barra) }
        // Inicio: barra, espacio, barra, espacio angostos.
        repeat(2) { agrega(true, 1); agrega(false, 1) }
        for (i in codigo.indices step 2) {
            val barras = patrones[codigo[i] - '0']
            val espacios = patrones[codigo[i + 1] - '0']
            for (k in 0 until 5) {
                agrega(true, if (barras[k] == 'w') ancho else 1)
                agrega(false, if (espacios[k] == 'w') ancho else 1)
            }
        }
        // Fin: barra ancha, espacio y barra angostos.
        agrega(true, ancho); agrega(false, 1); agrega(true, 1)
        return modulos.toBooleanArray()
    }

    private val PATRONES_128 = arrayOf(
        "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
        "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
        "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
        "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
        "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
        "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
        "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
        "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
        "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
        "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
        "114131", "311141", "411131", "211412", "211214", "211232", "2331112",
    )

    /** Los símbolos de Code 128 con inicio, verificación y fin; los dígitos van de dos en dos (juego C), como en el POS. */
    fun simbolos128(texto: String): List<Int> {
        require(texto.isNotEmpty() && texto.all { it in ' '..'~' }) {
            "«$texto» no se puede poner en Code 128: solo letras, números y signos sin acento."
        }
        fun digitos(desde: Int): Int {
            var n = 0
            while (desde + n < texto.length && texto[desde + n] in '0'..'9') n++
            return n
        }
        val simbolos = ArrayList<Int>()
        val inicio = digitos(0)
        var enC = (if (inicio == texto.length) inicio >= 2 else inicio >= 4) && inicio % 2 == 0
        simbolos.add(if (enC) 105 else 104)
        var i = 0
        while (i < texto.length) {
            var n = digitos(i)
            val conviene = n >= 4 || (n >= 2 && i + n == texto.length)
            if (conviene && n >= 2) {
                if (!enC) {
                    if (n % 2 == 1) {
                        simbolos.add(texto[i] - ' '); i++; n--
                    }
                    simbolos.add(99); enC = true
                }
                while (n >= 2) {
                    simbolos.add((texto[i] - '0') * 10 + (texto[i + 1] - '0')); i += 2; n -= 2
                }
                continue
            }
            if (enC) {
                simbolos.add(100); enC = false
            }
            simbolos.add(texto[i] - ' '); i++
        }
        var suma = simbolos[0]
        for (p in 1 until simbolos.size) suma += simbolos[p] * p
        simbolos.add(suma % 103)
        simbolos.add(106)
        return simbolos
    }

    fun code128(texto: String): BooleanArray {
        val modulos = ArrayList<Boolean>()
        for (simbolo in simbolos128(texto)) {
            var barra = true
            for (ancho in PATRONES_128[simbolo]) {
                repeat(ancho - '0') { modulos.add(barra) }
                barra = !barra
            }
        }
        return modulos.toBooleanArray()
    }

    /** Los módulos del código según su simbología (las 1D que el banco de pruebas sabe dibujar). */
    fun para(simbologia: Simbologia, texto: String): BooleanArray = when (simbologia) {
        Simbologia.EAN_13 -> ean13(texto)
        Simbologia.EAN_8 -> ean8(texto)
        Simbologia.UPC_A -> upcA(texto)
        Simbologia.ITF -> itf(texto)
        Simbologia.CODE_128 -> code128(texto)
        else -> throw IllegalArgumentException("No se dibuja ${simbologia.nombre}.")
    }

    private fun aModulos(franjas: CharSequence) = BooleanArray(franjas.length) { franjas[it] == '1' }
}
