package com.powermath.math

import java.math.BigInteger

enum class Shape(val title: String, val params: List<String>, val solid: Boolean) {
    CIRCLE("Circle", listOf("radius r"), false),
    RECTANGLE("Rectangle", listOf("width w", "height h"), false),
    SQUARE("Square", listOf("side s"), false),
    TRIANGLE("Triangle (base, height)", listOf("base b", "height h"), false),
    TRIANGLE_SSS("Triangle (3 sides)", listOf("side a", "side b", "side c"), false),
    RIGHT_TRIANGLE("Right triangle (legs)", listOf("leg a", "leg b"), false),
    TRAPEZOID("Trapezoid", listOf("base a", "base b", "height h"), false),
    PARALLELOGRAM("Parallelogram", listOf("base b", "height h"), false),
    RHOMBUS("Rhombus", listOf("diagonal d1", "diagonal d2"), false),
    ELLIPSE("Ellipse", listOf("semi-axis a", "semi-axis b"), false),
    SECTOR("Circle sector", listOf("radius r", "angle (degrees)"), false),
    REGULAR_POLYGON("Regular polygon", listOf("sides n", "side length s"), false),
    SPHERE("Sphere", listOf("radius r"), true),
    CUBE("Cube", listOf("side s"), true),
    CUBOID("Cuboid", listOf("length l", "width w", "height h"), true),
    CYLINDER("Cylinder", listOf("radius r", "height h"), true),
    CONE("Cone", listOf("radius r", "height h"), true)
}

data class GeoResult(val rows: List<Pair<String, Value>>, val steps: List<String>)

/** Exact area / perimeter / volume formulas. Results stay exact (fractions, √, π) where the value type allows it. */
object Geometry {
    private val PI: Value = Value.P(Rat.ONE)
    private fun v(n: Int) = Value.of(n.toLong())
    private fun mul(vararg x: Value): Value = x.reduce { a, b -> Ops.mul(a, b) }
    private fun sq(x: Value) = Ops.mul(x, x)
    private fun cube(x: Value) = Ops.mul(x, sq(x))
    private fun half(x: Value) = Ops.div(x, v(2))

    private fun positive(p: List<Value>, names: List<String>) {
        p.forEachIndexed { i, x ->
            if (Ops.sign(x) <= 0) throw MathError(ErrorKind.DOMAIN, "The ${names[i]} must be greater than 0")
        }
    }

    fun compute(shape: Shape, p: List<Value>): GeoResult {
        if (p.size != shape.params.size) throw MathError(ErrorKind.SYNTAX, "${shape.title} needs ${shape.params.size} value(s)")
        if (shape != Shape.REGULAR_POLYGON && shape != Shape.SECTOR) positive(p, shape.params)
        val rows = ArrayList<Pair<String, Value>>()
        val steps = ArrayList<String>()
        when (shape) {
            Shape.CIRCLE -> {
                rows += "Area" to mul(PI, sq(p[0])); rows += "Circumference" to mul(v(2), PI, p[0]); rows += "Diameter" to mul(v(2), p[0])
                steps += "Area = πr²"; steps += "Circumference = 2πr"
            }
            Shape.RECTANGLE -> {
                rows += "Area" to mul(p[0], p[1]); rows += "Perimeter" to mul(v(2), Ops.add(p[0], p[1]))
                rows += "Diagonal" to Ops.sqrt(Ops.add(sq(p[0]), sq(p[1])))
                steps += "Area = w·h"; steps += "Perimeter = 2(w + h)"; steps += "Diagonal = √(w² + h²)"
            }
            Shape.SQUARE -> {
                rows += "Area" to sq(p[0]); rows += "Perimeter" to mul(v(4), p[0]); rows += "Diagonal" to Ops.sqrt(mul(v(2), sq(p[0])))
                steps += "Area = s²"; steps += "Perimeter = 4s"; steps += "Diagonal = s√2"
            }
            Shape.TRIANGLE -> { rows += "Area" to half(mul(p[0], p[1])); steps += "Area = ½·b·h" }
            Shape.TRIANGLE_SSS -> {
                val (a, b, c) = p
                if (Ops.compare(Ops.add(a, b), c) <= 0 || Ops.compare(Ops.add(a, c), b) <= 0 || Ops.compare(Ops.add(b, c), a) <= 0)
                    throw MathError(ErrorKind.DOMAIN, "These sides cannot form a triangle (each side must be shorter than the sum of the others)")
                val s = half(Ops.add(Ops.add(a, b), c))
                val area = Ops.sqrt(mul(s, Ops.sub(s, a), Ops.sub(s, b), Ops.sub(s, c)))
                rows += "Area" to area; rows += "Perimeter" to Ops.add(Ops.add(a, b), c)
                steps += "Semi-perimeter s = (a + b + c)/2"; steps += "Heron's formula: Area = √(s(s − a)(s − b)(s − c))"
            }
            Shape.RIGHT_TRIANGLE -> {
                val c = Ops.sqrt(Ops.add(sq(p[0]), sq(p[1])))
                rows += "Hypotenuse" to c; rows += "Area" to half(mul(p[0], p[1])); rows += "Perimeter" to Ops.add(Ops.add(p[0], p[1]), c)
                steps += "Pythagoras: c = √(a² + b²)"; steps += "Area = ½·a·b"
            }
            Shape.TRAPEZOID -> { rows += "Area" to half(mul(Ops.add(p[0], p[1]), p[2])); steps += "Area = ½(a + b)·h" }
            Shape.PARALLELOGRAM -> { rows += "Area" to mul(p[0], p[1]); steps += "Area = b·h" }
            Shape.RHOMBUS -> {
                val side = Ops.sqrt(Ops.add(sq(half(p[0])), sq(half(p[1]))))
                rows += "Area" to half(mul(p[0], p[1])); rows += "Side" to side; rows += "Perimeter" to mul(v(4), side)
                steps += "Area = ½·d₁·d₂"; steps += "Side = √((d₁/2)² + (d₂/2)²)"
            }
            Shape.ELLIPSE -> {
                rows += "Area" to mul(PI, p[0], p[1])
                val a = Ops.toDouble(p[0]); val b = Ops.toDouble(p[1])
                val h = ((a - b) * (a - b)) / ((a + b) * (a + b))
                rows += "Perimeter (approx.)" to Value.D(Math.PI * (a + b) * (1 + 3 * h / (10 + Math.sqrt(4 - 3 * h))))
                steps += "Area = πab"; steps += "Perimeter uses Ramanujan's approximation"
            }
            Shape.SECTOR -> {
                if (Ops.sign(p[0]) <= 0) throw MathError(ErrorKind.DOMAIN, "The radius must be greater than 0")
                if (Ops.sign(p[1]) <= 0 || Ops.compare(p[1], v(360)) > 0) throw MathError(ErrorKind.DOMAIN, "The angle must be between 0 and 360 degrees")
                val frac = Ops.div(p[1], v(360))
                rows += "Area" to mul(PI, sq(p[0]), frac); rows += "Arc length" to mul(v(2), PI, p[0], frac)
                steps += "Area = πr²·θ/360"; steps += "Arc length = 2πr·θ/360"
            }
            Shape.REGULAR_POLYGON -> {
                val nv = p[0]
                if (nv !is Value.Q || !nv.r.isInteger || nv.r.num < BigInteger.valueOf(3) || nv.r.num > BigInteger.valueOf(1000))
                    throw MathError(ErrorKind.DOMAIN, "The number of sides must be a whole number from 3 to 1000")
                if (Ops.sign(p[1]) <= 0) throw MathError(ErrorKind.DOMAIN, "The side length must be greater than 0")
                val n = nv.r.num.toInt(); val s2 = sq(p[1])
                val area: Value = when (n) {
                    3 -> Ops.mul(Ops.div(Ops.sqrt(v(3)), v(4)), s2)
                    4 -> s2
                    6 -> Ops.mul(Ops.div(Ops.mul(v(3), Ops.sqrt(v(3))), v(2)), s2)
                    else -> Value.D(n * Ops.toDouble(s2) / (4 * Math.tan(Math.PI / n)))
                }
                rows += "Perimeter" to mul(v(n), p[1]); rows += "Area" to area
                rows += "Interior angle (degrees)" to Value.Q(Rat.of((n - 2) * 180L, n.toLong()))
                steps += "Perimeter = n·s"; steps += "Area = n·s² / (4·tan(180°/n))"
            }
            Shape.SPHERE -> {
                rows += "Volume" to mul(Value.of(4, 3), PI, cube(p[0])); rows += "Surface area" to mul(v(4), PI, sq(p[0]))
                steps += "Volume = 4/3·πr³"; steps += "Surface area = 4πr²"
            }
            Shape.CUBE -> {
                rows += "Volume" to cube(p[0]); rows += "Surface area" to mul(v(6), sq(p[0])); rows += "Space diagonal" to Ops.sqrt(mul(v(3), sq(p[0])))
                steps += "Volume = s³"; steps += "Surface area = 6s²"; steps += "Space diagonal = s√3"
            }
            Shape.CUBOID -> {
                val (l, w, h) = p
                rows += "Volume" to mul(l, w, h)
                rows += "Surface area" to mul(v(2), Ops.add(Ops.add(mul(l, w), mul(l, h)), mul(w, h)))
                rows += "Space diagonal" to Ops.sqrt(Ops.add(Ops.add(sq(l), sq(w)), sq(h)))
                steps += "Volume = l·w·h"; steps += "Surface area = 2(lw + lh + wh)"
            }
            Shape.CYLINDER -> {
                rows += "Volume" to mul(PI, sq(p[0]), p[1]); rows += "Lateral area" to mul(v(2), PI, p[0], p[1])
                rows += "Total surface area" to mul(v(2), PI, p[0], Ops.add(p[0], p[1]))
                steps += "Volume = πr²h"; steps += "Lateral area = 2πrh"; steps += "Total = 2πr(r + h)"
            }
            Shape.CONE -> {
                val l = Ops.sqrt(Ops.add(sq(p[0]), sq(p[1])))
                rows += "Volume" to mul(Value.of(1, 3), PI, sq(p[0]), p[1]); rows += "Slant height" to l
                rows += "Lateral area" to mul(PI, p[0], l); rows += "Total surface area" to mul(PI, p[0], Ops.add(p[0], l))
                steps += "Volume = ⅓πr²h"; steps += "Slant height l = √(r² + h²)"; steps += "Lateral area = πrl"
            }
        }
        return GeoResult(rows, steps)
    }
}

/** Exact values on the unit circle for the special angles (multiples of 30° and 45°). */
data class UnitCirclePoint(val degrees: Int, val radians: Value, val cos: Value, val sin: Value, val tan: Value?)

object UnitCircle {
    val specialAngles: List<Int> = listOf(0, 30, 45, 60, 90, 120, 135, 150, 180, 210, 225, 240, 270, 300, 315, 330, 360)

    fun at(degrees: Int): UnitCirclePoint? {
        val s = Trig.sinExact(degrees) ?: return null
        val c = Trig.cosExact(degrees) ?: return null
        val t = try { Trig.tanExact(degrees) } catch (e: MathError) { null }
        return UnitCirclePoint(degrees, Ops.mkP(Rat.of(degrees.toLong(), 180L)), c, s, t)
    }
}

data class StatsResult(
    val count: Int, val sum: Rat, val mean: Rat, val median: Rat, val modes: List<Rat>, val min: Rat, val max: Rat,
    val range: Rat, val variancePop: Rat, val varianceSample: Rat?, val stdPop: Value, val stdSample: Value?
)

object Stats {
    fun analyze(values: List<Rat>): StatsResult {
        if (values.isEmpty()) throw MathError(ErrorKind.SYNTAX, "Enter at least one number")
        if (values.size > 5000) throw MathError(ErrorKind.LIMIT, "Too many values (max 5000)")
        val n = values.size
        val sorted = values.sorted()
        val sum = values.fold(Rat.ZERO) { a, b -> a + b }
        val mean = sum / Rat.of(n)
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / Rat.of(2)
        val counts = values.groupingBy { it }.eachCount()
        val top = counts.values.max()
        val modes = if (top == 1) emptyList() else counts.filterValues { it == top }.keys.sorted()
        val ss = values.fold(Rat.ZERO) { a, x -> a + (x - mean) * (x - mean) }
        val varPop = ss / Rat.of(n)
        val varSample = if (n > 1) ss / Rat.of(n - 1) else null
        return StatsResult(n, sum, mean, median, modes, sorted.first(), sorted.last(), sorted.last() - sorted.first(),
            varPop, varSample, Ops.sqrtRat(varPop), varSample?.let { Ops.sqrtRat(it) })
    }
}
