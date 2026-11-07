// El oráculo del precio: el núcleo del POS, tal cual (Cantidad, Redondeo, CalculoPrecio y las promociones), para revisar que
// el de Kotlin (pos/src/main/kotlin/mx/papeleriapos/pos/precio) dé lo mismo, con valores amplios (factores y cantidades
// raras o grandes, precios grandes, promociones con números grandes), EnUnidades, la división del decimal con divisores de
// más de 32 bits y Cantidad.Unidades con decimales al azar. «ver» imprime los números de DiferenciasConElPosTest. Se corre
// con el SDK de .NET 10 y el repositorio del POS a un lado:
//
//   dotnet run -c Release --project pos/herramientas/oraculo-precio -p:Pos=/ruta/a/papeleria-pos -- ver
using System.Globalization;
using System.Text;
using PapeleriaPos.Nucleo;
using PapeleriaPos.Nucleo.Catalogo;

var inv = CultureInfo.InvariantCulture;
if (args.Length > 0 && args[0] == "ver")
{
    Ver.Todo();
    return;
}
var salida = args.Length > 0 ? args[0] : "../salida/amplio.tsv";
var semilla = args.Length > 1 ? int.Parse(args[1], inv) : 7;
var cuantos = args.Length > 2 ? int.Parse(args[2], inv) : 40000;
var azar = new Random(semilla);
var sb = new StringBuilder();

string Error(Exception e) => "error:" + e.GetType().Name;
string Id(Guid g) => azar.Next(4) == 0 ? g.ToString().ToUpperInvariant() : g.ToString();
T De<T>(params T[] opciones) => opciones[azar.Next(opciones.Length)];
long Entre(long a, long b) => azar.NextInt64(a, b);

var hoy = new DateOnly(2027, 8, 4);
var epoca = new DateOnly(1970, 1, 1).DayNumber;
long Dias(DateOnly d) => d.DayNumber - epoca;
var variantes = new[] { Guid.NewGuid(), Guid.NewGuid() };
var sucursales = new[] { Guid.NewGuid(), Guid.NewGuid() };
long[] preciosTipicos = [500, 650, 700, 730, 800, 900, 1000, 1090, 1100, 1200, 1250, 1450, 1500, 1550, 12000, 15000, 7000, 2050, 1990, 333, 1];

long PrecioAlAzar() => azar.Next(12) switch
{
    0 => De(0L, -1L, -100L, 3_037_000_499L, 3_037_000_500L, 1_000_000_000_000L, 5_000_000_000_000_000L, long.MaxValue / 1000, long.MaxValue),
    1 => Entre(1, 10_000_000_000_000),
    2 => Entre(1, 100_000_000),
    3 or 4 => Entre(1, 100_000),
    _ => De(preciosTipicos),
};

Cantidad CantidadAlAzar() => azar.Next(14) switch
{
    0 => new Cantidad(Entre(1, 1_000_000_000)),
    1 => new Cantidad(Entre(1, 10_000_000_000_000_000)),
    2 => new Cantidad(De(long.MaxValue, long.MinValue + 1, 3_037_000_499L, 3_037_000_500L, -3_037_000_500L, 9_223_372_036_854_775L)),
    3 => De(Cantidad.Cero, Cantidad.Piezas(-1), Cantidad.Unidades(-2.5m), Cantidad.Piezas(-12), new Cantidad(-1)),
    4 => new Cantidad(Entre(1, 100_000)),
    5 => Cantidad.Piezas(Entre(1, 1_000_000)),
    _ => Cantidad.Piezas(Entre(1, 40)),
};

Cantidad MinimaAlAzar() => azar.Next(12) switch
{
    0 => new Cantidad(Entre(1, 100_000)),
    1 => De(Cantidad.Cero, Cantidad.Piezas(-1), new Cantidad(long.MaxValue), new Cantidad(1)),
    2 => Cantidad.Piezas(Entre(1, 1_000_000)),
    _ => Cantidad.Piezas(De(1, 1, 1, 2, 3, 5, 10, 12, 12, 24, 50, 100)),
};

Cantidad FactorAlAzar() => azar.Next(14) switch
{
    0 => De(Cantidad.Cero, Cantidad.Piezas(-1), new Cantidad(-7)),
    1 => new Cantidad(Entre(1, 100_000)),
    2 => new Cantidad(Entre(1, 10_000_000_000)),
    3 => new Cantidad(De(1L, 3L, 7L, 13L, 333L, 999L, 1001L, 4_294_967_297L, 3_037_000_500L, 9_223_372_036_854_775_807L)),
    4 => Cantidad.Piezas(Entre(1, 1_000_000)),
    _ => Cantidad.Piezas(De(1, 1, 1, 2, 3, 6, 12, 12, 24, 100)),
};

int EnteroGrande() => De(int.MaxValue, int.MinValue, int.MaxValue - 1, 100, 101, 1000, 65536);

List<Presentacion> PresentacionesAlAzar(Guid variante)
{
    var lista = new List<Presentacion>();
    for (var i = azar.Next(1, 4); i > 0; i--)
        lista.Add(new Presentacion { Id = Guid.NewGuid(), VarianteId = variante, Nombre = "P", Factor = FactorAlAzar() });
    return lista;
}

List<Precio> PreciosAlAzar(Guid variante, List<Presentacion> presentaciones)
{
    var lista = new List<Precio>();
    var n = azar.Next(0, 8);
    for (var i = 0; i < n; i++)
    {
        var p = new Precio
        {
            VarianteId = azar.Next(10) == 0 ? variantes[1 - Array.IndexOf(variantes, variante)] : variante,
            PresentacionId = azar.Next(10) < 4 ? presentaciones[azar.Next(presentaciones.Count)].Id : azar.Next(20) == 0 ? Guid.NewGuid() : null,
            SucursalId = azar.Next(10) < 3 ? sucursales[azar.Next(2)] : null,
            CantidadMinima = MinimaAlAzar(),
            PrecioCentavos = PrecioAlAzar(),
        };
        lista.Add(p);
        if (azar.Next(8) == 0)
            lista.Add(new Precio { VarianteId = p.VarianteId, PresentacionId = p.PresentacionId, SucursalId = p.SucursalId, CantidadMinima = p.CantidadMinima,
                PrecioCentavos = azar.Next(2) == 0 ? p.PrecioCentavos : PrecioAlAzar() });
    }
    return lista;
}

PromocionDeProducto PromocionAlAzar()
{
    var tipo = (TipoPromocion)azar.Next(4);
    var desde = hoy.AddDays(azar.Next(-15, 6));
    DateOnly? hasta = azar.Next(5) == 0 ? null : desde.AddDays(azar.Next(-3, 21));
    var dias = azar.Next(3) == 0 ? (DiasDeLaSemana)azar.Next(-3, 256) : DiasDeLaSemana.Todos;
    var id = azar.Next(4) == 0
        ? new Guid($"{De("00000000", "7fffffff", "80000000", "ffffffff")}-{De("0000", "7fff", "8000", "ffff")}-{De("0000", "7fff", "8000")}-{De("0000", "7f00", "8000", "00ff")}-00000000000{azar.Next(3)}")
        : Guid.NewGuid();
    int lleva = azar.Next(5) == 0 ? EnteroGrande() : azar.Next(4) == 0 ? azar.Next(0, 101) : De(0, 1, 2, 2, 3, 3, 3, 4, 5);
    int paga = azar.Next(5) == 0 ? EnteroGrande() : azar.Next(4) == 0 ? azar.Next(0, 101) : De(0, 1, 1, 2, 2, 3, 4);
    int centesimas = azar.Next(5) == 0 ? EnteroGrande() : azar.Next(3) == 0 ? azar.Next(-5, 10006) : De(0, 1, 500, 1000, 1500, 2000, 9999, 10000, -5);
    long? especial = azar.Next(4) == 0 ? null : azar.Next(3) == 0 ? PrecioAlAzar() : De(0L, -1L, 1L, 600L, 700L, 730L, 900L, 1000L, 1300L, 12000L);
    return new PromocionDeProducto(id, "x", tipo, lleva, paga, centesimas, especial, desde, hasta, dias);
}

string TextoPrecios(List<Precio> precios) => string.Join(";", precios.Select(p =>
    $"{Id(p.VarianteId)},{(p.PresentacionId is { } pr ? Id(pr) : "-")},{(p.SucursalId is { } s ? Id(s) : "-")},{p.CantidadMinima.Milesimas},{p.PrecioCentavos}"));
string TextoPromocion(PromocionDeProducto p) =>
    $"{Id(p.PromocionId)},{(int)p.Tipo},{p.Lleva},{p.Paga},{p.Centesimas},{(p.PrecioCentavos is { } c ? c.ToString(inv) : "-")},{Dias(p.Desde)},{(p.Hasta is { } h ? Dias(h).ToString(inv) : "-")},{(int)p.Dias}";
string TextoPresentacion(Presentacion p) => $"{Id(p.Id)},{Id(p.VarianteId)},{p.Factor.Milesimas}";

for (var caso = 0; caso < cuantos; caso++)
{
    var variante = variantes[azar.Next(2)];
    var presentaciones = PresentacionesAlAzar(variante);
    var presentacion = presentaciones[0];
    var precios = PreciosAlAzar(variante, presentaciones);
    var sucursal = azar.Next(6) == 0 ? Guid.NewGuid() : sucursales[azar.Next(2)];
    var cantidad = CantidadAlAzar();
    var mayoreo = azar.Next(10) < 3;
    DateOnly? dia = azar.Next(10) == 0 ? null : hoy.AddDays(azar.Next(-10, 11));
    int? descuento = azar.Next(2) == 0 ? null : azar.Next(4) == 0 ? EnteroGrande() : De(0, 1, 500, 1000, 4000, 9999, 10000, 15000, -100);
    List<PromocionDeProducto>? promociones = azar.Next(10) switch
    {
        0 => null,
        1 => [],
        _ => Enumerable.Range(0, azar.Next(1, 6)).Select(_ => PromocionAlAzar()).ToList(),
    };

    string resultado;
    try
    {
        var a = CalculoPrecio.Calcular(precios, sucursal, presentacion, cantidad, mayoreo, promociones, dia, descuento);
        if (a is null)
            resultado = "null";
        else
        {
            string porPresentacion;
            try { porPresentacion = a.PrecioPorPresentacion(presentacion).ToString(inv); }
            catch (Exception e) { porPresentacion = Error(e); }
            var promo = a.Promocion is { } pp ? promociones!.FindIndex(x => ReferenceEquals(x, pp)) : -1;
            resultado = string.Join(",", precios.FindIndex(x => ReferenceEquals(x, a.Precio)), a.ImporteCentavos,
                a.Menudeo is { } m ? precios.FindIndex(x => ReferenceEquals(x, m)) : -1, a.ImporteMenudeoCentavos, promo, a.AhorroPromocionCentavos,
                a.EsMayoreo ? 1 : 0, a.AhorroCentavos, porPresentacion);
        }
    }
    catch (Exception e) { resultado = Error(e); }

    string escalas;
    try
    {
        var e = CalculoPrecio.Escalas(precios, sucursal, presentacion);
        escalas = (e.DePresentacion ? "1" : "0") + ":" + string.Join(";", e.Escalas.Select(x => precios.FindIndex(y => ReferenceEquals(x, y))));
    }
    catch (Exception e) { escalas = Error(e); }

    sb.Append("calc\t").Append(Id(sucursal)).Append('\t').Append(TextoPresentacion(presentacion)).Append('\t').Append(cantidad.Milesimas)
        .Append('\t').Append(mayoreo ? 1 : 0).Append('\t').Append(dia is { } d ? Dias(d).ToString(inv) : "-")
        .Append('\t').Append(descuento is { } de ? de.ToString(inv) : "-").Append('\t').Append(TextoPrecios(precios))
        .Append('\t').Append(promociones is null ? "null" : string.Join(";", promociones.Select(TextoPromocion)))
        .Append('\t').Append(resultado).Append('\t').Append(escalas).Append('\n');
}

for (var caso = 0; caso < cuantos / 2; caso++)
{
    var variante = variantes[azar.Next(2)];
    var presentaciones = PresentacionesAlAzar(variante);
    if (azar.Next(5) == 0) presentaciones.RemoveAt(0);
    var precios = PreciosAlAzar(variante, presentaciones.Count > 0 ? presentaciones : PresentacionesAlAzar(variante));
    var sucursal = sucursales[azar.Next(2)];
    var promocion = PromocionAlAzar();
    string resultado;
    try { resultado = CalculoPrecio.PrecioMasBajoCon(promocion, precios, presentaciones, sucursal) is { } x ? x.ToString(inv) : "null"; }
    catch (Exception e) { resultado = Error(e); }
    sb.Append("masbajo\t").Append(Id(sucursal)).Append('\t').Append(TextoPromocion(promocion)).Append('\t').Append(TextoPrecios(precios))
        .Append('\t').Append(string.Join(";", presentaciones.Select(TextoPresentacion))).Append('\t').Append(resultado).Append('\n');
}

// La división del decimal de PorUnidadBase (CalculoPrecio.cs:179), con numeradores y divisores de todos los tamaños.
long Largo() => azar.Next(5) switch
{
    0 => Entre(1, 1000),
    1 => Entre(1, 4_294_967_296),
    2 => Entre(4_294_967_296, long.MaxValue),
    3 => De(1L, 2L, 3L, 7L, 999L, 1000L, 1001L, 4_294_967_295L, 4_294_967_296L, 4_294_967_297L, long.MaxValue, long.MaxValue - 1, 3_037_000_500L),
    _ => Entre(1, long.MaxValue),
};
for (var caso = 0; caso < cuantos * 2; caso++)
{
    long c = Largo(), m = Largo();
    string r;
    try { r = (c / (new Cantidad(m).Milesimas / (decimal)Cantidad.Escala)).ToString(inv); } catch (Exception e) { r = Error(e); }
    sb.Append($"decdiv\t{c}\t{m}\t{r}\n");
}

// EnUnidades (Cantidad.cs:25) y Cantidad.Unidades (Cantidad.cs:15) con decimales al azar de cualquier escala.
for (var caso = 0; caso < cuantos / 4; caso++)
{
    var m = azar.Next(3) == 0 ? Entre(long.MinValue, long.MaxValue) : Entre(-100_000, 100_000);
    sb.Append($"enunidades\t{m}\t{new Cantidad(m).EnUnidades.ToString(inv)}\n");
    var bytes = new byte[12];
    azar.NextBytes(bytes);
    var cortar = azar.Next(0, 12);
    for (var i = 12 - cortar; i < 12; i++) bytes[i] = 0;
    var dec = new decimal(BitConverter.ToInt32(bytes, 0), BitConverter.ToInt32(bytes, 4), BitConverter.ToInt32(bytes, 8), azar.Next(2) == 0, (byte)azar.Next(0, 29));
    string u;
    try { u = Cantidad.Unidades(dec).Milesimas.ToString(inv); } catch (Exception e) { u = Error(e); }
    sb.Append($"unidades2\t{dec.ToString(inv)}\t{u}\n");
}

File.WriteAllText(salida, sb.ToString());
Console.WriteLine($"listo: {salida}");

/// <summary>Los números de los casos de DiferenciasConElPosTest, del POS mismo.</summary>
static class Ver
{
    static readonly CultureInfo Inv = CultureInfo.InvariantCulture;
    static readonly Guid Cuaderno = Guid.NewGuid();
    static readonly Guid Matriz = Guid.NewGuid();
    static readonly Guid Sucursal2 = Guid.NewGuid();
    static readonly DateOnly Hoy = new(2027, 8, 4);
    static readonly Presentacion Pieza = new() { VarianteId = Cuaderno, Nombre = "Pieza" };
    static readonly Presentacion Docena = new() { VarianteId = Cuaderno, Nombre = "Docena", Factor = Cantidad.Piezas(12) };
    static readonly Presentacion Rollo = new() { VarianteId = Cuaderno, Nombre = "Rollo", Factor = Cantidad.Unidades(1.5m) };

    static Precio PP(long c, long desde = 1, Guid? suc = null) => new() { VarianteId = Cuaderno, CantidadMinima = Cantidad.Piezas(desde), PrecioCentavos = c, SucursalId = suc };
    static Precio Propio(Presentacion p, long c, long desde = 1) => new() { VarianteId = Cuaderno, PresentacionId = p.Id, CantidadMinima = Cantidad.Piezas(desde), PrecioCentavos = c };
    static PromocionDeProducto Regla(TipoPromocion t, int lleva = 0, int paga = 0, int cent = 0, long? precio = null, DiasDeLaSemana dias = DiasDeLaSemana.Todos, Guid? id = null, string nombre = "x") =>
        new(id ?? Guid.NewGuid(), nombre, t, lleva, paga, cent, precio, Hoy.AddDays(-10), Hoy.AddDays(10), dias);

    static string T(PrecioAplicado? a, params Precio[] precios) => a is null ? "null"
        : $"precio={a.Precio.PrecioCentavos}(#{Array.FindIndex(precios, p => ReferenceEquals(p, a.Precio))}) importe={a.ImporteCentavos} menudeo={a.Menudeo?.PrecioCentavos} importeMenudeo={a.ImporteMenudeoCentavos} esMayoreo={a.EsMayoreo} ahorro={a.AhorroCentavos} promo={a.Promocion?.Nombre ?? "-"} ahorroPromo={a.AhorroPromocionCentavos}";
    static string Intento(Func<string> f) { try { return f(); } catch (Exception e) { return "error:" + e.GetType().Name; } }
    static void L(string que, string valor) => Console.WriteLine($"{que}: {valor}");

    public static void Todo()
    {
        {
            var r125 = new Presentacion { VarianteId = Cuaderno, Nombre = "Rollo 1.25", Factor = Cantidad.Unidades(1.25m) };
            var a = PP(333);
            foreach (var c in new[] { 5, 12, 13 })
            {
                var r = CalculoPrecio.Calcular([a], Matriz, r125, Cantidad.Piezas(3), false, [Regla(TipoPromocion.Porcentaje, cent: c, nombre: $"{c}")], Hoy)!;
                L($"Y1 rollo 1.25 porcentaje {c}", T(r, a) + $" porPresentacion={r.PrecioPorPresentacion(r125)}");
            }
        }
        {
            var x = PP(800); var y = PP(800); var p = PP(700, 12); var q = PP(700, 12);
            var d = CalculoPrecio.Calcular([x, y, p, q], Matriz, Pieza, Cantidad.Piezas(12))!;
            var r = CalculoPrecio.Calcular([y, x, q, p], Matriz, Pieza, Cantidad.Piezas(12))!;
            L("X1 menudeo de doce es x", ReferenceEquals(d.Menudeo, x).ToString());
            L("X1 menudeo al reves es y", ReferenceEquals(r.Menudeo, y).ToString());
            var e = CalculoPrecio.Escalas([x, y, p, q], Matriz, Pieza);
            L("X1 escalas", string.Join(";", e.Escalas.Select(z => Array.IndexOf(new[] { x, y, p, q }, z))));
        }
        {
            var a = PP(1000);
            L("X2 lleva 2 paga 2", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [Regla(TipoPromocion.LlevaNPagaM, 2, 2)], Hoy), a));
            L("X2 lleva 3 paga 0", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [Regla(TipoPromocion.LlevaNPagaM, 3, 0)], Hoy), a));
            L("X2 porcentaje 10000", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [Regla(TipoPromocion.Porcentaje, cent: 10000)], Hoy), a));
            L("X2 porcentaje -5", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [Regla(TipoPromocion.Porcentaje, cent: -5)], Hoy), a));
            L("X2 especial null", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [Regla(TipoPromocion.PrecioEspecial)], Hoy), a));
        }
        {
            var metro = new Presentacion { VarianteId = Cuaderno, Nombre = "Metro" };
            var m = PP(800); var caro = PP(900, 12);
            L("X3 medio metro siempre", T(CalculoPrecio.Calcular([m, caro], Matriz, metro, Cantidad.Unidades(0.5m), true), m, caro));
            L("X3 medio metro", T(CalculoPrecio.Calcular([m, caro], Matriz, metro, Cantidad.Unidades(0.5m)), m, caro));
            L("X3 un metro siempre", T(CalculoPrecio.Calcular([m, caro], Matriz, metro, Cantidad.Piezas(1), true), m, caro));
        }
        foreach (var m in new long[] { 1500, 3000, 250, 1, -500, 12000 })
            L($"A1 EnUnidades({m})", new Cantidad(m).EnUnidades.ToString(Inv));
        L("A1 suma 3+2", (Cantidad.Piezas(3).EnUnidades + Cantidad.Piezas(2).EnUnidades).ToString(Inv));
        L("A1 igual a 1.5m", (Cantidad.Unidades(1.5m).EnUnidades == 1.5m).ToString());
        L("A3 importes", $"{new Cantidad(-125).Importe(4)} {new Cantidad(-1).Importe(500)} {new Cantidad(-1).Importe(499)} {new Cantidad(125).Importe(4)}");
        L("A4 por", $"{Cantidad.Unidades(0.333m).Por(Cantidad.Unidades(1.5m))} {Cantidad.Unidades(-0.333m).Por(Cantidad.Unidades(1.5m))}");

        { var a = PP(800); var b = PP(650, 12); L("B1 devolucion -12", T(CalculoPrecio.Calcular([a, b], Matriz, Pieza, Cantidad.Piezas(-12)), a, b)); }
        { var a = PP(800); var b = PP(600, 24); var c = PP(700, 12); L("B2 mayoreoSiempre desordenadas", T(CalculoPrecio.Calcular([a, b, c], Matriz, Pieza, Cantidad.Piezas(1), true), a, b, c)); }
        {
            var m = PP(800); var a = PP(700, 12); var b = PP(650, 12);
            L("B3 [m,a700,b650] siempre 1", T(CalculoPrecio.Calcular([m, a, b], Matriz, Pieza, Cantidad.Piezas(1), true), m, a, b));
            L("B3 [m,b650,a700] siempre 1", T(CalculoPrecio.Calcular([m, b, a], Matriz, Pieza, Cantidad.Piezas(1), true), m, b, a));
            L("B3 [m,a700,b650] 12", T(CalculoPrecio.Calcular([m, a, b], Matriz, Pieza, Cantidad.Piezas(12)), m, a, b));
        }
        {
            var x = PP(800); var y = PP(800); var p = PP(700, 12); var q = PP(700, 12);
            L("B4 empate 1", T(CalculoPrecio.Calcular([x, y, p, q], Matriz, Pieza, Cantidad.Piezas(1)), x, y, p, q));
            L("B4 empate 12", T(CalculoPrecio.Calcular([x, y, p, q], Matriz, Pieza, Cantidad.Piezas(12)), x, y, p, q));
            L("B4 empate 12 al reves", T(CalculoPrecio.Calcular([y, x, q, p], Matriz, Pieza, Cantidad.Piezas(12)), x, y, p, q));
        }
        { var pr = Propio(Docena, 7000); var a = PP(800); var b = PP(500, 12); L("B5 docena propia vs mayoreo", T(CalculoPrecio.Calcular([pr, a, b], Matriz, Docena, Cantidad.Piezas(1)), pr, a, b)); }
        {
            var pr = Propio(Rollo, 500); var a = PP(333);
            L("B6 rollo propio 500", T(CalculoPrecio.Calcular([pr, a], Matriz, Rollo, Cantidad.Piezas(1)), pr, a));
            var pr2 = Propio(Rollo, 501);
            L("B6 rollo propio 501", T(CalculoPrecio.Calcular([pr2, a], Matriz, Rollo, Cantidad.Piezas(1)), pr2, a));
        }
        {
            var pr = Propio(Docena, 15000); var a = PP(1200);
            var e = CalculoPrecio.Escalas([pr, a], Matriz, Docena);
            L("B7 escalas", $"{e.DePresentacion} {string.Join(";", e.Escalas.Select(x => x.PrecioCentavos))}");
            L("B7 calcular", T(CalculoPrecio.Calcular([pr, a], Matriz, Docena, Cantidad.Piezas(1)), pr, a));
        }
        {
            var pr = Propio(Docena, 7000); var a = PP(800); var b = PP(500, 1, Sucursal2);
            L("B8 sucursal2", T(CalculoPrecio.Calcular([pr, a, b], Sucursal2, Docena, Cantidad.Piezas(1)), pr, a, b));
            L("B8 matriz", T(CalculoPrecio.Calcular([pr, a, b], Matriz, Docena, Cantidad.Piezas(1)), pr, a, b));
        }
        {
            var a = PP(1000); var tres = Regla(TipoPromocion.LlevaNPagaM, 3, 2, nombre: "3x2");
            foreach (var d in new[] { 3333, 3334, 3330 })
                L($"C1 descuento {d}", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [tres], Hoy, d), a));
        }
        {
            var a = PP(333);
            foreach (var c in new[] { 1, 4 })
            {
                var r = CalculoPrecio.Calcular([a], Matriz, Rollo, Cantidad.Piezas(3), false, [Regla(TipoPromocion.Porcentaje, cent: c, nombre: $"{c}")], Hoy)!;
                L($"C2 porcentaje {c}", T(r, a) + $" porPresentacion={r.PrecioPorPresentacion(Rollo)}");
            }
        }
        {
            var a = PP(1);
            L("C3 9999", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(1), false, [Regla(TipoPromocion.Porcentaje, cent: 9999)], Hoy), a));
            L("C3 especial 0", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(1), false, [Regla(TipoPromocion.PrecioEspecial, precio: 0)], Hoy), a));
            L("C3 segundo a mitad 2 de 1c", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(2), false, [Regla(TipoPromocion.SegundoAMitad)], Hoy), a));
        }
        {
            var a = PP(1000);
            var id = Guid.Parse("0190c5a2-7f3b-7c1e-9a4d-00000000abcd");
            var primera = Regla(TipoPromocion.Porcentaje, cent: 1000, id: id, nombre: "primera");
            var segunda = Regla(TipoPromocion.PrecioEspecial, precio: 900, id: id, nombre: "segunda");
            L("C5 [primera, segunda]", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(1), false, [primera, segunda], Hoy), a));
            L("C5 [segunda, primera]", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(1), false, [segunda, primera], Hoy), a));
            var b7 = Regla(TipoPromocion.Porcentaje, cent: 1000, id: Guid.Parse("00000000-7fff-0000-0000-000000000000"), nombre: "7fff");
            var b8 = Regla(TipoPromocion.PrecioEspecial, precio: 900, id: Guid.Parse("00000000-8000-0000-0000-000000000000"), nombre: "8000");
            L("C5b [8000, 7fff]", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(1), false, [b8, b7], Hoy), a));
            var c7 = Regla(TipoPromocion.Porcentaje, cent: 1000, id: Guid.Parse("00000000-0000-7fff-0000-000000000000"), nombre: "c7fff");
            var c8 = Regla(TipoPromocion.PrecioEspecial, precio: 900, id: Guid.Parse("00000000-0000-8000-0000-000000000000"), nombre: "c8000");
            L("C5c [c8000, c7fff]", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(1), false, [c8, c7], Hoy), a));
        }
        {
            var pr = Propio(Docena, 7000); var a = PP(800); var tres = Regla(TipoPromocion.LlevaNPagaM, 3, 2, nombre: "3x2");
            L("C6 3 docenas", T(CalculoPrecio.Calcular([pr, a], Matriz, Docena, Cantidad.Piezas(3), false, [tres], Hoy), pr, a));
            L("C6 3.5 docenas", T(CalculoPrecio.Calcular([pr, a], Matriz, Docena, Cantidad.Unidades(3.5m), false, [tres], Hoy), pr, a));
            L("C6 2 docenas mitad", T(CalculoPrecio.Calcular([pr, a], Matriz, Docena, Cantidad.Piezas(2), false, [Regla(TipoPromocion.SegundoAMitad, nombre: "mitad")], Hoy), pr, a));
            L("C7 especial 500", T(CalculoPrecio.Calcular([pr, a], Matriz, Docena, Cantidad.Piezas(1), false, [Regla(TipoPromocion.PrecioEspecial, precio: 500, nombre: "esp")], Hoy), pr, a));
        }
        {
            var a = PP(1000);
            foreach (var d in new[] { 128, 128 | 4, -1, 128 | 2 })
                L($"C8 dias {d}", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(1), false, [Regla(TipoPromocion.Porcentaje, cent: 1000, dias: (DiasDeLaSemana)d, nombre: "10")], Hoy), a));
            L("C8 hoy es", Hoy.DayOfWeek.ToString());
        }
        {
            var a = PP(1000); var tres = Regla(TipoPromocion.LlevaNPagaM, 3, 2, nombre: "3x2");
            L("C9 sin dia", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [tres], null), a));
            L("C9 devolucion", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(-3), false, [tres], Hoy), a));
            L("C9 cero", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Cero, false, [tres], Hoy), a));
        }
        {
            var a = PP(1); var enorme = Regla(TipoPromocion.LlevaNPagaM, int.MaxValue, 1, nombre: "enorme");
            L("C10 lleva int.Max", Intento(() => T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(int.MaxValue), false, [enorme], Hoy), a)));
            L("C10 lleva int.Max menos", Intento(() => T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(int.MaxValue - 1L), false, [enorme], Hoy), a)));
        }
        {
            var a = PP(1000); var tres = Regla(TipoPromocion.LlevaNPagaM, 3, 2, nombre: "3x2");
            L("C12 2 con 5%", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(2), false, [tres], Hoy, 500), a));
            L("D3 descuento int.Max", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [tres], Hoy, int.MaxValue), a));
        }
        {
            var a = PP(5_000_000_000_000_000);
            L("D1 10 piezas", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(10)), a));
            L("D1 3x2", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [Regla(TipoPromocion.LlevaNPagaM, 3, 2, nombre: "3x2")], Hoy), a));
            L("D1 10%", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [Regla(TipoPromocion.Porcentaje, cent: 1000, nombre: "10")], Hoy), a));
            L("D1 descuento 5%", T(CalculoPrecio.Calcular([a], Matriz, Pieza, Cantidad.Piezas(3), false, [Regla(TipoPromocion.LlevaNPagaM, 3, 2, nombre: "3x2")], Hoy, 500), a));
            var b = PP(long.MaxValue / 1000);
            L("D2 no cabe", Intento(() => T(CalculoPrecio.Calcular([b], Matriz, Pieza, Cantidad.Piezas(2000)), b)));
            L("D2 docena no cabe", Intento(() => T(CalculoPrecio.Calcular([b], Matriz, Docena, Cantidad.Piezas(100)), b)));
        }
        {
            var rollo7 = new Presentacion { VarianteId = Cuaderno, Nombre = "Rollo 7", Factor = Cantidad.Piezas(7) };
            var tres = Regla(TipoPromocion.LlevaNPagaM, 3, 2);
            L("E1 rollo 7 m", CalculoPrecio.PrecioMasBajoCon(tres, [PP(2000), Propio(rollo7, 10000)], [Pieza, rollo7], Matriz)?.ToString(Inv) ?? "null");
            var grande = new Presentacion { VarianteId = Cuaderno, Nombre = "Grande", Factor = new Cantidad(4_294_967_297) };
            L("E1 factor de 33 bits", CalculoPrecio.PrecioMasBajoCon(Regla(TipoPromocion.PrecioEspecial, precio: 7), [Propio(grande, 100_000_000)], [grande], Matriz)?.ToString(Inv) ?? "null");
            var grande2 = new Presentacion { VarianteId = Cuaderno, Nombre = "Grande2", Factor = new Cantidad(9_007_199_254_740_993) };
            L("E1 factor de 54 bits 3x2", CalculoPrecio.PrecioMasBajoCon(tres, [Propio(grande2, 1_000_000_007)], [grande2], Matriz)?.ToString(Inv) ?? "null");
            L("E2 especial 1020", CalculoPrecio.PrecioMasBajoCon(Regla(TipoPromocion.PrecioEspecial, precio: 1020), [PP(1000), Propio(Docena, 12500), Propio(Docena, 11000, 5)], [Pieza, Docena], Matriz)?.ToString(Inv) ?? "null");
            L("E2 especial 1020 sin el mayoreo propio", CalculoPrecio.PrecioMasBajoCon(Regla(TipoPromocion.PrecioEspecial, precio: 1020), [PP(1000), Propio(Docena, 12500)], [Pieza, Docena], Matriz)?.ToString(Inv) ?? "null");
            var cero = new Presentacion { VarianteId = Cuaderno, Nombre = "Cero", Factor = Cantidad.Cero };
            L("E3 factor cero", CalculoPrecio.PrecioMasBajoCon(Regla(TipoPromocion.PrecioEspecial, precio: 500), [Propio(cero, 12500)], [cero], Matriz)?.ToString(Inv) ?? "null");
        }
    }
}
