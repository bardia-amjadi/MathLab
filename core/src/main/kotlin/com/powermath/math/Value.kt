package com.powermath.math

import java.math.BigInteger

/**
 * Result values. Exactness is explicit in the type:
 *  Q  - exact rational
 *  S  - exact a + b*sqrt(n) (n > 1, not a perfect square)
 *  P  - exact c*pi
 *  D  - approximate floating point (optional tag "e" marks Euler's constant)
 */
sealed class Value {
    data class Q(val r: Rat) : Value()
    data class S(val a: Rat, val b: Rat, val n: BigInteger) : Value()
    data class P(val c: Rat) : Value()
    data class D(val d: Double, val tag: String? = null) : Value()

    val isExact: Boolean get() = this !is D

    companion object {
        val ZERO: Value = Q(Rat.ZERO)
        val ONE: Value = Q(Rat.ONE)
        fun of(n: Int): Value = Q(Rat.of(n))
        fun of(n: Long): Value = Q(Rat.of(n))
        fun of(n: Long, d: Long): Value = Q(Rat.of(n, d))
    }
}

object NumUtil {
    /** Integer square root (floor). Own implementation: BigInteger.sqrt() needs Android API 33+. */
    fun isqrt(n: BigInteger): BigInteger {
        require(n.signum() >= 0)
        if (n.bitLength() < 2) return n
        var x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2)
        while (true) {
            val y = x.add(n.divide(x)).shiftRight(1)
            if (y >= x) return x
            x = y
        }
    }

    /** Square root of a non-negative BigDecimal rounded to mc (BigDecimal.sqrt needs Android API 33+). */
    fun sqrtBD(x: java.math.BigDecimal, mc: java.math.MathContext): java.math.BigDecimal {
        if (x.signum() == 0) return java.math.BigDecimal.ZERO
        val s = mc.precision + 10
        val scaled = x.movePointRight(2 * s).toBigInteger()
        return java.math.BigDecimal(isqrt(scaled), s).round(mc)
    }

    /** Returns (s, rad) with m = s^2 * rad (rad squarefree when fully factorable by trial division). */
    fun extractSquares(m: BigInteger): Pair<BigInteger, BigInteger> {
        if (m.signum() == 0) return Pair(BigInteger.ZERO, BigInteger.ONE)
        var rem = m
        var s = BigInteger.ONE
        var rad = BigInteger.ONE
        var p = 2L
        while (p <= 200_000L && BigInteger.valueOf(p * p) <= rem) {
            val bp = BigInteger.valueOf(p)
            var e = 0
            while (rem.mod(bp).signum() == 0) { rem = rem.divide(bp); e++ }
            if (e >= 2) s = s.multiply(bp.pow(e / 2))
            if (e % 2 == 1) rad = rad.multiply(bp)
            p += if (p == 2L) 1 else 2
        }
        val r = isqrt(rem)
        if (r.multiply(r) == rem) s = s.multiply(r) else rad = rad.multiply(rem)
        return Pair(s, rad)
    }

    /** Exact integer k-th root of a non-negative integer, or null when not a perfect power. */
    fun iroot(n: BigInteger, k: Int): BigInteger? {
        if (n.signum() < 0 || k < 1) return null
        if (n.signum() == 0 || n == BigInteger.ONE || k == 1) return n
        if (k == 2) { val r = isqrt(n); return if (r.multiply(r) == n) r else null }
        var lo = BigInteger.ONE
        var hi = BigInteger.ONE.shiftLeft(n.bitLength() / k + 1)
        while (lo <= hi) {
            val mid = lo.add(hi).shiftRight(1)
            val c = mid.pow(k).compareTo(n)
            if (c == 0) return mid
            if (c < 0) lo = mid.add(BigInteger.ONE) else hi = mid.subtract(BigInteger.ONE)
        }
        return null
    }

    fun isPowerOfTen(n: BigInteger): Int? {
        if (n.signum() <= 0) return null
        var k = 0
        var v = n
        val ten = BigInteger.TEN
        while (v != BigInteger.ONE) {
            val qr = v.divideAndRemainder(ten)
            if (qr[1].signum() != 0) return null
            v = qr[0]; k++
        }
        return k
    }
}

object Ops {
    private fun checkD(d: Double): Value {
        if (d.isNaN()) throw MathError(ErrorKind.DOMAIN, "The result is not a real number")
        if (d.isInfinite()) throw MathError(ErrorKind.LIMIT, "The result is too large")
        return Value.D(d)
    }

    fun mkS(a: Rat, b: Rat, n: BigInteger): Value = if (b.isZero) Value.Q(a) else Value.S(a, b, n)
    fun mkP(c: Rat): Value = if (c.isZero) Value.ZERO else Value.P(c)

    private val PI_D = Math.PI

    fun toDouble(v: Value): Double = when (v) {
        is Value.Q -> v.r.toDouble()
        is Value.S -> v.a.toDouble() + v.b.toDouble() * Math.sqrt(v.n.toDouble())
        is Value.P -> v.c.toDouble() * PI_D
        is Value.D -> v.d
    }

    fun isZero(v: Value): Boolean = when (v) {
        is Value.Q -> v.r.isZero
        is Value.D -> v.d == 0.0
        else -> false
    }

    fun sign(v: Value): Int = when (v) {
        is Value.Q -> v.r.signum
        else -> { val d = toDouble(v); if (d > 0) 1 else if (d < 0) -1 else 0 }
    }

    fun sqrtRat(r: Rat): Value {
        if (r.signum < 0) throw MathError(ErrorKind.NON_REAL, "Square root of a negative number is not a real number")
        if (r.isZero) return Value.ZERO
        val m = r.num.multiply(r.den)
        val (s, rad) = NumUtil.extractSquares(m)
        val coef = Rat.of(s, r.den)
        return if (rad == BigInteger.ONE) Value.Q(coef) else Value.S(Rat.ZERO, coef, rad)
    }

    fun neg(x: Value): Value = when (x) {
        is Value.Q -> Value.Q(-x.r)
        is Value.S -> Value.S(-x.a, -x.b, x.n)
        is Value.P -> Value.P(-x.c)
        is Value.D -> Value.D(-x.d)
    }

    fun add(x: Value, y: Value): Value = when {
        x is Value.Q && y is Value.Q -> Value.Q(x.r + y.r)
        x is Value.Q && y is Value.S -> mkS(x.r + y.a, y.b, y.n)
        x is Value.S && y is Value.Q -> add(y, x)
        x is Value.S && y is Value.S -> if (x.n == y.n) mkS(x.a + y.a, x.b + y.b, x.n) else checkD(toDouble(x) + toDouble(y))
        x is Value.P && y is Value.P -> mkP(x.c + y.c)
        x is Value.Q && x.r.isZero -> y
        y is Value.Q && y.r.isZero -> x
        else -> checkD(toDouble(x) + toDouble(y))
    }

    fun sub(x: Value, y: Value): Value = add(x, neg(y))

    fun mul(x: Value, y: Value): Value = when {
        x is Value.Q && y is Value.Q -> Value.Q(x.r * y.r)
        x is Value.Q && x.r.isZero -> Value.ZERO
        y is Value.Q && y.r.isZero -> Value.ZERO
        x is Value.Q && y is Value.S -> mkS(x.r * y.a, x.r * y.b, y.n)
        x is Value.S && y is Value.Q -> mul(y, x)
        x is Value.S && y is Value.S ->
            if (x.n == y.n) mkS(x.a * y.a + x.b * y.b * Rat.of(x.n, BigInteger.ONE), x.a * y.b + y.a * x.b, x.n)
            else {
                if (x.a.isZero && y.a.isZero) {
                    val (s, rad) = NumUtil.extractSquares(x.n.multiply(y.n))
                    val coef = x.b * y.b * Rat.of(s, BigInteger.ONE)
                    if (rad == BigInteger.ONE) Value.Q(coef) else Value.S(Rat.ZERO, coef, rad)
                } else checkD(toDouble(x) * toDouble(y))
            }
        x is Value.Q && y is Value.P -> mkP(x.r * y.c)
        x is Value.P && y is Value.Q -> mkP(x.c * y.r)
        else -> checkD(toDouble(x) * toDouble(y))
    }

    private fun invS(s: Value.S): Value {
        val den = s.a * s.a - s.b * s.b * Rat.of(s.n, BigInteger.ONE)
        return mkS(s.a / den, -s.b / den, s.n)
    }

    fun div(x: Value, y: Value): Value {
        if (isZero(y)) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
        return when {
            y is Value.Q -> mul(x, Value.Q(y.r.inverse()))
            y is Value.S -> mul(x, invS(y))
            y is Value.P && x is Value.P -> Value.Q(x.c / y.c)
            else -> checkD(toDouble(x) / toDouble(y))
        }
    }

    fun abs(x: Value): Value = if (sign(x) < 0) neg(x) else x

    fun sqrt(x: Value): Value = when (x) {
        is Value.Q -> sqrtRat(x.r)
        else -> {
            val d = toDouble(x)
            if (d < 0) throw MathError(ErrorKind.NON_REAL, "Square root of a negative number is not a real number")
            Value.D(Math.sqrt(d))
        }
    }

    fun pow(x: Value, y: Value): Value {
        if (y is Value.Q && y.r.isInteger && y.r.num.bitLength() < 31) {
            val k = y.r.num.toInt()
            if (Math.abs(k) > 100_000) throw MathError(ErrorKind.LIMIT, "Exponent is too large")
            if (isZero(x)) {
                if (k == 0) throw MathError(ErrorKind.DOMAIN, "0^0 is undefined")
                if (k < 0) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
                return Value.ZERO
            }
            when (x) {
                is Value.Q -> return Value.Q(x.r.pow(k))
                is Value.S -> if (Math.abs(k) <= 64) {
                    var result: Value = Value.ONE
                    repeat(Math.abs(k)) { result = mul(result, x) }
                    return if (k < 0) div(Value.ONE, result) else result
                }
                else -> {}
            }
            return checkD(Math.pow(toDouble(x), k.toDouble()))
        }
        if (y is Value.Q && x is Value.Q) {
            val p = y.r.num
            val q = y.r.den
            if (q.bitLength() < 16) {
                val qi = q.toInt()
                var base = x.r
                var negBase = false
                if (base.signum < 0) {
                    if (qi % 2 == 0) throw MathError(ErrorKind.NON_REAL, "Even root of a negative number is not a real number (for example (-2)^1.1 or (-4)^(1/2))")
                    base = -base; negBase = true
                }
                if (base.isZero) {
                    if (p.signum() < 0) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
                    return Value.ZERO
                }
                if (qi == 2 && p.bitLength() < 16) {
                    val root = sqrtRat(base)
                    return pow(root, Value.Q(Rat.of(p, BigInteger.ONE)))
                }
                val rn = NumUtil.iroot(base.num, qi)
                val rd = NumUtil.iroot(base.den, qi)
                if (rn != null && rd != null && p.bitLength() < 31) {
                    var r: Rat = Rat.of(rn, rd).pow(p.toInt())
                    if (negBase && p.testBit(0)) r = -r
                    return Value.Q(r)
                }
                val d = Math.pow(base.toDouble(), y.r.toDouble())
                return checkD(if (negBase && p.testBit(0)) -d else d)
            }
        }
        val bx = toDouble(x)
        val by = toDouble(y)
        if (bx < 0 && by != Math.rint(by)) throw MathError(ErrorKind.NON_REAL, "Negative base with non-integer exponent is not real")
        if (bx == 0.0 && by < 0) throw MathError(ErrorKind.DIV_ZERO, "Division by zero")
        return checkD(Math.pow(bx, by))
    }

    fun factorial(x: Value): Value {
        if (x !is Value.Q || !x.r.isInteger || x.r.signum < 0)
            throw MathError(ErrorKind.DOMAIN, "Factorial is defined for non-negative integers only")
        if (x.r.num > BigInteger.valueOf(2000)) throw MathError(ErrorKind.LIMIT, "Factorial input is too large (max 2000)")
        var r = BigInteger.ONE
        for (i in 2..x.r.num.toInt()) r = r.multiply(BigInteger.valueOf(i.toLong()))
        return Value.Q(Rat.of(r, BigInteger.ONE))
    }

    fun compare(x: Value, y: Value): Int =
        if (x is Value.Q && y is Value.Q) x.r.compareTo(y.r) else toDouble(x).compareTo(toDouble(y))

    fun floor(x: Value): Value = when (x) {
        is Value.Q -> Value.Q(Rat.of(x.r.floor(), BigInteger.ONE))
        else -> {
            val d = toDouble(x)
            if (d.isNaN() || d.isInfinite() || Math.abs(d) > 1e15) throw MathError(ErrorKind.LIMIT, "Value is out of range for floor/ceil")
            Value.of(Math.floor(d).toLong())
        }
    }

    fun ceil(x: Value): Value = neg(floor(neg(x)))
}
