package com.powermath.math

import java.math.BigInteger

/** Prints an expression tree back to text that the shared parser reads again unchanged. */
object ExprPrinter {
    private fun isAtom(e: Expr) = e is Expr.Var || e is Expr.Const || e is Expr.Call || (e is Expr.Num && e.value.isInteger && e.value.signum >= 0)

    fun show(e: Expr): String = when (e) {
        is Expr.Num -> if (e.value.isInteger) e.value.toString() else if (e.value.signum < 0) "-" + frac(e.value.abs()) else frac(e.value)
        is Expr.Const -> e.name
        is Expr.Var -> e.name
        is Expr.Neg -> "-" + wrap(e.e, 2)
        is Expr.Percent -> wrap(e.e, 5) + "%"
        is Expr.Fact -> wrap(e.e, 5) + "!"
        is Expr.Call -> e.name + "(" + e.args.joinToString(", ") { show(it) } + ")"
        is Expr.Bin -> bin(e)
        else -> throw MathError(ErrorKind.UNSUPPORTED, "Cannot print this expression")
    }

    private fun frac(r: Rat) = "${r.num}/${r.den}"

    /** precedence: 1 additive, 2 multiplicative, 3 unary minus, 4 power, 5 atom */
    private fun prec(e: Expr): Int = when (e) {
        is Expr.Bin -> when (e.op) { '+', '-' -> 1; '*', '/' -> 2; else -> 4 }
        is Expr.Neg -> 3
        is Expr.Num -> if (e.value.isInteger && e.value.signum >= 0) 5 else if (e.value.isInteger) 3 else 2
        else -> 5
    }

    private fun wrap(e: Expr, min: Int) = if (prec(e) < min) "(" + show(e) + ")" else show(e)

    private fun bin(e: Expr.Bin): String = when (e.op) {
        '+' -> if (e.r is Expr.Neg) wrap(e.l, 1) + " - " + wrap(e.r.e, 2) else wrap(e.l, 1) + " + " + wrap(e.r, 2)
        '-' -> wrap(e.l, 1) + " - " + wrap(e.r, 2)
        '*' -> times(e)
        '/' -> wrap(e.l, 2) + "/" + wrap(e.r, 3)
        else -> wrap(e.l, 5) + "^" + (if (isAtom(e.r)) show(e.r) else "(" + show(e.r) + ")")
    }

    private fun times(e: Expr.Bin): String {
        val l = e.l; val r = e.r
        if (l is Expr.Num && !l.value.isInteger && l.value.signum > 0) {
            // coefficient p/q: print as "p*t/q" so that 1/3 x^3 reads x^3/3
            val p = l.value.num; val q = l.value.den
            val t = wrap(r, 3)
            val top = if (p == BigInteger.ONE) t else if (implicitOk(r)) p.toString() + t else "$p*$t"
            return "$top/$q"
        }
        if (l is Expr.Num && l.value.isInteger && l.value.signum >= 0 && implicitOk(r)) return l.value.toString() + wrap(r, 3)
        if (r is Expr.Bin && r.op == '*') return wrap(l, 2) + "*" + show(r) // products are associative: a*(b*c) = a*b*c
        return wrap(l, 2) + "*" + wrap(r, 3)
    }

    /** true when "2" followed by the right operand reads as a product without a star */
    private fun implicitOk(r: Expr): Boolean = when (r) {
        is Expr.Var, is Expr.Const -> true
        is Expr.Call -> true
        is Expr.Bin -> (r.op == '^' && (r.l is Expr.Var || r.l is Expr.Call)) || r.op == '+' || r.op == '-'
        else -> false
    }
}

/** Symbolic differentiation and integration on the shared expression tree. */
class Calculus(private val scope: Scope, private val angle: AngleMode) {
    private fun n(x: Long) = Expr.Num(Rat.of(x))
    private fun num(r: Rat) = Expr.Num(r)
    private val ZERO = n(0)
    private val ONE = n(1)
    private fun add(a: Expr, b: Expr): Expr = Expr.Bin('+', a, b)
    private fun sub(a: Expr, b: Expr): Expr = Expr.Bin('-', a, b)
    private fun mul(a: Expr, b: Expr): Expr = Expr.Bin('*', a, b)
    private fun div(a: Expr, b: Expr): Expr = Expr.Bin('/', a, b)
    private fun pow(a: Expr, b: Expr): Expr = Expr.Bin('^', a, b)
    private fun call(f: String, vararg a: Expr): Expr = Expr.Call(f, a.toList())

    /** degrees-to-radians factor applied to trig arguments */
    private val degK: Expr get() = if (angle == AngleMode.DEG) div(Expr.Const("pi"), n(180)) else ONE

    private fun has(e: Expr, v: String): Boolean = v in ExprUtil.freeVars(e) || ExprUtil.calledFunctions(e).any { f ->
        scope.function(f) != null
    }

    // ---------------- simplification ----------------
    fun simp(e: Expr): Expr {
        var cur = e
        repeat(8) { val nx = step(cur); if (nx == cur) return cur; cur = nx }
        return cur
    }

    private fun isNum(e: Expr, v: Long) = e is Expr.Num && e.value == Rat.of(v)

    private fun step(e: Expr): Expr = when (e) {
        is Expr.Neg -> { val a = step(e.e)
            when { a is Expr.Num -> num(-a.value); a is Expr.Neg -> a.e; else -> Expr.Neg(a) } }
        is Expr.Call -> Expr.Call(e.name, e.args.map { step(it) }).let { c ->
            when {
                c.name == "ln" && c.args[0] == Expr.Const("e") -> ONE
                c.name == "exp" && isNum(c.args[0], 0) -> ONE
                c.name == "ln" && isNum(c.args[0], 1) -> ZERO
                else -> c
            }
        }
        is Expr.Bin -> {
            val l = step(e.l); val r = step(e.r)
            when (e.op) {
                '+' -> when {
                    isNum(l, 0) -> r; isNum(r, 0) -> l
                    l is Expr.Num && r is Expr.Num -> num(l.value + r.value)
                    r is Expr.Neg -> sub(l, r.e)
                    r is Expr.Num && r.value.signum < 0 -> sub(l, num(-r.value))
                    r is Expr.Bin && r.op == '+' -> add(add(l, r.l), r.r)
                    r is Expr.Bin && r.op == '-' -> sub(add(l, r.l), r.r)
                    l == r -> mul(n(2), l)
                    else -> add(l, r)
                }
                '-' -> when {
                    isNum(r, 0) -> l; isNum(l, 0) -> Expr.Neg(r)
                    l is Expr.Num && r is Expr.Num -> num(l.value - r.value)
                    r is Expr.Neg -> add(l, r.e)
                    r is Expr.Bin && r.op == '-' -> add(sub(l, r.l), r.r)
                    r is Expr.Bin && r.op == '+' -> sub(sub(l, r.l), r.r)
                    l == r -> ZERO
                    else -> sub(l, r)
                }
                '*' -> when {
                    isNum(l, 0) || isNum(r, 0) -> ZERO
                    isNum(l, 1) -> r; isNum(r, 1) -> l
                    l is Expr.Num && r is Expr.Num -> num(l.value * r.value)
                    l is Expr.Neg -> Expr.Neg(mul(l.e, r))
                    r is Expr.Neg -> Expr.Neg(mul(l, r.e))
                    l is Expr.Num && l.value.signum < 0 -> Expr.Neg(mul(num(-l.value), r))
                    r is Expr.Num -> mul(r, l)
                    l !is Expr.Num && r is Expr.Bin && r.op == '*' && r.l is Expr.Num -> mul(r.l, mul(l, r.r))
                    l is Expr.Num && r is Expr.Bin && r.op == '*' && r.l is Expr.Num -> mul(num(l.value * r.l.value), r.r)
                    l is Expr.Num && r is Expr.Bin && r.op == '/' && r.l is Expr.Num -> div(num(l.value * r.l.value), r.r)
                    r is Expr.Bin && r.op == '/' && isNum(r.l, 1) -> div(l, r.r)
                    l == r -> pow(l, n(2))
                    else -> mul(l, r)
                }
                '/' -> when {
                    isNum(r, 1) -> l; isNum(l, 0) -> ZERO
                    l is Expr.Num && r is Expr.Num && !r.value.isZero -> num(l.value / r.value)
                    l is Expr.Neg -> Expr.Neg(div(l.e, r))
                    r is Expr.Neg -> Expr.Neg(div(l, r.e))
                    r is Expr.Num && !r.value.isZero && l is Expr.Bin && l.op == '*' && l.l is Expr.Num -> mul(num(l.l.value / r.value), l.r)
                    r is Expr.Num && !r.value.isZero -> mul(num(Rat.ONE / r.value), l)
                    l == r -> ONE
                    l is Expr.Bin && l.op == '/' -> div(l.l, mul(l.r, r))
                    else -> div(l, r)
                }
                else -> when {
                    isNum(r, 1) -> l; isNum(r, 0) -> ONE
                    l is Expr.Num && r is Expr.Num && r.value.isInteger && r.value.num.bitLength() < 8 && !(l.value.isZero && r.value.signum <= 0) -> num(l.value.pow(r.value.num.toInt()))
                    else -> pow(l, r)
                }
            }
        }
        else -> e
    }

    // ---------------- differentiation ----------------
    fun derivative(e: Expr, v: String): Expr = simp(d(e, v))

    private fun d(e: Expr, v: String): Expr {
        if (!has(e, v)) return ZERO
        return when (e) {
            is Expr.Var -> if (e.name == v) ONE else ZERO
            is Expr.Neg -> Expr.Neg(d(e.e, v))
            is Expr.Bin -> {
                val u = e.l; val w = e.r
                when (e.op) {
                    '+' -> add(d(u, v), d(w, v))
                    '-' -> sub(d(u, v), d(w, v))
                    '*' -> add(mul(d(u, v), w), mul(u, d(w, v)))
                    '/' -> div(sub(mul(d(u, v), w), mul(u, d(w, v))), pow(w, n(2)))
                    else -> when {
                        !has(w, v) -> mul(mul(w, pow(u, sub(w, ONE))), d(u, v))
                        !has(u, v) -> if (u == Expr.Const("e")) mul(e, d(w, v)) else mul(mul(e, call("ln", u)), d(w, v))
                        else -> mul(e, add(mul(d(w, v), call("ln", u)), div(mul(w, d(u, v)), u)))
                    }
                }
            }
            is Expr.Call -> {
                if (scope.function(e.name) != null && e.name !in Names.functions) {
                    val f = scope.function(e.name)!!
                    if (f.params.size != e.args.size) throw MathError(ErrorKind.SYNTAX, "${e.name}() expects ${f.params.size} argument(s)")
                    return d(substitute(f.body, f.params.zip(e.args).toMap()), v)
                }
                val u = e.args.getOrNull(0) ?: throw MathError(ErrorKind.SYNTAX, "Missing argument")
                val du = d(u, v)
                val k = degK
                when (e.name) {
                    "sqrt" -> div(du, mul(n(2), e))
                    "root" -> { val nn = e.args[0]; val x = e.args[1]
                        if (has(nn, v)) throw MathError(ErrorKind.UNSUPPORTED, "Cannot differentiate a root whose index contains $v")
                        d(pow(x, div(ONE, nn)), v) }
                    "abs" -> mul(du, div(u, e))
                    "sin" -> mul(mul(call("cos", u), du), k)
                    "cos" -> Expr.Neg(mul(mul(call("sin", u), du), k))
                    "tan" -> mul(div(du, pow(call("cos", u), n(2))), k)
                    "asin" -> div(du, mul(call("sqrt", sub(ONE, pow(u, n(2)))), k))
                    "acos" -> Expr.Neg(div(du, mul(call("sqrt", sub(ONE, pow(u, n(2)))), k)))
                    "atan" -> div(du, mul(add(ONE, pow(u, n(2))), k))
                    "sinh" -> mul(call("cosh", u), du)
                    "cosh" -> mul(call("sinh", u), du)
                    "tanh" -> div(du, pow(call("cosh", u), n(2)))
                    "ln" -> div(du, u)
                    "log" -> if (e.args.size == 1) div(du, mul(u, call("ln", n(10)))) else {
                        val b = e.args[0]; val x = e.args[1]
                        if (has(b, v)) throw MathError(ErrorKind.UNSUPPORTED, "Cannot differentiate a logarithm whose base contains $v")
                        div(d(x, v), mul(x, call("ln", b)))
                    }
                    "exp" -> mul(e, du)
                    else -> throw MathError(ErrorKind.UNSUPPORTED, "Cannot differentiate ${e.name}() symbolically")
                }
            }
            else -> throw MathError(ErrorKind.UNSUPPORTED, "Cannot differentiate this expression")
        }
    }

    private fun substitute(e: Expr, m: Map<String, Expr>): Expr = when (e) {
        is Expr.Var -> m[e.name] ?: e
        is Expr.Neg -> Expr.Neg(substitute(e.e, m))
        is Expr.Bin -> Expr.Bin(e.op, substitute(e.l, m), substitute(e.r, m))
        is Expr.Call -> Expr.Call(e.name, e.args.map { substitute(it, m) })
        is Expr.Percent -> Expr.Percent(substitute(e.e, m))
        is Expr.Fact -> Expr.Fact(substitute(e.e, m))
        else -> e
    }

    // ---------------- integration ----------------
    /** An antiderivative (without + C), or null when no closed form is known. */
    fun antiderivative(e: Expr, v: String): Expr? = try { anti(e, v)?.let { simp(it) } } catch (ex: MathError) { if (ex.kind == ErrorKind.UNSUPPORTED) null else throw ex }

    private fun constRat(e: Expr): Rat? = if (!has(e, v0)) try { (Evaluator(scope, angle, EvalLimits()).eval(e) as? Value.Q)?.r } catch (ex: MathError) { null } else null
    private var v0 = "x"

    /** a, b with e = a v + b (rational), a != 0 */
    private fun linear(e: Expr, v: String): Pair<Rat, Rat>? {
        val p = try { PolyBuilder(scope, angle).build(e) } catch (ex: MathError) { return null }
        if (p.vars() != setOf(v) || p.degreeIn(v) != 1) return null
        val u = UPoly.fromM(p, v)
        return Pair(u.c[1], u.c[0])
    }

    private fun anti(e: Expr, v: String): Expr? {
        v0 = v
        if (!has(e, v)) return mul(e, Expr.Var(v))
        // polynomial in v with rational coefficients
        val p = try { PolyBuilder(scope, angle).build(e) } catch (ex: MathError) { null }
        if (p != null && p.vars() == setOf(v)) {
            val u = UPoly.fromM(p, v)
            var acc: Expr? = null
            for ((i, c) in u.c.withIndex().reversed()) {
                if (c.isZero) continue
                val term = mul(num(c / Rat.of(i + 1)), pow(Expr.Var(v), n((i + 1).toLong())))
                acc = if (acc == null) term else add(acc, term)
            }
            return acc ?: ZERO
        }
        return when (e) {
            is Expr.Neg -> anti(e.e, v)?.let { Expr.Neg(it) }
            is Expr.Bin -> when (e.op) {
                '+' -> { val a = anti(e.l, v); val b = anti(e.r, v); if (a != null && b != null) add(a, b) else null }
                '-' -> { val a = anti(e.l, v); val b = anti(e.r, v); if (a != null && b != null) sub(a, b) else null }
                '*' -> when {
                    e.r is Expr.Bin && e.r.op == '/' && !has(e.r.r, v) && has(e.r.l, v) && has(e.l, v) -> anti(div(mul(e.l, e.r.l), e.r.r), v)
                    e.l is Expr.Bin && e.l.op == '/' && !has(e.l.r, v) && has(e.l.l, v) && has(e.r, v) -> anti(div(mul(e.l.l, e.r), e.l.r), v)
                    !has(e.l, v) -> anti(e.r, v)?.let { mul(e.l, it) }
                    !has(e.r, v) -> anti(e.l, v)?.let { mul(e.r, it) }
                    else -> byParts(e.l, e.r, v) ?: byParts(e.r, e.l, v)
                }
                '/' -> when {
                    !has(e.r, v) -> anti(e.l, v)?.let { div(it, e.r) }
                    !has(e.l, v) && e.r is Expr.Bin && e.r.op == '^' && !has(e.r.r, v) && constRat(e.r.r) != null ->
                        anti(mul(e.l, pow(e.r.l, num(-constRat(e.r.r)!!))), v)
                    !has(e.l, v) -> linear(e.r, v)?.let { (a, _) -> mul(div(e.l, num(a)), call("ln", call("abs", e.r))) }
                    else -> null
                }
                else -> { // power
                    val lin = linear(e.l, v)
                    val k = if (!has(e.r, v)) constRat(e.r) else null
                    if (lin != null && k != null) {
                        if (k == -Rat.ONE) mul(div(ONE, num(lin.first)), call("ln", call("abs", e.l)))
                        else div(pow(e.l, num(k + Rat.ONE)), num((k + Rat.ONE) * lin.first))
                    } else if (!has(e.l, v)) { // a^(linear)
                        val l2 = linear(e.r, v)
                        if (l2 != null) {
                            if (e.l == Expr.Const("e")) div(e, num(l2.first)) else div(e, mul(num(l2.first), call("ln", e.l)))
                        } else null
                    } else null
                }
            }
            is Expr.Call -> {
                val u = e.args.getOrNull(0)
                if (scope.function(e.name) != null && e.name !in Names.functions) {
                    val f = scope.function(e.name)!!
                    return anti(substitute(f.body, f.params.zip(e.args).toMap()), v)
                }
                val lin = if (u != null) linear(u, v) else null
                if (lin == null) return null
                val a = num(lin.first)
                val ak = if (angle == AngleMode.DEG) mul(a, div(Expr.Const("pi"), n(180))) else a
                when (e.name) {
                    "sin" -> Expr.Neg(div(call("cos", u!!), ak))
                    "cos" -> div(call("sin", u!!), ak)
                    "tan" -> Expr.Neg(div(call("ln", call("abs", call("cos", u!!))), ak))
                    "exp" -> div(e, a)
                    "sinh" -> div(call("cosh", u!!), a)
                    "cosh" -> div(call("sinh", u!!), a)
                    "sqrt" -> div(mul(n(2), pow(u!!, div(n(3), n(2)))), mul(n(3), a))
                    "ln" -> div(sub(mul(u!!, e), u), a)
                    else -> null
                }
            }
            else -> null
        }
    }

    /** ∫ p·g where p is a polynomial of degree ≥ 1 and g is exp/sin/cos of a linear argument. */
    private fun byParts(p: Expr, g: Expr, v: String): Expr? {
        val poly = try { PolyBuilder(scope, angle).build(p) } catch (ex: MathError) { return null }
        if (poly.vars() != setOf(v) || poly.degreeIn(v) < 1) return null
        if (!(g is Expr.Call && g.name in setOf("exp", "sin", "cos"))) return null
        val gi = anti(g, v) ?: return null
        val dp = simp(d(p, v))
        val rest = anti(simp(mul(dp, gi)), v) ?: return null
        return sub(mul(p, gi), rest)
    }

    // ---------------- numeric fallbacks ----------------
    /** Adaptive Simpson on doubles, used when no closed form exists. */
    fun numericIntegral(f: (Double) -> Double, a: Double, b: Double): Double {
        fun simpson(fa: Double, fm: Double, fb: Double, h: Double) = h / 6.0 * (fa + 4 * fm + fb)
        fun rec(a: Double, b: Double, fa: Double, fm: Double, fb: Double, whole: Double, eps: Double, depth: Int): Double {
            val m = (a + b) / 2; val lm = (a + m) / 2; val rm = (m + b) / 2
            val flm = f(lm); val frm = f(rm)
            val left = simpson(fa, flm, fm, m - a); val right = simpson(fm, frm, fb, b - m)
            if (depth <= 0 || Math.abs(left + right - whole) <= 15 * eps) return left + right + (left + right - whole) / 15
            return rec(a, m, fa, flm, fm, left, eps / 2, depth - 1) + rec(m, b, fm, frm, fb, right, eps / 2, depth - 1)
        }
        val fa = f(a); val fb = f(b); val fm = f((a + b) / 2)
        if (fa.isNaN() || fb.isNaN() || fm.isNaN()) throw MathError(ErrorKind.DOMAIN, "The function is not defined on the whole interval")
        val r = rec(a, b, fa, fm, fb, simpson(fa, fm, fb, b - a), 1e-10, 40)
        if (r.isNaN() || r.isInfinite()) throw MathError(ErrorKind.DOMAIN, "The integral does not converge")
        return r
    }
}
