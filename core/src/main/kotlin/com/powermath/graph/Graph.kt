package com.powermath.graph

import com.powermath.math.*

data class Pt(val x: Double, val y: Double)

/** Coordinate transformation only; contains no mathematics about functions. */
data class Viewport(val xMin: Double, val xMax: Double, val yMin: Double, val yMax: Double) {
    val width get() = xMax - xMin
    val height get() = yMax - yMin

    fun toScreenX(x: Double, wPx: Float): Float = ((x - xMin) / width * wPx).toFloat()
    fun toScreenY(y: Double, hPx: Float): Float = (hPx - (y - yMin) / height * hPx).toFloat()
    fun toWorldX(px: Float, wPx: Float): Double = xMin + px.toDouble() / wPx.toDouble() * width
    fun toWorldY(py: Float, hPx: Float): Double = yMin + (hPx.toDouble() - py.toDouble()) / hPx.toDouble() * height

    /** Dragging by (dx, dy) pixels moves the content with the finger. */
    fun pan(dxPx: Float, dyPx: Float, wPx: Float, hPx: Float): Viewport {
        val dx = dxPx.toDouble() / wPx.toDouble() * width
        val dy = dyPx.toDouble() / hPx.toDouble() * height
        return Viewport(xMin - dx, xMax - dx, yMin + dy, yMax + dy)
    }

    /** factor > 1 zooms in. The world point under the focus pixel stays fixed. */
    fun zoom(factor: Double, fxPx: Float, fyPx: Float, wPx: Float, hPx: Float): Viewport {
        if (factor <= 0.0 || factor.isNaN() || factor.isInfinite()) return this
        val newW = (width / factor).coerceIn(1e-6, 1e9)
        val newH = (height / factor).coerceIn(1e-6, 1e9)
        val fx = toWorldX(fxPx, wPx)
        val fy = toWorldY(fyPx, hPx)
        val rx = (fx - xMin) / width
        val ry = (fy - yMin) / height
        return Viewport(fx - rx * newW, fx + (1 - rx) * newW, fy - ry * newH, fy + (1 - ry) * newH)
    }

    companion object { val DEFAULT = Viewport(-10.0, 10.0, -10.0, 10.0) }
}

/** Evaluates the shared Expr model with doubles. Returns NaN for invalid real-domain points. */
class DoubleEval(private val scope: Scope, private val angle: AngleMode) {
    private var depth = 0

    private fun rad(x: Double) = if (angle == AngleMode.DEG) Math.toRadians(x) else x
    private fun deg(x: Double) = if (angle == AngleMode.DEG) Math.toDegrees(x) else x

    fun eval(e: Expr, vars: Map<String, Double>): Double = when (e) {
        is Expr.Num -> e.value.toDouble()
        is Expr.Const -> when (e.name) {
            "pi" -> Math.PI; "e" -> Math.E; "tau" -> 2 * Math.PI; "phi" -> (1 + Math.sqrt(5.0)) / 2
            else -> Double.NaN
        }
        is Expr.Var -> vars[e.name] ?: scope.variable(e.name)?.let { Ops.toDouble(it) }
            ?: throw MathError(ErrorKind.UNDEFINED, "Undefined variable '${e.name}'")
        is Expr.Neg -> -eval(e.e, vars)
        is Expr.Percent -> eval(e.e, vars) / 100.0
        is Expr.Fact -> fact(eval(e.e, vars))
        is Expr.Bin -> {
            if ((e.op == '+' || e.op == '-') && e.r is Expr.Percent) {
                val l = eval(e.l, vars); val p = eval(e.r.e, vars) / 100.0
                if (e.op == '+') l + l * p else l - l * p
            } else {
                val l = eval(e.l, vars); val r = eval(e.r, vars)
                when (e.op) {
                    '+' -> l + r; '-' -> l - r; '*' -> l * r
                    '/' -> if (r == 0.0) Double.NaN else l / r
                    '^' -> pow(l, r)
                    else -> Double.NaN
                }
            }
        }
        is Expr.Call -> call(e, vars)
        else -> throw MathError(ErrorKind.UNSUPPORTED, "Cannot graph this expression")
    }

    private fun pow(b: Double, x: Double): Double {
        if (b < 0 && x != Math.rint(x)) {
            val q = nearestOddDenominator(x) ?: return Double.NaN
            val p = Math.rint(x * q)
            val r = Math.pow(-b, x)
            return if (p.toLong() % 2L != 0L) -r else r
        }
        return Math.pow(b, x)
    }

    private fun nearestOddDenominator(x: Double): Int? {
        for (q in 1..15 step 2) { if (Math.abs(x * q - Math.rint(x * q)) < 1e-9) return q }
        return null
    }

    private fun fact(x: Double): Double {
        if (x < 0 || x != Math.rint(x) || x > 170) return Double.NaN
        var r = 1.0
        for (i in 2..x.toInt()) r *= i
        return r
    }

    private fun call(c: Expr.Call, vars: Map<String, Double>): Double {
        val fd = scope.function(c.name)
        if (fd != null && c.name !in Names.functions) {
            if (fd.params.size != c.args.size || depth > 40) return Double.NaN
            val a = c.args.map { eval(it, vars) }
            depth++
            try { return eval(fd.body, fd.params.zip(a).toMap()) } finally { depth-- }
        }
        val a = c.args.map { eval(it, vars) }
        val x = a.getOrElse(0) { Double.NaN }
        return when (c.name) {
            "sqrt" -> if (x < 0) Double.NaN else Math.sqrt(x)
            "root" -> if (x == 0.0) Double.NaN else pow(a[1], 1.0 / x)
            "abs" -> Math.abs(x)
            "sin" -> Math.sin(rad(x)); "cos" -> Math.cos(rad(x)); "tan" -> Math.tan(rad(x))
            "asin" -> if (x < -1 || x > 1) Double.NaN else deg(Math.asin(x))
            "acos" -> if (x < -1 || x > 1) Double.NaN else deg(Math.acos(x))
            "atan" -> deg(Math.atan(x))
            "sinh" -> Math.sinh(x); "cosh" -> Math.cosh(x); "tanh" -> Math.tanh(x)
            "ln" -> if (x <= 0) Double.NaN else Math.log(x)
            "log" -> if (a.size == 1) (if (x <= 0) Double.NaN else Math.log10(x)) else if (a[0] <= 0 || a[1] <= 0 || a[0] == 1.0) Double.NaN else Math.log(a[1]) / Math.log(a[0])
            "exp" -> Math.exp(x)
            "mean" -> a.average()
            "min" -> a.minOrNull() ?: Double.NaN
            "max" -> a.maxOrNull() ?: Double.NaN
            "floor" -> Math.floor(x); "ceil" -> Math.ceil(x)
            "sgn" -> Math.signum(x)
            "fact" -> fact(x)
            "mod" -> if (a[1] == 0.0) Double.NaN else ((x % a[1]) + a[1]) % a[1]
            "delta" -> a[1] * a[1] - 4 * a[0] * a[2]
            else -> throw MathError(ErrorKind.UNSUPPORTED, "Function '${c.name}' cannot be graphed")
        }
    }
}

/** A plottable single-variable function compiled from user text with the shared parser. */
class GraphFunction private constructor(
    val text: String, val label: String, val expr: Expr, val varName: String,
    private val evaluator: DoubleEval
) {
    fun eval(x: Double): Double = try { evaluator.eval(expr, mapOf(varName to x)) } catch (e: MathError) { Double.NaN }

    companion object {
        fun compileExpr(body: Expr, scope: Scope, angle: AngleMode, v: String) = GraphFunction("", "", body, v, DoubleEval(scope, angle))

        /** Accepts "f(x) = x^2", "y = x^2", "x^2 - 4" or a saved function call such as "f(x)". */
        fun compile(text: String, scope: Scope, angle: AngleMode): GraphFunction {
            val src = ExprUtil.balanceParens(text.trim())
            if (src.isEmpty()) throw MathError(ErrorKind.SYNTAX, "Enter a function to plot")
            // "f(x) = ..." always means "plot this definition", even when a saved f exists (nothing is saved here).
            val defMatch = Regex("^([A-Za-z_]+)\\(([A-Za-z_]+)\\)\\s*=(?!=)(.*)$").find(src)
            val parseScope = if (defMatch != null && !Regex("[<>=!≤≥≠]").containsMatchIn(defMatch.groupValues[3])) {
                val hidden = defMatch.groupValues[1]
                object : Scope {
                    override fun variable(name: String) = scope.variable(name)
                    override fun function(name: String) = if (name == hidden) null else scope.function(name)
                }
            } else scope
            val st = Parser(parseScope).parse(src)
            val body: Expr
            val label: String
            var paramName: String? = null
            when {
                st is Expr.FuncDefStmt -> {
                    if (st.params.size != 1) throw MathError(ErrorKind.UNSUPPORTED, "Only functions of one variable can be graphed")
                    body = st.body; paramName = st.params[0]; label = "${st.name}(${st.params[0]}) = ${st.bodySource}"
                }
                st is Expr.Rel && st.op == RelOp.EQ && st.l is Expr.Var && st.l.name == "y" -> {
                    body = st.r; label = "y = ${src.substringAfter('=').trim()}"
                }
                st is Expr.Rel || st is Expr.Assign -> throw MathError(ErrorKind.UNSUPPORTED, "Enter y = ..., f(x) = ... or an expression in x")
                st is Expr.Call && st.name in Names.commands -> throw MathError(ErrorKind.UNSUPPORTED, "Commands cannot be graphed")
                else -> { body = st; label = "y = $src" }
            }
            val free = ExprUtil.freeVars(body).filter { scope.variable(it) == null }
            val v = paramName ?: when (free.size) {
                0 -> "x"
                1 -> free.first()
                else -> throw MathError(ErrorKind.UNSUPPORTED, "Expression has several variables (${free.joinToString(", ")}); assign values to all but one")
            }
            val extra = free.filter { it != v }
            if (extra.isNotEmpty()) throw MathError(ErrorKind.UNDEFINED, "Undefined variable '${extra.first()}'")
            return GraphFunction(text.trim(), label, body, v, DoubleEval(scope, angle))
        }
    }
}

object GraphEngine {
    /** Samples f across the viewport; returns continuous segments, breaking at invalid values and asymptotes. */
    fun sample(f: (Double) -> Double, vp: Viewport, samples: Int = 800): List<List<Pt>> {
        val segs = ArrayList<List<Pt>>()
        var cur = ArrayList<Pt>()
        var prev: Pt? = null
        val yr = vp.height
        fun flush() { if (cur.size >= 2) segs.add(cur); cur = ArrayList() }
        for (i in 0..samples) {
            val x = vp.xMin + vp.width * i / samples
            val y = try { f(x) } catch (e: Exception) { Double.NaN }
            if (y.isNaN() || y.isInfinite()) { flush(); prev = null; continue }
            val p = prev
            if (p != null && Math.abs(y - p.y) > 2 * yr && p.y * y < 0) flush()
            cur.add(Pt(x, y)); prev = Pt(x, y)
        }
        flush()
        return segs
    }

    fun niceStep(range: Double, target: Int = 8): Double {
        val raw = range / target
        val mag = Math.pow(10.0, Math.floor(Math.log10(raw)))
        val norm = raw / mag
        val nice = when { norm <= 1.0 -> 1.0; norm <= 2.0 -> 2.0; norm <= 5.0 -> 5.0; else -> 10.0 }
        return nice * mag
    }

    fun ticks(min: Double, max: Double, target: Int = 8): List<Double> {
        if (!(max > min)) return emptyList()
        val step = niceStep(max - min, target)
        val first = Math.ceil(min / step)
        val out = ArrayList<Double>()
        var k = first
        while (k * step <= max + step * 1e-9 && out.size < 200) { out.add(Math.round(k) * step); k += 1.0 }
        return out
    }

    fun tickLabel(v: Double, step: Double): String {
        if (Math.abs(v) < step * 1e-9) return "0"
        val decimals = Math.max(0, -Math.floor(Math.log10(step)).toInt())
        val abs = Math.abs(v)
        if (abs >= 1e7 || abs < 1e-5) return String.format(java.util.Locale.US, "%.2e", v)
        val s = java.math.BigDecimal(v).setScale(decimals, java.math.RoundingMode.HALF_UP).toPlainString()
        return if (s == "-0") "0" else s
    }

    /** Chooses a sensible first view from sampled values and interesting points (roots, vertex). */
    fun autoViewport(fns: List<(Double) -> Double>, interesting: List<Pt>): Viewport {
        var xMin = -10.0; var xMax = 10.0
        val ix = interesting.map { it.x }.filter { it.isFinite() && Math.abs(it) < 1e4 }
        if (ix.isNotEmpty()) {
            val lo = ix.min(); val hi = ix.max()
            val span = Math.max(hi - lo, 1.0)
            xMin = Math.min(xMin, lo - 0.25 * span)
            xMax = Math.max(xMax, hi + 0.25 * span)
        }
        val xr = xMax - xMin
        val ys = ArrayList<Double>()
        for (f in fns) for (i in 0..400) {
            val y = try { f(xMin + xr * i / 400) } catch (e: Exception) { Double.NaN }
            if (y.isFinite()) ys.add(y)
        }
        if (ys.isEmpty()) return Viewport(xMin, xMax, -xr / 2, xr / 2)
        ys.sort()
        val lo = ys[(ys.size * 0.05).toInt()]
        val hi = ys[Math.min(ys.size - 1, (ys.size * 0.95).toInt())]
        val iy = interesting.map { it.y }.filter { it.isFinite() }
        var yMin: Double; var yMax: Double
        if (hi - lo <= xr) {
            yMin = Math.min(lo, iy.minOrNull() ?: lo); yMax = Math.max(hi, iy.maxOrNull() ?: hi)
            if (yMax - yMin < 2.0) { val c = (yMax + yMin) / 2; yMin = c - 1.5; yMax = c + 1.5 }
            val pad = (yMax - yMin) * 0.15
            yMin -= pad; yMax += pad
        } else {
            val center = if (iy.isNotEmpty()) iy.average() else ys[ys.size / 2]
            yMin = center - xr / 2; yMax = center + xr / 2
        }
        return Viewport(xMin, xMax, yMin, yMax)
    }
}

data class Annotations(val roots: List<Pt>, val extrema: List<Pt>, val yIntercept: Pt?, val isQuadratic: Boolean) {
    val all: List<Pt> get() = roots + extrema + listOfNotNull(yIntercept)
    companion object { val EMPTY = Annotations(emptyList(), emptyList(), null, false) }
}

object GraphAnalyzer {
    /** Roots, extrema and y-intercept for polynomial functions, computed mathematically (never guessed). */
    fun analyze(fn: GraphFunction, scope: Scope, angle: AngleMode): Annotations {
        return try {
            val p = PolyBuilder(scope, angle).build(fn.expr)
            if (p.vars().any { it != fn.varName }) return Annotations.EMPTY
            val u = UPoly.fromM(p, fn.varName)
            if (u.degree < 1 || u.degree > PolyRoots.MAX_DEGREE) return Annotations.EMPTY
            val roots = PolyRoots.realRoots(u).map { Pt(it.d, 0.0) }
            val crit = PolyRoots.realRoots(u.derivative()).map { Pt(it.d, u.evalD(it.d)) }
            Annotations(roots, crit, Pt(0.0, u.c[0].toDouble()), u.degree == 2)
        } catch (e: MathError) { Annotations.EMPTY }
    }
}
