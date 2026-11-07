#!/usr/bin/env python3
"""
Lleva lo que escribió el oráculo (pos/herramientas/oraculo: la búsqueda del POS en C#) a BaseDePruebaDatos.kt, los datos de las
pruebas de Kotlin de la búsqueda.

    python3 pos/herramientas/datos_kotlin.py /tmp/salida-oraculo pos/src/test/kotlin/mx/papeleriapos/pos/busqueda/BaseDePruebaDatos.kt

Cada renglón va con sus campos separados por «|». Lo que no se ve o no cabe en un texto crudo de Kotlin ($, \\, |, las comillas,
los espacios de las orillas de cada campo) va como \\uXXXX, y BaseDePrueba.kt lo lee de vuelta.
"""
import os
import sys

salida, destino = sys.argv[1], sys.argv[2]


def lineas(archivo):
    with open(os.path.join(salida, archivo), encoding="utf-8") as f:
        return [l.rstrip("\n") for l in f if l.rstrip("\n") != ""]


def desescapar(s):
    """Lo que el oráculo escribió con \\uXXXX."""
    out, i = [], 0
    while i < len(s):
        if s[i] == "\\" and s[i + 1:i + 2] == "u":
            out.append(chr(int(s[i + 2:i + 6], 16)))
            i += 6
        else:
            out.append(s[i])
            i += 1
    return "".join(out)


INVISIBLES = set(range(0x00, 0x20)) | set(range(0x7F, 0xA1)) | {0xAD} | set(range(0x0300, 0x0370)) | set(range(0x2000, 0x2010)) | \
    set(range(0x2028, 0x2030)) | set(range(0x205F, 0x2070)) | {0xFEFF}


def escapar(s):
    """Para un campo en un texto crudo de Kotlin."""
    out = []
    for k, c in enumerate(s):
        n = ord(c)
        orilla = c == " " and (k == 0 or k == len(s) - 1)
        if c in "$\\|\"" or n in INVISIBLES or n > 0xFFFF or 0xD800 <= n <= 0xDFFF or orilla:
            out.append("\\u%04x" % n)
        else:
            out.append(c)
    return "".join(out)


def campos(*valores):
    return "|".join(escapar(desescapar(v)) for v in valores)


def huella(s):
    """String.hashCode de Java, en ocho dígitos hexadecimales."""
    h = 0
    b = s.encode("utf-16-le", "surrogatepass")
    for i in range(0, len(b), 2):
        h = (31 * h + (b[i] | (b[i + 1] << 8))) & 0xFFFFFFFF
    return "%08x" % h


def trozos(renglones, maximo=30000):
    """Los renglones en pedazos que caben en una constante de la JVM (64 KB en UTF-8)."""
    actual, largo, todos = [], 0, []
    for r in renglones:
        n = len(r.encode("utf-8")) + 1
        if actual and largo + n > maximo:
            todos.append(actual)
            actual, largo = [], 0
        actual.append(r)
        largo += n
    if actual:
        todos.append(actual)
    return todos


def envolver(tokens, ancho=116):
    renglones, actual = [], ""
    for t in tokens:
        if actual and len(actual) + 1 + len(t) > ancho:
            renglones.append(actual)
            actual = t
        else:
            actual = f"{actual} {t}" if actual else t
    if actual:
        renglones.append(actual)
    return renglones


partes = lineas("partes.tsv")
productos = []
for i, l in enumerate(lineas("catalogo.tsv")):
    descripcion, codigo, codigos, vendidos = l.split("\t")
    productos.append(campos(descripcion, codigo, codigos, vendidos, partes[i]))
marcas = [desescapar(m) for m in lineas("marcas.txt")]
textos = [escapar(desescapar(t)) for t in lineas("textos.txt")]
esperado = [campos(*l.split("\t")) for l in lineas("esperado.tsv")]
familias = []
for l in lineas("familias.tsv"):
    texto, agrupacion = l.split("\t")
    familias.append(campos(texto, huella(desescapar(agrupacion))))
extras = dict(l.split("\t") for l in lineas("extras.tsv"))
orden = lineas("orden-catalogo.txt")
casos = [l[0] + escapar(desescapar(l[1:])) for l in lineas("orden-casos.txt")]
lector = [campos(*l.split("\t")) for l in lineas("lector.tsv")]


def constante(nombre, renglones):
    texto = "\n".join(renglones)
    assert '"""' not in texto and "$" not in texto
    return f'    private const val {nombre} = """\n{texto}\n"""\n'


def lista(nombre, renglones, doc):
    pedazos = trozos(renglones)
    nombres = [f"{nombre}_{k + 1}" for k in range(len(pedazos))]
    s = f"    /** {doc} */\n    val {nombre}: List<String> = listOf({', '.join(nombres)})\n\n"
    for n, p in zip(nombres, pedazos):
        s += constante(n, p) + "\n"
    return s


with open(destino, "w", encoding="utf-8", newline="\n") as o:
    o.write("""// Generado con pos/herramientas/datos_kotlin.py desde lo que escribe pos/herramientas/oraculo. No se edita a mano.
package mx.papeleriapos.pos.busqueda

/**
 * Lo que dio la búsqueda del POS (sus fuentes de C# tal cual) con el catálogo de su base de prueba: GeneradorFicticios con 30
 * días, como ServicioDePrueba, con los Id cambiados por unos que se leen como su lugar (en el mismo orden). [BaseDePrueba] lo lee.
 */
internal object BaseDePruebaDatos {
""")
    o.write(lista("PRODUCTOS", productos,
                  "Los productos en el orden de su Id: descripción|código|códigos|vendidos|huella de sus partes (texto/eje/clave)."))
    o.write(f'    /** Las marcas de los productos, como las manda el POS. */\n    const val MARCAS = "{"|".join(escapar(m) for m in marcas)}"\n\n')
    o.write(lista("TEXTOS", textos, "Lo que escribiría la cajera (BusquedaEnLaCopiaTests.Textos) con este catálogo, en su orden."))
    o.write(lista("ESPERADO", esperado,
                  "Lo que encontró el POS, hasta 200: juego|texto|elegido (su lugar, -1 sin él)|parecido|lugares (con :sinónimo)."))
    o.write(lista("FAMILIAS", familias, "Las familias de lo que encontró (hasta 30; con «200:», hasta 200): texto|huella de la agrupación."))
    o.write(f"    /** El producto que renombra CopiaAlDiaTests (su lugar) y cómo se llama después. */\n"
            f"    const val RENOMBRADO = {extras['renombrado']}\n\n"
            f'    const val NOMBRE_RENOMBRADO = "{escapar(desescapar(extras["nombre-renombrado"]))}"\n\n')
    o.write(f"    /** El que agrega Enter con «pluma gel» y con «pluma punto fino»: el elegido del primer renglón de sus familias. */\n"
            f"    const val PRIMERO_PLUMA_GEL = {extras['primero:pluma gel']}\n\n"
            f"    const val PRIMERO_PLUMA_PUNTO_FINO = {extras['primero:pluma punto fino']}\n\n")
    o.write(lista("ORDEN_DEL_CATALOGO", envolver(orden),
                  "Los productos (su lugar) en el orden de su nombre en el POS: «<» va después del anterior, «=» empata con él."))
    o.write(lista("CASOS_DE_ORDEN", casos, "Nombres raros en el orden del POS: «<» va después del anterior, «=» empata con él."))
    o.write(lista("LECTOR", lector, "Nombres raros, lo que lee el POS de cada uno (texto/eje/clave) y el orden de sus medidas."))
    o.write("}\n")
print(f"{len(productos)} productos, {len(esperado)} búsquedas, {len(familias)} familias, {len(casos)} casos de orden, "
      f"{len(lector)} nombres", file=sys.stderr)
