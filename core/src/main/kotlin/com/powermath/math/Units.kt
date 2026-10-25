package com.powermath.math

import java.math.BigInteger

enum class UnitCategory(val title: String) {
    LENGTH("Length"), MASS("Mass"), TIME("Time"), AREA("Area"), VOLUME("Volume"),
    SPEED("Speed"), TEMPERATURE("Temperature"), ANGLE("Angle"), DATA("Data"), ENERGY("Energy"), PRESSURE("Pressure")
}

data class UnitDef(val symbol: String, val name: String, val category: UnitCategory, val toBase: Rat)

/** Exact unit conversion using rational factors (temperature is handled separately because it is not a pure factor). */
object Units {
    private fun r(s: String): Rat { val p = s.split('.'); return Rat.fromDecimal(p[0], p.getOrElse(1) { "" }, 0) }
    private fun u(sym: String, name: String, c: UnitCategory, f: String) = UnitDef(sym, name, c, r(f))
    private fun uq(sym: String, name: String, c: UnitCategory, n: Long, d: Long) = UnitDef(sym, name, c, Rat.of(n, d))

    val all: List<UnitDef> = listOf(
        u("mm", "millimetre", UnitCategory.LENGTH, "0.001"), u("cm", "centimetre", UnitCategory.LENGTH, "0.01"), u("m", "metre", UnitCategory.LENGTH, "1"),
        u("km", "kilometre", UnitCategory.LENGTH, "1000"), u("in", "inch", UnitCategory.LENGTH, "0.0254"), u("ft", "foot", UnitCategory.LENGTH, "0.3048"),
        u("yd", "yard", UnitCategory.LENGTH, "0.9144"), u("mi", "mile", UnitCategory.LENGTH, "1609.344"), u("nmi", "nautical mile", UnitCategory.LENGTH, "1852"),
        u("mg", "milligram", UnitCategory.MASS, "0.000001"), u("g", "gram", UnitCategory.MASS, "0.001"), u("kg", "kilogram", UnitCategory.MASS, "1"),
        u("t", "tonne", UnitCategory.MASS, "1000"), u("oz", "ounce", UnitCategory.MASS, "0.028349523125"), u("lb", "pound", UnitCategory.MASS, "0.45359237"),
        u("ms", "millisecond", UnitCategory.TIME, "0.001"), u("s", "second", UnitCategory.TIME, "1"), u("min", "minute", UnitCategory.TIME, "60"),
        u("h", "hour", UnitCategory.TIME, "3600"), u("day", "day", UnitCategory.TIME, "86400"), u("week", "week", UnitCategory.TIME, "604800"),
        u("mm²", "square millimetre", UnitCategory.AREA, "0.000001"), u("cm²", "square centimetre", UnitCategory.AREA, "0.0001"), u("m²", "square metre", UnitCategory.AREA, "1"),
        u("ha", "hectare", UnitCategory.AREA, "10000"), u("km²", "square kilometre", UnitCategory.AREA, "1000000"),
        u("ft²", "square foot", UnitCategory.AREA, "0.09290304"), u("acre", "acre", UnitCategory.AREA, "4046.8564224"),
        u("mL", "millilitre", UnitCategory.VOLUME, "0.001"), u("L", "litre", UnitCategory.VOLUME, "1"), u("m³", "cubic metre", UnitCategory.VOLUME, "1000"),
        u("tsp", "teaspoon (US)", UnitCategory.VOLUME, "0.00492892159375"), u("cup", "cup (US)", UnitCategory.VOLUME, "0.2365882365"), u("gal", "gallon (US)", UnitCategory.VOLUME, "3.785411784"),
        uq("m/s", "metre per second", UnitCategory.SPEED, 1, 1), uq("km/h", "kilometre per hour", UnitCategory.SPEED, 5, 18),
        u("mph", "mile per hour", UnitCategory.SPEED, "0.44704"), uq("kn", "knot", UnitCategory.SPEED, 1852, 3600),
        UnitDef("°C", "degree Celsius", UnitCategory.TEMPERATURE, Rat.ONE), UnitDef("°F", "degree Fahrenheit", UnitCategory.TEMPERATURE, Rat.ONE), UnitDef("K", "kelvin", UnitCategory.TEMPERATURE, Rat.ONE),
        UnitDef("rev", "revolution", UnitCategory.ANGLE, Rat.ONE), uq("deg", "degree", UnitCategory.ANGLE, 1, 360), uq("grad", "gradian", UnitCategory.ANGLE, 1, 400),
        uq("bit", "bit", UnitCategory.DATA, 1, 8), uq("B", "byte", UnitCategory.DATA, 1, 1), uq("KB", "kilobyte", UnitCategory.DATA, 1000, 1),
        uq("MB", "megabyte", UnitCategory.DATA, 1000000, 1), uq("GB", "gigabyte", UnitCategory.DATA, 1000000000, 1), uq("KiB", "kibibyte", UnitCategory.DATA, 1024, 1),
        uq("MiB", "mebibyte", UnitCategory.DATA, 1048576, 1), uq("GiB", "gibibyte", UnitCategory.DATA, 1073741824, 1),
        uq("J", "joule", UnitCategory.ENERGY, 1, 1), uq("kJ", "kilojoule", UnitCategory.ENERGY, 1000, 1), u("cal", "calorie", UnitCategory.ENERGY, "4.184"),
        uq("kcal", "kilocalorie", UnitCategory.ENERGY, 4184, 1), uq("Wh", "watt-hour", UnitCategory.ENERGY, 3600, 1), uq("kWh", "kilowatt-hour", UnitCategory.ENERGY, 3600000, 1),
        uq("Pa", "pascal", UnitCategory.PRESSURE, 1, 1), uq("kPa", "kilopascal", UnitCategory.PRESSURE, 1000, 1), uq("bar", "bar", UnitCategory.PRESSURE, 100000, 1),
        u("atm", "atmosphere", UnitCategory.PRESSURE, "101325"), u("psi", "pound per square inch", UnitCategory.PRESSURE, "6894.757293168")
    )

    fun of(category: UnitCategory) = all.filter { it.category == category }

    fun find(symbol: String): UnitDef = all.firstOrNull { it.symbol == symbol }
        ?: all.firstOrNull { it.symbol.equals(symbol, ignoreCase = true) }
        ?: throw MathError(ErrorKind.UNDEFINED, "Unknown unit '$symbol'")

    /** Revolutions in a full turn convert through 2π only for display elsewhere; here an angle category stays rational. */
    fun convert(value: Rat, from: UnitDef, to: UnitDef): Rat {
        if (from.category != to.category) throw MathError(ErrorKind.DOMAIN, "Cannot convert ${from.category.title.lowercase()} to ${to.category.title.lowercase()}")
        if (from.category == UnitCategory.TEMPERATURE) return temperature(value, from.symbol, to.symbol)
        return value * from.toBase / to.toBase
    }

    private fun temperature(v: Rat, from: String, to: String): Rat {
        val nine5 = Rat.of(9, 5)
        val kelvin = when (from) {
            "°C" -> v + r("273.15")
            "°F" -> (v - Rat.of(32)) / nine5 + r("273.15")
            else -> v
        }
        if (kelvin.signum < 0) throw MathError(ErrorKind.DOMAIN, "That is below absolute zero")
        return when (to) {
            "°C" -> kelvin - r("273.15")
            "°F" -> (kelvin - r("273.15")) * nine5 + Rat.of(32)
            else -> kelvin
        }
    }
}
