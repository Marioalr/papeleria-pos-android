// El oráculo de la búsqueda: la del POS (sus fuentes de C# tal cual, IndiceDeBusqueda y lo que usa) sobre el catálogo de la base
// de prueba de sus pruebas (GeneradorFicticios con 30 días, como ServicioDePrueba), para revisar que la de Kotlin
// (pos/src/main/kotlin/mx/papeleriapos/pos/busqueda) dé lo mismo. Escribe en la carpeta del primer argumento el catálogo, las
// marcas, lo que encuentra con cada texto de las pruebas, las familias, el orden por nombre en español de México y lo que lee
// de nombres raros; pos/herramientas/datos_kotlin.py lo lleva a los datos de las pruebas de Kotlin. Se corre con el SDK de
// .NET 10 y el repositorio del POS a un lado:
//
//   dotnet run -c Release --project pos/herramientas/oraculo -p:Pos=/ruta/a/papeleria-pos -- /tmp/salida-oraculo
//   python3 pos/herramientas/datos_kotlin.py /tmp/salida-oraculo pos/src/test/kotlin/mx/papeleriapos/pos/busqueda/BaseDePruebaDatos.kt
//
// En Linux, .NET ordena con la libicu del sistema (Ubuntu 24.04: ICU 74), la misma de pos/herramientas/tabla_de_orden.py.
using System.Globalization;
using System.Text;
using PapeleriaPos.Herramientas.Ficticios;
using PapeleriaPos.Nucleo.Catalogo;

var salida = args.Length > 0 ? args[0] : "salida";
Directory.CreateDirectory(salida);
var datos = GeneradorFicticios.Generar(new OpcionesFicticios { Dias = 30 });

// El catálogo como lo arma el servicio (CatalogoEnMemoria): las variantes activas de productos activos, con su nombre completo,
// el código de su presentación base y todos sus códigos, y lo vendido (renglones de ventas no canceladas).
var productosPorId = datos.Productos.ToDictionary(p => p.Id);
var marcasPorId = datos.Marcas.ToDictionary(m => m.Id);
var presentaciones = datos.Presentaciones.ToDictionary(p => p.Id);
var variantes = datos.Variantes.Where(v => v.Activa && productosPorId[v.ProductoId].Activo).OrderBy(v => v.Id).ToList();
var codigosPorVariante = datos.Codigos.Select(c => (c.Codigo, presentaciones[c.PresentacionId].VarianteId, presentaciones[c.PresentacionId].EsBase))
    .ToLookup(c => c.VarianteId);
var vendidosPorVariante = datos.Ventas.Where(v => !v.Cancelada).SelectMany(v => v.Lineas).GroupBy(l => l.VarianteId)
    .ToDictionary(g => g.Key, g => g.Count());

static string Descripcion(string producto, string? marca, string? descripcion) =>
    string.Join(" ", new[] { producto, marca, descripcion }.Where(p => !string.IsNullOrWhiteSpace(p)));

// Los Id en Kotlin: los mismos para todos, en el orden de los de verdad (van por fecha), y que se leen como su lugar.
static Guid Sintetico(int i) => Guid.Parse($"00000000-0000-7000-8000-{i:x12}");

var catalogo = variantes.Select((v, i) =>
{
    var p = productosPorId[v.ProductoId];
    var marca = p.MarcaId is { } m ? marcasPorId[m].Nombre : null;
    var suyos = codigosPorVariante[v.Id].ToList();
    return new Prod(i, Sintetico(i), Descripcion(p.Nombre, marca, v.Descripcion),
        suyos.OrderByDescending(c => c.EsBase).Select(c => c.Codigo).FirstOrDefault(), suyos.Select(c => c.Codigo).ToList(),
        vendidosPorVariante.GetValueOrDefault(v.Id));
}).ToList();
var marcas = variantes.Select(v => productosPorId[v.ProductoId].MarcaId).OfType<Guid>().Select(m => marcasPorId[m].Nombre)
    .Distinct().Order(StringComparer.Ordinal).ToList();

using (var w = Escritor("catalogo.tsv"))
    foreach (var p in catalogo)
        w.WriteLine($"{Escapar(p.Descripcion)}\t{Escapar(p.Codigo ?? "")}\t{Escapar(string.Join(" ", p.Codigos))}\t{p.Vendidos}");
File.WriteAllLines(Path.Combine(salida, "marcas.txt"), marcas.Select(Escapar), new UTF8Encoding(false));

var textos = Textos(catalogo);
File.WriteAllLines(Path.Combine(salida, "textos.txt"), textos.Select(Escapar), new UTF8Encoding(false));

IndiceDeBusqueda Armar(IEnumerable<Prod> productos, params (string, string)[] sinonimos) =>
    IndiceDeBusqueda.Armar(productos.Select(p => new ProductoParaBuscar(p.Id, p.Descripcion, p.Codigo, p.Codigos)), marcas, sinonimos);

var porId = catalogo.ToDictionary(p => p.Id);
int Vendidos(Guid id) => porId.TryGetValue(id, out var p) ? p.Vendidos : 0;
int Indice(Guid id) => porId[id].Indice;

// Lo que encuentra cada juego (un índice con sus sinónimos) con cada texto, con 200 como máximo.
var esperado = Escritor("esperado.tsv");
void Anotar(string juego, IndiceDeBusqueda indice, string texto, Guid? elegido = null, Func<Guid, int>? vendidos = null)
{
    var encontrados = indice.Buscar(texto, 200, elegido, vendidos ?? Vendidos);
    var parecido = encontrados.Count > 0 && encontrados[0].Parecido;
    if (encontrados.Any(e => e.Parecido != parecido))
        throw new InvalidOperationException("parecido mezclado");
    esperado.WriteLine($"{juego}\t{Escapar(texto)}\t{(elegido is { } e ? Indice(e) : -1)}\t{(parecido ? 1 : 0)}\t"
        + string.Join(",", encontrados.Select(x => Indice(x.Articulo.VarianteId) + (x.PorSinonimo is { } s ? ":" + s : ""))));
}

var baseDePrueba = Armar(catalogo);
// Las partes de cada producto, como las compara BusquedaEnLaCopiaTests (su huella, la de String.hashCode de Java).
using (var w = Escritor("partes.tsv"))
    foreach (var p in catalogo)
        w.WriteLine(Huella(Partes(baseDePrueba.PorVariante(p.Id)!.Partes)).ToString("x8"));

foreach (var texto in textos)
    Anotar("base", baseDePrueba, texto);
// Los de BusquedaApiTests, SinonimosApiTests, ApiCajaTests, CopiaAlDiaTests y CopiaPuestaAlDiaTests (y los de lo que tarda).
string[] mas =
[
    "gel plu", "unto fino", "pluma punto fino", "PLUMA PUNTO FINO", " Pluma  PUNTO fino", "pluma pumto fino", "lustre plata",
    "boligrafo punto fino", "bolig gel", "bol gel", "rotulador", "rotul", "plumon", "plumon rotulador", "rotulador negro", "zanfona",
    "ocarina", "engar orden", "lap az", "lus az", "zz-caso", "ZZ-CASO-01", "cuaderno", "pl", "plu", "pluma", "pluma g", "pluma gel",
    "cua", "cua pro", "folder", "sobre manial", "cuadreno", "750", "p0000", "lapiz", "LÁPIZ", "lápiz  ", "  ", "", "·", "colores",
    "azul", "negro fino", "papel", "pegamento", "tijeras", "marcador", "regla 30", "liston ROJO", "lapicero", "plumero de avestruz",
    .. catalogo.Where((_, i) => i % 25 == 0).Select(p => string.Join(" ", p.Descripcion.Split(' ').Take(2))),
];
foreach (var texto in mas.Distinct().Where(t => !textos.Contains(t)))
    Anotar("base", baseDePrueba, texto);
// Los códigos: el exacto primero (y sin importar mayúsculas ni espacios de las orillas).
foreach (var p in catalogo.Where(p => p.Codigo is not null).Where((_, i) => i % 97 == 0))
{
    Anotar("base", baseDePrueba, p.Codigo!);
    Anotar("base", baseDePrueba, " " + p.Codigo!.ToLowerInvariant() + " ");
}

// Lo elegido antes: el último de «pluma punto fino» sale primero.
var plumas = baseDePrueba.Buscar("pluma punto fino", 200, null, Vendidos);
var elegido = plumas[^1].Articulo.VarianteId;
Anotar("base", baseDePrueba, "pluma punto fino", elegido);
Anotar("base", baseDePrueba, "PLUMA PUNTO FINO", elegido);
Anotar("base", baseDePrueba, "plu", elegido);

// Los sinónimos de las pruebas, cada juego en su índice: «rotulador» con su al revés y solo, «boligrafo» con su al revés.
var rotulador = Armar(catalogo, ("ROTULADOR", "PLUMON"), ("PLUMON", "ROTULADOR"));
foreach (var texto in new[] { "rotulador", "rotul", "plumon", "plumon rotulador", "rotulador negro", "pluma", "rot" })
    Anotar("rotulador", rotulador, texto);
var rotuladorSolo = Armar(catalogo, ("ROTULADOR", "PLUMON"));
foreach (var texto in new[] { "rotulador", "plumon" })
    Anotar("rotulador-solo", rotuladorSolo, texto);
var boligrafo = Armar(catalogo, ("BOLIGRAFO", "PLUMA"), ("PLUMA", "BOLIGRAFO"));
foreach (var texto in new[] { "boligrafo punto fino", "bolig gel", "bol gel", "pluma punto fino", "boligrafo", "bolígrafo multicolor", "pluma pumto fino" })
    Anotar("boligrafo", boligrafo, texto);
// Al revés, con cada séptimo producto otra vez y los sinónimos en otro orden y repetidos: el producto repetido sale dos veces.
var revueltos = Armar(catalogo.AsEnumerable().Reverse().Concat(catalogo.Where((_, i) => i % 7 == 0)), ("PLUMA", "BOLIGRAFO"),
    ("BOLIGRAFO", "PLUMA"), ("BOLIGRAFO", "PLUMA"));
foreach (var texto in new[] { "boligrafo punto fino", "bolig gel", "pluma punto fino" })
    Anotar("revueltos", revueltos, texto);

// Lo renombrado y su sinónimo (CopiaAlDiaTests): el mismo producto que elige esa prueba (UnoSinMayoreo(c, 6)) se llama ahora
// «… Zanfona», y la tienda da de alta «ocarina» = «zanfona».
var renombrado = UnoSinMayoreo(6);
var conZanfona = catalogo.Select(p => p.Indice == renombrado.Indice ? p with { Descripcion = Descripcion(NombreSinVariante(p), null, "Zanfona") } : p)
    .ToList();
var zanfona = Armar(conZanfona, ("OCARINA", "ZANFONA"));
foreach (var texto in new[] { "zanfona", "ocarina" })
    Anotar("zanfona", zanfona, texto);

// Dos servicios sin código ni ventas (BusquedaApiTests): por nombre, y con uno vendido, el vendido primero.
var alfa = new Prod(catalogo.Count, Sintetico(catalogo.Count), "Engargolado orden alfa", null, [], 0);
var beta = new Prod(catalogo.Count + 1, Sintetico(catalogo.Count + 1), "Engargolado orden beta", null, [], 0);
porId[alfa.Id] = alfa;
porId[beta.Id] = beta;
var engargolados = Armar([.. catalogo, alfa, beta]);
Anotar("engargolado", engargolados, "engar orden");
Anotar("engargolado-vendido", engargolados, "engar orden", null, id => id == beta.Id ? 1 : Vendidos(id));
esperado.Dispose();

using (var w = Escritor("extras.tsv"))
{
    w.WriteLine($"renombrado\t{renombrado.Indice}");
    w.WriteLine($"nombre-renombrado\t{Escapar(conZanfona[renombrado.Indice].Descripcion)}");
    // Las familias de lo que encuentra la caja: el que agrega Enter (el elegido del primer renglón, con 200).
    foreach (var texto in new[] { "pluma gel", "pluma punto fino" })
    {
        var agrupacion = PapeleriaPos.Nucleo.Catalogo.Familias.Agrupar(baseDePrueba.Buscar(texto, 200, null, Vendidos), e => e.Articulo.Partes, e => Vendidos(e.Articulo.VarianteId));
        w.WriteLine($"primero:{texto}\t{Indice(agrupacion.Renglones[0].Elegido.Articulo.VarianteId)}");
    }
}

// Las familias de lo que encuentra la caja (hasta 30 y, con «200:», hasta 200): la búsqueda las junta en renglones.
using (var w = Escritor("familias.tsv"))
{
    foreach (var texto in textos.Concat(["pluma gel", "pluma punto fino", "lapicero", "cuaderno", "foami", "folder"]).Distinct())
        w.WriteLine($"{Escapar(texto)}\t{Escapar(Agrupacion(baseDePrueba, texto, 30))}");
    foreach (var texto in new[] { "pluma gel", "pluma punto fino" })
        w.WriteLine($"200:{Escapar(texto)}\t{Escapar(Agrupacion(baseDePrueba, texto, 200))}");
}

// Lo que dice la clase IndiceDeBusqueda, con un catálogo chico hecho a mano (el mismo de IndiceDeBusquedaTest en Kotlin).
{
    (string Nombre, string? Codigo, string[] Codigos, int Vendidos)[] chico =
    [
        ("Papel lustre azul", "750100", ["750100"], 0),
        ("Papel lustre rojo", "750101", ["750101", "750101"], 0),
        ("Papel lustre plateado", "750102", ["750102"], 4),
        ("Papelería surtida", null, [], 0),
        ("Folder (azul) carta", "FOL-AZ", ["FOL-AZ"], 2),
        ("Broca 3/4 acero", "BRO-3/4", ["BRO-3/4"], 0),
        ("Libro de cuentos", "LIB-01", ["LIB-01", "7501234567890"], 0),
        ("Lapicero 1.0 Mm Negro Trazo", "P000001", ["P000001"], 7),
        ("Lapicero 0.7mm Negra Trazo", "P000002", ["P000002"], 3),
        ("Pluma V/Colores Trazo", "P000003", ["P000003"], 0),
        ("Pluma Colores Surt. Trazo", null, [], 1),
        ("Cuaderno Prof 100h Raya Escriba", "7501234567890", ["7501234567890"], 0),
        ("Sobre manila carta", null, [], 5),
        ("Sobre manila oficio", null, [], 5),
        ("Plumón permanente negro", null, [], 0),
        ("Pluma punto fino negro", null, [], 0),
        ("Ñandú de peluche", null, [], 0),
        ("Nanda gris", null, [], 0),
        ("Lápiz adhesivo", null, [], 0),
        ("Lapiz Mirado 2", null, [], 0),
        ("LAPIZ MIRADO 2", null, [], 0),
        ("Bolígrafo multicolor", null, [], 0),
    ];
    var productos = chico.Select((p, i) => new ProductoParaBuscar(Sintetico(i), p.Nombre, p.Codigo, p.Codigos)).ToList();
    var vendidosChico = chico.Select((p, i) => (Sintetico(i), p.Vendidos)).ToDictionary(x => x.Item1, x => x.Vendidos);
    var sinSinonimos = IndiceDeBusqueda.Armar(productos, [], null);
    var conSinonimos = IndiceDeBusqueda.Armar(productos, ["Trazo"], [("BOLIGRAFO", "PLUMA"), ("PLUMA", "BOLIGRAFO")]);
    using var w = Escritor("indice.tsv");
    void Caso(string juego, IndiceDeBusqueda indice, string texto, int maximo, int elegido = -1, bool conVendidos = true)
    {
        var encontrados = indice.Buscar(texto, maximo, elegido >= 0 ? Sintetico(elegido) : null, conVendidos ? vendidosChico.GetValueOrDefault : null);
        w.WriteLine($"{juego}\t{Escapar(texto)}\t{maximo}\t{elegido}\t{(conVendidos ? 1 : 0)}\t{(encontrados.Count > 0 && encontrados[0].Parecido ? 1 : 0)}\t"
            + string.Join(",", encontrados.Select(e => int.Parse(e.Articulo.VarianteId.ToString()[24..], NumberStyles.HexNumber) + (e.PorSinonimo is { } s ? ":" + s : ""))));
    }
    string[] consultas =
    [
        "lus az", "az lus", "papel", "PAPEL", "papel lus", "azul", "(azul)", "3/4", "bro-3", "lib-01", "LIB", "lib", "negro", "surtido", "1mm",
        "0.7mm", "sobre manial", "manial", "cuadreno", "pluma pumto", "lapiz", "lápiz", "LAPIZ MIRADO", "ñandu", "nandu", "750100", " 750100 ",
        "fol-az", "7501", "7501234567890", "", "   ", "x", "pluma", "plu", "bolig", "boli", "bol", "boligrafo", "cuaderno raya", "raya cuaderno",
        "lu", "lustre plata", "lapicero negro", "pluma trazo", "trazo", "sobre", "zzz",
    ];
    foreach (var texto in consultas)
    {
        Caso("sin", sinSinonimos, texto, 30);
        Caso("con", conSinonimos, texto, 30);
    }
    Caso("sin", sinSinonimos, "papel", 2);
    Caso("sin", sinSinonimos, "papel", 1);
    Caso("sin", sinSinonimos, "papel", 0);
    Caso("sin", sinSinonimos, "papel", -1);
    Caso("sin", sinSinonimos, "papel", 30, elegido: 3);
    Caso("sin", sinSinonimos, "papel", 30, conVendidos: false);
    Caso("sin", sinSinonimos, "sobre", 30, elegido: 13);
    Caso("sin", sinSinonimos, "lapiz", 30, conVendidos: false);
    Caso("sin", sinSinonimos, "750100", 30, elegido: 2);
    Caso("con", conSinonimos, "pluma", 30, elegido: 21);
    w.WriteLine($"clave\t{Escapar(IndiceDeBusqueda.Clave("  Lápiz   Azul \t"))}");
    w.WriteLine($"guardada\t{Escapar(IndiceDeBusqueda.ClaveGuardada(new string('a', 99) + " bcd"))}");
    w.WriteLine($"guardada\t{Escapar(IndiceDeBusqueda.ClaveGuardada(new string('a', 100) + "bcd"))}");
    w.WriteLine($"guardada\t{Escapar(IndiceDeBusqueda.ClaveGuardada(" Pluma  PUNTO fino"))}");
    // Las partes y lo normalizado de cada uno.
    foreach (var a in conSinonimos.Articulos)
        w.WriteLine($"articulo\t{Escapar(a.Normalizado)}\t{Escapar(Partes(a.Partes))}");
}

// El orden por nombre: el de la búsqueda (CompareInfo de es-MX sin importar mayúsculas, con su llave ya calculada).
var espanol = CultureInfo.GetCultureInfo("es-MX").CompareInfo;
byte[] Llave(string s) => espanol.GetSortKey(s, CompareOptions.IgnoreCase).KeyData;
int Comparar(byte[] a, byte[] b) => a.AsSpan().SequenceCompareTo(b);
void Ordenar(string archivo, IEnumerable<string> cadenas)
{
    var conLlave = cadenas.Distinct(StringComparer.Ordinal).Select(s => (Texto: s, Llave: Llave(s))).ToList();
    conLlave.Sort((x, y) => Comparar(x.Llave, y.Llave) is var c and not 0 ? c : string.CompareOrdinal(x.Texto, y.Texto));
    using var w = Escritor(archivo);
    for (var i = 0; i < conLlave.Count; i++)
    {
        var igual = i > 0 && Comparar(conLlave[i - 1].Llave, conLlave[i].Llave) == 0;
        w.WriteLine((igual ? "=" : "<") + Escapar(conLlave[i].Texto));
    }
}
// Los nombres del catálogo, en el orden de la búsqueda (su lugar en el catálogo).
{
    var lugares = catalogo.Select(p => (p.Indice, Llave: Llave(p.Descripcion))).ToList();
    lugares.Sort((x, y) => Comparar(x.Llave, y.Llave) is var c and not 0 ? c : x.Indice.CompareTo(y.Indice));
    using var w = Escritor("orden-catalogo.txt");
    for (var i = 0; i < lugares.Count; i++)
        w.WriteLine((i > 0 && Comparar(lugares[i - 1].Llave, lugares[i].Llave) == 0 ? "=" : "<") + lugares[i].Indice);
}
Ordenar("orden-casos.txt", File.ReadAllLines(Path.Combine(AppContext.BaseDirectory, "casos-orden.txt")).Select(Desescapar));
// Para revisar la tabla de Kotlin con muchas cadenas al azar (no van a las pruebas): con lo que puede traer un nombre, con
// puntos de todos los rangos de la tabla (con letras y signos en medio), y con lo que ICU no cuenta y la ñ escrita por partes.
// Los rangos salen de la tabla misma (TablaDeOrden.kt), para que no se queden atrás cuando cambie.
{
    var r = new Random(7);
    var alfabeto = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "alfabeto-orden.txt")).Trim('\n').Split('\n').Select(Desescapar).ToArray();
    var azar = new List<string>();
    for (var i = 0; i < 40000; i++)
    {
        var sb = new StringBuilder();
        var largo = r.Next(1, 9);
        for (var k = 0; k < largo; k++)
            sb.Append(alfabeto[r.Next(alfabeto.Length)]);
        azar.Add(sb.ToString());
    }
    Ordenar("orden-azar.txt", azar);
}
{
    var r = new Random(11);
    var puntos = DeLaTabla("RANGOS");
    var comunes = "aeiounlsrtcdmpbgvhfjqxyzñ áéíóú.,-/()#0123456789".ToCharArray();
    var azar = new List<string>();
    for (var i = 0; i < 60000; i++)
    {
        var sb = new StringBuilder();
        var largo = r.Next(1, 6);
        for (var k = 0; k < largo; k++)
            sb.Append(r.Next(3) == 0 ? comunes[r.Next(comunes.Length)] : (char)puntos[r.Next(puntos.Length)]);
        azar.Add(sb.ToString());
    }
    Ordenar("orden-azar2.txt", azar);
}
{
    // Lo que no cuenta, metido en palabras, y la ñ escrita por partes: una letra con que empieza una contracción, marcas que
    // conoce la tabla (las de abajo no tapan la tilde, las de arriba sí) y la tilde, en cualquier orden.
    var r = new Random(13);
    var noCuentan = DeLaTabla("IGNORABLES");
    var conocidos = new HashSet<int>(DeLaTabla("RANGOS").Concat(noCuentan));
    var acentos = conocidos.Where(n => CharUnicodeInfo.GetUnicodeCategory((char)n) == UnicodeCategory.NonSpacingMark).Order().ToArray();
    int[] abajo = [0x0323, 0x0327, 0x0331, 0x032D, 0x0328, 0x0316, 0x0329, 0x0334, 0x0338, 0x1DCE, 0x1DFA, 0x0591];
    int[] arriba = [0x0301, 0x0300, 0x0308, 0x0345, 0x035C, 0x035D, 0x1DC0, 0x20D0, 0xFE20];
    var inicios = "nN\u0145\u0146\u1E46\u1E47\u1E48\u1E49\u1E4A\u1E4B\u00F1lLoa".ToCharArray();
    string[] palabras = ["Agenda", "Ano", "Año", "Anzo", "Cuaderno A", "Cuaderno B", "n", "ñ", "na", "nz", "l·l"];
    var comunes = "aeiounlsrtcdmpbgvhfjqxyzñ áéíóú.,-/()#0123456789".ToCharArray();
    var azar = new List<string>();
    for (var i = 0; i < 30000; i++)
    {
        var p = palabras[r.Next(palabras.Length)];
        for (var k = r.Next(1, 3); k > 0; k--)
        {
            var donde = r.Next(p.Length + 1);
            p = p[..donde] + (r.Next(4) == 0 ? '\u0303' : (char)noCuentan[r.Next(noCuentan.Length)]) + p[donde..];
        }
        azar.Add(p);
    }
    for (var i = 0; i < 30000; i++)
    {
        var sb = new StringBuilder(r.Next(2) == 0 ? "A" : "");
        sb.Append(inicios[r.Next(inicios.Length)]);
        for (var k = r.Next(0, 4); k > 0; k--)
        {
            var t = r.Next(10);
            sb.Append((char)(t < 4 ? abajo[r.Next(abajo.Length)] : t < 6 ? arriba[r.Next(arriba.Length)] : t < 9 ? 0x0303 : acentos[r.Next(acentos.Length)]));
        }
        if (r.Next(3) != 0) sb.Append(comunes[r.Next(comunes.Length)]);
        azar.Add(sb.ToString());
    }
    Ordenar("orden-azar3.txt", azar);
}

// Los puntos de una lista de rangos de TablaDeOrden.kt (desde, hasta, …), sin los sustitutos.
static int[] DeLaTabla(string lista)
{
    var tabla = File.ReadAllText(Path.Combine(AppContext.BaseDirectory, "TablaDeOrden.kt"));
    var inicio = tabla.IndexOf($"val {lista} = intArrayOf(", StringComparison.Ordinal);
    var desde = tabla.IndexOf('(', inicio) + 1;
    var numeros = tabla[desde..tabla.IndexOf(')', desde)].Split(',', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries)
        .Select(x => Convert.ToInt32(x, 16)).ToArray();
    return Enumerable.Range(0, numeros.Length / 2).SelectMany(k => Enumerable.Range(numeros[2 * k], numeros[2 * k + 1] - numeros[2 * k] + 1))
        .Where(n => n is < 0xD800 or > 0xDFFF).ToArray();
}

// Lo que .NET hace con cada carácter del plano básico, para revisar los de Kotlin (no va a las pruebas): Texto.Normalizar
// («!» si truena), char.IsWhiteSpace, ToUpperInvariant, ToLowerInvariant, si OrdinalIgnoreCase lo iguala con su mayúscula,
// char.IsLetter, si es lo que no es letra ni número de la búsqueda y si es \d.
{
    var noLetraNiNumero = new System.Text.RegularExpressions.Regex(@"^[^\p{L}\p{N}]$");
    var digito = new System.Text.RegularExpressions.Regex(@"^\d$");
    using var w = Escritor("caracteres.tsv");
    for (var n = 0; n <= 0xFFFF; n++)
    {
        if (n is >= 0xD800 and <= 0xDFFF)
            continue;
        var c = (char)n;
        var s1 = c.ToString();
        string normal;
        try { normal = string.Join(" ", PapeleriaPos.Nucleo.Texto.Normalizar(s1).Select(x => ((int)x).ToString("x4"))); }
        catch (ArgumentException) { normal = "!"; }
        var mayuscula = char.ToUpperInvariant(c);
        var minuscula = s1.ToLowerInvariant();
        w.WriteLine($"{n:x4}\t{normal}\t{(char.IsWhiteSpace(c) ? 1 : 0)}\t{(int)mayuscula:x4}\t{string.Join(" ", minuscula.Select(x => ((int)x).ToString("x4")))}\t"
            + $"{(string.Equals(s1, mayuscula.ToString(), StringComparison.OrdinalIgnoreCase) ? 1 : 0)}\t{(char.IsLetter(c) ? 1 : 0)}\t"
            + $"{(noLetraNiNumero.IsMatch(s1) ? 1 : 0)}\t{(digito.IsMatch(s1) ? 1 : 0)}");
    }
}

// Nombres raros para el lector (con las marcas del catálogo), con lo que lee de cada uno y el orden de sus medidas.
{
    var lector = new LectorDeNombres(catalogo.Select(p => p.Descripcion), marcas);
    using var w = Escritor("lector.tsv");
    foreach (var nombre in File.ReadAllLines(Path.Combine(AppContext.BaseDirectory, "nombres-lector.txt")).Select(Desescapar))
    {
        var partes = lector.Leer(nombre);
        var medidas = string.Join(",", partes.Where(x => x.Eje == EjeFamilia.Medida && x.Clave.Length > 0)
            .Select(x => LectorDeNombres.OrdenDeMedida(x.Clave).ToString("R", CultureInfo.InvariantCulture)));
        w.WriteLine($"{Escapar(nombre)}\t{Escapar(Partes(partes))}\t{medidas}");
    }
}
Console.WriteLine($"listo: {catalogo.Count} productos, {marcas.Count} marcas, {textos.Count} textos; renombrado {renombrado.Indice}");

StreamWriter Escritor(string archivo) => new(Path.Combine(salida, archivo), false, new UTF8Encoding(false)) { NewLine = "\n" };

// Lo que no se ve o rompería el renglón, como \uXXXX.
static string Escapar(string s)
{
    var sb = new StringBuilder();
    foreach (var c in s)
    {
        if (c < 0x20 || c == '\\' || (c >= 0x7F && c <= 0x9F) || char.IsSurrogate(c) || c == (char)0x2028 || c == (char)0x2029)
            sb.Append($"\\u{(int)c:x4}");
        else
            sb.Append(c);
    }
    return sb.ToString();
}

static string Desescapar(string s)
{
    var sb = new StringBuilder();
    for (var i = 0; i < s.Length; i++)
    {
        if (s[i] == '\\' && i + 5 < s.Length && s[i + 1] == 'u')
        {
            sb.Append((char)int.Parse(s.AsSpan(i + 2, 4), NumberStyles.HexNumber));
            i += 5;
        }
        else
        {
            sb.Append(s[i]);
        }
    }
    return sb.ToString();
}

static string Partes(IReadOnlyList<ParteDeNombre> partes) => string.Join(" ", partes.Select(p => $"{p.Texto}/{p.Eje}/{p.Clave}"));

static int Huella(string s)
{
    unchecked
    {
        var h = 0;
        foreach (var c in s)
            h = 31 * h + c;
        return h;
    }
}

// La agrupación en familias de lo que encuentra, en texto: el eje, los ejes y cada renglón con sus valores.
string Agrupacion(IndiceDeBusqueda indice, string texto, int maximo)
{
    var a = PapeleriaPos.Nucleo.Catalogo.Familias.Agrupar(indice.Buscar(texto, maximo, null, Vendidos), e => e.Articulo.Partes, e => Vendidos(e.Articulo.VarianteId));
    return $"{a.Eje}|{string.Join(",", a.Ejes)}|" + string.Join(" ; ", a.Renglones.Select(r =>
        $"{r.Eje}~{r.Titulo}~{r.MasVendido}~" + string.Join(",", r.Valores.Select(v => $"{Indice(v.Elemento.Articulo.VarianteId)}={v.Texto}={v.Clave}"))));
}

// El nombre del producto con su marca, sin lo de la variante (para renombrar la variante como en CopiaAlDiaTests).
string NombreSinVariante(Prod p)
{
    var producto = productosPorId[variantes[p.Indice].ProductoId];
    return Descripcion(producto.Nombre, producto.MarcaId is { } m ? marcasPorId[m].Nombre : null, null);
}

// El que elige CopiaAlDiaTests.UnoSinMayoreo: por pieza, con inventario y presentación base, con código y un solo precio, el de
// la base; en el orden de su Id.
Prod UnoSinMayoreo(int cual)
{
    var preciosPorVariante = datos.Precios.ToLookup(p => p.VarianteId);
    var presentacionesPorVariante = datos.Presentaciones.ToLookup(p => p.VarianteId);
    return catalogo.Where(p =>
    {
        var v = variantes[p.Indice];
        var precios = preciosPorVariante[v.Id].ToList();
        return v.UnidadBase == UnidadBase.Pieza && productosPorId[v.ProductoId].LlevaInventario && presentacionesPorVariante[v.Id].Any(x => x.EsBase)
            && p.Codigo is not null && precios.Count == 1 && precios[0].PresentacionId is null;
    }).ElementAt(cual);
}

// Lo que escribiría la cajera, como BusquedaEnLaCopiaTests.Textos (con los productos de la copia, en su orden).
static List<string> Textos(List<Prod> productos)
{
    var palabras = productos.SelectMany(p => p.Descripcion.Split(' ', StringSplitOptions.RemoveEmptyEntries))
        .Select(w => w.Trim('(', ')', '.', ',')).Where(w => w.Length >= 3 && w.Any(char.IsLetter))
        .Distinct(StringComparer.OrdinalIgnoreCase).Order(StringComparer.Ordinal).ToList();
    var textos = new List<string>();
    for (var i = 0; i < palabras.Count; i += Math.Max(1, palabras.Count / 40))
    {
        var w = palabras[i];
        textos.Add(w.ToLowerInvariant());
        textos.Add(w[..3].ToLowerInvariant());
        if (w.Length >= 5)
            textos.Add($"{w[..2]}{w[3]}{w[2]}{w[4..]}".ToLowerInvariant());
    }
    foreach (var p in productos.Where((_, i) => i % Math.Max(1, productos.Count / 30) == 0))
    {
        var ws = p.Descripcion.Split(' ', StringSplitOptions.RemoveEmptyEntries).Where(w => w.Length >= 3 && w.Any(char.IsLetter)).ToList();
        if (ws.Count >= 2)
        {
            textos.Add($"{ws[0][..3]} {ws[1][..3]}");
            textos.Add($"{ws[1][..3]} {ws[0][..3]}");
            textos.Add($"{ws[0]} {ws[^1]}");
        }
        if (p.Codigo is { Length: >= 4 } codigo)
            textos.Add(codigo[..4]);
    }
    textos.AddRange(["pluma gel", "pl gel", "cua pro", "folder", "lustre plata", "negro", "surtido", "1mm", "sobre manial", "cuadreno",
        "pluma pumto fino", "Pluma  PUNTO fino", "lápiz", "z"]);
    return textos.Distinct().ToList();
}

internal sealed record Prod(int Indice, Guid Id, string Descripcion, string? Codigo, List<string> Codigos, int Vendidos);
