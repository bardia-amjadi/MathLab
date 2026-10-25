package com.powermath.math

import java.math.BigInteger

data class Mono(val vars: List<Pair<String, Int>>) {
    val degree: Int get() = vars.sumOf { it.second }
    fun times(o: Mono): Mono {
        val m = java.util.TreeMap<String, Int>()
        for ((n, e) in vars) m[n] = e
        for ((n, e) in o.vars) m[n] = (m[n] ?: 0) + e
        return Mono(m.map { Pair(it.key, it.value) })
    }
    fun exp(v: String): Int = vars.firstOrNull { it.first == v }?.second ?: 0
    companion object { val ONE = Mono(emptyList()) }
}

/** Sparse multivariate polynomial with exact rational coefficients. */
class MPoly(terms: Map<Mono, Rat>) {
    val terms: Map<Mono, Rat> = terms.filterValues { !it.isZero }

    companion object {
        val ZERO = MPoly(emptyMap())
        fun const(r: Rat) = MPoly(mapOf(Mono.ONE to r))
        fun variable(name: String) = MPoly(mapOf(Mono(listOf(Pair(name, 1))) to Rat.ONE))
        const val MAX_TERMS = 5000
        const val MAX_DEGREE = 64
    }

    val isZero get() = terms.isEmpty()
    val isConstant get() = terms.keys.all { it.vars.isEmpty() }
    fun constantValue(): Rat = terms[Mono.ONE] ?: Rat.ZERO
    val totalDegree get() = terms.keys.maxOfOrNull { it.degree } ?: 0
    fun vars(): Set<String> = terms.keys.flatMap { m -> m.vars.map { it.first } }.toCollection(java.util.TreeSet())
    fun degreeIn(v: String) = terms.keys.maxOfOrNull { it.exp(v) } ?: 0

    operator fun plus(o: MPoly): MPoly {
        val m = HashMap(terms)
        for ((k, v) in o.terms) m[k] = (m[k] ?: Rat.ZERO) + v
        return MPoly(m)
    }
    operator fun unaryMinus() = MPoly(terms.mapValues { -it.value })
    operator fun minus(o: MPoly) = this + (-o)
    operator fun times(o: MPoly): MPoly {
        if (terms.size.toLong() * o.terms.size > 250_000) throw MathError(ErrorKind.LIMIT, "Expression is too complex")
        val m = HashMap<Mono, Rat>()
        for ((k1, v1) in terms) for ((k2, v2) in o.terms) {
            val k = k1.times(k2)
            m[k] = (m[k] ?: Rat.ZERO) + v1 * v2
        }
        if (m.size > MAX_TERMS) throw MathError(ErrorKind.LIMIT, "Expression is too complex")
        return MPoly(m)
    }
    fun scale(r: Rat) = MPoly(terms.mapValues { it.value * r })
    fun pow(n: Int): MPoly {
        if (n < 0 || n > MAX_DEGREE) throw MathError(ErrorKind.UNSUPPORTED, "Exponent must be an integer between 0 and $MAX_DEGREE")
        var result = const(Rat.ONE)
        repeat(n) { result = result * this }
        return result
    }

    fun toText(): String {
        if (terms.isEmpty()) return "0"
        val allSingle = vars().all { it.length == 1 }
        val order = vars().toList()
        val sorted = terms.entries.sortedWith(Comparator<Map.Entry<Mono, Rat>> { a, b ->
            if (a.key.degree != b.key.degree) return@Comparator b.key.degree.compareTo(a.key.degree)
            for (v in order) { val c = b.key.exp(v).compareTo(a.key.exp(v)); if (c != 0) return@Comparator c }
            0
        })
        val sb = StringBuilder()
        for ((idx, e) in sorted.withIndex()) {
            val (m, c) = e
            val neg = c.signum < 0
            val ac = c.abs()
            if (idx == 0) { if (neg) sb.append("-") } else sb.append(if (neg) " - " else " + ")
            val mono = m.vars.joinToString(if (allSingle) "" else "*") { (n, ex) -> if (ex > 1) "$n^$ex" else n }
            when {
                mono.isEmpty() -> sb.append(ac.toString())
                ac == Rat.ONE -> sb.append(mono)
                ac.isInteger -> sb.append(ac.toString()).append(if (allSingle) "" else "*").append(mono)
                else -> sb.append("(").append(ac.toString()).append(")").append(if (allSingle) "" else "*").append(mono)
            }
        }
        return sb.toString()
    }
}

/** Univariate polynomial; coefficients ascending (c[0] + c[1]x + ...). */
class UPoly(coeffs: List<Rat>) {
    val c: List<Rat> = coeffs.dropLastWhile { it.isZero }
    val degree: Int get() = c.size - 1
    val isZero get() = c.isEmpty()
    val lead: Rat get() = c.last()

    companion object {
        fun fromM(p: MPoly, v: String): UPoly {
            val d = p.degreeIn(v)
            val arr = Array(d + 1) { Rat.ZERO }
            for ((m, co) in p.terms) {
                if (m.vars.any { it.first != v }) throw MathError(ErrorKind.UNSUPPORTED, "Polynomial has more than one variable")
                arr[m.exp(v)] = co
            }
            return UPoly(arr.toList())
        }
    }

    fun toM(v: String): MPoly {
        var acc = MPoly.ZERO
        for ((i, co) in c.withIndex()) if (!co.isZero) acc += MPoly(mapOf(Mono(if (i == 0) emptyList() else listOf(Pair(v, i))) to co))
        return acc
    }

    fun eval(x: Rat): Rat { var r = Rat.ZERO; for (i in c.indices.reversed()) r = r * x + c[i]; return r }
    fun evalD(x: Double): Double { var r = 0.0; for (i in c.indices.reversed()) r = r * x + c[i].toDouble(); return r }

    fun derivative(): UPoly = UPoly(c.drop(1).mapIndexed { i, co -> co * Rat.of(i + 1) })

    operator fun times(o: UPoly): UPoly {
        if (isZero || o.isZero) return UPoly(emptyList())
        val r = Array(c.size + o.c.size - 1) { Rat.ZERO }
        for (i in c.indices) for (j in o.c.indices) r[i + j] = r[i + j] + c[i] * o.c[j]
        return UPoly(r.toList())
    }
    fun scale(k: Rat) = UPoly(c.map { it * k })

    fun divmod(d: UPoly): Pair<UPoly, UPoly> {
        if (d.isZero) throw MathError(ErrorKind.DIV_ZERO, "Division by zero polynomial")
        var rem = c.toMutableList()
        val q = Array(maxOf(degree - d.degree + 1, 0)) { Rat.ZERO }
        while (rem.size >= d.c.size && rem.isNotEmpty()) {
            val coef = rem.last() / d.lead
            val shift = rem.size - d.c.size
            q[shift] = coef
            for (i in d.c.indices) rem[shift + i] = rem[shift + i] - coef * d.c[i]
            rem = rem.dropLastWhile { it.isZero }.toMutableList()
        }
        return Pair(UPoly(q.toList()), UPoly(rem))
    }

    fun monic(): UPoly = if (isZero) this else scale(lead.inverse())

    fun gcd(o: UPoly): UPoly {
        var a = this; var b = o
        while (!b.isZero) { val r = a.divmod(b).second; a = b; b = r }
        return a.monic()
    }

    fun squarefree(): UPoly {
        if (degree < 1) return this
        val g = gcd(derivative())
        return if (g.degree >= 1) divmod(g).first else this
    }

    /** Returns (content, primitive integer polynomial with positive leading coefficient); content carries the sign. */
    fun primitive(): Pair<Rat, UPoly> {
        if (isZero) return Pair(Rat.ONE, this)
        var lcmDen = BigInteger.ONE
        for (co in c) lcmDen = lcmDen.multiply(co.den).divide(lcmDen.gcd(co.den))
        val ints = c.map { it.num.multiply(lcmDen.divide(it.den)) }
        var g = BigInteger.ZERO
        for (i in ints) g = g.gcd(i)
        var content = Rat.of(g, lcmDen)
        if (lead.signum < 0) content = -content
        return Pair(content, scale(content.inverse()))
    }

    fun intCoeffs(): List<BigInteger> = c.map { require(it.isInteger); it.num }
}

class RealRoot(val value: Value, val exact: Boolean) { val d: Double get() = Ops.toDouble(value) }

class QuadResult(val a: Rat, val b: Rat, val c: Rat, val delta: Rat, val roots: List<Value>)

object PolyRoots {
    const val MAX_DEGREE = 24

    fun quadratic(a: Rat, b: Rat, c: Rat): QuadResult {
        val delta = b * b - Rat.of(4) * a * c
        val roots: List<Value> = when {
            delta.signum < 0 -> emptyList()
            delta.isZero -> listOf(Value.Q(-b / (Rat.of(2) * a)))
            else -> {
                val sq = Ops.sqrtRat(delta)
                val two = Value.Q(Rat.of(2) * a)
                val r1 = Ops.div(Ops.sub(Value.Q(-b), sq), two)
                val r2 = Ops.div(Ops.add(Value.Q(-b), sq), two)
                listOf(r1, r2).sortedBy { Ops.toDouble(it) }
            }
        }
        return QuadResult(a, b, c, delta, roots)
    }

    private fun divisors(n: BigInteger): List<Long> {
        val v = n.toLong()
        val out = ArrayList<Long>()
        var i = 1L
        while (i * i <= v) { if (v % i == 0L) { out.add(i); if (i != v / i) out.add(v / i) }; i++ }
        return out
    }

    fun rationalRoots(p: UPoly): List<Rat> {
        val res = ArrayList<Rat>()
        var q = p
        if (q.degree < 1) return res
        if (q.c[0].isZero) { res.add(Rat.ZERO); q = UPoly(q.c.dropWhile { it.isZero }) }
        if (q.degree < 1) return res
        val prim = q.primitive().second
        val ints = prim.intCoeffs()
        val a0 = ints.first().abs()
        val an = ints.last().abs()
        val cap = BigInteger.valueOf(1_000_000_000_000L)
        if (a0 > cap || an > cap) return res
        val d0 = divisors(a0)
        val dn = divisors(an)
        if (d0.size.toLong() * dn.size > 200_000L) return res
        val seen = HashSet<Rat>()
        for (pp in d0) for (qq in dn) for (s in intArrayOf(1, -1)) {
            val cand = Rat.of(s * pp, qq)
            if (seen.add(cand) && prim.eval(cand).isZero) res.add(cand)
        }
        return res
    }

    fun numericRoots(p: UPoly): List<Double> {
        val deg = p.degree
        if (deg < 1) return emptyList()
        if (deg == 1) return listOf(-p.c[0].toDouble() / p.c[1].toDouble())
        if (deg > MAX_DEGREE) throw MathError(ErrorKind.UNSUPPORTED, "Polynomials of degree above $MAX_DEGREE are not supported")
        val crit = numericRoots(p.derivative().squarefree())
        val lc = p.lead.toDouble()
        var bound = 1.0
        for (i in 0 until deg) bound = maxOf(bound, 1.0 + Math.abs(p.c[i].toDouble() / lc))
        val pts = ArrayList<Double>()
        pts.add(-bound)
        crit.sorted().filter { it > -bound && it < bound }.forEach { pts.add(it) }
        pts.add(bound)
        val out = ArrayList<Double>()
        for (i in 0 until pts.size - 1) {
            val l = pts[i]; val r = pts[i + 1]
            val fl = p.evalD(l); val fr = p.evalD(r)
            if (fl == 0.0) { out.add(l); continue }
            if (fl * fr < 0) {
                var a = l; var b = r; var fa = fl
                repeat(200) {
                    val m = (a + b) / 2
                    if (m == a || m == b) return@repeat
                    val fm = p.evalD(m)
                    if (fm == 0.0) { a = m; b = m; return@repeat }
                    if (fa * fm < 0) b = m else { a = m; fa = fm }
                }
                out.add((a + b) / 2)
            } else if (fr == 0.0 && i == pts.size - 2) out.add(r)
        }
        val dedup = ArrayList<Double>()
        for (r in out.sorted()) if (dedup.isEmpty() || Math.abs(r - dedup.last()) > 1e-12 * maxOf(1.0, Math.abs(r))) dedup.add(r)
        return dedup
    }

    /** Distinct real roots in ascending order. Exact where possible, otherwise numeric approximations. */
    fun realRoots(p: UPoly): List<RealRoot> {
        if (p.degree < 1) return emptyList()
        if (p.degree > MAX_DEGREE) throw MathError(ErrorKind.UNSUPPORTED, "Polynomials of degree above $MAX_DEGREE are not supported")
        val sq = p.squarefree()
        val roots = ArrayList<RealRoot>()
        var rest = sq
        for (r in rationalRoots(sq)) {
            roots.add(RealRoot(Value.Q(r), true))
            rest = rest.divmod(UPoly(listOf(-r, Rat.ONE))).first
        }
        when (rest.degree) {
            1 -> roots.add(RealRoot(Value.Q(-rest.c[0] / rest.c[1]), true))
            2 -> quadratic(rest.c[2], rest.c[1], rest.c[0]).roots.forEach { roots.add(RealRoot(it, true)) }
            else -> if (rest.degree >= 3) numericRoots(rest).forEach { roots.add(RealRoot(Value.D(it), false)) }
        }
        return roots.sortedBy { it.d }
    }
}

/** Converts expressions into polynomials using the same Expr model as the evaluator. */
class PolyBuilder(private val scope: Scope, private val angle: AngleMode = AngleMode.DEG) {
    private var userDepth = 0

    fun build(e: Expr, env: Map<String, MPoly> = emptyMap()): MPoly = when (e) {
        is Expr.Num -> MPoly.const(e.value)
        is Expr.Const -> throw MathError(ErrorKind.UNSUPPORTED, "Symbolic operations do not support the constant '${e.name}'")
        is Expr.Var -> env[e.name] ?: scope.variable(e.name)?.let {
            if (it is Value.Q) MPoly.const(it.r)
            else throw MathError(ErrorKind.UNSUPPORTED, "Variable '${e.name}' has a non-rational value, which is not supported in symbolic operations")
        } ?: MPoly.variable(e.name)
        is Expr.Neg -> -build(e.e, env)
        is Expr.Percent -> build(e.e, env).scale(Rat.of(1, 100))
        is Expr.Fact -> {
            val a = build(e.e, env)
            if (!a.isConstant) throw MathError(ErrorKind.UNSUPPORTED, "Factorial of a variable is not a polynomial")
            MPoly.const(constRat(Ops.factorial(Value.Q(a.constantValue()))))
        }
        is Expr.Bin -> bin(e, env)
        is Expr.Call -> call(e, env)
        else -> throw MathError(ErrorKind.UNSUPPORTED, "This expression cannot be converted to a polynomial")
    }

    private fun constRat(v: Value): Rat =
        (v as? Value.Q)?.r ?: throw MathError(ErrorKind.UNSUPPORTED, "Only rational coefficients are supported in symbolic operations")

    private fun bin(e: Expr.Bin, env: Map<String, MPoly>): MPoly {
        val l = build(e.l, env)
        if (e.op == '+' || e.op == '-') {
            if (e.r is Expr.Percent) {
                val pct = build(e.r.e, env).scale(Rat.of(1, 100))
                val d = l * pct
                return if (e.op == '+') l + d else l - d
            }
        }
        val r = build(e.r, env)
        return when (e.op) {
            '+' -> l + r
            '-' -> l - r
            '*' -> l * r
            '/' -> {
                if (!r.isConstant) throw MathError(ErrorKind.UNSUPPORTED, "Division by an expression containing a variable is not supported by solve, expand and factor (it can still be evaluated and graphed)")
                if (r.constantValue().isZero) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
                l.scale(r.constantValue().inverse())
            }
            '^' -> {
                if (!r.isConstant) throw MathError(ErrorKind.UNSUPPORTED, "Variable exponents are not supported by solve, expand and factor (they can still be evaluated and graphed)")
                val k = r.constantValue()
                if (l.isConstant) MPoly.const(constRat(Ops.pow(Value.Q(l.constantValue()), Value.Q(k))))
                else if (k.isInteger && k.signum >= 0 && k.num.bitLength() < 16) l.pow(k.num.toInt())
                else throw MathError(ErrorKind.UNSUPPORTED, "Solve, expand and factor need whole-number powers such as x^2. A power like this can still be evaluated and graphed, e.g. type it as y = ... or use the Graph tool")
            }
            else -> throw MathError(ErrorKind.SYNTAX, "Unknown operator")
        }
    }

    private fun call(c: Expr.Call, env: Map<String, MPoly>): MPoly {
        val fd = scope.function(c.name)
        if (fd != null && c.name !in Names.functions) {
            if (fd.params.size != c.args.size) throw MathError(ErrorKind.SYNTAX, "Function '${c.name}' expects ${fd.params.size} argument(s)")
            if (++userDepth > 40) { userDepth = 0; throw MathError(ErrorKind.LIMIT, "Function calls are nested too deeply") }
            try {
                val args = c.args.map { build(it, env) }
                return build(fd.body, fd.params.zip(args).toMap())
            } finally { if (userDepth > 0) userDepth-- }
        }
        val args = c.args.map { build(it, env) }
        if (args.any { !it.isConstant })
            throw MathError(ErrorKind.UNSUPPORTED, "Function '${c.name}' applied to a variable is not supported symbolically")
        val v = Evaluator(scope, angle).eval(Expr.Call(c.name, args.map { Expr.Num(it.constantValue()) }))
        return MPoly.const(constRat(v))
    }
}
