#!/usr/bin/env python3
"""
Genera TablaDeOrden.kt: el orden por nombre de la búsqueda del POS, sacado de ICU (la intercalación que usa .NET para es-MX
en Windows y en Linux), para que la búsqueda de Kotlin ordene igual sin depender del Collator de cada plataforma.

Usa la libicu de esta máquina (libicui18n.so.74 en Ubuntu 24.04, la misma con que ordena .NET en Linux): cada punto de los
rangos con sus elementos de intercalación de es_MX en fuerza secundaria (la letra y sus acentos; las mayúsculas no cuentan),
con las primarias y las secundarias cambiadas por su lugar (1 es el separador entre los dos niveles). Además, fuera de los
rangos, lo que ICU no cuenta (el BOM, los selectores de variante), y la clase de combinación de esas marcas y las de los
rangos, con la que ICU lee la ñ escrita como n + marca de abajo + tilde. Lo demás (otras escrituras, como el cirílico; el
ancho completo y el kana, que .NET reordena con reglas propias; la U+FFFD, que ICU pone al final) va después de todo, por su
número. Los emojis fuera del plano básico no llegan: el POS truena con ellos. El oráculo (pos/herramientas/oraculo) escribe
el orden de .NET para revisarla.

    python3 pos/herramientas/tabla_de_orden.py > pos/src/main/kotlin/mx/papeleriapos/pos/busqueda/TablaDeOrden.kt
"""
import ctypes
import sys

i18n = ctypes.CDLL("libicui18n.so.74")
uc = ctypes.CDLL("libicuuc.so.74")
V = "_74"


def f(lib, nombre, res, *args):
    fn = getattr(lib, nombre + V)
    fn.restype = res
    fn.argtypes = list(args)
    return fn


P16 = ctypes.POINTER(ctypes.c_uint16)
PI = ctypes.POINTER(ctypes.c_int)
ucol_open = f(i18n, "ucol_open", ctypes.c_void_p, ctypes.c_char_p, PI)
ucol_setStrength = f(i18n, "ucol_setStrength", None, ctypes.c_void_p, ctypes.c_int)
ucol_openElements = f(i18n, "ucol_openElements", ctypes.c_void_p, ctypes.c_void_p, P16, ctypes.c_int32, PI)
ucol_next = f(i18n, "ucol_next", ctypes.c_int32, ctypes.c_void_p, PI)
ucol_closeElements = f(i18n, "ucol_closeElements", None, ctypes.c_void_p)
u_charType = f(uc, "u_charType", ctypes.c_int8, ctypes.c_int32)
u_getVersion = f(uc, "u_getVersion", None, ctypes.POINTER(ctypes.c_uint8))
u_getIntPropertyValue = f(uc, "u_getIntPropertyValue", ctypes.c_int32, ctypes.c_int32, ctypes.c_int32)
UCHAR_LEAD_CANONICAL_COMBINING_CLASS = 0x1010
UCHAR_TRAIL_CANONICAL_COMBINING_CLASS = 0x1011

estado = ctypes.c_int(0)
COLL = ucol_open(b"es_MX", ctypes.byref(estado))
ucol_setStrength(COLL, 1)  # UCOL_SECONDARY: lo que hace .NET con CompareOptions.IgnoreCase

# Lo que puede traer el nombre de un producto: el latín con todos sus acentos y letras (también las del alfabeto fonético,
# como la ɡ de imprenta), los signos, los números, las fracciones, el griego (µ, π) y los símbolos (las flechas, las cajas, ⭐).
# Lo demás se ordena después de todo esto, por su número.
RANGOS = [
    (0x0000, 0x03FF),  # el latín básico, el de Europa, el fonético, los modificadores, los acentos y el griego
    (0x1AB0, 0x1AFF),  # más acentos
    (0x1D00, 0x1FFF),  # más letras fonéticas, más acentos, el latín de Vietnam y Gales, y el griego con acentos
    (0x2000, 0x2BFF),  # la puntuación, los índices, las monedas, los símbolos, las flechas, las cajas, las figuras
    (0x2C60, 0x2C7F),  # latín extendido C
    (0x2E00, 0x2E7F),  # más puntuación
    (0x3000, 0x303F),  # la puntuación de China y Japón, con su espacio (para .NET es un espacio; sus reglas no la tocan)
    (0xA720, 0xA7FF),  # latín extendido D
    (0xAB30, 0xAB6F),  # latín extendido E
    (0xFB00, 0xFB06),  # las ligaduras latinas (ﬁ)
    (0xFE20, 0xFE2F),  # las medias marcas
    (0xFFFC, 0xFFFC),  # el objeto (U+FFFD no: ICU la pone después de todo, como lo que no está aquí)
]
# Lo que .NET cambia con sus reglas (el kana y el ancho completo y medio, CompareOptions sin IgnoreKanaType ni IgnoreWidth):
# ahí el orden de es_MX no es el suyo, así que no va en la tabla ni entre lo que no cuenta.
REGLAS_DE_NET = [(0x3040, 0x30FF), (0xFF00, 0xFFEF)]
ALFABETO = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz+*"
SIN_ENTRADA = 63
BITS_SECUNDARIA = 10


def elementos(s):
    """Los CE de s como (primaria de 32 bits, secundaria de 16), sin los que no pesan en estos dos niveles."""
    b = s.encode("utf-16-le")
    n = len(b) // 2
    arr = (ctypes.c_uint16 * n).from_buffer_copy(b)
    st = ctypes.c_int(0)
    it = ucol_openElements(COLL, arr, n, ctypes.byref(st))
    crudos = []
    while True:
        ce = ucol_next(it, ctypes.byref(st))
        if ce == -1:
            break
        crudos.append(ce & 0xFFFFFFFF)
    ucol_closeElements(it)
    salida = []
    i = 0
    while i < len(crudos):
        a = crudos[i]
        p = a & 0xFFFF0000
        s2 = ((a >> 8) & 0xFF) << 8
        if i + 1 < len(crudos) and (crudos[i + 1] & 0xC0) == 0xC0:  # la continuación de una primaria larga
            c = crudos[i + 1]
            i += 1
            p |= (c >> 16) & 0xFFFF
            s2 |= (c >> 8) & 0xFF
        i += 1
        if p or s2:
            salida.append((p, s2))
    return salida


def asignado(cp):
    return not 0xD800 <= cp <= 0xDFFF and u_charType(cp) != 0  # 0: sin asignar


def en(rangos, cp):
    return any(desde <= cp <= hasta for desde, hasta in rangos)


tabla = {}
for desde, hasta in RANGOS:
    for cp in range(desde, hasta + 1):
        if asignado(cp):
            tabla[cp] = elementos(chr(cp))

# Lo que ICU no cuenta fuera de los rangos, en el plano básico (lo de fuera no llega: el POS truena con medio par): el BOM, los
# selectores de variante (el U+FE0F de ❤️), los separadores del mongol, las notas interlineales. La U+FFFE tampoco llega.
ignorables = []
for cp in range(0x10000):
    if cp in tabla or cp == 0xFFFE or not asignado(cp) or en(REGLAS_DE_NET, cp) or elementos(chr(cp)):
        continue
    if ignorables and ignorables[-1][1] == cp - 1:
        ignorables[-1][1] = cp
    else:
        ignorables.append([cp, cp])

# Las marcas de los rangos y de lo que no cuenta con su clase de combinación (la del principio y la del final de su
# descomposición): con ellas ICU junta la n con una tilde que viene después de otra marca (la contracción separada de la
# intercalación de Unicode).
combinantes = []
for cp in sorted(set(tabla) | {cp for d, h in ignorables for cp in range(d, h + 1)}):
    delantera = u_getIntPropertyValue(cp, UCHAR_LEAD_CANONICAL_COMBINING_CLASS)
    if delantera == 0:
        continue
    clase = delantera * 256 + u_getIntPropertyValue(cp, UCHAR_TRAIL_CANONICAL_COMBINING_CLASS)
    if combinantes and combinantes[-1][1] == cp - 1 and combinantes[-1][2] == clase:
        combinantes[-1][1] = cp
    else:
        combinantes.append([cp, cp, clase])

# Las contracciones: dos caracteres que ICU lee juntos (la ñ escrita como n + tilde, la «l·» del catalán), las de su lista
# que caen en los rangos.
uset_openEmpty = f(uc, "uset_openEmpty", ctypes.c_void_p)
uset_getItemCount = f(uc, "uset_getItemCount", ctypes.c_int32, ctypes.c_void_p)
uset_getItem = f(uc, "uset_getItem", ctypes.c_int32, ctypes.c_void_p, ctypes.c_int32, PI, PI, P16, ctypes.c_int32, PI)
ucol_getContractionsAndExpansions = f(i18n, "ucol_getContractionsAndExpansions", None, ctypes.c_void_p, ctypes.c_void_p, ctypes.c_void_p,
                                      ctypes.c_int8, PI)
lista = uset_openEmpty()
ucol_getContractionsAndExpansions(COLL, lista, uset_openEmpty(), 1, ctypes.byref(estado))
contracciones = {}
for k in range(uset_getItemCount(lista)):
    desde, hasta, buf, st = ctypes.c_int(0), ctypes.c_int(0), (ctypes.c_uint16 * 64)(), ctypes.c_int(0)
    n = uset_getItem(lista, k, ctypes.byref(desde), ctypes.byref(hasta), buf, 64, ctypes.byref(st))
    if n <= 0:
        continue  # un rango de puntos sueltos, no una contracción
    cadena = bytes(buf)[:2 * n].decode("utf-16-le", "surrogatepass")
    if all(ord(c) in tabla for c in cadena):
        if len(cadena) != 2:
            raise ValueError(f"contracción de {len(cadena)}: {cadena!r}")
        juntos = elementos(cadena)
        if juntos != tabla[ord(cadena[0])] + tabla[ord(cadena[1])]:
            contracciones[(ord(cadena[0]), ord(cadena[1]))] = juntos
# Solo la tilde se lee separada de su letra (es la única que no es letra), y la clase de combinación lo dice.
for (cp, m) in contracciones:
    assert u_getIntPropertyValue(cp, UCHAR_LEAD_CANONICAL_COMBINING_CLASS) == 0
    assert m in (0x00B7, 0x0387, 0x0303), hex(m)

primarias = sorted({p for v in list(tabla.values()) + list(contracciones.values()) for p, _ in v if p})
secundarias = sorted({s for v in list(tabla.values()) + list(contracciones.values()) for _, s in v if s})
lugar_p = {p: i + 2 for i, p in enumerate(primarias)}
lugar_s = {s: i + 2 for i, s in enumerate(secundarias)}
assert len(primarias) + 2 < 1 << (24 - BITS_SECUNDARIA) and len(secundarias) + 2 < 1 << BITS_SECUNDARIA


def codificar(ces):
    if len(ces) >= SIN_ENTRADA:
        raise ValueError("demasiados elementos")
    texto = ALFABETO[len(ces)]
    for p, s in ces:
        valor = (lugar_p.get(p, 0) << BITS_SECUNDARIA) | lugar_s.get(s, 0)
        texto += "".join(ALFABETO[(valor >> d) & 63] for d in (18, 12, 6, 0))
    return texto


cuerpo = ""
for desde, hasta in RANGOS:
    for cp in range(desde, hasta + 1):
        cuerpo += codificar(tabla[cp]) if cp in tabla else ALFABETO[SIN_ENTRADA]

version = (ctypes.c_uint8 * 4)()
u_getVersion(version)
icu = ".".join(str(x) for x in version[:2])
comun = lugar_s[0x0500]
ancho = 112
lineas = [cuerpo[i:i + ancho] for i in range(0, len(cuerpo), ancho)]


def pares(valores, por_renglon):
    texto = [", ".join(valores[i:i + por_renglon]) for i in range(0, len(valores), por_renglon)]
    return "".join(f"        {t},\n" for t in texto)


o = sys.stdout
o.write(f"""// Generado con pos/herramientas/tabla_de_orden.py desde ICU {icu} (es_MX, fuerza secundaria). No se edita a mano.
package mx.papeleriapos.pos.busqueda

/**
 * Los elementos de intercalación de ICU para es-MX en fuerza secundaria, de cada punto de [RANGOS]: con ellos
 * [OrdenEspanol] ordena los nombres como el POS (.NET con ICU, en Windows y en Linux). Cada primaria y cada secundaria va
 * cambiada por su lugar entre todas (empiezan en 2: el 1 separa los dos niveles en la llave).
 */
internal object TablaDeOrden {{
    const val VERSION_ICU = "{icu}"

    /** La secundaria de una letra sin acento. */
    const val COMUN = {comun}

    /** Más que cualquier primaria de la tabla: lo que no está en ella va después de todo, por su número. */
    const val SIN_TABLA = 0x10000

    /** Los bits de la secundaria en cada elemento de [ELEMENTOS]; los de arriba son la primaria. */
    const val BITS_SECUNDARIA = {BITS_SECUNDARIA}

    /** Los rangos de la tabla, desde y hasta (incluido). */
    val RANGOS = intArrayOf(
""")
o.write(pares([f"0x{d:04X}, 0x{h:04X}" for d, h in RANGOS], 1))
o.write("""    )

    /** Lo que ICU no cuenta fuera de [RANGOS] (el BOM, los selectores de variante como el de ❤️), desde y hasta (incluido). */
    val IGNORABLES = intArrayOf(
""")
o.write(pares([f"0x{d:04X}, 0x{h:04X}" for d, h in ignorables], 6))
o.write("""    )

    /**
     * Las marcas de [RANGOS] y de [IGNORABLES] (su clase de combinación no es 0): desde, hasta (incluido) y su clase al
     * principio por 256 más la del final (la de su descomposición). Con ellas se lee la ñ escrita como n + marca de abajo +
     * tilde.
     */
    val COMBINANTES = intArrayOf(
""")
o.write(pares([f"0x{d:04X}, 0x{h:04X}, 0x{c:04X}" for d, h, c in combinantes], 4))
o.write("""    )

    /** Dos caracteres que ICU lee juntos (la ñ escrita como n + tilde, la «l·» del catalán): los dos y sus elementos. */
    val CONTRACCIONES = arrayOf(
""")
for (cp, m), ces in sorted(contracciones.items()):
    elementos_texto = ", ".join(f"{lugar_p.get(p, 0)}, {lugar_s.get(s, 0)}" for p, s in ces)
    o.write(f"        intArrayOf(0x{cp:04X}, 0x{m:04X}, {elementos_texto}),\n")
o.write(f"""    )

    /**
     * Por cada punto de [RANGOS], en orden: cuántos elementos tiene (un carácter de [ALFABETO]; el último, que no está) y
     * cada elemento en cuatro caracteres (base 64): su primaria por 2^[BITS_SECUNDARIA] más su secundaria (0 si no pesa en
     * ese nivel).
     */
    const val ALFABETO = "{ALFABETO}"

    const val ELEMENTOS =
""")
for k, linea in enumerate(lineas):
    fin = " +" if k + 1 < len(lineas) else ""
    o.write(f'        "{linea}"{fin}\n')
o.write("}\n")
print(f"{len(tabla)} puntos, {len(primarias)} primarias, {len(secundarias)} secundarias, {len(contracciones)} contracciones, "
      f"{len(ignorables)} rangos que no cuentan, {len(combinantes)} rangos de marcas, {len(cuerpo)} caracteres; común {comun}",
      file=sys.stderr)
