package com.powermath.math

import java.math.BigInteger

class EvalLimits(val deadlineNanos: Long = Long.MAX_VALUE, val maxDepth: Int = 50) {
    companion object { fun withTimeoutMs(ms: Long) = EvalLimits(System.nanoTime() + ms * 1_000_000L) }
}

object Trig {
    private val half = Rat.of(1, 2)
    private fun base(k: Int): Value? = when (k) {
        0 -> Value.ZERO
        30 -> Value.Q(half)
        45 -> Value.S(Rat.ZERO, half, BigInteger.valueOf(2))
        60 -> Value.S(Rat.ZERO, half, BigInteger.valueOf(3))
        90 -> Value.ONE
        else -> null
    }

    /** Exact sin for integer degrees that are multiples of 30 or 45; null otherwise. */
    fun sinExact(deg: Int): Value? {
        val k = ((deg % 360) + 360) % 360
        return when {
            k <= 90 -> base(k)
            k <= 180 -> base(180 - k)
            k <= 270 -> base(k - 180)?.let { Ops.neg(it) }
            else -> base(360 - k)?.let { Ops.neg(it) }
        }
    }

    fun cosExact(deg: Int): Value? = sinExact(deg + 90)

    fun tanExact(deg: Int): Value? {
        val s = sinExact(deg) ?: return null
        val c = cosExact(deg) ?: return null
        if (Ops.isZero(c)) throw MathError(ErrorKind.DOMAIN, "tan is undefined at this angle")
        return Ops.div(s, c)
    }

    /** Degrees as exact integer if the angle is an exact whole number of degrees. */
    fun exactDegrees(arg: Value, mode: AngleMode): Int? = when {
        arg is Value.Q && arg.r.isZero -> 0
        mode == AngleMode.DEG && arg is Value.Q && arg.r.isInteger && arg.r.num.bitLength() < 31 -> arg.r.num.toInt()
        mode == AngleMode.RAD && arg is Value.P -> {
            val d = arg.c * Rat.of(180)
            if (d.isInteger && d.num.bitLength() < 31) d.num.toInt() else null
        }
        else -> null
    }

    fun toRadians(arg: Value, mode: AngleMode): Double =
        if (mode == AngleMode.DEG) Math.toRadians(Ops.toDouble(arg)) else Ops.toDouble(arg)
}

class Evaluator(
    private val scope: Scope,
    val angle: AngleMode = AngleMode.DEG,
    private val limits: EvalLimits = EvalLimits(),
    private val initialFrame: Map<String, Value> = emptyMap()
) {
    private var frame: Map<String, Value> = initialFrame
    private var depth = 0
    private val callStack = ArrayList<String>()

    private fun tick() {
        if (System.nanoTime() > limits.deadlineNanos) throw MathError(ErrorKind.LIMIT, "Calculation took too long")
    }

    fun eval(e: Expr): Value {
        tick()
        return when (e) {
            is Expr.Num -> Value.Q(e.value)
            is Expr.Const -> constant(e.name)
            is Expr.Var -> frame[e.name] ?: scope.variable(e.name) ?: throw MathError(ErrorKind.UNDEFINED, "Undefined variable '${e.name}'")
            is Expr.Neg -> Ops.neg(eval(e.e))
            is Expr.Bin -> bin(e)
            is Expr.Percent -> Ops.div(eval(e.e), Value.of(100))
            is Expr.Fact -> Ops.factorial(eval(e.e))
            is Expr.Call -> call(e)
            is Expr.Rel, is Expr.Chain -> throw MathError(ErrorKind.UNSUPPORTED, "An equation or inequality has no numeric value; use solve(...)")
            is Expr.FuncDefStmt, is Expr.Assign -> throw MathError(ErrorKind.SYNTAX, "Definitions cannot be nested inside expressions")
        }
    }

    private fun constant(name: String): Value = when (name) {
        "pi" -> Value.P(Rat.ONE)
        "tau" -> Value.P(Rat.of(2))
        "e" -> Value.D(Math.E, "e")
        "phi" -> Value.S(Rat.of(1, 2), Rat.of(1, 2), BigInteger.valueOf(5))
        else -> throw MathError(ErrorKind.UNDEFINED, "Unknown constant '$name'")
    }

    private fun bin(e: Expr.Bin): Value {
        // Percent semantics: a + b% = a + a*b/100 ; a - b% = a - a*b/100
        if ((e.op == '+' || e.op == '-') && e.r is Expr.Percent) {
            val l = eval(e.l)
            val pct = Ops.div(eval(e.r.e), Value.of(100))
            val delta = Ops.mul(l, pct)
            return if (e.op == '+') Ops.add(l, delta) else Ops.sub(l, delta)
        }
        val l = eval(e.l)
        val r = eval(e.r)
        return when (e.op) {
            '+' -> Ops.add(l, r)
            '-' -> Ops.sub(l, r)
            '*' -> Ops.mul(l, r)
            '/' -> Ops.div(l, r)
            '^' -> Ops.pow(l, r)
            else -> throw MathError(ErrorKind.SYNTAX, "Unknown operator '${e.op}'")
        }
    }

    private fun call(c: Expr.Call): Value {
        val name = c.name
        if (name in Names.commands)
            throw MathError(ErrorKind.UNSUPPORTED, "'$name' must be used as a whole command, not inside an expression")
        val fd = scope.function(name)
        if (fd != null && name !in Names.functions) return callUser(fd, c.args)
        val range = Names.functions[name] ?: throw MathError(ErrorKind.UNDEFINED, "Unknown function '$name'")
        if (c.args.size !in range) throw MathError(ErrorKind.SYNTAX, "Function '$name' expects ${if (range.first == range.last) "${range.first}" else "${range.first}-${range.last}"} argument(s), got ${c.args.size}")
        val a = c.args.map { eval(it) }
        return builtin(name, a)
    }

    private fun callUser(fd: FuncDef, args: List<Expr>): Value {
        if (args.size != fd.params.size)
            throw MathError(ErrorKind.SYNTAX, "Function '${fd.name}' expects ${fd.params.size} argument(s), got ${args.size}")
        if (callStack.contains(fd.name))
            throw MathError(ErrorKind.DEFINITION, "Circular definition: ${(callStack + fd.name).joinToString(" → ")}")
        if (depth >= limits.maxDepth) throw MathError(ErrorKind.LIMIT, "Function calls are nested too deeply")
        val values = args.map { eval(it) }
        val saved = frame
        frame = fd.params.zip(values).toMap()
        callStack.add(fd.name); depth++
        try { return eval(fd.body) } finally { frame = saved; callStack.removeAt(callStack.size - 1); depth-- }
    }

    private fun intArg(v: Value, what: String): BigInteger {
        if (v !is Value.Q || !v.r.isInteger) throw MathError(ErrorKind.DOMAIN, "$what must be an integer")
        return v.r.num
    }

    private fun builtin(name: String, a: List<Value>): Value {
        val x = a[0]
        return when (name) {
            "sqrt" -> Ops.sqrt(x)
            "root" -> {
                val n = a[0]
                if (n is Value.Q && n.r.isZero) throw MathError(ErrorKind.DOMAIN, "The root index cannot be zero")
                Ops.pow(a[1], Ops.div(Value.ONE, n))
            }
            "abs" -> Ops.abs(x)
            "sin" -> trig(x, 0)
            "cos" -> trig(x, 1)
            "tan" -> trig(x, 2)
            "asin", "acos", "atan" -> inverseTrig(name, x)
            "sinh" -> real(Math.sinh(Ops.toDouble(x)))
            "cosh" -> real(Math.cosh(Ops.toDouble(x)))
            "tanh" -> real(Math.tanh(Ops.toDouble(x)))
            "ln" -> ln(x)
            "log" -> if (a.size == 1) log10(x) else logBase(a[0], a[1])
            "exp" -> if (x is Value.Q && x.r.isZero) Value.ONE else if (x is Value.Q && x.r == Rat.ONE) Value.D(Math.E, "e") else real(Math.exp(Ops.toDouble(x)))
            "mean" -> { var s: Value = Value.ZERO; a.forEach { s = Ops.add(s, it) }; Ops.div(s, Value.of(a.size.toLong())) }
            "min" -> a.reduce { m, v -> if (Ops.compare(v, m) < 0) v else m }
            "max" -> a.reduce { m, v -> if (Ops.compare(v, m) > 0) v else m }
            "floor" -> Ops.floor(x)
            "ceil" -> Ops.ceil(x)
            "sgn" -> Value.of(Ops.sign(x).toLong())
            "fact" -> Ops.factorial(x)
            "gcd" -> Value.Q(Rat.of(intArg(a[0], "gcd argument").gcd(intArg(a[1], "gcd argument")), BigInteger.ONE))
            "lcm" -> {
                val p = intArg(a[0], "lcm argument"); val q = intArg(a[1], "lcm argument")
                if (p.signum() == 0 || q.signum() == 0) Value.ZERO
                else Value.Q(Rat.of(p.multiply(q).abs().divide(p.gcd(q)), BigInteger.ONE))
            }
            "mod" -> {
                val p = intArg(a[0], "mod argument"); val q = intArg(a[1], "mod argument")
                if (q.signum() == 0) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
                val m = p.mod(q.abs()); Value.Q(Rat.of(if (q.signum() < 0 && m.signum() != 0) m.add(q) else m, BigInteger.ONE))
            }
            "ncr", "npr" -> {
                val n = intArg(a[0], "n"); val k = intArg(a[1], "r")
                if (n.signum() < 0 || k.signum() < 0 || k > n) throw MathError(ErrorKind.DOMAIN, "$name requires 0 ≤ r ≤ n")
                if (n > BigInteger.valueOf(5000)) throw MathError(ErrorKind.LIMIT, "n is too large")
                var perm = BigInteger.ONE
                for (i in 0 until k.toInt()) perm = perm.multiply(n.subtract(BigInteger.valueOf(i.toLong())))
                if (name == "npr") Value.Q(Rat.of(perm, BigInteger.ONE))
                else { var f = BigInteger.ONE; for (i in 2..k.toInt()) f = f.multiply(BigInteger.valueOf(i.toLong())); Value.Q(Rat.of(perm.divide(f), BigInteger.ONE)) }
            }
            "delta" -> Ops.sub(Ops.mul(a[1], a[1]), Ops.mul(Value.of(4), Ops.mul(a[0], a[2])))
            "arith" -> Sequences.arithTerm(a[0], a[1], a[2])
            "arithsum" -> Sequences.arithSum(a[0], a[1], a[2])
            "geom" -> Sequences.geomTerm(a[0], a[1], a[2])
            "geomsum" -> Sequences.geomSum(a[0], a[1], a[2])
            "geominf" -> Sequences.geomInfinite(a[0], a[1])
            else -> throw MathError(ErrorKind.UNDEFINED, "Unknown function '$name'")
        }
    }

    private fun real(d: Double): Value {
        if (d.isNaN()) throw MathError(ErrorKind.DOMAIN, "The result is not a real number")
        if (d.isInfinite()) throw MathError(ErrorKind.LIMIT, "The result is too large")
        return Value.D(d)
    }

    private fun trig(x: Value, which: Int): Value {
        val deg = Trig.exactDegrees(x, angle)
        if (deg != null) {
            val ex = when (which) { 0 -> Trig.sinExact(deg); 1 -> Trig.cosExact(deg); else -> Trig.tanExact(deg) }
            if (ex != null) return ex
        }
        val r = Trig.toRadians(x, angle)
        val d = when (which) { 0 -> Math.sin(r); 1 -> Math.cos(r); else -> Math.tan(r) }
        if (which == 2 && Math.abs(d) > 1e14) throw MathError(ErrorKind.DOMAIN, "tan is undefined at this angle")
        return real(d)
    }

    private fun inverseTrig(name: String, x: Value): Value {
        val v = Ops.toDouble(x)
        val rad = when (name) {
            "asin" -> { if (v < -1 || v > 1) throw MathError(ErrorKind.DOMAIN, "asin is defined for -1 ≤ x ≤ 1"); Math.asin(v) }
            "acos" -> { if (v < -1 || v > 1) throw MathError(ErrorKind.DOMAIN, "acos is defined for -1 ≤ x ≤ 1"); Math.acos(v) }
            else -> Math.atan(v)
        }
        val deg = Math.toDegrees(rad)
        val r = Math.rint(deg)
        if (x.isExact && Math.abs(deg - r) < 1e-9) {
            val k = r.toInt()
            val fwd = when (name) { "asin" -> Trig.sinExact(k); "acos" -> Trig.cosExact(k); else -> try { Trig.tanExact(k) } catch (e: MathError) { null } }
            if (fwd != null && fwd == x) {
                return if (angle == AngleMode.DEG) Value.of(k.toLong()) else Ops.mkP(Rat.of(k.toLong(), 180L))
            }
        }
        return real(if (angle == AngleMode.DEG) deg else rad)
    }

    private fun ln(x: Value): Value {
        if (Ops.sign(x) <= 0) throw MathError(ErrorKind.DOMAIN, "ln is defined for positive numbers only")
        if (x is Value.Q && x.r == Rat.ONE) return Value.ZERO
        if (x is Value.D && x.tag == "e") return Value.ONE
        return real(Math.log(Ops.toDouble(x)))
    }

    private fun log10(x: Value): Value {
        if (Ops.sign(x) <= 0) throw MathError(ErrorKind.DOMAIN, "log is defined for positive numbers only")
        if (x is Value.Q) {
            val a = NumUtil.isPowerOfTen(x.r.num)
            if (x.r.den == BigInteger.ONE && a != null) return Value.of(a.toLong())
            val b = NumUtil.isPowerOfTen(x.r.den)
            if (x.r.num == BigInteger.ONE && b != null) return Value.of(-b.toLong())
        }
        return real(Math.log10(Ops.toDouble(x)))
    }

    private fun logBase(b: Value, x: Value): Value {
        if (Ops.sign(x) <= 0 || Ops.sign(b) <= 0) throw MathError(ErrorKind.DOMAIN, "log is defined for positive numbers only")
        if (b is Value.Q && b.r == Rat.ONE) throw MathError(ErrorKind.DOMAIN, "log base 1 is undefined")
        if (b is Value.Q && x is Value.Q) {
            val k = Math.rint(Math.log(x.r.toDouble()) / Math.log(b.r.toDouble()))
            if (!k.isNaN() && Math.abs(k) < 5000) {
                try { if (b.r.pow(k.toInt()) == x.r) return Value.of(k.toLong()) } catch (_: MathError) {}
            }
        }
        return real(Math.log(Ops.toDouble(x)) / Math.log(Ops.toDouble(b)))
    }
}
